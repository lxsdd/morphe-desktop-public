package app.morphe.gui.ui.components

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceManagementOverviewTest {
    private val builtIn = PatchSource(
        id = "official", name = "Morphe Official", type = PatchSourceType.DEFAULT,
        url = "https://github.com/MorpheApp/morphe-patches", deletable = false,
    )
    private val community = PatchSource(
        id = "community", name = "Community Patches", type = PatchSourceType.GITHUB,
        url = "https://github.com/example/patches",
    )
    private val local = PatchSource(
        id = "local", name = "Desktop local", type = PatchSourceType.LOCAL,
        filePath = "/private/work/folder/custom-source.mpp", enabled = false,
    )
    private val sources = listOf(local, community, builtIn)

    @Test fun `overview ignores unknown and disabled stale diagnostics`() {
        val overview = sourceOverview(
            sources,
            errorIds = setOf("local", "unknown", "community"),
            signerChangedIds = setOf("community", "local", "unknown"),
            updateIds = setOf("community", "local", "unknown", "official"),
        )
        assertEquals(3, overview.total)
        assertEquals(2, overview.active)
        assertEquals(1, overview.needsAttention)
        assertEquals(1, overview.signerChanges)
        assertEquals(0, overview.failedSources) // signer issue has precedence
        assertEquals(1, overview.availableSourceVersions) // only official
    }

    @Test fun `attention uses only enabled known sources, retaining their order`() {
        assertEquals(listOf("community"), visibleManagedSources(
            sources, SourceViewFilter.ATTENTION, "",
            errorIds = setOf("community", "local", "unknown"), signerChangedIds = emptySet(),
        ).map(PatchSource::id))
        assertEquals(listOf("community", "official"), visibleManagedSources(
            sources, SourceViewFilter.ENABLED, "", emptySet(), emptySet(),
        ).map(PatchSource::id))
        assertEquals(sources, visibleManagedSources(sources, SourceViewFilter.ALL, "", emptySet(), emptySet()))
    }

    @Test fun `search matches only displayed name provider URL and local basename`() {
        assertEquals(listOf("community"), visibleManagedSources(
            sources, SourceViewFilter.ALL, "github EXAMPLE", emptySet(), emptySet(),
        ).map(PatchSource::id))
        assertEquals(listOf("local"), visibleManagedSources(
            sources, SourceViewFilter.ALL, "custom-source.mpp", emptySet(), emptySet(),
        ).map(PatchSource::id))
        assertTrue(visibleManagedSources(sources, SourceViewFilter.ALL, "/private/work/folder", emptySet(), emptySet()).isEmpty())
        assertTrue(visibleManagedSources(sources, SourceViewFilter.ALL, "unknown-secret-token", emptySet(), emptySet()).isEmpty())
    }

    @Test fun `filtering and search never permit a partial persistent reorder`() {
        assertTrue(sourceOrderEditable(true, 3, SourceViewFilter.ALL, "  "))
        assertFalse(sourceOrderEditable(true, 3, SourceViewFilter.ENABLED, ""))
        assertFalse(sourceOrderEditable(true, 3, SourceViewFilter.ATTENTION, ""))
        assertFalse(sourceOrderEditable(true, 3, SourceViewFilter.ALL, "official"))
        assertFalse(sourceOrderEditable(false, 3, SourceViewFilter.ALL, ""))
        assertFalse(sourceOrderEditable(true, 1, SourceViewFilter.ALL, ""))
    }
}
