/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.morphe.gui.util.DeviceUpdateOwner
import app.morphe.gui.ui.theme.MorpheTheme

/** Positions popup above anchor, flipping below if space is insufficient. */
private class AboveAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val above = anchorBounds.top - popupContentSize.height - gap
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            if (above >= 0) above else anchorBounds.bottom + gap,
        )
    }
}

@Composable
fun MorpheTooltip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val gap = with(density) { 6.dp.roundToPx() }

    Box(modifier = modifier.hoverable(hover)) {
        content()
        if (isHovered && enabled) {
            Popup(popupPositionProvider = remember(gap) { AboveAnchor(gap) }) {
                Surface(
                    shape = MorpheTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MorpheTheme.colors.primary.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}


object TooltipText {
    const val DEVICE_PICKER = "Select an authorized ADB device and view its connection status."
    const val TOOLS = "Open maintenance tools and cache controls."
    const val SETTINGS = "Configure appearance, ADB, signing, and advanced options."
    const val SOURCE_REFRESH = "Reload enabled patch sources and local developer folders."
    const val SOURCE_EDIT = "Edit this patch source without changing its priority."
    const val SOURCE_REMOVE = "Remove this source from Morphe Desktop. Existing files are not deleted."
    const val SOURCE_REORDER = "Change source priority by dragging or using the arrow controls."
    const val DEVICE_REFRESH = "Reload installed apps, app names, ownership, and patch status from the selected device."
    const val COMMAND_PREVIEW = "Show or hide the command equivalent of this patch configuration."
    const val CONTINUE_ON_ERROR = "Continue patching when an individual patch fails. The result may be incomplete."
    const val UNINSTALL = "Remove this installed app from the selected device."
    const val FORGET = "Remove this app from Morphe Desktop history without deleting its APK."
    const val INSTALL = "Install the already patched APK on the selected device."
    const val REPATCH = "Create a new patched APK using the current sources and options."
    const val MIGRATE = "Uninstall the existing app and reinstall this patched APK so Morphe Desktop can manage future updates."
    const val DEVICE_IMPORT = "Use the app package currently installed on this device as the patch source."
    const val SORT_ACTION_PRIORITY =
        "Prioritize installation-ready APKs, combined required actions, required APKs, re-patching, optional offers, current apps, then unknown states."

    fun ownership(owner: DeviceUpdateOwner): String = when (owner) {
        DeviceUpdateOwner.DesktopManaged -> "Updates are managed by Morphe Desktop on this device."
        DeviceUpdateOwner.MorpheManager ->
            "This installation must be reinstalled once through Morphe Desktop before Desktop-managed updates can be used."
        is DeviceUpdateOwner.Other ->
            "Updates are currently managed by ${owner.packageName}. A fresh Morphe Desktop install is required to migrate."
        DeviceUpdateOwner.NoOwner ->
            "This installed app has no update owner and cannot be migrated by an update alone."
        DeviceUpdateOwner.Unsupported -> "This device does not support Android Update Ownership."
        DeviceUpdateOwner.NotApplicable -> "Update ownership is not relevant without an available patch source."
        is DeviceUpdateOwner.Unavailable -> "Update ownership could not be determined reliably. No ownership assumption was made."
    }
}
