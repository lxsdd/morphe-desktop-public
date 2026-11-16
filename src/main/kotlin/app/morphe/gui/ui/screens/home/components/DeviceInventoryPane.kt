package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.util.rememberLocalLauncherIcon
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.screens.home.DeviceInventoryRefreshStage
import app.morphe.gui.util.AppLabelIndexState
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class DeviceViewFilter {
    UPDATES, RELEVANT, APP_UPDATES, PATCH_UPDATES, NEEDS_ATTENTION, PATCHABLE, VERIFIED,
    VERSION_MATCH, SIGNER_CONFLICT, LOCAL_BUILD_REVIEW, REVIEW_REQUIRED,
    OUTSIDE_CATALOG, OTHER, ALL
}

/** All statuses remain filterable even when they are not present in a shortcut chip. */
@Composable
private fun deviceFilterLabel(filter: DeviceViewFilter): String = when (filter) {
    DeviceViewFilter.UPDATES -> stringResource(Res.string.device_updates_filter)
    DeviceViewFilter.RELEVANT -> stringResource(Res.string.device_filter_relevant)
    DeviceViewFilter.APP_UPDATES -> stringResource(Res.string.device_filter_app_updates)
    DeviceViewFilter.PATCH_UPDATES -> stringResource(Res.string.device_filter_patch_updates)
    DeviceViewFilter.NEEDS_ATTENTION -> stringResource(Res.string.device_filter_attention)
    DeviceViewFilter.PATCHABLE -> stringResource(Res.string.device_filter_patchable)
    DeviceViewFilter.VERIFIED -> stringResource(Res.string.device_filter_verified)
    DeviceViewFilter.VERSION_MATCH -> stringResource(Res.string.device_filter_version_match)
    DeviceViewFilter.SIGNER_CONFLICT -> stringResource(Res.string.device_overview_signer_conflict)
    DeviceViewFilter.LOCAL_BUILD_REVIEW -> stringResource(Res.string.device_overview_local_build)
    DeviceViewFilter.REVIEW_REQUIRED -> stringResource(Res.string.device_overview_review)
    DeviceViewFilter.OUTSIDE_CATALOG -> stringResource(Res.string.device_overview_other)
    DeviceViewFilter.OTHER -> stringResource(Res.string.device_filter_other)
    DeviceViewFilter.ALL -> stringResource(Res.string.device_filter_all)
}

@Composable
private fun deviceSortLabel(mode: DeviceInventorySort): String = when (mode) {
    DeviceInventorySort.STATUS_FIRST -> stringResource(Res.string.device_sort_status_first)
    DeviceInventorySort.STATUS_LAST -> stringResource(Res.string.device_sort_status_last)
    DeviceInventorySort.NAME_ASC -> stringResource(Res.string.device_sort_name_asc)
    DeviceInventorySort.NAME_DESC -> stringResource(Res.string.device_sort_name_desc)
}

private data class DeviceOverviewRow(
    val packageName: String,
    val displayName: String,
    val installedVersion: String?,
    val targetVersion: String?,
    val record: PatchedAppRecord?,
    val sourceCount: Int,
    val sourceCompatibility: DevicePatchCompatibility,
    val separateLocalPackage: String?,
    val appUpdateAvailable: Boolean,
    val confirmedPatchUpdate: Boolean,
    val status: DeviceOverviewStatus,
)

private fun deviceLabel(status: DeviceOverviewStatus): StringResource = when (status) {
    DeviceOverviewStatus.APP_UPDATE -> Res.string.device_overview_app_update
    DeviceOverviewStatus.PATCH_UPDATE -> Res.string.device_overview_patch_update
    DeviceOverviewStatus.LOCAL_BUILD_REVIEW -> Res.string.device_overview_local_build
    DeviceOverviewStatus.PATCH_CANDIDATE -> Res.string.device_overview_patchable
    DeviceOverviewStatus.PATCH_VERSION_INCOMPATIBLE -> Res.string.device_overview_patch_version_incompatible
    DeviceOverviewStatus.PATCH_SOURCE_UNVERIFIED -> Res.string.device_overview_patch_source_unverified
    DeviceOverviewStatus.ORIGINAL_WITH_SEPARATE_INSTALL -> Res.string.device_overview_original_separate
    DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED -> Res.string.device_overview_version_match
    DeviceOverviewStatus.EXACT_BUILD_VERIFIED -> Res.string.device_overview_exact_build
    DeviceOverviewStatus.EXACT_BUILD_OTHER_KEY -> Res.string.device_overview_exact_other_key
    DeviceOverviewStatus.SIGNER_CONFLICT -> Res.string.device_overview_signer_conflict
    DeviceOverviewStatus.REVIEW_REQUIRED -> Res.string.device_overview_review
    DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG -> Res.string.device_overview_other
}

/** Device-first, read-only inventory. Never mutates packages or installs an APK. */
@Composable
internal fun DeviceInventoryPane(
    installedPackages: Set<String>,
    deviceAppLabels: Map<String, String>,
    deviceLabelIndexState: AppLabelIndexState,
    deviceLabelIndexedCount: Int,
    selectedDeviceSerial: String?,
    refreshSerial: String?,
    refreshStage: DeviceInventoryRefreshStage,
    supportedApps: List<SupportedApp>,
    patchedRecords: List<PatchedAppRecord>,
    deviceInstalledAppInfo: Map<String, DeviceAppInfo>,
    updateInfoByPackage: Map<String, RecallUpdateInfo>,
    sourceNamesByPackage: Map<String, List<String>>,
    patchesBySource: Map<String, List<PatchMetadata>>,
    searchQuery: String,
    paneMaxHeight: Dp,
    onShowDetail: (PatchedAppRecord) -> Unit,
    onShowDeviceApp: (String) -> Unit,
    onRefreshDeviceInfo: () -> Unit,
    onRetryDeviceLabels: () -> Unit,
    onInstallExistingApk: () -> Unit,
) {
    if (refreshSerial != null && refreshStage.inProgress) {
        // A scan may have a partially loaded package list internally, but those
        // entries are NOT ready for interaction and must not look up-to-date.
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 230.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.device_scan_loading),
                style = MorpheTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            val phase = when (refreshStage) {
                DeviceInventoryRefreshStage.READING_PACKAGES -> Res.string.device_scan_packages
                DeviceInventoryRefreshStage.READING_VERSIONS -> Res.string.device_scan_versions
                DeviceInventoryRefreshStage.VERIFYING_BUILDS -> Res.string.device_scan_fingerprints
                DeviceInventoryRefreshStage.READING_CATALOG_APPS -> Res.string.device_scan_catalog
                else -> Res.string.device_scan_packages
            }
            Text(stringResource(phase), style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.device_scan_old_hidden),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (refreshSerial != null && refreshStage == DeviceInventoryRefreshStage.FAILED) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.device_scan_failed),
                style = MorpheTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onRefreshDeviceInfo) {
                Text(stringResource(Res.string.device_overview_refresh))
            }
        }
        return
    }
    if (selectedDeviceSerial == null) {
        YourAppsEmptyHint(
            title = stringResource(Res.string.home_filter_device_apps),
            subtitle = stringResource(Res.string.home_device_inventory_unavailable),
        )
        return
    }
    val uriHandler = LocalUriHandler.current
    // The loaded patch metadata is the same source snapshot as patching/details.
    val catalogPatches = remember(patchesBySource) { patchesBySource.values.flatten() }
    val rows = remember(selectedDeviceSerial, installedPackages, supportedApps,
        patchedRecords, deviceInstalledAppInfo, deviceAppLabels, updateInfoByPackage, sourceNamesByPackage, catalogPatches) {
        val supported = supportedApps.associateBy { it.packageName }
        val history = patchedRecords.associateBy { it.installedPackageName }
        installedPackages.map { pkg ->
            val record = history[pkg]
            val app = supported[pkg]
            // A separately installed Morphe package is NOT the original on-device
            // app. Its historical source package is only used to locate compatible
            // patch metadata; installed version/identity still come from pkg.
            val catalogApp = app ?: record?.let { supported[it.packageName] }
            // Read the exact installed package, never the original historical key.
            val metadata = deviceInstalledAppInfo[pkg]
            val installedVersion = metadata?.installedVersion?.takeIf { it.isNotBlank() }
            val compatibility = devicePatchCompatibility(catalogPatches, pkg, installedVersion)
            val separatePackage = if (record == null) {
                installedSeparateHistoryPackage(pkg, installedPackages, patchedRecords)
            } else null
            val update = record?.let { updateInfoByPackage[it.packageName] }
            // Device recommendations are evaluated against this exact installed
            // version, never an original APK's or a cached history version.
            val target = latestCompatibleDeviceAppTarget(catalogApp, installedVersion)
            val facts = verifiedDeviceUpdateFacts(
                installedPackageName = pkg,
                installedVersion = installedVersion,
                targetVersion = target,
                history = record,
                historicalUpdates = update,
                exactBuildVerified = metadata?.exactBuildVerified,
            )
            val newerVersion = facts.confirmedAppVersionUpdate
            val status = classifyDeviceOverview(DeviceOverviewEvidence(
                patchSupported = catalogApp != null,
                hasLocalPatchHistory = record != null,
                installedVersion = installedVersion?.removePrefix("v"),
                locallyPatchedVersion = record?.apkVersion?.removePrefix("v"),
                signerMatchesMorphe = metadata?.signedByMorphe,
                newerCompatibleAppVersion = newerVersion,
                relevantPatchChange = facts.confirmedPatchUpdate,
                localBuildVersionNewer = metadata?.installPending == true,
                exactBuildVerified = metadata?.exactBuildVerified,
                sourceCompatibility = compatibility,
                separateLocalPackageInstalled = separatePackage != null,
            ))
            DeviceOverviewRow(
                packageName = pkg,
                displayName = resolveDeviceInventoryDisplayName(
                    installedPackageName = pkg,
                    recordedDisplayName = record?.displayName,
                    originalPackageName = record?.packageName,
                    originalCatalogName = record?.packageName?.let { supported[it]?.displayName },
                    installedCatalogName = app?.displayName,
                    androidResolvedName = deviceAppLabels[pkg],
                ),
                installedVersion = installedVersion,
                targetVersion = target?.takeIf { newerVersion },
                record = record,
                sourceCount = sourceNamesByPackage[pkg]?.size ?: 0,
                sourceCompatibility = compatibility,
                separateLocalPackage = separatePackage,
                appUpdateAvailable = newerVersion,
                confirmedPatchUpdate = facts.confirmedPatchUpdate,
                status = status,
            )
        }
    }
    // Default means actionable updates, NOT forensic signing/hash checks.
    var filter by remember(selectedDeviceSerial) { mutableStateOf(DeviceViewFilter.UPDATES) }
    var diagnosticsVisible by remember(selectedDeviceSerial) { mutableStateOf(false) }
    var sort by remember(selectedDeviceSerial) { mutableStateOf(DeviceInventorySort.NAME_ASC) }
    val supportedPackages = remember(supportedApps) { supportedApps.map { it.packageName }.toSet() }
    val relevantCount = rows.count { it.packageName in supportedPackages || it.record != null }
    val supportedCount = rows.count { it.packageName in supportedPackages }
    // A device can have both an app update and a patch update. Each dimension
    // is counted independently, even when app upgrade takes visual priority.
    val appUpdateCount = rows.count { it.appUpdateAvailable }
    val patchUpdateCount = rows.count { it.confirmedPatchUpdate }
    val updateCount = confirmedDeviceUpdateCount(rows.map {
        DeviceUpdateFacts(it.appUpdateAvailable, it.confirmedPatchUpdate)
    })
    val technicalReviewCount = rows.count { deviceTechnicalReview(it.status) }
    val verifiedCount = rows.count { row ->
        row.record?.let { record ->
            deviceInstalledAppInfo[row.packageName]?.exactBuildVerified == true &&
                row.installedVersion?.removePrefix("v") == record.apkVersion.removePrefix("v")
        } == true
    }
    val visible = remember(rows, searchQuery, filter, sort, supportedPackages) {
        val matching = 
        rows.filter { row ->
            (searchQuery.isBlank() ||
                row.packageName.contains(searchQuery, ignoreCase = true) ||
                row.displayName.contains(searchQuery, ignoreCase = true)) &&
                when (filter) {
                    DeviceViewFilter.UPDATES ->
                        DeviceUpdateFacts(row.appUpdateAvailable, row.confirmedPatchUpdate).hasConfirmedUpdate
                    DeviceViewFilter.RELEVANT ->
                        row.packageName in supportedPackages || row.record != null
                    DeviceViewFilter.APP_UPDATES -> row.appUpdateAvailable
                    DeviceViewFilter.PATCH_UPDATES -> row.confirmedPatchUpdate
                    DeviceViewFilter.ALL -> true
                    DeviceViewFilter.NEEDS_ATTENTION -> deviceTechnicalReview(row.status)
                    DeviceViewFilter.PATCHABLE -> row.status == DeviceOverviewStatus.PATCH_CANDIDATE
                    DeviceViewFilter.VERIFIED -> row.record?.let { record ->
                        deviceInstalledAppInfo[row.packageName]?.exactBuildVerified == true &&
                            row.installedVersion?.removePrefix("v") == record.apkVersion.removePrefix("v")
                    } == true
                    DeviceViewFilter.VERSION_MATCH -> row.status == DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED
                    DeviceViewFilter.SIGNER_CONFLICT ->
                        row.status == DeviceOverviewStatus.SIGNER_CONFLICT
                    DeviceViewFilter.LOCAL_BUILD_REVIEW ->
                        row.status == DeviceOverviewStatus.LOCAL_BUILD_REVIEW
                    DeviceViewFilter.REVIEW_REQUIRED ->
                        row.status == DeviceOverviewStatus.REVIEW_REQUIRED ||
                            row.status == DeviceOverviewStatus.PATCH_SOURCE_UNVERIFIED
                    DeviceViewFilter.OUTSIDE_CATALOG ->
                        row.status == DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG
                    DeviceViewFilter.OTHER ->
                        row.status == DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG ||
                        row.status == DeviceOverviewStatus.PATCH_VERSION_INCOMPATIBLE ||
                        row.status == DeviceOverviewStatus.REVIEW_REQUIRED
                }
        }
        sortDeviceInventory(matching, sort, { it.status }, { it.displayName }, { it.packageName })
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.device_overview_summary,
                    installedPackages.size, supportedCount, updateCount),
                style = MorpheTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onInstallExistingApk) {
                Text(stringResource(Res.string.direct_apk_install_button))
            }
            OutlinedButton(onClick = onRefreshDeviceInfo) {
                Text(stringResource(Res.string.device_overview_refresh))
            }
        }
        when (deviceLabelIndexState) {
            AppLabelIndexState.INDEXING -> Text(
                text = stringResource(Res.string.device_labels_indexing,
                    deviceLabelIndexedCount, installedPackages.size, deviceAppLabels.size),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppLabelIndexState.INCOMPLETE_RETRYABLE -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.device_labels_incomplete),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onRetryDeviceLabels) {
                    Text(stringResource(Res.string.device_labels_retry))
                }
            }
            AppLabelIndexState.COMPLETE -> {
                if (deviceAppLabels.size < installedPackages.size) Text(
                    text = stringResource(Res.string.device_labels_complete),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppLabelIndexState.NOT_STARTED -> Unit
        }
        // Diagnostic warnings and sorting never compete with confirmed updates.
        if (diagnosticsVisible) {
            Text(
                text = stringResource(Res.string.device_overview_disclaimer),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Advanced filters and technical sorting remain available on demand.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 600.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MorpheDropdown(
                        label = stringResource(Res.string.device_filter_label, deviceFilterLabel(filter)),
                        items = DeviceViewFilter.entries.map { entry ->
                            MorpheDropdownItem(deviceFilterLabel(entry)) { filter = entry }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MorpheDropdown(
                        label = stringResource(Res.string.device_sort_label, deviceSortLabel(sort)),
                        items = DeviceInventorySort.entries.map { entry ->
                            MorpheDropdownItem(deviceSortLabel(entry)) { sort = entry }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorpheDropdown(
                        label = stringResource(Res.string.device_filter_label, deviceFilterLabel(filter)),
                        items = DeviceViewFilter.entries.map { entry ->
                            MorpheDropdownItem(deviceFilterLabel(entry)) { filter = entry }
                        },
                        modifier = Modifier.width(270.dp),
                    )
                    MorpheDropdown(
                        label = stringResource(Res.string.device_sort_label, deviceSortLabel(sort)),
                        items = DeviceInventorySort.entries.map { entry ->
                            MorpheDropdownItem(deviceSortLabel(entry)) { sort = entry }
                        },
                        modifier = Modifier.width(250.dp),
                    )
                }
            }
        }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MorpheChoiceChip(text = stringResource(Res.string.device_updates_count, updateCount),
                active = filter == DeviceViewFilter.UPDATES,
                onClick = { filter = DeviceViewFilter.UPDATES })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_relevant, relevantCount),
                active = filter == DeviceViewFilter.RELEVANT,
                onClick = { filter = DeviceViewFilter.RELEVANT })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_all, rows.size),
                active = filter == DeviceViewFilter.ALL,
                onClick = { filter = DeviceViewFilter.ALL })
            MorpheChoiceChip(text = stringResource(
                    if (diagnosticsVisible) Res.string.device_diagnostics_hide
                    else Res.string.device_diagnostics_show),
                active = diagnosticsVisible,
                onClick = {
                    diagnosticsVisible = !diagnosticsVisible
                    filter = if (diagnosticsVisible) DeviceViewFilter.RELEVANT
                        else DeviceViewFilter.UPDATES
                })
            if (diagnosticsVisible) {
                MorpheChoiceChip(text = stringResource(Res.string.device_overview_apps_to_update, appUpdateCount),
                    active = filter == DeviceViewFilter.APP_UPDATES,
                    onClick = { filter = DeviceViewFilter.APP_UPDATES })
                MorpheChoiceChip(text = stringResource(Res.string.device_overview_patches_to_update, patchUpdateCount),
                    active = filter == DeviceViewFilter.PATCH_UPDATES,
                    onClick = { filter = DeviceViewFilter.PATCH_UPDATES })
                MorpheChoiceChip(text = stringResource(Res.string.device_diagnostics_cases_count, technicalReviewCount),
                    active = filter == DeviceViewFilter.NEEDS_ATTENTION,
                    onClick = { filter = DeviceViewFilter.NEEDS_ATTENTION })
                MorpheChoiceChip(text = stringResource(Res.string.device_overview_verified_count, verifiedCount),
                    active = filter == DeviceViewFilter.VERIFIED,
                    onClick = { filter = DeviceViewFilter.VERIFIED })
            }
        }
        if (diagnosticsVisible) {
            Text(
                text = stringResource(Res.string.device_update_counts_scope),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (visible.isEmpty()) {
            val noUpdates = filter == DeviceViewFilter.UPDATES && searchQuery.isBlank()
            YourAppsEmptyHint(
                title = stringResource(if (noUpdates) Res.string.device_updates_none
                    else Res.string.no_matches),
                subtitle = stringResource(if (noUpdates) Res.string.device_updates_none_explain
                    else Res.string.device_overview_no_results),
            )
        } else {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxWidth()
                .heightIn(max = (paneMaxHeight - 175.dp).coerceAtLeast(120.dp))) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(visible, key = { it.packageName }) { row ->
                        val confirmedUpdate = DeviceUpdateFacts(
                            row.appUpdateAvailable, row.confirmedPatchUpdate).hasConfirmedUpdate
                        // Every installed app has details. Existing local-history
                        // entries keep their full patched-app editor; other entries
                        // open a read-only catalog/device dialog instead.
                        val validatedSource = verifiedLocalIconSource(
                            record = row.record,
                            installedPackageName = row.packageName,
                            installedVersion = row.installedVersion,
                            exactBuildVerified = deviceInstalledAppInfo[row.packageName]?.exactBuildVerified == true,
                        )
                        val icon = rememberLocalLauncherIcon(
                            validatedSource?.outputApkPath,
                            validatedSource?.inputApkPath,
                        )
                        Card(modifier = Modifier.fillMaxWidth().clickable {
                            val record = row.record
                            if (record != null) onShowDetail(record)
                            else onShowDeviceApp(row.packageName)
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MorpheAppAvatar(
                                    name = row.displayName,
                                    size = MorpheAvatarSize.MEDIUM,
                                    iconBitmap = icon,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(row.displayName, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (row.displayName != row.packageName) {
                                        Text(row.packageName,
                                            style = MorpheTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    row.record?.let { record ->
                                        Text(stringResource(Res.string.device_overview_local_version,
                                            "v" + record.apkVersion.removePrefix("v")),
                                            style = MorpheTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (diagnosticsVisible) {
                                    // Only an exact installed APK hash match proves the local
                                    // patch-source snapshot is also installed on this device.
                                    val patchInfo = when {
                                        row.status == DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG ->
                                            stringResource(Res.string.device_overview_outside_source)
                                        row.separateLocalPackage != null ->
                                            stringResource(Res.string.device_overview_separate_package,
                                                row.separateLocalPackage)
                                        row.record == null -> when (row.sourceCompatibility) {
                                            DevicePatchCompatibility.COMPATIBLE ->
                                                stringResource(Res.string.device_overview_patch_candidate_count,
                                                    row.sourceCount)
                                            DevicePatchCompatibility.INCOMPATIBLE ->
                                                stringResource(Res.string.device_overview_patch_version_not_supported)
                                            DevicePatchCompatibility.UNKNOWN_VERSION ->
                                                stringResource(Res.string.device_overview_patch_version_unknown)
                                            DevicePatchCompatibility.NO_SOURCE ->
                                                stringResource(Res.string.device_overview_patch_source_missing)
                                        }
                                        deviceInstalledAppInfo[row.packageName]?.exactBuildVerified == true -> {
                                            val relevantUpdates = verifiedDeviceDiagnosticPatchSources(
                                                installedPackageName = row.packageName,
                                                installedVersion = row.installedVersion,
                                                history = row.record,
                                                historicalUpdates = updateInfoByPackage[row.record.packageName],
                                                exactBuildVerified = deviceInstalledAppInfo[row.packageName]?.exactBuildVerified,
                                            ).joinToString(" · ") { src ->
                                                    "${src.name} v${src.usedVersion.removePrefix("v")} → " +
                                                        "v${src.latestAvailableVersion!!.removePrefix("v")}"
                                                }
                                            val versions = row.record.sourcesSnapshot
                                                .joinToString(" · ") { src ->
                                                    "${src.sourceName} v${src.version.removePrefix("v")}"
                                                }
                                            when {
                                                relevantUpdates.isNotBlank() ->
                                                    stringResource(Res.string.device_overview_patch_change, relevantUpdates)
                                                versions.isNotBlank() ->
                                                    stringResource(Res.string.device_overview_installed_patches, versions)
                                                else -> stringResource(Res.string.device_overview_patch_unverified)
                                            }
                                        }
                                        else -> stringResource(Res.string.device_overview_patch_unverified)
                                    }
                                    Text(
                                        text = patchInfo,
                                        style = MorpheTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    }
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    val readableStatus = deviceReadableUpdateStatus(
                                        DeviceUpdateFacts(row.appUpdateAvailable, row.confirmedPatchUpdate),
                                        diagnostic = row.status,
                                        historicalUpdates = row.record?.let { updateInfoByPackage[it.packageName] },
                                        history = row.record,
                                        installedVersion = row.installedVersion,
                                        exactBuildVerified = deviceInstalledAppInfo[row.packageName]?.exactBuildVerified,
                                    )
                                    if (diagnosticsVisible || readableStatus != DeviceReadableUpdateStatus.NONE) {
                                        val statusText = if (diagnosticsVisible) {
                                            stringResource(deviceLabel(row.status))
                                        } else when (readableStatus) {
                                            DeviceReadableUpdateStatus.APP_AND_PATCH -> stringResource(Res.string.device_updates_both)
                                            DeviceReadableUpdateStatus.APP -> stringResource(Res.string.device_overview_app_update)
                                            DeviceReadableUpdateStatus.PATCH -> stringResource(Res.string.device_overview_patch_update)
                                            DeviceReadableUpdateStatus.CURRENT -> stringResource(Res.string.home_state_current)
                                            DeviceReadableUpdateStatus.CHECKING -> stringResource(Res.string.home_update_checks_incomplete_short)
                                            DeviceReadableUpdateStatus.UNSUPPORTED -> stringResource(Res.string.device_overview_patch_version_incompatible)
                                            DeviceReadableUpdateStatus.NONE -> ""
                                        }
                                        Text(statusText,
                                            style = MorpheTheme.typography.bodySmall,
                                            color = if (confirmedUpdate) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(stringResource(
                                            Res.string.device_overview_installed_app_version,
                                            row.installedVersion?.let { "v" + it.removePrefix("v") }
                                                ?: stringResource(Res.string.device_overview_version_unknown)),
                                        style = MorpheTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    row.targetVersion?.let { target ->
                                        Text(stringResource(Res.string.device_overview_target_version,
                                            "v" + target.removePrefix("v")),
                                            style = MorpheTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val searchUrl = remember(row.packageName, target) {
                                            SupportedApp.getDownloadUrl(row.packageName, target)
                                        }
                                        if (row.appUpdateAvailable &&
                                            !searchUrl.isNullOrBlank()) {
                                            OutlinedButton(onClick = { uriHandler.openUri(searchUrl) }) {
                                                Text(stringResource(
                                                    Res.string.home_guided_find_app_version,
                                                    target.removePrefix("v")))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    style = MorpheScrollbarStyle(),
                )
            }
        }
    }
}
