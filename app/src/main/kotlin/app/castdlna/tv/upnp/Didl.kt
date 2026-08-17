package app.castdlna.tv.upnp

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Metadata DIDL-Lite del elemento que se va a reproducir.
 *
 * Los controladores no coinciden en cómo declaran los subtítulos: Samsung usa
 * `sec:CaptionInfo(Ex)`, BubbleUPnP y Web Video Caster usan `pv:subtitleFileUri`
 * o un `<res>` extra con protocolInfo de texto, y algunos servidores solo lo
 * exponen en la cabecera `CaptionInfo.sec` de la propia respuesta HTTP. Se
 * atienden las cuatro vías.
 */
data class Media(
    val titulo: String = "",
    val duracion: Double = 0.0,
    val subtitulos: List<String> = emptyList(),
    val cabeceras: Map<String, String> = emptyMap(),
    val mime: String = "",
    val directo: Boolean = false,
    val esImagen: Boolean = false,
)

private val MIMES_SUBTITULO = listOf(
    "text/srt", "text/vtt", "text/plain", "application/x-subrip",
    "text/x-ssa", "application/x-ssa", "text/ass", "smi/caption",
    "application/ttml+xml",
)

private val EXT_SUBTITULO = listOf(
    ".srt", ".vtt", ".ass", ".ssa", ".sub", ".smi", ".ttml", ".dfxp")

private val EXT_IMAGEN = listOf(
    ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff", ".heic")

/**
 * MPEG-TS por HTTP es lo que emiten las funciones de compartir pantalla. Es un
 * flujo sin fin ni duración, y conviene saberlo antes de configurar el
 * reproductor.
 */
private val MIMES_DIRECTO = listOf(
    "video/mp2t", "video/vnd.dlna.mpeg-tts", "video/mpeg-tts",
    "application/x-mpegurl", "application/vnd.apple.mpegurl",
)

fun parsearDidl(crudo: String, uri: String): Media {
    if (crudo.isBlank()) return Media(cabeceras = cabecerasDeUri(uri), esImagen = pareceImagen(uri))

    var titulo = ""
    var duracion = 0.0
    var mime = ""
    var directo = false
    var imagen = false
    val subtitulos = mutableListOf<String>()

    try {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            .newPullParser()
        parser.setInput(StringReader(crudo))
        var evento = parser.eventType
        var etiqueta: String? = null
        var protocolo = ""
        var duracionRes = ""
        var tipoSub = false

        while (evento != XmlPullParser.END_DOCUMENT) {
            when (evento) {
                XmlPullParser.START_TAG -> {
                    etiqueta = parser.name.lowercase()
                    protocolo = ""
                    duracionRes = ""
                    tipoSub = false
                    for (i in 0 until parser.attributeCount) {
                        val attr = parser.getAttributeName(i).lowercase()
                        val valor = parser.getAttributeValue(i)
                        when (attr) {
                            "protocolinfo" -> protocolo = valor.lowercase()
                            "duration" -> duracionRes = valor
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val texto = parser.text?.trim().orEmpty()
                    if (texto.isNotEmpty()) when (etiqueta) {
                        "title" -> if (titulo.isEmpty()) titulo = texto
                        "class" -> if (texto.lowercase().contains("imageitem")) imagen = true
                        "captioninfo", "captioninfoex", "subtitlefileuri" -> subtitulos += texto
                        "res" -> {
                            val esSub = MIMES_SUBTITULO.any { protocolo.contains(it) } ||
                                EXT_SUBTITULO.any { texto.lowercase().substringBefore('?').endsWith(it) }
                            if (esSub) {
                                subtitulos += texto
                            } else {
                                if (mime.isEmpty()) {
                                    mime = protocolo.split(":").getOrNull(2).orEmpty()
                                }
                                if (duracion == 0.0) duracion = parsearDuracion(duracionRes)
                                if (MIMES_DIRECTO.any { protocolo.contains(it) }) directo = true
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> etiqueta = null
            }
            evento = parser.next()
        }
    } catch (e: Exception) {
        // Un DIDL mal formado no debe impedir reproducir: la URI ya vino aparte.
    }

    if (mime.startsWith("image/") || pareceImagen(uri)) imagen = true

    return Media(
        titulo = titulo,
        duracion = duracion,
        subtitulos = subtitulos.filter { it.startsWith("http") }.distinct(),
        cabeceras = cabecerasDeUri(uri),
        mime = mime,
        directo = directo,
        esImagen = imagen,
    )
}

private fun pareceImagen(uri: String) =
    EXT_IMAGEN.any { uri.lowercase().substringBefore('?').endsWith(it) }

/**
 * Si el controlador no manda `Referer`, se deriva del dominio del propio
 * recurso: sin él muchos sitios responden 403 al reproducir desde fuera.
 */
private fun cabecerasDeUri(uri: String): Map<String, String> {
    if (!uri.startsWith("http")) return emptyMap()
    val referer = Regex("""[?&]referer=([^&]+)""", RegexOption.IGNORE_CASE).find(uri)
        ?.groupValues?.get(1)
    if (referer != null) return mapOf("Referer" to referer)
    val corte = uri.indexOf('/', uri.indexOf("//") + 2)
    val base = if (corte > 0) uri.substring(0, corte) else uri
    return mapOf("Referer" to "$base/")
}

fun parsearDuracion(texto: String?): Double {
    if (texto.isNullOrBlank()) return 0.0
    val m = Regex("""^\s*(?:(\d+):)?(\d{1,2}):(\d{2})(?:\.(\d+))?""").find(texto) ?: return 0.0
    val (h, min, seg, frac) = m.destructured
    var total = (h.toIntOrNull() ?: 0) * 3600.0 + min.toInt() * 60 + seg.toInt()
    if (frac.isNotEmpty()) total += "0.$frac".toDouble()
    return total
}

fun formatearDuracion(segundos: Double): String {
    val s = segundos.toInt().coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
}
