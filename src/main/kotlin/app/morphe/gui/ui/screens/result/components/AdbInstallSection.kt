/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.MorpheActionButton
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.TooltipText
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.AdbDevice
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.DeviceStatus
import app.morphe.gui.util.forSerial
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdbInstallSection(
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    deployments: Map<String, DeviceDeploymentState>,
    installTarget: DeviceOperationTarget?,
    corners: MorpheCornerStyle = LocalMorpheCorners.current,
    font: FontFamily = LocalMorpheFont.current,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    migrationRequired: Boolean,
    onMigrationClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalMorpheAccents.current
    val dimens = LocalMorpheDimens.current
    val selectedState = selectedDevice?.let { deployments.forSerial(it.id) }
    val anotherInstallRunning = deployments.values.any {
        it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING && it.serial != selectedDevice?.id
    }
    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.result_adb_section_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                devices.isEmpty() -> {
                    Text(
                        text = stringResource(Res.string.result_adb_no_devices),
                        fontSize = 12.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(Res.string.result_adb_connect_hint),
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val readyDevices = devices.filter { it.isReady }
                    val notReadyDevices = devices.filter { !it.isReady }

                        // Device list
                        (readyDevices + notReadyDevices).forEach { device ->
                            val isSelected = selectedDevice?.id == device.id
                            val enabled = device.isReady
                            val state = deployments.forSerial(device.id)
                            val deviceHover = remember { MutableInteractionSource() }
                            val isDeviceHovered by deviceHover.collectIsHoveredAsState()

                            val deviceBorder by animateColorAsState(
                                when {
                                    isSelected -> accents.secondary.copy(alpha = 0.5f)
                                    isDeviceHovered && enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    else -> borderColor
                                },
                                animationSpec = tween(150)
                            )
                            val deviceBg by animateColorAsState(
                                when {
                                    isSelected -> accents.secondary.copy(alpha = 0.06f)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(150)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .hoverable(deviceHover)
                                    .clip(RoundedCornerShape(corners.small))
                                    .border(1.dp, deviceBorder, RoundedCornerShape(corners.small))
                                    .background(deviceBg, RoundedCornerShape(corners.small))
                                    .handCursor(enabled)
                                    .then(
                                        if (enabled) Modifier.clickable { onDeviceSelected(device) }
                                        else Modifier
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.PhoneAndroid,
                                    contentDescription = null,
                                    tint = when {
                                        isSelected -> accents.secondary
                                        enabled -> accents.primary.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = font,
                                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = device.id,
                                        fontSize = 11.sp,
                                        fontFamily = font,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Status tag
                                val statusColor = when {
                                    state.installPhase == DeviceDeploymentState.InstallPhase.FAILED -> MaterialTheme.colorScheme.error
                                    state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLED -> accents.secondary
                                    state.installPhase == DeviceDeploymentState.InstallPhase.PRESENT -> accents.warning
                                    state.installPhase == DeviceDeploymentState.InstallPhase.CHECKING -> accents.primary
                                    state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING -> accents.primary
                                    device.status == DeviceStatus.DEVICE -> MaterialTheme.colorScheme.onSurfaceVariant
                                    device.status == DeviceStatus.UNAUTHORIZED -> accents.warning
                                    else -> MaterialTheme.colorScheme.error
                                }
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(corners.small))
                                        .background(statusColor.copy(alpha = 0.06f), RoundedCornerShape(corners.small))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = when {
                                            state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING -> stringResource(Res.string.installing)
                                            state.installPhase == DeviceDeploymentState.InstallPhase.CHECKING -> "Checking…"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLED -> "Installed"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.PRESENT && state.installedOutputMatchesCurrent == false -> "Update ready"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.PRESENT -> "App installed"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.FAILED -> "Failed"
                                            device.status == DeviceStatus.DEVICE -> "Not installed"
                                            device.status == DeviceStatus.UNAUTHORIZED -> stringResource(Res.string.result_device_status_unauth)
                                            device.status == DeviceStatus.OFFLINE -> stringResource(Res.string.status_offline)
                                            else -> stringResource(Res.string.unknown)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = font,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        when {
                            selectedState?.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accents.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(selectedState.installMessage ?: stringResource(Res.string.installing), fontSize = 11.sp, fontFamily = font, color = accents.primary)
                            }
                            selectedState?.installError != null && installTarget?.serial == selectedDevice.id -> {
                                Text(selectedState.installError, fontSize = 11.sp, fontFamily = font, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onDismissError, modifier = Modifier.height(dimens.controlHeight)) {
                                        Text(stringResource(Res.string.dismiss), fontFamily = font, fontSize = 11.sp)
                                    }
                                    MorpheTooltip(if (migrationRequired) TooltipText.MIGRATE else "Retry installation on this device.") {
                                        Button(
                                            onClick = if (migrationRequired) onMigrationClick else onRetryClick,
                                            modifier = Modifier.height(dimens.controlHeight),
                                        ) {
                                            Text(if (migrationRequired) "Uninstall & retry" else stringResource(Res.string.retry), fontFamily = font, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            anotherInstallRunning -> Text(
                                "Another device installation is still running.",
                                fontSize = 11.sp,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            selectedDevice?.isReady == true -> MorpheActionButton(
                                label = if (selectedState?.installed == true) {
                                    stringResource(Res.string.result_adb_update_button, selectedDevice.displayName)
                                } else {
                                    stringResource(Res.string.result_screen_install_on_device_label, selectedDevice.displayName)
                                },
                                enabled = true,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onInstallClick,
                            )
                            devices.any { it.isReady } -> Text(stringResource(Res.string.result_adb_select_device_button), fontSize = 11.sp, fontFamily = font)
                        }
                }
            }
        }
    }
}
