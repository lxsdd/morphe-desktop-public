/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.util.isNewerVersion
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.screens.home.VerifiedUpdateFact
import app.morphe.gui.ui.screens.home.verifiedAppUpdates
import app.morphe.gui.util.selectSupportedAppTarget

/** The installed package and version, never the old source APK's version, drive device offers. */
internal fun latestCompatibleDeviceAppTarget(
    app: SupportedApp?,
    installedVersion: String?,
): String? {
    if (app == null || installedVersion.isNullOrBlank()) return null
    val candidate = selectSupportedAppTarget(
        app.recommendedVersion, app.experimentalVersions, installedVersion,
    ).version ?: return null
    // An arbitrary newer app-store version is not a Morphe-supported target.
    return candidate.takeIf { selected ->
        (app.supportedVersions + app.experimentalVersions).any {
            it.removePrefix("v").equals(selected.removePrefix("v"), ignoreCase = true)
        }
    }
}

/** Used without alteration by device counters, filters, status badge and update action. */
internal fun verifiedDeviceUpdateFacts(
    installedPackageName: String,
    installedVersion: String?,
    targetVersion: String?,
    history: PatchedAppRecord?,
    historicalUpdates: RecallUpdateInfo?,
    exactBuildVerified: Boolean?,
): DeviceUpdateFacts {
    val appVersionUpdate = !installedVersion.isNullOrBlank() &&
        !targetVersion.isNullOrBlank() && isNewerVersion(targetVersion, installedVersion)
    // A saved patch recipe is not proof that this same recipe is on the device.
    // The exact signed APK hash, installed package identity and version must all match.
    val currentHistoryInstalled = history != null &&
        history.installedPackageName == installedPackageName &&
        !installedVersion.isNullOrBlank() &&
        installedVersion.removePrefix("v").equals(history.apkVersion.removePrefix("v"), true) &&
        history.apkVersion.removePrefix("v").equals(
            historicalUpdates?.appUsedVersion?.removePrefix("v"), true,
        ) &&
        exactBuildVerified == true
    val installedPatchUpdate = currentHistoryInstalled &&
        verifiedAppUpdates(historicalUpdates).patch == VerifiedUpdateFact.AVAILABLE
    return DeviceUpdateFacts(appVersionUpdate, installedPatchUpdate)
}

/** Human-facing state is independent from signing-key and hash diagnostics. */
internal enum class DeviceReadableUpdateStatus {
    APP_AND_PATCH, APP, PATCH, CURRENT, CHECKING, UNSUPPORTED, NONE,
}

internal fun deviceReadableUpdateStatus(
    facts: DeviceUpdateFacts,
    diagnostic: DeviceOverviewStatus,
    historicalUpdates: RecallUpdateInfo?,
    history: PatchedAppRecord?,
    installedVersion: String?,
    exactBuildVerified: Boolean?,
): DeviceReadableUpdateStatus = when {
    facts.confirmedAppVersionUpdate && facts.confirmedPatchUpdate -> DeviceReadableUpdateStatus.APP_AND_PATCH
    facts.confirmedAppVersionUpdate -> DeviceReadableUpdateStatus.APP
    facts.confirmedPatchUpdate -> DeviceReadableUpdateStatus.PATCH
    diagnostic == DeviceOverviewStatus.PATCH_VERSION_INCOMPATIBLE -> DeviceReadableUpdateStatus.UNSUPPORTED
    history == null -> DeviceReadableUpdateStatus.NONE
    exactBuildVerified == true && !installedVersion.isNullOrBlank() &&
        installedVersion.removePrefix("v").equals(history.apkVersion.removePrefix("v"), true) &&
        history.apkVersion.removePrefix("v").equals(
            historicalUpdates?.appUsedVersion?.removePrefix("v"), true,
        ) &&
        verifiedAppUpdates(historicalUpdates).app == VerifiedUpdateFact.CURRENT &&
        verifiedAppUpdates(historicalUpdates).patch == VerifiedUpdateFact.CURRENT ->
        DeviceReadableUpdateStatus.CURRENT
    else -> DeviceReadableUpdateStatus.CHECKING
}
