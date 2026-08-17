package app.castdlna.tv.upnp

import android.util.Log
import java.net.URI

private const val TAG = "Renderer"

/** Lo que el reproductor tiene que saber hacer para servir al receptor. */
interface Reproductor {
    val estado: String              // PLAYING, PAUSED_PLAYBACK, STOPPED, …
    val posicionSeg: Double
    val duracionSeg: Double
    val volumen: Int                // 0..100
    val silenciado: Boolean
    val pistas: Int
    val pistaActual: Int

    fun cargar(uri: String, media: Media)
    fun encolar(uri: String, media: Media)
    fun reproducir()
    fun pausar()
    fun parar()
    fun buscar(segundos: Double)
    fun irAPista(indice: Int)
    fun siguiente()
    fun anterior()
    fun ajustarVolumen(nivel: Int)
    fun silenciar(activo: Boolean)
    fun modoDeLista(modo: String)
}

/** Qué hacer con el volumen que manda el controlador. */
enum class VolumenRemoto { AUTO, SIEMPRE, NUNCA }

/**
 * Implementación de las acciones de los tres servicios.
 *
 * Es el equivalente del renderer del receptor de escritorio, con las mismas
 * decisiones: se aceptan acciones que luego se descartan en vez de devolver un
 * fault —rechazarlas hace que el controlador dé el receptor por roto—, y el
 * volumen se aplica o no según de dónde venga el contenido.
 */
class Renderer(
    private val reproductor: Reproductor,
    private val eventos: Map<String, Gena>,
    var volumenRemoto: VolumenRemoto = VolumenRemoto.AUTO,
) {
    var uri: String = ""; private set
    var metadata: String = ""; private set
    private var siguienteUri = ""
    private var siguienteMetadata = ""
    private var media = Media()
    private var modoLista = "NORMAL"
    private var compartirPantalla = false
    private var ipControlador: String? = null

    private val ultimoAvt = mutableMapOf<String, Any>()
    private val ultimoRcs = mutableMapOf<String, Any>()

    // ── Estado que se publica ──────────────────────────────────────────────
    fun estadoAvt(): Map<String, Any> = mapOf(
        "TransportState" to reproductor.estado,
        "TransportStatus" to "OK",
        "CurrentPlayMode" to modoLista,
        "TransportPlaySpeed" to "1",
        "NumberOfTracks" to if (uri.isEmpty()) 0 else maxOf(1, reproductor.pistas),
        "CurrentTrack" to if (uri.isEmpty()) 0 else reproductor.pistaActual + 1,
        "CurrentTrackDuration" to duracionTexto(),
        "CurrentMediaDuration" to duracionTexto(),
        "CurrentTrackURI" to uri,
        "CurrentTrackMetaData" to metadata,
        "AVTransportURI" to uri,
        "AVTransportURIMetaData" to metadata,
        "NextAVTransportURI" to siguienteUri,
        "NextAVTransportURIMetaData" to siguienteMetadata,
        "PlaybackStorageMedium" to if (uri.isEmpty()) "NONE" else "NETWORK",
        "RecordStorageMedium" to "NOT_IMPLEMENTED",
        "RecordMediumWriteStatus" to "NOT_IMPLEMENTED",
        "CurrentRecordQualityMode" to "NOT_IMPLEMENTED",
        "PossibleRecordQualityModes" to "NOT_IMPLEMENTED",
        "CurrentTransportActions" to accionesPosibles(),
        "RelativeTimePosition" to posicionTexto(),
        "AbsoluteTimePosition" to posicionTexto(),
    )

    fun estadoRcs(): Map<String, Any> = mapOf(
        "Volume" to reproductor.volumen,
        "Mute" to if (reproductor.silenciado) 1 else 0,
        "PresetNameList" to "FactoryDefaults",
    )

    fun estadoInicial(servicio: String): Map<String, Any> = when (servicio) {
        "AVTransport" -> estadoAvt()
        "RenderingControl" -> estadoRcs()
        else -> mapOf(
            "SourceProtocolInfo" to "",
            "SinkProtocolInfo" to SINK_PROTOCOL_INFO,
            "CurrentConnectionIDs" to "0",
        )
    }

    /** Publica solo lo que cambió: un aviso por cada campo sería ruido. */
    fun avisarCambio() {
        publicar("AVTransport", estadoAvt(), ultimoAvt)
        publicar("RenderingControl", estadoRcs(), ultimoRcs)
    }

    private fun publicar(servicio: String, estado: Map<String, Any>, cache: MutableMap<String, Any>) {
        val delta = estado.filter { (k, v) -> cache[k] != v }
        cache.putAll(estado)
        if (delta.isNotEmpty()) eventos.getValue(servicio).publicar(delta)
    }

    fun estadoTexto(): String = buildString {
        appendLine("cast-dlna-tv")
        appendLine("estado : ${reproductor.estado}")
        appendLine("uri    : $uri")
        appendLine("pos    : ${posicionTexto()} / ${duracionTexto()}")
        appendLine("volumen: ${reproductor.volumen}${if (reproductor.silenciado) " (mute)" else ""}")
    }

    // ── AVTransport ────────────────────────────────────────────────────────
    fun avt(accion: String, args: Map<String, String>, ipCliente: String): Map<String, Any>? {
        return when (accion) {
            "SetAVTransportURI" -> setUri(args, ipCliente)
            "SetNextAVTransportURI" -> setSiguienteUri(args)
            "GetMediaInfo" -> infoMedio()
            "GetMediaInfo_Ext" -> buildMap {
                put("CurrentType", when {
                    uri.isEmpty() -> "NO_MEDIA"
                    media.directo -> "TRACK_UNAWARE"
                    else -> "TRACK_AWARE"
                })
                putAll(infoMedio())
            }
            "GetTransportInfo" -> mapOf(
                "CurrentTransportState" to reproductor.estado,
                "CurrentTransportStatus" to "OK",
                "CurrentSpeed" to "1",
            )
            "GetPositionInfo" -> mapOf(
                "Track" to if (uri.isEmpty()) 0 else reproductor.pistaActual + 1,
                "TrackDuration" to duracionTexto(),
                "TrackMetaData" to metadata,
                "TrackURI" to uri,
                "RelTime" to posicionTexto(),
                "AbsTime" to posicionTexto(),
                "RelCount" to contador(),
                "AbsCount" to contador(),
            )
            "GetDeviceCapabilities" -> mapOf(
                "PlayMedia" to "NONE,NETWORK,HDD,UNKNOWN",
                "RecMedia" to "NOT_IMPLEMENTED",
                "RecQualityModes" to "NOT_IMPLEMENTED",
            )
            "GetTransportSettings" -> mapOf(
                "PlayMode" to modoLista,
                "RecQualityMode" to "NOT_IMPLEMENTED",
            )
            "GetCurrentTransportActions" -> mapOf("Actions" to accionesPosibles())
            "X_DLNA_GetBytePositionInfo" -> mapOf(
                "TrackSize" to "0", "RelByte" to "0", "AbsByte" to "0")
            "Play" -> { reproductor.reproducir(); avisarCambio(); emptyMap() }
            "Pause" -> { reproductor.pausar(); avisarCambio(); emptyMap() }
            "Stop" -> { reproductor.parar(); avisarCambio(); emptyMap() }
            "Seek" -> buscar(args)
            "Next" -> { reproductor.siguiente(); avisarCambio(); emptyMap() }
            "Previous" -> { reproductor.anterior(); avisarCambio(); emptyMap() }
            "SetPlayMode" -> ponerModo(args)
            else -> null
        }
    }

    private fun setUri(args: Map<String, String>, ipCliente: String): Map<String, Any> {
        val nueva = desescapar(args["CurrentURI"]).trim()
        if (nueva.isEmpty()) {
            uri = ""; metadata = ""; siguienteUri = ""; siguienteMetadata = ""
            media = Media()
            reproductor.parar()
            avisarCambio()
            return emptyMap()
        }
        val crudo = desescapar(args["CurrentURIMetaData"]).trim()
        media = parsearDidl(crudo, nueva)
        uri = nueva
        metadata = args["CurrentURIMetaData"].orEmpty()
        ipControlador = ipCliente
        compartirPantalla = esCompartirPantalla(nueva, media, ipCliente)

        Log.i(TAG, "▶ ${media.titulo.ifEmpty { "(sin título)" }} — $nueva")
        reproductor.cargar(nueva, media)
        avisarCambio()
        return emptyMap()
    }

    private fun setSiguienteUri(args: Map<String, String>): Map<String, Any> {
        siguienteUri = desescapar(args["NextURI"]).trim()
        siguienteMetadata = args["NextURIMetaData"].orEmpty()
        if (siguienteUri.isNotEmpty()) {
            reproductor.encolar(siguienteUri, parsearDidl(desescapar(siguienteMetadata), siguienteUri))
        }
        avisarCambio()
        return emptyMap()
    }

    private fun infoMedio(): Map<String, Any> = mapOf(
        "NrTracks" to if (uri.isEmpty()) 0 else maxOf(1, reproductor.pistas),
        "MediaDuration" to duracionTexto(),
        "CurrentURI" to uri,
        "CurrentURIMetaData" to metadata,
        "NextURI" to siguienteUri,
        "NextURIMetaData" to siguienteMetadata,
        "PlayMedium" to if (uri.isEmpty()) "NONE" else "NETWORK",
        "RecordMedium" to "NOT_IMPLEMENTED",
        "WriteStatus" to "NOT_IMPLEMENTED",
    )

    private fun buscar(args: Map<String, String>): Map<String, Any> {
        val unidad = (args["Unit"] ?: "REL_TIME").uppercase()
        val objetivo = args["Target"].orEmpty().trim()
        when (unidad) {
            "REL_TIME", "ABS_TIME" -> {
                val segundos = parsearDuracion(objetivo)
                if (segundos == 0.0 && !objetivo.startsWith("0")) throw FalloUpnp(711)
                reproductor.buscar(segundos)
            }
            "TRACK_NR" -> {
                val indice = objetivo.toIntOrNull() ?: throw FalloUpnp(711)
                reproductor.irAPista((indice - 1).coerceAtLeast(0))
            }
            else -> throw FalloUpnp(710)
        }
        avisarCambio()
        return emptyMap()
    }

    private fun ponerModo(args: Map<String, String>): Map<String, Any> {
        val modo = (args["NewPlayMode"] ?: "NORMAL").uppercase()
        if (modo !in setOf("NORMAL", "REPEAT_ONE", "REPEAT_ALL", "SHUFFLE", "RANDOM")) {
            throw FalloUpnp(712)
        }
        modoLista = modo
        reproductor.modoDeLista(modo)
        avisarCambio()
        return emptyMap()
    }

    // ── RenderingControl ───────────────────────────────────────────────────
    fun rcs(accion: String, args: Map<String, String>): Map<String, Any>? = when (accion) {
        "ListPresets" -> mapOf("CurrentPresetNameList" to "FactoryDefaults")
        "SelectPreset" -> {
            if (args["PresetName"] == "FactoryDefaults") {
                reproductor.ajustarVolumen(100)
                reproductor.silenciar(false)
            }
            emptyMap()
        }
        "GetVolume" -> mapOf("CurrentVolume" to reproductor.volumen)
        "SetVolume" -> ponerVolumen(args)
        "GetVolumeDB" -> mapOf("CurrentVolume" to aDecibelios(reproductor.volumen))
        "SetVolumeDB" -> {
            val db = args["DesiredVolume"]?.toIntOrNull() ?: throw FalloUpnp(600)
            if (volumenEsRemoto()) reproductor.ajustarVolumen(deDecibelios(db))
            emptyMap()
        }
        "GetVolumeDBRange" -> mapOf("MinValue" to -10240, "MaxValue" to 0)
        "GetMute" -> mapOf("CurrentMute" to if (reproductor.silenciado) 1 else 0)
        "SetMute" -> {
            val quiere = (args["DesiredMute"] ?: "0").lowercase() in setOf("1", "true", "yes")
            if (volumenEsRemoto()) {
                reproductor.silenciar(quiere)
                avisarCambio()
            }
            emptyMap()
        }
        else -> null
    }

    private fun ponerVolumen(args: Map<String, String>): Map<String, Any> {
        val nivel = args["DesiredVolume"]?.toIntOrNull() ?: throw FalloUpnp(600)
        if (nivel !in 0..100) throw FalloUpnp(601)
        // Se acepta y se descarta en vez de devolver un fault: rechazarlo hace
        // que el controlador dé el receptor por roto. `GetVolume` sigue
        // diciendo el valor real, así que su barra vuelve a su sitio sola.
        if (!volumenEsRemoto()) {
            Log.i(TAG, "SetVolume($nivel) ignorado (compartir pantalla)")
            return emptyMap()
        }
        reproductor.ajustarVolumen(nivel)
        avisarCambio()
        return emptyMap()
    }

    private fun volumenEsRemoto(): Boolean = when (volumenRemoto) {
        VolumenRemoto.SIEMPRE -> true
        VolumenRemoto.NUNCA -> false
        VolumenRemoto.AUTO -> !compartirPantalla
    }

    // ── ConnectionManager ──────────────────────────────────────────────────
    fun cms(accion: String): Map<String, Any>? = when (accion) {
        "GetProtocolInfo" -> mapOf("Source" to "", "Sink" to SINK_PROTOCOL_INFO)
        "GetCurrentConnectionIDs" -> mapOf("ConnectionIDs" to "0")
        "GetCurrentConnectionInfo" -> mapOf(
            "RcsID" to 0,
            "AVTransportID" to 0,
            "ProtocolInfo" to "http-get:*:*:*",
            "PeerConnectionManager" to "",
            "PeerConnectionID" to -1,
            "Direction" to "Input",
            "Status" to "OK",
        )
        else -> null
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────
    /**
     * Compartir pantalla es el controlador sirviéndose a sí mismo un flujo en
     * directo. Hacen falta las dos señales: por MIME sola se confundiría con
     * televisión en directo, y por origen sola con el servidor local que monta
     * Web Video Caster para servir un mp4 desde el propio teléfono.
     */
    private fun esCompartirPantalla(uri: String, media: Media, ipCliente: String): Boolean {
        if (!media.directo) return false
        val host = runCatching { URI(uri).host }.getOrNull() ?: return false
        return host == ipCliente
    }

    private fun accionesPosibles(): String {
        val acciones = mutableListOf("Play", "Stop", "Seek", "X_DLNA_SeekTime")
        if (reproductor.estado == "PLAYING") acciones += "Pause"
        if (reproductor.pistas > 1) { acciones += "Next"; acciones += "Previous" }
        return acciones.distinct().joinToString(",")
    }

    private fun duracionTexto(): String {
        val d = if (reproductor.duracionSeg > 0) reproductor.duracionSeg else media.duracion
        return if (d > 0) formatearDuracion(d) else "00:00:00"
    }

    private fun posicionTexto() = formatearDuracion(reproductor.posicionSeg)

    /** Por convención se manda el máximo cuando el contador no aplica. */
    private fun contador(): Int =
        reproductor.posicionSeg.toInt().takeIf { it > 0 } ?: 2147483647

    private fun aDecibelios(volumen: Int): Int =
        if (volumen <= 0) -10240 else Math.round(2000 * (volumen / 100.0 - 1)).toInt()

    private fun deDecibelios(db: Int): Int =
        Math.round((db / 2000.0 + 1) * 100).toInt().coerceIn(0, 100)
}
