package app.morphe.gui.ui.screens.home

import app.morphe.gui.util.ChangelogEntry
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.CompatiblePackage
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
    @Test fun `stable source id survives source display rename`() {
        val old = PatchedAppRecord.PatchedSourceSnapshot(
            sourceId = "immutable-id", sourceName = "Previous label", version = "2.2.0")
        assertTrue(matchHistoricalSourceId(old, listOf("immutable-id" to "New display label")) == "immutable-id")
    }

    @Test fun `a name collision cannot borrow another source identity`() {
        val old = PatchedAppRecord.PatchedSourceSnapshot(
            sourceId = "source-that-is-gone", sourceName = "Shared name", version = "2.2.0")
        assertTrue(matchHistoricalSourceId(old, listOf("other-id" to "Shared name")) == null)
        val legacy = old.copy(sourceId = "Shared name")
        assertTrue(matchHistoricalSourceId(legacy,
            listOf("one" to "Shared name", "two" to "Shared name")) == null)
    }

    @Test fun `only app scoped changes within selected release count`() {
        val changelog = listOf(
            ChangelogEntry("2.3.0-dev.5", null, "", mapOf("YouTube" to listOf("Some fix"))),
            ChangelogEntry("2.2.0", null, "", mapOf("YouTube" to listOf("Earlier fix"))),
        )
        assertTrue(assessScopedSourceChange("2.1.0", "2.2.0", changelog,
            listOf("YouTube")) == ScopedSourceChange.CHANGED)
        assertTrue(assessScopedSourceChange("2.2.0", "2.2.0", changelog,
            listOf("YouTube")) == ScopedSourceChange.NO_CHANGE)
        assertTrue(assessScopedSourceChange("2.2.0", "2.3.0-dev.5", changelog,
            listOf("Reddit")) == ScopedSourceChange.NO_CHANGE)
    }

    @Test fun `missing metadata and offline sources remain unknown`() {
        assertTrue(assessScopedSourceChange("2.1.0", null, null,
            listOf("YouTube")) == ScopedSourceChange.UNKNOWN)
        assertTrue(assessScopedSourceChange("2.1.0", "2.2.0", null,
            listOf("YouTube")) == ScopedSourceChange.UNKNOWN)
        assertTrue(assessScopedSourceChange("2.1.0", "2.2.0", emptyList(),
            listOf("YouTube"), unavailable = true) == ScopedSourceChange.UNKNOWN)
        assertTrue(assessScopedSourceChange("2.1.0", "2.2.0", emptyList(),
            emptyList()) == ScopedSourceChange.UNKNOWN)
    }
    @Test fun `update eligibility requires actual source patch compatibility`() {
        val src = listOf(PatchMetadata(
            name = "Example patch", compatiblePackages = listOf(
                CompatiblePackage(name = "org.example.app", versions = listOf("1.0")),
            ),
        ))
        assertTrue(sourceContainsCompatiblePackage(src, "org.example.app"))
        assertFalse(sourceContainsCompatiblePackage(src, "com.google.android.youtube"))
        assertFalse(sourceContainsCompatiblePackage(null, "org.example.app"))
    }
}
