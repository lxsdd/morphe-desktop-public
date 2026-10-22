/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.AppSortPreference
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.UnifiedHomeAppStateIndex
import app.morphe.gui.util.DeviceAppDiscoverySnapshot
import app.morphe.gui.util.DiscoveredDeviceApp

@Composable
internal fun HomeSplitLayout(
    uiState: HomeUiState,
    deviceAppInfo: Map<String, DeviceAppInfo>,
    unifiedAppStates: UnifiedHomeAppStateIndex,
    padding: Dp,
    sourceNamesByPackage: Map<String, List<String>>,
    patchSourcesForSelectedApk: List<String>,
    patchesLoaded: Boolean,
    onSortPreferenceChange: (String, AppSortPreference) -> Unit,
    onShowDetail: (PatchedAppRecord) -> Unit,
    onInstallPatchedApp: (String) -> Unit,
    onFilterChange: (AppListFilter) -> Unit,
    deviceDiscovery: DeviceAppDiscoverySnapshot?,
    deviceDisplayName: String?,
    onRefreshDeviceApps: () -> Unit,
    onVisibleDevicePackages: (String, List<String>) -> Unit,
    onImportDeviceApp: (DiscoveredDeviceApp) -> Unit,
    onRetry: () -> Unit,
    onManageSources: () -> Unit,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            // Small cute padding for small cute space
            // between the HeaderBar's bottom
            // divider and the actual body section.
            .padding(
                start = 10.dp,
                end = padding,
                top = 4.dp,
                bottom = padding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bodyViewport = this.maxHeight
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(padding),
            verticalAlignment = Alignment.Top,
        ) {
            // Left: browse/discover supported apps (wizard step 1).
            SupportedAppsListPane(
                supportedApps = uiState.supportedApps,
                patchedStates = uiState.patchedStates,
                patchedRecords = uiState.patchedRecords,
                deviceAppInfo = deviceAppInfo,
                updateInfoByPackage = uiState.updateInfoByPackage,
                unifiedAppStates = unifiedAppStates,
                sortPreferences = uiState.appSortPreferences,
                onSortPreferenceChange = onSortPreferenceChange,
                onShowDetail = onShowDetail,
                onInstallPatchedApp = onInstallPatchedApp,
                filter = uiState.appListFilter,
                onFilterChange = onFilterChange,
                deviceDiscovery = deviceDiscovery,
                deviceDisplayName = deviceDisplayName,
                onRefreshDeviceApps = onRefreshDeviceApps,
                onVisibleDevicePackages = onVisibleDevicePackages,
                onImportDeviceApp = onImportDeviceApp,
                importingDevicePackage = uiState.importingDevicePackage,
                deviceImportStatus = uiState.deviceImportStatus,
                sourceNamesByPackage = sourceNamesByPackage,
                isLoading = uiState.isLoadingPatches,
                loadError = uiState.patchLoadError,
                onRetry = onRetry,
                onManageSources = onManageSources,
                modifier = Modifier
                    .weight(1.2f)
                    .heightIn(max = bodyViewport),
            )
            // Right: APK info / drop zone (wizard step 2, pick the
            // APK you want patched). Content centers vertically when
            // it fits, scrolls when it doesn't, so the CONTINUE
            // button is never clipped off the bottom.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .heightIn(max = bodyViewport)
                    .padding(top = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiddleContent(
                    uiState = uiState,
                    patchesLoaded = patchesLoaded,
                    onClearClick = onClearClick,
                    onChangeClick = onChangeClick,
                    onContinueClick = onContinueClick,
                    patchSourceNames = patchSourcesForSelectedApk,
                )
            }
        }
    }
}
