/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.model.AppSortPreference
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.TooltipText
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.screens.home.UnifiedHomeAppState
import app.morphe.gui.ui.screens.home.UnifiedHomeAppStateIndex
import app.morphe.gui.ui.screens.home.projectDeviceHomeApp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.DeviceAppDiscoverySnapshot
import app.morphe.gui.util.DevicePatchability
import app.morphe.gui.util.DevicePatchSourceAvailability
import app.morphe.gui.util.DiscoveredDeviceApp
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun installedVersionLabel(versionName: String?, versionCode: Long?): String = when {
    versionName != null && versionCode != null -> "Installed: $versionName ($versionCode)"
    versionName != null -> "Installed: $versionName"
    versionCode != null -> "Installed version code: $versionCode"
    else -> "Installed: unknown"
}

// ============================================================================
// SUPPORTED APPS LIST PANE
// ============================================================================

/**
 * Vertical-list variant of the supported-apps display used in the side-by-side
 * layout. Search field at top, scrollable LazyColumn of [SupportedAppListRow]
 * below. Single-expand semantics. Clicking a row expands it and collapses any
 * previously-expanded one.
 */
@Composable
internal fun SupportedAppsListPane(
    supportedApps: List<SupportedApp>,
    patchedStates: Map<String, PatchedAppState> = emptyMap(),
    patchedRecords: List<PatchedAppRecord> = emptyList(),
    deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
    updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
    unifiedAppStates: UnifiedHomeAppStateIndex = UnifiedHomeAppStateIndex(emptyMap(), emptyMap()),
    onShowDetail: (PatchedAppRecord) -> Unit = {},
    onInstallPatchedApp: (String) -> Unit = {},
    filter: AppListFilter = AppListFilter.ALL,
    onFilterChange: (AppListFilter) -> Unit = {},
    deviceDiscovery: DeviceAppDiscoverySnapshot? = null,
    deviceDisplayName: String? = null,
    onRefreshDeviceApps: () -> Unit = {},
    onVisibleDevicePackages: (String, List<String>) -> Unit = { _, _ -> },
    onImportDeviceApp: (DiscoveredDeviceApp) -> Unit = {},
    importingDevicePackage: String? = null,
    deviceImportStatus: String? = null,
    sourceNamesByPackage: Map<String, List<String>>,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onManageSources: () -> Unit = {},
    sortPreferences: Map<String, AppSortPreference> = emptyMap(),
    onSortPreferenceChange: (String, AppSortPreference) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }
    var yourAppsFilter by remember { mutableStateOf(YourAppsFilter.ALL) }
    var deviceAppsFilter by remember { mutableStateOf(DeviceAppsFilter.KNOWN) }
    val sortState = resolveSortState(filter, sortPreferences[filter.name])
    val onSortChange: (AppSortState) -> Unit = { updated ->
        onSortPreferenceChange(filter.name, updated.toPreference())
    }

    val filtered = sortSupportedApps(if (searchQuery.isBlank()) supportedApps else supportedApps.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }, sortState)
    val statesByPackage = unifiedAppStates.byOriginalPackage
    val yourAppStates = remember(statesByPackage, patchedRecords) {
        patchedRecords.mapNotNull { record ->
            statesByPackage[record.packageName]?.let { record.packageName to it }
        }.toMap()
    }
    val filteredRecords = sortPatchedApps(
        apps = filterPatchedApps(patchedRecords, yourAppsFilter, yourAppStates, searchQuery),
        state = sortState,
        unifiedStates = yourAppStates,
    )
    val allDeviceApps = deviceDiscovery?.apps.orEmpty()
    val filteredDeviceApps = sortDeviceApps(filterDeviceApps(allDeviceApps, deviceAppsFilter, searchQuery), sortState)
    val activeCount = when (filter) {
        AppListFilter.YOURS -> patchedRecords.size
        AppListFilter.DEVICE -> allDeviceApps.size
        AppListFilter.ALL -> supportedApps.size
    }

    // Collapse if the currently expanded app filters out.
    LaunchedEffect(searchQuery, filtered) {
        if (expandedPackage != null && filtered.none { it.packageName == expandedPackage }) {
            expandedPackage = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
      val paneMaxHeight = maxHeight
      Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .align(Alignment.TopCenter),
      ) {
        // ── On-open update notice: jumps to "Your apps" where each is badged ──
        val updateCount = yourAppStates.values.count { it.requiresAction }
        if (filter == AppListFilter.ALL && updateCount > 0) {
            PatchedUpdatesBanner(updateCount) {
                searchQuery = ""
                yourAppsFilter = YourAppsFilter.ACTION_REQUIRED
                onFilterChange(AppListFilter.YOURS)
                onSortPreferenceChange(
                    AppListFilter.YOURS.name,
                    AppSortState(AppSortCriterion.ACTION_PRIORITY, AppSortDirection.ASCENDING).toPreference(),
                )
            }
        }

        // ── Filter: ALL APPS · YOUR APPS ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppListFilterChips(
                filter = filter,
                onSelect = onFilterChange,
                allCount = supportedApps.size,
                yourCount = patchedRecords.size,
                deviceCount = deviceAppsTotalCount(allDeviceApps),
                modifier = Modifier.weight(1f),
            )
        }

        // ── Search field ──
        if (activeCount > 4) {
            // Match the LazyColumn's right padding so the field aligns with cards.
            // Dp.Unspecified disables the default 340dp cap so the field fills
            // the pane width like the cards below it.
            Box(modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                SlimSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    font = font,
                    corners = corners,
                    accents = accents,
                    maxWidth = Dp.Unspecified,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (filter == AppListFilter.DEVICE) {
            DeviceAppFilterControls(allDeviceApps, deviceAppsFilter) { deviceAppsFilter = it }
        } else if (filter == AppListFilter.YOURS) {
            YourAppFilterControls(
                unifiedStates = yourAppStates,
                totalCount = patchedRecords.size,
                filter = yourAppsFilter,
                onFilterChange = { yourAppsFilter = it },
            )
        }

        AppSortControls(
            filter = filter,
            state = sortState,
            onStateChange = onSortChange,
            font = font,
            corners = corners,
            accents = accents,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (filter == AppListFilter.DEVICE) {
            DeviceAppsListBody(
                snapshot = deviceDiscovery,
                deviceDisplayName = deviceDisplayName,
                filteredApps = filteredDeviceApps,
                searchQuery = searchQuery,
                onRefresh = onRefreshDeviceApps,
                onVisiblePackages = onVisibleDevicePackages,
                onImport = onImportDeviceApp,
                importingPackage = importingDevicePackage,
                importStatus = deviceImportStatus,
                sourceNamesByPackage = sourceNamesByPackage,
                unifiedAppStates = unifiedAppStates,
                onInstallPatchedApp = onInstallPatchedApp,
                paneMaxHeight = paneMaxHeight,
            )
        } else if (filter == AppListFilter.YOURS) {
            YourAppsListBody(
                patchedRecords = patchedRecords,
                filteredRecords = filteredRecords,
                searchQuery = searchQuery,
                patchedStates = patchedStates,
                deviceAppInfo = deviceAppInfo,
                updateInfoByPackage = updateInfoByPackage,
                unifiedStates = yourAppStates,
                appIconColorByPackage = supportedApps.associate { it.packageName to (it.appIconColor ?: "") }.filterValues { it.isNotEmpty() },
                onShowDetail = onShowDetail,
                paneMaxHeight = paneMaxHeight,
                showSearch = activeCount > 4,
            )
        } else when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(4) { idx ->
                        SkeletonAppRow(
                            corners = corners,
                            // Slight stagger: each row pulses 120ms after the previous
                            // so the skeleton list feels alive instead of lock-step.
                            staggerOffsetMs = idx * 120,
                        )
                    }
                }
            }
            loadError != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.home_list_load_failed),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = loadError,
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(corners.small),
                        ) {
                            Text(
                                text = stringResource(Res.string.retry),
                                fontFamily = font,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        // Always offer a way to the source manager here: when a bundle is
                        // broken (e.g. needs a newer patcher), fixing it means removing or
                        // re-pointing that source, so it must be reachable from the error.
                        OutlinedButton(
                            onClick = onManageSources,
                            shape = RoundedCornerShape(corners.small),
                        ) {
                            Text(
                                text = stringResource(Res.string.home_header_manage_sources_button),
                                fontFamily = font,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) stringResource(Res.string.home_list_no_supported_apps)
                               else stringResource(Res.string.home_list_no_apps_match, searchQuery),
                        fontSize = 13.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()
                // Cap the list at the pane's available height (minus a header
                // + optional search allowance) so it scrolls when there are
                // many apps but wraps tight + lets the Column center when few.
                // Tight estimate: header ~22dp, search field (only shown when
                // >4 apps) ~46dp. Anything over-budgeted leaves dead space
                // above the list when content fills, so be precise.
                val headerSearchAllowance =
                    if (supportedApps.size > 4) 68.dp else 22.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max = (paneMaxHeight - headerSearchAllowance)
                                .coerceAtLeast(120.dp)
                        ),
                ) {
                    LazyColumn(
                        state = listState,
                        // Scrollbar is 6dp wide and sits at the Box's right edge.
                        // 6 (scrollbar width) + 6 (visible gap) = 12dp keeps content
                        // fully clear of the scrollbar with breathing room.
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items = filtered, key = { it.packageName }) { app ->
                            SupportedAppListRow(
                                app = app,
                                isExpanded = expandedPackage == app.packageName,
                                onClick = {
                                    expandedPackage = if (expandedPackage == app.packageName) null
                                                      else app.packageName
                                },
                                patchSourceNames = sourceNamesByPackage[app.packageName] ?: emptyList(),
                                patchedState = patchedStates[app.packageName] ?: PatchedAppState.NEVER_PATCHED,
                                deviceInfo = deviceAppInfo[app.packageName],
                            )
                        }
                    }
                    // Wrap the scrollbar in a matchParentSize Box so it
                    // tracks the LazyColumn's wrapped height WITHOUT forcing
                    // the outer Box to fill its heightIn(max=…) cap. Then
                    // align CenterEnd + wrap width to keep it pinned at the
                    // right edge at its natural 6dp thickness.
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        VerticalScrollbar(
                            modifier = Modifier.fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(listState),
                            style = morpheScrollbarStyle(),
                        )
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun DeviceAppFilterControls(
    apps: List<DiscoveredDeviceApp>,
    filter: DeviceAppsFilter,
    onFilterChange: (DeviceAppsFilter) -> Unit,
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val knownCount = apps.count { it.patchSourceAvailability == DevicePatchSourceAvailability.AVAILABLE }
    val patchableCount = apps.count { it.patchability == DevicePatchability.PATCHABLE }
    val noSourceCount = apps.size - knownCount
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(stringResource(Res.string.home_filter_device_patchable), patchableCount.takeIf { it > 0 }, filter == DeviceAppsFilter.PATCHABLE, accents.primary, font, corners.small, stringResource(Res.string.home_tooltip_device_patchable)) {
            onFilterChange(DeviceAppsFilter.PATCHABLE)
        }
        FilterChip(stringResource(Res.string.home_filter_device_known), knownCount.takeIf { it > 0 }, filter == DeviceAppsFilter.KNOWN, accents.primary, font, corners.small, stringResource(Res.string.home_tooltip_device_known)) {
            onFilterChange(DeviceAppsFilter.KNOWN)
        }
        FilterChip(stringResource(Res.string.home_filter_device_no_source), noSourceCount.takeIf { it > 0 }, filter == DeviceAppsFilter.NO_PATCH_SOURCE, accents.primary, font, corners.small, stringResource(Res.string.home_tooltip_device_no_source)) {
            onFilterChange(DeviceAppsFilter.NO_PATCH_SOURCE)
        }
        FilterChip(stringResource(Res.string.home_filter_device_all), apps.size.takeIf { it > 0 }, filter == DeviceAppsFilter.ALL, accents.primary, font, corners.small, stringResource(Res.string.home_tooltip_device_all)) {
            onFilterChange(DeviceAppsFilter.ALL)
        }
    }
}

@Composable
private fun DeviceAppsListBody(
    snapshot: DeviceAppDiscoverySnapshot?,
    deviceDisplayName: String?,
    filteredApps: List<DiscoveredDeviceApp>,
    searchQuery: String,
    onRefresh: () -> Unit,
    onVisiblePackages: (String, List<String>) -> Unit,
    onImport: (DiscoveredDeviceApp) -> Unit,
    importingPackage: String?,
    importStatus: String?,
    sourceNamesByPackage: Map<String, List<String>>,
    unifiedAppStates: UnifiedHomeAppStateIndex,
    onInstallPatchedApp: (String) -> Unit,
    paneMaxHeight: Dp,
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val accents = LocalMorpheAccents.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MorpheTooltip(snapshot?.deviceSerial?.let { "ADB serial: $it" } ?: "No ready device selected") {
            Text(
                deviceDisplayName ?: "No ready device selected",
                modifier = Modifier.weight(1f),
                fontFamily = font,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MorpheTooltip(TooltipText.DEVICE_REFRESH) {
            TextButton(
                onClick = onRefresh,
                enabled = snapshot?.isRefreshing != true,
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Icon(MorpheIcons.Refresh, contentDescription = "Refresh device apps", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (snapshot?.isRefreshing == true) "Refreshing…" else "Refresh", fontSize = 10.sp)
            }
        }
    }
    when {
        snapshot == null -> DeviceDiscoveryMessage("Connect and select an authorized ADB device.")
        snapshot.isRefreshing && snapshot.apps.isEmpty() -> DeviceDiscoveryMessage("Reading installed apps…")
        snapshot.error != null -> DeviceDiscoveryMessage(snapshot.error)
        filteredApps.isEmpty() -> DeviceDiscoveryMessage(
            if (searchQuery.isBlank()) "No installed apps match this filter." else "No device apps match \"$searchQuery\".",
        )
        else -> {
            val listState = rememberLazyListState()
            LaunchedEffect(snapshot.deviceSerial, listState, filteredApps) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
                    .collect { onVisiblePackages(snapshot.deviceSerial, it) }
            }
            Box(Modifier.fillMaxWidth().heightIn(max = (paneMaxHeight - 158.dp).coerceAtLeast(120.dp))) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val unifiedState = unifiedAppStates.forInstalledPackage(app.packageName)
                        val deviceProjection = projectDeviceHomeApp(app.patchability, unifiedState)
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(corners.medium))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), RoundedCornerShape(corners.medium))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(app.displayName, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (app.displayName != app.packageName) Text(app.packageName, fontFamily = font, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(installedVersionLabel(app.versionName, app.versionCode), fontFamily = font, fontSize = 10.sp)
                            Text(
                                stringResource(Res.string.home_device_patch_input_label),
                                fontFamily = font,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                when (app.patchability) {
                                    DevicePatchability.PATCHABLE -> "Patches available: ${app.patchNames.joinToString().ifBlank { "Yes" }}"
                                    DevicePatchability.VERSION_NOT_CONFIRMED -> "Patches available · version not confirmed"
                                    DevicePatchability.INCOMPATIBLE_VERSION -> "No compatible patch for installed version"
                                    DevicePatchability.NO_PATCH_SOURCE -> "No patch source"
                                },
                                fontFamily = font,
                                fontSize = 10.sp,
                            )
                            sourceNamesByPackage[app.packageName].orEmpty().takeIf { it.isNotEmpty() }?.let {
                                Text("Sources: ${it.joinToString()}", fontFamily = font, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                stringResource(Res.string.home_device_morphe_action_label),
                                fontFamily = font,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MorpheTooltip(stringResource(Res.string.home_device_endpoint_tooltip)) {
                                Text(
                                    deviceActionStatusLabel(deviceProjection.actionStatus),
                                    fontFamily = font,
                                    fontSize = 10.sp,
                                    color = if (unifiedState?.requiresAction == true) accents.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (deviceProjection.showInstallAction && unifiedState != null) {
                                MorpheTooltip(TooltipText.INSTALL) {
                                    Button(
                                        onClick = { onInstallPatchedApp(unifiedState.originalPackageName) },
                                        modifier = Modifier.height(dimens.controlHeight),
                                    ) {
                                        Icon(MorpheIcons.Download, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(Res.string.home_action_install_to_device), fontFamily = font, fontSize = 10.sp)
                                    }
                                }
                            } else if (
                                app.versionCode != null &&
                                deviceProjection.showUseInstalledAppAction
                            ) {
                                val importing = importingPackage == app.packageName
                                MorpheTooltip(TooltipText.DEVICE_IMPORT) {
                                    OutlinedButton(
                                        onClick = { onImport(app) },
                                        enabled = importingPackage == null,
                                        modifier = Modifier.height(dimens.controlHeight),
                                    ) {
                                        if (importing) {
                                            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(if (importing) importStatus ?: "Importing…" else "Use installed app", fontFamily = font, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                    style = morpheScrollbarStyle(),
                )
            }
        }
    }
}

@Composable
private fun DeviceDiscoveryMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp, end = 12.dp), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 11.sp, fontFamily = LocalMorpheFont.current, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AppSortControls(
    filter: AppListFilter,
    state: AppSortState,
    onStateChange: (AppSortState) -> Unit,
    font: FontFamily,
    corners: MorpheCornerStyle,
    accents: MorpheAccentColors,
) {
    var menuExpanded by remember(filter) { mutableStateOf(false) }
    val dimens = LocalMorpheDimens.current
    val directionLabel = if (state.direction == AppSortDirection.ASCENDING) "Sort ascending" else "Sort descending"
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            MorpheTooltip(sortCriterionTooltip(state.criterion)) {
                Row(
                    modifier = Modifier
                        .height(dimens.controlHeight)
                        .clip(RoundedCornerShape(corners.small))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(corners.small))
                        .clickable { menuExpanded = true }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Sort: ${state.criterion.label}",
                        fontFamily = font,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(MorpheIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(corners.small),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                availableSortCriteria(filter).forEach { criterion ->
                    MorpheTooltip(sortCriterionTooltip(criterion)) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    criterion.label,
                                    fontFamily = font,
                                    fontSize = 11.sp,
                                    color = if (criterion == state.criterion) accents.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                onStateChange(state.copy(
                                    criterion = criterion,
                                    direction = if (criterion == AppSortCriterion.ACTION_PRIORITY) {
                                        AppSortDirection.ASCENDING
                                    } else state.direction,
                                ))
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        MorpheTooltip(directionLabel) {
            IconButton(
                onClick = {
                    onStateChange(state.copy(
                        direction = if (state.direction == AppSortDirection.ASCENDING) {
                            AppSortDirection.DESCENDING
                        } else AppSortDirection.ASCENDING,
                    ))
                },
                modifier = Modifier.size(dimens.controlHeight),
            ) {
                Icon(
                    imageVector = if (state.direction == AppSortDirection.ASCENDING) MorpheIcons.KeyboardArrowUp else MorpheIcons.KeyboardArrowDown,
                    contentDescription = directionLabel,
                    modifier = Modifier.size(18.dp),
                    tint = accents.primary,
                )
            }
        }
    }
}

@Composable
private fun YourAppFilterControls(
    unifiedStates: Map<String, UnifiedHomeAppState>,
    totalCount: Int,
    filter: YourAppsFilter,
    onFilterChange: (YourAppsFilter) -> Unit,
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val counts = mapOf(
        YourAppsFilter.ALL to totalCount,
        YourAppsFilter.ACTION_REQUIRED to unifiedStates.values.count { it.requiresAction },
        YourAppsFilter.APP_UPDATE to unifiedStates.values.count { it.appUpdateAvailable },
        YourAppsFilter.PATCH_UPDATE to unifiedStates.values.count { it.relevantPatchUpdate },
        YourAppsFilter.INSTALL_READY to unifiedStates.values.count { it.installReady },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YourAppsFilter.entries.forEach { candidate ->
            FilterChip(
                label = yourAppsFilterLabel(candidate),
                count = counts[candidate]?.takeIf { it > 0 },
                selected = filter == candidate,
                accent = accents.primary,
                font = font,
                corner = corners.small,
                tooltip = yourAppsFilterTooltip(candidate),
                onClick = { onFilterChange(candidate) },
            )
        }
    }
}

@Composable
private fun yourAppsFilterLabel(filter: YourAppsFilter): String = when (filter) {
    YourAppsFilter.ALL -> stringResource(Res.string.home_filter_your_all)
    YourAppsFilter.ACTION_REQUIRED -> stringResource(Res.string.home_filter_action_required)
    YourAppsFilter.APP_UPDATE -> stringResource(Res.string.home_filter_app_update)
    YourAppsFilter.PATCH_UPDATE -> stringResource(Res.string.home_filter_patch_update)
    YourAppsFilter.INSTALL_READY -> stringResource(Res.string.home_filter_install_ready)
}

@Composable
private fun yourAppsFilterTooltip(filter: YourAppsFilter): String = when (filter) {
    YourAppsFilter.ALL -> stringResource(Res.string.home_tooltip_your_all)
    YourAppsFilter.ACTION_REQUIRED -> stringResource(Res.string.home_tooltip_action_required)
    YourAppsFilter.APP_UPDATE -> stringResource(Res.string.home_tooltip_app_update)
    YourAppsFilter.PATCH_UPDATE -> stringResource(Res.string.home_tooltip_patch_update)
    YourAppsFilter.INSTALL_READY -> stringResource(Res.string.home_tooltip_install_ready)
}

private fun sortCriterionTooltip(criterion: AppSortCriterion): String = when (criterion) {
    AppSortCriterion.NAME -> "Sort apps alphabetically by displayed name."
    AppSortCriterion.PACKAGE -> "Sort apps alphabetically by Android package name."
    AppSortCriterion.LAST_PATCHED -> "Sort apps by the time their local patched APK was created."
    AppSortCriterion.INSTALLED_VERSION -> "Sort apps by the version installed on the selected device."
    AppSortCriterion.ACTION_PRIORITY -> TooltipText.SORT_ACTION_PRIORITY
}

/**
 * Slim, elongated search field used when third-party patches are loaded.
 * Built on BasicTextField so we can drop below the 56dp minimum height that
 * Material 3's OutlinedTextField enforces internally. Visually mirrors the
 * default OutlinedTextField (border, leading search icon, trailing clear,
 * font placeholder), just thinner and wider.
 */
@Composable
internal fun SlimSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    font: FontFamily,
    corners: MorpheCornerStyle,
    accents: MorpheAccentColors,
    maxWidth: Dp = 340.dp,
) {
    val dimens = LocalMorpheDimens.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(150),
        label = "slimSearchBorder"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = font,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(accents.primary),
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    MorpheIcons.Search,
                    contentDescription = null,
                    tint = muted.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.filter_apps_hint),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = muted.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            MorpheIcons.Clear,
                            contentDescription = stringResource(Res.string.clear),
                            tint = muted.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    )
}

/** Loading skeleton. Ghost row that mimics SupportedAppListRow's shape. */
@Composable
internal fun SkeletonAppRow(
    corners: MorpheCornerStyle,
    staggerOffsetMs: Int,
) {
    val infinite = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, delayMillis = staggerOffsetMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val cardBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .background(cardBg)
            .border(1.dp, outline, RoundedCornerShape(corners.medium))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Row 1: avatar + name/package bars
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor),
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(140.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .background(baseColor),
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(180.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .background(baseColor.copy(alpha = alpha * 0.6f)),
                )
            }
        }
        // Row 2: chip placeholders
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(110.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor),
            )
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(130.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor.copy(alpha = alpha * 0.7f)),
            )
        }
    }
}
