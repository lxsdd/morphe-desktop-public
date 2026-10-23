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
    @Test fun `supported device app is visible without local patch history`() {
        assertEquals(DeviceOverviewStatus.PATCH_CANDIDATE,
            classifyDeviceOverview(evidence(history = false, local = null, signer = null)))
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
    @Test fun `matching exact SHA verifies local build only when signer and version agree`() {
        assertEquals(DeviceOverviewStatus.EXACT_BUILD_VERIFIED,
            classifyDeviceOverview(evidence().copy(exactBuildVerified = true)))
        assertEquals(DeviceOverviewStatus.SIGNER_CONFLICT,
            classifyDeviceOverview(evidence(signer = false).copy(exactBuildVerified = true)))
        assertEquals(DeviceOverviewStatus.REVIEW_REQUIRED,
            classifyDeviceOverview(evidence(installed = "1.2").copy(exactBuildVerified = true)))
        assertEquals(DeviceOverviewStatus.PATCH_UPDATE,
            classifyDeviceOverview(evidence(patchUpdate = true).copy(exactBuildVerified = true)))
    }

    @Test fun `unknown signer with equal version cannot be marked current`() {
        assertEquals(DeviceOverviewStatus.REVIEW_REQUIRED,
            classifyDeviceOverview(evidence(signer = null)))
    }
}
