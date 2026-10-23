package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchMetadata

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
