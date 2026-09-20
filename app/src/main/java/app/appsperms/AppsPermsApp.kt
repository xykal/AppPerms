package app.appsperms

import android.app.Application
import android.util.Log
import app.appsperms.core.AppOpsBridge
import app.appsperms.core.Settings
import app.appsperms.guard.GhostGuardService

/**
 * HiddenApiBypass dipasang sedini mungkin — sebelum method hidden (IAppOpsService)
 * pertama kali di-resolve, karena pembatasan hidden API di Android 9+ hanya bisa
 * dibuka selama proses masih "fresh".
 *
 * OPTIMIZED v1.5: Bersihkan semua setting berbahaya saat app start
 */
class AppsPermsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Terapkan bahasa pilihan pengguna sebelum Activity pertama dibuat,
        // supaya tidak ada kedipan bahasa Indonesia lalu berubah.
        runCatching { Settings.applyStoredLanguage(this) }
            .onFailure { Log.w("AppsPermsApp", "gagal menerapkan bahasa", it) }

        // v1.5 OPTIMIZED: Matikan & bersihkan semua fitur berbahaya secara permanen
        // - GhostGuard overlay (bikin panas, lag sentuhan, foreground service boros)
        // - Protected app whitelist (bikin baterai boros, Doze rusak)
        runCatching {
            Settings.cleanDeprecatedKeys(this)
            Settings.setGuardEnabled(this, false)
            Settings.setProtectedApp(this, null)
            // Pastikan service mati total, tidak ada overlay yang tertinggal
            GhostGuardService.syncFromSettings(this)
            // Hapus notifikasi sisa jika ada
            stopService(android.content.Intent(this, GhostGuardService::class.java))
        }.onFailure { Log.w("AppsPermsApp", "gagal cleanup guard lama", it) }

        runCatching { AppOpsBridge.hiddenApiBypass() }
            .onFailure { Log.w("AppsPermsApp", "gagal pasang HiddenApiBypass", it) }
    }
}
