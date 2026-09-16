package app.overlayops.core

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import app.overlayops.R
import app.overlayops.model.AppEntry

/**
 * Status sebuah AppOp, dipetakan langsung ke mode integer di AppOpsManager.
 */
enum class OpStatus(
    val mode: Int,
    @StringRes val labelRes: Int,
    @ColorRes val colorRes: Int,
) {
    ALLOWED(0, R.string.status_allowed, R.color.ok),
    IGNORED(1, R.string.status_ignored, R.color.ignored),
    ERRORED(2, R.string.status_denied, R.color.deny),
    DEFAULT(3, R.string.status_default, R.color.neutral),
    FOREGROUND(4, R.string.status_foreground, R.color.warn),
    UNKNOWN(-1, R.string.status_unknown, R.color.neutral);

    val isExplicit: Boolean get() = this == ALLOWED || this == IGNORED || this == ERRORED

    companion object {
        fun fromMode(mode: Int): OpStatus = entries.firstOrNull { it.mode == mode } ?: UNKNOWN

        /** Opsi yang bisa dipilih user saat mengubah mode. */
        val choices: List<OpStatus> = listOf(ALLOWED, IGNORED, ERRORED, DEFAULT, FOREGROUND)

        fun forShellName(name: String): OpStatus = when (name.trim().lowercase()) {
            "allow", "allowed" -> ALLOWED
            "ignore", "ignored" -> IGNORED
            "deny", "denied", "errored" -> ERRORED
            "default" -> DEFAULT
            "foreground" -> FOREGROUND
            else -> UNKNOWN
        }

        fun shellName(status: OpStatus): String = when (status) {
            ALLOWED -> "allow"
            IGNORED -> "ignore"
            ERRORED -> "deny"
            DEFAULT -> "default"
            FOREGROUND -> "foreground"
            UNKNOWN -> "default"
        }
    }
}

/** Filter cepat untuk daftar app. */
enum class StatusFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    ALLOWED(R.string.filter_allowed),
    BLOCKED(R.string.filter_denied),
    DEFAULT(R.string.filter_default),
    SYSTEM(R.string.filter_system);

    fun accepts(entry: AppEntry): Boolean = when (this) {
        ALL -> true
        ALLOWED -> entry.overlayStatus == OpStatus.ALLOWED
        BLOCKED -> entry.overlayStatus == OpStatus.ERRORED || entry.overlayStatus == OpStatus.IGNORED
        DEFAULT -> entry.overlayStatus == OpStatus.DEFAULT || entry.overlayStatus == OpStatus.UNKNOWN
        SYSTEM -> entry.isSystem
    }
}

/** Dari mana perintah dijalankan. */
enum class AccessMode(val label: String) {
    NONE("—"),
    SHIZUKU_SHELL("Shizuku · shell"),
    SHIZUKU_ROOT("Shizuku · root"),
}

/** Backend teknis yang dipakai untuk baca/tulis AppOps. */
enum class Backend { NONE, BINDER, SHELL }
