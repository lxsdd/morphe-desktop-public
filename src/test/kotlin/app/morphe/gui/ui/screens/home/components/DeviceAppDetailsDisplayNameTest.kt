package app.morphe.gui.ui.screens.home.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceAppDetailsDisplayNameTest {
    @Test fun `on device Android name appears in detail header`() {
        assertEquals("Deezer", deviceAppDetailsTitle("com.example.deezer", "Old source name", "Deezer"))
    }

    @Test fun `catalog name remains available before Android resolves`() {
        assertEquals("My App", deviceAppDetailsTitle("com.example.app", "My App", null))
    }

    @Test fun `unknown identity stays precise when no trustworthy name exists`() {
        assertEquals("com.example.unknown", deviceAppDetailsTitle("com.example.unknown", null, null))
    }

    @Test fun `technical package label cannot override a readable name`() {
        assertEquals("Camera", deviceAppDetailsTitle("com.example.camera", "Camera", "com.example.camera"))
    }
}
