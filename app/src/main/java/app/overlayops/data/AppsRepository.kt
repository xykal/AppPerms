package app.overlayops.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import app.overlayops.core.AppOpsBridge
import app.overlayops.core.Backend
import app.overlayops.core.OpCatalog
import app.overlayops.core.OpStatus
import app.overlayops.core.ShizukuBridge
import app.overlayops.model.AppEntry

/**
 * Baca daftar aplikasi terpasang + status AppOp overlay-nya.
 * Semua fungsi di sini berat, jadi panggil dari Dispatchers.IO.
 */
class AppsRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun loadApps(includeSystem: Boolean = true): List<AppEntry> {
        val bulk: Map<String, OpStatus>? =
            if (AppOpsBridge.backend == Backend.BINDER) null else ShizukuBridge.shellQueryOverlayBulk()

        val useShellPerApp = AppOpsBridge.backend != Backend.BINDER && bulk == null

        val infos: List<ApplicationInfo> = try {
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (t: Throwable) {
            Log.w(TAG, "getInstalledApplications gagal", t)
            emptyList()
        }

        return infos.asSequence()
            .mapNotNull { info -> toEntry(info, bulk, useShellPerApp) }
            .filter { includeSystem || !it.isSystem }
            .sortedBy { it.labelLower }
            .toList()
    }

    private fun toEntry(
        info: ApplicationInfo,
        bulk: Map<String, OpStatus>?,
        useShellPerApp: Boolean,
    ): AppEntry? {
        val pkg = info.packageName ?: return null

        val isSystem = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
        val icon = runCatching { info.loadIcon(pm) }.getOrNull()
        val enabled = info.enabled
        val declaresOverlay = AppOpsBridge.declaresOverlayPermission(pm, pkg)

        val status = when {
            bulk != null -> bulk[pkg] ?: OpStatus.DEFAULT
            useShellPerApp -> ShizukuBridge.shellGetOp(pkg, OpCatalog.OVERLAY.shell)
            else -> AppOpsBridge.getStatus(OpCatalog.OVERLAY.op, info.uid, pkg)
        }

        return AppEntry(
            packageName = pkg,
            label = label,
            uid = info.uid,
            isSystem = isSystem,
            targetSdk = info.targetSdkVersion,
            enabled = enabled,
            declaresOverlay = declaresOverlay,
            icon = icon,
            overlayStatus = status,
        )
    }

    fun readOps(entry: AppEntry): List<Pair<app.overlayops.core.OpDef, OpStatus>> {
        if (AppOpsBridge.backend == Backend.BINDER) {
            return AppOpsBridge.readAll(entry.uid, entry.packageName)
        }
        return OpCatalog.ALL.map { def ->
            def to ShizukuBridge.shellGetOp(entry.packageName, def.shell)
        }
    }

    fun writeOp(entry: AppEntry, def: app.overlayops.core.OpDef, status: OpStatus): String? {
        if (AppOpsBridge.backend == Backend.BINDER) {
            val error = AppOpsBridge.setStatus(def.op, entry.uid, entry.packageName, status.mode)
            if (error == null) return null
            // Binder jalan tapi ROM menolak -> coba jalur shell sebagai cadangan.
            return ShizukuBridge.shellSetOp(entry.packageName, def.shell, status) ?: error
        }
        return ShizukuBridge.shellSetOp(entry.packageName, def.shell, status)
    }

    companion object {
        private const val TAG = "AppsRepository"
    }
}
