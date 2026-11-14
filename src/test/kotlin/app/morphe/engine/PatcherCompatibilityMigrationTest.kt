package app.morphe.engine

import app.morphe.engine.patches.PatchBundleLoader
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Preserve the official older-bundle compatibility path without weakening newer-engine checks. */
class PatcherCompatibilityMigrationTest {
    @TempDir
    lateinit var root: File

    private fun bundle(patcherVersion: String?): File {
        val file = File.createTempFile("patcher-compat-", ".mpp", root)
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            patcherVersion?.let { mainAttributes.putValue("Patcher-Version", it) }
        }
        JarOutputStream(file.outputStream(), manifest).use { }
        return file
    }

    @Test
    fun `previously rejected older major is allowed into the real loader`() {
        assertFalse(PatcherCompatibility.isIncompatiblePatcherVersion("1.8.0", "2.3.0"))
        assertFalse(PatcherCompatibility.isIncompatiblePatcherVersion("v1.15.1", "2.3.0"))
        val source = bundle("1.8.0")
        assertEquals("1.8.0", PatcherCompatibility.requiredPatcherVersion(source))
        assertNull(PatcherCompatibility.incompatibilityMessage(source))
        // A synthetic manifest-only MPP has no patches. Not pre-rejecting it
        // does NOT claim the contents are a valid or trusted patch bundle.
    }

    @Test
    fun `same major earlier and equal requirements stay allowed`() {
        assertFalse(PatcherCompatibility.isIncompatiblePatcherVersion("2.0.0", "2.3.0"))
        assertFalse(PatcherCompatibility.isIncompatiblePatcherVersion("2.3.0", "2.3.0"))
        assertNull(PatcherCompatibility.incompatibilityMessage(bundle("2.0.0")))
    }

    @Test
    fun `newer major and newer minor require an engine update`() {
        assertTrue(PatcherCompatibility.isIncompatiblePatcherVersion("3.0.0", "2.3.0"))
        assertTrue(PatcherCompatibility.isIncompatiblePatcherVersion("2.4.0", "2.3.0"))
        val file = bundle("2.4.0")
        assertTrue(PatcherCompatibility.incompatibilityMessage(file)?.contains("2.4.0") == true)
        // The production direct PatchEngine/CLI path must refuse before loading.
        assertFailsWith<PatchBundleIncompatibleException> {
            PatchBundleLoader.loadEach(listOf(file))
        }
    }

    @Test
    fun `newer bundle fails its own source while others are unaffected`() = runBlocking {
        val newer = bundle("3.0.0")
        val older = bundle("1.8.0")
        val result = MultiSourceLoader.load(
            listOf(
                MultiSourceLoader.SourceInput("future", "Future", newer),
                MultiSourceLoader.SourceInput("legacy", "Legacy", older),
            )
        )
        assertEquals(listOf("future", "legacy"), result.perSource.map { it.sourceId })
        assertTrue(result.perSource[0].error is PatchBundleIncompatibleException)
        assertFalse(result.perSource[0].isSuccess)
        // Manifest-only fixtures may contain no patches or fail class loading;
        // the key regression is the absence of a version-based rejection.
        assertTrue(PatcherCompatibility.incompatibilityMessage(older) == null)
        assertFalse(result.perSource[1].error is PatchBundleIncompatibleException)
    }

    @Test
    fun `missing or malformed requirement does not invent a major mismatch`() {
        assertFalse(PatcherCompatibility.isIncompatiblePatcherVersion("unknown", "2.3.0"))
        assertFalse(PatcherCompatibility.isIncompatiblePatcherVersion("1.8.0", "unknown"))
        assertNull(PatcherCompatibility.incompatibilityMessage(bundle(null)))
        assertNull(PatcherCompatibility.incompatibilityMessage(bundle("unknown")))
    }

    @Test
    fun `diagnostics still identify legacy actual linkage failures`() {
        assertEquals(PatcherRequirementDirection.LEGACY_MAJOR, patcherRequirementDirection("1.8.0", "2.3.0"))
        assertEquals(PatcherRequirementDirection.LEGACY_MAJOR, patcherRequirementDirection("v1.5.0", "2.3.0"))
        assertEquals(PatcherRequirementDirection.NEWER_REQUIRED, patcherRequirementDirection("3.0.0", "2.3.0"))
        assertEquals(PatcherRequirementDirection.NEWER_REQUIRED, patcherRequirementDirection("2.4.0", "2.3.0"))
        assertEquals(PatcherRequirementDirection.UNKNOWN, patcherRequirementDirection("2.0.0", "2.3.0"))
        assertEquals(PatcherRequirementDirection.UNKNOWN, patcherRequirementDirection(null, "2.3.0"))
        assertEquals(PatcherRequirementDirection.UNKNOWN, patcherRequirementDirection("unknown", "2.3.0"))
    }
}
