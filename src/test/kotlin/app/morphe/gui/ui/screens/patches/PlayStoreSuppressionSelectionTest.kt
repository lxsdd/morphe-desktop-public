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
    fun `selected legacy Change version code is warned even from another source`() {
        val legacy = PatchMetadata(
            name = "Change version code",
            description = "Turns off app store updates",
            compatiblePackages = listOf(app.morphe.engine.model.CompatiblePackage("com.instagram.android")),
            isEnabled = true,
        )
        val bundles = listOf(bundle("legacy", "Older Patch Bundle", listOf(legacy)))
        assertEquals(
            listOf(legacy.uniqueId),
            selectedVersionCodeUpdateSuppressionPatches(
                bundles, mapOf("legacy" to setOf(legacy.uniqueId))
            ).map { it.uniqueId },
        )
    }

    @Test
    fun `unselected version code patch does not block ordinary patching`() {
        val bundles = listOf(bundle("official", "Morphe Patches", listOf(optionalPlayPatch)))
        assertEquals(
            emptyList(),
            selectedVersionCodeUpdateSuppressionPatches(bundles, emptyMap()),
        )
    }

    @Test
    fun `similar patch names do not trigger version code warning`() {
        val unrelated = PatchMetadata(name = "Display app version code")
        val bundles = listOf(bundle("other", "Other Patches", listOf(unrelated)))
        assertEquals(
            emptyList(),
            selectedVersionCodeUpdateSuppressionPatches(
                bundles, mapOf("other" to setOf(unrelated.uniqueId))
            ),
        )
    }

    @Test
    fun `absent optional patch never becomes silently selected`() {
        assertNull(findPlayStoreSuppressionPatch(
            listOf(bundle("official", "Morphe Patches", listOf(PatchMetadata("Another patch"))))))
    }
}
