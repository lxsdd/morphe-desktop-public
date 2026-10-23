package app.morphe.gui.ui.screens.home.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceVersionCodeUpdateWarningTest {
    @Test
    fun `maximum manifest version code is flagged`() {
        assertTrue(hasPlayUpdateSuppressingVersionCode(Int.MAX_VALUE.toLong()))
    }

    @Test
    fun `ordinary and missing version codes are not flagged`() {
        assertFalse(hasPlayUpdateSuppressingVersionCode(1243240206L))
        assertFalse(hasPlayUpdateSuppressingVersionCode(null))
    }
}
