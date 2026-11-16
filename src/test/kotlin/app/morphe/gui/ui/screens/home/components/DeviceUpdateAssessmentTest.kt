package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.util.UpdateMetadataResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceUpdateAssessmentTest {
    private fun app(
        stable: String? = "21.30.216",
        experimental: List<String> = listOf("21.45.82"),
    ) = SupportedApp(
        packageName = "com.google.android.youtube",
        displayName = "YouTube",
        supportedVersions = listOfNotNull("21.18.164", stable),
        experimentalVersions = experimental,
        recommendedVersion = stable,
    )

    private fun record(original: String, installed: String, version: String) = PatchedAppRecord(
        packageName = original,
        currentPackageName = installed,
        displayName = "Locally patched",
        apkVersion = version,
        inputApkPath = "/not-read/input.apk",
        outputApkPath = "/not-read/output.apk",
        patchedAt = 1L,
        patchedWithMorpheVersion = "test",
    )

    private fun sources(confirmed: Boolean, appVersion: String = "21.45.82") = RecallUpdateInfo(
        sources = listOf(RecallUpdateInfo.SourceUpdate(
            sourceId = "trusted-source",
            name = "Morphe Patches",
            usedVersion = "1.0",
            resolvedVersion = "1.1",
            latestAvailableVersion = "1.1",
            outdated = true,
            hasRelevantChanges = confirmed,
            metadataKnown = true,
            checkIncomplete = false,
        )),
        appUsedVersion = appVersion,
        appChannel = RecallUpdateInfo.AppChannel.EXPERIMENTAL,
        appSuggestedVersion = "21.45.82",
        appOutdated = false,
    )

    @Test fun `original YouTube update does not make renamed YouTube Morphe outdated`() {
        val catalog = app()
        val originalTarget = latestCompatibleDeviceAppTarget(catalog, "21.18.164")
        val cloneTarget = latestCompatibleDeviceAppTarget(catalog, "21.45.82")
        assertEquals("21.30.216", originalTarget)
        assertEquals("21.45.82", cloneTarget)
        val history = record("com.google.android.youtube", "app.morphe.android.youtube", "21.45.82")
        val original = verifiedDeviceUpdateFacts(
            "com.google.android.youtube", "21.18.164", originalTarget,
            history, sources(false), false,
        )
        val clone = verifiedDeviceUpdateFacts(
            "app.morphe.android.youtube", "21.45.82", cloneTarget,
            history, sources(false), false,
        )
        assertTrue(original.confirmedAppVersionUpdate)
        assertFalse(original.confirmedPatchUpdate)
        assertFalse(clone.hasConfirmedUpdate)
        assertEquals(DeviceReadableUpdateStatus.CHECKING,
            deviceReadableUpdateStatus(clone, DeviceOverviewStatus.SIGNER_CONFLICT,
                sources(false), history, "21.45.82", false))
    }

    @Test fun `exact matching installed clone permits real source update even with another current key`() {
        val history = record("com.google.android.youtube", "app.morphe.android.youtube", "21.45.82")
        val actual = verifiedDeviceUpdateFacts(
            "app.morphe.android.youtube", "21.45.82", "21.45.82",
            history, sources(true), true,
        )
        assertFalse(actual.confirmedAppVersionUpdate)
        assertTrue(actual.confirmedPatchUpdate)
        assertEquals(DeviceReadableUpdateStatus.PATCH,
            deviceReadableUpdateStatus(actual, DeviceOverviewStatus.EXACT_BUILD_OTHER_KEY,
                sources(true), history, "21.45.82", true))
        assertEquals(1, confirmedDeviceUpdateCount(listOf(actual)))
    }

    @Test fun `signer mismatch or old history never implies an installed patch update`() {
        val history = record("com.google.android.youtube", "app.morphe.android.youtube", "21.45.82")
        val positive = sources(true)
        for ((pkg,version,exact) in listOf(
            Triple("com.google.android.youtube", "21.45.82", true),
            Triple("app.morphe.android.youtube", "21.45.82", false),
            Triple("app.morphe.android.youtube", "21.44.00", true),
        )) {
            assertFalse(verifiedDeviceUpdateFacts(pkg, version, "21.45.82",
                history, positive, exact).confirmedPatchUpdate)
        }
        val unchecked = positive.copy(sources = positive.sources.map {
            it.copy(checkIncomplete = true, metadataKnown = false)
        })
        assertFalse(verifiedDeviceUpdateFacts(
            "app.morphe.android.youtube", "21.45.82", null,
            history, unchecked, true,
        ).confirmedPatchUpdate)
    }

    @Test fun `unknown change proof cannot mark an exact device build current`() {
        val history = record("com.example.app", "com.example.app", "1.0")
        val unknown = verifiedDeviceUpdateFacts("com.example.app", "1.0", null,
            history, null, true)
        assertEquals(DeviceReadableUpdateStatus.CHECKING,
            deviceReadableUpdateStatus(unknown, DeviceOverviewStatus.EXACT_BUILD_VERIFIED,
                null, history, "1.0", true))
        val checked = sources(false, appVersion = "1.0")
        assertEquals(DeviceReadableUpdateStatus.CURRENT,
            deviceReadableUpdateStatus(unknown, DeviceOverviewStatus.EXACT_BUILD_OTHER_KEY,
                checked, history, "1.0", true))
    }

    @Test fun `unsupported installed Google Photos version is not an app update`() {
        val photos = SupportedApp("com.google.android.apps.photos", "Photos",
            supportedVersions = listOf("7.0"), recommendedVersion = "7.0")
        val target = latestCompatibleDeviceAppTarget(photos, "8.0.0")
        assertEquals("7.0", target)
        val facts = verifiedDeviceUpdateFacts("com.google.android.apps.photos",
            "8.0.0", target, null, null, null)
        assertFalse(facts.hasConfirmedUpdate)
        assertEquals(DeviceReadableUpdateStatus.UNSUPPORTED,
            deviceReadableUpdateStatus(facts, DeviceOverviewStatus.PATCH_VERSION_INCOMPATIBLE,
                null, null, "8.0.0", null))
    }

    @Test fun `app and patch facts overlap but count one installed app`() {
        val history = record("app.demo", "app.demo", "1.0")
        val actual = verifiedDeviceUpdateFacts("app.demo", "1.0", "2.0",
            history, sources(true, appVersion = "1.0"), true)
        assertTrue(actual.confirmedAppVersionUpdate)
        assertTrue(actual.confirmedPatchUpdate)
        assertEquals(1, confirmedDeviceUpdateCount(listOf(actual)))
        assertEquals(DeviceReadableUpdateStatus.APP_AND_PATCH,
            deviceReadableUpdateStatus(actual, DeviceOverviewStatus.APP_UPDATE,
                sources(true, appVersion = "1.0"), history, "1.0", true))
    }

    @Test fun `device diagnostic source updates require exact installed APK and recipe`() {
        val history = record("com.google.android.youtube", "app.morphe.android.youtube", "21.45.82")
        val verified = sources(true)
        fun diagnostic(
            pkg: String = "app.morphe.android.youtube",
            version: String? = "21.45.82",
            info: RecallUpdateInfo? = verified,
            exact: Boolean? = true,
        ) = verifiedDeviceDiagnosticPatchSources(pkg, version, history, info, exact)

        assertEquals(listOf("Morphe Patches"), diagnostic().map { it.name })
        assertTrue(diagnostic(pkg = "com.google.android.youtube").isEmpty())
        assertTrue(diagnostic(version = "21.44.528").isEmpty())
        assertTrue(diagnostic(exact = null).isEmpty())
        assertTrue(diagnostic(exact = false).isEmpty())
        assertTrue(diagnostic(info = null).isEmpty())
        assertTrue(diagnostic(info = verified.copy(appUsedVersion = "21.44.528")).isEmpty())
    }

    @Test fun `diagnostic source names exclude unresolved and irrelevant changelog claims`() {
        val history = record("com.google.android.youtube", "app.morphe.android.youtube", "21.45.82")
        val verified = sources(true)
        val confirmed = verified.sources.single()
        val unchecked = confirmed.copy(sourceId = "offline", name = "Unverified source",
            metadataKnown = false, checkIncomplete = true)
        val irrelevant = confirmed.copy(sourceId = "unchanged", name = "Unchanged source",
            hasRelevantChanges = false)
        val noRelease = confirmed.copy(sourceId = "no-release", name = "Missing version",
            latestAvailableVersion = null)
        val combined = verified.copy(sources = listOf(unchecked, irrelevant, noRelease, confirmed))
        fun diagnostic(info: RecallUpdateInfo) = verifiedDeviceDiagnosticPatchSources(
            "app.morphe.android.youtube", "21.45.82", history, info, true,
        )

        assertEquals(listOf("Morphe Patches"), diagnostic(combined).map { it.name })
        assertTrue(diagnostic(combined.copy(sources = listOf(unchecked, irrelevant, noRelease))).isEmpty())
        assertTrue(diagnostic(combined.copy(metadataResolution = UpdateMetadataResolution.UNAVAILABLE)).isEmpty())
        assertTrue(diagnostic(combined.copy(metadataResolution = UpdateMetadataResolution.CHECKING)).isEmpty())
    }

}
