package app.morphe.gui.ui.screens.patches

import app.morphe.engine.ReleaseChannel
import app.morphe.engine.model.Release
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.SourceVersionPref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionBrowserModelTest {
    private val stable = listOf(
        Release(tagName = "v2.3.0", publishedAt = "2026-11-20T10:00:00Z"),
        Release(tagName = "v2.2.0", publishedAt = "2026-11-10T10:00:00Z"),
    )
    private val dev = listOf(
        Release(tagName = "v2.4.0-dev.7", isPrerelease = true, publishedAt = "2026-11-21T16:00:00Z"),
        Release(tagName = "v2.4.0-dev.6", isPrerelease = true, publishedAt = "2026-11-20T16:00:00Z"),
    )

    @Test fun missingPinnedReleaseIsNotSilentlyReplaced() {
        val pref = SourceVersionPref(FollowMode.PINNED, "v2.1.0")
        assertEquals("v2.1.0", sourceInUseTag(pref, true, stable, dev))
        assertNull(sourceInUseTag(SourceVersionPref(FollowMode.PINNED), true, stable, dev))
    }

    @Test fun trackingChannelsHonorExplicitSourcePreference() {
        assertEquals("v2.3.0", sourceInUseTag(SourceVersionPref(FollowMode.FOLLOW_STABLE), true, stable, dev))
        assertEquals("v2.4.0-dev.7", sourceInUseTag(SourceVersionPref(FollowMode.FOLLOW_DEV), false, stable, dev))
        assertEquals("v2.3.0", sourceInUseTag(null, false, stable, dev))
        assertEquals("v2.4.0-dev.7", sourceInUseTag(null, true, stable, dev))
    }

    @Test fun experimentalReleasesAreSearchableFromTheStableTab() {
        val state = PatchesUiState(
            selectedChannel = ReleaseChannel.STABLE,
            stableReleases = stable,
            devReleases = dev,
            versionQuery = "dev.7",
        )
        assertEquals(listOf("v2.4.0-dev.7"), visibleSourceReleases(state).map { it.tagName })
        assertEquals(ReleaseChannel.DEV, releaseChannel(visibleSourceReleases(state).single()))
    }

    @Test fun blankSearchDoesNotMixExperimentalIntoStableResults() {
        val state = PatchesUiState(selectedChannel = ReleaseChannel.STABLE,
            stableReleases = stable, devReleases = dev)
        assertEquals(stable, visibleSourceReleases(state))
    }

    @Test fun offlineSearchUsesOnlyCachedEvidenceAndDoesNotClaimInUse() {
        val state = PatchesUiState(
            sourceId = "official",
            inUseTag = "v2.4.0-dev.7",
            isOffline = true,
            offlineReleases = listOf(stable.first()),
            stableReleases = stable,
            devReleases = dev,
            versionQuery = "dev",
        )
        assertTrue(visibleSourceReleases(state).isEmpty())
        assertFalse(sourceReleaseIsInUse(state, dev.first()))
    }

    @Test fun unknownSourceNeverClaimsInUseEvenIfTagMatches() {
        val selected = stable.first()
        assertFalse(sourceReleaseIsInUse(PatchesUiState(inUseTag = selected.tagName), selected))
        assertTrue(sourceReleaseIsInUse(
            PatchesUiState(sourceId = "official", inUseTag = selected.tagName), selected))
    }

    @Test fun multipleChannelsAreDeduplicatedAndSortedOnlyForSearch() {
        val same = stable.first()
        val state = PatchesUiState(
            stableReleases = stable,
            devReleases = listOf(same) + dev,
            versionQuery = "v2",
        )
        val tags = visibleSourceReleases(state).map { it.tagName }
        assertEquals(tags.toSet().size, tags.size)
        assertEquals("v2.4.0-dev.7", tags.first())
    }
}
