package app.morphe.gui.ui.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutlinePolicyTest {
    @Test fun defaultVisibleAndExplicitlyEnabledDecorations() {
        assertTrue(shouldDrawOutline(appearanceEnabled = true))
        assertTrue(shouldDrawOutline(appearanceEnabled = true, required = false))
    }

    @Test fun disablingDecorationsRemovesOnlyOptionalBorders() {
        assertFalse(shouldDrawOutline(appearanceEnabled = false))
        assertFalse(shouldDrawOutline(appearanceEnabled = false, required = false))
    }

    @Test fun focusErrorAndExplicitOutlineIndicationsCannotBeHidden() {
        assertTrue(shouldDrawOutline(appearanceEnabled = false, required = true))
        assertTrue(shouldDrawOutline(appearanceEnabled = true, required = true))
    }
}
