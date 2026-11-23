/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.screens.home.HistoricalSourceIssue
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.verifiedAppUpdates

/**
 * Separate application-version changes from patch-source changes.
 * Unknown or merely newer (but irrelevant) patch-source releases are NOT
 * silently classified as confirmed per-app patch updates.
 */
internal fun hasConfirmedAppUpdate(info: RecallUpdateInfo?): Boolean =
    verifiedAppUpdates(info).hasConfirmedAppUpdate

/** Experimental offers remain separately marked and never imply stable eligibility. */
internal fun isExperimentalAppUpdate(info: RecallUpdateInfo?): Boolean =
    hasConfirmedAppUpdate(info) && info?.appChannel == RecallUpdateInfo.AppChannel.EXPERIMENTAL

/** The cached per-app status already contains confirmed scoped changelog changes.
 * Count that evidence even if a subsequent async catalogue refresh has no metadata.
 */
/** Unknown check state is never equivalent to zero available updates. */
private fun updateCheckIncomplete(info: RecallUpdateInfo?): Boolean =
    verifiedAppUpdates(info).checkIncomplete

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

/**
 * Historical PATCHED_WITH_UPDATES is a cached hint, not a current scoped proof.
 * Do not let it make the top tabs, card badge and device counters disagree.
 * Kept as an optional parameter for existing callers, but it cannot override
 * missing or unverified source metadata.
 */
@Suppress("UNUSED_PARAMETER")
internal fun hasConfirmedPatchUpdate(
    info: RecallUpdateInfo?,
    state: PatchedAppState? = null,
): Boolean = verifiedAppUpdates(info).hasConfirmedPatchUpdate

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

/** Explain why a saved record cannot yet be declared current, without inventing an update. */
internal enum class IncompleteUpdateReason {
    METADATA_UNAVAILABLE, NO_SOURCE_PROOF, SOURCE_UNAVAILABLE, CHANGE_PROOF_UNAVAILABLE,
}

internal data class IncompleteAppCheck(
    val appName: String,
    val sourceName: String?,
    val reason: IncompleteUpdateReason,
    /** Specific proof loss from the real configured historical source, when available. */
    val sourceIssue: HistoricalSourceIssue? = null,
)

internal fun incompleteAppChecks(
    records: List<PatchedAppRecord>,
    infoByPackage: Map<String, RecallUpdateInfo>,
    displayNames: Map<String, String> = emptyMap(),
): List<IncompleteAppCheck> = records.asSequence().mapNotNull { record ->
    val info = infoByPackage[record.packageName]
    if (!verifiedAppUpdates(info).checkIncomplete) return@mapNotNull null
    val unresolved = info?.sources?.firstOrNull { it.checkIncomplete || !it.metadataKnown }
    val reason = when {
        info == null || info.metadataResolution != app.morphe.gui.util.UpdateMetadataResolution.READY ->
            IncompleteUpdateReason.METADATA_UNAVAILABLE
        info.sources.isEmpty() -> IncompleteUpdateReason.NO_SOURCE_PROOF
        unresolved == null -> IncompleteUpdateReason.NO_SOURCE_PROOF
        unresolved.resolvedVersion.isNullOrBlank() && unresolved.latestAvailableVersion.isNullOrBlank() ->
            IncompleteUpdateReason.SOURCE_UNAVAILABLE
        else -> IncompleteUpdateReason.CHANGE_PROOF_UNAVAILABLE
    }
    IncompleteAppCheck(
        appName = displayNames[record.packageName]?.takeIf(String::isNotBlank) ?: record.displayName,
        sourceName = unresolved?.name,
        reason = reason,
        sourceIssue = unresolved?.sourceIssue,
    )
}.distinctBy { it.appName }.sortedBy { it.appName.lowercase() }.toList()

/** Source-management access is offered only for actual unresolved provenance/access. */
internal fun shouldOfferSourceManagement(entries: List<IncompleteAppCheck>): Boolean =
    entries.any { it.sourceIssue in setOf(
        HistoricalSourceIssue.SOURCE_UNMATCHED,
        HistoricalSourceIssue.SOURCE_DISABLED,
        HistoricalSourceIssue.SOURCE_NOT_ACCESSIBLE,
        HistoricalSourceIssue.SOURCE_POLICY_BLOCKED,
        HistoricalSourceIssue.SOURCE_ACCESS_RESTRICTED,
    ) }

/** Only a successfully captured device inventory may display a numeric count. */
internal fun verifiedDeviceInventoryCount(installedCount: Int, inventorySerial: String?): Int? =
    installedCount.takeIf { inventorySerial != null }

internal fun deviceInventoryCountText(verifiedCount: Int?): String =
    verifiedCount?.toString() ?: "?"
