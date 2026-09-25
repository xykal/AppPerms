package app.appsperms.model

import android.graphics.drawable.Drawable
import app.appsperms.core.OpStatus

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
    /** Jumlah paket (termasuk app ini) yang berbagi UID sama. Di atas 1 = klon/profil kerja. */
    val sharedUidCount: Int = 1,
    /** UserId dari app (0 = owner, 10+ = work profile, 999 = clone, etc) */
    val userId: Int = 0,
    /** Apakah ini app clone (parallel, dual, shelter, island) */
    val isClone: Boolean = false,
    /** Apakah ini work profile */
    val isWorkProfile: Boolean = false,
    /** Apakah ini emulator */
    val isEmulatorApp: Boolean = false,
) {
    val labelLower: String = label.lowercase()
    val packageLower: String = packageName.lowercase()

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return labelLower.contains(q) || packageLower.contains(q) || uid.toString().contains(q)
    }

    val displayUid: String get() = "$uid (user $userId)"

    val cloneBadge: String? get() = when {
        isClone -> "Clone"
        isWorkProfile -> "Work"
        userId != 0 -> "User $userId"
        sharedUidCount > 1 -> "Shared UID x$sharedUidCount"
        else -> null
    }

    val isSpecialUser: Boolean get() = userId != 0 || isClone || isWorkProfile
}
