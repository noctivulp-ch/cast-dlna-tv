package app.castdlna.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.castdlna.tv.player.ReproductorExo
import app.castdlna.tv.upnp.Gena
import app.castdlna.tv.upnp.Renderer
import app.castdlna.tv.upnp.ServidorHttp
import app.castdlna.tv.upnp.Ssdp
import java.net.Inet4Address
import java.util.UUID
import kotlin.concurrent.thread

private const val TAG = "Receptor"
const val PUERTO = 8201

/**
 * El receptor entero: descubrimiento, servidor de control y reproductor.
 *
 * Vive en un objeto porque la actividad se destruye al cambiar de aplicación
 * en el televisor y el receptor tiene que seguir anunciándose y respondiendo.
 */
@OptIn(UnstableApi::class)
object Receptor {

    @Volatile var reproductor: ReproductorExo? = null; private set
    @Volatile var nombre: String = "cast-dlna"; private set
    @Volatile var enEspera: Boolean = true; private set

    private var ssdp: Ssdp? = null
    private var servidor: ServidorHttp? = null
    private var renderer: Renderer? = null
    private var iniciado = false

    /**
     * El identificador tiene que sobrevivir a los reinicios: los controladores
     * recuerdan el dispositivo por él, y uno nuevo en cada arranque aparece
     * como un receptor distinto en la lista del móvil.
     */
    private fun udnDe(contexto: Context): String {
        val prefs = contexto.getSharedPreferences("receptor", Context.MODE_PRIVATE)
        prefs.getString("udn", null)?.let { return it }
        val nuevo = "uuid:" + UUID.randomUUID()
        prefs.edit().putString("udn", nuevo).apply()
        return nuevo
    }

    @Synchronized
    fun arrancar(contexto: Context) {
        if (iniciado) return
        iniciado = true

        val app = contexto.applicationContext
        nombre = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android TV"

        val exo = ReproductorExo(app) { alCambiar() }
        reproductor = exo

        val eventos = mapOf(
            "AVTransport" to Gena("AVTransport"),
            "RenderingControl" to Gena("RenderingControl"),
            "ConnectionManager" to Gena("ConnectionManager"),
        )
        val r = Renderer(exo, eventos)
        renderer = r

        val udn = udnDe(app)
        servidor = ServidorHttp(
            puerto = PUERTO,
            nombre = { nombre },
            udn = udn,
            baseUrl = { "http://${ipLocal(app)}:$PUERTO" },
            renderer = r,
            eventos = eventos,
        ).also { it.arrancar() }

        ssdp = Ssdp(
            wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager,
            udn = udn,
            puerto = PUERTO,
            ipActual = { ipLocal(app) },
        ).also { it.arrancar() }

        Log.i(TAG, "receptor en marcha: $nombre — http://${ipLocal(app)}:$PUERTO")

        thread(isDaemon = true, name = "receptor-latido") {
            while (true) {
                Thread.sleep(1000)
                if (reproductor?.estado in ESTADOS_ACTIVOS) renderer?.avisarCambio()
            }
        }
    }

    fun parar() {
        ssdp?.parar()
        servidor?.parar()
        reproductor?.soltar()
        iniciado = false
    }

    private fun alCambiar() {
        enEspera = reproductor?.estado in setOf("NO_MEDIA_PRESENT", "STOPPED", null)
        renderer?.avisarCambio()
    }

    /** La dirección por la que se llega a la red local, sin depender del wifi. */
    fun ipLocal(contexto: Context): String? {
        val cm = contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val red = cm.activeNetwork ?: return null
        val enlaces = cm.getLinkProperties(red) ?: return null
        return enlaces.linkAddresses
            .map(LinkAddress::getAddress)
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    private val ESTADOS_ACTIVOS = setOf("PLAYING", "TRANSITIONING")
}
