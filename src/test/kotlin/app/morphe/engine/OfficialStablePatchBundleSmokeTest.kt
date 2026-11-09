package app.morphe.engine

import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.patcher.patch.PatchBundleSignature
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        assertEquals(mixed.perSource[1].patches.toSet(), mixed.allPatches)
    }

    /**
     * SHA-256 pinned public rushiranpise v1.23.1 is the historically configured
     * Amazon source. Loading it alongside official 2.1.0 under Patcher 2.3.0
     * is a compatibility check only; it NEVER records signer trust on disk.
     */
    @Test
    fun amazonThirdPartyStableLoadsAlongsideOfficialWithoutTrustMutation() = runBlocking {
        val officialPath = System.getenv("MORPHE_OFFICIAL_PATCHES_TEST_FILE")
            ?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val thirdPath = System.getenv("MORPHE_THIRDPARTY_PATCHES_TEST_FILE")
            ?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val official = File(officialPath)
        val third = File(thirdPath)
        assertTrue(official.isFile && official.length() > 1024)
        assertTrue(third.isFile && third.length() > 1024)
        assertEquals("2.3.0", PatcherCompatibility.currentPatcherVersion)
        // The third-party public signature is observed, never automatically trusted.
        assertNotNull(PatchBundleSignature.verify(third), "Third-party stable MPP must carry a signature")
        assertNull(PatcherCompatibility.incompatibilityMessage(third))
        val mixed = MultiSourceLoader.load(
            listOf(
                MultiSourceLoader.SourceInput("official", "Morphe Patches", official),
                MultiSourceLoader.SourceInput("amazon", "rushiranpise Morphe Patches", third),
            )
        )
        assertEquals(listOf("official", "amazon"), mixed.perSource.map { it.sourceId })
        mixed.perSource.forEach { source ->
            assertTrue(source.isSuccess, "Source ${source.sourceName} failed: ${source.error?.message}")
            assertTrue(source.patches.isNotEmpty(), "Source ${source.sourceName} loaded no patches")
            assertTrue(mixed.allPatches.containsAll(source.patches))
        }
    }
}
