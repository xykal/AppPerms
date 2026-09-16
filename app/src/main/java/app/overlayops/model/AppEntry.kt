package app.overlayops.model

import android.graphics.drawable.Drawable
import app.overlayops.core.OpStatus

data class AppEntry(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val targetSdk: Int,
    val enabled: Boolean,
    val declaresOverlay: Boolean,
    val icon: Drawable?,
    val overlayStatus: OpStatus = OpStatus.UNKNOWN,
) {
    val labelLower: String = label.lowercase()
    val packageLower: String = packageName.lowercase()

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return labelLower.contains(q) || packageLower.contains(q)
    }
}
