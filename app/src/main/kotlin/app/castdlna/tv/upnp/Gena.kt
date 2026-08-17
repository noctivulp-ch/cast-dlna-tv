package app.castdlna.tv.upnp

import android.util.Log
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private const val TAG = "Gena"
private const val TIMEOUT_POR_DEFECTO = 1800

/**
 * Suscripciones de eventos y `NOTIFY` con `LastChange`.
 *
 * Sin esto el controlador no refresca la barra de progreso ni el botón de
 * reproducción salvo que se dedique a preguntar; varios dependen de los avisos
 * para dar la sesión por viva.
 */
class Gena(private val servicio: String) {

    private class Suscripcion(
        val sid: String,
        val callbacks: List<String>,
        @Volatile var expira: Long,
        @Volatile var seq: Long = 0,
    )

    private val suscripciones = ConcurrentHashMap<String, Suscripcion>()

    init {
        thread(name = "gena-caducidad", isDaemon = true) {
            while (true) {
                Thread.sleep(30_000)
                val ahora = System.currentTimeMillis()
                suscripciones.entries.removeIf { it.value.expira < ahora }
            }
        }
    }

    fun suscribir(callback: String?, timeout: String?, estadoInicial: Map<String, Any>): Pair<String, Int>? {
        val destinos = Regex("""<([^>]+)>""").findAll(callback.orEmpty())
            .map { it.groupValues[1] }.toList()
        if (destinos.isEmpty()) return null
        val segundos = segundosDe(timeout)
        val sid = "uuid:" + UUID.randomUUID()
        val s = Suscripcion(sid, destinos, System.currentTimeMillis() + segundos * 1000L)
        suscripciones[sid] = s
        thread(isDaemon = true) { enviar(s, estadoInicial) }
        return sid to segundos
    }

    fun renovar(sid: String?, timeout: String?): Pair<String, Int>? {
        val s = suscripciones[sid ?: return null] ?: return null
        val segundos = segundosDe(timeout)
        s.expira = System.currentTimeMillis() + segundos * 1000L
        return s.sid to segundos
    }

    fun cancelar(sid: String?): Boolean {
        if (sid == null) return false
        return suscripciones.remove(sid) != null
    }

    fun publicar(estado: Map<String, Any>) {
        if (estado.isEmpty() || suscripciones.isEmpty()) return
        for (s in suscripciones.values) {
            thread(isDaemon = true) { enviar(s, estado) }
        }
    }

    private fun enviar(s: Suscripcion, estado: Map<String, Any>) {
        val cuerpo = cuerpoDe(estado)
        val seq = synchronized(s) { s.seq.also { s.seq = (s.seq + 1) and 0xFFFFFFFFL } }
        for (destino in s.callbacks) {
            if (entregar(destino, s.sid, seq, cuerpo)) return
        }
    }

    private fun cuerpoDe(estado: Map<String, Any>): String {
        val ns = when (servicio) {
            "AVTransport" -> "urn:schemas-upnp-org:metadata-1-0/AVT/"
            "RenderingControl" -> "urn:schemas-upnp-org:metadata-1-0/RCS/"
            else -> null
        } ?: return propertyset(
            estado.entries.joinToString("") { (k, v) ->
                "<e:property><$k>${escapar(v.toString())}</$k></e:property>"
            }
        )

        val interior = estado.entries.joinToString("") { (nombre, valor) ->
            val canal = if (nombre in CON_CANAL) """ channel="Master"""" else ""
            """<$nombre$canal val="${escapar(valor.toString()).replace("\"", "&quot;")}"/>"""
        }
        val lastChange = """<Event xmlns="$ns"><InstanceID val="0">$interior</InstanceID></Event>"""
        return propertyset("<e:property><LastChange>${escapar(lastChange)}</LastChange></e:property>")
    }

    private fun entregar(destino: String, sid: String, seq: Long, cuerpo: String): Boolean = try {
        val conexion = (URL(destino).openConnection() as HttpURLConnection).apply {
            requestMethod = "NOTIFY"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", """text/xml; charset="utf-8"""")
            setRequestProperty("NT", "upnp:event")
            setRequestProperty("NTS", "upnp:propchange")
            setRequestProperty("SID", sid)
            setRequestProperty("SEQ", seq.toString())
            setRequestProperty("Connection", "close")
        }
        conexion.outputStream.use { salida: OutputStream ->
            salida.write(cuerpo.toByteArray())
        }
        val codigo = conexion.responseCode
        conexion.disconnect()
        codigo in 200..299
    } catch (e: Exception) {
        Log.d(TAG, "$servicio: no se pudo avisar a $destino: ${e.message}")
        false
    }

    private fun segundosDe(timeout: String?): Int =
        Regex("""Second-(\d+)""", RegexOption.IGNORE_CASE).find(timeout.orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(60, 7200)
            ?: TIMEOUT_POR_DEFECTO

    private fun propertyset(propiedades: String) =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">$propiedades</e:propertyset>"""

    private companion object {
        val CON_CANAL = setOf("Volume", "Mute", "VolumeDB", "Loudness")
    }
}
