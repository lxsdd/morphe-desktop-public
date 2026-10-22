/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Read-only inventory of ALL packages returned by the exact selected ADB device,
 * not only Morphe-patched or currently supported applications.
 * No install/uninstall action can be triggered from this inventory.
 */
@Composable
internal fun DeviceInventoryPane(
    installedPackages: Set<String>,
    selectedDeviceSerial: String?,
    supportedApps: List<SupportedApp>,
    patchedRecords: List<PatchedAppRecord>,
    deviceAppInfo: Map<String, DeviceAppInfo>,
    searchQuery: String,
    paneMaxHeight: Dp,
) {
    if (selectedDeviceSerial == null) {
        YourAppsEmptyHint(
            title = stringResource(Res.string.home_filter_device_apps),
            subtitle = stringResource(Res.string.home_device_inventory_unavailable),
        )
        return
    }
    val supportedByPackage = remember(supportedApps) { supportedApps.associateBy { it.packageName } }
    val recordsByInstalled = remember(patchedRecords) { patchedRecords.associateBy { it.installedPackageName } }
    val visible = remember(installedPackages, searchQuery, supportedByPackage, recordsByInstalled) {
        installedPackages.asSequence().map { pkg ->
            val title = recordsByInstalled[pkg]?.displayName ?: supportedByPackage[pkg]?.displayName ?: pkg
            pkg to title
        }.filter { (pkg, title) ->
            searchQuery.isBlank() ||
                pkg.contains(searchQuery, ignoreCase = true) ||
                title.contains(searchQuery, ignoreCase = true)
        }.sortedWith(compareBy<Pair<String, String>>({ it.second.lowercase() }, { it.first.lowercase() })).toList()
    }
    if (visible.isEmpty()) {
        YourAppsEmptyHint(
            title = stringResource(Res.string.no_matches),
            subtitle = if (searchQuery.isBlank()) stringResource(Res.string.home_device_inventory_empty)
                else stringResource(Res.string.home_your_apps_no_matches_subtitle, searchQuery),
        )
        return
    }
    val listState = rememberLazyListState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.home_device_inventory_title),
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = (paneMaxHeight - 130.dp).coerceAtLeast(120.dp)),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visible, key = { it.first }) { (pkg, name) ->
                    val record = recordsByInstalled[pkg]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(name, fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            if (name != pkg) {
                                Text(pkg, style = MorpheTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (record != null) {
                                Text(stringResource(Res.string.home_device_inventory_patched_record),
                                    style = MorpheTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                                deviceAppInfo[record.packageName]?.installedVersion?.let { version ->
                                    Text(stringResource(Res.string.home_device_installed_version, "v" + version.removePrefix("v")),
                                        style = MorpheTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
