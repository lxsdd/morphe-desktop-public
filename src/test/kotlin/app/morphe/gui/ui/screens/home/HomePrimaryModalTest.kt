/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import app.morphe.gui.util.DeviceOperationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomePrimaryModalTest {
    private val target = DeviceOperationTarget(serial = "pixel-6a", displayName = "Pixel 6a")

    @Test
    fun `detail transitions atomically to install confirmation`() {
        val modal = HomePrimaryModal.Detail("example.app")
            .requestInstall("example.app", target)

        assertIs<HomePrimaryModal.InstallConfirmation>(modal)
        assertEquals("example.app", modal.packageName)
        assertEquals(target, modal.target)
    }

    @Test
    fun `detail transitions atomically to forget confirmation`() {
        val modal = HomePrimaryModal.Detail("example.app").requestForget("example.app")

        assertIs<HomePrimaryModal.ForgetConfirmation>(modal)
        assertEquals("example.app", modal.packageName)
    }

    @Test
    fun `detail transitions atomically to uninstall confirmation`() {
        val modal = HomePrimaryModal.Detail("example.app").requestUninstall("example.app")

        assertIs<HomePrimaryModal.UninstallConfirmation>(modal)
        assertEquals("example.app", modal.packageName)
    }
}
