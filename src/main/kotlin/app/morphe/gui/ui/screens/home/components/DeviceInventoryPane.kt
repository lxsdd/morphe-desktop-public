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
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.util.isNewerVersion
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class DeviceViewFilter { RELEVANT, APP_UPDATES, PATCH_UPDATES, NEEDS_ATTENTION, PATCHABLE, VERIFIED, VERSION_MATCH, OTHER, ALL }

private data class DeviceOverviewRow(
    val packageName: String,
    val displayName: String,
    val installedVersion: String?,
    val targetVersion: String?,
    val record: PatchedAppRecord?,
    val sourceCount: Int,
    val status: DeviceOverviewStatus,
)

private fun deviceLabel(status: DeviceOverviewStatus): StringResource = when (status) {
    DeviceOverviewStatus.APP_UPDATE -> Res.string.device_overview_app_update
    DeviceOverviewStatus.PATCH_UPDATE -> Res.string.device_overview_patch_update
    DeviceOverviewStatus.LOCAL_BUILD_REVIEW -> Res.string.device_overview_local_build
    DeviceOverviewStatus.PATCH_CANDIDATE -> Res.string.device_overview_patchable
    DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED -> Res.string.device_overview_version_match
    DeviceOverviewStatus.EXACT_BUILD_VERIFIED -> Res.string.device_overview_exact_build
    DeviceOverviewStatus.SIGNER_CONFLICT -> Res.string.device_overview_signer_conflict
    DeviceOverviewStatus.REVIEW_REQUIRED -> Res.string.device_overview_review
    DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG -> Res.string.device_overview_other
}

/** Device-first, read-only inventory. Never mutates packages or installs an APK. */
@Composable
internal fun DeviceInventoryPane(
    installedPackages: Set<String>,
    selectedDeviceSerial: String?,
    supportedApps: List<SupportedApp>,
    patchedRecords: List<PatchedAppRecord>,
    deviceAppInfo: Map<String, DeviceAppInfo>,
    updateInfoByPackage: Map<String, RecallUpdateInfo>,
    sourceNamesByPackage: Map<String, List<String>>,
    searchQuery: String,
    paneMaxHeight: Dp,
    onShowDetail: (PatchedAppRecord) -> Unit,
    onRefreshDeviceInfo: () -> Unit,
) {
    if (selectedDeviceSerial == null) {
        YourAppsEmptyHint(
            title = stringResource(Res.string.home_filter_device_apps),
            subtitle = stringResource(Res.string.home_device_inventory_unavailable),
        )
        return
    }
    val uriHandler = LocalUriHandler.current
    val rows = remember(selectedDeviceSerial, installedPackages, supportedApps,
        patchedRecords, deviceAppInfo, updateInfoByPackage, sourceNamesByPackage) {
        val supported = supportedApps.associateBy { it.packageName }
        val history = patchedRecords.associateBy { it.installedPackageName }
        installedPackages.map { pkg ->
            val record = history[pkg]
            val app = supported[pkg]
            val metadata = deviceAppInfo[record?.packageName ?: pkg]
            val installedVersion = metadata?.installedVersion?.takeIf { it.isNotBlank() }
            val update = record?.let { updateInfoByPackage[it.packageName] }
            val target = if (record != null && hasConfirmedAppUpdate(update)) {
                update?.appSuggestedVersion
            } else app?.recommendedVersion
            val newerVersion = installedVersion != null &&
                !target.isNullOrBlank() && isNewerVersion(target, installedVersion)
            val status = classifyDeviceOverview(DeviceOverviewEvidence(
                patchSupported = app != null,
                hasLocalPatchHistory = record != null,
                installedVersion = installedVersion?.removePrefix("v"),
                locallyPatchedVersion = record?.apkVersion?.removePrefix("v"),
                signerMatchesMorphe = metadata?.signedByMorphe,
                newerCompatibleAppVersion = newerVersion,
                relevantPatchChange = hasConfirmedPatchUpdate(update),
                localBuildVersionNewer = metadata?.installPending == true,
                exactBuildVerified = metadata?.exactBuildVerified,
            ))
            DeviceOverviewRow(
                packageName = pkg,
                displayName = record?.displayName ?: app?.displayName ?: pkg,
                installedVersion = installedVersion,
                targetVersion = target?.takeIf { newerVersion },
                record = record,
                sourceCount = sourceNamesByPackage[pkg]?.size ?: 0,
                status = status,
            )
        }.sortedWith(compareBy<DeviceOverviewRow>(
            { deviceOverviewPriority(it.status) }, { it.displayName.lowercase() }, { it.packageName }))
    }
    var filter by remember(selectedDeviceSerial) { mutableStateOf(DeviceViewFilter.RELEVANT) }
    val supportedPackages = remember(supportedApps) { supportedApps.map { it.packageName }.toSet() }
    val relevantCount = rows.count { it.packageName in supportedPackages || it.record != null }
    val supportedCount = rows.count { it.packageName in supportedPackages }
    val appUpdateCount = rows.count { it.status == DeviceOverviewStatus.APP_UPDATE }
    val patchUpdateCount = rows.count { it.status == DeviceOverviewStatus.PATCH_UPDATE }
    val needsCount = rows.count { deviceOverviewPriority(it.status) <= 3 }
    val patchableCount = rows.count { it.status == DeviceOverviewStatus.PATCH_CANDIDATE }
    val verifiedCount = rows.count { it.status == DeviceOverviewStatus.EXACT_BUILD_VERIFIED }
    val versionMatchCount = rows.count { it.status == DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED }
    val otherCount = rows.count {
        it.status == DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG ||
            it.status == DeviceOverviewStatus.REVIEW_REQUIRED
    }
    val visible = remember(rows, searchQuery, filter, supportedPackages) {
        rows.filter { row ->
            (searchQuery.isBlank() ||
                row.packageName.contains(searchQuery, ignoreCase = true) ||
                row.displayName.contains(searchQuery, ignoreCase = true)) &&
                when (filter) {
                    DeviceViewFilter.RELEVANT ->
                        row.packageName in supportedPackages || row.record != null
                    DeviceViewFilter.APP_UPDATES -> row.status == DeviceOverviewStatus.APP_UPDATE
                    DeviceViewFilter.PATCH_UPDATES -> row.status == DeviceOverviewStatus.PATCH_UPDATE
                    DeviceViewFilter.ALL -> true
                    DeviceViewFilter.NEEDS_ATTENTION -> deviceOverviewPriority(row.status) <= 3
                    DeviceViewFilter.PATCHABLE -> row.status == DeviceOverviewStatus.PATCH_CANDIDATE
                    DeviceViewFilter.VERIFIED -> row.status == DeviceOverviewStatus.EXACT_BUILD_VERIFIED
                    DeviceViewFilter.VERSION_MATCH -> row.status == DeviceOverviewStatus.VERSION_MATCH_UNVERIFIED
                    DeviceViewFilter.OTHER ->
                        row.status == DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG ||
                        row.status == DeviceOverviewStatus.REVIEW_REQUIRED
                }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.device_overview_summary,
                    installedPackages.size, supportedCount, needsCount),
                style = MorpheTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onRefreshDeviceInfo) {
                Text(stringResource(Res.string.device_overview_refresh))
            }
        }
        Text(
            text = stringResource(Res.string.device_overview_disclaimer),
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_relevant, relevantCount),
                active = filter == DeviceViewFilter.RELEVANT,
                onClick = { filter = DeviceViewFilter.RELEVANT })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_apps_to_update, appUpdateCount),
                active = filter == DeviceViewFilter.APP_UPDATES,
                onClick = { filter = DeviceViewFilter.APP_UPDATES })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_patches_to_update, patchUpdateCount),
                active = filter == DeviceViewFilter.PATCH_UPDATES,
                onClick = { filter = DeviceViewFilter.PATCH_UPDATES })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_all, rows.size),
                active = filter == DeviceViewFilter.ALL, onClick = { filter = DeviceViewFilter.ALL })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_needs, needsCount),
                active = filter == DeviceViewFilter.NEEDS_ATTENTION,
                onClick = { filter = DeviceViewFilter.NEEDS_ATTENTION })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_patchable_count, patchableCount),
                active = filter == DeviceViewFilter.PATCHABLE,
                onClick = { filter = DeviceViewFilter.PATCHABLE })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_verified_count, verifiedCount),
                active = filter == DeviceViewFilter.VERIFIED,
                onClick = { filter = DeviceViewFilter.VERIFIED })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_version_count, versionMatchCount),
                active = filter == DeviceViewFilter.VERSION_MATCH,
                onClick = { filter = DeviceViewFilter.VERSION_MATCH })
            MorpheChoiceChip(text = stringResource(Res.string.device_overview_other_count, otherCount),
                active = filter == DeviceViewFilter.OTHER,
                onClick = { filter = DeviceViewFilter.OTHER })
        }
        if (visible.isEmpty()) {
            YourAppsEmptyHint(
                title = stringResource(Res.string.no_matches),
                subtitle = stringResource(Res.string.device_overview_no_results),
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
                        Card(modifier = Modifier.fillMaxWidth().then(
                            row.record?.let { record ->
                                Modifier.clickable { onShowDetail(record) }
                            } ?: Modifier
                        )) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
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
                                    // Only an exact installed APK hash match proves the local
                                    // patch-source snapshot is also installed on this device.
                                    val patchInfo = when {
                                        row.status == DeviceOverviewStatus.OUTSIDE_PATCH_CATALOG ->
                                            stringResource(Res.string.device_overview_outside_source)
                                        row.record == null ->
                                            stringResource(Res.string.device_overview_patch_candidate_count,
                                                row.sourceCount)
                                        deviceAppInfo[row.record.packageName]?.exactBuildVerified == true -> {
                                            val relevantUpdates = updateInfoByPackage[row.record.packageName]
                                                ?.sources.orEmpty()
                                                .filter { src -> src.hasRelevantChanges &&
                                                    !src.latestAvailableVersion.isNullOrBlank() }
                                                .joinToString(" · ") { src ->
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
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(stringResource(deviceLabel(row.status)),
                                        style = MorpheTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
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
                                        if (row.status == DeviceOverviewStatus.APP_UPDATE &&
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
