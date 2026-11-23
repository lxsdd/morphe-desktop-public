package app.morphe.gui.ui.screens.home

import app.morphe.gui.util.UpdateMetadataResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifiedUpdateEvidenceTest {
    private fun info(
        sources: List<RecallUpdateInfo.SourceUpdate>,
        outdated: Boolean = false,
        target: String? = "1.0",
        resolution: UpdateMetadataResolution = UpdateMetadataResolution.READY,
    ) = RecallUpdateInfo(
        sources = sources,
        appUsedVersion = "1.0",
        appChannel = RecallUpdateInfo.AppChannel.STABLE,
        appSuggestedVersion = target,
        appOutdated = outdated,
        metadataResolution = resolution,
    )

    private fun source(checked: Boolean, changed: Boolean) = RecallUpdateInfo.SourceUpdate(
        sourceId = "trusted-id",
        name = "Source",
        usedVersion = "1.0",
        resolvedVersion = "1.1",
        latestAvailableVersion = "1.1",
        outdated = true,
        hasRelevantChanges = changed,
        checkIncomplete = !checked,
        metadataKnown = checked,
    )

    @Test fun `one positive scoped proof remains positive despite another offline source`() {
        val assessment = verifiedAppUpdates(info(listOf(
            source(true, true), source(false, false).copy(sourceId = "offline"),
        )))
        assertEquals(VerifiedUpdateFact.AVAILABLE, assessment.patch)
        assertTrue(assessment.checkIncomplete) // Other unverified sources remain visible.
    }

    @Test fun `all checked sources without app changes yield a confirmed negative`() {
        val assessment = verifiedAppUpdates(info(listOf(source(true, false))))
        assertEquals(VerifiedUpdateFact.CURRENT, assessment.patch)
        assertEquals(VerifiedUpdateFact.CURRENT, assessment.app)
        assertFalse(assessment.checkIncomplete)
    }

    @Test fun `one unverified source blocks a false all-current verdict`() {
        val assessment = verifiedAppUpdates(info(listOf(
            source(true, false), source(false, false).copy(sourceId = "unchecked"),
        )))
        assertEquals(VerifiedUpdateFact.UNCHECKED, assessment.patch)
        assertTrue(assessment.checkIncomplete)
    }

    @Test fun `newer source tag without scoped check is not a patch update`() {
        val stale = source(false, true)
        assertEquals(VerifiedUpdateFact.UNCHECKED, verifiedAppUpdates(info(listOf(stale))).patch)
        assertEquals(VerifiedUpdateFact.AVAILABLE, verifiedAppUpdates(info(listOf(
            stale.copy(checkIncomplete = false, metadataKnown = true),
        ))).patch)
    }

    @Test fun `no app target is not unverified when supported catalog was checked`() {
        val assessment = verifiedAppUpdates(info(listOf(source(true, false)), target = null))
        assertEquals(VerifiedUpdateFact.CURRENT, assessment.app)
        assertFalse(assessment.checkIncomplete)
    }

    @Test fun `missing metadata or empty source list remain explicit unknown`() {
        assertEquals(VerifiedUpdateFact.UNCHECKED, verifiedAppUpdates(null).patch)
        assertEquals(VerifiedUpdateFact.UNCHECKED, verifiedAppUpdates(info(emptyList())).patch)
        val unavailable = verifiedAppUpdates(info(listOf(source(true, true)),
            outdated = true, target = "1.1", resolution = UpdateMetadataResolution.UNAVAILABLE))
        assertEquals(VerifiedUpdateFact.UNCHECKED, unavailable.app)
        assertEquals(VerifiedUpdateFact.UNCHECKED, unavailable.patch)
    }

    @Test fun `two independently verified updates can coexist`() {
        val assessment = verifiedAppUpdates(info(listOf(source(true, true)),
            outdated = true, target = "1.1"))
        assertTrue(assessment.hasConfirmedAppUpdate)
        assertTrue(assessment.hasConfirmedPatchUpdate)
    }
}
