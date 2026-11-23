/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.engine.CacheManager
import app.morphe.engine.MorpheData
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.MorpheAlertDialog
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.AvatarLoader
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import java.io.File
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Explicitly enumerated folders. Never allow arbitrary paths from UI inputs to be deleted. */
private enum class StorageKind { PATCHES, LOGS, SCRATCH, AVATARS }

/**
 * 2.2-inspired granular cache management in the existing Custom Settings modal.
 * Only a confirmed click can delete caches; no signer pins, personal APKs or
 * settings are in scope. Actions are disabled during patching.
 */
@Composable
internal fun StorageOverview(
    enabled: Boolean,
    borderColor: Color,
) {
    val patchSources: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var stats by remember { mutableStateOf<Map<StorageKind, CacheManager.DirStats>>(emptyMap()) }
    var legacyIconStats by remember { mutableStateOf<CacheManager.DirStats?>(null) }
    var confirm by remember { mutableStateOf<StorageKind?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<Pair<StorageKind, Boolean>?>(null) }

    suspend fun refresh() {
        val (categories, iconCache) = withContext(Dispatchers.IO) {
            val categories = mapOf(
                StorageKind.PATCHES to CacheManager.getPatchDownloadStats(MorpheData.patchesDir),
                StorageKind.LOGS to CacheManager.getDirectoryStats(MorpheData.logsDir),
                StorageKind.SCRATCH to CacheManager.getDirectoryStats(WorkspaceManager.scratchDir),
                StorageKind.AVATARS to CacheManager.getDirectoryStats(MorpheData.avatarsDir),
            )
            // Legacy full-Custom app-icon cache is diagnostic only. Do not create its directory
            // or expose a deletion action. The newer launcher renderer uses memory-only icons.
            categories to CacheManager.getDirectoryStats(File(MorpheData.root, "app-icons"))
        }
        stats = categories
        legacyIconStats = iconCache
    }

    LaunchedEffect(Unit) { refresh() }

    SectionLabel(
        text = stringResource(Res.string.settings_section_storage),
        icon = MorpheIcons.LayersClear,
    )
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StorageKind.entries.forEach { kind ->
            val label = when (kind) {
                StorageKind.PATCHES -> stringResource(Res.string.settings_cache_category_patches)
                StorageKind.LOGS -> stringResource(Res.string.settings_cache_category_logs)
                StorageKind.SCRATCH -> stringResource(Res.string.settings_cache_category_temp)
                StorageKind.AVATARS -> stringResource(Res.string.settings_cache_category_avatars)
            }
            val values = stats[kind] ?: CacheManager.DirStats(0L, 0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MorpheTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            Res.string.settings_storage_category_summary,
                            FormatUtils.formatFileSize(values.bytes, locale),
                            values.fileCount,
                        ),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                MorpheButton(
                    label = stringResource(Res.string.clear),
                    onClick = { confirm = kind },
                    enabled = enabled && !busy && values.fileCount > 0,
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                )
            }
            if (kind != StorageKind.AVATARS) SettingsDivider(borderColor)
        }
        // Preserve the previous full-Custom fifth read-only cache category.
        // It must never be sent to clearDirectoryContents or other deletion functions.
        SettingsDivider(borderColor)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_cache_category_app_icons),
                    style = MorpheTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = legacyIconStats?.let { value ->
                        stringResource(
                            Res.string.settings_storage_category_summary,
                            FormatUtils.formatFileSize(value.bytes, locale),
                            value.fileCount,
                        )
                    } ?: stringResource(Res.string.settings_storage_scan_pending),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        lastResult?.let { (kind, ok) ->
            val title = when (kind) {
                StorageKind.PATCHES -> stringResource(Res.string.settings_cache_category_patches)
                StorageKind.LOGS -> stringResource(Res.string.settings_cache_category_logs)
                StorageKind.SCRATCH -> stringResource(Res.string.settings_cache_category_temp)
                StorageKind.AVATARS -> stringResource(Res.string.settings_cache_category_avatars)
            }
            Text(
                text = if (ok) stringResource(Res.string.settings_cache_category_cleared, title)
                    else stringResource(Res.string.settings_cache_category_clear_failed, title),
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                style = MorpheTheme.typography.bodySmall,
            )
        }
    }

    confirm?.let { kind ->
        val title = when (kind) {
            StorageKind.PATCHES -> stringResource(Res.string.settings_cache_confirm_patches_title)
            StorageKind.LOGS -> stringResource(Res.string.settings_cache_confirm_logs_title)
            StorageKind.SCRATCH -> stringResource(Res.string.settings_cache_confirm_temp_title)
            StorageKind.AVATARS -> stringResource(Res.string.settings_cache_confirm_avatars_title)
        }
        val description = when (kind) {
            StorageKind.PATCHES -> stringResource(Res.string.settings_cache_confirm_patches_message)
            StorageKind.LOGS -> stringResource(Res.string.settings_cache_confirm_logs_message)
            StorageKind.SCRATCH -> stringResource(Res.string.settings_cache_confirm_temp_message)
            StorageKind.AVATARS -> stringResource(Res.string.settings_cache_confirm_avatars_message)
        }
        MorpheAlertDialog(
            onDismiss = { confirm = null },
            title = { Text(title) },
            text = { Text(description) },
            confirmButton = {
                MorpheButton(
                    label = stringResource(Res.string.clear),
                    onClick = {
                        confirm = null
                        if (!enabled || busy) return@MorpheButton
                        scope.launch {
                            busy = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    when (kind) {
                                        StorageKind.PATCHES -> CacheManager.clearPatchDownloads("Patches", MorpheData.patchesDir)
                                        StorageKind.LOGS -> CacheManager.clearDirectoryContents("Logs", MorpheData.logsDir)
                                        StorageKind.SCRATCH -> WorkspaceManager.clearScratch()
                                        StorageKind.AVATARS -> CacheManager.clearDirectoryContents("Avatars", MorpheData.avatarsDir)
                                    }
                                }
                                val success = result.failedFiles == 0
                                if (success && kind == StorageKind.PATCHES) patchSources.notifyCacheCleared()
                                if (success && kind == StorageKind.AVATARS) AvatarLoader.clearMemoryCache()
                                lastResult = kind to success
                                refresh()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = enabled && !busy,
                    variant = MorpheButtonVariant.DANGER,
                    size = MorpheButtonSize.COMPACT,
                )
            },
            dismissButton = {
                MorpheButton(
                    label = stringResource(Res.string.cancel),
                    onClick = { confirm = null },
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                )
            },
        )
    }
}
