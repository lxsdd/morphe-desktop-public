package app.morphe.gui.ui.screens.home.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceOverviewStatusTest {
    private fun evidence(
        supported: Boolean = true, history: Boolean = true,
        installed: String? = "1.0", local: String? = "1.0",
        signer: Boolean? = true, appUpdate: Boolean = false,
        patchUpdate: Boolean = false, localNewer: Boolean = false,
    ) = DeviceOverviewEvidence(supported, history, installed, local, signer,
        appUpdate, patchUpdate, localNewer)

    @Test fun `wrong signer is never advertised as safe to update`() {
        assertEquals(DeviceOverviewStatus.SIGNER_CONFLICT,
            classifyDeviceOverview(evidence(signer = false, patchUpdate = true)))
    }
    @Test fun `new app version and new patch version are separate`() {
        assertEquals(DeviceOverviewStatus.APP_UPDATE,
            classifyDeviceOverview(evidence(appUpdate = true, patchUpdate = true)))
        assertEquals(DeviceOverviewStatus.PATCH_UPDATE,
            classifyDeviceOverview(evidence(patchUpdate = true).copy(exactBuildVerified = true)))
        assertEquals(DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED,
            classifyDeviceOverview(evidence(patchUpdate = true)))
        assertEquals(DeviceOverviewStatus.REVIEW_REQUIRED,
            classifyDeviceOverview(evidence(patchUpdate = true, installed = "1.2")))
    }
    @Test fun `missing installed version blocks certainty`() {
        assertEquals(DeviceOverviewStatus.REVIEW_REQUIRED,
            classifyDeviceOverview(evidence(installed = null, patchUpdate = true)))
    }
    @Test fun `matching signer and version are not cryptographic identity`() {
        assertEquals(DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED,
            classifyDeviceOverview(evidence()))
    }
    @Test fun `catalog package is patchable only for a confirmed compatible version`() {
        val stock = evidence(history = false, local = null, signer = null)
        assertEquals(DeviceOverviewStatus.PATCH_SOURCE_UNVERIFIED,
            classifyDeviceOverview(stock))
        assertEquals(DeviceOverviewStatus.PATCH_CANDIDATE,
            classifyDeviceOverview(stock.copy(sourceCompatibility = DevicePatchCompatibility.COMPATIBLE)))
        assertEquals(DeviceOverviewStatus.PATCH_VERSION_INCOMPATIBLE,
            classifyDeviceOverview(stock.copy(sourceCompatibility = DevicePatchCompatibility.INCOMPATIBLE)))
    }
    @Test fun `unknown local history cannot mark Morphe signed device app as unpatched`() {
        assertEquals(DeviceOverviewStatus.REVIEW_REQUIRED,
            classifyDeviceOverview(evidence(history = false, local = null, signer = true)))
    }

    @Test fun `non catalog packages remain visible`() {
        assertEquals(DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG,
            classifyDeviceOverview(evidence(supported = false, history = false)))
    }
    @Test fun `newer local output is a review, not an automatic installation`() {
        assertEquals(DeviceOverviewStatus.LOCAL_BUILD_REVIEW,
            classifyDeviceOverview(evidence(localNewer = true)))
    }
    @Test fun `byte identical signed APK proves identity independently of current key`() {
        assertEquals(DeviceOverviewStatus.EXACT_BUILD_VERIFIED,
            classifyDeviceOverview(evidence().copy(exactBuildVerified = true)))
        assertEquals(DeviceOverviewStatus.EXACT_BUILD_OTHER_KEY,
            classifyDeviceOverview(evidence(signer = false).copy(exactBuildVerified = true)))
        assertEquals(DeviceOverviewStatus.REVIEW_REQUIRED,
            classifyDeviceOverview(evidence(installed = "1.2").copy(exactBuildVerified = true)))
        assertEquals(DeviceOverviewStatus.PATCH_UPDATE,
            classifyDeviceOverview(evidence(patchUpdate = true).copy(exactBuildVerified = true)))
    }

    @Test fun `renamed Morphe installation is not the original app's installed patch evidence`() {
        val stock = evidence(history = false, local = null, signer = null)
        assertEquals(DeviceOverviewStatus.ORIGINAL_WITH_SEPARATE_INSTALL,
            classifyDeviceOverview(stock.copy(separateLocalPackageInstalled = true)))
        assertEquals(DeviceOverviewStatus.PATCH_SOURCE_UNVERIFIED,
            classifyDeviceOverview(stock))
        assertEquals(DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG,
            classifyDeviceOverview(stock.copy(patchSupported = false, separateLocalPackageInstalled = true)))
    }
    @Test fun `unverified SHA or changed version cannot override current-key mismatch`() {
        assertEquals(DeviceOverviewStatus.SIGNER_CONFLICT,
            classifyDeviceOverview(evidence(signer = false).copy(exactBuildVerified = false)))
        assertEquals(DeviceOverviewStatus.SIGNER_CONFLICT,
            classifyDeviceOverview(evidence(signer = false, installed = "2.0")
                .copy(exactBuildVerified = true)))
    }

    @Test fun `unknown signer with equal version cannot be marked current`() {
        assertEquals(DeviceOverviewStatus.REVIEW_REQUIRED,
            classifyDeviceOverview(evidence(signer = null)))
    }

    @Test fun `signer mismatch and missing proof never imply a confirmed update`() {
        assertEquals(0, confirmedDeviceUpdateCount(listOf(
            DeviceUpdateFacts(), DeviceUpdateFacts(), DeviceUpdateFacts())))
        assertEquals(true, deviceTechnicalReview(DeviceOverviewStatus.SIGNER_CONFLICT))
        assertEquals(false, deviceTechnicalReview(DeviceOverviewStatus.EXACT_BUILD_OTHER_KEY))
        assertEquals(false, deviceTechnicalReview(DeviceOverviewStatus.ORIGINAL_WITH_SEPARATE_INSTALL))
    }
    @Test fun `confirmed patch and app updates are counted once per package`() {
        assertEquals(3, confirmedDeviceUpdateCount(listOf(
            DeviceUpdateFacts(), DeviceUpdateFacts(true), DeviceUpdateFacts(false, true),
            DeviceUpdateFacts(true, true))))
    }
    @Test fun `technical signer state cannot mask an actual independently confirmed update`() {
        assertEquals(DeviceOverviewStatus.SIGNER_CONFLICT,
            classifyDeviceOverview(evidence(signer = false, patchUpdate = true)))
        assertEquals(1, confirmedDeviceUpdateCount(listOf(DeviceUpdateFacts(
            confirmedPatchUpdate = true))))
    }
}
