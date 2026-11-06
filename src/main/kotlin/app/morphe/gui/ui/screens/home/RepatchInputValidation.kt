package app.morphe.gui.ui.screens.home

/**
 * A previous patch history record is not the manifest of a newly selected APK.
 * Never use a remembered version or a file-name heuristic to validate a new input.
 */
internal fun verifiedRepatchVersion(
    expectedPackage: String,
    inspectedPackage: String,
    inspectedVersion: String?,
): String {
    require(expectedPackage.isNotBlank() && inspectedPackage == expectedPackage) {
        "The selected APK belongs to a different app: $inspectedPackage (expected $expectedPackage)."
    }
    val version = inspectedVersion?.trim().orEmpty()
    require(version.isNotBlank() && !version.equals("unknown", ignoreCase = true)) {
        "The selected APK's version could not be verified from its Android manifest."
    }
    return version
}
