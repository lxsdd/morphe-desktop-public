/*
 * Morphe Desktop Custom — read-only provenance of incomplete historical source checks.
 * Never converts an inaccessible source into a trusted replacement or a current update.
 */
package app.morphe.gui.ui.screens.home

internal enum class HistoricalSourceIssue {
    SOURCE_UNMATCHED, SOURCE_DISABLED, SOURCE_NOT_ACCESSIBLE, SOURCE_ACCESS_RESTRICTED,
    TRANSIENT_FAILURE, SOURCE_LOAD_FAILURE, SOURCE_NOT_RESOLVED, OFFLINE_CACHE,
    NO_COMPATIBLE_PATCHES, CHANGELOG_UNVERIFIED, CHANNEL_UNVERIFIED,
}

/**
 * Classifies diagnostic evidence from the configured source and its real load result.
 * HTTP 404/410/451 does not prove permanent deletion: private, moved and policy-blocked
 * repositories are also possible. We never infer status from a source's display name.
 */
internal fun historicalSourceIssue(
    configured: Boolean,
    enabled: Boolean,
    resolved: Boolean,
    error: String?,
    offline: Boolean,
    compatiblePatches: Boolean,
    scopedCheckComplete: Boolean,
    channelKnown: Boolean,
): HistoricalSourceIssue? {
    if (!configured) return HistoricalSourceIssue.SOURCE_UNMATCHED
    if (!enabled) return HistoricalSourceIssue.SOURCE_DISABLED
    if (!error.isNullOrBlank()) {
        val raw = error.lowercase()
        return when {
            Regex("""\b(404|410|451)\b""").containsMatchIn(raw) ||
                "repository not found" in raw || "terms of service" in raw ||
                "repository has been disabled" in raw || "not accessible" in raw ->
                HistoricalSourceIssue.SOURCE_NOT_ACCESSIBLE
            Regex("""\b(401|403)\b""").containsMatchIn(raw) ||
                "unauthorized" in raw || "forbidden" in raw ||
                "requires authentication" in raw ->
                HistoricalSourceIssue.SOURCE_ACCESS_RESTRICTED
            Regex("""\b(408|429|500|502|503|504)\b""").containsMatchIn(raw) ||
                "timeout" in raw || "timed out" in raw || "connection reset" in raw ||
                "rate limit" in raw || "unknownhost" in raw ->
                HistoricalSourceIssue.TRANSIENT_FAILURE
            else -> HistoricalSourceIssue.SOURCE_LOAD_FAILURE
        }
    }
    if (!resolved) return HistoricalSourceIssue.SOURCE_NOT_RESOLVED
    if (offline) return HistoricalSourceIssue.OFFLINE_CACHE
    if (!compatiblePatches) return HistoricalSourceIssue.NO_COMPATIBLE_PATCHES
    if (!scopedCheckComplete) return HistoricalSourceIssue.CHANGELOG_UNVERIFIED
    if (!channelKnown) return HistoricalSourceIssue.CHANNEL_UNVERIFIED
    return null
}
