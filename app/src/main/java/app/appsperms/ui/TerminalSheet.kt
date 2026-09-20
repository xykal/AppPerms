package app.appsperms.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import app.appsperms.R
import app.appsperms.core.ShizukuBridge
import app.appsperms.core.TerminalBridge
import app.appsperms.databinding.SheetTerminalBinding

/**
 * Terminal Shizuku — seperti LADB / Brevent
 * Jalankan perintah shell via Shizuku (uid 2000) tanpa PC
 * Contoh: pm, appops, dumpsys, settings, wm, cmd
 */
object TerminalSheet {

    fun show(activity: Activity, onNotify: (String) -> Unit = {}) {
        val dialog = BottomSheetDialog(activity)
        val b = SheetTerminalBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(b.root)

        val canOperate = ShizukuBridge.isBinderAlive() && ShizukuBridge.hasPermission()

        // Status
        b.termStatus.text = when {
            !ShizukuBridge.isBinderAlive() -> "⚠️ Shizuku belum jalan — buka Shizuku dulu via Wireless Debugging / Root"
            !ShizukuBridge.hasPermission() -> "⚠️ Shizuku jalan tapi izin belum diberikan — tekan 'Minta izin' di halaman utama"
            ShizukuBridge.myUid() == 0 -> "✅ Shizuku root (uid 0) — semua perintah shell siap"
            else -> "✅ Shizuku shell (uid 2000) — semua perintah shell siap"
        }
        b.termStatus.setTextColor(activity.getColor(if (canOperate) R.color.ok else R.color.warn))
        b.termRun.isEnabled = canOperate
        b.termInput.isEnabled = canOperate
        if (!canOperate) {
            b.termInput.hint = "Sambungkan Shizuku dulu..."
        }

        // Preset chips
        TerminalBridge.PRESETS.forEach { (cmd, label) ->
            val chip = Chip(activity).apply {
                text = label
                isCheckable = false
                setOnClickListener {
                    val current = b.termInput.text?.toString().orEmpty()
                    // Jika sudah ada teks dan preset butuh paket, append
                    if (current.isBlank() || cmd.endsWith(" ")) {
                        b.termInput.setText(cmd)
                    } else {
                        b.termInput.setText(cmd)
                    }
                    b.termInput.setSelection(b.termInput.text?.length ?: 0)
                }
            }
            b.termPresetGroup.addView(chip)
        }

        // History dalam memory (sesi ini)
        val history = mutableListOf<String>()

        fun appendHistory(cmd: String) {
            if (cmd.isBlank() || history.lastOrNull() == cmd) return
            history.add(0, cmd)
            if (history.size > 20) history.removeAt(history.size - 1)
        }

        var lastOutput = b.termOutput.text.toString()

        b.termRun.setOnClickListener {
            val cmd = b.termInput.text?.toString()?.trim().orEmpty()
            if (cmd.isBlank()) {
                Snackbar.make(b.root, "Ketik perintah dulu", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!canOperate) {
                Snackbar.make(b.root, "Shizuku belum siap", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            b.termRun.isEnabled = false
            b.termRun.text = "⏳ Menjalankan…"
            b.termOutput.text = "▶ $cmd\n\n⏳ menunggu shell (uid ${ShizukuBridge.myUid()})..."
            b.termMeta.text = "menjalankan..."

            TerminalBridge.execute(cmd) { res ->
                b.termRun.isEnabled = true
                b.termRun.text = "▶ Jalankan"
                if (res == null) {
                    b.termOutput.text = "Perintah kosong"
                    return@execute
                }
                appendHistory(cmd)
                val out = buildString {
                    append("▶ $cmd\n")
                    append("—".repeat(40)).append("\n")
                    append(res.combined)
                }
                lastOutput = out
                b.termOutput.text = out
                b.termMeta.text = "exit: ${res.exitCode} ${if (res.isSuccess) "✓" else "✗"} · durasi: ${res.durationMs}ms · ${res.stdout.length + res.stderr.length} bytes"
                // auto scroll ke atas
                (b.termOutput.parent as? android.view.View)?.post {
                    (b.termOutput.parent as? android.widget.ScrollView)?.fullScroll(android.widget.ScrollView.FOCUS_UP)
                }
            }
        }

        // Enter untuk jalankan
        b.termInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                b.termRun.performClick()
                true
            } else false
        }

        b.termClear.setOnClickListener {
            b.termOutput.text = "Dibersihkan.\n\nContoh:\n• pm list packages -3\n• appops get com.whatsapp\n• dumpsys deviceidle whitelist"
            b.termMeta.text = "exit: — · durasi: —"
            lastOutput = b.termOutput.text.toString()
        }

        b.termCopy.setOnClickListener {
            val clip = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("terminal_output", b.termOutput.text.toString()))
            Snackbar.make(b.root, "Output disalin", Snackbar.LENGTH_SHORT).show()
        }

        b.termHistoryClear.setOnClickListener {
            history.clear()
            Snackbar.make(b.root, "History sesi ini dibersihkan", Snackbar.LENGTH_SHORT).show()
        }

        // Long press input untuk paste history (simple)
        b.termInput.setOnLongClickListener {
            if (history.isEmpty()) return@setOnLongClickListener false
            MaterialAlertDialogBuilder(activity)
                .setTitle("History perintah")
                .setItems(history.toTypedArray()) { _, which ->
                    b.termInput.setText(history[which])
                    b.termInput.setSelection(b.termInput.text?.length ?: 0)
                }
                .setNegativeButton("Tutup", null)
                .show()
            true
        }

        dialog.show()
    }
}
