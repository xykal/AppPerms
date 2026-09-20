package app.appsperms.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.appsperms.core.GhostGuard.Side

/** Pilihan bahasa tampilan. [tag] kosong = ikuti bahasa sistem. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    INDONESIAN("in"),
    ENGLISH("en"),
}

/**
 * Preferensi ringan berbasis SharedPreferences.
 *
 * OPTIMIZED v1.5: Fitur berbahaya dihapus.
 * - guard_enabled / guard_sides / guard_thickness / guard_test -> DEPRECATED, selalu OFF (tidak dipakai lagi)
 * - protected_app -> DEPRECATED (whitelist deviceidle bikin baterai boros permanen)
 *
 * Key lama tetap dibaca agar migrasi tidak crash, tapi setter/getter di-override jadi no-op / default aman.
 */
object Settings {

    private const val FILE = "appsperms_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_CONFIRM_RISK = "confirm_risk"
    private const val KEY_SHARED_UID = "warn_shared_uid"
    private const val KEY_SORT = "default_sort"
    // Deprecated keys - kept for reading old prefs to clean them
    private const val KEY_GUARD_ENABLED = "guard_enabled"
    private const val KEY_GUARD_SIDES = "guard_sides"
    private const val KEY_GUARD_THICKNESS = "guard_thickness"
    private const val KEY_GUARD_TEST = "guard_test"
    private const val KEY_PROTECTED_APP = "protected_app"
    private const val KEY_TUNING_SNAPSHOT = "tuning_snapshot"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- bahasa

    fun language(context: Context): AppLanguage {
        val stored = prefs(context).getString(KEY_LANGUAGE, null) ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.name).apply()
        applyLanguage(language)
    }

    /** Terapkan bahasa tersimpan — panggil sekali di Application.onCreate(). */
    fun applyStoredLanguage(context: Context) = applyLanguage(language(context))

    private fun applyLanguage(language: AppLanguage) {
        val locales = if (language.tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    // -------------------------------------------------------------- perilaku

    /** Tanya dulu sebelum menerapkan mode berisiko (deny/ignore) ke app yang minta overlay. */
    fun confirmRiskyModes(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONFIRM_RISK, true)

    fun setConfirmRiskyModes(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONFIRM_RISK, value).apply()
    }

    /** Tampilkan peringatan kalau beberapa app berbagi UID yang sama. */
    fun warnSharedUid(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHARED_UID, true)

    fun setWarnSharedUid(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHARED_UID, value).apply()
    }

    // ----------------------------------------------------------------- urutan

    fun defaultSort(context: Context): SortMode {
        val stored = prefs(context).getString(KEY_SORT, null)
        return SortMode.entries.firstOrNull { it.name == stored } ?: SortMode.NAME
    }

    fun setDefaultSort(context: Context, mode: SortMode) {
        prefs(context).edit().putString(KEY_SORT, mode.name).apply()
    }

    // -------------------------------------------------------- tuning aman

    fun protectedApp(context: Context): String? = null // REMOVED: always null, tidak ada proteksi lagi

    fun setProtectedApp(context: Context, packageName: String?) {
        // no-op, hapus key lama jika ada
        prefs(context).edit().remove(KEY_PROTECTED_APP).apply()
    }

    /** Snapshot kecil: size|density|anim. String kosong berarti nilai bawaan sistem. */
    fun tuningSnapshot(context: Context): String? = prefs(context).getString(KEY_TUNING_SNAPSHOT, null)
    fun setTuningSnapshot(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_TUNING_SNAPSHOT, value).apply()
    }

    // -------------------------------------------------------- anti ghost touch
    // DEPRECATED v1.5: Semua guard selalu OFF. Jangan pernah aktifkan foreground overlay lagi.

    /** Master switch perisai. Selalu false demi keamanan (tidak ada overlay yang menelan sentuhan). */
    fun guardEnabled(context: Context): Boolean = false

    fun setGuardEnabled(context: Context, value: Boolean) {
        // paksa false dan hapus key lama
        prefs(context).edit().putBoolean(KEY_GUARD_ENABLED, false).apply()
    }

    /** Sisi mana saja yang diberi pita. Default: bawah (tapi tidak dipakai karena guard mati). */
    fun guardSides(context: Context): Set<Side> = setOf(Side.BOTTOM)

    fun setGuardSides(context: Context, sides: Set<Side>) {
        // no-op, bersihkan preference lama
        prefs(context).edit().remove(KEY_GUARD_SIDES).apply()
    }

    /** Ketebalan pita dalam DP. */
    fun guardThicknessDp(context: Context): Int = 24

    fun setGuardThicknessDp(context: Context, dp: Int) {
        prefs(context).edit().remove(KEY_GUARD_THICKNESS).apply()
    }

    /** Mode uji: pita diberi warna agar terlihat. */
    fun guardTestMode(context: Context): Boolean = false

    fun setGuardTestMode(context: Context, value: Boolean) {
        prefs(context).edit().remove(KEY_GUARD_TEST).apply()
    }

    /** Bersihkan semua key berbahaya saat migrasi ke v1.5 */
    fun cleanDeprecatedKeys(context: Context) {
        prefs(context).edit()
            .remove(KEY_GUARD_ENABLED)
            .remove(KEY_GUARD_SIDES)
            .remove(KEY_GUARD_THICKNESS)
            .remove(KEY_GUARD_TEST)
            .remove(KEY_PROTECTED_APP)
            .apply()
    }
}
