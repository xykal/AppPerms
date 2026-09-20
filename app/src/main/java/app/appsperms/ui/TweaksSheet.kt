package app.appsperms.ui

import android.app.Activity
import android.os.CountDownTimer
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.appsperms.R
import app.appsperms.core.Settings as AppPrefs
import app.appsperms.core.TweaksBridge
import app.appsperms.core.WmParser
import app.appsperms.databinding.DialogResolutionBinding
import app.appsperms.databinding.SheetTweaksBinding

/**
 * Sheet "Tuning" v1.5 OPTIMIZED — Hanya 2 kartu AMAN:
 *  1. Resolusi & DPI (wm size / wm density) dengan pengaman auto-revert 15 dtk;
 *  2. Animasi global 0x / 0.5x / 1x;
 *  3. Snapshot & diagnostics (aman, tidak ada kill/whitelist)
 *
 * DIHAPUS permanen (bikin panas/lag/ngekill diam-diam):
 *  - Booster ringan (am kill-all, pm trim-caches)
 *  - Perisai anti ghost-touch (overlay WindowManager)
 *  - Proteksi app / profil whitelist (deviceidle)
 *
 * Semua perintah berat lewat [TweaksBridge] (executor serial + callback main-thread).
 * Konfirmasi "tetap pakai?" disimpan di companion supaya countdown tetap jalan
 * walau activity di-recreate akibat perubahan resolusi — layar tidak bisa macet.
 */
object TweaksSheet {

    /** Konfirmasi countdown yang sedang aktif — bertahan melewati recreate. */
    private var pendingConfirm: PendingConfirm? = null

    private class PendingConfirm(
        var secondsLeft: Long,
        val onRevert: () -> Unit,
        var timer: CountDownTimer? = null,
        var dialog: AlertDialog? = null,
    )

    fun show(activity: Activity, canOperate: Boolean, onNotify: (String) -> Unit) {
        val dialog = BottomSheetDialog(activity)
        val b = SheetTweaksBinding.inflate(android.view.LayoutInflater.from(activity))
        dialog.setContentView(b.root)

        // v1.5: Pastikan sisa setting berbahaya bersih
        AppPrefs.cleanDeprecatedKeys(activity)

        fun alive() = !activity.isFinishing && !activity.isDestroyed

        // ------------------------------------------------------------ tampilan
        TweaksBridge.readDisplay { info, error ->
            if (!alive()) return@readDisplay
            b.dispStatus.text = when {
                error != null -> activity.getString(R.string.tweaks_read_fail, error)
                else -> formatDisplay(activity, info)
            }
        }

        b.btnSizeChange.setOnClickListener {
            if (!canOperate) {
                onNotify(activity.getString(R.string.tweaks_need_shizuku))
                return@setOnClickListener
            }
            TweaksBridge.readDisplay { info, _ ->
                if (alive()) showResolutionDialog(activity, info, canOperate, onNotify)
            }
        }

        b.btnSizeReset.setOnClickListener {
            if (!canOperate) {
                onNotify(activity.getString(R.string.tweaks_need_shizuku))
                return@setOnClickListener
            }
            TweaksBridge.resetSize { e1 ->
                TweaksBridge.resetDensity { e2 ->
                    if (!alive()) return@resetDensity
                    val err = e1 ?: e2
                    onNotify(
                        if (err == null) activity.getString(R.string.tweaks_reset_done)
                        else activity.getString(R.string.tweaks_apply_fail, err),
                    )
                    TweaksBridge.readDisplay { info, _ ->
                        if (alive()) b.dispStatus.text = formatDisplay(activity, info)
                    }
                }
            }
        }

        // ------------------------------------------------------------- animasi
        TweaksBridge.readAnimScales { scale ->
            if (!alive()) return@readAnimScales
            b.animStatus.text = activity.getString(
                R.string.tweaks_anim_current,
                scale?.toString() ?: activity.getString(R.string.tweaks_anim_unknown),
            )
        }
        listOf(b.btnAnimOff to 0f, b.btnAnimHalf to 0.5f, b.btnAnimFull to 1f)
            .forEach { (btn, scale) ->
                btn.setOnClickListener {
                    if (!canOperate) {
                        onNotify(activity.getString(R.string.tweaks_need_shizuku))
                        return@setOnClickListener
                    }
                    TweaksBridge.setAnimScales(scale) { err ->
                        if (!alive()) return@setAnimScales
                        onNotify(
                            if (err == null) activity.getString(R.string.tweaks_anim_set, formatScale(scale))
                            else activity.getString(R.string.tweaks_apply_fail, err),
                        )
                        b.animStatus.text =
                            activity.getString(R.string.tweaks_anim_current, formatScale(scale))
                    }
                }
            }

        // ------------------------------------------------------------- snapshot aman
        b.btnSnapshot.setOnClickListener {
            TweaksBridge.readDisplay { info, err ->
                if (err != null) return@readDisplay onNotify(activity.getString(R.string.tweaks_apply_fail, err))
                TweaksBridge.readAnimScales { anim ->
                    val size = if (info.isSizeOverridden) "${info.overrideW}x${info.overrideH}" else ""
                    val density = info.overrideDensity?.toString().orEmpty()
                    AppPrefs.setTuningSnapshot(activity, "$size|$density|${anim ?: 1f}")
                    onNotify(activity.getString(R.string.snapshot_saved))
                }
            }
        }
        b.btnRestoreSnapshot.setOnClickListener {
            val parts = AppPrefs.tuningSnapshot(activity)?.split('|')
            if (parts == null || parts.size != 3) return@setOnClickListener onNotify(activity.getString(R.string.snapshot_empty))
            val size = parts[0].takeIf { it.isNotBlank() }?.let(WmParser::parseSize)
            val density = parts[1].toIntOrNull()
            val anim = parts[2].toFloatOrNull()
            MaterialAlertDialogBuilder(activity).setTitle(R.string.snapshot_restore)
                .setMessage(R.string.snapshot_restore_confirm)
                .setPositiveButton(R.string.dialog_apply) { _, _ ->
                    TweaksBridge.restoreTuning(size, density, anim) { err ->
                        onNotify(if (err == null) activity.getString(R.string.snapshot_restored)
                            else activity.getString(R.string.tweaks_apply_fail, err))
                    }
                }.setNegativeButton(R.string.dialog_cancel, null).show()
        }
        b.btnTweakDiagnostics.setOnClickListener {
            TweaksBridge.readDisplay { info, _ -> TweaksBridge.readAnimScales { anim ->
                MaterialAlertDialogBuilder(activity).setTitle(R.string.tweak_diag_title)
                    .setMessage(activity.getString(R.string.tweak_diag_body, formatDisplay(activity, info),
                        anim?.let(::formatScale) ?: "?"))
                    .setPositiveButton(R.string.dialog_close, null).show()
            } }
        }

        // Munculkan kembali konfirmasi countdown yang tertunda (mis. sheet dibuka
        // ulang setelah recreate karena resolusi berubah).
        pendingConfirm?.let { pc ->
            showKeepDialog(activity, pc, onDone = { pendingConfirm = null }, onNotify = onNotify)
            onNotify(activity.getString(R.string.res_confirm_hint))
        }

        dialog.show()
    }

    // ------------------------------------------------------ dialog resolusi

    private fun showResolutionDialog(
        activity: Activity,
        info: WmParser.DisplayInfo,
        canOperate: Boolean,
        onNotify: (String) -> Unit,
    ) {
        val rb = DialogResolutionBinding.inflate(android.view.LayoutInflater.from(activity))
        val physical = info.physicalW?.let { w -> info.physicalH?.let { h -> w to h } }
        val physicalDensity = info.physicalDensity

        rb.resCurrent.text = formatDisplay(activity, info)

        var selectedPercent: Int? = null
        WmParser.PRESETS.forEach { pct ->
            val chip = Chip(activity).apply {
                isCheckable = true
                text = if (physical != null) {
                    val (sw, sh) = WmParser.scaledSize(physical.first, physical.second, pct)
                    "$pct% · ${sw}x$sh"
                } else {
                    "$pct%"
                }
                setOnClickListener {
                    selectedPercent = pct
                    rb.resSizeWrap.error = null
                    if (physical != null) {
                        val (sw, sh) = WmParser.scaledSize(physical.first, physical.second, pct)
                        rb.resSizeInput.setText("${sw}x$sh")
                        if (rb.resAutoDensity.isChecked && physicalDensity != null) {
                            rb.resDensityInput.setText(
                                WmParser.scaledDensity(physicalDensity, pct).toString(),
                            )
                        }
                    }
                }
            }
            rb.resPresetGroup.addView(chip)
        }

        // Default: density ikut dihitung otomatis.
        rb.resAutoDensity.isChecked = true
        rb.resDensityWrap.isEnabled = false
        rb.resAutoDensity.setOnCheckedChangeListener { _, checked ->
            rb.resDensityWrap.isEnabled = !checked
            if (checked) {
                rb.resDensityInput.setText("")
            } else if (physical != null && physicalDensity != null) {
                val pct = selectedPercent ?: 100
                rb.resDensityInput.setText(WmParser.scaledDensity(physicalDensity, pct).toString())
            }
        }

        if (info.isSizeOverridden) {
            rb.resSizeInput.setText("${info.overrideW}x${info.overrideH}")
        }

        val dlg = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.res_dialog_title)
            .setView(rb.root)
            .setPositiveButton(R.string.dialog_apply, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!canOperate) {
                    dlg.dismiss()
                    return@setOnClickListener
                }
                val sizeText = rb.resSizeInput.text?.toString().orEmpty()
                val landscape = activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val sizeErr = WmParser.validateSize(sizeText, physical, landscape)
                if (sizeErr != null) {
                    rb.resSizeWrap.error = sizeErr
                    return@setOnClickListener
                }
                val (w, h) = WmParser.resolveSize(sizeText, physical, landscape)!!

                // Density: manual kalau kolomnya aktif; otomatis dari rasio skala.
                val autoDensity = rb.resAutoDensity.isChecked
                var density: Int? = null
                if (autoDensity && physical != null && physicalDensity != null) {
                    val pct = (w.toLong() * 100 / physical.first).toInt()
                    density = WmParser.scaledDensity(physicalDensity, pct)
                } else if (!autoDensity) {
                    val densityText = rb.resDensityInput.text?.toString().orEmpty()
                    if (densityText.isNotBlank()) {
                        val v = densityText.toIntOrNull()
                        if (v == null || v !in WmParser.MIN_DENSITY..WmParser.MAX_DENSITY) {
                            rb.resDensityWrap.error = activity.getString(
                                R.string.res_density_range,
                                WmParser.MIN_DENSITY,
                                WmParser.MAX_DENSITY,
                            )
                            return@setOnClickListener
                        }
                        density = v
                    }
                }

                rb.resSizeWrap.error = null
                rb.resDensityWrap.error = null
                dlg.dismiss()
                applyWithSafetyNet(activity, w, h, density, onNotify)
            }
        }
        dlg.show()
    }

    /**
     * Terapkan override lalu beri jendela 15 detik untuk "Tetap".
     * Countdown hidup di companion, BUKAN di dialog — jadi kalau activity
     * di-recreate oleh perubahan resolusi, auto-revert tetap terjadi.
     */
    private fun applyWithSafetyNet(
        activity: Activity,
        width: Int,
        height: Int,
        density: Int?,
        onNotify: (String) -> Unit,
    ) {
        val pc = PendingConfirm(
            secondsLeft = 15,
            onRevert = { TweaksBridge.resetDisplay { } },
        )
        TweaksBridge.applySize(width, height) { err ->
            if (err != null) {
                onNotify(activity.getString(R.string.tweaks_apply_fail, err))
                return@applySize
            }
            if (density == null) {
                openConfirm(activity, pc, onNotify)
            } else {
                TweaksBridge.applyDensity(density) { err2 ->
                    if (err2 != null) {
                        // density gagal — kembalikan ukuran juga, jangan setengah jalan.
                        TweaksBridge.resetSize {
                            onNotify(activity.getString(R.string.tweaks_apply_fail, err2))
                        }
                    } else {
                        openConfirm(activity, pc, onNotify)
                    }
                }
            }
        }
    }

    private fun openConfirm(activity: Activity, pc: PendingConfirm, onNotify: (String) -> Unit) {
        pendingConfirm = pc
        showKeepDialog(activity, pc, onDone = { pendingConfirm = null }, onNotify = onNotify)
    }

    private fun showKeepDialog(
        activity: Activity,
        pc: PendingConfirm,
        onDone: () -> Unit,
        onNotify: (String) -> Unit = {},
    ) {
        val dlg = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.res_confirm_title)
            .setMessage(activity.getString(R.string.res_confirm_body, pc.secondsLeft.toInt()))
            .setCancelable(false)
            .setPositiveButton(R.string.res_keep) { d, _ ->
                pc.timer?.cancel()
                d.dismiss()
                onDone()
                onNotify(activity.getString(R.string.res_kept))
            }
            .setNegativeButton(R.string.res_revert) { d, _ ->
                pc.timer?.cancel()
                d.dismiss()
                pc.onRevert()
                onDone()
                onNotify(activity.getString(R.string.res_reverted))
            }
            .create()
        pc.dialog = dlg
        pc.timer = object : CountDownTimer(pc.secondsLeft * 1000L, 1000L) {
            override fun onTick(msLeft: Long) {
                if (!dlg.isShowing || activity.isFinishing || activity.isDestroyed) return
                val sec = (msLeft / 1000L).toInt()
                dlg.setMessage(activity.getString(R.string.res_confirm_body, sec))
                pc.secondsLeft = sec.toLong()
            }

            override fun onFinish() {
                if (dlg.isShowing) dlg.dismiss()
                pc.onRevert()
                onDone()
                onNotify(activity.getString(R.string.res_autoreverted))
            }
        }.start()
        dlg.show()
    }

    // ------------------------------------------------------------- utilitas

    private fun formatDisplay(activity: Activity, info: WmParser.DisplayInfo): String {
        val eff = info.effectiveSize ?: return activity.getString(R.string.tweaks_display_unread)
        val base = if (info.isSizeOverridden) {
            activity.getString(
                R.string.tweaks_display_override,
                "${eff.first}x${eff.second}",
                "${info.physicalW}x${info.physicalH}",
            )
        } else {
            activity.getString(R.string.tweaks_display_physical, "${eff.first}x${eff.second}")
        }
        val dens = info.density?.let { d ->
            if (info.isDensityOverridden) " · $d/${info.physicalDensity} dpi" else " · $d dpi"
        }.orEmpty()
        return base + dens
    }

    private fun formatScale(v: Float): String = when (v) {
        0f -> "0×"
        1f -> "1× (bawaan)"
        else -> "${v}×"
    }
}
