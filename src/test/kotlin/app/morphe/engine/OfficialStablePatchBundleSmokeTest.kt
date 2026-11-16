package app.morphe.engine

import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.SupportedAppCatalog
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
        assertEquals("2.4.0", PatcherCompatibility.currentPatcherVersion)
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

    /** Verify actual Patcher-1.8.0 community MPP releases against the current 2.4.0 loader. */
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
        assertEquals("2.4.0", PatcherCompatibility.currentPatcherVersion)
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

    /**
     * Load six actual pinned current MPP releases with Patcher 2.4.0.
     * A listed version is only qualified if actual loaded patch metadata exposes it.
     * No APK, signer, device, or local user data is modified.
     */
    @Test
    fun latestOfficialAndCommunityBundleRuntimeAndTargets() = runBlocking {
        val sourceEnv = listOf(
            Triple("official23", "Official Patches 2.3.0", "MORPHE_OFFICIAL_LATEST_PATCHES_TEST_FILE"),
            Triple("piko", "Piko 3.11.1", "MORPHE_PIKO_PATCHES_TEST_FILE"),
            Triple("froggo", "Froggo GitHub-latest 1.2.4", "MORPHE_FROGGO_PATCHES_TEST_FILE"),
            Triple("hoodles", "Hoodles 1.47.0", "MORPHE_HOODLES_PATCHES_TEST_FILE"),
            Triple("patchedup", "Patched-Up 1.10.0", "MORPHE_PATCHEDUP_PATCHES_TEST_FILE"),
            Triple("aliexpress", "AliExpress 1.0.7", "MORPHE_ALIEXPRESS_PATCHES_TEST_FILE"),
        )
        val files = sourceEnv.map { System.getenv(it.third)?.takeIf(String::isNotBlank) }
        if (files.all { it == null }) return@runBlocking
        assertTrue(files.all { it != null }, "Partially provisioned current-source fixtures")
        val inputs = sourceEnv.mapIndexed { idx, (id, name, _) ->
            MultiSourceLoader.SourceInput(id, name, File(files[idx]!!))
        }
        assertEquals("2.4.0", PatcherCompatibility.currentPatcherVersion)
        assertTrue(inputs.all { it.patchFile.isFile && it.patchFile.length() > 1024 })
        inputs.forEach { assertNull(PatcherCompatibility.incompatibilityMessage(it.patchFile)) }
        val loaded = MultiSourceLoader.load(inputs)
        assertEquals(inputs.map { it.sourceId }, loaded.perSource.map { it.sourceId })
        val targets = mapOf(
            "official23" to listOf(
                "com.google.android.youtube" to "21.45.82",
                "com.google.android.apps.youtube.music" to "9.46.51",
                "com.reddit.frontpage" to "2026.46.0",
            ),
            "piko" to listOf("com.instagram.android" to "452.1.0.53.64"),
            "froggo" to listOf("com.facebook.katana" to "573.0.0.37.74"),
            "hoodles" to listOf("com.soundcloud.android" to "2026.08.26-release"),
            "patchedup" to listOf("com.ebay.kleinanzeigen" to "2026.16.1"),
            "aliexpress" to listOf("com.alibaba.aliexpresshd" to "8.162.8"),
        )
        loaded.perSource.forEach { source ->
            assertTrue(source.isSuccess,
                "${source.sourceName} failed to load: ${source.error?.readableMessage()}")
            assertTrue(source.patches.isNotEmpty(), "${source.sourceName} returned no patches")
            val byPackage = SupportedAppCatalog.extractSupportedApps(source.patches)
                .associateBy { it.packageName }
            targets.getValue(source.sourceId).forEach { (name, version) ->
                val app = byPackage[name]
                assertTrue(app != null, "${source.sourceName}: missing $name")
                assertTrue(version in app.supportedVersions || version in app.experimentalVersions,
                    "${source.sourceName}: missing $name $version")
            }
        }
    }

}
