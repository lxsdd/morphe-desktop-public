package app.morphe.gui.data.repository

import app.morphe.engine.AppUpdateCheck
import app.morphe.engine.UpdateInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SuccessfulCustomUpdateCacheTest {
    private fun sample() = AppUpdateCheck.Available(
        UpdateInfo("2.2.0-beta.6", "2.2.0-beta.7", false,
            "https://github.com/lxsdd/morphe-desktop-public/releases/download/example")
    )

    @Test fun failureIsRetryableAndNeverAppearsAsConfirmedCurrent() {
        val cache = SuccessfulCustomUpdateCache()
        cache.remember(AppUpdateCheck.Failed)
        assertNull(cache.get())
        cache.remember(AppUpdateCheck.UpToDate)
        assertIs<AppUpdateCheck.UpToDate>(cache.get())
        cache.clear()
        assertNull(cache.get())
    }

    @Test fun positiveUpdateRemainsStableAcrossSubsequentFailedProbes() {
        val cache = SuccessfulCustomUpdateCache()
        val update = sample()
        cache.remember(update)
        cache.remember(AppUpdateCheck.Failed)
        assertEquals(update, cache.get())
        cache.clear()
        assertNull(cache.get())
    }

    @Test fun availableUpdateIsPreservedAsVerifiedCustomOnly() {
        val cache = SuccessfulCustomUpdateCache()
        cache.remember(sample())
        assertEquals("2.2.0-beta.7", assertIs<AppUpdateCheck.Available>(cache.get()).info.latestVersion)
    }
}
