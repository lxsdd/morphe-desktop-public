/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import app.morphe.gui.util.DeviceOperationTarget

/**
 * The mutually-exclusive primary action dialog shown by Home.
 *
 * A single sealed value makes overlapping detail/confirmation dialogs
 * unrepresentable. Secondary surfaces with independent navigation semantics
 * (source management, add-source, and version warnings) remain separate.
 */
sealed interface HomePrimaryModal {
    data object None : HomePrimaryModal
    data class Detail(val packageName: String) : HomePrimaryModal
    data class InstallConfirmation(
        val packageName: String,
        val target: DeviceOperationTarget,
    ) : HomePrimaryModal
    data class ForgetConfirmation(val packageName: String) : HomePrimaryModal
    data class UninstallConfirmation(val packageName: String) : HomePrimaryModal
    data class RepatchMissingInput(val packageName: String) : HomePrimaryModal
}

internal fun HomePrimaryModal.requestInstall(
    packageName: String,
    target: DeviceOperationTarget,
): HomePrimaryModal = HomePrimaryModal.InstallConfirmation(packageName, target)

internal fun HomePrimaryModal.requestForget(packageName: String): HomePrimaryModal =
    HomePrimaryModal.ForgetConfirmation(packageName)

internal fun HomePrimaryModal.requestUninstall(packageName: String): HomePrimaryModal =
    HomePrimaryModal.UninstallConfirmation(packageName)

internal fun HomePrimaryModal.requestMissingInput(packageName: String): HomePrimaryModal =
    HomePrimaryModal.RepatchMissingInput(packageName)
