package app.appsperms.core

import android.content.Context
import java.io.File

/** Local-only persistence for reversible Device Care actions. */
class DeviceCareStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("device_care_restore_v1", Context.MODE_PRIVATE)

    private fun snapshotFile(userId: Int): File =
        File(appContext.filesDir, "debloat_snapshot_v1_user$userId.txt")

    @Synchronized
    fun saveSnapshot(snapshot: DebloatSnapshot) {
        require(ShellSafety.isValidUserId(snapshot.userId))
        val file = snapshotFile(snapshot.userId)
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(DebloatSnapshotCodec.encode(snapshot))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    @Synchronized
    fun readSnapshot(userId: Int): DebloatSnapshot? = runCatching {
        if (!ShellSafety.isValidUserId(userId)) return@runCatching null
        val file = snapshotFile(userId)
        if (!file.exists()) null else DebloatSnapshotCodec.decode(file.readText())
    }.getOrNull()

    @Synchronized
    fun clearSnapshot(userId: Int) {
        if (ShellSafety.isValidUserId(userId)) snapshotFile(userId).delete()
    }

    private fun featureKey(featureId: String, userId: Int, namespace: String, key: String): String =
        "feature.$featureId.user$userId.$namespace.$key"

    /** Capture once; repeated enable calls must not overwrite the true pre-feature value. */
    fun rememberFeatureValue(
        featureId: String,
        userId: Int,
        namespace: String,
        key: String,
        value: String?,
    ) {
        val prefKey = featureKey(featureId, userId, namespace, key)
        if (!prefs.contains(prefKey)) {
            prefs.edit().putString(prefKey, value ?: NULL_SENTINEL).apply()
        }
    }

    fun featureOriginalValue(
        featureId: String,
        userId: Int,
        namespace: String,
        key: String,
    ): Pair<Boolean, String?> {
        val prefKey = featureKey(featureId, userId, namespace, key)
        if (!prefs.contains(prefKey)) return false to null
        val stored = prefs.getString(prefKey, NULL_SENTINEL)
        return true to stored?.takeUnless { it == NULL_SENTINEL }
    }

    fun forgetFeature(featureId: String, selectedUserId: Int, settings: List<FeatureSetting>) {
        val edit = prefs.edit()
        settings.forEach { setting ->
            val storageUserId = if (setting.namespace.perUser) selectedUserId else 0
            edit.remove(featureKey(featureId, storageUserId, setting.namespace.shellName, setting.key))
        }
        edit.apply()
    }

    companion object {
        private const val NULL_SENTINEL = "<APPSPERMS_NULL>"
    }
}
