package app.appsperms.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Terminal Shizuku — eksekutor shell seperti LADB / Brevent
 * Jalan via Shizuku (uid 2000 / root) tanpa ADB wireless PC
 * Semua command dijalankan serial di executor daemon, hasil balik ke main thread
 */
object TerminalBridge {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "appsperms-terminal").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Result(
        val command: String,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
    ) {
        val isSuccess: Boolean get() = exitCode == 0
        val combined: String get() = buildString {
            if (stdout.isNotBlank()) append(stdout)
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("STDERR:\n").append(stderr)
            }
            if (isEmpty()) append("(no output, exit $exitCode)")
        }
    }

    fun execute(command: String, onDone: (Result?) -> Unit) {
        if (command.isBlank()) {
            mainHandler.post { onDone(null) }
            return
        }
        executor.execute {
            val start = System.currentTimeMillis()
            val shellResult = ShizukuBridge.shell(command)
            val result = if (shellResult == null) {
                Result(command, -1, "", "Shell tidak tersedia — Shizuku belum jalan atau izin belum diberikan", System.currentTimeMillis() - start)
            } else {
                Result(command, shellResult.code, shellResult.stdout, shellResult.stderr, System.currentTimeMillis() - start)
            }
            mainHandler.post { onDone(result) }
        }
    }

    fun postMain(block: () -> Unit) = mainHandler.post(block)

    // Preset commands — seperti Brevent / LADB
    val PRESETS = listOf(
        "appops query-op SYSTEM_ALERT_WINDOW allow" to "overlay allow",
        "appops query-op SYSTEM_ALERT_WINDOW deny" to "overlay deny",
        "pm list packages -3" to "list user apps",
        "pm list packages --apex" to "list apex",
        "dumpsys deviceidle whitelist" to "whitelist",
        "dumpsys window | grep -i overlay" to "dump overlay",
        "settings get global window_animation_scale" to "get anim",
        "wm size; wm density" to "check screen",
        "cmd deviceidle get deep" to "deep doze",
        "cmd appops get " to "appops get <pkg>",
    )
}
