package app.morphe.gui.ui.screens.patches

import app.morphe.engine.model.PatchMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayStoreSuppressionPatchTest {
    @Test
    fun `universal Play patch found only when actually present in loaded bundles`() {
        val patch = PatchMetadata("Disable Play Store updates", isEnabled = false)
        val other = PatchMetadata("Dark mode")
        val selected = findPlayStoreSuppressionPatch(
            listOf(
                BundlePatches("amazon-only", "rushiranpise/morphe-patches", listOf(other)),
                BundlePatches("official", "Morphe Patches", listOf(patch)),
            )
        )
        assertEquals("official", selected?.bundleId)
        assertEquals(patch.uniqueId, selected?.patchId)
    }

    @Test
    fun `Amazon patch source alone does not imply Play suppression`() {
        val bundles = listOf(
            BundlePatches("amazon-only", "rushiranpise/morphe-patches",
                listOf(PatchMetadata("Dark mode"), PatchMetadata("Remove ads")))
        )
        assertNull(findPlayStoreSuppressionPatch(bundles))
    }

    @Test
    fun `package-specific patch with same name is not claimed to be universal`() {
        val patch = PatchMetadata("Disable Play Store updates",
            compatiblePackages = listOf(app.morphe.engine.model.CompatiblePackage("com.other.app")))
        assertNull(findPlayStoreSuppressionPatch(
            listOf(BundlePatches("untrusted", "Sample", listOf(patch)))
        ))
    }
}
