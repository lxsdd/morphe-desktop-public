package app.morphe.gui.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatchSourceRemovalGuardTest {
    @Test fun `explicit confirmation permits only current editable source`() {
        assertTrue(mayCommitPatchSourceRemoval("source-1", "source-1", true, true))
        assertFalse(mayCommitPatchSourceRemoval("source-1", null, true, true))
        assertFalse(mayCommitPatchSourceRemoval("source-1", "source-2", true, true))
    }
    @Test fun `disabled or default source never removed even after confirmation`() {
        assertFalse(mayCommitPatchSourceRemoval("source-1", "source-1", false, true))
        assertFalse(mayCommitPatchSourceRemoval("source-1", "source-1", true, false))
        assertFalse(mayCommitPatchSourceRemoval("", "", true, true))
    }
}
