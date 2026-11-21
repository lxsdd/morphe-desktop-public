package app.morphe.gui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class X11UiScaleTest {
    @Test fun `parse standard fractional desktop dpi without environmental dependencies`() {
        assertEquals(120f, X11UiScale.parseXftDpi("Xft.dpi:\t120\n"))
        assertEquals(144f, X11UiScale.parseXftDpi("other: value\nXft.dpi: 144\n"))
        assertEquals(144f, X11UiScale.parseXftDpi("Xft.dpi: 144"))
    }

    @Test fun `invalid headless and nonfinite dpi values cannot set a desktop scale`() {
        assertNull(X11UiScale.parseXftDpi(null))
        assertNull(X11UiScale.parseXftDpi(""))
        assertNull(X11UiScale.parseXftDpi("Xft.dpi: nope"))
        assertNull(X11UiScale.parseXftDpi("Xft.dpi: 0"))
        assertNull(X11UiScale.parseXftDpi("Xft.dpi: -120"))
        assertNull(X11UiScale.parseXftDpi("Xft.dpi: Infinity"))
    }

    @Test fun `only fractional desktop scaling not already provided by AWT is added`() {
        assertEquals(1.25f, X11UiScale.desktopScaleRatio(120f, 1f))
        assertEquals(1.5f, X11UiScale.desktopScaleRatio(288f, 2f))
        assertNull(X11UiScale.desktopScaleRatio(96f, 1f))
        assertNull(X11UiScale.desktopScaleRatio(120f, 2f))
        assertNull(X11UiScale.desktopScaleRatio(192f, 0f))
        assertNull(X11UiScale.desktopScaleRatio(Float.POSITIVE_INFINITY, 1f))
    }
}
