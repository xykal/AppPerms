package app.overlayops.core

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Helper tipis di atas Shizuku: cek status, minta izin, dan jalankan shell
 * (dipakai sebagai jalur cadangan kalau refleksi binder diblokir ROM).
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"

    const val REQUEST_CODE = 4210
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        Log.w(TAG, "pingBinder gagal", t)
        false
    }

    fun hasPermission(): Boolean = try {
        isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Log.w(TAG, "checkSelfPermission gagal", t)
        false
    }

    fun isRoot(): Boolean = try {
        hasPermission() && Shizuku.getUid() == 0
    } catch (t: Throwable) {
        false
    }

    fun version(): Int = try {
        Shizuku.getVersion()
    } catch (t: Throwable) {
        -1
    }

    /** Shizuku versi < 11 tidak mendukung banyak API yang kita pakai. */
    fun isTooOld(): Boolean = version() in 0..10

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission gagal", t)
        }
    }

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

    /**
     * Jalankan perintah shell dengan hak akses Shizuku. Panggil dari thread IO.
     *
     * Shizuku API 13 menyembunyikan Shizuku.newProcess (masih ada di class, tapi private),
     * jadi dipanggil lewat refleksi. Kalau gagal, kita kembalikan null dan UI memberi tahu user.
     */
    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            Shizuku::class.java
                .getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                .apply { isAccessible = true }
        }.onFailure { Log.w(TAG, "newProcess() tidak bisa diakses", it) }.getOrNull()
    }

    val shellAvailable: Boolean get() = newProcessMethod != null

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

    data class ShellResult(val code: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = code == 0
    }

    // ---------- Perintah `appops` sebagai jalur cadangan ----------

    fun shellSetOp(pkg: String, shellOp: String, status: OpStatus): String? {
        val result = shell("appops set --uid $pkg $shellOp ${OpStatus.shellName(status)}")
            ?: return "perintah appops tidak bisa dijalankan"
        if (!result.ok) return result.stderr.ifBlank { result.stdout }.trim().ifBlank { "exit ${result.code}" }
        return null
    }

    fun shellGetOp(pkg: String, shellOp: String): OpStatus {
        val result = shell("appops get $pkg $shellOp") ?: return OpStatus.UNKNOWN
        val text = result.stdout.ifBlank { result.stderr }
        if (text.isBlank() || text.contains("No operations", ignoreCase = true)) return OpStatus.DEFAULT
        val line = text.lineSequence().firstOrNull { it.contains(shellOp) } ?: text.lineSequence().firstOrNull()
            ?: return OpStatus.DEFAULT
        val value = line.substringAfter(':', "").trim().substringBefore(';').trim()
        return OpStatus.forShellName(value)
    }

    /**
     * Baca mode overlay untuk SEMUA paket sekaligus (4 perintah),
     * jauh lebih cepat daripada memanggil per paket.
     */
    fun shellQueryOverlayBulk(): Map<String, OpStatus>? {
        val map = HashMap<String, OpStatus>()
        var anySuccess = false
        for (status in listOf(OpStatus.ALLOWED, OpStatus.IGNORED, OpStatus.ERRORED, OpStatus.FOREGROUND)) {
            val result = shell("appops query-op ${OpCatalog.OVERLAY.shell} ${OpStatus.shellName(status)}")
                ?: continue
            if (!result.ok) continue
            anySuccess = true
            result.stdout.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("Uid") && !it.contains(':') }
                .forEach { map[it] = status }
        }
        return if (anySuccess) map else null
    }

    fun deviceSummary(): String {
        val shizuku = if (isBinderAlive()) {
            "aktif (v${version()}, uid=${runCatching { Shizuku.getUid() }.getOrDefault(-1)}, " +
                "izin=${if (hasPermission()) "diberikan" else "belum"})"
        } else "tidak berjalan"
        return buildString {
            append("Android ").append(Build.VERSION.RELEASE)
            append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Perangkat: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("ROM: ").append(Build.DISPLAY).append('\n')
            append("Shizuku: ").append(shizuku).append('\n')
            append("Bridge: ").append(AppOpsBridge.describe())
        }
    }
}
