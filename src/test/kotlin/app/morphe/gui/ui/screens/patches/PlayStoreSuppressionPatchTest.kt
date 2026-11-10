package app.morphe.gui.ui.screens.patches

import app.morphe.engine.model.PatchMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayStoreSuppressionPatchTest {
    private val patch = PatchMetadata(
        name = "Disable Play Store updates",
        description = "Changes manifest versionCode",
        compatiblePackages = emptyList(),
        isEnabled = false,
    )

    @Test fun `recognize official universal patch regardless of default opt in`() {
        val bundles = listOf(BundlePatches("official-id", "Morphe Patches", listOf(patch)))
        assertEquals(patch.uniqueId, findPlayStoreSuppressionPatch(bundles)?.patchId)
    }

    @Test fun `do not misattribute third party same named patch`() {
        val bundles = listOf(BundlePatches("third", "Unrelated Source", listOf(patch)))
        assertNull(findPlayStoreSuppressionPatch(bundles))
    }

    @Test fun `do not offer package specific patch as universal workaround`() {
        val packagePatch = patch.copy(compatiblePackages =
            listOf(app.morphe.engine.model.CompatiblePackage("only.example")))
        assertNull(findPlayStoreSuppressionPatch(listOf(
            BundlePatches("official", "Morphe Patches", listOf(packagePatch)))))
    }
}
