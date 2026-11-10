package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.engine.model.VersionStatus as ModernVersionStatus

/** Deliberately conservative mapping: private Quick status never upgrades a
 * questionable APK to 'recommended' on the strength of a newer UI model. */
internal fun VersionStatus.toModernStatus(): ModernVersionStatus = when (this) {
    VersionStatus.LATEST_STABLE -> ModernVersionStatus.RECOMMENDED
    VersionStatus.OLDER_STABLE -> ModernVersionStatus.SUPPORTED
    VersionStatus.LATEST_EXPERIMENTAL,
    VersionStatus.OLDER_EXPERIMENTAL -> ModernVersionStatus.EXPERIMENTAL
    VersionStatus.BUILD_UNSUPPORTED -> ModernVersionStatus.BUILD_UNSUPPORTED
    VersionStatus.TOO_NEW,
    VersionStatus.TOO_OLD,
    VersionStatus.UNSUPPORTED_BETWEEN -> ModernVersionStatus.UNSUPPORTED
    VersionStatus.UNKNOWN -> ModernVersionStatus.UNKNOWN
}

fun resolveStatusColorType(
    versionStatus: VersionStatus,
    checksumStatus: ChecksumStatus,
): StatusColorType = resolveStatusColorType(versionStatus.toModernStatus(), checksumStatus)

@Composable
fun resolveVersionStatusDisplay(
    versionStatus: VersionStatus,
    checksumStatus: ChecksumStatus,
    suggestedVersion: String? = null,
): VersionStatusDisplay? = resolveVersionStatusDisplay(
    versionStatus.toModernStatus(), checksumStatus, suggestedVersion,
)
