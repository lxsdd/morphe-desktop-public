/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Display-only evidence from the historical record; never claims a new update. */
internal data class IncompleteCheckDescription(
    val appName: String,
    val sourceName: String?,
    val reason: String,
)

/**
 * Compact by default. Full, unellipsized historical reasons are visible on demand
 * in a bounded independent scroll viewport, with actions outside that viewport.
 */
@Composable
internal fun IncompleteChecksNotice(
    entries: List<IncompleteCheckDescription>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    isLoading: Boolean,
    showManageSources: Boolean,
    onRetry: () -> Unit,
    onManageSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    val names = remember(entries) {
        entries.take(3).joinToString(" · ") { it.appName } +
            if (entries.size > 3) " · …" else ""
    }
    val detailScrollState = rememberScrollState()
    Surface(
        modifier = modifier,
        shape = MorpheTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    ) {
        BoxWithConstraints {
            val narrow = maxWidth < 540.dp
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                Res.string.home_update_checks_incomplete_intro, entries.size
                            ),
                            style = MorpheTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = true,
                        )
                        Text(
                            text = names,
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis, // Summary only; not the full reasons.
                        )
                    }
                    TextButton(onClick = onToggleExpanded) {
                        Text(
                            if (expanded) stringResource(Res.string.home_update_checks_hide_details)
                            else stringResource(Res.string.home_update_checks_show_details)
                        )
                    }
                    if (!narrow) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                            )
                            Text(
                                stringResource(Res.string.home_update_checks_checking),
                                style = MorpheTheme.typography.labelSmall,
                            )
                        } else {
                            TextButton(onClick = onRetry) {
                                Text(stringResource(Res.string.home_update_retry_check))
                            }
                        }
                    }
                }

                if (expanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 224.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .verticalScroll(detailScrollState)
                                .padding(end = 14.dp),
                        ) {
                            entries.forEachIndexed { index, entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(
                                        modifier = Modifier.padding(top = 7.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = entry.appName,
                                            style = MorpheTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            softWrap = true,
                                        )
                                        if (!entry.sourceName.isNullOrBlank()) {
                                            Text(
                                                text = entry.sourceName,
                                                style = MorpheTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                softWrap = true,
                                            )
                                        }
                                        Text(
                                            text = entry.reason,
                                            style = MorpheTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            softWrap = true,
                                        )
                                    }
                                }
                                if (index != entries.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                    )
                                }
                            }
                        }
                        if (detailScrollState.maxValue > 0) {
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(detailScrollState),
                                style = MorpheScrollbarStyle(),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.home_update_checks_count_only_confirmed),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        softWrap = true,
                    )
                    if (expanded && showManageSources && !narrow) {
                        TextButton(onClick = onManageSources) {
                            Text(stringResource(Res.string.home_header_manage_sources_button))
                        }
                    }
                    if (narrow) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                            )
                            Text(
                                stringResource(Res.string.home_update_checks_checking),
                                style = MorpheTheme.typography.labelSmall,
                            )
                        } else {
                            TextButton(onClick = onRetry) {
                                Text(stringResource(Res.string.home_update_retry_check))
                            }
                        }
                    }
                }
                // On narrow panes keep the explanatory footer readable:
                // two actions must not squeeze the confirmation sentence.
                if (narrow && expanded && showManageSources) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onManageSources) {
                            Text(stringResource(Res.string.home_header_manage_sources_button))
                        }
                    }
                }
            }
        }
    }
}
