/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.LocalNavController
import app.morphe.gui.ui.components.MorpheBanners
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.ScreenErrorDialog
import app.morphe.gui.ui.screens.patches.components.patches.*
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Screen for selecting which patch bundle version to use. */
@Composable
fun PatchesScreen(
    apkName: String,
    viewModel: PatchesViewModel = koinViewModel { parametersOf(apkName) }
) {
    PatchesScreenContent(viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PatchesScreenContent(viewModel: PatchesViewModel) {
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    ScreenErrorDialog(
        error = uiState.error,
        title = stringResource(Res.string.patches_dialog_error_title),
        onDismiss = { viewModel.clearError() },
        onRetry = { viewModel.loadReleases() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PatchesHeader(
            apkName = viewModel.getApkName(),
            isLocalSource = uiState.isLocalSource,
            isLoading = uiState.isLoading,
            onBackClick = { navController.popBackStack() },
            onRefreshClick = { viewModel.loadReleases() }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLocalSource) {
                LocalSourceBanner(
                    patchFile = uiState.downloadedPatchFile,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                if (uiState.isOffline && uiState.currentReleases.isNotEmpty()) {
                    MorpheBanners(inset = 16.dp) {
                        OfflineBanner(onRetry = { viewModel.loadReleases() })
                    }
                }
            }

            when {
                uiState.isLocalSource -> {
                    Spacer(modifier = Modifier.weight(1f))
                }
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(Res.string.patches_fetching_releases),
                                style = MorpheTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                uiState.stableReleases.isEmpty() && uiState.devReleases.isEmpty() && uiState.offlineReleases.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(Res.string.patches_no_releases_found),
                                style = MorpheTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            MorpheButton(
                                onClick = { viewModel.loadReleases() },
                                label = stringResource(Res.string.retry),
                                variant = MorpheButtonVariant.OUTLINED,
                                size = MorpheButtonSize.COMPACT,
                            )
                        }
                    }
                }
                else -> {
                    // Single 2.3-inspired version browser. No duplicate download/use controls:
                    // the original Custom BottomActionBar below remains the only action path.
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        if (maxWidth >= 800.dp) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                CustomVersionRail(
                                    state = uiState,
                                    onChannelSelect = viewModel::setChannel,
                                    onVersionQueryChange = viewModel::setVersionQuery,
                                    onReleaseSelect = viewModel::selectRelease,
                                    modifier = Modifier.width(336.dp).fillMaxHeight(),
                                )
                                VerticalDivider()
                                CustomVersionDetail(
                                    state = uiState,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        } else {
                            // Compact windows retain both navigation and release details.
                            Column(modifier = Modifier.fillMaxSize()) {
                                CustomVersionRail(
                                    state = uiState,
                                    onChannelSelect = viewModel::setChannel,
                                    onVersionQueryChange = viewModel::setVersionQuery,
                                    onReleaseSelect = viewModel::selectRelease,
                                    modifier = Modifier.weight(0.53f).fillMaxWidth(),
                                )
                                HorizontalDivider()
                                CustomVersionDetail(
                                    state = uiState,
                                    modifier = Modifier.weight(0.47f).fillMaxWidth(),
                                )
                            }
                        }
                    }

                    val exportOptionsTitle = stringResource(Res.string.patches_export_options_title)
                    BottomActionBar(
                        uiState = uiState,
                        onDownloadClick = { viewModel.downloadPatches() },
                        onSelectClick = {
                            viewModel.confirmSelection {
                                navController.popBackStack()
                            }
                        },
                        onExportJsonClick = {
                            scope.launch {
                                val dest = MorpheFilePicker.saveFile(
                                    title = exportOptionsTitle,
                                    baseName = "options",
                                    extension = "json",
                                ) ?: return@launch
                                viewModel.exportOptionsJson(dest)
                            }
                        },
                    )

                }
            }
        }
    }
}
