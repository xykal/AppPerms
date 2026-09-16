package app.overlayops.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import app.overlayops.R
import app.overlayops.core.OpCatalog
import app.overlayops.core.OpDef
import app.overlayops.core.OpStatus
import app.overlayops.databinding.ItemModeOptionBinding
import app.overlayops.databinding.SheetModeBinding
import app.overlayops.model.AppEntry

/**
 * Bottom sheet pemilih mode: tiap opsi diberi penjelasan singkat + konsekuensinya,
 * plus peringatan kalau memblokir overlay app yang memang meminta izin itu.
 */
object ModeSheet {

    fun show(
        context: Context,
        entry: AppEntry,
        def: OpDef,
        current: OpStatus,
        onPick: (OpStatus) -> Unit,
    ) {
        val dialog = BottomSheetDialog(context)
        val b = SheetModeBinding.inflate(LayoutInflater.from(context))

        b.sheetTitle.text = def.title
        b.sheetSub.text = "${entry.label} · ${entry.packageName}"
        b.sheetCurrent.text = context.getString(
            R.string.sheet_current,
            context.getString(current.labelRes),
            OpStatus.shellName(current),
        )

        if (def.op == OpCatalog.OVERLAY.op && entry.declaresOverlay) {
            b.sheetWarning.isVisible = true
            b.sheetWarning.text = context.getString(R.string.warn_overlay_break)
        }

        OpStatus.choices.forEach { status ->
            val row = ItemModeOptionBinding.inflate(LayoutInflater.from(context), b.modeContainer, false)
            row.optionTitle.text = context.getString(status.labelRes)
            row.optionDesc.text = context.getString(status.descRes)
            row.optionDot.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, status.colorRes))
            row.optionNow.isVisible = status == current
            row.optionRoot.setOnClickListener {
                dialog.dismiss()
                onPick(status)
            }
            b.modeContainer.addView(row.root)
        }

        dialog.setContentView(b.root)
        dialog.show()
    }
}
