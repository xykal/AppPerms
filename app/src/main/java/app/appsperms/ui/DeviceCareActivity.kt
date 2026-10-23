package app.appsperms.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.text.Editable
import android.webkit.WebView
import android.text.TextWatcher
import android.text.format.Formatter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import app.appsperms.R
import app.appsperms.core.DebloatPackage
import app.appsperms.core.DebloatState
import app.appsperms.core.DebloatTier
import app.appsperms.core.DeviceCareBridge
import app.appsperms.core.DeviceCareStore
import app.appsperms.core.FeatureCatalog
import app.appsperms.core.FeatureDefinition
import app.appsperms.core.ShizukuBridge
import app.appsperms.databinding.ActivityDeviceCareBinding
import app.appsperms.databinding.DialogDebloatBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar

/**
 * Reversible privileged tools. Every structured command is bounded by DeviceCareBridge;
 * arbitrary shell remains isolated in the explicit Terminal screen.
 */
class DeviceCareActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDeviceCareBinding
    private var selectedUserId: Int = 0
    private var updatingFeatureSwitches = false
    private var cleanerBusy = false

    private val canOperate: Boolean
        get() = ShizukuBridge.isBinderAlive() && ShizukuBridge.hasPermission()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceCareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        selectedUserId = currentUserId()

        setSupportActionBar(binding.deviceCareToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.deviceCareToolbar.setNavigationOnClickListener { finish() }

        binding.btnDeviceCareUser.setOnClickListener { chooseUser() }
        binding.btnDebloatManage.setOnClickListener { showDebloatManager() }
        binding.btnDebloatSnapshot.setOnClickListener { createSnapshot() }
        binding.btnDebloatRestore.setOnClickListener { confirmRestoreSnapshot() }
        binding.btnTrim512.setOnClickListener { confirmTrim(512L * MIB) }
        binding.btnTrim1024.setOnClickListener { confirmTrim(1L * GIB) }
        binding.btnRootCache.setOnClickListener { chooseRootCachePackage() }

        setupFeatureSwitch("freeform_windows", binding.switchFreeform)
        setupFeatureSwitch("desktop_external_display", binding.switchDesktop)
        setupFeatureSwitch("notification_history", binding.switchNotificationHistory)
        setupFeatureSwitch("notification_bubbles", binding.switchBubbles)
    }

    override fun onResume() {
        super.onResume()
        renderAccess()
        refreshStorage()
        refreshFeatures()
    }

    private fun renderAccess() {
        val ready = canOperate
        binding.deviceCareAccessStatus.text = if (ready) {
            getString(R.string.device_care_access_ready, ShizukuBridge.myUid(), selectedUserId)
        } else {
            getString(R.string.device_care_access_missing)
        }
        binding.btnDeviceCareUser.text = getString(R.string.device_care_select_user, selectedUserId)
        binding.btnDebloatManage.isEnabled = ready
        binding.btnDebloatSnapshot.isEnabled = ready
        binding.btnDebloatRestore.isEnabled = ready &&
            DeviceCareBridge.hasDebloatSnapshot(this, selectedUserId)
        binding.btnTrim512.isEnabled = ready && !cleanerBusy
        binding.btnTrim1024.isEnabled = ready && !cleanerBusy
        binding.btnRootCache.isEnabled = ready && !cleanerBusy && ShizukuBridge.myUid() == 0
    }

    private fun chooseUser() {
        if (!canOperate) return snack(getString(R.string.device_care_access_missing))
        binding.btnDeviceCareUser.isEnabled = false
        DeviceCareBridge.listUsers { users ->
            if (isFinishing || isDestroyed) return@listUsers
            binding.btnDeviceCareUser.isEnabled = true
            val available = users.ifEmpty { listOf(currentUserId()) }
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.device_care_select_user, selectedUserId))
                .setItems(available.map { "user $it" }.toTypedArray()) { _, which ->
                    selectedUserId = available[which]
                    renderAccess()
                    refreshFeatures()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    // ---------------------------------------------------------------- Debloat

    private fun showDebloatManager() {
        if (!canOperate) return snack(getString(R.string.device_care_access_missing))
        val dialogBinding = DialogDebloatBinding.inflate(layoutInflater)
        var allRows = emptyList<DebloatUiRow>()
        var selectedTier: DebloatTier? = null
        lateinit var reload: () -> Unit

        val adapter = DebloatAdapter { row -> handleDebloatAction(row, reload) }
        dialogBinding.debloatList.layoutManager = LinearLayoutManager(this)
        dialogBinding.debloatList.adapter = adapter

        fun applyFilter() {
            val query = dialogBinding.debloatSearch.text?.toString().orEmpty().trim().lowercase()
            val filtered = allRows.filter { row ->
                (selectedTier == null || row.item.classification.tier == selectedTier) &&
                    (query.isBlank() || row.label.lowercase().contains(query) ||
                        row.item.packageName.lowercase().contains(query))
            }
            adapter.submitList(filtered)
            dialogBinding.debloatListStatus.text =
                getString(R.string.device_care_packages_count, filtered.size, allRows.size)
        }

        dialogBinding.debloatSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilter()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        dialogBinding.debloatTierGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedTier = when (checkedIds.firstOrNull()) {
                R.id.debloatTierSafe -> DebloatTier.SAFE
                R.id.debloatTierAdvanced -> DebloatTier.ADVANCED
                R.id.debloatTierExpert -> DebloatTier.EXPERT
                else -> null
            }
            applyFilter()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.device_care_debloat_title) + " · user $selectedUserId")
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.dialog_close, null)
            .create()

        reload = {
            dialogBinding.debloatProgress.isVisible = true
            dialogBinding.debloatListStatus.setText(R.string.loading)
            DeviceCareBridge.scanDebloat(selectedUserId, dynamicProtectedPackages()) { packages, error ->
                if (!dialog.isShowing || isFinishing || isDestroyed) return@scanDebloat
                dialogBinding.debloatProgress.isVisible = false
                if (error != null) {
                    dialogBinding.debloatListStatus.text = error
                    allRows = emptyList()
                } else {
                    allRows = packages.map { item -> DebloatUiRow(item, packageLabel(item.packageName)) }
                        .sortedWith(compareBy<DebloatUiRow> { it.item.classification.tier.ordinal }.thenBy { it.label.lowercase() })
                }
                applyFilter()
                renderAccess()
            }
        }
        dialog.setOnShowListener { reload() }
        dialog.show()
    }

    private fun handleDebloatAction(row: DebloatUiRow, onChanged: () -> Unit) {
        val item = row.item
        if (item.state != DebloatState.ENABLED) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.device_care_restore)
                .setMessage(getString(R.string.device_care_restore_package_confirm, row.label, item.userId))
                .setPositiveButton(R.string.device_care_restore) { _, _ ->
                    runDebloatMutation(row, DeviceCareBridge.DebloatAction.RESTORE, onChanged)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.device_care_debloat_actions, row.label))
            .setItems(
                arrayOf(
                    getString(R.string.device_care_disable_user),
                    getString(R.string.device_care_uninstall_user, item.userId),
                ),
            ) { _, which ->
                val action = if (which == 0) DeviceCareBridge.DebloatAction.DISABLE
                    else DeviceCareBridge.DebloatAction.UNINSTALL_USER
                confirmDebloatMutation(row, action, onChanged)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmDebloatMutation(
        row: DebloatUiRow,
        action: DeviceCareBridge.DebloatAction,
        onChanged: () -> Unit,
    ) {
        val actionLabel = when (action) {
            DeviceCareBridge.DebloatAction.DISABLE -> getString(R.string.device_care_disable_user)
            DeviceCareBridge.DebloatAction.UNINSTALL_USER ->
                getString(R.string.device_care_uninstall_user, row.item.userId)
            DeviceCareBridge.DebloatAction.RESTORE -> getString(R.string.device_care_restore)
        }
        val warning = if (row.item.classification.tier == DebloatTier.EXPERT) {
            "\n\n" + getString(R.string.device_care_debloat_expert_warning)
        } else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_care_debloat_confirm_title)
            .setMessage(
                getString(
                    R.string.device_care_debloat_confirm,
                    row.item.packageName,
                    actionLabel,
                ) + warning,
            )
            .setPositiveButton(R.string.dialog_apply) { _, _ ->
                runDebloatMutation(row, action, onChanged)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun runDebloatMutation(
        row: DebloatUiRow,
        action: DeviceCareBridge.DebloatAction,
        onChanged: () -> Unit,
    ) {
        snack(getString(R.string.tweaks_busy))
        DeviceCareBridge.mutatePackage(
            context = this,
            packageName = row.item.packageName,
            userId = row.item.userId,
            action = action,
            dynamicProtected = dynamicProtectedPackages(),
        ) { result ->
            if (isFinishing || isDestroyed) return@mutatePackage
            snack(result.message)
            renderAccess()
            onChanged()
        }
    }

    private fun createSnapshot() {
        if (!canOperate) return snack(getString(R.string.device_care_access_missing))
        binding.btnDebloatSnapshot.isEnabled = false
        DeviceCareBridge.captureDebloatSnapshot(this, selectedUserId, dynamicProtectedPackages()) { result ->
            if (isFinishing || isDestroyed) return@captureDebloatSnapshot
            snack(result.message)
            renderAccess()
        }
    }

    private fun confirmRestoreSnapshot() {
        if (!DeviceCareBridge.hasDebloatSnapshot(this, selectedUserId)) {
            return snack(getString(R.string.device_care_no_snapshot))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_care_restore_snapshot_title)
            .setMessage(getString(R.string.device_care_restore_snapshot_body, selectedUserId))
            .setPositiveButton(R.string.device_care_restore) { _, _ ->
                binding.btnDebloatRestore.isEnabled = false
                DeviceCareBridge.restoreDebloatSnapshot(
                    context = this,
                    userId = selectedUserId,
                    dynamicProtected = dynamicProtectedPackages(),
                    onProgress = { done, total ->
                        if (!isFinishing && !isDestroyed) {
                            binding.deviceCareAccessStatus.text = "$done/$total"
                        }
                    },
                ) { result ->
                    if (isFinishing || isDestroyed) return@restoreDebloatSnapshot
                    snack(result.message)
                    renderAccess()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- Cleaner

    private fun refreshStorage() {
        if (!canOperate || cleanerBusy) return
        DeviceCareBridge.readStorage { space, error ->
            if (isFinishing || isDestroyed || cleanerBusy) return@readStorage
            binding.cleanerStatus.text = if (space != null) {
                getString(
                    R.string.device_care_storage,
                    formatBytes(space.freeBytes),
                    formatBytes(space.totalBytes),
                )
            } else {
                getString(R.string.device_care_storage_error, error ?: "?")
            }
        }
    }

    private fun confirmTrim(bytes: Long) {
        if (!canOperate || cleanerBusy) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_care_trim_confirm_title)
            .setMessage(getString(R.string.device_care_trim_confirm, formatBytes(bytes)))
            .setPositiveButton(R.string.dialog_run) { _, _ -> runTrim(bytes) }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun runTrim(bytes: Long) {
        setCleanerBusy(true)
        DeviceCareBridge.trimCaches(bytes) { result ->
            if (isFinishing || isDestroyed) return@trimCaches
            val reclaimed = result.reclaimedBytes?.let(::formatBytes) ?: "—"
            binding.cleanerStatus.text = getString(R.string.device_care_clean_result, result.message, reclaimed)
            snack(result.message)
            // Cooldown prevents repeated I/O bursts even when a ROM returns immediately.
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    setCleanerBusy(false)
                    refreshStorage()
                }
            }, CLEANER_COOLDOWN_MS)
        }
    }

    private fun setCleanerBusy(value: Boolean) {
        cleanerBusy = value
        if (value) binding.cleanerStatus.setText(R.string.device_care_cleaning)
        renderAccess()
    }

    private fun chooseRootCachePackage() {
        if (ShizukuBridge.myUid() != 0) return snack(getString(R.string.device_care_root_required))
        val protected = dynamicProtectedPackages()
        val apps = packageManager.getInstalledApplications(
            android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS,
        ).asSequence()
            .filter { it.packageName !in protected && it.packageName != packageName }
            .map { info -> packageManager.getApplicationLabel(info).toString() to info.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
            .toList()
        if (apps.isEmpty()) return snack(getString(R.string.snack_no_apps))

        val labels = apps.map { "${it.first}\n${it.second}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_care_root_cache)
            .setItems(labels) { _, which -> confirmRootClean(apps[which].first, apps[which].second, protected) }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmRootClean(label: String, packageName: String, protected: Set<String>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_care_root_confirm_title)
            .setMessage(getString(R.string.device_care_root_confirm, "$label\n$packageName"))
            .setPositiveButton(R.string.dialog_run) { _, _ ->
                setCleanerBusy(true)
                DeviceCareBridge.cleanPackageCacheRoot(packageName, selectedUserId, protected) { result ->
                    if (isFinishing || isDestroyed) return@cleanPackageCacheRoot
                    val reclaimed = result.reclaimedBytes?.let(::formatBytes) ?: "—"
                    binding.cleanerStatus.text =
                        getString(R.string.device_care_clean_result, result.message, reclaimed)
                    snack(result.message)
                    binding.root.postDelayed({
                        if (!isFinishing && !isDestroyed) setCleanerBusy(false)
                    }, CLEANER_COOLDOWN_MS)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ------------------------------------------------------------ Feature Lab

    private fun featureSwitches(): Map<String, MaterialSwitch> = mapOf(
        "freeform_windows" to binding.switchFreeform,
        "desktop_external_display" to binding.switchDesktop,
        "notification_history" to binding.switchNotificationHistory,
        "notification_bubbles" to binding.switchBubbles,
    )

    private fun setupFeatureSwitch(id: String, switch: MaterialSwitch) {
        val feature = FeatureCatalog.byId(id) ?: return
        switch.setOnCheckedChangeListener { _, checked ->
            if (updatingFeatureSwitches) return@setOnCheckedChangeListener
            updatingFeatureSwitches = true
            switch.isChecked = !checked
            updatingFeatureSwitches = false
            if (!canOperate) return@setOnCheckedChangeListener snack(getString(R.string.device_care_access_missing))
            confirmFeatureChange(feature, switch.text.toString(), checked)
        }
    }

    private fun refreshFeatures() {
        val ready = canOperate
        featureSwitches().forEach { (id, switch) ->
            val feature = FeatureCatalog.byId(id) ?: return@forEach
            switch.isEnabled = false
            if (!ready) return@forEach
            DeviceCareBridge.readFeature(feature, selectedUserId) { state ->
                if (isFinishing || isDestroyed) return@readFeature
                val store = DeviceCareStore(this)
                val managed = feature.settings.all {
                    val storageUserId = if (it.namespace.perUser) selectedUserId else 0
                    store.featureOriginalValue(
                        feature.id,
                        storageUserId,
                        it.namespace.shellName,
                        it.key,
                    ).first
                }
                updatingFeatureSwitches = true
                switch.isChecked = state.enabled
                updatingFeatureSwitches = false
                // Never turn off a ROM default without a saved original value.
                switch.isEnabled = state.supported && state.error == null && (!state.enabled || managed)
            }
        }
    }

    private fun confirmFeatureChange(feature: FeatureDefinition, label: String, enable: Boolean) {
        val message = if (enable) {
            getString(R.string.device_care_feature_enable_confirm, label, selectedUserId)
        } else {
            getString(R.string.device_care_feature_restore_confirm, label)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_care_feature_confirm_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_apply) { _, _ ->
                featureSwitches()[feature.id]?.isEnabled = false
                DeviceCareBridge.setFeature(this, feature, selectedUserId, enable) { result ->
                    if (isFinishing || isDestroyed) return@setFeature
                    snack(result.message)
                    refreshFeatures()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- Helpers

    private fun packageLabel(pkg: String): String = runCatching {
        val flags = android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
            android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, flags)).toString()
    }.getOrDefault(pkg)

    private fun dynamicProtectedPackages(): Set<String> = buildSet {
        add(packageName)
        packageManager.resolveActivity(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName?.let(::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(this@DeviceCareActivity) }.getOrNull()?.let(::add)
        runCatching {
            (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
        }.getOrNull()?.let(::add)
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let(::add)
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')
            ?.mapNotNull(ComponentName::unflattenFromString)
            ?.mapTo(this) { it.packageName }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching { WebView.getCurrentWebViewPackage()?.packageName }.getOrNull()?.let(::add)
        }
        runCatching {
            (getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
                .activeAdmins.orEmpty().map { it.packageName }
        }.getOrDefault(emptyList()).let(::addAll)
    }

    private fun currentUserId(): Int = Process.myUid() / 100_000

    private fun formatBytes(bytes: Long): String = Formatter.formatFileSize(this, bytes)

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val MIB = 1024L * 1024L
        private const val GIB = 1024L * MIB
        private const val CLEANER_COOLDOWN_MS = 20_000L
    }
}
