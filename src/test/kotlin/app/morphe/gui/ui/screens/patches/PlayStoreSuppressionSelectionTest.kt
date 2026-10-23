package app.morphe.gui.ui.screens.patches

import app.morphe.engine.model.PatchMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayStoreSuppressionSelectionTest {
    private val optionalPlayPatch = PatchMetadata(
        name = "Disable Play Store updates",
        description = "Sets manifest versionCode to maximum",
        isEnabled = false,
    )
    private fun bundle(id: String, source: String, patches: List<PatchMetadata>) =
        BundlePatches(bundleId = id, bundleName = source, patches = patches)

    @Test
    fun `optional official universal patch can be selected for Amazon`() {
        val bundles = listOf(
            bundle("amazon", "rushiranpise/morphe-patches", listOf(
                PatchMetadata(name = "Remove ads"),
            )),
            bundle("official", "Morphe Patches", listOf(optionalPlayPatch)),
        )
        assertEquals(
            PlayStoreSuppressionPatch("official", optionalPlayPatch.uniqueId),
            findPlayStoreSuppressionPatch(bundles),
        )
    }

    @Test
    fun `do not surface a source impersonating the universal patch name`() {
        val other = bundle("untrusted", "Another Patch Source", listOf(optionalPlayPatch))
        assertNull(findPlayStoreSuppressionPatch(listOf(other)))
    }

    @Test
    fun `package-specific patch with same name is not universal offer`() {
        val notUniversal = PatchMetadata(
            name = "Disable Play Store updates",
            compatiblePackages = listOf(app.morphe.engine.model.CompatiblePackage("another.package")),
        )
        assertNull(findPlayStoreSuppressionPatch(
            listOf(bundle("official", "Morphe Patches", listOf(notUniversal)))))
    }

    @Test
    fun `absent optional patch never becomes silently selected`() {
        assertNull(findPlayStoreSuppressionPatch(
            listOf(bundle("official", "Morphe Patches", listOf(PatchMetadata("Another patch"))))))
    }
}
