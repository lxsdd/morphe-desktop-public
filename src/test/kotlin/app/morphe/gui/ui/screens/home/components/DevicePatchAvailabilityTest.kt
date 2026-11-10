package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.CompatiblePackage
import app.morphe.engine.model.PatchMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicePatchAvailabilityTest {
    private val youtube = "com.google.android.youtube"
    private val music = "com.google.android.apps.youtube.music"

    private val patches = listOf(
        PatchMetadata("YouTube-compatible", "A catalog patch", compatiblePackages = listOf(
            CompatiblePackage(youtube, versions = listOf("21.20.405")),
        )),
        PatchMetadata("Music-only", compatiblePackages = listOf(
            CompatiblePackage(music, versions = listOf("9.20.53")),
        )),
        PatchMetadata("YouTube-compatible", "A catalog patch", compatiblePackages = listOf(
            CompatiblePackage(youtube, versions = listOf("21.20.405")),
        )),
        PatchMetadata("Universal only", compatiblePackages = emptyList()),
    )

    @Test
    fun `YouTube is visible without any patched history`() {
        val result = availableDevicePatches(patches, youtube, "21.20.405")
        assertEquals(listOf("YouTube-compatible"), result.map { it.name })
        assertTrue(result.single().compatibleWithInstalledVersion == true)
    }

    @Test
    fun `YouTube Music uses its own package compatibility`() {
        val result = availableDevicePatches(patches, music, "9.20.53")
        assertEquals(listOf("Music-only"), result.map { it.name })
        assertTrue(result.single().compatibleWithInstalledVersion == true)
    }

    @Test
    fun `older installed app does not inherit new-version compatibility`() {
        val result = availableDevicePatches(patches, youtube, "21.18.164")
        assertFalse(result.single().compatibleWithInstalledVersion!!)
    }

    @Test
    fun `missing device version stays unknown rather than compatible`() {
        val result = availableDevicePatches(patches, youtube, null)
        assertNull(result.single().compatibleWithInstalledVersion)
    }

    @Test
    fun `unrelated and uncatalogued packages do not inherit patches`() {
        assertTrue(availableDevicePatches(patches, "com.example.other", "1.0").isEmpty())
        assertTrue(availableDevicePatches(patches, "", "1.0").isEmpty())
    }
}
