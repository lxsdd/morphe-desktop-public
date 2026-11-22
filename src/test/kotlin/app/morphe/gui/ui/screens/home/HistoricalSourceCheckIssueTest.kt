package app.morphe.gui.ui.screens.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoricalSourceCheckIssueTest {
    private fun issue(
        configured: Boolean = true,
        enabled: Boolean = true,
        resolved: Boolean = true,
        error: String? = null,
        offline: Boolean = false,
        compatiblePatches: Boolean = true,
        scopedCheckComplete: Boolean = true,
        channelKnown: Boolean = true,
    ) = historicalSourceIssue(
        configured, enabled, resolved, error, offline, compatiblePatches,
        scopedCheckComplete, channelKnown,
    )

    @Test fun `missing historical YouTube source is not declared current`() {
        assertEquals(HistoricalSourceIssue.SOURCE_UNMATCHED,
            issue(configured = false, resolved = false))
        assertEquals(HistoricalSourceIssue.SOURCE_DISABLED,
            issue(enabled = false, resolved = false))
        assertEquals(HistoricalSourceIssue.SOURCE_NOT_RESOLVED,
            issue(resolved = false))
    }

    @Test fun `riky CapCut missing or Hoodles policy restricted is diagnosed without guessing permanent deletion`() {
        assertEquals(HistoricalSourceIssue.SOURCE_NOT_ACCESSIBLE,
            issue(error = "GitHub returned 404 Not Found"))
        assertEquals(HistoricalSourceIssue.SOURCE_NOT_ACCESSIBLE,
            issue(error = "451 Unavailable For Legal Reasons"))
        assertEquals(HistoricalSourceIssue.SOURCE_POLICY_BLOCKED,
            issue(error = "Repository has been disabled because of a Terms of Service violation"))
        assertEquals(HistoricalSourceIssue.SOURCE_POLICY_BLOCKED,
            issue(error = "403 Repository access blocked; reason: tos"))
        assertEquals(HistoricalSourceIssue.SOURCE_ACCESS_RESTRICTED,
            issue(error = "403 Forbidden"))
    }

    @Test fun `transient connectivity, signer or unknown failure remains separate from missing repository`() {
        assertEquals(HistoricalSourceIssue.TRANSIENT_FAILURE,
            issue(error = "429 rate limit"))
        assertEquals(HistoricalSourceIssue.TRANSIENT_FAILURE,
            issue(error = "connection reset"))
        assertEquals(HistoricalSourceIssue.SOURCE_LOAD_FAILURE,
            issue(error = "Patch bundle signature changed"))
        assertEquals(HistoricalSourceIssue.SOURCE_LOAD_FAILURE,
            issue(error = "Invalid MPP manifest"))
    }

    @Test fun `a cached bundle or missing relevant changelog is never a proven current patch`() {
        assertEquals(HistoricalSourceIssue.OFFLINE_CACHE, issue(offline = true))
        assertEquals(HistoricalSourceIssue.NO_COMPATIBLE_PATCHES,
            issue(compatiblePatches = false))
        assertEquals(HistoricalSourceIssue.CHANGELOG_UNVERIFIED,
            issue(scopedCheckComplete = false))
        assertEquals(HistoricalSourceIssue.CHANNEL_UNVERIFIED,
            issue(channelKnown = false))
        assertNull(issue())
    }

    @Test fun `specific source failure always wins over generic missing changelog`() {
        assertEquals(HistoricalSourceIssue.SOURCE_NOT_ACCESSIBLE,
            issue(error = "HTTP 404", scopedCheckComplete = false,
                compatiblePatches = false, resolved = false))
    }
}
