package app.castdlna.tv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.castdlna.tv.upnp.Media
import app.castdlna.tv.upnp.Reproductor

/** Doce horas: una foto en pantalla no se acaba, la quita quien la puso. */
private const val DURACION_IMAGEN_MS = 12L * 60 * 60 * 1000

/**
 * Reproductor sobre ExoPlayer.
 *
 * Media3 trae de fábrica el control de velocidad para directos
 * (`targetOffsetMs`, `minPlaybackSpeed`, `maxPlaybackSpeed`), que es
 * justamente lo que en el receptor de escritorio no se pudo resolver: allí
 * mpv no lo tiene, cambiar la velocidad a mano saturaba el audio y el flujo
 * no admitía saltos. Aquí basta con declararlo en el propio elemento.
 */
@UnstableApi
class ReproductorExo(
    context: Context,
    private val alCambiar: () -> Unit,
) : Reproductor {

    private val hilo = Handler(Looper.getMainLooper())

    private val fuente = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(fuente))
        .setLoadControl(DefaultLoadControl())
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) = alCambiar()
                override fun onIsPlayingChanged(reproduciendo: Boolean) = alCambiar()
                override fun onMediaItemTransition(item: MediaItem?, razon: Int) = alCambiar()
            })
        }

    /** Una imagen no tiene duración: se queda hasta que llegue otra cosa. */
    @Volatile var mostrandoImagen: Boolean = false; private set

    @Volatile private var silencio = false
    @Volatile private var volumenPrevio = 1f

    override val estado: String
        get() = enHilo {
            when {
                mostrandoImagen -> "PLAYING"
                exo.currentMediaItem == null -> if (paradoExpreso) "STOPPED" else "NO_MEDIA_PRESENT"
                exo.playbackState == Player.STATE_BUFFERING -> "TRANSITIONING"
                exo.isPlaying -> "PLAYING"
                exo.playbackState == Player.STATE_ENDED -> "STOPPED"
                else -> "PAUSED_PLAYBACK"
            }
        }

    @Volatile private var paradoExpreso = false

    override val posicionSeg: Double get() = enHilo { exo.currentPosition / 1000.0 }

    override val duracionSeg: Double
        get() = enHilo {
            val d = exo.duration
            if (d > 0) d / 1000.0 else 0.0
        }

    override val volumen: Int get() = enHilo { Math.round(exo.volume * 100) }
    override val silenciado: Boolean get() = silencio
    override val pistas: Int get() = enHilo { exo.mediaItemCount }
    override val pistaActual: Int get() = enHilo { exo.currentMediaItemIndex }

    override fun cargar(uri: String, media: Media) = enHilo {
        paradoExpreso = false
        mostrandoImagen = media.esImagen
        fuente.setDefaultRequestProperties(media.cabeceras)
        exo.setMediaItem(elementoDe(uri, media))
        exo.prepare()
        exo.playWhenReady = true
        alCambiar()
    }

    override fun encolar(uri: String, media: Media) = enHilo {
        exo.addMediaItem(elementoDe(uri, media))
        alCambiar()
    }

    private fun elementoDe(uri: String, media: Media): MediaItem {
        val constructor = MediaItem.Builder().setUri(uri)

        if (media.esImagen) {
            // Media3 no muestra una imagen si no se le dice cuánto debe durar:
            // sin esto se prepara, no selecciona la pista y deja la pantalla en
            // negro, sin dar ningún error que lo delate. Como en DLNA una foto
            // se queda puesta hasta que llegue otra cosa o paren el envío, se
            // declara una duración larga en vez de una real.
            constructor.setImageDurationMs(DURACION_IMAGEN_MS)
            // El tipo va explícito porque la URL puede no acabar en .jpg —los
            // servidores de los móviles sirven con nombres sin extensión— y sin
            // él la detección se queda a merced de lo que adivine el extractor.
            mimeDeImagen(media.mime, uri)?.let { constructor.setMimeType(it) }
        }

        if (media.directo) {
            // El desfase objetivo y el margen de velocidad son lo que mantiene
            // acotado el retraso de un directo sin dar saltos.
            constructor.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(1_000)
                    .setMinOffsetMs(500)
                    .setMaxOffsetMs(3_000)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
        }

        if (media.subtitulos.isNotEmpty()) {
            constructor.setSubtitleConfigurations(
                media.subtitulos.map { url ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
                        .setMimeType(mimeDeSubtitulo(url))
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
            )
        }
        return constructor.build()
    }

    private fun mimeDeImagen(mimeDeclarado: String, url: String): String? {
        if (mimeDeclarado.startsWith("image/")) return mimeDeclarado
        val limpio = url.lowercase().substringBefore('?')
        return when {
            limpio.endsWith(".jpg") || limpio.endsWith(".jpeg") -> MimeTypes.IMAGE_JPEG
            limpio.endsWith(".png") -> MimeTypes.IMAGE_PNG
            limpio.endsWith(".webp") -> MimeTypes.IMAGE_WEBP
            limpio.endsWith(".bmp") -> MimeTypes.IMAGE_BMP
            limpio.endsWith(".heic") || limpio.endsWith(".heif") -> MimeTypes.IMAGE_HEIF
            limpio.endsWith(".avif") -> MimeTypes.IMAGE_AVIF
            else -> null
        }
    }

    private fun mimeDeSubtitulo(url: String): String {
        val limpio = url.lowercase().substringBefore('?')
        return when {
            limpio.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            limpio.endsWith(".ass") || limpio.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            limpio.endsWith(".ttml") || limpio.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    override fun reproducir() = enHilo {
        paradoExpreso = false
        if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        exo.play()
        alCambiar()
    }

    override fun pausar() = enHilo { exo.pause(); alCambiar() }

    override fun parar() = enHilo {
        paradoExpreso = true
        mostrandoImagen = false
        exo.stop()
        exo.clearMediaItems()
        alCambiar()
    }

    override fun buscar(segundos: Double) = enHilo {
        exo.seekTo((segundos * 1000).toLong())
        alCambiar()
    }

    override fun irAPista(indice: Int) = enHilo {
        if (indice in 0 until exo.mediaItemCount) exo.seekTo(indice, 0)
        alCambiar()
    }

    override fun siguiente() = enHilo { exo.seekToNextMediaItem(); alCambiar() }
    override fun anterior() = enHilo { exo.seekToPreviousMediaItem(); alCambiar() }

    override fun ajustarVolumen(nivel: Int) = enHilo {
        silencio = false
        exo.volume = nivel.coerceIn(0, 100) / 100f
        alCambiar()
    }

    override fun silenciar(activo: Boolean) = enHilo {
        if (activo && !silencio) volumenPrevio = exo.volume
        silencio = activo
        exo.volume = if (activo) 0f else volumenPrevio
        alCambiar()
    }

    override fun modoDeLista(modo: String) = enHilo {
        exo.repeatMode = when (modo) {
            "REPEAT_ONE" -> Player.REPEAT_MODE_ONE
            "REPEAT_ALL" -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        exo.shuffleModeEnabled = modo == "SHUFFLE" || modo == "RANDOM"
    }

    fun soltar() = enHilo { exo.release() }

    /**
     * ExoPlayer solo se deja tocar desde el hilo que lo creó, y aquí las
     * llamadas llegan desde los hilos del servidor HTTP.
     */
    private fun <T> enHilo(bloque: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return bloque()
        var resultado: T? = null
        var error: Throwable? = null
        val cerrojo = java.util.concurrent.CountDownLatch(1)
        hilo.post {
            try { resultado = bloque() } catch (e: Throwable) { error = e }
            cerrojo.countDown()
        }
        cerrojo.await(5, java.util.concurrent.TimeUnit.SECONDS)
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return resultado as T
    }
}
