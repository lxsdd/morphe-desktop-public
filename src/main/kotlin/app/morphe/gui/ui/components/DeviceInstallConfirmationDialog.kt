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
        title = { Text(if (previouslyInstalled) "Confirm APK update" else "Confirm APK install") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device: ${request.target.displayName} (${request.target.serial})")
                Text("Package: ${request.packageName}")
                Text("Selected APK: ${request.versionName ?: "version unknown"}")
                Text(
                    "Only this exact device and APK will be installed. Android may refuse " +
                        "an update due to ownership or signing. Removing an existing app " +
                        "requires a separate acknowledgement of possible data loss.",
                )
                if (!stillSelectedAndReady) {
                    Text(
                        "The target changed or went offline. Select the original device to continue.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = stillSelectedAndReady) {
                Text(if (previouslyInstalled) "Update this device" else "Install on this device")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
