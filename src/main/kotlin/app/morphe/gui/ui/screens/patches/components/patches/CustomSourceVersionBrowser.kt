/*
 * Morphe Desktop Custom — integrated upstream 2.3-inspired source version browser.
 * The two panes share the existing Custom PatchesViewModel and safe bottom actions.
 */
package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.ReleaseChannel
import app.morphe.engine.model.Release
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.ui.components.FormattedReleaseNotes
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.screens.patches.PatchesUiState
import app.morphe.gui.ui.screens.patches.sourcePinnedReleaseMissing
import app.morphe.gui.ui.screens.patches.sourceReleaseIsInUse
import app.morphe.gui.ui.screens.patches.visibleSourceReleases
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Single source-scoped version picker: one channel switch and one search input. */
@Composable
internal fun CustomVersionRail(
    state: PatchesUiState,
    onChannelSelect: (ReleaseChannel) -> Unit,
    onVersionQueryChange: (String) -> Unit,
    onReleaseSelect: (Release) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(
        state.stableReleases, state.devReleases, state.offlineReleases,
        state.isOffline, state.selectedChannel, state.versionQuery,
    ) { visibleSourceReleases(state) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.selectedRelease?.tagName, shown) {
        val target = shown.indexOfFirst { it.tagName == state.selectedRelease?.tagName }
        if (target >= 0 && target !in listState.layoutInfo.visibleItemsInfo.map { it.index }) {
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = state.sourceName?.takeIf { it.isNotBlank() }
                ?: stringResource(Res.string.custom_versions_source_unknown),
            style = MorpheTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sourcePinnedReleaseMissing(state)) {
            Text(
                text = stringResource(Res.string.custom_versions_pinned_missing, state.inUseTag.orEmpty()),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!state.isOffline) {
            ChannelSelector(
                selectedChannel = state.selectedChannel,
                onChannelSelected = onChannelSelect,
                stableCount = state.stableReleases.size,
                devCount = state.devReleases.size,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = state.versionQuery,
            onValueChange = onVersionQueryChange,
            label = { Text(stringResource(Res.string.custom_versions_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.versionQuery.isNotBlank() && !state.isOffline) {
            Text(
                text = stringResource(Res.string.custom_versions_search_both),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.custom_versions_search_no_match),
                    style = MorpheTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val step = when (event.key) {
                                Key.DirectionDown -> 1
                                Key.DirectionUp -> -1
                                else -> return@onPreviewKeyEvent false
                            }
                            val current = shown.indexOfFirst { it.tagName == state.selectedRelease?.tagName }
                            val target = (current + step).coerceIn(0, shown.lastIndex)
                            shown.getOrNull(target)?.let(onReleaseSelect)
                            true
                        }
                        .focusable(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { it.tagName }) { release ->
                        CustomVersionRailRow(
                            release = release,
                            isSelected = release.tagName == state.selectedRelease?.tagName,
                            isInUse = sourceReleaseIsInUse(state, release),
                            isCached = release.tagName in state.cachedReleaseVersions,
                            onClick = {
                                focusRequester.requestFocus()
                                onReleaseSelect(release)
                            },
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                    style = MorpheScrollbarStyle(),
                )
            }
        }
    }
}

@Composable
private fun CustomVersionRailRow(
    release: Release,
    isSelected: Boolean,
    isInUse: Boolean,
    isCached: Boolean,
    onClick: () -> Unit,
) {
    val selectedBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(
                if (isSelected) selectedBg else MaterialTheme.colorScheme.surface,
                MorpheTheme.shapes.small,
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                shape = MorpheTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            release.tagName,
            style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isInUse) {
                MorpheBadge(text = stringResource(Res.string.custom_versions_in_use), tone = MorpheBadgeTone.Success)
            }
            if (release.isDevRelease()) {
                MorpheBadge(text = stringResource(Res.string.version_label_prerelease), tone = MorpheBadgeTone.Neutral)
            }
            if (isCached) {
                MorpheBadge(text = stringResource(Res.string.patches_cached_badge))
            }
        }
    }
}

/** One detail area; actual download/use/export actions remain in the existing bottom bar. */
@Composable
internal fun CustomVersionDetail(
    state: PatchesUiState,
    modifier: Modifier = Modifier,
) {
    val release = state.selectedRelease
    if (release == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.custom_versions_select_to_view),
                style = MorpheTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = release.tagName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sourceReleaseIsInUse(state, release)) {
                MorpheBadge(text = stringResource(Res.string.custom_versions_in_use), tone = MorpheBadgeTone.Success)
            }
            if (release.isDevRelease()) {
                MorpheBadge(text = stringResource(Res.string.version_label_prerelease), tone = MorpheBadgeTone.Neutral)
            }
            if (release.tagName in state.cachedReleaseVersions) {
                MorpheBadge(text = stringResource(Res.string.patches_cached_badge))
            }
        }
        val status = when (state.sourceTrackingMode) {
            FollowMode.PINNED -> stringResource(Res.string.custom_versions_tracking_pinned)
            FollowMode.FOLLOW_DEV -> stringResource(Res.string.custom_versions_tracking_dev)
            FollowMode.FOLLOW_STABLE -> stringResource(Res.string.custom_versions_tracking_stable)
            null -> null
        }
        if (status != null && state.sourceId != null) {
            Text(
                text = status,
                style = MorpheTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isOffline) {
            Text(
                text = stringResource(Res.string.custom_versions_offline_warning),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        release.publishedAt?.takeIf { it.isNotBlank() }?.let { published ->
            Text(
                text = stringResource(Res.string.custom_versions_published, published.substringBefore('T')),
                style = MorpheTheme.typography.labelMedium,
            )
        }
        release.assets.firstOrNull { it.isPatchFile() }?.let { asset ->
            Text(
                text = "${asset.name} · ${FormatUtils.formatFileSize(asset.size, currentLocale())}",
                style = MorpheTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        Text(
            text = stringResource(Res.string.custom_versions_release_notes),
            style = MorpheTheme.typography.titleMedium,
        )
        val body = release.body?.takeIf { it.isNotBlank() }
        if (body == null) {
            Text(
                text = stringResource(Res.string.custom_versions_no_release_notes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MorpheTheme.typography.bodyMedium,
            )
        } else {
            FormattedReleaseNotes(markdown = body)
        }
    }
}
