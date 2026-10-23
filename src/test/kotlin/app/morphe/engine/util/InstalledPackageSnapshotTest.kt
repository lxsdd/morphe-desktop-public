package app.morphe.engine.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstalledPackageSnapshotTest {
    private val manager = AdbManager()
    private val amazon = "com.amazon.mShop.android.shopping"

    @Test
    fun `owner is read from exact installed Amazon package section`() {
        val dump = """
            Packages:
              Package [other.example] (abcd):
                versionName=9999
                versionCode=2147483647 minSdk=24 targetSdk=32
                updateOwnerPackageName=com.android.vending
              Package [$amazon] (efgh):
                versionName=32.18.0.100
                versionCode=1243240206 minSdk=26 targetSdk=34
                updateOwnerPackageName=com.android.shell
        """.trimIndent()
        val snapshot = manager.parseInstalledPackageSnapshot(amazon, dump)
        assertEquals("32.18.0.100", snapshot?.version)
        assertEquals(1243240206L, snapshot?.versionCode)
        assertEquals("com.android.shell", snapshot?.updateOwnerPackage)
        assertTrue(snapshot?.updateOwnerVerified == true)
    }

    @Test
    fun `no update owner is recorded as known absence not Play proof`() {
        val dump = """
            Packages:
              Package [$amazon] (efgh):
                versionName=32.18.0.100
        """.trimIndent()
        val snapshot = manager.parseInstalledPackageSnapshot(amazon, dump)
        assertEquals("32.18.0.100", snapshot?.version)
        assertNull(snapshot?.updateOwnerPackage)
        assertTrue(snapshot?.updateOwnerVerified == true)
    }

    @Test
    fun `other package cannot supply the owner or version`() {
        val dump = """
            Packages:
              Package [other.example] (abcd):
                versionName=9
                updateOwnerPackageName=com.android.vending
        """.trimIndent()
        assertNull(manager.parseInstalledPackageSnapshot(amazon, dump))
    }

    @Test
    fun `adjacent package version cannot override selected package version`() {
        val dump = """
            Packages:
              Package [$amazon] (abcd):
                updateOwnerPackageName=com.android.shell
              Package [other.example] (xyz):
                versionName=999
        """.trimIndent()
        val snapshot = manager.parseInstalledPackageSnapshot(amazon, dump)
        assertNull(snapshot?.version)
        assertNull(snapshot?.versionCode)
        assertEquals("com.android.shell", snapshot?.updateOwnerPackage)
        assertFalse(snapshot?.updateOwnerVerified == false)
    }
    @Test
    fun `max manifest versionCode is observable without claiming Play has stopped updating`() {
        val dump = """
            Packages:
              Package [$amazon] (efgh):
                versionName=32.18.0.100
                versionCode=2147483647 minSdk=24 targetSdk=35
                updateOwnerPackageName=com.android.shell
        """.trimIndent()
        val snapshot = manager.parseInstalledPackageSnapshot(amazon, dump)
        assertEquals(2147483647L, snapshot?.versionCode)
        assertTrue(snapshot?.updateOwnerVerified == true)
    }
}
