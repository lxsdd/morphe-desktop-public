package app.morphe.engine

import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.patcher.patch.PatchBundleSignature
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
     * This historically configured Amazon bundle is hash-pinned but is UNSIGNED
     * and requires Patcher 1.8.0. It must NOT be accepted as a compatible
     * source by Desktop 2.1.1 / Patcher 2.3.0. The official source remains usable.
     * Read-only verification; never enroll signer trust or alter preferences.
     */
    @Test
    fun amazonThirdPartyLegacyBundleIsRejectedWhileOfficialStillLoads() = runBlocking {
        val officialPath = System.getenv("MORPHE_OFFICIAL_PATCHES_TEST_FILE")
            ?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val thirdPath = System.getenv("MORPHE_THIRDPARTY_PATCHES_TEST_FILE")
            ?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val official = File(officialPath)
        val third = File(thirdPath)
        assertTrue(official.isFile && official.length() > 1024)
        assertTrue(third.isFile && third.length() > 1024)
        assertEquals("2.3.0", PatcherCompatibility.currentPatcherVersion)
        assertNull(PatchBundleSignature.verify(third), "Unexpected signature change requires trust review")
        assertEquals("1.8.0", PatcherCompatibility.requiredPatcherVersion(third))
        val incompatible = PatcherCompatibility.incompatibilityMessage(third)
        assertTrue(incompatible?.contains("requires Morphe Patcher 1.8.0") == true)
        assertTrue(incompatible.contains("ships 2.3.0"))
        assertNull(PatcherCompatibility.incompatibilityMessage(official))
        val loaded = MultiSourceLoader.load(
            listOf(
                MultiSourceLoader.SourceInput("amazon", "rushiranpise Morphe Patches", third),
                MultiSourceLoader.SourceInput("official", "Morphe Patches", official),
            )
        )
        assertEquals(listOf("amazon", "official"), loaded.perSource.map { it.sourceId })
        val rejected = loaded.perSource[0]
        assertFalse(rejected.isSuccess)
        assertTrue(rejected.error is PatchBundleIncompatibleException)
        assertTrue(rejected.error?.message?.contains("requires Morphe Patcher 1.8.0") == true)
        assertTrue(rejected.patches.isEmpty())
        val compatible = loaded.perSource[1]
        assertTrue(compatible.isSuccess)
        assertTrue(compatible.patches.isNotEmpty())
        assertEquals(compatible.patches.toSet(), loaded.allPatches)

        // The UI cannot carry a failed but downloaded source into patching.
        val snapshot = EnabledSourcesLoader.Result(
            resolved = listOf(
                EnabledSourcesLoader.ResolvedSource(
                    PatchSource("amazon", "Amazon Patches", PatchSourceType.GITHUB), third,
                ),
                EnabledSourcesLoader.ResolvedSource(
                    PatchSource("official", "Morphe Patches", PatchSourceType.DEFAULT), official,
                ),
            ),
            loaded = loaded,
            unionPatches = emptyList(),
            patchesBySource = emptyMap(),
        )
        assertEquals(listOf("official"), snapshot.usableResolvedSources.map { it.source.id })
        assertEquals(listOf(official), snapshot.usableResolvedSources.mapNotNull { it.patchFile })

        // Direct PatchEngine/CLI consumers have a matching independent guard.
        val rejection = assertFailsWith<PatchBundleIncompatibleException> {
            PatchBundleLoader.loadEach(listOf(third))
        }
        assertTrue(rejection.message?.contains("requires Morphe Patcher 1.8.0") == true)
        assertTrue(PatchBundleLoader.loadEach(listOf(official)).single().patches.isNotEmpty())
    }
}
