package app.castdlna.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import app.castdlna.tv.player.ReproductorExo
import app.castdlna.tv.upnp.Gena
import app.castdlna.tv.upnp.Renderer
import app.castdlna.tv.upnp.ServidorHttp
import app.castdlna.tv.ui.EstadoPantalla
import app.castdlna.tv.ui.faseDe
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

    /** Lo que la pantalla observa; se recompone sola al cambiar. */
    var estado by mutableStateOf(EstadoPantalla())
        private set

    private var direccion: String = ""

    private var ssdp: Ssdp? = null
    private var servidor: ServidorHttp? = null
    private var renderer: Renderer? = null
    private var eventos: Map<String, Gena> = emptyMap()
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

        eventos = mapOf(
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

        direccion = "http://${ipLocal(app)}:$PUERTO"
        Log.i(TAG, "receptor en marcha: $nombre — $direccion")
        alCambiar()

        // Late una vez por segundo mientras hay algo en marcha: los avisos
        // solo salen cuando cambia el estado, y la posición cambia sola.
        // Cada vuelta va protegida: si una excepción escapa del hilo se lleva
        // el proceso entero, y no vale la pena tumbar el receptor porque un
        // aviso de estado no saliera.
        thread(isDaemon = true, name = "receptor-latido") {
            while (true) {
                Thread.sleep(1000)
                try {
                    if (reproductor?.estado in ESTADOS_ACTIVOS) alCambiar()
                    else refrescarPantalla()
                } catch (e: Exception) {
                    Log.w(TAG, "latido: ${e.message}")
                }
            }
        }
    }

    /** Apaga el receptor entero; no confundir con parar la reproducción. */
    fun apagar() {
        ssdp?.parar()
        servidor?.parar()
        reproductor?.soltar()
        iniciado = false
    }

    private fun alCambiar() {
        refrescarPantalla()
        renderer?.avisarCambio()
    }

    /**
     * Solo la pantalla, sin avisar a nadie.
     *
     * Va aparte porque mientras se espera también cambia algo que merece verse
     * —quién se ha suscrito— y no tendría sentido mandar un aviso de estado a
     * los controladores cada segundo para reflejarlo.
     */
    private fun refrescarPantalla() {
        val p = reproductor
        val r = renderer
        if (p != null) {
            estado = EstadoPantalla(
                fase = faseDe(p.estado),
                titulo = r?.tituloActual().orEmpty(),
                posicionSeg = p.posicionSeg,
                duracionSeg = p.duracionSeg,
                volumen = p.volumen,
                silenciado = p.silenciado,
                esImagen = p.mostrandoImagen,
                esDirecto = r?.esDirecto() ?: false,
                nombreReceptor = nombre,
                direccion = direccion,
                // Basta con los de AVTransport: un controlador que va a mandar
                // algo se suscribe siempre a ése. Los de volumen los pide solo
                // parte de ellos, y contarlos aparte solo daría duplicados.
                controladores = eventos["AVTransport"]?.suscriptores.orEmpty(),
            )
        }
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

    fun reproducirOPausar() {
        val p = reproductor ?: return
        if (p.estado == "PLAYING") p.pausar() else p.reproducir()
    }

    fun buscarFraccion(fraccion: Float) {
        val p = reproductor ?: return
        val duracion = p.duracionSeg
        if (duracion > 0) p.buscar((fraccion.coerceIn(0f, 1f) * duracion))
    }

    fun parar() {
        reproductor?.parar()
    }

    private val ESTADOS_ACTIVOS = setOf("PLAYING", "TRANSITIONING")
}
