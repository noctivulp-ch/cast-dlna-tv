package app.castdlna.tv.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Margen de seguridad para televisión.
 *
 * Muchos televisores recortan el borde de la imagen —el viejo *overscan*—, así
 * que nada que haga falta leer puede apoyarse en el filo de la pantalla. La
 * recomendación de Android TV es dejar un 5 % del ancho y un 5 % del alto, que
 * en 1080p son 48 dp por lado y 27 arriba y abajo.
 *
 * Vive aquí y no en Wulfenite porque es propio de la escena de televisión y la
 * librería la lleva otra línea de trabajo; si acaba haciendo falta en más
 * sitios, es candidato a proponerlo allí.
 */
val ZONA_SEGURA = PaddingValues(horizontal = 48.dp, vertical = 27.dp)
