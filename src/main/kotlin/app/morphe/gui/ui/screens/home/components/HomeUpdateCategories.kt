/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.util.UpdateMetadataResolution

/**
 * Separate application-version changes from patch-source changes.
 * Unknown or merely newer (but irrelevant) patch-source releases are NOT
 * silently classified as confirmed per-app patch updates.
 */
internal fun hasConfirmedAppUpdate(info: RecallUpdateInfo?): Boolean =
    info != null && info.metadataResolution == UpdateMetadataResolution.READY &&
        info.appOutdated && !info.appSuggestedVersion.isNullOrBlank()

/** Experimental offers remain separately marked and never imply stable eligibility. */
internal fun isExperimentalAppUpdate(info: RecallUpdateInfo?): Boolean =
    hasConfirmedAppUpdate(info) && info?.appChannel == RecallUpdateInfo.AppChannel.EXPERIMENTAL

/** The cached per-app status already contains confirmed scoped changelog changes.
 * Count that evidence even if a subsequent async catalogue refresh has no metadata.
 */
/** Unknown check state is never equivalent to zero available updates. */
private fun updateCheckIncomplete(info: RecallUpdateInfo?): Boolean =
    info == null ||
        info.metadataResolution != UpdateMetadataResolution.READY ||
        info.appSuggestedVersion.isNullOrBlank() ||
        info.patchCheckIncomplete

internal fun incompleteUpdateChecks(
    records: List<PatchedAppRecord>,
    infoByPackage: Map<String, RecallUpdateInfo>,
): Int = records.count { updateCheckIncomplete(infoByPackage[it.packageName]) }

/** Provenance names, not an invented claim that unchecked apps have zero updates. */
internal fun incompleteUpdateRecordNames(
    records: List<PatchedAppRecord>,
    infoByPackage: Map<String, RecallUpdateInfo>,
    displayNames: Map<String, String> = emptyMap(),
): List<String> = records.asSequence()
    .filter { updateCheckIncomplete(infoByPackage[it.packageName]) }
    .map { displayNames[it.packageName]?.takeIf(String::isNotBlank) ?: it.displayName }
    .distinct()
    .sortedWith(String.CASE_INSENSITIVE_ORDER)
    .toList()

internal fun hasConfirmedPatchUpdate(
    info: RecallUpdateInfo?,
    state: PatchedAppState? = null,
): Boolean =
    state == PatchedAppState.PATCHED_WITH_UPDATES ||
        (info?.metadataResolution == UpdateMetadataResolution.READY &&
            info.sources.any { it.hasRelevantChanges })

internal fun visiblePatchedRecords(
    filter: AppListFilter,
    records: List<PatchedAppRecord>,
    info: Map<String, RecallUpdateInfo>,
    states: Map<String, PatchedAppState> = emptyMap(),
): List<PatchedAppRecord> = when (filter) {
    AppListFilter.APP_UPDATES -> records.filter { hasConfirmedAppUpdate(info[it.packageName]) }
    AppListFilter.PATCH_UPDATES -> records.filter {
        hasConfirmedPatchUpdate(info[it.packageName], states[it.packageName])
    }
    else -> records
}

/** Only a successfully captured device inventory may display a numeric count. */
internal fun verifiedDeviceInventoryCount(installedCount: Int, inventorySerial: String?): Int? =
    installedCount.takeIf { inventorySerial != null }

internal fun deviceInventoryCountText(verifiedCount: Int?): String =
    verifiedCount?.toString() ?: "?"
