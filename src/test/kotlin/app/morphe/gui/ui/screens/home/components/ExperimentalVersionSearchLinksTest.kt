package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.MorpheConstants.MORPHE_API_URL
import app.morphe.engine.model.SupportedApp
import app.morphe.patcher.patch.FormFactor
import app.morphe.patcher.patch.SupportedAbi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentalVersionSearchLinksTest {
    @Test
    fun onlyUniqueExperimentalVersionsProduceBuildAndFormFactorAwareLookup() {
        val catalog = SupportedApp(
            packageName = "com.example",
            displayName = "Example",
            supportedVersions = listOf("1.0", "v2.0"),
            experimentalVersions = listOf("v2.0", "3.0", "v3.0", "4.0", ""),
            recommendedVersion = "1.0",
            versionAbiBuildCodes = mapOf("3.0" to mapOf(SupportedAbi.ARM64_V8A to 71)),
            formFactor = FormFactor.TV,
        )
        val links = experimentalVersionSearchLinks(catalog)
        assertEquals(listOf("3.0", "4.0"), links.map { it.version })
        assertEquals("$MORPHE_API_URL/v2/web-search/com.example~3.0~all~71~tv", links[0].url)
        assertEquals("$MORPHE_API_URL/v2/web-search/com.example~4.0~all~tv", links[1].url)
    }

    @Test
    fun missingCatalogAndEmptyPackageDoNotCreateSearchLinks() {
        assertTrue(experimentalVersionSearchLinks(null).isEmpty())
        val catalog = SupportedApp(
            packageName = "", displayName = "", supportedVersions = emptyList(),
            experimentalVersions = listOf("3.0"), recommendedVersion = null,
        )
        assertTrue(experimentalVersionSearchLinks(catalog).isEmpty())
    }
}
