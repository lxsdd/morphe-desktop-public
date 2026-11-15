package app.morphe.gui.ui.screens.home.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.util.selectSupportedAppTarget
import app.morphe.gui.util.SupportedTargetChannel

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

    private fun catalog(
        packageName: String = "com.google.android.youtube",
        stable: List<String> = listOf("21.30.216"),
        experimental: List<String> = listOf("21.45.82", "21.44.528", "21.43.523"),
    ) = SupportedApp(
        packageName = packageName,
        displayName = "YouTube",
        supportedVersions = stable,
        experimentalVersions = experimental,
        recommendedVersion = stable.firstOrNull(),
    )

    @Test fun `every metadata-supported experimental build has its own version-scoped web search`() {
        val app = catalog()
        val links = experimentalVersionSearchLinks(app)
        assertEquals(listOf("21.45.82", "21.44.528", "21.43.523"),
            links.map { it.version })
        assertEquals(links.map { SupportedApp.getDownloadUrl(app.packageName, it.version) },
            links.map { it.url })
    }

    @Test fun `experimental lookup does not turn a stable install into an experimental update`() {
        val app = catalog()
        val stableTarget = selectSupportedAppTarget(
            app.recommendedVersion, app.experimentalVersions, "21.18.164")
        assertEquals("21.30.216", stableTarget.version)
        assertEquals(SupportedTargetChannel.STABLE, stableTarget.channel)
        assertEquals(3, experimentalVersionSearchLinks(app).size)
    }

    @Test fun `lookup excludes duplicate unsupported and empty source entries`() {
        val app = catalog(experimental = listOf(
            "v21.45.82", "21.45.82", "", "v21.30.216", "21.44.528"))
        assertEquals(listOf("21.45.82", "21.44.528"),
            experimentalVersionSearchLinks(app).map { it.version })
        assertTrue(experimentalVersionSearchLinks(null).isEmpty())
        assertTrue(experimentalVersionSearchLinks(catalog(packageName = "")).isEmpty())
        assertTrue(experimentalVersionSearchLinks(catalog(experimental = emptyList())).isEmpty())
    }
}
