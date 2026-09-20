package app.appsperms.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * OPTIMIZED v1.5 - DEPRECATED
 * Receiver ini sudah tidak dipakai lagi karena GhostGuardService dimatikan permanen.
 * Dipertahankan agar tidak crash saat PendingIntent lama masih ada, tapi isinya no-op.
 */
class GhostGuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("GhostGuard-DEPRECATED", "Receiver dipanggil ${intent.action} -> diabaikan (fitur dimatikan)")
        // No-op: jangan start service lagi
        GhostGuardService.syncFromSettings(context)
    }
}
