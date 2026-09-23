package app.appsperms.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import app.appsperms.R
import app.appsperms.core.ShizukuBridge
import app.appsperms.core.TerminalBridge
import app.appsperms.databinding.ActivityTerminalBinding

/**
 * Layar Dedicated "Terminal Shizuku"
 * Menjalankan perintah shell via Shizuku (uid 2000 / uid 0) dalam tampilan penuh.
 */
class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private val history = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.termToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.termToolbar.setNavigationOnClickListener { finish() }

        val canOperate = ShizukuBridge.isBinderAlive() && ShizukuBridge.hasPermission()

        // Status
        binding.termStatus.text = when {
            !ShizukuBridge.isBinderAlive() -> getString(R.string.term_status_not_running)
            !ShizukuBridge.hasPermission() -> getString(R.string.term_status_no_permission)
            ShizukuBridge.myUid() == 0 -> getString(R.string.term_status_root_ready)
            else -> getString(R.string.term_status_shell_ready)
        }
        binding.termStatus.setTextColor(getColor(if (canOperate) R.color.ok else R.color.warn))
        binding.termRun.isEnabled = canOperate
        binding.termInput.isEnabled = canOperate
        if (!canOperate) {
            binding.termInput.hint = getString(R.string.term_hint_connect)
        }

        // Preset chips
        TerminalBridge.PRESETS.forEach { (cmd, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = false
                setOnClickListener {
                    binding.termInput.setText(cmd)
                    binding.termInput.setSelection(binding.termInput.text?.length ?: 0)
                }
            }
            binding.termPresetGroup.addView(chip)
        }

        binding.termRun.setOnClickListener {
            runCommand()
        }

        binding.termInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                runCommand()
                true
            } else false
        }

        binding.termClear.setOnClickListener {
            binding.termOutput.text = getString(R.string.term_output_cleared)
            binding.termMeta.text = getString(R.string.term_meta_idle)
        }

        binding.termCopy.setOnClickListener {
            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("terminal_output", binding.termOutput.text.toString()))
            Snackbar.make(binding.root, R.string.term_snack_copied, Snackbar.LENGTH_SHORT).show()
        }

        binding.termInput.setOnLongClickListener {
            if (history.isEmpty()) return@setOnLongClickListener false
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.term_history_title)
                .setItems(history.toTypedArray()) { _, which ->
                    binding.termInput.setText(history[which])
                    binding.termInput.setSelection(binding.termInput.text?.length ?: 0)
                }
                .setNegativeButton(R.string.term_history_close, null)
                .show()
            true
        }
    }

    private fun runCommand() {
        val cmd = binding.termInput.text?.toString()?.trim().orEmpty()
        if (cmd.isBlank()) {
            Snackbar.make(binding.root, R.string.term_snack_type_first, Snackbar.LENGTH_SHORT).show()
            return
        }
        val canOperate = ShizukuBridge.isBinderAlive() && ShizukuBridge.hasPermission()
        if (!canOperate) {
            Snackbar.make(binding.root, R.string.term_snack_not_ready, Snackbar.LENGTH_SHORT).show()
            return
        }
        binding.termRun.isEnabled = false
        binding.termRun.text = getString(R.string.term_run_running)
        binding.termOutput.text = "▶ $cmd\n\n" + getString(R.string.term_output_waiting_shell, ShizukuBridge.myUid())
        binding.termMeta.text = getString(R.string.term_meta_running)

        TerminalBridge.execute(cmd) { res ->
            binding.termRun.isEnabled = true
            binding.termRun.text = getString(R.string.term_run_label)
            if (res == null) {
                binding.termOutput.text = getString(R.string.term_output_empty)
                return@execute
            }
            if (cmd.isNotBlank() && history.firstOrNull() != cmd) {
                history.add(0, cmd)
                if (history.size > 30) history.removeAt(history.size - 1)
            }
            val out = buildString {
                append("▶ $cmd\n")
                append("—".repeat(40)).append("\n")
                append(res.combined)
            }
            binding.termOutput.text = out
            binding.termMeta.text = getString(
                R.string.term_meta_exit,
                res.exitCode,
                if (res.isSuccess) "✓" else "✗",
                res.durationMs,
                res.stdout.length + res.stderr.length
            )
            binding.termScroll.post {
                binding.termScroll.fullScroll(android.widget.ScrollView.FOCUS_UP)
            }
        }
    }
}
