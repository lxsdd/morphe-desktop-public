package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.morphe.gui.util.DeviceInstallConfirmationRequest
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Exact-target confirmation copy is shared with the private regression.
 * This text never promises Android update ownership or data preservation.
 */
fun deviceInstallConfirmationText(
    packageName: String,
    apkVersion: String?,
    deviceName: String,
    deviceSerial: String,
    installedVersion: String?,
    replacingExisting: Boolean,
): String = buildString {
    append("Device: $deviceName ($deviceSerial)\n")
    append("Package: $packageName\n")
    append("Selected APK: ${apkVersion ?: "unknown"}\n")
    if (replacingExisting) {
        installedVersion?.let { append("Installed: v${it.removePrefix("v")}\n") }
        append("This updates the installed package only if Android accepts its signature and update owner. ")
    } else {
        append("This installs the selected patched APK on only the confirmed device. ")
    }
    append("Morphe asks separately before any uninstall. Data loss may occur if you later approve migration.")
}

/** Shared Simple/Expert normal installation consent. ADB is invoked by its caller only. */
@Composable
fun DeviceInstallConfirmationDialog(
    request: DeviceInstallConfirmationRequest,
    stillSelectedAndReady: Boolean,
    previouslyInstalled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            MorpheDialogTitleBar(onDismiss = onDismiss, title = {
                Text(stringResource(if (previouslyInstalled) Res.string.ui_install_title_update
                    else Res.string.ui_install_title_new))
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.ui_install_device, request.target.displayName, request.target.serial))
                Text(stringResource(Res.string.ui_install_package, request.packageName))
                Text(stringResource(Res.string.ui_install_apk_version,
                    request.versionName ?: stringResource(Res.string.device_overview_version_unknown)))
                Text(stringResource(Res.string.ui_install_warning))
                if (!stillSelectedAndReady) {
                    Text(
                        stringResource(Res.string.ui_install_target_lost),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = stillSelectedAndReady) {
                Text(stringResource(if (previouslyInstalled) Res.string.ui_install_action_update
                    else Res.string.ui_install_action_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
