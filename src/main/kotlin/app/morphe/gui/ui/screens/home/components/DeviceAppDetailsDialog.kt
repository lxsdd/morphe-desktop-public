package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.ui.components.MorpheDialogTitleBar
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The detail title uses the exact same name precedence as the device list. */
internal fun deviceAppDetailsTitle(
    packageName: String,
    catalogDisplayName: String?,
    androidDisplayName: String?,
): String = resolveDeviceInventoryDisplayName(
    installedPackageName = packageName,
    recordedDisplayName = null,
    originalPackageName = null,
    originalCatalogName = null,
    installedCatalogName = catalogDisplayName,
    androidResolvedName = androidDisplayName,
)


/**
 * A lookup action is ONLY a web search. It neither downloads nor installs an APK,
 * and experimental support never promotes an app to a stable update.
 * Resolve from currently loaded patch metadata rather than guessed versions.
 */
internal data class ExperimentalVersionSearchLink(val version: String, val url: String)

internal fun experimentalVersionSearchLinks(app: SupportedApp?): List<ExperimentalVersionSearchLink> {
    if (app == null || app.packageName.isBlank()) return emptyList()
    val stable = app.supportedVersions.map { it.removePrefix("v") }.toSet()
    return app.experimentalVersions.asSequence()
        .map { it.removePrefix("v").trim() }
        .filter { it.isNotEmpty() && it !in stable }
        .distinct()
        .mapNotNull { version ->
            app.downloadUrl(version)
                ?.takeIf { it.isNotBlank() }
                ?.let { ExperimentalVersionSearchLink(version, it) }
        }
        .toList()
}

/** Source patches available for one package. Never interpreted as applied patches. */
internal data class DevicePatchSourceDetails(
    val sourceName: String,
    val sourceVersion: String?,
    val patches: List<DeviceAvailablePatch>,
)

/**
 * The official version-code suppression patch writes Int.MAX_VALUE to the
 * manifest. This diagnostic never infers patch provenance or changes the app.
 */
internal fun hasPlayUpdateSuppressingVersionCode(versionCode: Long?): Boolean =
    versionCode == Int.MAX_VALUE.toLong()

/**
 * Read-only details for installed apps without a local PatchedAppRecord.
 * In particular, a Morphe-signed YouTube package is NOT evidence that
 * any particular source or patch set from this desktop is installed.
 */
@Composable
internal fun DeviceAppDetailsDialog(
    packageName: String,
    app: SupportedApp?,
    deviceInfo: DeviceAppInfo?,
    patchSources: List<DevicePatchSourceDetails>,
    onDismiss: () -> Unit,
    separateLocalPackage: String? = null,
    resolvedDisplayName: String? = null,
) {
    val uriHandler = LocalUriHandler.current
    val appName = deviceAppDetailsTitle(packageName, app?.displayName, resolvedDisplayName)
    val installed = deviceInfo?.installedVersion?.takeIf(String::isNotBlank)
    val target = app?.recommendedVersion?.takeIf(String::isNotBlank)
    val url = target?.let { app?.downloadUrl(it) }
    val experimentalLookups = experimentalVersionSearchLinks(app)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = (maxWidth - 32.dp).coerceAtMost(800.dp))
                    .heightIn(max = maxHeight * 0.88f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 5.dp,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                        .padding(PaddingValues(horizontal = 22.dp, vertical = 16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MorpheDialogTitleBar(onDismiss = onDismiss, title = {
                        Column {
                            Text(
                                appName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    })
                    HorizontalDivider()
                    Text(
                        stringResource(Res.string.device_details_on_device),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(
                            Res.string.device_overview_installed_app_version,
                            installed?.let { "v" + it.removePrefix("v") }
                                ?: stringResource(Res.string.device_overview_version_unknown),
                        ),
                    )
                    Text(
                        when (deviceInfo?.signedByMorphe) {
                            true -> stringResource(Res.string.device_details_morphe_signer)
                            false -> stringResource(Res.string.device_details_other_signer)
                            null -> stringResource(Res.string.device_details_unknown_signer)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            Res.string.device_play_owner_label,
                            when {
                                deviceInfo?.updateOwnerChecked != true ->
                                    stringResource(Res.string.device_play_owner_unknown)
                                deviceInfo?.updateOwnerPackage.isNullOrBlank() ->
                                    stringResource(Res.string.device_play_owner_none)
                                else -> deviceInfo?.updateOwnerPackage.orEmpty()
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    deviceInfo?.androidVersionCode?.let { code ->
                        Text(
                            stringResource(Res.string.device_android_version_code, code.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (hasPlayUpdateSuppressingVersionCode(code)) {
                            Text(
                                stringResource(Res.string.device_version_code_update_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(
                        stringResource(Res.string.device_play_owner_is_not_play_detachment),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(Res.string.device_details_no_local_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    separateLocalPackage?.let { other ->
                        Text(
                            stringResource(Res.string.device_overview_separate_package, other),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(Res.string.device_details_compatibility),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (app != null) {
                        Text(
                            stringResource(
                                Res.string.device_details_stable_versions,
                                app.supportedVersions.joinToString(", ") {
                                    FormatUtils.formatVersionWithBuild("v" + it.removePrefix("v"), app.requiredBuildCodes(it))
                                }
                                    .ifBlank { "–" },
                            ),
                        )
                        if (app.experimentalVersions.isNotEmpty()) {
                            Text(
                                stringResource(
                                    Res.string.device_details_experimental_versions,
                                    app.experimentalVersions.joinToString(", ") {
                                        FormatUtils.formatVersionWithBuild("v" + it.removePrefix("v"), app.requiredBuildCodes(it))
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (experimentalLookups.isNotEmpty()) {
                                Text(
                                    stringResource(Res.string.device_details_experimental_lookup_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                experimentalLookups.forEach { lookup ->
                                    OutlinedButton(
                                        onClick = { uriHandler.openUri(lookup.url) },
                                    ) {
                                        Text(stringResource(
                                            Res.string.device_details_find_experimental_version,
                                            FormatUtils.formatVersionWithBuild(lookup.version, app.requiredBuildCodes(lookup.version)),
                                        ))
                                    }
                                }
                            }
                        }
                        if (target != null) {
                            Text(
                                stringResource(
                                    Res.string.device_overview_target_version,
                                    FormatUtils.formatVersionWithBuild("v" + target.removePrefix("v"), app.requiredBuildCodes(target)),
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (!url.isNullOrBlank()) {
                                OutlinedButton(onClick = { uriHandler.openUri(url) }) {
                                    Text(stringResource(
                                        Res.string.home_guided_find_app_version,
                                        FormatUtils.formatVersionWithBuild(target.removePrefix("v"), app.requiredBuildCodes(target)),
                                    ))
                                }
                            }
                        }
                    } else {
                        Text(
                            stringResource(Res.string.device_details_not_in_catalog),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(Res.string.device_details_available_patches),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(Res.string.device_details_patches_not_installed_proof),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (patchSources.isEmpty()) {
                        Text(stringResource(Res.string.device_details_no_sources))
                    } else {
                        patchSources.forEach { source ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    buildString {
                                        append(source.sourceName)
                                        source.sourceVersion?.let {
                                            append("  v")
                                            append(it.removePrefix("v"))
                                        }
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                source.patches.forEach { patch ->
                                    Column(
                                        modifier = Modifier.padding(start = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text("• " + patch.name)
                                        if (patch.description.isNotBlank()) {
                                            Text(
                                                patch.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            when (patch.compatibleWithInstalledVersion) {
                                                true -> stringResource(Res.string.device_details_compatible_installed)
                                                false -> stringResource(Res.string.device_details_incompatible_installed)
                                                null -> stringResource(Res.string.device_details_compatibility_unknown)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}
