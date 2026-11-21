package app.morphe.gui.ui.screens.home

import app.morphe.engine.AppUpdateCheck
import app.morphe.engine.UpdateInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeDesktopUpdateStatusTest {
    @Test fun failedCustomReleaseProbeIsVisibleButNotAnAvailableUpdate() {
        val state = HomeUiState(updateCheckStatus = AppUpdateCheck.Failed)
        assertTrue(state.showCustomUpdateCheckFailure)
        assertFalse(state.showUpdateBanner)
    }

    @Test fun dismissingFailureDoesNotClaimAnUpdateOrUpToDateStatus() {
        val state = HomeUiState(
            updateCheckStatus = AppUpdateCheck.Failed,
            updateCheckFailureDismissed = true,
        )
        assertFalse(state.showCustomUpdateCheckFailure)
        assertFalse(state.showUpdateBanner)
    }

    @Test fun disabledAndSuccessfulChecksCannotShowFalseFailure() {
        assertFalse(HomeUiState().showCustomUpdateCheckFailure)
        assertFalse(HomeUiState(updateCheckStatus = AppUpdateCheck.UpToDate).showCustomUpdateCheckFailure)
        val info = UpdateInfo(
            currentVersion = "2.2.0-beta.6",
            latestVersion = "2.2.0-beta.7",
            crossesDevToStable = false,
            downloadLink = "https://github.com/lxsdd/morphe-desktop-public/releases",
        )
        val state = HomeUiState(
            updateCheckStatus = AppUpdateCheck.Available(info),
            updateInfo = info,
        )
        assertFalse(state.showCustomUpdateCheckFailure)
        assertTrue(state.showUpdateBanner)
    }
}
