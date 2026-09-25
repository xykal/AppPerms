package app.appsperms.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper Shizuku: probe status akses, minta izin, dan jalankan perintah `appops`
 * (jalur utama yang paling tahan-banting; binder dipakai sebagai bonus).
 *
 * v1.7.4 improvements:
 * - Support semua UID (owner, work profile, clone, parallel)
 * - Support emulator detection
 * - Support multi-user via --user flag
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"

    const val REQUEST_CODE = 4210
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    // ---------------------------------------------------------------- status

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun myUid(): Int = try {
        Shizuku.getUid()
    } catch (t: Throwable) {
        -1
    }

    fun version(): Int = try {
        Shizuku.getVersion()
    } catch (t: Throwable) {
        -1
    }

    fun isInstalledHuh(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (t: Throwable) {
        false
    }

    /** Satu tembakan: semua informasi yang dibutuhkan UI untuk memutuskan pesan yang tepat. */
    fun probe(context: Context, connectBridge: Boolean = true): AccessSnapshot {
        val alive = isBinderAlive()
        val allowed = alive && hasPermission()
        val shellOk = allowed && shellAvailable

        var bridgeReady = false
        var bridgeError: String? = null
        if (allowed && connectBridge) {
            bridgeReady = AppOpsBridge.connect()
            bridgeError = AppOpsBridge.lastError
        }

        return AccessSnapshot(
            shizukuInstalled = isInstalledHuh(context),
            binderAlive = alive,
            permission = allowed,
            uid = if (alive) myUid() else -1,
            version = if (alive) version() else -1,
            bridgeReady = bridgeReady,
            bridgeError = bridgeError,
            shellAvailable = shellOk,
            shellError = if (!shellOk && allowed) "Shizuku.newProcess() tidak bisa diakses" else null,
        )
    }

    fun requestPermission() = runCatching { Shizuku.requestPermission(REQUEST_CODE) }

    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) =
        runCatching { Shizuku.addBinderReceivedListenerSticky(listener) }

    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) =
        runCatching { Shizuku.removeBinderReceivedListener(listener) }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) =
        runCatching { Shizuku.addBinderDeadListener(listener) }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) =
        runCatching { Shizuku.removeBinderDeadListener(listener) }

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) =
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) =
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }

    // ----------------------------------------------------------------- shell

    private val newProcessMethod: Method? by lazy {
        runCatching {
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                )
                .apply { isAccessible = true }
        }.onFailure { Log.w(TAG, "newProcess() tidak bisa diakses", it) }.getOrNull()
    }

    val shellAvailable: Boolean get() = newProcessMethod != null

    data class ShellResult(val code: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = code == 0
        val message: String get() = stderr.ifBlank { stdout }.trim().ifBlank { "exit $code" }
    }

    /** Jalankan perintah dengan hak akses Shizuku. Panggil dari thread IO. */
    fun shell(command: String): ShellResult? {
        val method = newProcessMethod ?: return null
        return try {
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val errBuffer = StringBuilder()
            val errThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { errBuffer.append(it).append('\n') }
                }
            }.apply { isDaemon = true; start() }
            val stdout = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            runCatching { errThread.join(1_000) }
            ShellResult(code, stdout, errBuffer.toString())
        } catch (t: Throwable) {
            Log.w(TAG, "shell gagal: $command", t)
            null
        }
    }

    // ------------------------------------------------------- perintah appops

    /** Daftar userId di device (0 = owner, 10 = work profile, 999 = clone, etc) */
    fun listUsers(): List<Int> {
        val result = shell("pm list users") ?: return listOf(0)
        if (!result.ok) return listOf(0)
        // Output: Users: UserInfo{0:Owner:13} running, UserInfo{10:Work profile:30} running
        val regex = Regex("""UserInfo\{(\d+):""")
        val users = regex.findAll(result.stdout).map { it.groupValues[1].toInt() }.toList()
        return if (users.isNotEmpty()) users else listOf(0)
    }

    /** Apakah device ini emulator? */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                Build.PRODUCT.contains("sdk") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu"))
    }

    /** Baca mode overlay SEMUA paket sekaligus (4 perintah) — jauh lebih cepat */
    fun shellQueryOverlayBulk(): Map<String, OpStatus>? {
        val map = HashMap<String, OpStatus>()
        var anySuccess = false
        for (status in listOf(OpStatus.ALLOWED, OpStatus.IGNORED, OpStatus.ERRORED, OpStatus.FOREGROUND)) {
            val result = shell("appops query-op ${OpCatalog.OVERLAY.shell} ${OpStatus.shellName(status)}")
                ?: continue
            if (!result.ok) continue
            anySuccess = true
            AppOpsParser.parseQueryOpPackages(result.stdout).forEach { map[it] = status }
        }
        return if (anySuccess) map else null
    }

    /** Query overlay untuk semua user */
    fun shellQueryOverlayBulkAllUsers(): Map<String, OpStatus> {
        val combined = HashMap<String, OpStatus>()
        val users = listUsers()
        for (userId in users) {
            val result = shell("appops query-op --user $userId ${OpCatalog.OVERLAY.shell} ${OpStatus.shellName(OpStatus.ALLOWED)}")
            if (result?.ok == true) {
                AppOpsParser.parseQueryOpPackages(result.stdout).forEach { pkg ->
                    combined["$pkg:user$userId"] = OpStatus.ALLOWED
                    if (!combined.containsKey(pkg)) combined[pkg] = OpStatus.ALLOWED
                }
            }
        }
        // Fallback to bulk without user flag
        shellQueryOverlayBulk()?.let { combined.putAll(it) }
        return combined
    }

    /** Semua op milik satu paket dalam SATU perintah: `appops get <pkg>`. */
    fun shellReadOps(pkg: String): Map<String, OpStatus>? {
        val result = shell("appops get $pkg") ?: return null
        if (!result.ok) return null
        val parsed = parseAppOpsGet(result.stdout)
        if (parsed.isEmpty()) return null
        return parsed
    }

    /** Baca ops untuk user tertentu */
    fun shellReadOpsForUser(pkg: String, userId: Int): Map<String, OpStatus>? {
        val result = shell("appops get --user $userId $pkg") ?: shell("appops get $pkg")
        if (result?.ok != true) return null
        return parseAppOpsGet(result.stdout).ifEmpty { null }
    }

    fun parseAppOpsGet(output: String): Map<String, OpStatus> =
        AppOpsParser.parseAppOpsGet(output)

    /** Tulis satu op: `appops set --uid <pkg> <OP> <mode>` dengan support multi-user */
    fun shellSetOp(pkg: String, shellOp: String, status: OpStatus): String? {
        // Try with --uid first (sets for all UIDs, handles clones)
        val resultUid = shell("appops set --uid $pkg $shellOp ${OpStatus.shellName(status)}")
        if (resultUid?.ok == true) return null

        // Fallback: try per-user
        val users = listUsers()
        var lastError: String? = resultUid?.message
        for (userId in users) {
            val result = shell("appops set --user $userId $pkg $shellOp ${OpStatus.shellName(status)}")
            if (result?.ok == true) return null
            lastError = result?.message ?: lastError
        }

        // Last fallback: without any flag
        val resultPlain = shell("appops set $pkg $shellOp ${OpStatus.shellName(status)}")
        return if (resultPlain?.ok == true) null else resultPlain?.message ?: lastError ?: "perintah appops tidak bisa dijalankan"
    }

    /** Set op untuk semua user sekaligus */
    fun shellSetOpAllUsers(pkg: String, shellOp: String, status: OpStatus): String? {
        val users = listUsers()
        var firstError: String? = null
        var anySuccess = false
        for (userId in users) {
            val result = shell("appops set --user $userId $pkg $shellOp ${OpStatus.shellName(status)}")
            if (result?.ok == true) anySuccess = true else if (firstError == null) firstError = result?.message
        }
        // Also try --uid
        val resultUid = shell("appops set --uid $pkg $shellOp ${OpStatus.shellName(status)}")
        if (resultUid?.ok == true) anySuccess = true

        return if (anySuccess) null else firstError ?: "gagal set untuk semua user"
    }

    /** Baca satu op (fallback kalau `appops get <pkg>` tidak bisa diparse). */
    fun shellGetOp(pkg: String, shellOp: String): OpStatus {
        val result = shell("appops get $pkg $shellOp") ?: return OpStatus.UNKNOWN
        val text = result.stdout.ifBlank { result.stderr }
        if (text.isBlank() || text.contains("No operations", ignoreCase = true)) return OpStatus.DEFAULT
        val parsed = parseAppOpsGet(text)
        parsed[shellOp]?.let { return it }
        val line = text.lineSequence().firstOrNull { it.contains(shellOp) } ?: return OpStatus.DEFAULT
        return OpStatus.forShellName(line.substringAfter(':').trim().substringBefore(';'))
    }

    // ------------------------------------------------------------ diagnostics

    fun deviceSummary(context: Context, snapshot: AccessSnapshot = probe(context)): String {
        val shizuku = when {
            !snapshot.shizukuInstalled -> "belum terinstall"
            !snapshot.binderAlive -> "terinstall, tapi belum berjalan"
            !snapshot.permission -> "berjalan (v${snapshot.version}), izin BELUM diberikan"
            else -> "berjalan (v${snapshot.version}, uid=${snapshot.uid}, ${if (snapshot.isRoot) "root" else "shell"})"
        }
        val users = if (snapshot.permission) listUsers() else emptyList()
        val emulator = isEmulator()
        return buildString {
            append("Android ").append(Build.VERSION.RELEASE)
            append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Perangkat: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("ROM: ").append(Build.DISPLAY).append('\n')
            append("Emulator: ").append(if (emulator) "Ya (deteksi: ${Build.MODEL})" else "Tidak").append('\n')
            append("Users: ").append(users.joinToString(", ").ifEmpty { "0 (owner only)" }).append('\n')
            append("Shizuku: ").append(shizuku).append('\n')
            append("Status: ").append(snapshot.state.name).append('\n')
            append("Jalur aktif: ").append(if (snapshot.preferShell) "perintah `appops` (shell)" else "binder IAppOpsService").append('\n')
            append("Binder: ").append(if (snapshot.bridgeReady) "OK" else "gagal").append('\n')
            snapshot.bridgeError?.let { append("  error binder: ").append(it).append('\n') }
            append("Shell Shizuku: ").append(if (snapshot.shellAvailable) "OK" else "tidak tersedia").append('\n')
            append("opCode(overlay): ").append(AppOpsBridge.opCode(OpCatalog.OVERLAY.op) ?: "belum ter-resolve (pakai fallback 24)").append('\n')
            append("Timestamp: ").append(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
        }
    }
}
