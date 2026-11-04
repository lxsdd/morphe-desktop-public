package app.morphe.gui.ui.screens.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepatchInputValidationTest {
    @Test
    fun newlySelectedApkVersionSupersedesOldHistory() {
        val actual = verifiedRepatchVersion(
            expectedPackage = "com.example.social",
            inspectedPackage = "com.example.social",
            inspectedVersion = "447.0.0.55.81",
        )
        assertEquals("447.0.0.55.81", actual)
    }

    @Test
    fun wrongPackageIsRejectedInsteadOfPatchingAnotherApp() {
        assertFailsWith<IllegalArgumentException> {
            verifiedRepatchVersion("com.example.social", "com.example.other", "447.0")
        }
    }

    @Test
    fun unverifiedOrEmptyVersionIsRejectedRatherThanReusingHistory() {
        for (version in listOf(null, "", " ", "unknown", "UNKNOWN")) {
            assertFailsWith<IllegalArgumentException> {
                verifiedRepatchVersion("com.example.social", "com.example.social", version)
            }
        }
    }
}
