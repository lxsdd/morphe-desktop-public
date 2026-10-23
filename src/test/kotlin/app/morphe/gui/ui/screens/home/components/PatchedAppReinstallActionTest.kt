package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.screens.home.PatchedAppState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatchedAppReinstallActionTest {
    @Test
    fun `same version on device offers explicit reinstall`() {
        assertTrue(shouldOfferPatchedAppInstall(PatchedAppState.PATCHED, false, true))
        assertTrue(shouldOfferPatchedAppInstall(PatchedAppState.PATCHED_WITH_UPDATES, false, true))
    }

    @Test
    fun `older or missing device app retains install action`() {
        assertTrue(shouldOfferPatchedAppInstall(PatchedAppState.PATCHED, true, false))
    }

    @Test
    fun `newer device version does not offer downgrade disguised as reinstall`() {
        assertFalse(shouldOfferPatchedAppInstall(PatchedAppState.PATCHED, false, false))
    }

    @Test
    fun `missing or externally modified output cannot be offered`() {
        for (state in listOf(PatchedAppState.APK_MISSING, PatchedAppState.MODIFIED_EXTERNALLY)) {
            assertFalse(shouldOfferPatchedAppInstall(state, false, true))
            assertFalse(shouldOfferPatchedAppInstall(state, true, false))
        }
    }
}
