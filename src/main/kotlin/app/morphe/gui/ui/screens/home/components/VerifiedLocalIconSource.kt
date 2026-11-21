package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord

/**
 * Prevent a local-history APK icon from masquerading as an unverified app on a
 * connected device. This read-only predicate cannot query or mutate devices.
 */
internal fun verifiedLocalIconSource(
    record: PatchedAppRecord?,
    installedPackageName: String,
    installedVersion: String?,
    exactBuildVerified: Boolean,
): PatchedAppRecord? {
    if (record == null || !exactBuildVerified) return null
    if (record.outputApkSha256?.matches(Regex("[0-9a-fA-F]{64}")) != true) return null
    if (record.installedPackageName != installedPackageName) return null
    if (installedVersion.isNullOrBlank() ||
        installedVersion.removePrefix("v") != record.apkVersion.removePrefix("v")) return null
    return record
}
