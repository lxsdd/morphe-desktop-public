/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.appCardColors
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Compact summary row displaying a previously patched application. */
@Composable
fun YourAppRow(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    onClick: () -> Unit,
    appIconColorHex: String? = null,
) {
    val cardFills = LocalCardFills.current

    val versionPart = if (updateInfo?.appOutdated == true && updateInfo.appSuggestedVersion != null) {
        "v${record.apkVersion.removePrefix("v")} → v${updateInfo.appSuggestedVersion.removePrefix("v")}"
    } else {
        "v${record.apkVersion.removePrefix("v")}"
    }
    val datePart = relativeOrShortDate(record.patchedAt)
    val devicePart = if (deviceInfo != null) {
        when {
            !deviceInfo.installed -> stringResource(Res.string.home_app_row_not_on_device)
            deviceInfo.signedByMorphe == false -> stringResource(Res.string.home_app_row_on_device_not_signed)
            deviceInfo.installedVersion != null && deviceInfo.installedVersion.removePrefix("v") != record.apkVersion.removePrefix("v") ->
                "Device: v${deviceInfo.installedVersion.removePrefix("v")}"
            else -> stringResource(Res.string.home_app_row_on_device)
        }
    } else null

    val subtitle = remember(versionPart, datePart, devicePart) {
        listOfNotNull(versionPart, datePart, devicePart).joinToString(" · ")
    }

    val hasUpdate = (state == PatchedAppState.PATCHED_WITH_UPDATES) ||
        (updateInfo != null && (updateInfo.appOutdated || updateInfo.sources.any { it.outdated }))

    val cardColors = appCardColors()

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = MorpheTheme.shapes.mediumDp,
        appIconColorHex = null, // neutral default; custom saved fills preserved
        fill = cardFills[record.packageName],
        onClick = onClick,
        onCustomise = {
            cardFills.requestEdit(record.packageName, record.displayName, appIconColorHex)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorpheAppAvatar(
                name = record.displayName,
                size = MorpheAvatarSize.MEDIUM,
                onGradient = !cardColors.isMonochrome,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = record.displayName,
                    style = MorpheTheme.typography.titleSmall.copy(color = cardColors.title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${stringResource(Res.string.home_version_patched, "v" + record.apkVersion.removePrefix("v"))} · ${relativeOrShortDate(record.patchedAt)}",
                    style = MorpheTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = MorpheTheme.alphas.secondaryText)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (deviceInfo?.installPending == true) {
                Spacer(Modifier.width(8.dp))
                MorpheBadge(
                    text = stringResource(Res.string.home_your_apps_install_ready),
                    containerColor = cardChipInk,
                    onGradient = true,
                )
            }
            if (hasConfirmedAppUpdate(updateInfo)) {
                Spacer(Modifier.width(8.dp))
                MorpheBadge(
                    text = stringResource(
                        Res.string.home_guided_new_version_badge,
                        updateInfo!!.appSuggestedVersion!!.removePrefix("v")),
                    containerColor = cardChipInk,
                    onGradient = true,
                )
            } else if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(8.dp))
                PatchedStateBadge(state)
            }
        }
        deviceInfo?.let { DeviceLine(it, Color.White.copy(alpha = 0.5f), cardChipInk) }
        updateInfo?.sources?.firstOrNull()?.let { s ->
            val more = updateInfo.sources.size - 1
            VersionBumpText(
                label = "${stringResource(Res.string.home_version_patch_source, s.name)} ",
                oldVersion = s.usedVersion,
                // A newer source release alone is not a confirmed patch update for this app.
                newVersion = if (s.hasRelevantChanges) s.latestAvailableVersion else null,
                suffix = if (more > 0) "  +$more" else null,
            )
        }
        if (hasConfirmedAppUpdate(updateInfo)) {
            Text(
                text = stringResource(
                    Res.string.home_version_app_upgrade,
                    "v" + updateInfo!!.appUsedVersion.removePrefix("v"),
                    "v" + updateInfo.appSuggestedVersion!!.removePrefix("v"),
                ),
                style = MorpheTheme.typography.bodySmall.copy(color = Color.White),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val cardAdvice = updateInfo?.let { appAdvice(it) }
        if (cardAdvice != null) {
            Text(
                text = cardAdvice.first,
                style = MorpheTheme.typography.bodySmall.copy(color = cardChipInk),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (hasConfirmedPatchUpdate(updateInfo)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MorpheIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White.copy(alpha = MorpheTheme.alphas.secondaryText),
                )
                Text(
                    text = stringResource(Res.string.home_your_apps_patch_review_hint),
                    style = MorpheTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = MorpheTheme.alphas.secondaryText)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Prompt installation when the patched APK is newer than the version on device.
        if (deviceInfo?.installPending == true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White,
                )
                Text(
                    text = if (deviceInfo.installed)
                        stringResource(
                            Res.string.home_your_apps_device_version_ready,
                            record.apkVersion.removePrefix("v"),
                            deviceInfo.installedVersion?.removePrefix("v") ?: "?"
                        )
                    } else {
                        MorpheBadge(
                            text = stringResource(Res.string.home_your_apps_install_ready),
                            containerColor = cardColors.chipInk,
                            onGradient = true,
                        )
                    }
                }
                if (state == PatchedAppState.APK_MISSING || state == PatchedAppState.MODIFIED_EXTERNALLY) {
                    PatchedStateBadge(state)
                }
            }
        }
    }
}
