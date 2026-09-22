package app.appsperms.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import app.appsperms.BuildConfig
import app.appsperms.R
import app.appsperms.databinding.SheetAboutBinding

/** Halaman Tentang AppsPerms: versi, audit privasi, kontributor, ekosistem XyVerse, dan feedback. */
object AboutSheet {
    fun show(context: Context) {
        val dialog = BottomSheetDialog(context)
        val b = SheetAboutBinding.inflate(LayoutInflater.from(context))
        b.aboutVersion.text = context.getString(
            R.string.about_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
        b.aboutWebsite.setOnClickListener { open(context, "https://appsperms.xyverse.my.id/") }
        b.aboutSource.setOnClickListener { open(context, "https://github.com/xykalnotkel/OverlayOps") }
        b.aboutUpdate.setOnClickListener { open(context, "https://github.com/xykalnotkel/OverlayOps/releases/latest") }
        b.aboutChannel.setOnClickListener { open(context, "https://t.me/xyverse") }
        b.aboutFeedback.setOnClickListener { open(context, "https://github.com/xykalnotkel/OverlayOps/issues") }
        b.aboutXyDesk.setOnClickListener { open(context, "https://github.com/xykalnotkel/xydesk") }
        b.aboutXyStudio.setOnClickListener { open(context, "https://github.com/xykalnotkel") }
        b.aboutXyCloud.setOnClickListener { open(context, "https://github.com/xykalnotkel") }

        dialog.setContentView(b.root)
        dialog.show()
    }

    private fun open(context: Context, url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
