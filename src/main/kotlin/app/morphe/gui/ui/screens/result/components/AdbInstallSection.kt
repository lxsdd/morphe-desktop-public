/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.AdbDevice
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.forSerial
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Shows ALL devices after each install, including success and failures.
 * Selection changes never reattribute a success to another ADB serial.
 * Installing on another device requires an explicit per-device selection + click.
 */
@Composable
internal fun AdbInstallSection(
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    deployments: Map<String, DeviceDeploymentState>,
    installTarget: DeviceOperationTarget?,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = selectedDevice?.takeIf { it.isReady && devices.any { d ->
        d.id == it.id && d.isReady
    } }
    val selectedState = selected?.let { deployments.forSerial(it.id) }
    val anyInstalling = deployments.values.any {
        it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING
    }
    Column(
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth()
            .border(1.dp, borderColor, MorpheTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.result_adb_section_title),
            style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (devices.isEmpty()) {
            Text(stringResource(Res.string.result_adb_no_devices))
            Text(stringResource(Res.string.result_adb_connect_hint),
                style = MorpheTheme.typography.labelSmall)
        }
        // Always show the other devices even after a successful installation.
        devices.sortedWith(compareBy<AdbDevice>({ !it.isReady }, { it.displayName })).forEach { device ->
            val state = deployments.forSerial(device.id)
            val isSelected = selected?.id == device.id
            val message = when (state.installPhase) {
                DeviceDeploymentState.InstallPhase.INSTALLED ->
                    stringResource(Res.string.result_device_installed_exact)
                DeviceDeploymentState.InstallPhase.INSTALLING -> stringResource(Res.string.installing)
                DeviceDeploymentState.InstallPhase.CHECKING -> stringResource(Res.string.result_device_checking)
                DeviceDeploymentState.InstallPhase.FAILED -> stringResource(Res.string.result_device_failed)
                DeviceDeploymentState.InstallPhase.PRESENT -> stringResource(Res.string.result_device_present_unverified)
                DeviceDeploymentState.InstallPhase.IDLE -> stringResource(Res.string.result_device_not_installed)
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else borderColor,
                        MorpheTheme.shapes.small)
                    .then(if (device.isReady && !anyInstalling) Modifier.clickable {
                        onDeviceSelected(device)
                    } else Modifier)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.displayName, style = MorpheTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    Text(device.id, style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (!device.isReady) stringResource(Res.string.status_offline) else message,
                    style = MorpheTheme.typography.labelSmall,
                    color = if (state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLED)
                        MorpheTheme.colors.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val error = selectedState?.installError
        if (error != null) {
            Text(error, color = MorpheTheme.colors.danger,
                style = MorpheTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MorpheButton(label = stringResource(Res.string.dismiss),
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                    onClick = onDismissError)
                if (installTarget?.serial == selected?.id) {
                    MorpheButton(label = stringResource(Res.string.retry),
                        variant = MorpheButtonVariant.DANGER,
                        size = MorpheButtonSize.COMPACT,
                        onClick = onRetryClick)
                }
            }
        } else if (selectedState?.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING) {
            Text(selectedState.installMessage ?: stringResource(Res.string.installing),
                style = MorpheTheme.typography.bodySmall)
        } else if (selected != null) {
            MorpheButton(
                label = if (selectedState?.installed == true)
                    stringResource(Res.string.result_adb_update_button, selected.displayName)
                else stringResource(Res.string.result_screen_install_on_device_label, selected.displayName),
                variant = MorpheButtonVariant.FILLED,
                size = MorpheButtonSize.LARGE,
                enabled = !anyInstalling,
                modifier = Modifier.fillMaxWidth(),
                onClick = onInstallClick,
            )
        } else if (devices.any { it.isReady }) {
            Text(stringResource(Res.string.result_adb_select_device_button))
        }
    }
}
