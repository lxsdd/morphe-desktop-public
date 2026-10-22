package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.util.UpdateMetadataResolution
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeUpdateCategoriesTest {
    private fun info(
        appOutdated: Boolean = false,
        suggested: String? = "2.0",
        sources: List<RecallUpdateInfo.SourceUpdate> = emptyList(),
        resolution: UpdateMetadataResolution = UpdateMetadataResolution.READY,
    ) = RecallUpdateInfo(
        sources = sources,
        appUsedVersion = "1.0",
        appChannel = RecallUpdateInfo.AppChannel.STABLE,
        appSuggestedVersion = suggested,
        appOutdated = appOutdated,
        metadataResolution = resolution,
    )

    @Test
    fun `an app update is not a patch update`() {
        val x = info(appOutdated = true)
        assertTrue(hasConfirmedAppUpdate(x))
        assertFalse(hasConfirmedPatchUpdate(x))
    }

    @Test
    fun `new patch source alone does not claim app patch has changed`() {
        val x = info(sources = listOf(
            RecallUpdateInfo.SourceUpdate("source", "1.0", "1.1", "1.1",
                outdated = true, hasRelevantChanges = false)
        ))
        assertFalse(hasConfirmedPatchUpdate(x))
        assertFalse(hasConfirmedAppUpdate(x))
    }

    @Test
    fun `a scoped source change is a confirmed patch update`() {
        val x = info(sources = listOf(
            RecallUpdateInfo.SourceUpdate("source", "1.0", "1.1", "1.1",
                outdated = true, hasRelevantChanges = true)
        ))
        assertTrue(hasConfirmedPatchUpdate(x))
        assertFalse(hasConfirmedAppUpdate(x))
    }

    @Test
    fun `both update types can apply simultaneously`() {
        val x = info(appOutdated = true, sources = listOf(
            RecallUpdateInfo.SourceUpdate("source", "1.0", "1.1", "1.1",
                outdated = true, hasRelevantChanges = true)
        ))
        assertTrue(hasConfirmedPatchUpdate(x))
        assertTrue(hasConfirmedAppUpdate(x))
    }

    @Test
    fun `unavailable metadata and missing target never imply confirmed app update`() {
        assertFalse(hasConfirmedAppUpdate(info(appOutdated = true,
            resolution = UpdateMetadataResolution.UNAVAILABLE)))
        assertFalse(hasConfirmedAppUpdate(info(appOutdated = true, suggested = null)))
        assertFalse(hasConfirmedAppUpdate(null))
        assertFalse(hasConfirmedPatchUpdate(null))
    }
}
