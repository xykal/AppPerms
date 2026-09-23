package app.appsperms.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import app.appsperms.R
import app.appsperms.core.AppOpsBridge
import app.appsperms.core.HistoryCodec
import app.appsperms.core.HistoryLine
import app.appsperms.core.HistoryStore
import app.appsperms.core.OpCatalog
import app.appsperms.core.OpStatus
import app.appsperms.core.ShizukuBridge
import app.appsperms.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Layar Dedicated "Riwayat Perubahan"
 * Melihat log edit AppOps, melakukan Undo massal, membersihkan riwayat, dan menyalin catatan audit.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val store by lazy { HistoryStore(this) }
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.historyToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.historyToolbar.setNavigationOnClickListener { finish() }

        loadHistory()
    }

    private fun loadHistory() {
        ioExecutor.execute {
            val lines = store.read().reversed() // Terbaru di atas
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                renderLines(lines)
            }
        }
    }

    private fun renderLines(lines: List<HistoryLine>) {
        val canOperate = ShizukuBridge.isBinderAlive() && ShizukuBridge.hasPermission()
        binding.historyCountText.text = getString(R.string.history_subtitle, lines.size)
        binding.historyEmptyView.isVisible = lines.isEmpty()
        binding.historyScrollView.isVisible = lines.isNotEmpty()

        binding.historyListContainer.removeAllViews()

        val timeFmt = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())

        lines.take(100).forEach { line ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setBackgroundResource(R.drawable.bg_card)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 0, 0, dp(8))
                }
                layoutParams = params
            }

            val def = OpCatalog.byName(line.op)
            val opLabel = def?.let { getString(it.titleRes) }
                ?: line.op.substringAfter(':').lowercase(Locale.US).replace('_', ' ')

            val head = TextView(this).apply {
                textSize = 13.5f
                setTextColor(getColor(R.color.on_surface))
                text = "$opLabel · ${statusArrow(line.from)} → ${statusArrow(line.to)}"
            }

            val sub = TextView(this).apply {
                textSize = 11f
                setTextColor(getColor(R.color.on_surface_dim))
                text = "${line.pkg} · ${timeFmt.format(Date(line.timeMs))}"
            }

            row.addView(head)
            row.addView(sub)
            binding.historyListContainer.addView(row)
        }

        val undoPlan = HistoryCodec.undoPlan(lines)
        binding.btnHistoryUndo.isEnabled = canOperate && lines.isNotEmpty()
        binding.btnHistoryUndo.text = getString(R.string.history_undo_all, undoPlan.size)

        binding.btnHistoryUndo.setOnClickListener {
            confirmUndo(undoPlan)
        }

        binding.btnHistoryCopy.setOnClickListener {
            if (lines.isEmpty()) {
                Snackbar.make(binding.root, R.string.history_empty, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val text = HistoryCodec.renderText(lines) { op ->
                OpCatalog.byName(op)?.let { getString(it.titleRes) } ?: op
            }
            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("history_report", text))
            Snackbar.make(binding.root, R.string.history_copied, Snackbar.LENGTH_SHORT).show()
        }

        binding.btnHistoryClear.setOnClickListener {
            if (lines.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history_btn_clear)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.dialog_clear) { _, _ ->
                    ioExecutor.execute {
                        store.clear()
                        runOnUiThread {
                            loadHistory()
                            Snackbar.make(binding.root, R.string.history_cleared, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun confirmUndo(plan: List<HistoryLine>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history_undo_title)
            .setMessage(getString(R.string.history_undo_confirm, plan.size))
            .setPositiveButton(R.string.dialog_apply) { _, _ ->
                binding.btnHistoryUndo.isEnabled = false
                binding.btnHistoryUndo.setText(R.string.history_undoing)
                ioExecutor.execute {
                    var applied = 0
                    var skipped = 0
                    for (item in plan) {
                        val current = AppOpsBridge.getOp(item.pkg, item.op)
                        if (current == item.from) {
                            skipped++
                            continue
                        }
                        val err = AppOpsBridge.setOp(item.pkg, item.op, item.from)
                        if (err == null) applied++ else skipped++
                    }
                    store.clear()
                    runOnUiThread {
                        loadHistory()
                        val msg = getString(R.string.history_undo_done, applied, skipped)
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun statusArrow(status: OpStatus): String = when (status) {
        OpStatus.ALLOWED -> getString(R.string.status_allowed)
        OpStatus.ERRORED -> getString(R.string.status_errored)
        OpStatus.IGNORED -> getString(R.string.status_ignored)
        OpStatus.DEFAULT -> getString(R.string.status_default)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
