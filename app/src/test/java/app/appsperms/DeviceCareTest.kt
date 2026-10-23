package app.appsperms

import app.appsperms.core.DebloatPolicy
import app.appsperms.core.DebloatSnapshot
import app.appsperms.core.DebloatSnapshotCodec
import app.appsperms.core.DebloatState
import app.appsperms.core.DebloatTier
import app.appsperms.core.ShellSafety
import app.appsperms.core.StorageSpaceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCareTest {
    @Test
    fun packageValidationRejectsShellMetacharacters() {
        assertTrue(ShellSafety.isValidPackageName("com.example.app"))
        assertTrue(ShellSafety.isValidPackageName("android"))
        assertFalse(ShellSafety.isValidPackageName("com.example.app;reboot"))
        assertFalse(ShellSafety.isValidPackageName("com.example." + "\$" + "(id)"))
        assertFalse(ShellSafety.isValidPackageName("com.example/app"))
        assertFalse(ShellSafety.isValidPackageName(""))
    }

    @Test
    fun packageListParserDropsInvalidRows() {
        val parsed = ShellSafety.parsePackageList(
            """
            package:com.example.good
            package:android
            package:com.bad;reboot
            random output
            """.trimIndent(),
        )
        assertEquals(setOf("com.example.good", "android"), parsed)
    }

    @Test
    fun criticalAndDynamicPackagesAreProtected() {
        assertEquals(DebloatTier.PROTECTED, DebloatPolicy.classify("com.android.systemui").tier)
        assertEquals(
            DebloatTier.PROTECTED,
            DebloatPolicy.classify("com.vendor.launcher", setOf("com.vendor.launcher")).tier,
        )
        assertFalse(DebloatPolicy.canMutate("com.android.settings"))
        assertTrue(DebloatPolicy.canMutate("com.vendor.optional"))
    }

    @Test
    fun snapshotRoundTripAndCorruptionHandling() {
        val original = DebloatSnapshot(
            createdAtMs = 1234L,
            userId = 10,
            states = mapOf(
                "com.example.one" to DebloatState.ENABLED,
                "com.example.two" to DebloatState.UNINSTALLED_USER,
            ),
        )
        val decoded = DebloatSnapshotCodec.decode(DebloatSnapshotCodec.encode(original))
        assertEquals(original, decoded)
        assertNull(DebloatSnapshotCodec.decode("not a snapshot"))
        assertNotNull(
            DebloatSnapshotCodec.decode(
                "# AppsPerms Debloat Snapshot v1\ncreated=1\nuser=0\ninvalid;pkg|ENABLED\n",
            ),
        )
    }

    @Test
    fun parsesAndroidDfOutput() {
        val parsed = StorageSpaceParser.parseDfKilobytes(
            """
            Filesystem     1K-blocks    Used Available Use% Mounted on
            /dev/block/dm-8  104857600 52428800  52428800  50% /data
            """.trimIndent(),
        )
        assertNotNull(parsed)
        assertEquals(104857600L * 1024L, parsed!!.totalBytes)
        assertEquals(52428800L * 1024L, parsed.freeBytes)
    }
}
