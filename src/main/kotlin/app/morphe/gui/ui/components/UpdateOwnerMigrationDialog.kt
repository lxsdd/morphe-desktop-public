package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.engine.installation.MigrationRequiredReason
import app.morphe.gui.util.UpdateOwnerMigrationRequest

/** Destructive migration is disabled until the user acknowledges possible data loss. */
@Composable
fun UpdateOwnerMigrationDialog(
    request: UpdateOwnerMigrationRequest,
    isBusy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var acknowledged by remember(request) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Review Android update-ownership migration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Device: ${request.deviceName} (${request.deviceSerial})")
                Text("Package: ${request.packageName}")
                request.versionName?.let { Text("Selected APK version: $it") }
                val ownerText = when (val reason = request.reason) {
                    MigrationRequiredReason.NoOwner -> "The existing app has no update owner."
                    is MigrationRequiredReason.ForeignOwner ->
                        "The existing app's update owner is ${reason.packageName}."
                }
                Text(ownerText)
                Text(
                    "Continuing will uninstall the existing app and then install a verified copy of the " +
                        "currently selected patched APK. Uninstalling may permanently delete local app " +
                        "data, accounts, settings and downloads. Back up important data first. " +
                        "Reinstallation or Play Store updates are not guaranteed.",
                    color = MaterialTheme.colorScheme.error,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                        enabled = !isBusy,
                    )
                    Text(
                        "I understand that this may permanently erase this app's local data.",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = acknowledged && !isBusy) {
                if (isBusy) CircularProgressIndicator()
                else Text("Uninstall and reinstall")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancel") }
        },
    )
}
