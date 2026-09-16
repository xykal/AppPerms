package app.overlayops.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import app.overlayops.core.AppOpsBridge
import app.overlayops.core.OpCatalog
import app.overlayops.core.OpDef
import app.overlayops.core.OpStatus
import app.overlayops.core.ShizukuBridge
import app.overlayops.model.AppEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Baca daftar aplikasi + status AppOps-nya.
 *
 * Strategi baca/write: **shell-first** (`appops query-op` / `appops get` / `appops set`)
 * karena perintah ini resmi & stabil di semua ROM; binder IAppOpsService dipakai kalau
 * refleksi berhasil (lebih cepat, satu panggilan per op).
 *
 * Semua fungsi di sini berat -> panggil dari Dispatchers.IO.
 */
class AppsRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val iconCache = LruCache<String, Drawable>(300)

    // ------------------------------------------------------------ daftar app

    fun loadApps(preferShell: Boolean): List<AppEntry> {
        val infos: List<ApplicationInfo> = try {
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (t: Throwable) {
            Log.w(TAG, "getInstalledApplications gagal", t)
            emptyList()
        }

        // Ambil himpunan paket yang meminta izin overlay dalam 1 batch call (bukan N IPC calls)
        val declaredOverlaySet: Set<String> = runCatching {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .asSequence()
                .filter { pi ->
                    pi.requestedPermissions?.any { it == android.Manifest.permission.SYSTEM_ALERT_WINDOW } == true
                }
                .mapNotNull { it.packageName }
                .toSet()
        }.getOrElse { emptySet() }

        // Satu tembakan untuk semua paket (4 perintah), bukan satu perintah per app.
        val bulk: Map<String, OpStatus>? = if (preferShell) ShizukuBridge.shellQueryOverlayBulk() else null

        return infos.asSequence()
            .mapNotNull { info -> toEntry(info, bulk, declaredOverlaySet, preferShell) }
            .sortedBy { it.labelLower }
            .toList()
    }

    private fun toEntry(
        info: ApplicationInfo,
        bulk: Map<String, OpStatus>?,
        declaredOverlaySet: Set<String>,
        preferShell: Boolean,
    ): AppEntry? {
        val pkg = info.packageName ?: return null
        val isSystem = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
        val enabled = info.enabled
        val declaresOverlay = if (declaredOverlaySet.isNotEmpty()) {
            declaredOverlaySet.contains(pkg)
        } else {
            declaresOverlayPermission(pkg)
        }
        val icon = iconFor(pkg, info)

        val status = when {
            bulk != null -> bulk[pkg] ?: OpStatus.DEFAULT
            preferShell -> ShizukuBridge.shellGetOp(pkg, OpCatalog.OVERLAY.shell)
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

    /** Ikon di-cache: loadIcon itu mahal kalau dipanggil tiap refresh untuk ratusan app. */
    private fun iconFor(pkg: String, info: ApplicationInfo): Drawable? {
        iconCache.get(pkg)?.let { return it }
        val drawable = runCatching { info.loadIcon(pm) }.getOrNull() ?: return null
        iconCache.put(pkg, drawable)
        return drawable
    }

    fun declaresOverlayPermission(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.any { it == android.Manifest.permission.SYSTEM_ALERT_WINDOW } == true
    } catch (t: Throwable) {
        false
    }

    // ----------------------------------------------------------- baca 1 paket

    /** Semua op katalog untuk satu paket, urut sesuai [OpCatalog.ALL]. */
    fun readOps(entry: AppEntry, preferShell: Boolean): List<Pair<OpDef, OpStatus>> {
        if (preferShell) {
            val parsed = ShizukuBridge.shellReadOps(entry.packageName)
            if (parsed != null) {
                return OpCatalog.ALL.map { def -> def to (parsed[def.shell] ?: OpStatus.DEFAULT) }
            }
            // parser gagal (ROM beda) -> baca satu-satu
            return OpCatalog.ALL.map { def ->
                def to ShizukuBridge.shellGetOp(entry.packageName, def.shell)
            }
        }
        return AppOpsBridge.readAll(entry.uid, entry.packageName)
    }

    // ------------------------------------------------------------- tulis op

    fun writeOp(entry: AppEntry, def: OpDef, status: OpStatus, preferShell: Boolean): String? {
        if (preferShell) {
            return ShizukuBridge.shellSetOp(entry.packageName, def.shell, status)
        }
        val viaBinder = AppOpsBridge.setStatus(def.op, entry.uid, entry.packageName, status.mode)
        if (viaBinder == null) return null
        // Binder jalan tapi ROM menolak (SecurityException dsb) -> coba jalur shell.
        val viaShell = ShizukuBridge.shellSetOp(entry.packageName, def.shell, status)
        return viaShell ?: viaBinder
    }

    /** Tulis overlay untuk banyak app sekaligus. `onProgress` dipanggil per app. */
    fun writeOverlayBatch(
        entries: List<AppEntry>,
        status: OpStatus,
        preferShell: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Pair<Int, String?> {
        var applied = 0
        var firstError: String? = null
        entries.forEachIndexed { index, entry ->
            val error = writeOp(entry, OpCatalog.OVERLAY, status, preferShell)
            if (error == null) applied++ else if (firstError == null) firstError = "${entry.label}: $error"
            onProgress(index + 1, entries.size)
        }
        return applied to firstError
    }

    // ---------------------------------------------------------------- backup

    /** Backup status eksplisit ke teks sederhana yang gampang dibaca manusia. */
    fun exportBackup(apps: List<AppEntry>): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            append("# OverlayOps backup\n")
            append("# dibuat: ").append(stamp).append('\n')
            append("# format: <nama paket>=<allow|ignore|deny|foreground>\n")
            apps.filter { it.overlayStatus.isExplicit || it.overlayStatus == OpStatus.FOREGROUND }
                .sortedBy { it.packageName }
                .forEach { append(it.packageName).append('=').append(OpStatus.shellName(it.overlayStatus)).append('\n') }
        }
    }

    /** Parse hasil [exportBackup]; baris tidak valid diabaikan. */
    fun parseBackup(text: String): List<Pair<String, OpStatus>> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .mapNotNull { line ->
                val pkg = line.substringBefore('=').trim()
                val status = OpStatus.forShellName(line.substringAfter('=').trim())
                if (pkg.isEmpty() || status == OpStatus.UNKNOWN) null else pkg to status
            }
            .distinctBy { it.first }
            .toList()

    companion object {
        private const val TAG = "AppsRepository"
    }
}
