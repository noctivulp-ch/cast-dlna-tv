package app.castdlna.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Mantiene el receptor vivo cuando la actividad no está en pantalla.
 *
 * En un televisor el usuario sale a la pantalla de inicio constantemente, y
 * sin esto el receptor dejaría de anunciarse justo cuando alguien va a
 * mandarle algo.
 */
class ServicioReceptor : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearCanal()
        val aviso = Notification.Builder(this, CANAL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("A la espera de recibir")
            .setSmallIcon(R.drawable.banner)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_AVISO, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(ID_AVISO, aviso)
        }
        Receptor.arrancar(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(CANAL, "Receptor", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    companion object {
        private const val CANAL = "receptor"
        private const val ID_AVISO = 1

        fun arrancar(contexto: Context) {
            val intent = Intent(contexto, ServicioReceptor::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                contexto.startForegroundService(intent)
            } else {
                contexto.startService(intent)
            }
        }
    }
}
