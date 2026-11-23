package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.screens.home.HistoricalSourceIssue
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
                outdated = true, hasRelevantChanges = true, metadataKnown = true)
        ))
        assertTrue(hasConfirmedPatchUpdate(x))
        assertFalse(hasConfirmedAppUpdate(x))
    }

    @Test
    fun `new release tag is not a confirmed scoped patch update until checked`() {
        val unverified = RecallUpdateInfo.SourceUpdate(
            name = "Source A", usedVersion = "1.0",
            resolvedVersion = "1.1", latestAvailableVersion = "1.1",
            outdated = true, hasRelevantChanges = true,
            checkIncomplete = true, metadataKnown = false,
        )
        assertFalse(hasConfirmedPatchUpdate(info(sources = listOf(unverified))))
        assertFalse(hasConfirmedPatchUpdate(null, PatchedAppState.PATCHED_WITH_UPDATES))
        assertTrue(hasConfirmedPatchUpdate(info(sources = listOf(
            unverified.copy(checkIncomplete = false, metadataKnown = true),
        ))))
    }

    @Test
    fun `no newer supported app target is a completed negative when sources are checked`() {
        val record = PatchedAppRecord(
            packageName = "package.stable", displayName = "Stable",
            apkVersion = "1.0", inputApkPath = "/not-read/in",
            outputApkPath = "/not-read/out", patchedAt = 1L,
            patchedWithMorpheVersion = "test",
        )
        val checked = info(suggested = null, sources = listOf(
            RecallUpdateInfo.SourceUpdate(
                "Source", "1.0", "1.0", "1.0",
                outdated = false, metadataKnown = true,
            )
        ))
        assertEquals(0, incompleteUpdateChecks(listOf(record), mapOf(record.packageName to checked)))
        assertFalse(hasConfirmedAppUpdate(checked))
        assertFalse(hasConfirmedPatchUpdate(checked))
    }

    @Test
    fun `unresolved patch metadata cannot confirm a per-app patch update`() {
        val change = RecallUpdateInfo.SourceUpdate("synthetic-source", "1.0", "1.1", "1.1",
            outdated = true, hasRelevantChanges = true, metadataKnown = true)
        assertFalse(hasConfirmedPatchUpdate(info(sources = listOf(change),
            resolution = UpdateMetadataResolution.UNAVAILABLE)))
    }

    @Test
    fun `both update types can apply simultaneously`() {
        val x = info(appOutdated = true, sources = listOf(
            RecallUpdateInfo.SourceUpdate("source", "1.0", "1.1", "1.1",
                outdated = true, hasRelevantChanges = true, metadataKnown = true)
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
    fun `stale history status cannot prove patch update without fresh scoped evidence`() {
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
        assertFalse(hasConfirmedPatchUpdate(infoByPackage[packageName], states[packageName]))
        assertTrue(visiblePatchedRecords(AppListFilter.PATCH_UPDATES, rows, infoByPackage, states).isEmpty())
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
            "source", "2.2.0", "2.2.0", "2.2.0", outdated = false, checkIncomplete = false, metadataKnown = true)))
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

    @Test
    fun `incomplete source details preserve source attribution and reject false updates`() {
        val missing = RecallUpdateInfo.SourceUpdate(
            name = "Synthetic source A", usedVersion = "1.0",
            resolvedVersion = null, latestAvailableVersion = null,
            outdated = false, hasRelevantChanges = false, checkIncomplete = true,
        )
        val changelog = RecallUpdateInfo.SourceUpdate(
            name = "Synthetic source B", usedVersion = "1.0",
            resolvedVersion = "1.3", latestAvailableVersion = "1.3",
            outdated = true, hasRelevantChanges = false, checkIncomplete = true,
        )
        val ready = RecallUpdateInfo.SourceUpdate(
            name = "Ready", usedVersion = "1.0",
            resolvedVersion = "1.1", latestAvailableVersion = "1.1",
            outdated = true, hasRelevantChanges = true, checkIncomplete = false, metadataKnown = true,
        )
        val evidence = info(sources = listOf(missing, changelog, ready))
        assertEquals(listOf(
            IncompleteUpdateSource("Synthetic source A", false),
            IncompleteUpdateSource("Synthetic source B", true),
        ), incompleteUpdateSources(evidence))
        assertTrue(incompleteUpdateSources(null).isEmpty())
        // An unavailable changelog is not proof of a newer app-scoped patch.
        assertFalse(hasConfirmedPatchUpdate(info(sources = listOf(changelog))))
    }
    @Test
    fun `incomplete history banner identifies actual reason without showing a false update`() {
        fun history(pkg: String) = PatchedAppRecord(
            packageName = pkg, displayName = pkg, apkVersion = "1.0",
            inputApkPath = "/not-read/input.apk", outputApkPath = "/not-read/output.apk",
            patchedAt = 1L, patchedWithMorpheVersion = "test",
        )
        val entries = listOf("no-meta", "no-source", "offline", "no-changelog").map(::history)
        val absent = info(sources = listOf(
            RecallUpdateInfo.SourceUpdate("Offline source", "1.0", null, null,
                outdated = false, checkIncomplete = true),
        ))
        val noProof = info(sources = listOf(
            RecallUpdateInfo.SourceUpdate("Community source", "1.0", "1.2", "1.2",
                outdated = true, checkIncomplete = true, metadataKnown = false),
        ))
        val mapping = mapOf(
            "no-source" to info(suggested = null, sources = emptyList()),
            "offline" to absent,
            "no-changelog" to noProof,
        )
        val result = incompleteAppChecks(entries, mapping)
        assertEquals(4, result.size)
        assertEquals(mapOf(
            "no-meta" to IncompleteUpdateReason.METADATA_UNAVAILABLE,
            "no-source" to IncompleteUpdateReason.NO_SOURCE_PROOF,
            "offline" to IncompleteUpdateReason.SOURCE_UNAVAILABLE,
            "no-changelog" to IncompleteUpdateReason.CHANGE_PROOF_UNAVAILABLE,
        ), result.associate { it.appName to it.reason })
        assertEquals("Community source",
            result.single { it.appName == "no-changelog" }.sourceName)
        assertEquals(0, entries.count { hasConfirmedPatchUpdate(mapping[it.packageName]) })
    }

    @Test
    fun `actual inaccessible source and unmatched historical YouTube identity appear in shared UI evidence`() {
        fun history(pkg: String) = PatchedAppRecord(
            packageName = pkg, displayName = pkg, apkVersion = "1.0",
            inputApkPath = "/not-read/input.apk", outputApkPath = "/not-read/output.apk",
            patchedAt = 1L, patchedWithMorpheVersion = "test",
        )
        fun missing(name: String, blocker: HistoricalSourceIssue) = info(sources = listOf(
            RecallUpdateInfo.SourceUpdate(
                name = name, usedVersion = "1.0", resolvedVersion = null,
                latestAvailableVersion = null, outdated = false,
                checkIncomplete = true, metadataKnown = false, sourceIssue = blocker,
            )
        ))
        val records = listOf(history("capcut"), history("soundcloud"), history("youtube"))
        val mapping = mapOf(
            "capcut" to missing("Riky patches", HistoricalSourceIssue.SOURCE_NOT_ACCESSIBLE),
            "soundcloud" to missing("Hoodles patches", HistoricalSourceIssue.SOURCE_POLICY_BLOCKED),
            "youtube" to missing("Recorded YouTube source", HistoricalSourceIssue.SOURCE_UNMATCHED),
        )
        val incomplete = incompleteAppChecks(records, mapping).associateBy { it.appName }
        assertEquals(3, incomplete.size)
        assertEquals(HistoricalSourceIssue.SOURCE_NOT_ACCESSIBLE, incomplete["capcut"]?.sourceIssue)
        assertEquals(HistoricalSourceIssue.SOURCE_POLICY_BLOCKED, incomplete["soundcloud"]?.sourceIssue)
        assertEquals(HistoricalSourceIssue.SOURCE_UNMATCHED, incomplete["youtube"]?.sourceIssue)
        assertEquals(HistoricalSourceIssue.SOURCE_UNMATCHED,
            incompleteUpdateSources(mapping["youtube"]).single().sourceIssue)
        assertEquals(0, records.count { hasConfirmedPatchUpdate(mapping[it.packageName]) })
    }

    @Test
    fun `experimental app updates do not masquerade as stable updates`() {
        val stable = info(appOutdated = true)
        val experimental = stable.copy(appChannel = RecallUpdateInfo.AppChannel.EXPERIMENTAL)
        assertTrue(hasConfirmedAppUpdate(stable))
        assertFalse(isExperimentalAppUpdate(stable))
        assertTrue(hasConfirmedAppUpdate(experimental))
        assertTrue(isExperimentalAppUpdate(experimental))
        assertFalse(isExperimentalAppUpdate(
            experimental.copy(metadataResolution = UpdateMetadataResolution.UNAVAILABLE)
        ))
        assertFalse(isExperimentalAppUpdate(null))
    }

    @Test
    fun `incomplete checks reveal exact app names without suggesting unavailable updates`() {
        fun history(pkg: String, name: String) = PatchedAppRecord(
            packageName = pkg,
            displayName = name,
            apkVersion = "1.0",
            inputApkPath = "/in/not-read",
            outputApkPath = "/out/not-read",
            patchedAt = 0L,
            patchedWithMorpheVersion = "synthetic",
        )
        val records = listOf(
            history("pkg.third", "Third record"),
            history("pkg.first", "First record"),
            history("pkg.good", "Proven record"),
        )
        val source = RecallUpdateInfo.SourceUpdate(
            name = "Unavailable community source", usedVersion = "1.0",
            resolvedVersion = null, latestAvailableVersion = null,
            outdated = false, checkIncomplete = true,
        )
        val infoByPackage = mapOf(
            "pkg.first" to info(suggested = null, resolution = UpdateMetadataResolution.UNAVAILABLE),
            "pkg.third" to info(sources = listOf(source)),
            // A truly checked app has usable, resolved source metadata; an empty
            // source list must remain UNKNOWN rather than imply no patches changed.
            "pkg.good" to info(appOutdated = false, suggested = "1.0", sources = listOf(
                RecallUpdateInfo.SourceUpdate(
                    name = "Verified source", usedVersion = "1.0",
                    resolvedVersion = "1.0", latestAvailableVersion = "1.0",
                    outdated = false, hasRelevantChanges = false, checkIncomplete = false, metadataKnown = true,
                )
            )),
        )
        val names = incompleteUpdateRecordNames(
            records, infoByPackage, mapOf("pkg.third" to "A display name"),
        )
        assertEquals(listOf("A display name", "First record"), names)
        assertEquals(2, incompleteUpdateChecks(records, infoByPackage))
        assertFalse(hasConfirmedAppUpdate(infoByPackage["pkg.first"]))
        assertFalse(hasConfirmedPatchUpdate(infoByPackage["pkg.third"]))
    }

    @Test
    fun `source-management action follows real provenance blockers only`() {
        val sourceIssues = listOf(
            HistoricalSourceIssue.SOURCE_UNMATCHED,
            HistoricalSourceIssue.SOURCE_DISABLED,
            HistoricalSourceIssue.SOURCE_NOT_ACCESSIBLE,
            HistoricalSourceIssue.SOURCE_POLICY_BLOCKED,
            HistoricalSourceIssue.SOURCE_ACCESS_RESTRICTED,
        )
        sourceIssues.forEach { issue ->
            assertTrue(shouldOfferSourceManagement(listOf(IncompleteAppCheck(
                appName = "Example", sourceName = "Real provider",
                reason = IncompleteUpdateReason.SOURCE_UNAVAILABLE,
                sourceIssue = issue,
            ))))
        }
        assertFalse(shouldOfferSourceManagement(emptyList()))
        assertFalse(shouldOfferSourceManagement(listOf(IncompleteAppCheck(
            appName = "Example", sourceName = null,
            reason = IncompleteUpdateReason.METADATA_UNAVAILABLE,
        ))))
    }


}
