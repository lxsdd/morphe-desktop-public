package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.data.model.AppConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class CommunityPatchesDiscoveryTest {
    private fun visible(
        enabled: Boolean = true,
        filter: AppListFilter = AppListFilter.ALL,
        search: String = "",
        loading: Boolean = false,
        error: String? = null,
        supportedCount: Int = 5,
    ) = shouldOfferCommunityPatches(enabled, filter, search, loading, error, supportedCount)

    @Test fun communityCardRequiresExplicitOptInAndRealCatalog() {
        assertTrue(visible())
        assertFalse(visible(enabled = false))
        assertFalse(visible(supportedCount = 0))
        assertFalse(visible(loading = true))
        assertFalse(visible(error = "source validation failed"))
        assertFalse(visible(search = "YouTube"))
    }

    @Test fun communityCardCannotAppearAsAConfirmedAppOrPatchUpdate() {
        assertFalse(visible(filter = AppListFilter.DEVICE))
        assertFalse(visible(filter = AppListFilter.YOURS))
        assertFalse(visible(filter = AppListFilter.APP_UPDATES))
        assertFalse(visible(filter = AppListFilter.PATCH_UPDATES))
    }

    @Test fun preferencesDefaultSafelyAndRoundTripWithoutChangingOtherSettings() {
        val json = Json { ignoreUnknownKeys = true }
        val preexisting = json.decodeFromString<AppConfig>("{}")
        assertFalse(preexisting.showMoreAppsCard)
        assertTrue(preexisting.useOutlines, "Previously existing Custom outlines stay on after schema migration")
        assertFalse(preexisting.communityNoticeAcknowledged)
        val changed = preexisting.copy(
            showMoreAppsCard = true,
            communityNoticeAcknowledged = true,
            homeAppListFilter = "APP_UPDATES",
            useOutlines = false,
            gitHubPat = "test-only-placeholder",
        )
        val restored = json.decodeFromString<AppConfig>(json.encodeToString(changed))
        assertFalse(restored.useOutlines, "The outlines toggle must persist without altering other preferences")
        assertTrue(restored.showMoreAppsCard)
        assertTrue(restored.communityNoticeAcknowledged)
        assertTrue(restored.gitHubPat == "test-only-placeholder")
        assertTrue(restored.homeAppListFilter == "APP_UPDATES")
    }
}
