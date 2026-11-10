/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorpheDialogHostStateTest {
    @Test
    fun `most recent dialog is active and closing it restores the previous one`() {
        val state = MorpheDialogHostState()
        val first = Any()
        val second = Any()

        state.show(HostedMorpheDialog(first, {}, {}))
        state.show(HostedMorpheDialog(second, {}, {}))

        assertEquals(second, state.current?.id)
        state.hide(second)
        assertEquals(first, state.current?.id)
        state.hide(first)
        assertNull(state.current)
    }


    @Test
    fun `hosted destructive or form dialogs do not dismiss on background clicks`() {
        val state = MorpheDialogHostState()
        val formId = Any()
        state.show(HostedMorpheDialog(formId, {}, {}))
        assertFalse(state.current!!.dismissOnOutside)
        val informationalId = Any()
        state.show(HostedMorpheDialog(informationalId, {}, {}, dismissOnOutside = true))
        assertTrue(state.current!!.dismissOnOutside)
        state.hide(informationalId)
        assertFalse(state.current!!.dismissOnOutside)
    }

    @Test
    fun `showing the same dialog again replaces rather than duplicates it`() {
        val state = MorpheDialogHostState()
        val id = Any()

        state.show(HostedMorpheDialog(id, {}, {}))
        state.show(HostedMorpheDialog(id, {}, {}))
        state.hide(id)

        assertNull(state.current)
    }
}
