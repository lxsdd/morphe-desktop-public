/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheBanner
import app.morphe.gui.ui.components.MorpheBannerAction
import app.morphe.gui.ui.components.MorpheBannerDismiss
import app.morphe.gui.ui.components.MorpheBannerText
import app.morphe.gui.ui.components.MorpheBannerTone
import app.morphe.gui.ui.components.MorpheBanners
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import app.morphe.gui.ui.components.UpdateBanner
import app.morphe.gui.ui.screens.home.HomeUiState

@Composable
internal fun HomeBanners(
    uiState: HomeUiState,
    onDismissUpdateSession: () -> Unit,
    onDismissUpdateVersion: () -> Unit,
    onRetryUpdateCheck: () -> Unit,
    onDismissUpdateCheckFailure: () -> Unit,
    onDismissMultiSourceHint: () -> Unit,
    onManageSources: () -> Unit,
    onDismissSourcesFailed: () -> Unit,
    modifier: Modifier = Modifier,
    onShowChangelog: (() -> Unit)? = null,
) {
    if (uiState.showUpdateBanner ||
        uiState.showCustomUpdateCheckFailure ||
        uiState.showMultiSourceHint ||
        uiState.showSourcesFailedBanner
    ) {
        Column(modifier = modifier) {
            MorpheBanners {
                if (uiState.showCustomUpdateCheckFailure) {
                    MorpheBanner(
                        tone = MorpheBannerTone.Info,
                        icon = MorpheIcons.Warning,
                    ) {
                        MorpheBannerText(
                            text = stringResource(Res.string.custom_desktop_update_check_unavailable),
                            modifier = Modifier.weight(1f),
                        )
                        MorpheBannerAction(
                            label = stringResource(Res.string.retry),
                            onClick = onRetryUpdateCheck,
                        )
                        MorpheBannerDismiss(onClick = onDismissUpdateCheckFailure)
                    }
                }
                if (uiState.showUpdateBanner) {
                    UpdateBanner(
                        info = uiState.updateInfo!!,
                        onDismissForSession = onDismissUpdateSession,
                        onDismissForVersion = onDismissUpdateVersion,
                        onShowChangelog = onShowChangelog,
                    )
                }
                if (uiState.showMultiSourceHint) {
                    MultiSourceHintBanner(
                        onDismiss = onDismissMultiSourceHint,
                    )
                }
                if (uiState.showSourcesFailedBanner) {
                    SourcesFailedBanner(
                        count = uiState.failedSourcesCount,
                        onlyLegacySourcesFailed = uiState.onlyLegacySourcesFailed,
                        onManageSources = onManageSources,
                        onDismiss = onDismissSourcesFailed,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
