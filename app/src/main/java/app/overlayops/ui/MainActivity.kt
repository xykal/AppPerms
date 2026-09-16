package app.overlayops.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import app.overlayops.R
import app.overlayops.core.AccessSnapshot
import app.overlayops.core.AccessState
import app.overlayops.core.AppTypeFilter
import app.overlayops.core.OpCatalog
import app.overlayops.core.OpDef
import app.overlayops.core.OpStatus
import app.overlayops.core.ShizukuBridge
import app.overlayops.core.SortMode
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
    private var lastNotice: String? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { probe("binder masuk") }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { probe("binder mati") }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, granted ->
        probe(if (granted == 0) "izin diberikan" else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppListAdapter(
            onOpen = { entry -> showAppDetail(entry) },
            onChangeOverlay = { entry -> pickOverlayStatus(entry) },
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.list.setHasFixedSize(false)
        binding.list.itemAnimator = null

        buildTabs()
        buildTypeFilter()
        buildStatusFilter()

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
            when {
                !ShizukuBridge.isBinderAlive() ->
                    snack("Shizuku belum berjalan — mulai dulu lewat ADB atau root")

                ShizukuBridge.hasPermission() -> {
                    snack("Izin sudah ada, memeriksa ulang…")
                    probe(null)
                }

                else -> ShizukuBridge.requestPermission()
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
        probe(null)
    }

    override fun onResume() {
        super.onResume()
        // Izin bisa diberikan lewat app Shizuku saat kita di background -> cek ulang.
        probe(null)
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

    private fun probe(notice: String?) {
        lastNotice = notice
        viewModel.refreshAccess { snapshot -> notice?.let { announce(snapshot) } }
    }

    private fun announce(snapshot: AccessSnapshot) {
        when (snapshot.state) {
            AccessState.SHIZUKU_OFF -> snack("Shizuku tidak berjalan")
            AccessState.PERMISSION_DENIED -> snack("Izin Shizuku belum diberikan")
            AccessState.BRIDGE_FAILED ->
                snack("Gagal akses AppOps: ${snapshot.bridgeError ?: "tidak diketahui"}")

            AccessState.SHELL_FALLBACK ->
                snack("Mode shell aktif (binder gagal: ${snapshot.bridgeError ?: "?"})")

            AccessState.READY_SHELL -> snack("Tersambung · Shizuku shell")
            AccessState.READY_ROOT -> snack("Tersambung · Shizuku root")
        }
    }

    private fun buildTabs() = with(binding.tabs) {
        removeAllTabs()
        addTab(newTab().setText(getString(R.string.tab_overlay)), 0, true)
        addTab(newTab().setText(getString(R.string.tab_apps)), 1, false)
        addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = viewModel.setTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun buildTypeFilter() {
        binding.typeFilterGroup.removeAllViews()
        AppTypeFilter.entries.forEach { f ->
            binding.typeFilterGroup.addView(filterChip(getString(f.labelRes), f == AppTypeFilter.ALL) {
                viewModel.setTypeFilter(f)
            })
        }
    }

    private fun buildStatusFilter() {
        binding.statusFilterGroup.removeAllViews()
        StatusFilter.entries.forEach { f ->
            binding.statusFilterGroup.addView(filterChip(getString(f.labelRes), f == StatusFilter.ALL) {
                viewModel.setStatusFilter(f)
            })
        }
    }

    private fun filterChip(text: String, checked: Boolean, onClick: () -> Unit) =
        Chip(this).apply {
            this.text = text
            isCheckable = true
            isChecked = checked
            setOnClickListener { onClick() }
        }

    // --------------------------------------------------------------- rendering

    private fun render(s: UiState) {
        val state = s.snapshot.state
        val color = ContextCompat.getColor(this, state.colorRes)

        binding.modeChip.text = getString(state.chipRes)
        binding.modeChip.setTextColor(color)
        binding.modeChip.backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x2E))

        val needsSetup = state == AccessState.SHIZUKU_OFF ||
            state == AccessState.PERMISSION_DENIED ||
            state == AccessState.BRIDGE_FAILED
        binding.setupCard.isVisible = needsSetup
        if (needsSetup) {
            binding.setupDesc.text = when (state) {
                AccessState.SHIZUKU_OFF -> getString(R.string.setup_off)
                AccessState.PERMISSION_DENIED -> getString(R.string.setup_permission)
                else -> getString(R.string.setup_bridge_failed, s.snapshot.bridgeError ?: "?")
            }
        }
        binding.btnGrant.isEnabled = s.snapshot.binderAlive
        binding.btnGrant.text = getString(
            if (s.snapshot.binderAlive && s.snapshot.permission) R.string.recheck_permission
            else R.string.request_permission
        )

        binding.swipe.isRefreshing = false
        binding.progress.isVisible = s.loading && s.apps.isEmpty()

        binding.tabs.getTabAt(0)?.text = getString(R.string.tab_overlay_count, s.overlayCount)
        binding.tabs.getTabAt(1)?.text = getString(R.string.tab_apps_count, s.apps.size)

        adapter.submitList(s.items)

        val empty = s.items.isEmpty() && !s.loading
        binding.emptyView.isVisible = empty
        if (empty) {
            when {
                !s.snapshot.canOperate -> {
                    binding.emptyTitle.setText(R.string.empty_no_access)
                    binding.emptyDesc.text = getString(R.string.empty_no_access_desc)
                }

                s.apps.isEmpty() -> {
                    binding.emptyTitle.setText(R.string.empty_no_data)
                    binding.emptyDesc.text = getString(R.string.empty_no_data_desc)
                }

                s.tab == 0 && s.query.isBlank() -> {
                    binding.emptyTitle.setText(R.string.empty_no_explicit)
                    binding.emptyDesc.text = getString(R.string.empty_no_explicit_desc)
                }

                else -> {
                    binding.emptyTitle.setText(R.string.empty_no_result)
                    binding.emptyDesc.text = getString(R.string.empty_no_result_desc)
                }
            }
        }

        binding.hintBar.text = when {
            s.busy != null -> s.busy
            s.query.isNotBlank() || s.typeFilter != AppTypeFilter.ALL || s.statusFilter != StatusFilter.ALL ->
                getString(R.string.hint_filtered, s.apps.size, s.overlayCount, s.allowedCount, s.blockedCount)

            else -> getString(R.string.hint_default, s.apps.size, s.overlayCount, s.allowedCount, s.blockedCount)
        }
    }

    // ------------------------------------------------------------------ aksi

    private fun pickOverlayStatus(entry: AppEntry) {
        if (!viewModel.state.value.snapshot.canOperate) {
            snack("Belum tersambung ke Shizuku")
            return
        }
        ModeSheet.show(this, entry, OpCatalog.OVERLAY, entry.overlayStatus) { status ->
            applyAndOfferUndo(entry, OpCatalog.OVERLAY, status, entry.overlayStatus)
        }
    }

    private fun applyAndOfferUndo(entry: AppEntry, def: OpDef, status: OpStatus, previous: OpStatus) {
        viewModel.applyStatus(entry, def, status) { error, _ ->
            if (error == null) {
                snackWithUndo(
                    getString(R.string.applied, entry.label, getString(status.labelRes)),
                ) {
                    viewModel.applyStatus(entry, def, previous) { _, _ -> snack("Dikembalikan") }
                }
            } else {
                showError(error)
            }
        }
    }

    // ------------------------------------------------------------------ menu

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val sortLabel = if (viewModel.state.value.sort == SortMode.NAME) "Status" else "Nama"
        popup.menu.add(0, 1, 0, "Muat ulang daftar")
        popup.menu.add(0, 7, 1, "Urutkan berdasarkan: $sortLabel")
        popup.menu.add(0, 8, 2, "Aksi massal…")
        popup.menu.add(0, 10, 3, "Backup konfigurasi (copy)")
        popup.menu.add(0, 11, 4, "Restore dari backup")
        popup.menu.add(0, 2, 5, "Laporan perangkat")
        popup.menu.add(0, 3, 6, "Copy laporan")
        popup.menu.add(0, 4, 7, "Buka app Shizuku")
        popup.menu.add(0, 5, 8, "Buka Settings overlay")
        popup.menu.add(0, 6, 9, "Tentang OverlayOps")
        popup.setOnMenuItemClickListener { item ->
            handleMenu(item.itemId)
            true
        }
        popup.show()
    }

    private fun handleMenu(id: Int) {
        when (id) {
            1 -> viewModel.refresh()

            2 -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.device_report)
                .setMessage(viewModel.deviceReport())
                .setPositiveButton("Tutup", null)
                .show()

            3 -> copyToClipboard(viewModel.deviceReport(), "Laporan perangkat")
            4 -> openShizukuApp()
            5 -> startActivitySafely(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            6 -> showAbout()
            7 -> {
                val next = if (viewModel.state.value.sort == SortMode.NAME) SortMode.STATUS else SortMode.NAME
                viewModel.setSort(next)
                snack("Urutan: ${getString(next.labelRes)}")
            }

            8 -> showBatchDialog()
            10 -> copyToClipboard(viewModel.exportBackup(), "Backup OverlayOps")
            11 -> showRestoreDialog()
            else -> Unit
        }
    }

    private fun showBatchDialog() {
        val apps = viewModel.appsNow()
        if (apps.isEmpty()) {
            snack("Daftar app masih kosong")
            return
        }
        val userApps = apps.filter { !it.isSystem && (it.declaresOverlay || it.overlayStatus.isExplicit) }
        val declared = apps.filter { it.declaresOverlay }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.batch_title)
            .setItems(
                arrayOf(
                    "Blokir overlay semua app user (${userApps.size})",
                    "Izinkan overlay semua app yang meminta (${declared.size})",
                    "Reset ke default semua yang eksplisit (${apps.count { it.overlayStatus.isExplicit }})",
                ),
            ) { _, which ->
                when (which) {
                    0 -> confirmBatch(userApps, OpStatus.ERRORED, "Blokir overlay")
                    1 -> confirmBatch(declared, OpStatus.ALLOWED, "Izinkan overlay")
                    else -> confirmBatch(
                        apps.filter { it.overlayStatus.isExplicit },
                        OpStatus.DEFAULT,
                        "Reset ke default",
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmBatch(entries: List<AppEntry>, status: OpStatus, title: String) {
        if (entries.isEmpty()) {
            snack("Tidak ada app yang cocok untuk aksi ini")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(getString(R.string.batch_confirm, entries.size, getString(status.labelRes)))
            .setPositiveButton("Jalankan") { _, _ ->
                viewModel.batchApplyOverlay(entries, status) { applied, error, previous ->
                    val message = if (error == null) {
                        getString(R.string.batch_done, applied)
                    } else {
                        getString(R.string.batch_done_error, applied, error)
                    }
                    snackWithUndo(message) {
                        viewModel.applyTargets(previous, "Mengembalikan") { count, err ->
                            snack(if (err == null) "Dikembalikan: $count app" else "Sebagian gagal: $err")
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showRestoreDialog() {
        val clipboardText = readClipboard()
        val input = EditText(this).apply {
            hint = "Tempel hasil backup di sini"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            maxLines = 12
            setTextColor(getColor(R.color.on_surface))
            setHintTextColor(getColor(R.color.on_surface_dim))
            setPadding(0, dp(10), 0, 0)
            if (clipboardText.contains("# OverlayOps backup")) setText(clipboardText)
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(
                input,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_title)
            .setMessage(R.string.restore_desc)
            .setView(container)
            .setPositiveButton("Terapkan") { _, _ ->
                val pairs = viewModel.parseBackup(input.text.toString())
                if (pairs.isEmpty()) {
                    showError("Tidak ada baris valid di teks itu")
                    return@setPositiveButton
                }
                viewModel.restoreFrom(pairs) { applied, skipped, error ->
                    val msg = buildString {
                        append("Restore: $applied app")
                        if (skipped > 0) append(" · $skipped dilewati (tidak terpasang)")
                        error?.let { append(" · error: $it") }
                    }
                    snack(msg)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showConnectionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.connection_title)
            .setMessage(viewModel.deviceReport())
            .setPositiveButton(R.string.request_permission) { _, _ ->
                if (ShizukuBridge.isBinderAlive()) ShizukuBridge.requestPermission()
                else snack("Shizuku belum berjalan")
            }
            .setNeutralButton(R.string.open_shizuku) { _, _ -> openShizukuApp() }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_body))
            .setPositiveButton("Tutup", null)
            .show()
    }

    // ---------------------------------------------------------------- detail

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
                setPadding(0, dp(12), 0, dp(12))
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
                    setText(R.string.ops_unavailable)
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
                ModeSheet.show(this, entry, def, status) { picked ->
                    applyAndOfferUndo(entry, def, picked, status)
                    viewModel.readOps(entry) { fresh -> renderOps(b, entry, fresh) }
                }
            }
            b.opsContainer.addView(row.root)
        }
    }

    // --------------------------------------------------------------- helpers

    private fun openShizukuApp() {
        val intent = packageManager.getLaunchIntentForPackage(ShizukuBridge.SHIZUKU_PACKAGE)
        if (intent != null) {
            startActivitySafely(intent)
        } else {
            startActivitySafely(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuBridge.SHIZUKU_PACKAGE}"),
                )
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
            snack("Tidak bisa membuka: ${t.message}")
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        snack(getString(R.string.copied, label))
    }

    private fun readClipboard(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ""
        return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.apply_failed)
            .setMessage(message)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun snackWithUndo(message: String, onUndo: () -> Unit) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { onUndo() }
            .setActionTextColor(getColor(R.color.brand))
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
