package app.appsperms.core

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Privileged, bounded operations used by Debloat, Advanced Cleaner and Feature Lab. */
object DeviceCareBridge {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "appsperms-device-care").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Result(
        val success: Boolean,
        val message: String,
        val changed: Int = 0,
        val skipped: Int = 0,
    )

    data class CacheResult(
        val success: Boolean,
        val message: String,
        val beforeFreeBytes: Long? = null,
        val afterFreeBytes: Long? = null,
        val measuredCacheBytes: Long? = null,
    ) {
        val reclaimedBytes: Long?
            get() = if (beforeFreeBytes != null && afterFreeBytes != null) {
                max(0L, afterFreeBytes - beforeFreeBytes)
            } else measuredCacheBytes
    }

    data class FeatureState(
        val supported: Boolean,
        val enabled: Boolean,
        val values: Map<String, String?>,
        val error: String? = null,
    )

    private fun <T> async(block: () -> T, onDone: (T) -> Unit) {
        executor.execute {
            val value = block()
            mainHandler.post { onDone(value) }
        }
    }

    // ---------------------------------------------------------------- Debloat

    fun listUsers(onDone: (List<Int>) -> Unit) = async(
        block = { ShizukuBridge.listUsers().filter(ShellSafety::isValidUserId).distinct().sorted() },
        onDone = onDone,
    )

    fun scanDebloat(
        userId: Int,
        dynamicProtected: Set<String>,
        onDone: (List<DebloatPackage>, String?) -> Unit,
    ) = async(
        block = {
            val (packages, error) = scanDebloatBlocking(userId, dynamicProtected)
            packages to error
        },
        onDone = { (packages, error) -> onDone(packages, error) },
    )

    private fun listPackages(command: String): Pair<Set<String>, String?> {
        val result = ShizukuBridge.shell(command)
            ?: return emptySet<String>() to "Shizuku shell tidak tersedia"
        if (!result.ok) return emptySet<String>() to result.message
        return ShellSafety.parsePackageList(result.stdout) to null
    }

    private fun scanDebloatBlocking(
        userId: Int,
        dynamicProtected: Set<String>,
    ): Pair<List<DebloatPackage>, String?> {
        if (!ShellSafety.isValidUserId(userId)) return emptyList<DebloatPackage>() to "userId tidak valid"
        val effectiveProtected = protectedPackagesForUser(userId, dynamicProtected)

        val (known, knownError) = listPackages("pm list packages -s -u --user $userId")
        if (knownError != null) return emptyList<DebloatPackage>() to knownError
        val (installed, installedError) = listPackages("pm list packages -s --user $userId")
        if (installedError != null) return emptyList<DebloatPackage>() to installedError
        val (disabled, disabledError) = listPackages("pm list packages -s -d --user $userId")
        if (disabledError != null) return emptyList<DebloatPackage>() to disabledError

        return known.map { pkg ->
            val state = when {
                pkg in disabled -> DebloatState.DISABLED
                pkg in installed -> DebloatState.ENABLED
                else -> DebloatState.UNINSTALLED_USER
            }
            DebloatPackage(pkg, userId, state, DebloatPolicy.classify(pkg, effectiveProtected))
        }.sortedWith(compareBy<DebloatPackage> { it.classification.tier.ordinal }.thenBy { it.packageName }) to null
    }

    /** Discover active roles for the selected profile, not only the profile hosting AppsPerms. */
    private fun protectedPackagesForUser(userId: Int, base: Set<String>): Set<String> = buildSet {
        addAll(base.filter(ShellSafety::isValidPackageName))
        listOf(
            "android.app.role.HOME",
            "android.app.role.DIALER",
            "android.app.role.SMS",
        ).forEach { role ->
            val result = ShizukuBridge.shell("cmd role get-role-holders --user $userId $role")
            if (result?.ok == true) addAll(ShellSafety.parsePackageList(result.stdout))
        }
        listOf("default_input_method", "enabled_accessibility_services").forEach { key ->
            val result = ShizukuBridge.shell("settings --user $userId get secure $key")
            if (result?.ok == true) {
                result.stdout.trim().split(':').forEach { component ->
                    val pkg = component.substringBefore('/').trim()
                    if (ShellSafety.isValidPackageName(pkg)) add(pkg)
                }
            }
        }
    }

    fun captureDebloatSnapshot(
        context: Context,
        userId: Int,
        dynamicProtected: Set<String>,
        onDone: (Result) -> Unit,
    ) = async(
        block = {
            val (packages, error) = scanDebloatBlocking(userId, dynamicProtected)
            if (error != null) {
                Result(false, error)
            } else {
                val snapshot = DebloatSnapshot(
                    createdAtMs = System.currentTimeMillis(),
                    userId = userId,
                    states = packages.associate { it.packageName to it.state },
                )
                runCatching { DeviceCareStore(context).saveSnapshot(snapshot) }
                    .fold(
                        onSuccess = { Result(true, "Snapshot user $userId tersimpan (${packages.size} paket)", packages.size) },
                        onFailure = { Result(false, it.message ?: "Gagal menyimpan snapshot") },
                    )
            }
        },
        onDone = onDone,
    )

    fun hasDebloatSnapshot(context: Context, userId: Int): Boolean =
        DeviceCareStore(context).readSnapshot(userId) != null

    enum class DebloatAction { DISABLE, UNINSTALL_USER, RESTORE }

    fun mutatePackage(
        context: Context,
        packageName: String,
        userId: Int,
        action: DebloatAction,
        dynamicProtected: Set<String>,
        onDone: (Result) -> Unit,
    ) = async(
        block = {
            if (!ShellSafety.isValidUserId(userId) || !ShellSafety.isValidPackageName(packageName)) {
                return@async Result(false, "Paket atau userId tidak valid")
            }
            val effectiveProtected = protectedPackagesForUser(userId, dynamicProtected)
            if (!DebloatPolicy.canMutate(packageName, effectiveProtected)) {
                return@async Result(false, "Paket dilindungi dan tidak boleh diubah")
            }

            // Automatic restore point before the first mutation for this profile.
            val store = DeviceCareStore(context)
            if (store.readSnapshot(userId) == null) {
                val (packages, scanError) = scanDebloatBlocking(userId, dynamicProtected)
                if (scanError != null) return@async Result(false, "Snapshot otomatis gagal: $scanError")
                val saved = runCatching {
                    store.saveSnapshot(
                        DebloatSnapshot(
                            System.currentTimeMillis(),
                            userId,
                            packages.associate { it.packageName to it.state },
                        ),
                    )
                }
                if (saved.isFailure) {
                    return@async Result(false, saved.exceptionOrNull()?.message ?: "Snapshot otomatis gagal")
                }
            }

            val command = when (action) {
                DebloatAction.DISABLE -> "pm disable-user --user $userId $packageName"
                DebloatAction.UNINSTALL_USER -> "pm uninstall -k --user $userId $packageName"
                DebloatAction.RESTORE -> null
            }
            if (command != null) {
                val shell = ShizukuBridge.shell(command)
                    ?: return@async Result(false, "Shizuku shell tidak tersedia")
                if (!shell.ok) return@async Result(false, shell.message)
                val expected = if (action == DebloatAction.DISABLE) DebloatState.DISABLED else DebloatState.UNINSTALLED_USER
                val actual = queryState(packageName, userId)
                if (actual != expected) {
                    return@async Result(false, "Perintah selesai tetapi verifikasi status gagal ($actual)")
                }
                return@async Result(true, "$packageName → ${expected.name}", changed = 1)
            }

            val restored = restoreEnabled(packageName, userId)
            if (!restored.success) return@async restored
            val actual = queryState(packageName, userId)
            if (actual != DebloatState.ENABLED) {
                Result(false, "Restore selesai tetapi paket belum aktif ($actual)")
            } else {
                Result(true, "$packageName dipulihkan", changed = 1)
            }
        },
        onDone = onDone,
    )

    fun restoreDebloatSnapshot(
        context: Context,
        userId: Int,
        dynamicProtected: Set<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onDone: (Result) -> Unit,
    ) = async(
        block = {
            val snapshot = DeviceCareStore(context).readSnapshot(userId)
                ?: return@async Result(false, "Belum ada snapshot untuk user $userId")
            val entries = snapshot.states.entries.sortedBy { it.key }
            val effectiveProtected = protectedPackagesForUser(userId, dynamicProtected)
            var changed = 0
            var skipped = 0
            var firstError: String? = null

            entries.forEachIndexed { index, (pkg, target) ->
                if (!DebloatPolicy.canMutate(pkg, effectiveProtected)) {
                    skipped++
                } else {
                    val current = queryState(pkg, userId)
                    if (current == target || current == DebloatState.UNKNOWN) {
                        skipped++
                    } else {
                        val result = when (target) {
                            DebloatState.ENABLED -> restoreEnabled(pkg, userId)
                            DebloatState.DISABLED -> {
                                if (current == DebloatState.UNINSTALLED_USER) restoreEnabled(pkg, userId)
                                val shell = ShizukuBridge.shell("pm disable-user --user $userId $pkg")
                                if (shell?.ok == true) Result(true, "disabled", 1)
                                else Result(false, shell?.message ?: "shell tidak tersedia")
                            }
                            DebloatState.UNINSTALLED_USER -> {
                                val shell = ShizukuBridge.shell("pm uninstall -k --user $userId $pkg")
                                if (shell?.ok == true) Result(true, "uninstalled", 1)
                                else Result(false, shell?.message ?: "shell tidak tersedia")
                            }
                            DebloatState.UNKNOWN -> Result(true, "skip", skipped = 1)
                        }
                        if (result.success) changed++ else if (firstError == null) firstError = "$pkg: ${result.message}"
                    }
                }
                mainHandler.post { onProgress(index + 1, entries.size) }
            }
            if (firstError == null) {
                Result(true, "Snapshot dipulihkan: $changed perubahan, $skipped dilewati", changed, skipped)
            } else {
                Result(false, "Restore parsial: $changed berubah, $skipped dilewati · $firstError", changed, skipped)
            }
        },
        onDone = onDone,
    )

    private fun queryState(packageName: String, userId: Int): DebloatState {
        if (!ShellSafety.isValidPackageName(packageName) || !ShellSafety.isValidUserId(userId)) {
            return DebloatState.UNKNOWN
        }
        val disabled = listPackages("pm list packages -s -d --user $userId $packageName").first
        if (packageName in disabled) return DebloatState.DISABLED
        val installed = listPackages("pm list packages -s --user $userId $packageName").first
        if (packageName in installed) return DebloatState.ENABLED
        val known = listPackages("pm list packages -s -u --user $userId $packageName").first
        return if (packageName in known) DebloatState.UNINSTALLED_USER else DebloatState.UNKNOWN
    }

    private fun restoreEnabled(packageName: String, userId: Int): Result {
        val current = queryState(packageName, userId)
        if (current == DebloatState.UNINSTALLED_USER) {
            val primary = ShizukuBridge.shell("cmd package install-existing --user $userId $packageName")
            val install = if (primary?.ok == true) primary
                else ShizukuBridge.shell("pm install-existing --user $userId $packageName")
            if (install?.ok != true) return Result(false, install?.message ?: primary?.message ?: "install-existing gagal")
        }
        val enable = ShizukuBridge.shell("pm enable --user $userId $packageName")
            ?: return Result(false, "Shizuku shell tidak tersedia")
        return if (enable.ok) Result(true, "enabled", 1) else Result(false, enable.message)
    }

    // --------------------------------------------------------------- Cleaner

    fun readStorage(onDone: (StorageSpace?, String?) -> Unit) = async(
        block = {
            val result = ShizukuBridge.shell("df -k /data")
            if (result?.ok != true) null to (result?.message ?: "Shell tidak tersedia")
            else StorageSpaceParser.parseDfKilobytes(result.stdout) to null
        },
        onDone = { (space, error) -> onDone(space, error) },
    )

    /** One-shot Android package-manager cache trim. It never kills apps or clears app data. */
    fun trimCaches(requestedReclaimBytes: Long, onDone: (CacheResult) -> Unit) = async(
        block = {
            val beforeResult = ShizukuBridge.shell("df -k /data")
            val before = beforeResult?.takeIf { it.ok }?.let { StorageSpaceParser.parseDfKilobytes(it.stdout) }
            if (requestedReclaimBytes !in 64L * MIB..8L * GIB) {
                return@async CacheResult(false, "Target pembersihan di luar batas aman")
            }
            val desiredFree = before?.let { min(it.totalBytes, it.freeBytes + requestedReclaimBytes) }
                ?: requestedReclaimBytes
            val trim = ShizukuBridge.shell("pm trim-caches $desiredFree")
                ?: return@async CacheResult(false, "Shizuku shell tidak tersedia")
            if (!trim.ok) return@async CacheResult(false, trim.message, before?.freeBytes)
            val after = ShizukuBridge.shell("df -k /data")?.takeIf { it.ok }
                ?.let { StorageSpaceParser.parseDfKilobytes(it.stdout) }
            CacheResult(
                success = true,
                message = "Trim cache selesai tanpa clear data",
                beforeFreeBytes = before?.freeBytes,
                afterFreeBytes = after?.freeBytes,
            )
        },
        onDone = onDone,
    )

    /** Root-only removal of cache/cache-code directories for one validated package. */
    fun cleanPackageCacheRoot(
        packageName: String,
        userId: Int,
        protectedPackages: Set<String>,
        onDone: (CacheResult) -> Unit,
    ) = async(
        block = {
            if (ShizukuBridge.myUid() != 0) return@async CacheResult(false, "Fitur ini membutuhkan Shizuku mode root")
            if (!ShellSafety.isValidPackageName(packageName) || !ShellSafety.isValidUserId(userId)) {
                return@async CacheResult(false, "Paket atau userId tidak valid")
            }
            val effectiveProtected = protectedPackagesForUser(userId, protectedPackages)
            if (packageName in effectiveProtected ||
                DebloatPolicy.classify(packageName, effectiveProtected).tier == DebloatTier.PROTECTED
            ) {
                return@async CacheResult(false, "Cache paket yang dilindungi tidak boleh dibersihkan")
            }

            val paths = listOf(
                "/data/user/$userId/$packageName/cache",
                "/data/user/$userId/$packageName/code_cache",
                "/data/user_de/$userId/$packageName/cache",
                "/data/user_de/$userId/$packageName/code_cache",
                "/storage/emulated/$userId/Android/data/$packageName/cache",
            )
            val joined = paths.joinToString(" ")
            val before = ShizukuBridge.shell("du -sk $joined 2>/dev/null | awk '{s+=\$1} END {print s+0}'")
                ?.stdout?.trim()?.lineSequence()?.lastOrNull()?.toLongOrNull()?.times(1024L)

            // Force-stop is explicit here to prevent the app racing cache deletion and recreating files.
            ShizukuBridge.shell("am force-stop --user $userId $packageName")
            val script = paths.joinToString("; ") { path ->
                "if [ -d $path ]; then find $path -mindepth 1 -maxdepth 1 -exec rm -rf -- {} \\;; fi"
            }
            val clean = ShizukuBridge.shell(script)
                ?: return@async CacheResult(false, "Root shell tidak tersedia", measuredCacheBytes = before)
            if (!clean.ok) return@async CacheResult(false, clean.message, measuredCacheBytes = before)

            val after = ShizukuBridge.shell("du -sk $joined 2>/dev/null | awk '{s+=\$1} END {print s+0}'")
                ?.stdout?.trim()?.lineSequence()?.lastOrNull()?.toLongOrNull()?.times(1024L)
            CacheResult(
                success = true,
                message = "$packageName: cache dibersihkan; data/login tetap utuh",
                measuredCacheBytes = if (before != null && after != null) max(0L, before - after) else before,
            )
        },
        onDone = onDone,
    )

    // ----------------------------------------------------------- Feature Lab

    fun readFeature(feature: FeatureDefinition, userId: Int, onDone: (FeatureState) -> Unit) = async(
        block = { readFeatureBlocking(feature, userId) },
        onDone = onDone,
    )

    private fun readFeatureBlocking(feature: FeatureDefinition, userId: Int): FeatureState {
        if (Build.VERSION.SDK_INT < feature.minSdk) return FeatureState(false, false, emptyMap())
        if (!ShellSafety.isValidUserId(userId)) return FeatureState(false, false, emptyMap(), "userId tidak valid")
        val values = linkedMapOf<String, String?>()
        for (setting in feature.settings) {
            val prefix = if (setting.namespace.perUser) "--user $userId " else ""
            val result = ShizukuBridge.shell("settings ${prefix}get ${setting.namespace.shellName} ${setting.key}")
                ?: return FeatureState(true, false, values, "Shell tidak tersedia")
            if (!result.ok) return FeatureState(true, false, values, result.message)
            val raw = result.stdout.trim().lineSequence().firstOrNull()
            values["${setting.namespace.shellName}/${setting.key}"] = raw?.takeUnless { it == "null" || it.isBlank() }
        }
        val enabled = feature.settings.all { setting ->
            values["${setting.namespace.shellName}/${setting.key}"] == setting.enabledValue
        }
        return FeatureState(true, enabled, values)
    }

    fun setFeature(
        context: Context,
        feature: FeatureDefinition,
        userId: Int,
        enabled: Boolean,
        onDone: (Result) -> Unit,
    ) = async(
        block = {
            if (Build.VERSION.SDK_INT < feature.minSdk) return@async Result(false, "Versi Android tidak didukung")
            if (!ShellSafety.isValidUserId(userId)) return@async Result(false, "userId tidak valid")
            val store = DeviceCareStore(context)

            if (enabled) {
                // Capture each original setting once before making any writes.
                for (setting in feature.settings) {
                    val original = readSetting(setting, userId)
                    if (!original.first) return@async Result(false, original.second ?: "Gagal membaca nilai awal")
                    val storageUserId = if (setting.namespace.perUser) userId else 0
                    store.rememberFeatureValue(
                        feature.id,
                        storageUserId,
                        setting.namespace.shellName,
                        setting.key,
                        original.second,
                    )
                }
                for (setting in feature.settings) {
                    val result = writeSetting(setting, userId, setting.enabledValue)
                    if (!result.success) {
                        restoreFeatureBlocking(store, feature, userId)
                        return@async result
                    }
                }
                val verify = readFeatureBlocking(feature, userId)
                if (!verify.enabled) {
                    restoreFeatureBlocking(store, feature, userId)
                    return@async Result(false, verify.error ?: "ROM menolak atau mengabaikan flag")
                }
                Result(true, "Fitur diaktifkan${if (feature.restartRequired) " · restart mungkin diperlukan" else ""}", 1)
            } else {
                restoreFeatureBlocking(store, feature, userId)
            }
        },
        onDone = onDone,
    )

    private fun readSetting(setting: FeatureSetting, userId: Int): Pair<Boolean, String?> {
        val prefix = if (setting.namespace.perUser) "--user $userId " else ""
        val result = ShizukuBridge.shell("settings ${prefix}get ${setting.namespace.shellName} ${setting.key}")
            ?: return false to "Shell tidak tersedia"
        if (!result.ok) return false to result.message
        return true to result.stdout.trim().takeUnless { it == "null" || it.isBlank() }
    }

    private fun writeSetting(setting: FeatureSetting, userId: Int, value: String?): Result {
        val prefix = if (setting.namespace.perUser) "--user $userId " else ""
        val command = if (value == null) {
            "settings ${prefix}delete ${setting.namespace.shellName} ${setting.key}"
        } else {
            "settings ${prefix}put ${setting.namespace.shellName} ${setting.key} $value"
        }
        val result = ShizukuBridge.shell(command) ?: return Result(false, "Shell tidak tersedia")
        return if (result.ok) Result(true, "ok", 1) else Result(false, result.message)
    }

    private fun restoreFeatureBlocking(store: DeviceCareStore, feature: FeatureDefinition, userId: Int): Result {
        var restored = 0
        var firstError: String? = null
        for (setting in feature.settings) {
            val storageUserId = if (setting.namespace.perUser) userId else 0
            val (remembered, value) = store.featureOriginalValue(
                feature.id,
                storageUserId,
                setting.namespace.shellName,
                setting.key,
            )
            if (!remembered) {
                if (firstError == null) firstError = "Nilai awal belum tersimpan"
                continue
            }
            val result = writeSetting(setting, userId, value)
            if (result.success) restored++ else if (firstError == null) firstError = result.message
        }
        return if (firstError == null) {
            store.forgetFeature(feature.id, userId, feature.settings)
            Result(true, "Nilai bawaan dipulihkan", restored)
        } else {
            Result(false, "Restore parsial: $firstError", restored)
        }
    }

    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB
}
