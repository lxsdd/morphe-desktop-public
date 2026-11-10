package app.morphe.gui.util

import app.morphe.engine.installation.AdbApkInstaller
import app.morphe.engine.installation.AdbInstallMode
import app.morphe.engine.installation.AdbInstallRequest
import app.morphe.engine.installation.AdbPackageOwnershipProbe
import app.morphe.engine.installation.InstalledPackageOwnership
import app.morphe.engine.installation.ProcessAdbCommandRunner
import java.io.File

/** New installation only; migration and update cannot be triggered by file selection. */
internal class DirectExistingApkInstallService(
    private val matches: (DeviceInstallConfirmationRequest, DeviceOperationTarget?) -> Boolean =
        { request, target -> DeviceInstallConfirmationGate.stillMatches(request, target) },
    private val ownership: (String, String, String) -> InstalledPackageOwnership =
        { adb, serial, pkg ->
            AdbPackageOwnershipProbe(ProcessAdbCommandRunner()).probe(adb, serial, pkg)
        },
    private val installer: (AdbInstallRequest) -> Result<AdbInstallMode> =
        { request -> AdbApkInstaller().install(request) },
) {
    fun preflight(
        request: DeviceInstallConfirmationRequest,
        currentTarget: () -> DeviceOperationTarget?,
        adbPath: String?,
    ): Result<Unit> = runCatching {
        require(!adbPath.isNullOrBlank()) { "ADB is not available on this computer." }
        val selected = currentTarget() ?: error("Select a ready Android device.")
        require(selected.serial == request.target.serial) { "Selected device changed. Nothing was installed." }
        require(matches(request, selected)) { "The APK bytes, package or version changed." }
        check(ownership(adbPath, selected.serial, request.packageName) == InstalledPackageOwnership.NotInstalled) {
            "This app is already installed on the selected device. Fresh-install mode does not update or uninstall it."
        }
        val latest = currentTarget()
        require(latest?.serial == request.target.serial && matches(request, latest)) {
            "APK or selected device changed during inspection."
        }
    }

    /** Only callable after the user has confirmed the exact APK and serial in a second dialog. */
    fun install(
        request: DeviceInstallConfirmationRequest,
        currentTarget: () -> DeviceOperationTarget?,
        adbPath: String?,
    ): Result<Unit> = runCatching {
        preflight(request, currentTarget, adbPath).getOrThrow()
        val latest = currentTarget()
        require(latest?.serial == request.target.serial && matches(request, latest)) {
            "APK or selected device changed immediately before ADB installation."
        }
        installer(
            AdbInstallRequest(
                adbPath = requireNotNull(adbPath),
                apk = File(request.apkPath),
                deviceSerial = request.target.serial,
                expectedPackageName = request.packageName,
                allowDowngrade = false,
            )
        ).getOrThrow()
        Unit
    }
}
