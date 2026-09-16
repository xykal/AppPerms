package app.overlayops

import android.app.Application
import android.util.Log
import app.overlayops.core.AppOpsBridge

/**
 * HiddenApiBypass dipasang sedini mungkin — sebelum method hidden (IAppOpsService)
 * pertama kali di-resolve, karena pembatasan hidden API di Android 9+ hanya bisa
 * dibuka selama proses masih "fresh".
 */
class OverlayOpsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { AppOpsBridge.hiddenApiBypass() }
            .onFailure { Log.w("OverlayOpsApp", "gagal pasang HiddenApiBypass", it) }
    }
}
