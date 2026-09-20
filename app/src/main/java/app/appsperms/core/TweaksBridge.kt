package app.appsperms.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * OPTIMIZED v1.5 - Safe Tuning Only
 *
 * Perubahan optimasi:
 * - HAPUS fitur berbahaya: am kill-all, pm trim-caches, protectApp (RUN_IN_BACKGROUND/deviceidle whitelist)
 *   yang bikin HP panas, lag, dan ngekill app diam-diam.
 * - Hanya sisa fitur AMAN: wm size/density (dengan auto-revert 15 detik) + animasi global.
 * - Executor tetap single-thread + daemon agar tidak bocor memori.
 *
 * Semua panggilan shell jalan DI SERIAL executor (satu-satu, supaya perintah
 * `wm` yang bergantian tidak adu cepat dengan reset otomatis), hasilnya dikirim balik
 * ke main thread. `onDone(null)` = sukses; pesan error = teks mentah dari ROM.
 */
object TweaksBridge {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "appsperms-tweaks").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Hasil netral dari satu perintah shell. */
    private fun run(command: String): String? {
        val result = ShizukuBridge.shell(command)
            ?: return "Jalur shell Shizuku tidak tersedia (coba laporan perangkat)."
        return if (result.ok) null else result.message
    }

    fun io(block: () -> Unit) = executor.execute(block)

    fun postMain(block: () -> Unit) = mainHandler.post(block)

    // ------------------------------------------------------------- tampilan

    /** Baca `wm size` + `wm density` sekaligus. Error non-fatal: info bisa sebagian null. */
    fun readDisplay(onDone: (info: WmParser.DisplayInfo, error: String?) -> Unit) {
        io {
            val sizeOut = ShizukuBridge.shell("wm size")
            val densOut = ShizukuBridge.shell("wm density")
            val info = WmParser.parseDisplay(
                sizeOut?.stdout.orEmpty(),
                densOut?.stdout.orEmpty(),
            )
            val error = when {
                sizeOut == null -> "shell tidak tersedia"
                sizeOut.stdout.isBlank() && densOut?.stdout.isNullOrBlank() -> sizeOut.message
                else -> null
            }
            postMain { onDone(info, error) }
        }
    }

    fun applySize(width: Int, height: Int, onDone: (String?) -> Unit) =
        applyWithCallback("wm size ${width}x$height", onDone)

    fun applyDensity(density: Int, onDone: (String?) -> Unit) =
        applyWithCallback("wm density $density", onDone)

    fun resetSize(onDone: (String?) -> Unit) = applyWithCallback("wm size reset", onDone)
    fun resetDensity(onDone: (String?) -> Unit) = applyWithCallback("wm density reset", onDone)

    /** Reset KEDUANYA dalam satu perintah agar tidak separuh jalan saat error. */
    fun resetDisplay(onDone: (String?) -> Unit) =
        applyWithCallback("wm size reset && wm density reset", onDone)

    private fun applyWithCallback(command: String, onDone: (String?) -> Unit) {
        io {
            val error = run(command)
            postMain { onDone(error) }
        }
    }

    // -------------------------------------------------------------- animasi

    private val animKeys = listOf(
        "window_animation_scale",
        "transition_animation_scale",
        "animator_duration_scale",
    )

    /** Baca skala animasi global; null = tidak terbaca, nilai = rata-rata (biasanya sama ketiganya). */
    fun readAnimScales(onDone: (Float?) -> Unit) {
        io {
            val out = ShizukuBridge.shell(
                animKeys.joinToString(" ; ") { "settings get global $it" },
            )
            val first = out?.stdout?.lineSequence()?.firstOrNull()?.trim()
            val value = first?.takeIf { it.isNotBlank() && it != "null" }?.toFloatOrNull()
            postMain { onDone(value) }
        }
    }

    fun setAnimScales(scale: Float, onDone: (String?) -> Unit) {
        io {
            // Satu perintah gabungan supaya ketiganya atomik-sekali-pakai.
            val cmd = animKeys.joinToString(" && ") { "settings put global $it $scale" }
            val error = run(cmd)
            postMain { onDone(error) }
        }
    }

    /** Pulihkan snapshot persis; null berarti hapus override dan kembali ke bawaan Android. */
    fun restoreTuning(size: Pair<Int, Int>?, density: Int?, animation: Float?, onDone: (String?) -> Unit) {
        val sizeCmd = size?.let { "wm size ${it.first}x${it.second}" } ?: "wm size reset"
        val densityCmd = density?.let { "wm density $it" } ?: "wm density reset"
        val animCmd = animation?.let { v -> animKeys.joinToString(" && ") { "settings put global $it $v" } }
        applyWithCallback(listOfNotNull(sizeCmd, densityCmd, animCmd).joinToString(" && "), onDone)
    }

    // -------------------------------------------------------------
    // REMOVED FOR SAFETY (v1.5 Optimized):
    // - killBackground() -> am kill-all (bikin ngekill diam-diam, panas, lag reload loop)
    // - trimCaches() -> pm trim-caches (bikin I/O spike, tidak perlu, sistem kelola sendiri)
    // - protectApp()/unprotectApp() -> RUN_IN_BACKGROUND + deviceidle whitelist (bikin battery drain permanen, Doze rusak)
    // - allowOwnOverlay() -> hanya untuk GhostGuard yang sudah dinonaktifkan
    // Semua fungsi di atas dihapus agar tidak ada setting aneh yang bisa bikin HP panas/lag lagi.
}
