package app.morphe.gui.ui.screens.home.components

/** An assessment is not evidence of an exact installed APK SHA-256 match. */
internal enum class DeviceOverviewStatus {
    APP_UPDATE, PATCH_UPDATE, LOCAL_BUILD_REVIEW, PATCH_CANDIDATE,
    VERSION_MATCH_UNVERIFIED, EXACT_BUILD_VERIFIED, SIGNER_CONFLICT, REVIEW_REQUIRED, OUTSIDE_PATCH_CATALOG
}

internal data class DeviceOverviewEvidence(
    val patchSupported: Boolean,
    val hasLocalPatchHistory: Boolean,
    val installedVersion: String?,
    val locallyPatchedVersion: String?,
    val signerMatchesMorphe: Boolean?,
    val newerCompatibleAppVersion: Boolean,
    val relevantPatchChange: Boolean,
    val localBuildVersionNewer: Boolean,
    /** Verified SHA of the installed single APK equals the recorded, freshly hashed local output. */
    val exactBuildVerified: Boolean? = null,
)

/** Read-only classification; no status claims exact APK identity without a hash proof. */
internal fun classifyDeviceOverview(e: DeviceOverviewEvidence): DeviceOverviewStatus = when {
    !e.patchSupported && !e.hasLocalPatchHistory -> DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG
    e.hasLocalPatchHistory && e.signerMatchesMorphe == false -> DeviceOverviewStatus.SIGNER_CONFLICT
    e.hasLocalPatchHistory && e.installedVersion.isNullOrBlank() -> DeviceOverviewStatus.REVIEW_REQUIRED
    e.newerCompatibleAppVersion -> DeviceOverviewStatus.APP_UPDATE
    // A local patch snapshot is only the installed patch state after APK bytes match.
    e.hasLocalPatchHistory && e.relevantPatchChange && e.exactBuildVerified == true ->
        DeviceOverviewStatus.PATCH_UPDATE
    e.hasLocalPatchHistory && e.localBuildVersionNewer -> DeviceOverviewStatus.LOCAL_BUILD_REVIEW
    // A Morphe-signed device build without local history could have been patched
    // elsewhere; do not silently classify it as an unpatched candidate.
    !e.hasLocalPatchHistory && e.patchSupported && e.signerMatchesMorphe == true ->
        DeviceOverviewStatus.REVIEW_REQUIRED
    !e.hasLocalPatchHistory && e.patchSupported -> DeviceOverviewStatus.PATCH_CANDIDATE
    e.hasLocalPatchHistory && e.exactBuildVerified == true &&
        e.signerMatchesMorphe == true && e.installedVersion != null &&
        e.installedVersion == e.locallyPatchedVersion -> DeviceOverviewStatus.EXACT_BUILD_VERIFIED
    e.hasLocalPatchHistory && e.installedVersion != null &&
        e.installedVersion == e.locallyPatchedVersion &&
        e.signerMatchesMorphe == true -> DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED
    else -> DeviceOverviewStatus.REVIEW_REQUIRED
}

internal fun deviceOverviewPriority(status: DeviceOverviewStatus): Int = when (status) {
    DeviceOverviewStatus.SIGNER_CONFLICT -> 0
    DeviceOverviewStatus.APP_UPDATE -> 1
    DeviceOverviewStatus.PATCH_UPDATE -> 2
    DeviceOverviewStatus.LOCAL_BUILD_REVIEW -> 3
    DeviceOverviewStatus.PATCH_CANDIDATE -> 4
    DeviceOverviewStatus.REVIEW_REQUIRED -> 5
    DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED -> 6
    DeviceOverviewStatus.EXACT_BUILD_VERIFIED -> 7
    DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG -> 8
}
