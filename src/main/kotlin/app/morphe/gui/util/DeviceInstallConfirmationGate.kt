package app.morphe.gui.util

import app.morphe.engine.apk.ApkInspectionResult
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.util.FileChecksum
import java.io.File

/** Immutable output identity and exact ADB target captured before normal install consent. */
data class DeviceInstallConfirmationRequest(
    val target: DeviceOperationTarget,
    val apkPath: String,
    val apkSha256: String,
    val packageName: String,
    val versionName: String?,
)

object DeviceInstallConfirmationGate {
    /** Read-only preflight. Call on IO, never on the Compose thread. */
    fun prepare(
        file: File,
        target: DeviceOperationTarget,
        inspect: (File) -> ApkInspectionResult? = ApkInspector::inspect,
        hash: (File) -> String = FileChecksum::sha256,
    ): Result<DeviceInstallConfirmationRequest> = runCatching {
        require(target.serial.isNotBlank()) { "Select a ready Android device." }
        val apk = file.absoluteFile
        require(apk.isFile && apk.extension.equals("apk", ignoreCase = true)) {
            "The selected output APK is not available."
        }
        val metadata = inspect(apk)
        require(metadata != null && !metadata.isBundle && metadata.versionCode != null &&
            metadata.packageName.isNotBlank()) { "The APK package/version could not be verified." }
        val digest = hash(apk)
        require(digest.matches(Regex("[0-9a-fA-F]{64}"))) { "The APK fingerprint is invalid." }
        DeviceInstallConfirmationRequest(
            target, apk.path, digest, metadata.packageName, metadata.versionName,
        )
    }

    /** Reject changed device, bytes, package or version immediately before ADB. */
    fun stillMatches(
        request: DeviceInstallConfirmationRequest,
        selectedTarget: DeviceOperationTarget?,
        inspect: (File) -> ApkInspectionResult? = ApkInspector::inspect,
        hash: (File) -> String = FileChecksum::sha256,
    ): Boolean = runCatching {
        if (selectedTarget?.serial != request.target.serial || request.target.serial.isBlank()) {
            return@runCatching false
        }
        val file = File(request.apkPath)
        if (!file.isFile) return@runCatching false
        val metadata = inspect(file) ?: return@runCatching false
        !metadata.isBundle && metadata.versionCode != null &&
            metadata.packageName == request.packageName &&
            metadata.versionName == request.versionName &&
            hash(file).equals(request.apkSha256, ignoreCase = true)
    }.getOrDefault(false)
}
