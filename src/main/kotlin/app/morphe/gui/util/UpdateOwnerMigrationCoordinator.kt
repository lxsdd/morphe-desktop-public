/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.MorpheData
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.installation.AdbMigrationRequiredException
import app.morphe.engine.installation.InstalledPackageOwnership
import app.morphe.engine.installation.MigrationRequiredReason
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.FileChecksum
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class MigrationApkIdentity(val packageName: String, val versionName: String?)

internal fun inspectMigrationApk(file: File): MigrationApkIdentity? =
    ApkInspector.inspect(file)
        ?.takeUnless { it.isBundle || it.versionCode == null }
        ?.let { MigrationApkIdentity(it.packageName, it.versionName) }

data class UpdateOwnerMigrationRequest(
    val deviceSerial: String,
    val deviceName: String,
    val packageName: String,
    val patchedApkPath: String,
    val patchedApkSha256: String,
    val versionName: String?,
    val reason: MigrationRequiredReason,
)

/** Only a confirmed owner-blocked installation can create a migration offer. */
fun migrationRequestOrNull(
    error: Throwable?,
    target: DeviceOperationTarget,
    patchedApkPath: String,
    inspect: (File) -> MigrationApkIdentity? = ::inspectMigrationApk,
    hash: (File) -> String = FileChecksum::sha256,
): UpdateOwnerMigrationRequest? {
    val blocked = error as? AdbMigrationRequiredException ?: return null
    return try {
        val apk = File(patchedApkPath).absoluteFile
        val identity = if (apk.isFile && apk.extension.equals("apk", ignoreCase = true)) inspect(apk) else null
        if (target.serial.isBlank() || identity == null || identity.packageName != blocked.packageName) {
            null
        } else {
            UpdateOwnerMigrationRequest(
                deviceSerial = target.serial,
                deviceName = target.displayName,
                packageName = blocked.packageName,
                patchedApkPath = apk.path,
                patchedApkSha256 = hash(apk),
                versionName = identity.versionName,
                reason = blocked.reason,
            )
        }
    } catch (_: Exception) {
        null
    }
}

sealed interface UpdateOwnerMigrationResult {
    data object Success : UpdateOwnerMigrationResult
    data class Blocked(val message: String) : UpdateOwnerMigrationResult
    data class UninstallFailed(val message: String) : UpdateOwnerMigrationResult
    data class ReinstallFailed(val message: String) : UpdateOwnerMigrationResult
}

/**
 * Explicit migration only. A copy verified against the originally selected APK is
 * prepared BEFORE uninstall, so a replaced input cannot cause a different APK to
 * be installed after app data has been removed.
 */
class UpdateOwnerMigrationCoordinator(
    private val isDeviceReady: suspend (String) -> Boolean,
    private val readOwnership: suspend (String, String) -> Result<InstalledPackageOwnership>,
    private val uninstall: suspend (String, String) -> Result<Unit>,
    private val install: suspend (File, String, String) -> Result<Unit>,
    private val onPackageStateChanged: (String, String) -> Unit = { _, _ -> },
    private val inspect: (File) -> MigrationApkIdentity? = ::inspectMigrationApk,
    private val hash: (File) -> String = FileChecksum::sha256,
    private val scratchDirectory: () -> File = { MorpheData.tmpDir },
) {
    suspend fun execute(
        request: UpdateOwnerMigrationRequest,
        confirmedDataLoss: Boolean,
    ): UpdateOwnerMigrationResult = withContext(Dispatchers.IO) {
        fun blocked(message: String) = UpdateOwnerMigrationResult.Blocked(message)
        if (!confirmedDataLoss) return@withContext blocked("Data-loss confirmation is required.")
        if (request.deviceSerial.isBlank() || request.packageName.isBlank() ||
            !request.patchedApkSha256.matches(Regex("[a-fA-F0-9]{64}"))
        ) return@withContext blocked("Migration request is incomplete.")

        val source = File(request.patchedApkPath)
        if (!source.isFile || !source.extension.equals("apk", ignoreCase = true)) {
            return@withContext blocked("The selected APK is no longer available.")
        }
        val ready = try { isDeviceReady(request.deviceSerial) } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { false }
        if (!ready) return@withContext blocked("The selected device is no longer ready.")

        var snapshot: File? = null
        try {
            if (hash(source) != request.patchedApkSha256 ||
                inspect(source)?.packageName != request.packageName
            ) return@withContext blocked("The selected APK changed or has the wrong package.")

            val dir = scratchDirectory().also { if (!it.isDirectory) it.mkdirs() }
            snapshot = File.createTempFile("owner-migration-", ".apk", dir)
            source.copyTo(snapshot, overwrite = true)
            if (hash(snapshot) != request.patchedApkSha256 ||
                inspect(snapshot)?.packageName != request.packageName
            ) return@withContext blocked("The verified APK snapshot does not match the selected output.")

            // Re-read ownership after staging, as close as possible to the destructive action.
            if (!isDeviceReady(request.deviceSerial)) {
                return@withContext blocked("The selected device disconnected during verification.")
            }
            val owner = readOwnership(request.deviceSerial, request.packageName).getOrElse {
                return@withContext blocked("Could not re-check Android Update Ownership: ${it.message}")
            }
            val sameBlockedOwner = when (val reason = request.reason) {
                MigrationRequiredReason.NoOwner -> owner == InstalledPackageOwnership.NoOwner
                is MigrationRequiredReason.ForeignOwner ->
                    owner == InstalledPackageOwnership.OwnedBy(reason.packageName)
            }
            if (!sameBlockedOwner) {
                return@withContext blocked("Installed package or update owner changed. Retry the normal install first.")
            }

            // Once removal is attempted, a view/navigation cancellation must not
            // discard the verified APK snapshot before the replacement attempt.
            // Do not allow UI state-notification failures to abort recovery.
            withContext(NonCancellable) {
                val removal = try {
                    uninstall(request.packageName, request.deviceSerial)
                } catch (e: Exception) {
                    Result.failure(e)
                }
                runCatching { onPackageStateChanged(request.deviceSerial, request.packageName) }
                if (removal.isFailure) {
                    UpdateOwnerMigrationResult.UninstallFailed(
                        removal.exceptionOrNull()?.message ?: "Uninstall failed."
                    )
                } else {
                    val replacement = try {
                        if (hash(snapshot) != request.patchedApkSha256 ||
                            inspect(snapshot)?.packageName != request.packageName
                        ) Result.failure(IllegalStateException("Verified APK snapshot changed after uninstall."))
                        else install(snapshot, request.deviceSerial, request.packageName)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    runCatching { onPackageStateChanged(request.deviceSerial, request.packageName) }
                    if (replacement.isSuccess) UpdateOwnerMigrationResult.Success
                    else UpdateOwnerMigrationResult.ReinstallFailed(
                        "The app was uninstalled but could not be restored: " +
                            (replacement.exceptionOrNull()?.message ?: "Reinstallation failed.") +
                            " Keep the original APK and app-data backup."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            blocked("Migration preflight could not be completed: ${e.message ?: e::class.simpleName}")
        } finally {
            snapshot?.delete()
        }
    }

    companion object {
        fun using(manager: AdbManager): UpdateOwnerMigrationCoordinator =
            UpdateOwnerMigrationCoordinator(
                isDeviceReady = { serial ->
                    manager.getConnectedDevices().getOrNull()?.any { it.id == serial && it.isReady } == true
                },
                readOwnership = manager::readPackageUpdateOwnership,
                uninstall = manager::uninstallApk,
                install = { apk, serial, packageName ->
                    manager.installApk(
                        apkPath = apk.absolutePath,
                        deviceId = serial,
                        expectedPackageName = packageName,
                    )
                },
                onPackageStateChanged = DevicePackageMutations::notify,
            )
    }
}
