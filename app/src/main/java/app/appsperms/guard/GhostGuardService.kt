package app.appsperms.guard

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * OPTIMIZED v1.5 - GhostGuard DINONAKTIFKAN PERMANEN
 *
 * Kenapa dimatikan?
 * - Overlay 1-4 jendela TYPE_APPLICATION_OVERLAY menelan sentuhan (return true)
 *   bikin HP lag, sentuhan tidak responsif, dan GPU compositing ekstra = panas + boros baterai
 * - Foreground service START_STICKY bikin service restart terus, tidak pernah mati
 * - Di sebagian ROM (MIUI, ColorOS) overlay tebal >30% layar bikin user terkunci
 *
 * Di v1.5 service ini jadi NO-OP: setiap start langsung stopSelf(), tidak pasang jendela apapun.
 * File dipertahankan agar AndroidManifest tidak crash saat upgrade dari versi lama,
 * tapi tidak akan pernah aktif.
 */
class GhostGuardService : Service() {

    companion object {
        private const val TAG = "GhostGuard-DEPRECATED"

        const val ACTION_UPDATE = "app.appsperms.guard.UPDATE"
        const val ACTION_PAUSE = "app.appsperms.guard.PAUSE"
        const val ACTION_STOP = "app.appsperms.guard.STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastError: String? = "Fitur Perisai dinonaktifkan permanen di v1.5 (anti panas/lag)"

        fun syncFromSettings(context: android.content.Context) {
            // v1.5: Paksa stop, jangan pernah start foreground service
            try {
                context.stopService(Intent(context, GhostGuardService::class.java))
            } catch (_: Throwable) {}
            isRunning = false
            // Hapus notifikasi sisa
            try {
                val nm = context.getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.cancel(771)
            } catch (_: Throwable) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GhostGuardService dibuat tapi langsung dimatikan (v1.5 optimized)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "GhostGuardService dipanggil action=${intent?.action} -> langsung stop (disabled)")
        isRunning = false
        lastError = "Fitur Perisai dinonaktifkan permanen di v1.5"
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
