package app.castdlna.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import wulf.compose.componentes.WulfBarraBusqueda
import wulf.compose.componentes.WulfBotonPrimario
import wulf.compose.componentes.WulfBotonPlano
import wulf.compose.foco.wulfFocoInicial
import wulf.compose.tokens.WulfSpacing

/**
 * Los mandos que aparecen sobre el vídeo.
 *
 * En un televisor esto se maneja con un mando a tres metros, así que hay dos
 * cosas que mandan sobre lo demás: que se vea qué tiene el foco —de eso se
 * encarga el canto de la librería— y que la barra se pueda mover con
 * izquierda y derecha sin tener que apuntar a nada.
 *
 * Se dibuja sobre un degradado y no sobre una superficie opaca: el vídeo sigue
 * siendo el contenido, y taparlo entero para enseñar dos botones sería
 * ponerse por delante de lo que el usuario está mirando.
 */
@Composable
fun ControlesReproduccion(
    estado: EstadoPantalla,
    visible: Boolean,
    onReproducirPausar: () -> Unit,
    onBuscar: (Float) -> Unit,
    onParar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        // El degradado deja ver el vídeo pero garantiza que el
                        // texto se lea encima de cualquier imagen.
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                        )
                    )
                    .padding(ZONA_SEGURA)
                    .padding(top = WulfSpacing.paddingCeldaGrande),
            ) {
                if (estado.titulo.isNotEmpty()) {
                    Text(
                        estado.titulo,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(WulfSpacing.paddingCelda))
                }

                if (estado.sePuedeBuscar) {
                    WulfBarraBusqueda(
                        valor = estado.avance,
                        onCambio = onBuscar,
                        modifier = Modifier.fillMaxWidth().wulfFocoInicial(visible),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(reloj(estado.posicionSeg),
                            style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Text(reloj(estado.duracionSeg),
                            style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                } else if (estado.esDirecto) {
                    Text("En directo",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }

                Spacer(Modifier.height(WulfSpacing.paddingCelda))

                Row(horizontalArrangement = Arrangement.spacedBy(WulfSpacing.paddingCelda)) {
                    // Cuando no hay barra que enfocar, el foco tiene que
                    // arrancar aquí o el mando no encuentra nada donde entrar.
                    WulfBotonPrimario(
                        texto = if (estado.fase == EstadoPantalla.Fase.REPRODUCIENDO) "Pausa" else "Reproducir",
                        onClick = onReproducirPausar,
                        modifier = Modifier.wulfFocoInicial(visible && !estado.sePuedeBuscar),
                    )
                    WulfBotonPlano(texto = "Parar", onClick = onParar)
                }
            }
        }
    }
}
