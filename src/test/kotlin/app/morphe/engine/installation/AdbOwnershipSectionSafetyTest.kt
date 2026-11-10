package app.morphe.engine.installation

import kotlin.test.Test
import kotlin.test.assertEquals

class AdbOwnershipSectionSafetyTest {
    @Test
    fun `foreign owner remains visible even if another package precedes target`() {
        val dump = """
            Packages:
              Package [unrelated.example] (abcd):
                updateOwnerPackageName=com.android.shell
              Package [target.example] (efgh):
                versionName=2
                updateOwnerPackageName=foreign.manager
        """.trimIndent()
        assertEquals(
            InstalledPackageOwnership.OwnedBy("foreign.manager"),
            AdbPackageOwnershipProbe.parseInstalledPackageDump("target.example", dump),
        )
    }

    @Test
    fun `owner from later package cannot be mistaken for target owner`() {
        val dump = """
            Packages:
              Package [target.example] (aaaa):
                versionName=2
              Package [unrelated.example] (bbbb):
                updateOwnerPackageName=com.android.shell
        """.trimIndent()
        assertEquals(
            InstalledPackageOwnership.NoOwner,
            AdbPackageOwnershipProbe.parseInstalledPackageDump("target.example", dump),
        )
    }

    @Test
    fun `missing requested package never takes ownership from another app`() {
        val dump = """
            Packages:
              Package [unrelated.example] (abc):
                updateOwnerPackageName=com.android.shell
        """.trimIndent()
        assertEquals(
            InstalledPackageOwnership.NotInstalled,
            AdbPackageOwnershipProbe.parseInstalledPackageDump("target.example", dump),
        )
    }
}
