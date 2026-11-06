package app.morphe.gui

import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * The Custom 2.0 UI previously compiled but failed at runtime with
 * Resource with ID='string:nav_sources' not found. Exercise actual
 * Compose resource loading, not just generated Kotlin resource IDs.
 */
class ComposeNavigationResourceSmokeTest {
    @Test
    fun navigationStringsCanBeLoadedFromRuntimeResources() = runBlocking {
        val sourceLabel = getString(Res.string.nav_sources)
        val appsLabel = getString(Res.string.nav_apps)
        assertTrue(sourceLabel.isNotBlank(), "Sources navigation label must load")
        assertTrue(appsLabel.isNotBlank(), "Apps navigation label must load")
    }
}
