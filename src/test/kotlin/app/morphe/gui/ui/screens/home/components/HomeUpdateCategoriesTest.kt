package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.util.UpdateMetadataResolution
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
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
    fun `unresolved patch metadata cannot confirm a per-app patch update`() {
        val change = RecallUpdateInfo.SourceUpdate("synthetic-source", "1.0", "1.1", "1.1",
            outdated = true, hasRelevantChanges = true)
        assertFalse(hasConfirmedPatchUpdate(info(sources = listOf(change),
            resolution = UpdateMetadataResolution.UNAVAILABLE)))
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

    @Test
    fun `unknown device state must not show a fabricated zero count`() {
        assertEquals(null, verifiedDeviceInventoryCount(0, null))
        assertEquals(null, verifiedDeviceInventoryCount(17, null))
        assertEquals("?", deviceInventoryCountText(verifiedDeviceInventoryCount(0, null)))
    }

    @Test
    fun `verified empty and populated device inventories show exact counts`() {
        assertEquals("0", deviceInventoryCountText(verifiedDeviceInventoryCount(0, "synthetic-device")))
        assertEquals("17", deviceInventoryCountText(verifiedDeviceInventoryCount(17, "synthetic-device")))
    }

    @Test
    fun `status badge and patch filter agree even when async metadata is unavailable`() {
        val packageName = "com.example.video"
        val record = PatchedAppRecord(
            packageName = packageName,
            displayName = "Video",
            apkVersion = "1.0",
            inputApkPath = "/does/not/exist/input.apk",
            outputApkPath = "/does/not/exist/output.apk",
            patchedAt = 0L,
            patchedWithMorpheVersion = "2.0.0",
        )
        val rows = listOf(record)
        val states = mapOf(packageName to PatchedAppState.PATCHED_WITH_UPDATES)
        val infoByPackage = emptyMap<String, RecallUpdateInfo>()
        assertTrue(hasConfirmedPatchUpdate(infoByPackage[packageName], states[packageName]))
        assertEquals(rows, visiblePatchedRecords(AppListFilter.PATCH_UPDATES, rows, infoByPackage, states))
        assertTrue(visiblePatchedRecords(AppListFilter.APP_UPDATES, rows, infoByPackage, states).isEmpty())
    }

    @Test
    fun `plain newer app or repository is not mislabelled a confirmed patch update`() {
        val appVersionOnly = info(appOutdated = true)
        assertFalse(hasConfirmedPatchUpdate(appVersionOnly, PatchedAppState.PATCHED))
        assertTrue(hasConfirmedAppUpdate(appVersionOnly))
        val unscoped = info(sources = listOf(
            RecallUpdateInfo.SourceUpdate("source", "1.0", "2.0", "2.0",
                outdated = true, hasRelevantChanges = false)
        ))
        assertFalse(hasConfirmedPatchUpdate(unscoped, PatchedAppState.PATCHED))
        assertFalse(hasConfirmedPatchUpdate(null, PatchedAppState.PATCHED))
    }

    @Test fun `zero confirmed updates is not a statement that all apps were checked`() {
        val rows = listOf("unknown", "confirmed").map { pkg ->
            PatchedAppRecord(packageName = pkg, displayName = pkg, apkVersion = "1.0",
                inputApkPath = "/not-read/in", outputApkPath = "/not-read/out",
                patchedAt = 1, patchedWithMorpheVersion = "test")
        }
        val unknown = info(suggested = null, resolution = UpdateMetadataResolution.UNAVAILABLE)
        val checked = info(sources = listOf(RecallUpdateInfo.SourceUpdate(
            "source", "2.2.0", "2.2.0", "2.2.0", outdated = false, checkIncomplete = false)))
        val map = mapOf("unknown" to unknown, "confirmed" to checked)
        assertEquals(1, incompleteUpdateChecks(rows, map))
        assertEquals(0, rows.count { hasConfirmedAppUpdate(map[it.packageName]) })
        assertEquals(0, rows.count { hasConfirmedPatchUpdate(map[it.packageName]) })
    }

    @Test fun `newer unsupported app versions do not produce a Morphe update`() {
        val missing = info(appOutdated = false, suggested = null)
        assertFalse(hasConfirmedAppUpdate(missing))
        assertFalse(hasConfirmedPatchUpdate(missing))
    }
}
