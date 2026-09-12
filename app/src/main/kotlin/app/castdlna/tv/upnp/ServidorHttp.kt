package app.castdlna.tv.upnp

import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

private const val TAG = "ServidorHttp"

/** PNG de 48x48 de un color: algunos controladores piden el icono declarado. */
private val ICONO: ByteArray = Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAIAAABvSEP3AAAAPUlEQVR42u3OMQEAAAgDoC1" +
        "ZNRTwK4EGuHfaBAKBQCAQCAQCgUAgEAgEAoFAIBAIBAKBQCAQCAQCwYcFC7wAAdE9pRIAAAA" +
        "ASUVORK5CYII=", Base64.DEFAULT)

private data class Peticion(
    val metodo: String,
    val ruta: String,
    val cabeceras: Map<String, String>,
    val cuerpo: String,
    val ipCliente: String,
)

/**
 * Servidor HTTP del receptor: descriptores, control SOAP y suscripciones.
 *
 * Se escribe a mano en vez de traer un servidor hecho porque hacen falta
 * métodos que casi ninguno admite —`SUBSCRIBE` y `UNSUBSCRIBE`— y porque hay
 * detalles de la respuesta que resultaron decisivos con controladores reales.
 */
class ServidorHttp(
    private val puerto: Int,
    private val nombre: () -> String,
    private val udn: String,
    private val baseUrl: () -> String,
    private val renderer: Renderer,
    private val eventos: Map<String, Gena>,
) {
    private var servidor: ServerSocket? = null
    @Volatile private var enMarcha = false

    /** Las últimas peticiones, la más reciente primero. Ver [anotar]. */
    val ultimas = ArrayDeque<String>()

    fun arrancar() {
        if (enMarcha) return
        enMarcha = true
        thread(name = "http", isDaemon = true) {
            try {
                val s = ServerSocket(puerto)
                servidor = s
                while (enMarcha) {
                    val cliente = try { s.accept() } catch (e: Exception) { break }
                    thread(isDaemon = true) { atender(cliente) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "no se pudo abrir el puerto $puerto: ${e.message}")
            }
        }
    }

    fun parar() {
        enMarcha = false
        runCatching { servidor?.close() }
    }

    private fun atender(cliente: Socket) {
        // Todo el diálogo va envuelto, y no solo el responder: un controlador
        // que deja la conexión abierta sin pedir nada —cosa que hacen, es para
        // lo que sirve el keep-alive— agota el soTimeout y `leer` lanza. Una
        // excepción que escapa de un hilo se lleva por delante el proceso
        // entero, así que ese timeout perfectamente normal tumbaba el receptor.
        try {
            cliente.use { socket ->
                socket.soTimeout = 15_000
                socket.tcpNoDelay = true
                val entrada = BufferedReader(InputStreamReader(socket.getInputStream()))
                val salida = socket.getOutputStream()
                val ip = socket.inetAddress.hostAddress.orEmpty()
                // Se atienden varias peticiones por conexión, que es lo que
                // anuncia la cabecera. Cerrar tras la primera mientras se dice
                // `keep-alive` deja al controlador esperando una respuesta que
                // no llega, y los que comprueban el receptor a cada vuelta
                // reutilizan la conexión precisamente para eso.
                while (enMarcha) {
                    val peticion = leer(entrada, ip) ?: break
                    try {
                        responder(peticion, salida)
                    } catch (e: Exception) {
                        Log.w(TAG, "fallo atendiendo ${peticion.ruta}: ${e.message}")
                        break
                    }
                    salida.flush()
                    if (peticion.cabeceras["connection"]?.lowercase() == "close") break
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "conexión terminada: ${e.message}")
        }
    }

    private fun leer(entrada: BufferedReader, ip: String): Peticion? {
        val primera = entrada.readLine() ?: return null
        val partes = primera.split(" ")
        if (partes.size < 2) return null

        val cabeceras = mutableMapOf<String, String>()
        while (true) {
            val linea = entrada.readLine() ?: break
            if (linea.isEmpty()) break
            val corte = linea.indexOf(':')
            if (corte > 0) {
                cabeceras[linea.substring(0, corte).trim().lowercase()] =
                    linea.substring(corte + 1).trim()
            }
        }
        val largo = cabeceras["content-length"]?.toIntOrNull() ?: 0
        val cuerpo = if (largo > 0) {
            val buffer = CharArray(largo)
            var leidos = 0
            while (leidos < largo) {
                val n = entrada.read(buffer, leidos, largo - leidos)
                if (n <= 0) break
                leidos += n
            }
            String(buffer, 0, leidos)
        } else ""

        return Peticion(partes[0].uppercase(), partes[1], cabeceras, cuerpo, ip)
    }

    private fun responder(p: Peticion, salida: OutputStream): Unit = anotar(p).let { responderA(p, salida) }

    /**
     * Deja constancia de lo que pide cada controlador.
     *
     * Vale la pena tenerlo siempre y no solo al depurar: los controladores no
     * se comportan igual —unos se suscriben a los eventos y otros preguntan en
     * bucle— y sin ver sus peticiones no hay forma de saber por qué uno se
     * entera de que el vídeo acabó y otro se queda con los mandos puestos.
     */
    private fun anotar(p: Peticion) {
        val accion = p.cabeceras["soapaction"]?.substringAfterLast('#')?.trim('"').orEmpty()
        val linea = "${p.ipCliente} ${p.metodo} ${p.ruta}" + if (accion.isNotEmpty()) " $accion" else ""
        Log.i(TAG, linea)
        synchronized(ultimas) {
            ultimas.addFirst(linea)
            while (ultimas.size > MAX_ANOTADAS) ultimas.removeLast()
        }
    }

    private fun responderA(p: Peticion, salida: OutputStream) = when (p.metodo) {
        "GET", "HEAD" -> recurso(p, salida)
        "POST" -> control(p, salida)
        "SUBSCRIBE" -> suscribir(p, salida)
        "UNSUBSCRIBE" -> cancelar(p, salida)
        else -> enviar(p, salida, 501, "text/plain", "No implementado")
    }

    private fun recurso(p: Peticion, salida: OutputStream) {
        when (p.ruta) {
            "/device.xml" -> enviar(p, salida, 200, """text/xml; charset="utf-8"""",
                deviceXml(nombre(), udn, baseUrl()))
            "/scpd/avt.xml" -> enviar(p, salida, 200, """text/xml; charset="utf-8"""", SCPD_AVT)
            "/scpd/rcs.xml" -> enviar(p, salida, 200, """text/xml; charset="utf-8"""", SCPD_RCS)
            "/scpd/cms.xml" -> enviar(p, salida, 200, """text/xml; charset="utf-8"""", SCPD_CMS)
            "/icon.png" -> enviar(p, salida, 200, "image/png", ICONO)
            "/", "/status" -> enviar(p, salida, 200, "text/plain; charset=utf-8", renderer.estadoTexto())
            else -> enviar(p, salida, 404, "text/plain", "No encontrado")
        }
    }

    private fun control(p: Peticion, salida: OutputStream) {
        val clave = p.ruta.substringAfterLast('/')
        val servicio = SERVICIOS[clave]
        if (!p.ruta.startsWith("/ctrl/") || servicio == null) {
            return enviar(p, salida, 404, "text/plain", "No encontrado")
        }
        val (nombreServicio, urn) = servicio
        val peticion = parsearSoap(p.cabeceras["soapaction"], p.cuerpo)
        try {
            val valores = when (nombreServicio) {
                // La IP del controlador distingue el compartir pantalla —el
                // emisor sirviéndose a sí mismo— de un cast de contenido ajeno.
                "AVTransport" -> renderer.avt(peticion.accion, peticion.argumentos, p.ipCliente)
                "RenderingControl" -> renderer.rcs(peticion.accion, peticion.argumentos)
                else -> renderer.cms(peticion.accion)
            } ?: return enviar(p, salida, 500, """text/xml; charset="utf-8"""", faultSoap(401))
            enviar(p, salida, 200, """text/xml; charset="utf-8"""",
                respuestaSoap(peticion.accion, urn, valores))
        } catch (e: FalloUpnp) {
            enviar(p, salida, 500, """text/xml; charset="utf-8"""",
                faultSoap(e.codigo, e.descripcion))
        } catch (e: Exception) {
            Log.w(TAG, "${peticion.accion}: ${e.message}")
            enviar(p, salida, 500, """text/xml; charset="utf-8"""",
                faultSoap(501, e.message ?: "Action Failed"))
        }
    }

    private fun suscribir(p: Peticion, salida: OutputStream) {
        val clave = p.ruta.substringAfterLast('/')
        val servicio = SERVICIOS[clave]
        if (!p.ruta.startsWith("/evt/") || servicio == null) {
            return enviar(p, salida, 404, "text/plain", "No encontrado")
        }
        val gena = eventos.getValue(servicio.first)
        val sid = p.cabeceras["sid"]
        val resultado = if (sid != null) {
            gena.renovar(sid, p.cabeceras["timeout"])
        } else {
            gena.suscribir(p.cabeceras["callback"], p.cabeceras["timeout"],
                renderer.estadoInicial(servicio.first))
        }
        if (resultado == null) {
            return enviar(p, salida, 412, "text/plain", "Precondition Failed")
        }
        enviar(p, salida, 200, "text/plain", ByteArray(0), mapOf(
            "SID" to resultado.first,
            "TIMEOUT" to "Second-${resultado.second}",
        ))
    }

    private fun cancelar(p: Peticion, salida: OutputStream) {
        val servicio = SERVICIOS[p.ruta.substringAfterLast('/')]
            ?: return enviar(p, salida, 404, "text/plain", "No encontrado")
        val ok = eventos.getValue(servicio.first).cancelar(p.cabeceras["sid"])
        enviar(p, salida, if (ok) 200 else 412, "text/plain", ByteArray(0))
    }

    private fun enviar(
        p: Peticion,
        salida: OutputStream,
        codigo: Int,
        tipo: String,
        cuerpo: String,
        extra: Map<String, String> = emptyMap(),
    ) = enviar(p, salida, codigo, tipo, cuerpo.toByteArray(), extra)

    private fun enviar(
        p: Peticion,
        salida: OutputStream,
        codigo: Int,
        tipo: String,
        cuerpo: ByteArray,
        extra: Map<String, String> = emptyMap(),
    ) {
        val cabecera = buildString {
            append("HTTP/1.1 $codigo ${textoDe(codigo)}\r\n")
            append("Content-Type: $tipo\r\n")
            append("Content-Length: ${cuerpo.size}\r\n")
            append("Server: Android/1.0 UPnP/1.0 cast-dlna-tv/1.0\r\n")
            extra.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Connection: keep-alive\r\n\r\n")
        }
        salida.write(cabecera.toByteArray())
        // Una respuesta a HEAD lleva Content-Length pero nunca cuerpo: con
        // keep-alive el cliente leería ese cuerpo como el principio de la
        // respuesta siguiente y la conexión se descarrila. Hay controladores
        // que comprueban con HEAD que el receptor sigue vivo en cada vuelta de
        // su sondeo, y ahí esto es la diferencia entre funcionar y desaparecer.
        if (p.metodo != "HEAD" && cuerpo.isNotEmpty()) salida.write(cuerpo)
        salida.flush()
    }

    private fun textoDe(codigo: Int) = when (codigo) {
        200 -> "OK"
        404 -> "Not Found"
        412 -> "Precondition Failed"
        500 -> "Internal Server Error"
        501 -> "Not Implemented"
        else -> "OK"
    }

    private companion object {
        /** Lo que cabe leer de un vistazo; más es ruido de sondeo repetido. */
        const val MAX_ANOTADAS = 50
        val SERVICIOS = mapOf(
            "avt" to ("AVTransport" to Servicios.AVT),
            "rcs" to ("RenderingControl" to Servicios.RCS),
            "cms" to ("ConnectionManager" to Servicios.CMS),
        )
    }
}
