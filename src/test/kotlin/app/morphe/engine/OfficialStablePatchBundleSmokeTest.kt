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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real release assets downloaded and SHA-256 pinned by the private Ubuntu CI.
 * These tests only load patch metadata/classes. They never patch an APK, change
 * signing trust, install on devices or touch the user's own data.
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

    /** Verify actual Patcher-1.8.0 community MPP releases against the current 2.3.0 loader. */
    @Test
    fun legacyCommunityBundlesLoadAlongsideOfficialAndRemainPerSource() = runBlocking {
        val officialPath = System.getenv("MORPHE_OFFICIAL_PATCHES_TEST_FILE")
            ?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val amazonPath = System.getenv("MORPHE_THIRDPARTY_PATCHES_TEST_FILE")
            ?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val rikyPath = System.getenv("MORPHE_RIKY_PATCHES_TEST_FILE")
            ?.takeIf { it.isNotBlank() }
        val official = File(officialPath)
        val amazon = File(amazonPath)
        val riky = rikyPath?.let(::File)
        val fixtures = listOfNotNull(
            MultiSourceLoader.SourceInput("amazon", "rushiranpise Morphe Patches", amazon),
            riky?.let { MultiSourceLoader.SourceInput("riky", "riky Morphe Patches", it) },
            MultiSourceLoader.SourceInput("official", "Morphe Patches", official),
        )
        assertTrue(fixtures.all { it.patchFile.isFile && it.patchFile.length() > 1024 })
        assertEquals("2.3.0", PatcherCompatibility.currentPatcherVersion)
        assertNull(PatchBundleSignature.verify(amazon), "Unexpected signer change requires trust review")
        assertEquals("1.8.0", PatcherCompatibility.requiredPatcherVersion(amazon))
        riky?.let { assertEquals("1.8.0", PatcherCompatibility.requiredPatcherVersion(it)) }
        fixtures.forEach { assertNull(PatcherCompatibility.incompatibilityMessage(it.patchFile)) }
        // Loading is read-only. It does not enroll trust or bypass the patch
        // source repository's signer-verification and user-decision rules.
        val loaded = MultiSourceLoader.load(fixtures)
        assertEquals(fixtures.map { it.sourceId }, loaded.perSource.map { it.sourceId })
        loaded.perSource.forEach { source ->
            assertTrue(source.isSuccess, "${source.sourceName} failed to load: ${source.error?.readableMessage()}")
            assertTrue(source.patches.isNotEmpty(), "${source.sourceName} has no patches")
        }
        assertEquals(loaded.perSource.flatMap { it.patches }.toSet(), loaded.allPatches)

        // The production GUI only includes successfully loaded sources in
        // patch configurations. The legacy ones must not be filtered merely
        // because their manifest reports Patcher 1.8.0.
        val snapshot = EnabledSourcesLoader.Result(
            resolved = fixtures.map {
                val type = if (it.sourceId == "official") PatchSourceType.DEFAULT else PatchSourceType.GITHUB
                EnabledSourcesLoader.ResolvedSource(
                    PatchSource(it.sourceId, it.sourceName, type), it.patchFile,
                )
            },
            loaded = loaded,
            unionPatches = emptyList(),
            patchesBySource = emptyMap(),
        )
        assertEquals(fixtures.map { it.sourceId }, snapshot.usableResolvedSources.map { it.source.id })
        assertEquals(fixtures.map { it.patchFile }, snapshot.usableResolvedSources.mapNotNull { it.patchFile })

        // Direct PatchEngine/CLI consumers share the same compatibility contract.
        PatchBundleLoader.loadEach(fixtures.map { it.patchFile }).forEachIndexed { index, bundle ->
            assertEquals(fixtures[index].patchFile, bundle.sourceFile)
            assertTrue(bundle.patches.isNotEmpty())
        }
    }
}
