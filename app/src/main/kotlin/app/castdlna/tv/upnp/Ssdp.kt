package app.castdlna.tv.upnp

import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import kotlin.concurrent.thread
import kotlin.random.Random

private const val TAG = "Ssdp"
const val SSDP_ADDR = "239.255.255.250"
const val SSDP_PORT = 1900
private const val MAX_AGE = 1800

/** Cada cuánto se comprueba si la dirección cambió. */
private const val VIGILAR_IP_SEG = 10

/**
 * Descubrimiento y anuncio SSDP.
 *
 * En Android hace falta además un [WifiManager.MulticastLock]: sin él el
 * sistema descarta los paquetes multicast que no van dirigidos a esta app, y
 * los `M-SEARCH` de los controladores no llegan nunca. Es el motivo por el que
 * un receptor escrito solo en la capa portable no aparece en la red.
 */
class Ssdp(
    private val wifi: WifiManager,
    private val udn: String,
    private val puerto: Int,
    private val ipActual: () -> String?,
) {
    private var lock: WifiManager.MulticastLock? = null
    @Volatile private var enMarcha = false
    @Volatile private var ip: String? = null
    @Volatile private var bootId = (System.currentTimeMillis() / 1000).toInt() and 0x7FFFFFFF
    private var socket: MulticastSocket? = null

    private val tipos: List<String> by lazy {
        listOf(
            "upnp:rootdevice",
            udn,
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            Servicios.AVT,
            Servicios.RCS,
            Servicios.CMS,
        )
    }

    private fun usn(tipo: String) = if (tipo == udn) udn else "$udn::$tipo"

    private fun baseUrl() = "http://${ip}:$puerto"

    fun arrancar() {
        if (enMarcha) return
        enMarcha = true
        ip = ipActual()
        lock = wifi.createMulticastLock("cast-dlna-tv").apply {
            setReferenceCounted(false)
            acquire()
        }
        thread(name = "ssdp-escucha", isDaemon = true) { escuchar() }
        thread(name = "ssdp-anuncio", isDaemon = true) { anunciar() }
        thread(name = "ssdp-ip", isDaemon = true) { vigilarIp() }
    }

    fun parar() {
        enMarcha = false
        notificar(vivo = false)
        runCatching { socket?.close() }
        runCatching { lock?.release() }
        lock = null
    }

    // ── Respuesta a M-SEARCH ───────────────────────────────────────────────
    private fun respuesta(tipo: String) = buildString {
        append("HTTP/1.1 200 OK\r\n")
        append("CACHE-CONTROL: max-age=$MAX_AGE\r\n")
        append("EXT:\r\n")
        append("LOCATION: ${baseUrl()}/device.xml\r\n")
        append("SERVER: Android/1.0 UPnP/1.0 cast-dlna-tv/1.0\r\n")
        append("ST: $tipo\r\n")
        append("USN: ${usn(tipo)}\r\n")
        append("BOOTID.UPNP.ORG: $bootId\r\n")
        append("CONFIGID.UPNP.ORG: 1\r\n")
        append("CONTENT-LENGTH: 0\r\n")
        append("\r\n")
    }

    private fun escuchar() {
        try {
            val s = MulticastSocket(SSDP_PORT).apply {
                reuseAddress = true
                soTimeout = 1000
            }
            socket = s
            unirseAlGrupo(s, ip)
            val buffer = ByteArray(4096)
            while (enMarcha) {
                val paquete = DatagramPacket(buffer, buffer.size)
                try {
                    s.receive(paquete)
                } catch (e: Exception) {
                    continue
                }
                val mensaje = String(paquete.data, 0, paquete.length)
                if (!mensaje.startsWith("M-SEARCH", ignoreCase = true)) continue
                val cabeceras = cabecerasDe(mensaje)
                if (cabeceras["man"]?.trim('"') != "ssdp:discover") continue
                val st = cabeceras["st"].orEmpty()
                val objetivos = when {
                    st == "ssdp:all" -> tipos
                    st in tipos -> listOf(st)
                    else -> emptyList()
                }
                if (objetivos.isEmpty()) continue
                val mx = cabeceras["mx"]?.toIntOrNull()?.coerceAtMost(5) ?: 1
                responder(s, paquete.address, paquete.port, objetivos, mx)
            }
            s.close()
        } catch (e: Exception) {
            Log.w(TAG, "escucha detenida: ${e.message}")
        }
    }

    private fun responder(
        s: MulticastSocket,
        destino: InetAddress,
        puertoDestino: Int,
        objetivos: List<String>,
        mx: Int,
    ) {
        thread(isDaemon = true) {
            Thread.sleep((Random.nextDouble() * mx * 500).toLong())
            for (tipo in objetivos) {
                val datos = respuesta(tipo).toByteArray()
                // Dos veces: UDP sobre wifi se pierde, y la respuesta sale por
                // el mismo socket del 1900 para que le llegue al controlador
                // desde el puerto al que preguntó.
                repeat(2) {
                    runCatching { s.send(DatagramPacket(datos, datos.size, destino, puertoDestino)) }
                    Thread.sleep(50)
                }
            }
        }
    }

    // ── Anuncio ────────────────────────────────────────────────────────────
    private fun notificar(vivo: Boolean) {
        val direccion = ip ?: return
        runCatching {
            MulticastSocket().use { s ->
                s.timeToLive = 4
                runCatching { s.setInterface(InetAddress.getByName(direccion)) }
                for (tipo in tipos) {
                    val mensaje = if (vivo) buildString {
                        append("NOTIFY * HTTP/1.1\r\n")
                        append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
                        append("CACHE-CONTROL: max-age=$MAX_AGE\r\n")
                        append("LOCATION: ${baseUrl()}/device.xml\r\n")
                        append("NT: $tipo\r\n")
                        append("NTS: ssdp:alive\r\n")
                        append("SERVER: Android/1.0 UPnP/1.0 cast-dlna-tv/1.0\r\n")
                        append("USN: ${usn(tipo)}\r\n")
                        append("BOOTID.UPNP.ORG: $bootId\r\n")
                        append("CONFIGID.UPNP.ORG: 1\r\n\r\n")
                    } else buildString {
                        append("NOTIFY * HTTP/1.1\r\n")
                        append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
                        append("NT: $tipo\r\n")
                        append("NTS: ssdp:byebye\r\n")
                        append("USN: ${usn(tipo)}\r\n")
                        append("BOOTID.UPNP.ORG: $bootId\r\n")
                        append("CONFIGID.UPNP.ORG: 1\r\n\r\n")
                    }
                    val datos = mensaje.toByteArray()
                    runCatching {
                        s.send(DatagramPacket(datos, datos.size,
                            InetAddress.getByName(SSDP_ADDR), SSDP_PORT))
                    }
                }
            }
        }.onFailure { Log.w(TAG, "no se pudo anunciar por $direccion: ${it.message}") }
    }

    private fun anunciar() {
        // Ráfaga inicial: el primer anuncio se pierde a menudo si el
        // controlador aún no está escuchando el grupo.
        repeat(3) {
            notificar(vivo = true)
            Thread.sleep(400)
        }
        while (enMarcha) {
            repeat(MAX_AGE / 3) {
                if (!enMarcha) return
                Thread.sleep(1000)
            }
            notificar(vivo = true)
        }
    }

    /**
     * Los controladores guardan el dispositivo por su UDN junto a la dirección
     * donde lo encontraron, con media hora de validez. Si la IP cambia y nadie
     * avisa, siguen pidiendo el descriptor a una dirección muerta y el
     * receptor «desaparece» sin más.
     */
    private fun vigilarIp() {
        while (enMarcha) {
            repeat(VIGILAR_IP_SEG) {
                if (!enMarcha) return
                Thread.sleep(1000)
            }
            val nueva = ipActual() ?: continue
            val anterior = ip ?: continue
            if (nueva == anterior) continue

            Log.i(TAG, "la dirección cambió: $anterior → $nueva")
            notificar(vivo = false)
            ip = nueva
            bootId = (bootId + 1) and 0x7FFFFFFF
            socket?.let { rehacerGrupo(it, anterior, nueva) }
            repeat(3) {
                notificar(vivo = true)
                Thread.sleep(400)
            }
        }
    }

    private fun unirseAlGrupo(s: MulticastSocket, direccion: String?) {
        val grupo = InetSocketAddress(InetAddress.getByName(SSDP_ADDR), SSDP_PORT)
        val interfaz = direccion?.let { interfazDe(it) }
        runCatching {
            if (interfaz != null) s.joinGroup(grupo, interfaz) else s.joinGroup(grupo.address)
        }.onFailure { Log.w(TAG, "no se pudo unir al grupo: ${it.message}") }
    }

    private fun rehacerGrupo(s: MulticastSocket, anterior: String, nueva: String) {
        val grupo = InetSocketAddress(InetAddress.getByName(SSDP_ADDR), SSDP_PORT)
        runCatching { interfazDe(anterior)?.let { s.leaveGroup(grupo, it) } }
        unirseAlGrupo(s, nueva)
    }

    private fun interfazDe(direccion: String): NetworkInterface? =
        NetworkInterface.getNetworkInterfaces().toList().firstOrNull { nic ->
            nic.inetAddresses.toList().any { it.hostAddress == direccion }
        }

    private fun cabecerasDe(mensaje: String): Map<String, String> =
        mensaje.lines().drop(1).mapNotNull { linea ->
            val corte = linea.indexOf(':')
            if (corte <= 0) null
            else linea.substring(0, corte).trim().lowercase() to linea.substring(corte + 1).trim()
        }.toMap()
}
