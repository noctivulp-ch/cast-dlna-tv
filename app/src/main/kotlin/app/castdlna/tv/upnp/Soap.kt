package app.castdlna.tv.upnp

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** Error UPnP con su código, para responder un fault en condiciones. */
class FalloUpnp(val codigo: Int, val descripcion: String = ERRORES[codigo] ?: "Action Failed") :
    Exception(descripcion)

val ERRORES = mapOf(
    401 to "Invalid Action",
    402 to "Invalid Args",
    501 to "Action Failed",
    600 to "Argument Value Invalid",
    601 to "Argument Value Out of Range",
    602 to "Optional Action Not Implemented",
    701 to "Transition not available",
    710 to "Seek mode not supported",
    711 to "Illegal seek target",
    712 to "Play mode not supported",
    714 to "Illegal MIME-type",
    716 to "Resource not found",
)

data class PeticionSoap(val accion: String, val argumentos: Map<String, String>)

/**
 * Saca la acción y sus argumentos de una petición de control.
 *
 * El nombre viene de la cabecera `SOAPAction`, y del cuerpo solo se recogen
 * los hijos: el elemento que se llama igual que la acción es su envoltorio,
 * no un argumento.
 */
fun parsearSoap(cabeceraAccion: String?, cuerpo: String): PeticionSoap {
    val accion = Regex("""[#"]([\w:.\-]+)"?\s*$""")
        .find(cabeceraAccion.orEmpty())
        ?.groupValues?.get(1)
        ?.substringAfterLast('#')
        ?.trim('"')
        .orEmpty()

    val argumentos = mutableMapOf<String, String>()
    try {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            .newPullParser()
        parser.setInput(StringReader(cuerpo))
        var etiqueta: String? = null
        var evento = parser.eventType
        while (evento != XmlPullParser.END_DOCUMENT) {
            when (evento) {
                XmlPullParser.START_TAG -> etiqueta = parser.name
                XmlPullParser.TEXT -> {
                    val nombre = etiqueta
                    if (nombre != null && nombre !in ENVOLTORIOS && nombre != accion) {
                        val texto = parser.text
                        if (!texto.isNullOrBlank() || nombre !in argumentos) {
                            argumentos[nombre] = texto.orEmpty()
                        }
                    }
                }
                XmlPullParser.END_TAG -> etiqueta = null
            }
            evento = parser.next()
        }
    } catch (e: Exception) {
        return PeticionSoap(accion, argumentosPorRegex(cuerpo))
    }
    return PeticionSoap(accion, argumentos)
}

private val ENVOLTORIOS = setOf("Envelope", "Body", "Header")

private fun argumentosPorRegex(cuerpo: String): Map<String, String> =
    Regex("""<(?:\w+:)?(\w+)[^>]*>(.*?)</(?:\w+:)?\1>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(cuerpo)
        .filter { it.groupValues[1] !in ENVOLTORIOS }
        .associate { it.groupValues[1] to it.groupValues[2] }

fun respuestaSoap(accion: String, servicio: String, valores: Map<String, Any> = emptyMap()): String {
    val cuerpo = valores.entries.joinToString("") { (k, v) -> "<$k>${escapar(v.toString())}</$k>" }
    return """<?xml version="1.0" encoding="utf-8"?>""" +
        """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
        """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
        """<s:Body><u:${accion}Response xmlns:u="$servicio">$cuerpo""" +
        """</u:${accion}Response></s:Body></s:Envelope>"""
}

fun faultSoap(codigo: Int, descripcion: String = ERRORES[codigo] ?: "Action Failed"): String =
    """<?xml version="1.0" encoding="utf-8"?>""" +
        """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
        """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
        """<s:Body><s:Fault><faultcode>s:Client</faultcode>""" +
        """<faultstring>UPnPError</faultstring><detail>""" +
        """<UPnPError xmlns="urn:schemas-upnp-org:control-1-0">""" +
        """<errorCode>$codigo</errorCode>""" +
        """<errorDescription>${escapar(descripcion)}</errorDescription>""" +
        """</UPnPError></detail></s:Fault></s:Body></s:Envelope>"""

fun escapar(texto: String): String = texto
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

fun desescapar(texto: String?): String {
    if (texto.isNullOrEmpty()) return ""
    return texto
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&#34;", "\"")
        .replace("&amp;", "&")
}
