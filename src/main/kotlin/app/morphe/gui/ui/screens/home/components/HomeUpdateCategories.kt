/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.util.UpdateMetadataResolution

/**
 * Separate application-version changes from patch-source changes.
 * Unknown or merely newer (but irrelevant) patch-source releases are NOT
 * silently classified as confirmed per-app patch updates.
 */
internal fun hasConfirmedAppUpdate(info: RecallUpdateInfo?): Boolean =
    info != null && info.metadataResolution == UpdateMetadataResolution.READY &&
        info.appOutdated && !info.appSuggestedVersion.isNullOrBlank()

internal fun hasConfirmedPatchUpdate(info: RecallUpdateInfo?): Boolean =
    info?.metadataResolution == UpdateMetadataResolution.READY &&
        info.sources.any { it.hasRelevantChanges }

internal fun visiblePatchedRecords(
    filter: AppListFilter,
    records: List<PatchedAppRecord>,
    info: Map<String, RecallUpdateInfo>,
): List<PatchedAppRecord> = when (filter) {
    AppListFilter.APP_UPDATES -> records.filter { hasConfirmedAppUpdate(info[it.packageName]) }
    AppListFilter.PATCH_UPDATES -> records.filter { hasConfirmedPatchUpdate(info[it.packageName]) }
    else -> records
}

/** Only a successfully captured device inventory may display a numeric count. */
internal fun verifiedDeviceInventoryCount(installedCount: Int, inventorySerial: String?): Int? =
    installedCount.takeIf { inventorySerial != null }

internal fun deviceInventoryCountText(verifiedCount: Int?): String =
    verifiedCount?.toString() ?: "?"
