/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.screens.home.HomePrimaryAction
import kotlin.test.Test
import kotlin.test.assertEquals

class PatchedAppDetailActionTest {
    @Test
    fun `selected newer apk overrides stale new-apk-required state`() {
        assertEquals(
            PatchedAppDetailAction.PATCH_SELECTED_INPUTS,
            resolvePatchedAppDetailAction(
                primaryAction = HomePrimaryAction.NEW_APK_REQUIRED,
                inputsChanged = true,
            ),
        )
    }

    @Test
    fun `selected newer apk and bundle overrides combined requirement`() {
        assertEquals(
            PatchedAppDetailAction.PATCH_SELECTED_INPUTS,
            resolvePatchedAppDetailAction(
                primaryAction = HomePrimaryAction.NEW_APK_AND_REPATCH_REQUIRED,
                inputsChanged = true,
            ),
        )
    }

    @Test
    fun `missing newer apk still opens apk selection`() {
        assertEquals(
            PatchedAppDetailAction.CHOOSE_APK,
            resolvePatchedAppDetailAction(
                primaryAction = HomePrimaryAction.NEW_APK_REQUIRED,
                inputsChanged = false,
            ),
        )
    }

    @Test
    fun `unchanged valid input uses ordinary repatch`() {
        assertEquals(
            PatchedAppDetailAction.REPATCH_CURRENT_INPUTS,
            resolvePatchedAppDetailAction(
                primaryAction = HomePrimaryAction.REPATCH_CURRENT_INPUT,
                inputsChanged = false,
            ),
        )
    }
}
