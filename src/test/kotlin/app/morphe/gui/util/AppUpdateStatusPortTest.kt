package app.morphe.gui.util

import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class AppUpdateStatusPortTest {
    @Test
    fun `history and live device version use different evidence for patch status`() {
        val info = RecallUpdateInfo(
            sources = listOf(RecallUpdateInfo.SourceUpdate(
                name = "synthetic-patches",
                usedVersion = "1.0",
                resolvedVersion = "1.0",
                latestAvailableVersion = "1.1",
                outdated = true,
            )),
            appUsedVersion = "1.0",
            appChannel = RecallUpdateInfo.AppChannel.STABLE,
            appSuggestedVersion = "1.1",
            appOutdated = true,
        )
        assertEquals(AppUpdateStatus.APP_AND_PATCH_UPDATE, info.classification(null).status)
        assertEquals(AppUpdateStatus.NEWER_APP_VERSION, info.classification("1.0").status)
        assertEquals(AppUpdateStatus.APP_AND_PATCH_UPDATE, info.classification("1.0", true).status)
        assertEquals(AppUpdateStatus.UNKNOWN,
            info.copy(metadataResolution = UpdateMetadataResolution.CHECKING).classification(null).status)
    }

    @Test
    fun `independent dimensions classify updates without inventing certainty`() {
        assertEquals(AppUpdateStatus.PATCH_UPDATE,
            classifyAppUpdate(true, null, "1.0", "1.0").status)
        assertEquals(AppUpdateStatus.NEWER_APP_VERSION,
            classifyAppUpdate(false, null, "1.0", "1.1").status)
        assertEquals(AppUpdateStatus.APP_AND_PATCH_UPDATE,
            classifyAppUpdate(true, null, "1.0", "1.1").status)
        assertEquals(AppUpdateStatus.UP_TO_DATE,
            classifyAppUpdate(false, null, "1.1", "1.1").status)
        assertEquals(AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(null, null, "1.0", "1.0").status)
        assertEquals(AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(true, null, "1.0", null).status)
        assertEquals(AppUpdateStatus.NEWER_APP_VERSION,
            classifyAppUpdate(null, null, "1.0", "1.1").status)
    }

    @Test
    fun `stale or missing metadata cannot become an action`() {
        assertEquals(AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(true, null, "1.0", "1.1", UpdateMetadataResolution.CHECKING).status)
        assertEquals(AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(false, null, "1.0", "1.0", UpdateMetadataResolution.UNAVAILABLE).status)
        assertEquals(AppUpdateAction.NONE, recommendedAppUpdateAction(AppUpdateStatus.UNKNOWN))
    }

    @Test
    fun `stable users never automatically switch to experimental channel`() {
        val target = selectSupportedAppTarget(null, listOf("2.0-experimental"), "1.0")
        assertEquals(SupportedTargetChannel.STABLE, target.channel)
        assertEquals(null, target.version)
        assertEquals(SupportedTargetChannel.EXPERIMENTAL,
            selectSupportedAppTarget("1.0", listOf("2.0"), "2.0").channel)
    }

    @Test
    fun `live device version overrides historic record`() {
        val classified = classifyAppUpdate(false, "2.0", "1.0", "2.0")
        assertEquals(AppUpdateStatus.UP_TO_DATE, classified.status)
        assertEquals(CurrentVersionSource.SELECTED_DEVICE, classified.currentVersionSource)
        assertEquals("2.0", classified.currentVersion)
        assertEquals(CurrentVersionSource.PATCHED_RECORD,
            classifyAppUpdate(false, null, "1.0", "1.1").currentVersionSource)
    }

    @Test
    fun `old refresh results cannot supersede newer metadata`() {
        val clock = UpdateMetadataGenerationGuard()
        val first = clock.next()
        val second = clock.next()
        assertEquals(false, clock.isCurrent(first))
        assertEquals(true, clock.isCurrent(second))
    }
}
