package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.screens.home.DeviceAppInfo

/** Catalog data, not proof that any patch has been installed on an Android device. */
internal data class DeviceAvailablePatch(
    val name: String,
    val description: String,
    /** null if the installed app version is not known from the selected device. */
    val compatibleWithInstalledVersion: Boolean?,
)

/** Reuse the same package/version compatibility rules as the loaded patch catalog. */
internal fun availableDevicePatches(
    patches: List<PatchMetadata>,
    packageName: String,
    installedVersion: String?,
): List<DeviceAvailablePatch> {
    if (packageName.isBlank()) return emptyList()
    val normalizedVersion = installedVersion?.removePrefix("v")?.takeIf(String::isNotBlank)
    return patches.asSequence()
        .filter { patch -> patch.compatiblePackages.any { it.name == packageName } }
        .map { patch ->
            DeviceAvailablePatch(
                name = patch.name,
                description = patch.description,
                compatibleWithInstalledVersion = normalizedVersion?.let {
                    patch.isCompatibleWith(packageName, it)
                },
            )
        }
        .distinctBy { it.name to it.description }
        .sortedBy { it.name.lowercase() }
        .toList()
}

/** Package-level sources are not proof of version eligibility or installed patches. */
internal enum class DevicePatchCompatibility {
    COMPATIBLE, INCOMPATIBLE, UNKNOWN_VERSION, NO_SOURCE
}

internal fun devicePatchCompatibility(
    patches: Collection<PatchMetadata>,
    packageName: String,
    installedVersion: String?,
): DevicePatchCompatibility {
    if (packageName.isBlank()) return DevicePatchCompatibility.NO_SOURCE
    val candidates = patches.filter { patch ->
        patch.compatiblePackages.any { it.name == packageName }
    }
    if (candidates.isEmpty()) return DevicePatchCompatibility.NO_SOURCE
    val version = installedVersion?.removePrefix("v")?.takeUnless {
        it.isBlank() || it.equals("unknown", ignoreCase = true)
    } ?: return DevicePatchCompatibility.UNKNOWN_VERSION
    return if (candidates.any { it.isCompatibleWith(packageName, version) }) {
        DevicePatchCompatibility.COMPATIBLE
    } else DevicePatchCompatibility.INCOMPATIBLE
}

/** A recorded renamed APK cannot transfer installed patch provenance to the original package. */
internal fun installedSeparateHistoryPackage(
    originalPackage: String,
    installedPackages: Set<String>,
    history: List<PatchedAppRecord>,
): String? = history.asSequence()
    .filter { it.packageName == originalPackage &&
        it.installedPackageName != originalPackage &&
        it.installedPackageName in installedPackages }
    .map { it.installedPackageName }
    .distinct()
    .singleOrNull()

/**
 * Project historical, original-package-facing records into actual installed
 * Android package IDs for the device inventory ONLY. A renamed APK must never
 * transfer its signer, version, update owner or exact APK proof to the stock app.
 *
 * Ambiguous duplicate installed IDs fail closed instead of guessing ownership.
 */
internal fun projectHistoryInfoByInstalledPackage(
    records: Collection<PatchedAppRecord>,
    historyByOriginalPackage: Map<String, DeviceAppInfo>,
): Map<String, DeviceAppInfo> {
    val groups = records.groupBy { it.installedPackageName }
    return buildMap {
        groups.forEach { (installedPackage, matches) ->
            if (installedPackage.isBlank() || matches.size != 1) return@forEach
            val record = matches.single()
            val info = historyByOriginalPackage[record.packageName] ?: return@forEach
            put(installedPackage, info)
        }
    }
}
