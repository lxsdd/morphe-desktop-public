package app.morphe.gui.ui.screens.home

import app.morphe.gui.util.ChangelogEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopedPatchChangelogProofTest {
    private val redditChange = listOf(
        ChangelogEntry(
            version = "1.1",
            date = null,
            content = "",
            scopedBullets = mapOf("Reddit" to listOf("Fix a Reddit-only patch")),
        )
    )

    @Test
    fun `missing changelog is unknown not a confirmed update`() {
        assertFalse(hasConfirmedScopedPatchChange("1.0", "1.1", null, setOf("Reddit")))
        assertFalse(hasConfirmedScopedPatchChange("1.0", "1.1", emptyList(), setOf("Reddit")))
        assertFalse(hasConfirmedScopedPatchChange("1.0", null, redditChange, setOf("Reddit")))
    }

    @Test
    fun `unrelated patch release does not update this app`() {
        assertFalse(hasConfirmedScopedPatchChange("1.0", "1.1", redditChange, setOf("Instagram")))
        assertFalse(hasConfirmedScopedPatchChange("1.1", "1.1", redditChange, setOf("Reddit")))
    }

    @Test
    fun `only newer app-scoped change is confirmed`() {
        assertTrue(hasConfirmedScopedPatchChange("1.0", "1.1", redditChange, setOf("Reddit")))
    }
}
