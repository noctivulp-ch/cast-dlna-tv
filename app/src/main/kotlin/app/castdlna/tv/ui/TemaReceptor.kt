package app.castdlna.tv.ui

import androidx.compose.runtime.Composable
import wulf.compose.tokens.WulfAcento
import wulf.compose.tokens.WulfTheme

/**
 * El tema del receptor.
 *
 * Siempre oscuro: se mira en una sala a oscuras y con el vídeo ocupando la
 * pantalla, así que el modo claro no tiene escena de uso aquí.
 */
@Composable
fun TemaReceptor(contenido: @Composable () -> Unit) {
    WulfTheme(acento = WulfAcento.ORO, oscuro = true, content = contenido)
}
