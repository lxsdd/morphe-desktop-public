package app.morphe.gui.ui.screens.home.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceInventoryDisplayNameTest {
    private fun name(
        installed: String,
        recorded: String? = null,
        original: String? = null,
        originalCatalog: String? = null,
        installedCatalog: String? = null,
        android: String? = null,
    ) = resolveDeviceInventoryDisplayName(installed, recorded, original, originalCatalog, installedCatalog, android)

    @Test fun `renamed YouTube history package fallback shows proper product name`() {
        assertEquals("YouTube", name("app.morphe.android.youtube",
            recorded = "com.google.android.youtube",
            original = "com.google.android.youtube"))
    }

    @Test fun `same renamed YouTube uses source metadata if available`() {
        assertEquals("YouTube", name("app.morphe.android.youtube",
            recorded = "com.google.android.youtube",
            original = "com.google.android.youtube",
            originalCatalog = "YouTube"))
    }

    @Test fun `explicit historical human name takes precedence over catalog and known fallback`() {
        assertEquals("Meine YouTube-App", name("app.morphe.android.youtube",
            recorded = "Meine YouTube-App",
            original = "com.google.android.youtube",
            originalCatalog = "YouTube"))
    }

    @Test fun `installed clone package identity does not hide original catalog human name`() {
        assertEquals("Video", name("com.example.clone",
            recorded = "com.example.clone",
            original = "com.example.original",
            originalCatalog = "Video"))
    }

    @Test fun `original package name as catalog label cannot shadow built-in known name`() {
        assertEquals("YouTube", name("app.morphe.android.youtube",
            recorded = "com.google.android.youtube",
            original = "com.google.android.youtube",
            originalCatalog = "com.google.android.youtube"))
    }

    @Test fun `another original package cannot inherit YouTube known label`() {
        assertEquals("com.example.original", name("app.morphe.android.youtube",
            recorded = "com.example.original",
            original = "com.example.original"))
    }

    @Test fun `YouTube Music known label also survives renamed installation`() {
        assertEquals("YouTube Music", name("app.morphe.android.youtubemusic",
            recorded = "com.google.android.apps.youtube.music",
            original = "com.google.android.apps.youtube.music"))
    }

    @Test fun `unknown package without verifiable name keeps exact identity`() {
        assertEquals("org.example.unknown", name("org.example.unknown"))
    }

    @Test fun `installed app without history still uses matching catalog label`() {
        assertEquals("Browser", name("org.example.browser", installedCatalog = "Browser"))
    }


    @Test fun `on-device Android label overrides stale history and catalog names`() {
        assertEquals("Deezer", name("org.example.deezer",
            recorded = "Old name", originalCatalog = "Old catalog", android = "Deezer"))
    }

    @Test fun `unknown app gets Android label without hard-coded mapping`() {
        assertEquals("Camera", name("org.example.camera", android = "Camera"))
    }

    @Test fun `Android package identifier cannot override a readable historical name`() {
        assertEquals("Friendly", name("org.example.camera", recorded = "Friendly",
            android = "org.example.camera"))
    }

    @Test fun `blank names do not become blank titles`() {
        assertEquals("org.example.unknown",
            name("org.example.unknown", recorded = "  ", originalCatalog = " "))
    }
}
