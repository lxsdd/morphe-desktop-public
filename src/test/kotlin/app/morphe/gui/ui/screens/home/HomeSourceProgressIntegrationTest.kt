package app.morphe.gui.ui.screens.home

import app.morphe.gui.ui.components.SourceUpdateBannerState
import app.morphe.gui.ui.components.SourceUpdateStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Patch-source load progress never masquerades as confirmed Custom/app updates. */
class HomeSourceProgressIntegrationTest {
    @Test fun loadingSourceIsVisibleWithoutInventingAConfirmedAppUpdate() {
        val state = HomeUiState(
            isLoadingPatches = true,
            sourceUpdateBanner = SourceUpdateBannerState(
                visible = true,
                status = SourceUpdateStatus.Updating,
                completed = 1,
                total = 4,
            ),
        )
        assertTrue(state.sourceUpdateBanner.visible)
        assertFalse(state.showUpdateBanner)
        assertFalse(state.showCustomUpdateCheckFailure)
        assertTrue(state.isLoadingPatches)
    }

    @Test fun aPartiallyFailedSourceLoadCannotRaiseTheCompletionBanner() {
        val state = HomeUiState(
            sourceUpdateBanner = SourceUpdateBannerState(visible = true, total = 3)
                .finished(usableSources = 2, failedSources = 1),
            showSourcesFailedBanner = true,
            failedSourcesCount = 1,
        )
        assertFalse(state.sourceUpdateBanner.visible)
        assertTrue(state.showSourcesFailedBanner)
        assertFalse(state.showUpdateBanner)
    }
}
