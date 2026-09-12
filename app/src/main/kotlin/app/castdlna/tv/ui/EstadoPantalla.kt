package app.castdlna.tv.ui

import androidx.compose.runtime.Immutable

/**
 * Lo que la pantalla necesita saber del receptor, y nada más.
 *
 * Va aparte del renderer para que la interfaz no dependa del vocabulario de
 * UPnP: aquí no hay `TRANSITIONING` ni `NO_MEDIA_PRESENT`, hay «cargando» y
 * «a la espera».
 */
@Immutable
data class EstadoPantalla(
    val fase: Fase = Fase.ESPERANDO,
    val titulo: String = "",
    val posicionSeg: Double = 0.0,
    val duracionSeg: Double = 0.0,
    val volumen: Int = 100,
    val silenciado: Boolean = false,
    val esImagen: Boolean = false,
    val esDirecto: Boolean = false,
    val nombreReceptor: String = "",
    val direccion: String = "",
    /** Máquinas de los controladores que siguen el estado del receptor. */
    val controladores: List<String> = emptyList(),
) {
    /** Hay alguien pendiente, aunque todavía no haya mandado nada. */
    val hayControlador: Boolean
        get() = controladores.isNotEmpty()

    val avance: Float
        get() = if (duracionSeg > 0) (posicionSeg / duracionSeg).toFloat().coerceIn(0f, 1f) else 0f

    /** Una imagen o un directo no se pueden recorrer. */
    val sePuedeBuscar: Boolean
        get() = duracionSeg > 0 && !esImagen && !esDirecto

    enum class Fase { ESPERANDO, CARGANDO, REPRODUCIENDO, PAUSADO }
}

fun faseDe(estadoUpnp: String): EstadoPantalla.Fase = when (estadoUpnp) {
    "PLAYING" -> EstadoPantalla.Fase.REPRODUCIENDO
    "PAUSED_PLAYBACK" -> EstadoPantalla.Fase.PAUSADO
    "TRANSITIONING" -> EstadoPantalla.Fase.CARGANDO
    else -> EstadoPantalla.Fase.ESPERANDO
}

fun reloj(segundos: Double): String {
    val s = segundos.toInt().coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}
