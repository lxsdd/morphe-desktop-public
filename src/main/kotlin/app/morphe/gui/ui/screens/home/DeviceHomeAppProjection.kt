/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import app.morphe.gui.util.DevicePatchability

enum class DevicePatchInputStatus {
    PATCHABLE,
    VERSION_NOT_CONFIRMED,
    INCOMPATIBLE_VERSION,
    NO_PATCH_SOURCE,
}

enum class DeviceMorpheActionStatus {
    INSTALLATION_REQUIRED,
    EXACT_BUILD_INSTALLED,
    REPATCH_REQUIRED,
    NEW_APK_REQUIRED,
    NEW_APK_AND_REPATCH_REQUIRED,
    PATCHABLE_INSTALLED_APP,
    CURRENT,
    NO_PATCH_SOURCE,
    UNKNOWN,
}

data class DeviceHomeAppProjection(
    val patchInputStatus: DevicePatchInputStatus,
    val actionStatus: DeviceMorpheActionStatus,
    val showInstallAction: Boolean,
    val showUseInstalledAppAction: Boolean,
)

fun projectDeviceHomeApp(
    patchability: DevicePatchability,
    state: UnifiedHomeAppState?,
): DeviceHomeAppProjection {
    val inputStatus = when (patchability) {
        DevicePatchability.PATCHABLE -> DevicePatchInputStatus.PATCHABLE
        DevicePatchability.VERSION_NOT_CONFIRMED -> DevicePatchInputStatus.VERSION_NOT_CONFIRMED
        DevicePatchability.INCOMPATIBLE_VERSION -> DevicePatchInputStatus.INCOMPATIBLE_VERSION
        DevicePatchability.NO_PATCH_SOURCE -> DevicePatchInputStatus.NO_PATCH_SOURCE
    }
    val actionStatus = when (state?.primaryAction) {
        HomePrimaryAction.INSTALL_LOCAL_BUILD -> DeviceMorpheActionStatus.INSTALLATION_REQUIRED
        HomePrimaryAction.REPATCH_CURRENT_INPUT -> DeviceMorpheActionStatus.REPATCH_REQUIRED
        HomePrimaryAction.NEW_APK_REQUIRED -> DeviceMorpheActionStatus.NEW_APK_REQUIRED
        HomePrimaryAction.NEW_APK_AND_REPATCH_REQUIRED -> DeviceMorpheActionStatus.NEW_APK_AND_REPATCH_REQUIRED
        HomePrimaryAction.PATCH_CURRENT_DEVICE_APP -> DeviceMorpheActionStatus.PATCHABLE_INSTALLED_APP
        HomePrimaryAction.NONE -> when {
            state.localResult.state == HomeLocalResultState.NO_LOCAL_BUILD &&
                patchability == DevicePatchability.NO_PATCH_SOURCE -> DeviceMorpheActionStatus.NO_PATCH_SOURCE
            state.localResult.state == HomeLocalResultState.NO_LOCAL_BUILD &&
                patchability != DevicePatchability.PATCHABLE -> DeviceMorpheActionStatus.UNKNOWN
            state.selectedDevice.state == HomeSelectedDeviceState.EXACT_LOCAL_BUILD_INSTALLED ->
                DeviceMorpheActionStatus.EXACT_BUILD_INSTALLED
            state.selectedDevice.state == HomeSelectedDeviceState.INSTALLED_STATE_UNKNOWN ->
                DeviceMorpheActionStatus.UNKNOWN
            else -> DeviceMorpheActionStatus.CURRENT
        }
        null -> when (patchability) {
            DevicePatchability.PATCHABLE -> DeviceMorpheActionStatus.PATCHABLE_INSTALLED_APP
            DevicePatchability.NO_PATCH_SOURCE -> DeviceMorpheActionStatus.NO_PATCH_SOURCE
            else -> DeviceMorpheActionStatus.UNKNOWN
        }
    }
    return DeviceHomeAppProjection(
        patchInputStatus = inputStatus,
        actionStatus = actionStatus,
        showInstallAction = state?.primaryAction == HomePrimaryAction.INSTALL_LOCAL_BUILD,
        showUseInstalledAppAction = state?.primaryAction == HomePrimaryAction.PATCH_CURRENT_DEVICE_APP ||
            (state == null && patchability == DevicePatchability.PATCHABLE),
    )
}
