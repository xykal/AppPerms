package app.appsperms.core

import android.os.Build

enum class SettingsNamespace(val shellName: String, val perUser: Boolean) {
    GLOBAL("global", false),
    SECURE("secure", true),
    SYSTEM("system", true),
}

data class FeatureSetting(
    val namespace: SettingsNamespace,
    val key: String,
    val enabledValue: String,
)

data class FeatureDefinition(
    val id: String,
    val minSdk: Int,
    val restartRequired: Boolean,
    val settings: List<FeatureSetting>,
)

/**
 * Bounded allowlist of legitimate Android system flags. This intentionally contains no
 * third-party license, subscription or premium-feature bypasses.
 */
object FeatureCatalog {
    val ALL = listOf(
        FeatureDefinition(
            id = "freeform_windows",
            minSdk = Build.VERSION_CODES.N,
            restartRequired = true,
            settings = listOf(
                FeatureSetting(SettingsNamespace.GLOBAL, "enable_freeform_support", "1"),
                FeatureSetting(SettingsNamespace.GLOBAL, "force_resizable_activities", "1"),
            ),
        ),
        FeatureDefinition(
            id = "desktop_external_display",
            minSdk = Build.VERSION_CODES.Q,
            restartRequired = true,
            // Kept independent from freeform_windows so each switch can restore exactly its own key.
            // The UI explains that Freeform Windows should also be enabled for the full experience.
            settings = listOf(
                FeatureSetting(SettingsNamespace.GLOBAL, "force_desktop_mode_on_external_displays", "1"),
            ),
        ),
        FeatureDefinition(
            id = "notification_history",
            minSdk = Build.VERSION_CODES.R,
            restartRequired = false,
            settings = listOf(
                FeatureSetting(SettingsNamespace.SECURE, "notification_history_enabled", "1"),
            ),
        ),
        FeatureDefinition(
            id = "notification_bubbles",
            minSdk = Build.VERSION_CODES.R,
            restartRequired = false,
            settings = listOf(
                FeatureSetting(SettingsNamespace.SECURE, "notification_bubbles", "1"),
            ),
        ),
    )

    fun byId(id: String): FeatureDefinition? = ALL.firstOrNull { it.id == id }
}
