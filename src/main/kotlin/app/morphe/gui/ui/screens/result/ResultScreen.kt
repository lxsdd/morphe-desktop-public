/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.DevicePatchDeploymentStore
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.ApkManifestReader
import app.morphe.gui.HomeScreenRoute
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.LocalNavController
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.components.DeviceInstallConfirmationDialog
import app.morphe.gui.ui.components.UpdateOwnerMigrationDialog
import app.morphe.gui.ui.screens.result.components.*
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.screenScrim
import app.morphe.gui.util.AdbException
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.DevicePackageMutations
import app.morphe.gui.util.UpdateOwnerMigrationCoordinator
import app.morphe.gui.util.UpdateOwnerMigrationRequest
import app.morphe.gui.util.UpdateOwnerMigrationResult
import app.morphe.gui.util.captureOperationTarget
import app.morphe.gui.util.crossDeviceInstallBlockReason
import app.morphe.gui.util.forSerial
import app.morphe.gui.util.migrationRequestOrNull
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Screen showing the result of patching.
 */
@Composable
fun ResultScreen(
    outputPath: String
) {
    ResultScreenContent(outputPath = outputPath)
}

@Composable
fun ResultScreenContent(outputPath: String) {
    val navController = LocalNavController.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val configRepository: ConfigRepository = koinInject()

    // ADB state from DeviceMonitor
    val monitorState by DeviceMonitor.state.collectAsState()
    val adbPreference = LocalAdbPreference.current
    val isAdbDisabledByUser = !adbPreference.enabled
    var deployments by remember(outputPath) { mutableStateOf<Map<String, DeviceDeploymentState>>(emptyMap()) }
    var installTarget by remember { mutableStateOf<DeviceOperationTarget?>(null) }
    var pendingInstallTarget by remember { mutableStateOf<DeviceOperationTarget?>(null) }
    var migrationRequest by remember { mutableStateOf<UpdateOwnerMigrationRequest?>(null) }
    var showMigrationConfirm by remember { mutableStateOf(false) }
    var migrationBusy by remember { mutableStateOf(false) }
    var migrationError by remember { mutableStateOf<String?>(null) }

    // Whether the patched package is already on the selected device → show "Update"
    // instead of "Install" (the install itself already reinstalls with -r).
    var outputPackage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(outputPath) {
        outputPackage = withContext(Dispatchers.IO) {
            runCatching { ApkManifestReader.read(outputFile)?.packageName }.getOrNull()
        }
    }
    // Link-handling ("open with") state. The stock package, needed only for the
    // optional "stop stock from opening links" half, comes from the recall
    // record for this output (which stores original + renamed package names).
    var stockPackage by remember { mutableStateOf<String?>(null) }
    var sourceDeviceSerial by remember { mutableStateOf<String?>(null) }
    var deviceSpecificInput by remember { mutableStateOf(false) }
    var patchedRecord by remember(outputPath) { mutableStateOf<PatchedAppRecord?>(null) }
    var disableStockLinks by remember { mutableStateOf(false) }
    var isApplyingLinks by remember { mutableStateOf(false) }
    var linkProgress by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    var linkSuccess by remember { mutableStateOf(false) }
    var autoRouteLinks by remember { mutableStateOf(false) }
    LaunchedEffect(outputPath, outputPackage) {
        val record = withContext(Dispatchers.IO) {
            runCatching {
                val records = PatchedAppStore.shared.getAll()
                records.firstOrNull { it.outputApkPath == outputPath }
                    ?: outputPackage?.let { pkg -> records.firstOrNull { it.installedPackageName == pkg } }
            }.getOrNull()
        }
        stockPackage = record?.packageName
        sourceDeviceSerial = record?.sourceDeviceSerial
        deviceSpecificInput = record?.deviceSpecificInput == true
        patchedRecord = record
    }

    val readySerialsKey = monitorState.devices.filter { it.isReady }.joinToString("|") { it.id }
    LaunchedEffect(readySerialsKey, outputPackage, patchedRecord?.outputApkSha256) {
        val pkg = outputPackage ?: return@LaunchedEffect
        val currentHash = patchedRecord?.outputApkSha256?.takeIf { it.length == 64 }
        monitorState.devices.filter { it.isReady }.forEach { device ->
            val serial = device.id
            val existing = deployments.forSerial(serial)
            if (existing.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING) return@forEach
            deployments = deployments + (serial to existing.checking())
            val installed = adbManager.listInstalledPackages(serial).getOrNull()?.contains(pkg)
                ?: return@forEach
            val snapshot = if (installed) adbManager.getInstalledPackageSnapshot(serial, pkg) else null
            val installedHash = if (installed && currentHash != null) {
                adbManager.getInstalledBaseApkSha256(serial, pkg)
            } else null
            val matchesCurrent = if (currentHash != null && installedHash != null) {
                currentHash.equals(installedHash, ignoreCase = true)
            } else null
            val readyNow = DeviceMonitor.state.value.devices.any { it.id == serial && it.isReady }
            val current = deployments.forSerial(serial)
            if (readyNow && current.installPhase != DeviceDeploymentState.InstallPhase.INSTALLING) {
                deployments = deployments + (serial to current.observed(
                    isInstalled = installed,
                    version = snapshot?.versionName,
                    outputMatchesCurrent = matchesCurrent,
                ))
            }
        }
    }

    suspend fun recordSuccessfulDeployment(deviceSerial: String) {
        val record = patchedRecord ?: withContext(Dispatchers.IO) {
            PatchedAppStore.shared.getAll().firstOrNull { it.outputApkPath == outputPath }
        }
        record?.let { DevicePatchDeploymentStore.shared.recordSuccessfulInstall(deviceSerial, it) }
    }

    // Cleanup state
    var hasTempFiles by remember { mutableStateOf(false) }
    var tempFilesSize by remember { mutableStateOf(0L) }
    var tempFilesCleared by remember { mutableStateOf(false) }
    var autoCleanupEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val config = configRepository.loadConfig()
        autoCleanupEnabled = config.autoCleanupTempFiles
        autoRouteLinks = config.autoRouteLinksAfterInstall
        disableStockLinks = config.disableStockLinksAfterInstall
        hasTempFiles = FileUtils.hasTempFiles()
        tempFilesSize = FileUtils.getTempDirSize()

        if (autoCleanupEnabled && hasTempFiles) {
            FileUtils.cleanupAllTempDirs()
            hasTempFiles = false
            tempFilesCleared = true
            Logger.info("Auto-cleaned temp files after successful patching")
        }
    }

    fun installViaAdb(requestedTarget: DeviceOperationTarget? = null) {
        if (deployments.values.any { it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING }) return
        val target = requestedTarget ?: DeviceMonitor.state.value.captureOperationTarget() ?: return
        val device = DeviceMonitor.state.value.devices.firstOrNull { it.id == target.serial && it.isReady }
        if (device == null) {
            installTarget = target
            deployments = deployments + (target.serial to deployments.forSerial(target.serial).installFailed(
                "${target.displayName} is no longer connected and ready. Installation was not started.",
            ))
            return
        }
        crossDeviceInstallBlockReason(deviceSpecificInput, sourceDeviceSerial, target.serial)?.let { reason ->
            installTarget = target
            deployments = deployments + (target.serial to deployments.forSerial(target.serial).installFailed(
                "Cannot install on ${target.displayName}: $reason",
            ))
            return
        }
        val wasAlreadyInstalled = deployments.forSerial(target.serial).installed == true
        installTarget = target
        scope.launch {
            migrationRequest = null
            deployments = deployments + (target.serial to deployments.forSerial(target.serial).installing(
                if (wasAlreadyInstalled) getString(Res.string.result_adb_updating_on_device, target.displayName)
                else getString(Res.string.adb_status_installing, target.displayName),
            ))

            // Always record a non-Play installer so the Play Store won't clobber
            // the patched app with an official update.
            val installer = adbManager.resolveSpoofInstaller(target.serial)
            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = target.serial,
                installerPackage = installer,
                onProgress = { progress ->
                    deployments = deployments + (target.serial to deployments.forSerial(target.serial).installing(
                        "$progress (${target.displayName})",
                    ))
                },
            )
            if (result.isSuccess) {
                recordSuccessfulDeployment(target.serial)
                outputPackage?.let { DevicePackageMutations.notify(target.serial, it) }
            }
            deployments = deployments + (target.serial to result.fold(
                onSuccess = {
                    deployments.forSerial(target.serial).installed(
                        if (wasAlreadyInstalled) "Update successful on ${target.displayName}!"
                        else "Installation successful on ${target.displayName}!",
                    )
                },
                onFailure = { exception ->
                    migrationRequest = migrationRequestOrNull(exception, target.serial, outputPath)
                    val message = (exception as? AdbException)?.getUserMessage()
                        ?: exception.message ?: getString(Res.string.error_patching_unknown)
                    deployments.forSerial(target.serial).installFailed(
                        "Installation failed on ${target.displayName}: $message",
                    )
                },
            ))
            if (result.isSuccess && autoRouteLinks && outputPackage != null) {
                deployments = deployments + (target.serial to deployments.forSerial(target.serial).applyingLinks(
                    "Routing links on ${target.displayName}…",
                ))
                val linkResult = adbManager.setLinkHandling(
                    deviceId = target.serial,
                    patchedPackage = outputPackage!!,
                    stockPackage = if (disableStockLinks) stockPackage else null,
                    enable = true,
                )
                deployments = deployments + (target.serial to linkResult.fold(
                    onSuccess = { outcome -> deployments.forSerial(target.serial).linksConfigured(
                        if (outcome.stockChanged) getString(Res.string.result_link_routed_stock_disabled)
                        else getString(Res.string.result_screen_links_routed_label),
                    ) },
                    onFailure = { error -> deployments.forSerial(target.serial).linksFailed(
                        "Link handling failed on ${target.displayName}: ${error.message ?: getString(Res.string.error_patching_unknown)}",
                    ) },
                ))
            }
        }
    }

    fun requestInstall() {
        pendingInstallTarget = DeviceMonitor.state.value.captureOperationTarget()
    }

    fun applyLinkHandling(enable: Boolean, requestedTarget: DeviceOperationTarget? = null) {
        val target = requestedTarget ?: DeviceMonitor.state.value.captureOperationTarget() ?: return
        val patched = outputPackage ?: return
        scope.launch {
            deployments = deployments + (target.serial to deployments.forSerial(target.serial).applyingLinks())
            val result = adbManager.setLinkHandling(
                deviceId = target.serial,
                patchedPackage = patched,
                stockPackage = if (disableStockLinks) stockPackage else null,
                enable = enable,
                onProgress = { progress ->
                    deployments = deployments + (target.serial to deployments.forSerial(target.serial).applyingLinks(progress))
                },
            )
            deployments = deployments + (target.serial to result.fold(
                onSuccess = { outcome ->
                    val message = when {
                        !enable -> getString(Res.string.result_link_default_restored)
                        outcome.stockChanged -> getString(Res.string.result_link_routed_stock_disabled)
                        else -> getString(Res.string.result_screen_links_routed_label)
                    }
                    if (enable) deployments.forSerial(target.serial).linksConfigured(message)
                    else deployments.forSerial(target.serial).linksRestored(message)
                },
                onFailure = { e ->
                    val message = (e as? AdbException)?.getUserMessage()
                        ?: e.message ?: getString(Res.string.error_patching_unknown)
                    deployments.forSerial(target.serial).linksFailed(
                        "Link handling failed on ${target.displayName}: $message",
                    )
                },
            ))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenScrim)
    ) {
        ResultHeader(onBackClick = { navController.popBackStack() })

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val bodyMaxHeight = this.maxHeight
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .heightIn(min = bodyMaxHeight)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                OutputFileCard(
                    outputFile = outputFile,
                    corners = corners,
                    font = font,
                    borderColor = borderColor
                )

                // ADB Install section
                if (isAdbDisabledByUser) {
                    AdbDisabledHint(
                        corners = corners,
                        font = font,
                        borderColor = borderColor,
                        onEnableClick = { adbPreference.onChange(true) }
                    )
                } else if (monitorState.isAdbAvailable == true) {
                    AdbInstallSection(
                        devices = monitorState.devices,
                        selectedDevice = monitorState.selectedDevice,
                        deployments = deployments,
                        installTarget = installTarget,
                        corners = corners,
                        font = font,
                        borderColor = borderColor,
                        onDeviceSelected = { DeviceMonitor.selectDevice(it) },
                        onInstallClick = ::requestInstall,
                        onRetryClick = { installViaAdb(installTarget) },
                        migrationRequired = migrationRequest != null,
                        onMigrationClick = { showMigrationConfirm = true },
                        onDismissError = {
                            installTarget?.let { target ->
                                val current = deployments.forSerial(target.serial)
                                deployments = deployments + (target.serial to current.copy(
                                    installPhase = when {
                                        current.installed != true -> DeviceDeploymentState.InstallPhase.IDLE
                                        current.installedOutputMatchesCurrent == true -> DeviceDeploymentState.InstallPhase.INSTALLED
                                        else -> DeviceDeploymentState.InstallPhase.PRESENT
                                    },
                                    installError = null,
                                ))
                            }
                            migrationRequest = null
                        },
                    )

                    // Link handling ("open with"). Only meaningful once the patched
                    // app is on the device, so gate on a successful install (or the
                    // app already being present) + a ready, selected device.
                    val linkTarget = monitorState.captureOperationTarget()
                    val linkState = linkTarget?.let { deployments.forSerial(it.serial) }
                    if (outputPackage != null && linkTarget != null && linkState?.installed == true) {
                        LinkHandlingSection(
                            patchedPackage = outputPackage!!,
                            stockPackage = stockPackage?.takeIf { it != outputPackage },
                            disableStockLinks = disableStockLinks,
                            onToggleDisableStock = { disableStockLinks = it },
                            isApplying = linkState.linkPhase == DeviceDeploymentState.LinkPhase.APPLYING,
                            progress = linkState.linkMessage.orEmpty(),
                            error = linkState.linkError,
                            success = linkState.linkPhase == DeviceDeploymentState.LinkPhase.CONFIGURED,
                            selectedDeviceName = linkTarget.displayName,
                            corners = corners,
                            font = font,
                            borderColor = borderColor,
                            onApply = { applyLinkHandling(enable = true, requestedTarget = linkTarget) },
                            onRestore = { applyLinkHandling(enable = false, requestedTarget = linkTarget) },
                            onDismissError = {
                                deployments = deployments + (linkTarget.serial to linkState.copy(
                                    linkPhase = DeviceDeploymentState.LinkPhase.UNKNOWN,
                                    linkError = null,
                                ))
                            },
                        )
                    }
                }

                // Cleanup section
                if (hasTempFiles || tempFilesCleared) {
                    CleanupSection(
                        hasTempFiles = hasTempFiles,
                        tempFilesSize = tempFilesSize,
                        tempFilesCleared = tempFilesCleared,
                        autoCleanupEnabled = autoCleanupEnabled,
                        corners = corners,
                        font = font,
                        borderColor = borderColor,
                        onCleanupClick = {
                            FileUtils.cleanupAllTempDirs()
                            hasTempFiles = false
                            tempFilesCleared = true
                            Logger.info("Manually cleaned temp files after patching")
                        }
                    )
                }

                // ADB help text, only when the toggle is ON but the binary is
                // missing. When the toggle is OFF, AdbDisabledHint above carries
                // the explanation, so suppress the duplicate "ADB not found" text.
                if (!isAdbDisabledByUser && monitorState.isAdbAvailable == false) {
                    Text(
                        text = stringResource(Res.string.result_adb_not_found_hint),
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 520.dp)
                    )
                }

                // Patch Another button
                Spacer(Modifier.height(4.dp))
                PatchAnotherButton(onClick = { navController.popBackStack(HomeScreenRoute, inclusive = false) })

                Spacer(Modifier.height(8.dp))
            }

            // Show scrollbar only when content overflows
            if (scrollState.maxValue > 0) {
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = morpheScrollbarStyle()
                )
            }
        }
    }

    pendingInstallTarget?.let { target ->
        val deployment = deployments.forSerial(target.serial)
        DeviceInstallConfirmationDialog(
            appName = patchedRecord?.displayName ?: outputFile.nameWithoutExtension,
            packageName = outputPackage ?: patchedRecord?.installedPackageName ?: "Unknown package",
            apkVersion = patchedRecord?.apkVersion,
            deviceName = target.displayName,
            deviceSerial = target.serial,
            installedVersion = deployment.installedVersion,
            replacingExisting = deployment.installed == true,
            onDismiss = { pendingInstallTarget = null },
            onConfirm = {
                pendingInstallTarget = null
                installViaAdb(target)
            },
        )
    }

    if (showMigrationConfirm && migrationRequest != null) {
        UpdateOwnerMigrationDialog(
            isBusy = migrationBusy,
            error = migrationError,
            deviceName = installTarget?.displayName,
            onCancel = {
                if (!migrationBusy) {
                    showMigrationConfirm = false
                    migrationError = null
                }
            },
            onConfirm = {
                if (!migrationBusy) {
                    migrationBusy = true
                    migrationError = null
                    scope.launch {
                        val request = migrationRequest!!
                        val target = installTarget?.takeIf { it.serial == request.deviceSerial }
                            ?: DeviceOperationTarget(request.deviceSerial, request.deviceSerial)
                        when (val result = UpdateOwnerMigrationCoordinator.using(adbManager).execute(request)) {
                            UpdateOwnerMigrationResult.Success -> {
                                recordSuccessfulDeployment(target.serial)
                                deployments = deployments + (target.serial to deployments.forSerial(target.serial).installed(
                                    "Installation successful on ${target.displayName}!",
                                ))
                                migrationRequest = null
                                showMigrationConfirm = false
                                if (autoRouteLinks) applyLinkHandling(enable = true, requestedTarget = target)
                            }
                            UpdateOwnerMigrationResult.DeviceUnavailable ->
                                migrationError = "${target.displayName} is no longer connected and ready. Nothing was uninstalled."
                            is UpdateOwnerMigrationResult.UninstallFailed ->
                                migrationError = "Uninstall failed on ${target.displayName}. The patched APK was not reinstalled: ${result.message}"
                            is UpdateOwnerMigrationResult.ReinstallFailed -> {
                                deployments = deployments + (target.serial to deployments.forSerial(target.serial).installFailed(
                                    "The existing app was uninstalled from ${target.displayName}, but reinstalling the patched APK failed: ${result.message}. The patched APK remains at $outputPath",
                                ))
                                migrationRequest = null
                                showMigrationConfirm = false
                            }
                        }
                        migrationBusy = false
                    }
                }
            },
        )
    }
}

@Composable
internal fun formatFileSize(bytes: Long): String =
    FormatUtils.formatFileSize(bytes, currentLocale())
