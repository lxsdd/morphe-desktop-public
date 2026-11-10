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
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
        title = {
            MorpheDialogTitleBar(onDismiss = { if (!isBusy) onDismiss() },
                dismissEnabled = !isBusy,
                title = { Text(stringResource(Res.string.ui_migration_title)) })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.ui_install_device, request.deviceName, request.deviceSerial))
                Text(stringResource(Res.string.ui_install_package, request.packageName))
                request.versionName?.let { Text(stringResource(Res.string.ui_install_apk_version, it)) }
                val ownerText = when (val reason = request.reason) {
                    MigrationRequiredReason.NoOwner -> stringResource(Res.string.ui_migration_owner_none)
                    is MigrationRequiredReason.ForeignOwner ->
                        stringResource(Res.string.ui_migration_owner_other, reason.packageName)
                }
                Text(ownerText)
                Text(
                    stringResource(Res.string.ui_migration_warning),
                    color = MaterialTheme.colorScheme.error,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                        enabled = !isBusy,
                    )
                    Text(
                        stringResource(Res.string.ui_migration_acknowledge),
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
                else Text(stringResource(Res.string.ui_migration_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
