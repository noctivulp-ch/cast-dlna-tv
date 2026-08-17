package app.castdlna.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.castdlna.tv.ui.PantallaReceptor
import app.castdlna.tv.ui.TemaReceptor

/**
 * La única pantalla. El receptor en sí vive en un servicio, porque en un
 * televisor se sale a la pantalla de inicio a cada rato y tiene que seguir
 * anunciándose y respondiendo.
 */
@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Un receptor pasa horas con una imagen fija o en espera: sin esto el
        // televisor apagaría la pantalla en mitad de un cast.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ServicioReceptor.arrancar(this)

        setContent {
            TemaReceptor {
                PantallaReceptor(
                    estado = Receptor.estado,
                    reproductor = Receptor.reproductor,
                    onReproducirPausar = Receptor::reproducirOPausar,
                    onBuscarFraccion = Receptor::buscarFraccion,
                    onParar = Receptor::parar,
                )
            }
        }
    }
}
