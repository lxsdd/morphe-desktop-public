package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.SupportedApp

/**
 * A renamed/clone APK may store the original package ID as its historical display name.
 * That identity is not a human-readable Android label. Prefer any explicit name recorded
 * for this exact app, then the source catalog for its ORIGINAL package, then only trusted
 * built-in known-name mappings. Never use another package's catalog entry or invent a
 * name for unknown packages; all Android package identities remain unchanged.
 *
 * Presentation only: this code must not affect installer targeting, source matching,
 * ownership, APK hash verification or historical metadata.
 */
internal fun resolveDeviceInventoryDisplayName(
    installedPackageName: String,
    recordedDisplayName: String?,
    originalPackageName: String?,
    originalCatalogName: String?,
    installedCatalogName: String?,
    androidResolvedName: String? = null,
): String {
    val original = originalPackageName?.takeIf { it.isNotBlank() } ?: installedPackageName
    val identityNames = setOf(original, installedPackageName)

    fun humanName(name: String?): String? = name
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it !in identityNames }

    return humanName(androidResolvedName)
        ?: humanName(recordedDisplayName)
        ?: humanName(originalCatalogName)
        ?: humanName(installedCatalogName)
        ?: SupportedApp.getKnownDisplayName(original)
        ?: recordedDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: originalCatalogName?.trim()?.takeIf { it.isNotEmpty() }
        ?: installedCatalogName?.trim()?.takeIf { it.isNotEmpty() }
        ?: installedPackageName
}

/** Shared read-only historical name projection for cards, search, sort and dialogs. */
internal fun resolvePatchedHistoryDisplayName(
    record: app.morphe.engine.model.PatchedAppRecord,
    supportedApps: List<SupportedApp>,
): String = resolveDeviceInventoryDisplayName(
    installedPackageName = record.installedPackageName,
    recordedDisplayName = record.displayName,
    originalPackageName = record.packageName,
    originalCatalogName = supportedApps.firstOrNull { it.packageName == record.packageName }?.displayName,
    installedCatalogName = supportedApps.firstOrNull { it.packageName == record.installedPackageName }?.displayName,
)
