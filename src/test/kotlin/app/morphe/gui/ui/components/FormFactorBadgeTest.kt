package app.morphe.gui.ui.components

import app.morphe.patcher.patch.FormFactor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormFactorBadgeTest {
    @Test fun `unknown and mobile targets must not be mislabeled`() {
        assertFalse((null as FormFactor?).hasVisibleFormFactorBadge())
        assertFalse(FormFactor.MOBILE.hasVisibleFormFactorBadge())
    }

    @Test fun `non mobile patch metadata surfaces declared form factor`() {
        assertTrue(FormFactor.TV.hasVisibleFormFactorBadge())
        assertTrue(FormFactor.WEARABLE.hasVisibleFormFactorBadge())
        assertTrue(FormFactor.AUTOMOTIVE.hasVisibleFormFactorBadge())
    }
}
