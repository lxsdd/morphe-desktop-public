package app.morphe.gui.ui.components

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.util.EnabledSourcesLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceUpdateBannerStateTest {
    private val source = PatchSource(id = "sample", name = "Verified source", type = PatchSourceType.LOCAL)

    @Test fun countedProgressIsMonotonicAndNeverImpliesAnAppUpdate() {
        val started = SourceUpdateBannerState(visible = true, total = 3)
        val one = started.progress(EnabledSourcesLoader.SourceLoadProgress(1, 3, source, 0.25f))
        assertEquals(1, one.completed)
        assertEquals("Verified source", one.activeSourceName)
        assertEquals(0.25f, one.downloadFraction)
        val outOfOrder = one.progress(EnabledSourcesLoader.SourceLoadProgress(0, 3, source))
        assertEquals(1, outOfOrder.completed)
        val finishedResolving = outOfOrder.progress(EnabledSourcesLoader.SourceLoadProgress(9, 3, null))
        assertEquals(3, finishedResolving.completed)
        assertEquals(SourceUpdateStatus.Updating, finishedResolving.status)
        assertNull(finishedResolving.activeSourceName)
        assertTrue(finishedResolving.visible)
    }

    @Test fun successfulFullLoadIsExplicitAndDismissible() {
        val state = SourceUpdateBannerState(visible = true, total = 2)
            .finished(usableSources = 2, failedSources = 0)
        assertTrue(state.visible)
        assertEquals(SourceUpdateStatus.Success, state.status)
        assertEquals(2, state.completed)
        assertEquals(state, state.progress(EnabledSourcesLoader.SourceLoadProgress(0, 2, source)))
    }

    @Test fun unavailableOrPartiallyFailedLoadsNeverShowFullSuccess() {
        for ((usable, failed) in listOf(0 to 2, 1 to 1, 0 to 0)) {
            val state = SourceUpdateBannerState(visible = true, total = 2)
                .finished(usableSources = usable, failedSources = failed)
            assertFalse(state.visible)
            assertEquals(SourceUpdateStatus.Updating, state.status)
        }
        assertFalse(SourceUpdateBannerState(visible = true, total = 2)
            .finished(usableSources = 1, failedSources = 0).visible)
        assertFalse(SourceUpdateBannerState(visible = true, total = 0)
            .finished(usableSources = 1, failedSources = 0).visible)
    }

    @Test fun invalidDownloadFractionCannotPolluteUiOrCrossSources() {
        val init = SourceUpdateBannerState(visible = true, total = 1)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.5f, 1.2f)) {
            assertNull(init.progress(EnabledSourcesLoader.SourceLoadProgress(0, 1, source, invalid)).downloadFraction)
        }
        assertNull(init.progress(EnabledSourcesLoader.SourceLoadProgress(1, 1, null, null)).activeSourceName)
    }

    @Test fun hiddenStateCannotBeRevivedByLateEvents() {
        val hidden = SourceUpdateBannerState()
        assertEquals(hidden, hidden.progress(EnabledSourcesLoader.SourceLoadProgress(1, 2, source)))
        assertFalse(hidden.finished(usableSources = 2, failedSources = 0).visible)
    }
}
