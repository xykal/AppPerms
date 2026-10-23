package app.appsperms.core

/** Current package state for one Android user/profile. */
enum class DebloatState {
    ENABLED,
    DISABLED,
    UNINSTALLED_USER,
    UNKNOWN,
}

/**
 * Risk tiers shown by Debloat. PROTECTED can never be changed by AppsPerms.
 * SAFE still means "optional for most users", not "zero impact for everybody".
 */
enum class DebloatTier {
    SAFE,
    ADVANCED,
    EXPERT,
    PROTECTED,
}

enum class DebloatReason {
    KNOWN_OPTIONAL,
    UNKNOWN_SYSTEM_APP,
    SENSITIVE_SYSTEM_APP,
    CORE_SYSTEM_APP,
    CURRENT_DEVICE_ROLE,
}

data class DebloatClassification(
    val tier: DebloatTier,
    val reason: DebloatReason,
)

data class DebloatPackage(
    val packageName: String,
    val userId: Int,
    val state: DebloatState,
    val classification: DebloatClassification,
)

data class DebloatSnapshot(
    val createdAtMs: Long,
    val userId: Int,
    val states: Map<String, DebloatState>,
)

/** Pure package policy so the denylist and tiering can be covered by JVM tests. */
object DebloatPolicy {
    private val protectedPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.shell",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.android.providers.telephony",
        "com.android.providers.downloads",
        "com.android.externalstorage",
        "com.android.documentsui",
        "com.android.provision",
        "com.android.managedprovisioning",
        "com.google.android.setupwizard",
        "com.android.networkstack",
        "com.google.android.networkstack",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.keychain",
        "com.android.certinstaller",
        "com.google.android.gms",
        "com.google.android.gsf",
    )

    /** Conservative optional-package catalog. Every action still requires confirmation. */
    private val knownOptionalPackages = setOf(
        "com.android.egg",
        "com.android.dreams.basic",
        "com.android.dreams.phototable",
        "com.android.bips",
        "com.google.android.printservice.recommendation",
        "com.google.android.apps.tips",
        "com.google.android.feedback",
        "com.google.android.onetimeinitializer",
        "com.samsung.android.app.tips",
        "com.samsung.android.game.gamehome",
        "com.samsung.android.aremoji",
        "com.samsung.android.arzone",
        "com.miui.analytics",
        "com.miui.msa.global",
        "com.miui.systemAdSolution",
        "com.miui.bugreport",
        "com.oplus.logkit",
        "com.coloros.logkit",
    )

    private val sensitiveTokens = listOf(
        "launcher", "inputmethod", "keyboard", "telephony", "dialer", "incallui",
        "security", "biometric", "fingerprint", "credential", "backup", "restore",
        "camera", "location", "connectivity", "wifi", "ims", "emergency", "setupwizard",
        "devicepolicy", "managedprovisioning", "webview", "vending", "modulemetadata",
    )

    fun classify(packageName: String, dynamicProtected: Set<String> = emptySet()): DebloatClassification {
        if (packageName in dynamicProtected) {
            return DebloatClassification(DebloatTier.PROTECTED, DebloatReason.CURRENT_DEVICE_ROLE)
        }
        if (packageName in protectedPackages || packageName.startsWith("com.android.apex.")) {
            return DebloatClassification(DebloatTier.PROTECTED, DebloatReason.CORE_SYSTEM_APP)
        }
        if (packageName in knownOptionalPackages) {
            return DebloatClassification(DebloatTier.SAFE, DebloatReason.KNOWN_OPTIONAL)
        }
        if (sensitiveTokens.any(packageName::contains)) {
            return DebloatClassification(DebloatTier.EXPERT, DebloatReason.SENSITIVE_SYSTEM_APP)
        }
        return DebloatClassification(DebloatTier.ADVANCED, DebloatReason.UNKNOWN_SYSTEM_APP)
    }

    fun canMutate(packageName: String, dynamicProtected: Set<String> = emptySet()): Boolean =
        ShellSafety.isValidPackageName(packageName) &&
            classify(packageName, dynamicProtected).tier != DebloatTier.PROTECTED
}

/** Text format for a local, offline debloat snapshot. */
object DebloatSnapshotCodec {
    private const val HEADER = "# AppsPerms Debloat Snapshot v1"

    fun encode(snapshot: DebloatSnapshot): String = buildString {
        append(HEADER).append('\n')
        append("created=").append(snapshot.createdAtMs).append('\n')
        append("user=").append(snapshot.userId).append('\n')
        snapshot.states.toSortedMap().forEach { (pkg, state) ->
            if (ShellSafety.isValidPackageName(pkg) && state != DebloatState.UNKNOWN) {
                append(pkg).append('|').append(state.name).append('\n')
            }
        }
    }

    fun decode(text: String): DebloatSnapshot? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.firstOrNull() != HEADER) return null
        val created = lines.firstOrNull { it.startsWith("created=") }
            ?.substringAfter('=')?.toLongOrNull() ?: return null
        val userId = lines.firstOrNull { it.startsWith("user=") }
            ?.substringAfter('=')?.toIntOrNull()?.takeIf(ShellSafety::isValidUserId) ?: return null
        val states = lines.asSequence()
            .filter { !it.startsWith('#') && !it.startsWith("created=") && !it.startsWith("user=") }
            .mapNotNull { line ->
                val pkg = line.substringBefore('|')
                val state = runCatching { DebloatState.valueOf(line.substringAfter('|')) }.getOrNull()
                if (!ShellSafety.isValidPackageName(pkg) || state == null || state == DebloatState.UNKNOWN) null
                else pkg to state
            }
            .toMap()
        return DebloatSnapshot(created, userId, states)
    }
}

/** Parser for the stable columns emitted by `df -k`. */
data class StorageSpace(val totalBytes: Long, val freeBytes: Long)

object StorageSpaceParser {
    fun parseDfKilobytes(output: String): StorageSpace? {
        val row = output.lineSequence().map(String::trim)
            .firstOrNull { it.isNotEmpty() && !it.startsWith("Filesystem") } ?: return null
        val columns = row.split(Regex("\\s+"))
        if (columns.size < 4) return null
        val totalKb = columns.getOrNull(1)?.toLongOrNull() ?: return null
        val freeKb = columns.getOrNull(3)?.toLongOrNull() ?: return null
        if (totalKb < 0 || freeKb < 0) return null
        return StorageSpace(totalKb * 1024L, freeKb * 1024L)
    }
}
