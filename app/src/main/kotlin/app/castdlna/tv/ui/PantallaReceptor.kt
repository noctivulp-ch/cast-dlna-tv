package app.castdlna.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.castdlna.tv.player.ReproductorExo
import kotlinx.coroutines.delay

/** Cuánto siguen visibles los mandos sin que nadie toque nada. */
private const val OCULTAR_TRAS_MS = 4_000L

/**
 * La pantalla del receptor: el vídeo ocupa todo y los mandos se asoman cuando
 * hace falta.
 *
 * Las teclas se atienden en la raíz y no en cada botón porque en un televisor
 * la primera pulsación no debería «gastarse» en despertar la interfaz: si los
 * mandos están escondidos, cualquier tecla los muestra; si ya se ven, la tecla
 * hace lo suyo.
 */
@UnstableApi
@Composable
fun PantallaReceptor(
    estado: EstadoPantalla,
    reproductor: ReproductorExo?,
    onReproducirPausar: () -> Unit,
    onBuscarFraccion: (Float) -> Unit,
    onParar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ultimoToque by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var mandosVisibles by remember { mutableStateOf(false) }

    // Los mandos se retiran solos: en una pantalla que se mira de lejos, algo
    // encima del vídeo que no se va molesta más que ayuda.
    LaunchedEffect(ultimoToque, mandosVisibles) {
        if (mandosVisibles) {
            delay(OCULTAR_TRAS_MS)
            if (System.currentTimeMillis() - ultimoToque >= OCULTAR_TRAS_MS) {
                mandosVisibles = false
            }
        }
    }

    fun despertar(): Boolean {
        ultimoToque = System.currentTimeMillis()
        val estaban = mandosVisibles
        mandosVisibles = true
        return !estaban
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { evento ->
                if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (evento.key) {
                    // Las teclas de transporte del mando actúan siempre, estén
                    // los mandos a la vista o no: son inequívocas.
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        despertar(); onReproducirPausar(); true
                    }
                    Key.MediaStop -> { despertar(); onParar(); true }
                    Key.MediaFastForward -> {
                        despertar(); onBuscarFraccion(estado.avance + 0.05f); true
                    }
                    Key.MediaRewind -> {
                        despertar(); onBuscarFraccion(estado.avance - 0.05f); true
                    }
                    // El resto solo despierta la interfaz la primera vez; a
                    // partir de ahí las consume quien tenga el foco.
                    Key.DirectionCenter, Key.Enter -> {
                        if (despertar()) true else { onReproducirPausar(); true }
                    }
                    Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionLeft, Key.DirectionRight -> despertar()
                    Key.Back -> {
                        if (mandosVisibles) { mandosVisibles = false; true } else false
                    }
                    else -> false
                }
            }
    ) {
        if (reproductor != null) {
            AndroidView(
                factory = { contexto ->
                    PlayerView(contexto).apply {
                        player = reproductor.exo
                        // El mando lo lleva esta pantalla: el de ExoPlayer no
                        // sigue las reglas de la casa ni el foco de la librería.
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (estado.fase) {
            EstadoPantalla.Fase.ESPERANDO -> PantallaEspera(estado)
            else -> ControlesReproduccion(
                estado = estado,
                visible = mandosVisibles,
                onReproducirPausar = { ultimoToque = System.currentTimeMillis(); onReproducirPausar() },
                onBuscar = { ultimoToque = System.currentTimeMillis(); onBuscarFraccion(it) },
                onParar = onParar,
            )
        }
    }
}