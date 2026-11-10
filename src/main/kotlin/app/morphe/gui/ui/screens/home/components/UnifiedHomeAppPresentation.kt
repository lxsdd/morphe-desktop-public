/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.runtime.Composable
import app.morphe.gui.ui.screens.home.HomeAppVersionChannel
import app.morphe.gui.ui.screens.home.DeviceMorpheActionStatus
import app.morphe.gui.ui.screens.home.HomePatchBundleChannel
import app.morphe.gui.ui.screens.home.HomePatchBundleStatus
import app.morphe.gui.ui.screens.home.HomePrimaryAction
import app.morphe.gui.ui.screens.home.HomeSelectedDeviceState
import app.morphe.gui.ui.screens.home.UnifiedHomeAppState
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun unifiedPrimaryActionLabel(action: HomePrimaryAction): String = when (action) {
    HomePrimaryAction.NONE -> stringResource(Res.string.home_state_current)
    HomePrimaryAction.INSTALL_LOCAL_BUILD -> stringResource(Res.string.home_action_install_to_device)
    HomePrimaryAction.REPATCH_CURRENT_INPUT -> stringResource(Res.string.home_action_repatch)
    HomePrimaryAction.NEW_APK_REQUIRED -> stringResource(Res.string.home_action_get_newer_apk)
    HomePrimaryAction.NEW_APK_AND_REPATCH_REQUIRED ->
        stringResource(Res.string.home_action_get_newer_apk_and_patch)
    HomePrimaryAction.PATCH_CURRENT_DEVICE_APP -> stringResource(Res.string.home_action_patch_installed_app)
}

@Composable
internal fun unifiedActionStatusLabel(state: UnifiedHomeAppState): String = when (state.primaryAction) {
    HomePrimaryAction.INSTALL_LOCAL_BUILD -> stringResource(Res.string.home_state_installation_required)
    HomePrimaryAction.REPATCH_CURRENT_INPUT -> stringResource(Res.string.home_state_repatch_required)
    HomePrimaryAction.NEW_APK_REQUIRED -> stringResource(Res.string.home_state_new_apk_required)
    HomePrimaryAction.NEW_APK_AND_REPATCH_REQUIRED ->
        stringResource(Res.string.home_state_new_apk_and_repatch_required)
    HomePrimaryAction.PATCH_CURRENT_DEVICE_APP -> unifiedPrimaryActionLabel(state.primaryAction)
    HomePrimaryAction.NONE -> when (state.selectedDevice.state) {
        HomeSelectedDeviceState.EXACT_LOCAL_BUILD_INSTALLED ->
            stringResource(Res.string.home_state_exact_build_installed)
        HomeSelectedDeviceState.INSTALLED_STATE_UNKNOWN -> stringResource(Res.string.home_state_unknown)
        else -> stringResource(Res.string.home_state_current)
    }
}

@Composable
internal fun unifiedAppChannelStatusLabel(state: UnifiedHomeAppState): String = when {
    state.appVersion.newApkRequired -> stringResource(Res.string.home_state_new_apk_required)
    state.appVersion.selectedChannelUpdateAvailable &&
        state.appVersion.selectedChannel == HomeAppVersionChannel.STABLE ->
        stringResource(Res.string.home_state_new_stable_app)
    state.appVersion.selectedChannelUpdateAvailable &&
        state.appVersion.selectedChannel == HomeAppVersionChannel.EXPERIMENTAL ->
        stringResource(Res.string.home_state_new_experimental_app)
    state.appVersion.selectedChannel == HomeAppVersionChannel.STABLE &&
        state.appVersion.experimentalOfferAvailable ->
        stringResource(Res.string.home_state_stable_current_optional_experimental)
    state.appVersion.selectedChannel == HomeAppVersionChannel.EXPERIMENTAL &&
        state.appVersion.stableOfferAvailable ->
        stringResource(Res.string.home_state_experimental_current_optional_stable)
    state.appVersion.selectedChannel == HomeAppVersionChannel.STABLE ->
        stringResource(Res.string.home_state_stable_current)
    state.appVersion.selectedChannel == HomeAppVersionChannel.EXPERIMENTAL ->
        stringResource(Res.string.home_state_experimental_current)
    else -> stringResource(Res.string.home_state_unknown)
}

@Composable
internal fun unifiedPatchChannelStatusLabel(state: UnifiedHomeAppState): String = when (state.patchBundle.status) {
    HomePatchBundleStatus.RELEVANT_STABLE_PATCH_AVAILABLE ->
        stringResource(Res.string.home_state_new_stable_patch)
    HomePatchBundleStatus.RELEVANT_DEV_PATCH_AVAILABLE ->
        stringResource(Res.string.home_state_new_dev_patch)
    HomePatchBundleStatus.RELEVANT_STABLE_AND_DEV_PATCH_AVAILABLE ->
        stringResource(Res.string.home_state_new_mixed_patch)
    HomePatchBundleStatus.RELEVANT_LOCAL_PATCH_AVAILABLE ->
        stringResource(Res.string.home_state_new_local_patch)
    HomePatchBundleStatus.NEWER_RELEASE_BUT_NO_RELEVANT_APP_CHANGE ->
        stringResource(Res.string.home_state_irrelevant_patch_release)
    HomePatchBundleStatus.CURRENT -> {
        val channels = state.patchBundle.sources.map { it.channel }.toSet()
        when {
            channels.size == 1 && HomePatchBundleChannel.STABLE in channels ->
                stringResource(Res.string.home_state_stable_patch_current)
            channels.size == 1 && HomePatchBundleChannel.DEV in channels ->
                stringResource(Res.string.home_state_dev_patch_current)
            channels.size == 1 && HomePatchBundleChannel.LOCAL in channels ->
                stringResource(Res.string.home_state_local_patch_current)
            else -> stringResource(Res.string.home_state_patch_current)
        }
    }
    HomePatchBundleStatus.UNKNOWN -> stringResource(Res.string.home_state_unknown)
}

@Composable
internal fun deviceActionStatusLabel(status: DeviceMorpheActionStatus): String = when (status) {
    DeviceMorpheActionStatus.INSTALLATION_REQUIRED -> stringResource(Res.string.home_state_installation_required)
    DeviceMorpheActionStatus.EXACT_BUILD_INSTALLED -> stringResource(Res.string.home_state_exact_build_installed)
    DeviceMorpheActionStatus.REPATCH_REQUIRED -> stringResource(Res.string.home_state_repatch_required)
    DeviceMorpheActionStatus.NEW_APK_REQUIRED -> stringResource(Res.string.home_state_new_apk_required)
    DeviceMorpheActionStatus.NEW_APK_AND_REPATCH_REQUIRED -> stringResource(Res.string.home_state_new_apk_and_repatch_required)
    DeviceMorpheActionStatus.PATCHABLE_INSTALLED_APP -> stringResource(Res.string.home_state_patchable_installed_app)
    DeviceMorpheActionStatus.CURRENT -> stringResource(Res.string.home_state_current)
    DeviceMorpheActionStatus.NO_PATCH_SOURCE -> stringResource(Res.string.home_state_no_patch_source)
    DeviceMorpheActionStatus.UNKNOWN -> stringResource(Res.string.home_state_unknown)
}
