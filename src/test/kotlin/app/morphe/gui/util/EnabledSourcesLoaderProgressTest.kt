package app.morphe.gui.util

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import java.io.File
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual resolver -> progress callback on nonexisting local fixtures, no user data. */
class EnabledSourcesLoaderProgressTest {
    @TempDir lateinit var root: File

    @Test fun everySourceCompletesResolutionEvenWhenBothSourcesAreUnavailable() = runBlocking {
        val sources = listOf("a", "b").map { id ->
            PatchSource(
                id = id,
                name = "Missing source $id",
                type = PatchSourceType.LOCAL,
                filePath = File(root, "$id-not-present.mpp").absolutePath,
            ) to null
        }
        val events = Collections.synchronizedList(mutableListOf<EnabledSourcesLoader.SourceLoadProgress>())
        val result = EnabledSourcesLoader.loadAll(
            enabled = sources,
            onSourceProgress = { events.add(it) },
        )
        assertFalse(result.anyLoaded)
        assertEquals(2, result.resolved.size)
        assertTrue(result.resolved.all { it.error != null })
        assertTrue(events.any { it.completed == 0 && it.total == 2 })
        assertTrue(events.any { it.completed == 2 && it.total == 2 && it.activeSource == null })
        assertTrue(events.all { it.completed in 0..2 && it.total == 2 })
    }

    @Test fun disabledSourceSetNeverStartsOrClaimsACompletedUpdate() = runBlocking {
        val events = mutableListOf<EnabledSourcesLoader.SourceLoadProgress>()
        val result = EnabledSourcesLoader.loadAll(
            enabled = emptyList(),
            onSourceProgress = { events.add(it) },
        )
        assertFalse(result.anyLoaded)
        assertTrue(result.resolved.isEmpty())
        assertTrue(events.isEmpty())
    }
}
