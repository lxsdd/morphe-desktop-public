/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.AdbManager
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.result.components.OutputFileCard
import app.morphe.gui.ui.screens.result.components.AdbInstallSection
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.DeviceInstallConfirmationGate
import app.morphe.gui.util.DeviceInstallConfirmationRequest
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.DevicePackageMutations
import app.morphe.gui.util.captureOperationTarget
import app.morphe.gui.util.forSerial
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.OwnerMigrationUiState
import app.morphe.gui.util.UpdateOwnerMigrationResult
import app.morphe.gui.util.getUserMessage
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SimpleResultContent(
    outputPath: String,
    onPatchAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outputFile = remember(outputPath) { File(outputPath) }
    val monitorState by DeviceMonitor.state.collectAsState()
    val adbPreference = LocalAdbPreference.current
    val isAdbDisabledByUser = !adbPreference.enabled
    var deployments by remember(outputPath) {
        mutableStateOf<Map<String, DeviceDeploymentState>>(emptyMap())
    }
    var activeInstallTarget by remember(outputPath) { mutableStateOf<DeviceOperationTarget?>(null) }
    val adbManager = remember { AdbManager() }
    val migration = remember(outputPath) { OwnerMigrationUiState(adbManager) }
    var installConfirmation by remember(outputPath) { mutableStateOf<DeviceInstallConfirmationRequest?>(null) }
    var preparingConfirmation by remember(outputPath) { mutableStateOf(false) }
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    val completedScroll = rememberScrollState()

    fun requestInstallConfirmation(requested: DeviceOperationTarget? = null) {
        if (preparingConfirmation || installConfirmation != null || migration.busy ||
            deployments.values.any { it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING }) return
        val target = requested ?: DeviceMonitor.state.value.captureOperationTarget() ?: return
        if (DeviceMonitor.state.value.captureOperationTarget()?.serial != target.serial) return
        preparingConfirmation = true
        scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    DeviceInstallConfirmationGate.prepare(outputFile, target)
                }
                if (DeviceMonitor.state.value.captureOperationTarget()?.serial != target.serial) {
                    deployments = deployments + (target.serial to deployments.forSerial(target.serial)
                        .installFailed("Device selection changed before confirmation."))
                } else {
                    prepared.fold(
                        onSuccess = { installConfirmation = it },
                        onFailure = { err ->
                            deployments = deployments + (target.serial to deployments.forSerial(target.serial)
                                .installFailed(err.message ?: "Could not verify the selected APK."))
                        },
                    )
                }
            } finally { preparingConfirmation = false }
        }
    }

    fun installOnDevice(requested: DeviceOperationTarget, expectedPackageName: String) {
        if (migration.busy || deployments.values.any { it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING }) return
        val target = requested
        if (DeviceMonitor.state.value.captureOperationTarget()?.serial != target.serial ||
            DeviceMonitor.state.value.devices.none { it.id == target.serial && it.isReady }) return
        migration.clear()
        activeInstallTarget = target
        val previous = deployments.forSerial(target.serial)
        deployments = deployments + (target.serial to previous.installing("Installing on ${target.displayName}"))
        scope.launch {
            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = target.serial,
                expectedPackageName = expectedPackageName,
                onProgress = { line ->
                    deployments = deployments + (target.serial to
                        deployments.forSerial(target.serial).installing(line))
                },
            )
            if (result.isFailure) {
                migration.recordFailure(result.exceptionOrNull(), target, outputPath)
            }
            result.fold(
                onSuccess = {
                    deployments = deployments + (target.serial to
                        deployments.forSerial(target.serial).installed("Installed on ${target.displayName}"))
                    val record = PatchedAppStore.shared.getAll()
                        .firstOrNull { it.outputApkPath == outputPath }
                    record?.let { DevicePackageMutations.notify(target.serial, it.installedPackageName) }
                    val config = engineConfigRepository.loadConfig()
                    if (config.autoRouteLinksAfterInstall && record != null) {
                        // The user's selection may have changed in the meantime. Only
                        // configure links for the successful installation target.
                        adbManager.setLinkHandling(
                            deviceId = target.serial,
                            patchedPackage = record.installedPackageName,
                            stockPackage = if (config.disableStockLinksAfterInstall)
                                record.packageName else null,
                            enable = true,
                        )
                    }
                },
                onFailure = { error ->
                    deployments = deployments + (target.serial to
                        deployments.forSerial(target.serial).installFailed(
                            (error as? AdbException)?.getUserMessage()
                                ?: error.message ?: "Installation failed"))
                },
            )
        }
    }

    // No device-specific state may be derived from the selected device's display name.
    LaunchedEffect(outputPath, monitorState.selectedDevice?.id, monitorState.selectedDevice?.isReady) {
        val target = monitorState.captureOperationTarget() ?: return@LaunchedEffect
        val previous = deployments.forSerial(target.serial)
        if (previous.installPhase == DeviceDeploymentState.InstallPhase.INSTALLED ||
            previous.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING) return@LaunchedEffect
        deployments = deployments + (target.serial to previous.checking())
        val pkg = PatchedAppStore.shared.getAll()
            .firstOrNull { it.outputApkPath == outputPath }?.installedPackageName
        val installed = pkg?.let { adbManager.listInstalledPackages(target.serial).getOrNull()?.contains(it) }
        if (DeviceMonitor.state.value.captureOperationTarget()?.serial != target.serial) return@LaunchedEffect
        deployments = deployments + (target.serial to if (installed == null)
            previous.copy(installPhase = DeviceDeploymentState.InstallPhase.IDLE, installed = null)
        else previous.observed(installed))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(completedScroll)
                .padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.status_patching_completed),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutputFileCard(
                outputFile = outputFile,
                showFolderPath = false,
                modifier = Modifier.widthIn(max = 480.dp),
            )

            if (isAdbDisabledByUser) {
                Spacer(modifier = Modifier.height(12.dp))
                MorpheButton(
                    label = stringResource(Res.string.result_adb_off_button),
                    variant = MorpheButtonVariant.PRIMARY,
                    size = MorpheButtonSize.DEFAULT,
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                    onClick = { adbPreference.onChange(true) }
                )
            } else if (monitorState.isAdbAvailable == true) {
                Spacer(modifier = Modifier.height(12.dp))
                AdbInstallSection(
                    devices = monitorState.devices,
                    selectedDevice = monitorState.selectedDevice,
                    deployments = deployments,
                    installTarget = activeInstallTarget,
                    onDeviceSelected = { DeviceMonitor.selectDevice(it) },
                    onInstallClick = { requestInstallConfirmation() },
                    onRetryClick = { requestInstallConfirmation(activeInstallTarget) },
                    migrationAvailable = migration.request?.deviceSerial == monitorState.selectedDevice?.id,
migrationBusy = migration.busy || preparingConfirmation || installConfirmation != null,
                    onMigrationClick = { migration.showDialog() },
                    onDismissError = {
                        migration.clear()
                        activeInstallTarget?.let { target ->
                            val current = deployments.forSerial(target.serial)
                            deployments = deployments + (target.serial to current.copy(
                                installError = null,
                                installPhase = if (current.installed == true)
                                    DeviceDeploymentState.InstallPhase.PRESENT
                                else DeviceDeploymentState.InstallPhase.IDLE,
                            ))
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            MorpheButton(
                label = stringResource(Res.string.result_screen_patch_another_button),
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.LARGE,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                onClick = onPatchAnother
            )
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(completedScroll),
            style = MorpheScrollbarStyle(),
        )
    }

    val pendingInstall = installConfirmation
    if (pendingInstall != null) {
        DeviceInstallConfirmationDialog(
            request = pendingInstall,
            stillSelectedAndReady =
                DeviceMonitor.state.value.captureOperationTarget()?.serial == pendingInstall.target.serial,
            previouslyInstalled = deployments.forSerial(pendingInstall.target.serial).installed == true,
            onDismiss = { if (!preparingConfirmation) installConfirmation = null },
            onConfirm = {
                if (!preparingConfirmation) {
                    preparingConfirmation = true
                    installConfirmation = null
                    scope.launch {
                        try {
                            val selected = DeviceMonitor.state.value.captureOperationTarget()
                            val verified = withContext(Dispatchers.IO) {
                                DeviceInstallConfirmationGate.stillMatches(pendingInstall, selected)
                            }
                            if (!verified ||
                                DeviceMonitor.state.value.captureOperationTarget()?.serial != pendingInstall.target.serial) {
                                deployments = deployments + (pendingInstall.target.serial to
                                    deployments.forSerial(pendingInstall.target.serial).installFailed(
                                        "APK or device changed. No installation was started."))
                            } else {
                                installOnDevice(pendingInstall.target, pendingInstall.packageName)
                            }
                        } finally { preparingConfirmation = false }
                    }
                }
            },
        )
    }

    val pendingMigration = migration.request
    if (migration.dialogVisible && pendingMigration != null) {
        UpdateOwnerMigrationDialog(
            request = pendingMigration,
            isBusy = migration.busy,
            error = migration.error,
            onDismiss = { migration.dismiss() },
            onConfirm = {
                if (migration.beginConfirmation()) scope.launch {
                    when (val outcome = migration.confirm(
                        DeviceMonitor.state.value.captureOperationTarget()?.serial
                    )) {
                        UpdateOwnerMigrationResult.Success -> {
                            migration.clear()
                            val target = DeviceOperationTarget(
                                pendingMigration.deviceSerial, pendingMigration.deviceName
                            )
                            deployments = deployments + (target.serial to
                                deployments.forSerial(target.serial).installed(
                                    "Installed on ${target.displayName} after confirmed migration"
                                ))
                            DevicePackageMutations.notify(target.serial, pendingMigration.packageName)
                            val record = PatchedAppStore.shared.getAll()
                                .firstOrNull { it.outputApkPath == outputPath }
                            val config = engineConfigRepository.loadConfig()
                            if (config.autoRouteLinksAfterInstall && record != null) {
                                adbManager.setLinkHandling(
                                    deviceId = target.serial,
                                    patchedPackage = pendingMigration.packageName,
                                    stockPackage = if (config.disableStockLinksAfterInstall)
                                        record.packageName else null,
                                    enable = true,
                                )
                            }
                        }
                        is UpdateOwnerMigrationResult.Blocked -> migration.reportError(outcome.message)
                        is UpdateOwnerMigrationResult.UninstallFailed -> {
                            migration.reportError(outcome.message)
                            deployments = deployments + (pendingMigration.deviceSerial to
                                deployments.forSerial(pendingMigration.deviceSerial).installFailed(outcome.message))
                        }
                        is UpdateOwnerMigrationResult.ReinstallFailed -> {
                            migration.reportError(outcome.message)
                            deployments = deployments + (pendingMigration.deviceSerial to
                                deployments.forSerial(pendingMigration.deviceSerial).installFailed(outcome.message))
                        }
                    }
                }
            },
        )
    }
}
