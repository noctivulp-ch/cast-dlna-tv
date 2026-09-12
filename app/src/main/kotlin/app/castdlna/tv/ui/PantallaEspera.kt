package app.castdlna.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wulf.compose.superficies.WulfBandeja
import wulf.compose.superficies.WulfPozo
import wulf.compose.texto.TalladoNivel
import wulf.compose.texto.WulfTextoTallado
import wulf.compose.tokens.WulfSpacing
import wulf.compose.tokens.WulfTheme

/**
 * Lo que se ve mientras no llega nada.
 *
 * Es la pantalla que más tiempo pasa encendida, así que dice lo justo: cómo se
 * llama este receptor en la lista del móvil y en qué dirección está, que es lo
 * que hace falta cuando algo no aparece.
 */
@Composable
fun PantallaEspera(estado: EstadoPantalla, modifier: Modifier = Modifier) {
    WulfBandeja(modifier.fillMaxSize().padding(ZONA_SEGURA)) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WulfPozo(modifier = Modifier.fillMaxWidth(0.6f)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WulfTextoTallado(
                        texto = estado.nombreReceptor.ifEmpty { "cast-dlna" },
                        // El tallado necesita saber sobre qué se apoya para
                        // calcular su contraste; aquí es el pozo.
                        sobre = WulfTheme.laca.pozo,
                        estilo = MaterialTheme.typography.headlineMedium,
                        nivel = TalladoNivel.MARCADO,
                    )
                    Spacer(Modifier.height(WulfSpacing.paddingCelda))
                    // Los dos momentos de la espera no son el mismo, y desde el
                    // sofá la diferencia importa: «no me ve nadie» manda a
                    // revisar la red, «te veo, dale tú» manda a mirar el móvil.
                    Text(
                        when {
                            !estado.hayControlador -> "A la espera de recibir"
                            estado.controladores.size == 1 ->
                                "Conectado desde ${estado.controladores.first()} — elige algo que enviar"
                            else ->
                                "Conectado desde ${estado.controladores.size} dispositivos — elige algo que enviar"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (estado.direccion.isNotEmpty()) {
                        Spacer(Modifier.height(WulfSpacing.paddingCelda))
                        Text(
                            estado.direccion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
