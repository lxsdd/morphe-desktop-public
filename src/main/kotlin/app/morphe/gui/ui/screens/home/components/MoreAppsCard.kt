/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheAlertDialog
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.appCardColors
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Optional upstream 2.2 discovery affordance, not an "update available" or
 * "supported version" listing. No community source is installed automatically.
 */
@Composable
internal fun MoreAppsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appCardColors()
    val fills = LocalCardFills.current
    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = MorpheTheme.shapes.mediumDp,
        fill = fills.globalFill,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorpheAppAvatar(
                name = "+",
                size = MorpheAvatarSize.ROW,
                onGradient = !colors.isMonochrome,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.home_more_apps_title),
                    style = MorpheTheme.typography.titleMedium.copy(color = colors.title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.home_more_apps_subtitle),
                    style = MorpheTheme.typography.labelSmall.copy(color = colors.subtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Confirmation is mandatory before first opening the external community site. */
@Composable
internal fun CommunityPatchesNoticeDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.community_patches_notice_title)) },
        text = {
            Text(
                text = stringResource(Res.string.community_patches_notice_message),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            MorpheButton(
                label = stringResource(Res.string.open),
                onClick = onConfirm,
                variant = MorpheButtonVariant.FILLED,
                size = MorpheButtonSize.COMPACT,
            )
        },
        dismissButton = {
            MorpheButton(
                label = stringResource(Res.string.cancel),
                onClick = onDismiss,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
            )
        },
    )
}

/** Never insert a community advertisement into device, update or filtered app lists. */
internal fun shouldOfferCommunityPatches(
    enabled: Boolean,
    filter: AppListFilter,
    search: String,
    loading: Boolean,
    error: String?,
    supportedCount: Int,
): Boolean = enabled && filter == AppListFilter.ALL && search.isBlank() &&
    !loading && error == null && supportedCount > 0
