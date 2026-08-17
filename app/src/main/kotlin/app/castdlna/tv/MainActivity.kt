package app.castdlna.tv

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.castdlna.tv.player.ReproductorExo

/**
 * Pantalla única del receptor: la superficie de vídeo ocupa todo y, cuando no
 * hay nada reproduciéndose, se ve el aviso de que está a la espera.
 *
 * La interfaz definitiva irá con Wulfenite; esto es lo mínimo para poder
 * validar el receptor contra controladores reales.
 */
@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServicioReceptor.arrancar(this)

        setContent {
            MaterialTheme {
                val reproductor = Receptor.reproductor
                Box(
                    Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (reproductor != null) {
                        AndroidView(
                            factory = { contexto ->
                                PlayerView(contexto).apply {
                                    player = reproductor.exo
                                    useController = false
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (Receptor.enEspera) {
                        Text(
                            "Listo para recibir · ${Receptor.nombre}",
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
