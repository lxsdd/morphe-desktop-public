package app.morphe.gui.ui.screens.home.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import app.morphe.gui.ui.components.DeviceInstallConfirmationDialog
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceInstallConfirmationGate
import app.morphe.gui.util.DeviceInstallConfirmationRequest
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DirectExistingApkInstallService
import app.morphe.gui.util.ExistingApkInfo
import app.morphe.gui.util.ExistingApkInspector
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.captureOperationTarget
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private data class DirectApkOutcome(
    val success: Boolean,
    val packageName: String,
    val deviceName: String,
    val serial: String,
    val error: String?,
)

/** Uses the existing APK dialog and installer, but only after double, exact-target consent. */
@Composable
internal fun DirectExistingApkInstallFlow(onDismiss: () -> Unit, onInstalled: () -> Unit) {
    val monitored by DeviceMonitor.state.collectAsState()
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val service = remember { DirectExistingApkInstallService() }
    var info by remember { mutableStateOf<ExistingApkInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var approval by remember { mutableStateOf<DeviceInstallConfirmationRequest?>(null) }
    var installing by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<DirectApkOutcome?>(null) }
    val pickerTitle = stringResource(Res.string.direct_apk_choose_file)
    val noTarget = stringResource(Res.string.ui_direct_apk_no_target)
    val changedApk = stringResource(Res.string.ui_direct_apk_changed)
    val inspectError = stringResource(Res.string.ui_direct_apk_inspection_error)
    val verifyError = stringResource(Res.string.ui_direct_apk_verify_error)
    val installError = stringResource(Res.string.ui_direct_apk_install_error)

    LaunchedEffect(Unit) {
        val picked = MorpheFilePicker.pickFile(title = pickerTitle, extensions = listOf("apk"))
        if (picked == null) {
            onDismiss()
            return@LaunchedEffect
        }
        busy = true
        val scanned = withContext(Dispatchers.IO) { ExistingApkInspector.inspect(picked) }
        busy = false
        scanned.onSuccess { info = it }.onFailure { error = it.message ?: inspectError }
    }
    if (!installing && approval == null && outcome == null && (info != null || busy || error != null)) {
        ExistingApkInstallDialog(
            info = info,
            inspecting = busy,
            error = error,
            devices = monitored.devices,
            selectedDevice = monitored.selectedDevice,
            onDeviceSelected = { device -> DeviceMonitor.selectDevice(device); error = null },
            onInstall = {
                val selectedInfo = info
                val selected = DeviceMonitor.state.value.captureOperationTarget()
                if (selectedInfo == null || selected == null) {
                    error = noTarget
                } else if (!busy) {
                    busy = true
                    scope.launch {
                        val check = runCatching {
                            val req = withContext(Dispatchers.IO) {
                                DeviceInstallConfirmationGate.prepare(File(selectedInfo.path), selected).getOrThrow()
                            }
                            require(req.packageName == selectedInfo.packageName &&
                                req.versionName == selectedInfo.versionName) {
                                changedApk
                            }
                            val adbPath = adbManager.findAdb()
                            withContext(Dispatchers.IO) {
                                service.preflight(req, { DeviceMonitor.state.value.captureOperationTarget() }, adbPath)
                                    .getOrThrow()
                            }
                            req
                        }
                        busy = false
                        check.onSuccess { approval = it; error = null }
                            .onFailure { error = it.message ?: verifyError }
                    }
                }
            },
            onDismiss = onDismiss,
        )
    }
    approval?.let { approved ->
        DeviceInstallConfirmationDialog(
            request = approved,
            stillSelectedAndReady = monitored.captureOperationTarget()?.serial == approved.target.serial,
            previouslyInstalled = false,
            onDismiss = { approval = null },
            onConfirm = {
                approval = null
                installing = true
                scope.launch {
                    val adbPath = adbManager.findAdb()
                    val installed = withContext(Dispatchers.IO) {
                        service.install(approved, { DeviceMonitor.state.value.captureOperationTarget() }, adbPath)
                    }
                    installing = false
                    outcome = installed.fold(
                        onSuccess = {
                            onInstalled()
                            DirectApkOutcome(true, approved.packageName, approved.target.displayName, approved.target.serial, null)
                        },
                        onFailure = { failed ->
                            DirectApkOutcome(false, approved.packageName, approved.target.displayName, approved.target.serial,
                                failed.message ?: installError)
                        },
                    )
                }
            },
        )
    }
    if (installing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.direct_apk_installing)) },
            text = { Text(stringResource(Res.string.direct_apk_installing)) },
            confirmButton = {},
        )
    }
    outcome?.let { completion ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (completion.success) Res.string.direct_apk_success else Res.string.direct_apk_failed)) },
            text = {
                Text(if (completion.success) {
                    stringResource(
                        Res.string.direct_apk_success_message,
                        completion.packageName, completion.deviceName, completion.serial
                    )
                } else completion.error.orEmpty())
            },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.direct_apk_done)) } },
        )
    }
}
