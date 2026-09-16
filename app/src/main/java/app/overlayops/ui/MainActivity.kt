package app.overlayops.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import app.overlayops.R
import app.overlayops.core.AccessMode
import app.overlayops.core.OpCatalog
import app.overlayops.core.OpDef
import app.overlayops.core.OpStatus
import app.overlayops.core.ShizukuBridge
import app.overlayops.core.StatusFilter
import app.overlayops.databinding.ActivityMainBinding
import app.overlayops.databinding.DialogAppDetailBinding
import app.overlayops.databinding.ItemOpBinding
import app.overlayops.model.AppEntry
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    private var detailDialog: AlertDialog? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModel.connect { mode -> onAccessChanged(mode) }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        viewModel.connect { mode -> onAccessChanged(mode) }
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        viewModel.connect { mode -> onAccessChanged(mode) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppListAdapter(
            onOpen = { entry -> showAppDetail(entry) },
            onChangeOverlay = { entry ->
                showModePicker(entry, OpCatalog.OVERLAY, entry.overlayStatus) {
                    viewModel.refresh(showSpinner = false)
                }
            },
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        buildTabs()
        buildFilters()

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) =
                viewModel.setQuery(s?.toString().orEmpty())

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.swipe.setOnRefreshListener { viewModel.refresh(showSpinner = false) }
        binding.swipe.setColorSchemeColors(getColor(R.color.brand))
        binding.swipe.setProgressBackgroundColorSchemeColor(getColor(R.color.surface))

        binding.modeChip.setOnClickListener { showConnectionDialog() }
        binding.menuButton.setOnClickListener { showMenu(it) }
        binding.btnGrant.setOnClickListener {
            if (!ShizukuBridge.isBinderAlive()) {
                toast("Shizuku belum berjalan — mulai dulu lewat ADB atau root")
            } else {
                ShizukuBridge.requestPermission()
            }
        }
        binding.btnOpenShizuku.setOnClickListener { openShizukuApp() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ShizukuBridge.addBinderReceivedListener(binderReceivedListener)
        ShizukuBridge.addBinderDeadListener(binderDeadListener)
        ShizukuBridge.addPermissionResultListener(permissionListener)
        viewModel.connect { mode -> onAccessChanged(mode) }
    }

    override fun onStop() {
        ShizukuBridge.removeBinderReceivedListener(binderReceivedListener)
        ShizukuBridge.removeBinderDeadListener(binderDeadListener)
        ShizukuBridge.removePermissionResultListener(permissionListener)
        super.onStop()
    }

    override fun onDestroy() {
        detailDialog?.dismiss()
        super.onDestroy()
    }

    // ------------------------------------------------------------------- setup

    private fun buildTabs() = with(binding.tabs) {
        removeAllTabs()
        addTab(newTab().setText(R.string.tab_overlay), 0, true)
        addTab(newTab().setText(R.string.tab_apps), 1, false)
        addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setTab(tab.position)
                binding.hintBar.setText(
                    if (tab.position == 0) R.string.legend_overlay_hint else R.string.legend_hint
                )
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun buildFilters() {
        binding.filterGroup.removeAllViews()
        StatusFilter.entries.forEach { f ->
            val chip = Chip(this).apply {
                text = getString(f.labelRes)
                isCheckable = true
                isChecked = f == StatusFilter.ALL
                setOnClickListener { viewModel.setFilter(f) }
            }
            binding.filterGroup.addView(chip)
        }
    }

    // --------------------------------------------------------------- rendering

    private fun render(state: UiState) {
        binding.modeChip.text = when (state.accessMode) {
            AccessMode.NONE -> getString(R.string.mode_none)
            AccessMode.SHIZUKU_SHELL -> getString(R.string.mode_shizuku)
            AccessMode.SHIZUKU_ROOT -> getString(R.string.mode_root)
        }
        binding.modeChip.setTextColor(
            if (state.accessMode == AccessMode.NONE) getColor(R.color.deny) else getColor(R.color.ok)
        )
        binding.setupCard.isVisible = state.accessMode == AccessMode.NONE
        binding.swipe.isRefreshing = false
        binding.progress.isVisible = state.loading && state.apps.isEmpty()

        val allowed = state.apps.count { it.overlayStatus == OpStatus.ALLOWED }
        val blocked = state.apps.count { it.overlayStatus == OpStatus.ERRORED || it.overlayStatus == OpStatus.IGNORED }
        binding.subtitle.text = if (state.apps.isEmpty()) {
            "Overlay manager via Shizuku"
        } else {
            "$allowed diizinkan · $blocked diblokir · ${state.apps.size} app"
        }

        val base = when (state.tab) {
            0 -> state.apps.filter { it.declaresOverlay || it.overlayStatus.isExplicit }
            else -> state.apps
        }
        val visible = base.filter { it.matches(state.query) && state.filter.accepts(it) }
        adapter.submitList(visible)

        val empty = visible.isEmpty() && !state.loading
        binding.emptyView.isVisible = empty
        if (empty) {
            val noAccess = state.accessMode == AccessMode.NONE
            binding.emptyTitle.text = when {
                noAccess -> "Shizuku belum tersambung"
                base.isEmpty() -> getString(R.string.no_issues)
                else -> "Tidak ada hasil"
            }
            binding.emptyDesc.text = when {
                noAccess -> "Ikuti langkah di kartu atas untuk menyambungkan Shizuku, lalu tarik layar untuk memuat ulang."
                base.isEmpty() && state.tab == 0 ->
                    "Belum ada app yang punya op overlay eksplisit. Buka tab Semua app untuk melihat seluruh daftar."
                else -> "Coba ubah kata kunci atau filter."
            }
        }
    }

    private fun onAccessChanged(mode: AccessMode) {
        when (mode) {
            AccessMode.SHIZUKU_ROOT, AccessMode.SHIZUKU_SHELL -> {
                toast("Tersambung · ${mode.label}")
                viewModel.refresh()
            }

            AccessMode.NONE -> if (ShizukuBridge.isBinderAlive()) {
                toast("Izin Shizuku belum diberikan")
            }
        }
    }

    // ------------------------------------------------------------------ dialogs

    private fun showConnectionDialog() {
        val summary = viewModel.deviceReport()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.mode_label))
            .setMessage(summary)
            .setPositiveButton(R.string.request_permission) { _, _ ->
                if (ShizukuBridge.isBinderAlive()) ShizukuBridge.requestPermission()
                else toast("Shizuku belum berjalan")
            }
            .setNeutralButton(R.string.open_shizuku) { _, _ -> openShizukuApp() }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Muat ulang daftar")
        popup.menu.add(0, 2, 1, "Laporan perangkat")
        popup.menu.add(0, 3, 2, "Copy laporan")
        popup.menu.add(0, 4, 3, "Buka app Shizuku")
        popup.menu.add(0, 5, 4, "Buka Settings overlay")
        popup.menu.add(0, 6, 5, "Tentang OverlayOps")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> viewModel.refresh()
                2 -> MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.device_report)
                    .setMessage(viewModel.deviceReport())
                    .setPositiveButton("Tutup", null)
                    .show()

                3 -> copyToClipboard(viewModel.deviceReport(), "Laporan perangkat")
                4 -> openShizukuApp()
                5 -> startActivitySafely(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                6 -> MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.about_title)
                    .setMessage(
                        "OverlayOps — pengelola AppOps ringan, fokus ke op " +
                            "SYSTEM_ALERT_WINDOW (Display over other apps).\n\n" +
                            "Cara kerja: app ini bicara langsung ke IAppOpsService lewat Shizuku " +
                            "(atau root), sama seperti `adb shell appops`. Tidak ada data yang dikirim keluar.\n\n" +
                            "Kalau refleksi binder diblokir ROM, otomatis pindah ke perintah `appops`."
                    )
                    .setPositiveButton("Tutup", null)
                    .show()

                else -> false
            }
            true
        }
        popup.show()
    }

    private fun showModePicker(
        entry: AppEntry,
        def: OpDef,
        current: OpStatus,
        onApplied: (() -> Unit)? = null,
    ) {
        val choices = OpStatus.choices
        val labels = choices.map { "${getString(it.labelRes)}  (${OpStatus.shellName(it)})" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("${def.title}\n${entry.label}")
            .setSingleChoiceItems(labels, choices.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                applyStatus(entry, def, choices[which], onApplied)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun applyStatus(entry: AppEntry, def: OpDef, status: OpStatus, onApplied: (() -> Unit)?) {
        viewModel.applyStatus(entry, def, status) { error ->
            if (error == null) {
                toast("${entry.label} · ${def.title} → ${getString(status.labelRes)}")
                onApplied?.invoke()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.apply_failed)
                    .setMessage(error)
                    .setPositiveButton("Tutup", null)
                    .show()
            }
        }
    }

    private fun showAppDetail(entry: AppEntry) {
        val b = DialogAppDetailBinding.inflate(layoutInflater)
        b.detailIcon.setImageDrawable(entry.icon)
        b.detailLabel.text = entry.label
        b.detailPkg.text = entry.packageName
        b.detailBadgeUid.text = "uid ${entry.uid}"
        b.detailBadgeSdk.text = "targetSdk ${entry.targetSdk}"
        b.detailBadgeType.text = if (entry.isSystem) "APP SISTEM" else "APP USER"
        b.btnCopyPkg.setOnClickListener { copyToClipboard(entry.packageName, "Nama paket") }
        b.btnAppInfo.setOnClickListener { openAppInfo(entry.packageName) }
        b.btnSystemSettings.setOnClickListener {
            startActivitySafely(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${entry.packageName}"))
            )
        }
        b.opsContainer.removeAllViews()
        b.opsContainer.addView(
            TextView(this).apply {
                text = getString(R.string.loading)
                setTextColor(getColor(R.color.on_surface_dim))
                textSize = 12f
                setPadding(0, 12, 0, 12)
            }
        )

        detailDialog = MaterialAlertDialogBuilder(this)
            .setView(b.root)
            .setPositiveButton("Tutup", null)
            .create()
        detailDialog?.show()

        viewModel.readOps(entry) { ops -> renderOps(b, entry, ops) }
    }

    private fun renderOps(b: DialogAppDetailBinding, entry: AppEntry, ops: List<Pair<OpDef, OpStatus>>) {
        if (isFinishing || isDestroyed) return
        b.opsContainer.removeAllViews()
        if (ops.isEmpty()) {
            b.opsContainer.addView(
                TextView(this).apply {
                    text = "Tidak bisa membaca AppOps (Shizuku belum siap?)"
                    setTextColor(getColor(R.color.deny))
                    textSize = 12f
                }
            )
            return
        }
        ops.forEach { (def, status) ->
            val row = ItemOpBinding.inflate(layoutInflater, b.opsContainer, false)
            row.opTitle.text = def.title
            row.opDesc.text = "${def.op}\n${def.description}"
            row.opStatus.bindStatusChip(status)
            row.opRow.setOnClickListener {
                showModePicker(entry, def, status) {
                    // baca ulang semua op supaya chip menampilkan mode yang benar-benar tersimpan
                    viewModel.readOps(entry) { fresh -> renderOps(b, entry, fresh) }
                    viewModel.refresh(showSpinner = false)
                }
            }
            b.opsContainer.addView(row.root)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun openShizukuApp() {
        val pm = packageManager
        val intent = pm.getLaunchIntentForPackage(ShizukuBridge.SHIZUKU_PACKAGE)
        if (intent != null) {
            startActivitySafely(intent)
        } else {
            startActivitySafely(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuBridge.SHIZUKU_PACKAGE}"))
            )
        }
    }

    private fun openAppInfo(pkg: String) =
        startActivitySafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    private fun startActivitySafely(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (t: Throwable) {
            toast("Tidak bisa membuka: ${t.message}")
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        toast(getString(R.string.copied, label))
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

}
