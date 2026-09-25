package app.appsperms.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import app.appsperms.core.AppOpsBridge
import app.appsperms.core.OpCatalog
import app.appsperms.core.OpDef
import app.appsperms.core.OpStatus
import app.appsperms.core.ShizukuBridge
import app.appsperms.model.AppEntry

/**
 * Baca daftar aplikasi + status AppOps-nya.
 *
 * v1.7.4 improvements:
 * - Support semua UID (owner, work profile, clone, parallel, dual)
 * - Detect emulator, clone, work profile
 * - Bulk query for all users
 */
class AppsRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val iconCache = LruCache<String, Drawable>(200)

    companion object {
        private const val TAG = "AppsRepository"
    }

    fun loadApps(
        preferShell: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<AppEntry> {
        val infos: List<ApplicationInfo> = try {
            // MATCH_ALL to include work profile, clone, etc
            val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_UNINSTALLED_PACKAGES or
                    (if (android.os.Build.VERSION.SDK_INT >= 33) PackageManager.MATCH_ALL else 0)
            pm.getInstalledApplications(flags)
        } catch (t: Throwable) {
            Log.w(TAG, "getInstalledApplications MATCH_ALL gagal, fallback", t)
            try {
                pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            } catch (e: Throwable) {
                Log.w(TAG, "getInstalledApplications gagal", e)
                emptyList()
            }
        }

        val total = infos.size
        onProgress(0, total)

        val declaredOverlaySet: Set<String> = runCatching {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .asSequence()
                .filter { pi ->
                    pi.requestedPermissions?.any { it == android.Manifest.permission.SYSTEM_ALERT_WINDOW } == true
                }
                .mapNotNull { it.packageName }
                .toSet()
        }.getOrElse { emptySet() }

        // Bulk query - try all users version if shell available
        val bulk: Map<String, OpStatus>? = if (preferShell) {
            try {
                ShizukuBridge.shellQueryOverlayBulkAllUsers()
            } catch (e: Throwable) {
                ShizukuBridge.shellQueryOverlayBulk()
            }
        } else null

        val uidCounts: Map<Int, Int> = infos.groupingBy { it.uid }.eachCount()
        val isEmulator = ShizukuBridge.isEmulator()
        val users = if (preferShell) ShizukuBridge.listUsers() else listOf(0)

        val list = ArrayList<AppEntry>(total)
        var count = 0
        for (info in infos) {
            count++
            val entry = toEntry(info, bulk, declaredOverlaySet, preferShell, uidCounts[info.uid] ?: 1, isEmulator, users)
            if (entry != null) {
                list.add(entry)
            }
            if (count % 10 == 0 || count == total) {
                onProgress(count, total)
            }
        }

        return list.sortedWith(compareBy<AppEntry> { it.isSpecialUser }.thenBy { it.labelLower })
    }

    private fun toEntry(
        info: ApplicationInfo,
        bulk: Map<String, OpStatus>?,
        declaredOverlaySet: Set<String>,
        preferShell: Boolean,
        sharedUidCount: Int,
        isEmulatorDevice: Boolean,
        allUsers: List<Int>,
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

        val icon = iconCache.get(pkg)

        // Detect userId from uid: uid = userId * 100000 + appId
        val userId = info.uid / 100000
        val isClone = detectClone(pkg, info, userId)
        val isWorkProfile = userId >= 10 && userId < 100
        val isEmulatorApp = isEmulatorDevice && (pkg.contains("emulator") || pkg.contains("genymotion"))

        val status = when {
            bulk != null -> {
                // Try with user suffix first, then plain
                bulk["$pkg:user$userId"] ?: bulk[pkg] ?: OpStatus.DEFAULT
            }
            preferShell -> {
                // Try with user flag
                if (userId != 0) {
                    ShizukuBridge.shellReadOpsForUser(pkg, userId)?.get(OpCatalog.OVERLAY.shell) ?: OpStatus.DEFAULT
                } else {
                    ShizukuBridge.shellGetOp(pkg, OpCatalog.OVERLAY.shell)
                }
            }
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
            sharedUidCount = sharedUidCount,
            userId = userId,
            isClone = isClone,
            isWorkProfile = isWorkProfile,
            isEmulatorApp = isEmulatorApp,
        )
    }

    private fun detectClone(pkg: String, info: ApplicationInfo, userId: Int): Boolean {
        // MIUI Dual Apps, Samsung Secure Folder, OxygenOS Parallel Apps, Shelter, Island
        // Often userId 999 or 10+, or package with suffix, or shared UID >1 with same package prefix
        if (userId == 999) return true
        if (userId >= 10) return true // work profile or clone
        // Check if package is known clone pattern
        if (pkg.contains(":clone") || pkg.contains(".clone") || pkg.contains("_clone")) return true
        // Check data dir contains user id
        val dataDir = info.dataDir ?: ""
        if (dataDir.contains("/user/") && !dataDir.contains("/user/0/")) return true
        return false
    }

    fun getOrLoadIcon(pkg: String): Drawable? {
        iconCache.get(pkg)?.let { return it }
        val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: return null
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

    fun readOps(entry: AppEntry, preferShell: Boolean): List<Pair<OpDef, OpStatus>> {
        if (preferShell) {
            // Try with userId first
            if (entry.userId != 0) {
                val parsedUser = ShizukuBridge.shellReadOpsForUser(entry.packageName, entry.userId)
                if (parsedUser != null) {
                    return OpCatalog.ALL.map { def -> def to (parsedUser[def.shell] ?: OpStatus.DEFAULT) }
                }
            }
            val parsed = ShizukuBridge.shellReadOps(entry.packageName)
            if (parsed != null) {
                return OpCatalog.ALL.map { def -> def to (parsed[def.shell] ?: OpStatus.DEFAULT) }
            }
            return OpCatalog.ALL.map { def ->
                def to ShizukuBridge.shellGetOp(entry.packageName, def.shell)
            }
        }
        return AppOpsBridge.readAll(entry.uid, entry.packageName)
    }

    fun writeOp(entry: AppEntry, def: OpDef, status: OpStatus, preferShell: Boolean): String? {
        if (preferShell) {
            // For clones/work profile, set for all users to ensure consistency
            if (entry.isSpecialUser) {
                return ShizukuBridge.shellSetOpAllUsers(entry.packageName, def.shell, status)
            }
            return ShizukuBridge.shellSetOp(entry.packageName, def.shell, status)
        }
        val viaBinder = AppOpsBridge.setStatus(def.op, entry.uid, entry.packageName, status.mode)
        if (viaBinder == null) return null
        val viaShell = ShizukuBridge.shellSetOp(entry.packageName, def.shell, status)
        return viaShell ?: viaBinder
    }

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

    fun exportBackup(apps: List<AppEntry>): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        return buildString {
            append("# AppsPerms backup\n")
            append("# dibuat: ").append(stamp).append('\n')
            append("# format: <nama paket>=<allow|ignore|deny|foreground>[:userId]\n")
            apps.filter { it.overlayStatus.isExplicit || it.overlayStatus == OpStatus.FOREGROUND }
                .sortedBy { it.packageName }
                .forEach {
                    append(it.packageName)
                    if (it.userId != 0) append(":user${it.userId}")
                    append('=').append(OpStatus.shellName(it.overlayStatus)).append('\n')
                }
        }
    }

    /** Parse hasil exportBackup; support :userId suffix, baris tidak valid diabaikan. */
    fun parseBackup(text: String): List<Pair<String, OpStatus>> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .mapNotNull { line ->
                var pkg = line.substringBefore('=').trim()
                // Handle :userX suffix - strip for matching but keep logic
                if (pkg.contains(":user")) {
                    pkg = pkg.substringBefore(":user")
                }
                val status = OpStatus.forShellName(line.substringAfter('=').trim())
                if (pkg.isEmpty() || status == OpStatus.UNKNOWN) null else pkg to status
            }
            .distinctBy { it.first }
            .toList()
}
