package app.morphe.engine

import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.patcher.patch.PatchBundleSignature
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Actual official 2.1.0 release, hash-pinned by the public CI workflow.
 * No device interactions, patch writes, keys or user files.
 */
class OfficialStablePatchBundleSmokeTest {
    @Test
    fun latestOfficialStableBundleSignatureAndRuntimeLoad() = runBlocking {
        val path = System.getenv("MORPHE_OFFICIAL_PATCHES_TEST_FILE")
            ?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val file = File(path)
        assertTrue(file.isFile && file.length() > 1024)
        assertEquals("2.3.0", PatcherCompatibility.currentPatcherVersion)
        assertEquals(
            RemotePatchSourceFactory.OFFICIAL_PATCHES_SIGNING_KEY,
            PatchBundleSignature.verify(file),
            "Stable patch release must keep the expected official signer"
        )
        assertNull(PatcherCompatibility.incompatibilityMessage(file))
        val result = MultiSourceLoader.load(
            listOf(MultiSourceLoader.SourceInput("official", "Morphe Patches", file))
        )
        val loaded = result.perSource.single()
        assertTrue(loaded.isSuccess, "Official stable patch release failed: ${loaded.error?.message}")
        assertTrue(loaded.patches.isNotEmpty(), "Stable patch release unexpectedly has no patches")

        // A failing source copy must not abort the good official bundle. This
        // reproduces the source-loader failure that formerly escaped the try.
        val missing = File(file.parentFile, "missing-source-${System.nanoTime()}.mpp")
        assertFalse(missing.exists())
        val mixed = MultiSourceLoader.load(
            listOf(
                MultiSourceLoader.SourceInput("missing", "Missing source", missing),
                MultiSourceLoader.SourceInput("official", "Morphe Patches", file),
            )
        )
        assertEquals(listOf("missing", "official"), mixed.perSource.map { it.sourceId })
        assertFalse(mixed.perSource[0].isSuccess)
        assertTrue(mixed.perSource[0].patches.isEmpty())
        assertTrue(mixed.perSource[1].isSuccess)
        assertTrue(mixed.perSource[1].patches.isNotEmpty())
        assertEquals(mixed.perSource[1].patches, mixed.allPatches)
    }
}
