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
import app.morphe.gui.ui.screens.home.HomeUiState

@Composable
internal fun HomeSplitLayout(
    uiState: HomeUiState,
    padding: Dp,
    sourceNamesByPackage: Map<String, List<String>>,
    patchSourcesForSelectedApk: List<String>,
    patchesLoaded: Boolean,
    onSortModeChange: (HomeAppSortMode) -> Unit,
    onShowDetail: (PatchedAppRecord) -> Unit,
    onShowDeviceApp: (String) -> Unit,
    onFilterChange: (AppListFilter) -> Unit,
    onRetry: () -> Unit,
    onRefreshDeviceInfo: () -> Unit,
    onManageSources: () -> Unit,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(
                start = 10.dp,
                end = padding,
                top = 4.dp,
                bottom = padding,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        val bodyViewport = this.maxHeight
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(padding),
            verticalAlignment = Alignment.Top,
        ) {
            SupportedAppsListPane(
                supportedApps = uiState.supportedApps,
                patchedStates = uiState.patchedStates,
                patchedRecords = uiState.patchedRecords,
                deviceAppInfo = uiState.deviceAppInfo,
                deviceInstalledPackages = uiState.deviceInstalledPackages,
                deviceInventorySerial = uiState.deviceInventorySerial,
                deviceRefreshSerial = uiState.deviceRefreshSerial,
                deviceRefreshStage = uiState.deviceRefreshStage,
                updateInfoByPackage = uiState.updateInfoByPackage,
                sortMode = uiState.sortMode,
                onSortModeChange = onSortModeChange,
                onShowDetail = onShowDetail,
                onShowDeviceApp = onShowDeviceApp,
                filter = uiState.appListFilter,
                onFilterChange = onFilterChange,
                sourceNamesByPackage = sourceNamesByPackage,
                isLoading = uiState.isLoadingPatches,
                loadError = uiState.patchLoadError,
                onRetry = onRetry,
                onRefreshDeviceInfo = onRefreshDeviceInfo,
                onManageSources = onManageSources,
                modifier = (if (uiState.appListFilter == AppListFilter.DEVICE) {
                    // Device inventory is read-only: use the full workspace instead
                    // of leaving an unrelated APK drop target on the right.
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.weight(1.2f)
                }).heightIn(max = bodyViewport),
            )
            if (uiState.appListFilter != AppListFilter.DEVICE) Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.Top)
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
