/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.AdbManager
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.result.components.OutputFileCard
import app.morphe.gui.ui.screens.result.components.AdbInstallSection
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.DevicePackageMutations
import app.morphe.gui.util.captureOperationTarget
import app.morphe.gui.util.forSerial
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.getUserMessage
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun CompletedContent(
    outputPath: String,
    onPatchAnother: () -> Unit
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
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    val completedScroll = rememberScrollState()

    fun installOnDevice(requested: DeviceOperationTarget? = null) {
        if (deployments.values.any { it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING }) return
        val target = requested ?: DeviceMonitor.state.value.captureOperationTarget() ?: return
        if (DeviceMonitor.state.value.devices.none { it.id == target.serial && it.isReady }) return
        activeInstallTarget = target
        val previous = deployments.forSerial(target.serial)
        deployments = deployments + (target.serial to previous.installing("Installing on ${target.displayName}"))
        scope.launch {
            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = target.serial,
                onProgress = { line ->
                    deployments = deployments + (target.serial to
                        deployments.forSerial(target.serial).installing(line))
                },
            )
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
                    label = stringResource(Res.string.quick_patch_completed_adb_off),
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
                    onInstallClick = { installOnDevice() },
                    onRetryClick = { installOnDevice(activeInstallTarget) },
                    onDismissError = {
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
                label = stringResource(Res.string.quick_patch_patch_another_button),
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
}
