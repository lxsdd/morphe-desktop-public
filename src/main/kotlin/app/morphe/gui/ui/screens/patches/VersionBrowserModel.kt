/*
 * Morphe Desktop Custom — source-scoped version presentation (upstream 2.3 UI).
 * All helpers are pure: browsing a version never mutates device/APK/source data.
 */
package app.morphe.gui.ui.screens.patches

import app.morphe.engine.ReleaseChannel
import app.morphe.engine.model.Release
import app.morphe.engine.util.newerRelease
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.SourceVersionPref

/** A missing PIN stays missing; it must never silently appear to be the newest release. */
internal fun sourceInUseTag(
    preference: SourceVersionPref?,
    usePreRelease: Boolean,
    stable: List<Release>,
    dev: List<Release>,
): String? = when (preference?.mode) {
    FollowMode.PINNED -> preference.pinnedTag?.takeIf(String::isNotBlank)
    FollowMode.FOLLOW_STABLE -> stable.firstOrNull()?.tagName
    FollowMode.FOLLOW_DEV -> newerRelease(dev.firstOrNull(), stable.firstOrNull())?.tagName
    null -> if (usePreRelease) newerRelease(dev.firstOrNull(), stable.firstOrNull())?.tagName
            else stable.firstOrNull()?.tagName
}

/**
 * Resolve a selected release without substituting a missing explicit source pin.
 * Following sources may fall back; a PINNED source always requires an exact match.
 */
internal fun initialSourceRelease(
    preference: SourceVersionPref?,
    savedTag: String?,
    available: List<Release>,
    fallback: Release?,
): Release? {
    val match = savedTag?.takeIf { it.isNotBlank() }
        ?.let { tag -> available.firstOrNull { it.tagName == tag } }
    return if (preference?.mode == FollowMode.PINNED) match else match ?: fallback
}

/** A missing pinned tag can only be asserted from live, source-specific release results. */
internal fun sourcePinnedReleaseMissing(state: PatchesUiState): Boolean =
    !state.isOffline &&
        state.sourceId != null &&
        state.sourceTrackingMode == FollowMode.PINNED &&
        !state.inUseTag.isNullOrBlank() &&
        (state.stableReleases + state.devReleases).none { it.tagName == state.inUseTag }

/**
 * Bind an asynchronous download to the exact source and release that the user chose.
 * The generation flag also rejects results from an earlier selection or source reload.
 */
internal fun canApplySourceDownload(
    state: PatchesUiState,
    release: Release,
    requestedSourceId: String?,
    activeSourceId: String?,
    sameGeneration: Boolean,
): Boolean =
    sameGeneration &&
        state.sourceId == requestedSourceId &&
        activeSourceId == requestedSourceId &&
        state.selectedRelease?.tagName == release.tagName

/** Search always includes BOTH stable and experimental (dev) versions, regardless of current tab. */
internal fun visibleSourceReleases(
    state: PatchesUiState,
): List<Release> {
    val query = state.versionQuery.trim()
    val candidates = if (state.isOffline) {
        state.offlineReleases
    } else if (query.isBlank()) {
        state.currentReleases
    } else {
        (state.stableReleases + state.devReleases)
            .distinctBy(Release::tagName)
            .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
    }
    return if (query.isBlank()) candidates else candidates.filter {
        it.tagName.contains(query, ignoreCase = true) ||
            it.name.orEmpty().contains(query, ignoreCase = true)
    }
}

internal fun sourceReleaseIsInUse(state: PatchesUiState, release: Release): Boolean =
    !state.isOffline && state.sourceId != null && state.inUseTag == release.tagName

/** Which channel an explicitly selected release belongs to. */
internal fun releaseChannel(release: Release): ReleaseChannel =
    if (release.isDevRelease()) ReleaseChannel.DEV else ReleaseChannel.STABLE
