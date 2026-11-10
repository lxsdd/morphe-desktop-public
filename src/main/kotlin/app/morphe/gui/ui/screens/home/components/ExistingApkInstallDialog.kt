package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.AdbDevice
import app.morphe.gui.util.ExistingApkInfo
import app.morphe.gui.ui.components.MorpheDialogTitleBar
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** APK inspection and device selection only; no install or migration without final consent. */
@Composable
internal fun ExistingApkInstallDialog(
    info: ExistingApkInfo?,
    inspecting: Boolean,
    error: String?,
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val readyDevices = devices.filter { it.isReady }
    val readySelected = selectedDevice?.takeIf { dev -> readyDevices.any { it.id == dev.id } }
    AlertDialog(
        onDismissRequest = { if (!inspecting) onDismiss() },
        title = {
            MorpheDialogTitleBar(onDismiss = { if (!inspecting) onDismiss() },
                dismissEnabled = !inspecting,
                title = { Text(stringResource(Res.string.direct_apk_dialog_title)) })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.direct_apk_dialog_intro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (inspecting) Text(stringResource(Res.string.direct_apk_dialog_checking))
                info?.let { apk ->
                    Text(apk.appLabel?.takeIf(String::isNotBlank) ?: apk.fileName)
                    Text(apk.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(
                        Res.string.direct_apk_dialog_version,
                        apk.versionName ?: "?",
                        apk.versionCode?.toString() ?: "?",
                    ))
                    Text(stringResource(Res.string.direct_apk_dialog_target), style = MaterialTheme.typography.titleSmall)
                    if (readyDevices.isEmpty()) {
                        Text(stringResource(Res.string.direct_apk_dialog_no_device))
                    } else {
                        readyDevices.forEach { device ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = readySelected?.id == device.id,
                                    onClick = { onDeviceSelected(device) },
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(device.displayName)
                                    Text(device.id, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = onInstall, enabled = !inspecting && info != null && readySelected != null) {
                Text(stringResource(Res.string.direct_apk_dialog_continue))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !inspecting) {
                Text(stringResource(Res.string.direct_apk_dialog_cancel))
            }
        },
    )
}
