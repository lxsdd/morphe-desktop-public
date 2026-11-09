package app.morphe.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic private scratch only; never touches the user's Morphe cache or configured sources. */
class PatchCacheTrustPreservationTest {
    private val root = Files.createTempDirectory("morphe-patches-cache-test").toFile()

    @AfterTest
    fun clean() { root.deleteRecursively() }

    @Test
    fun `clearing patch downloads preserves signing keys refusal markers and unrelated files`() {
        val a = File(root, "repo-a").apply { mkdirs() }
        val b = File(root, "repo-b").apply { mkdirs() }
        val keyA = File(a, "signing.key").apply { writeText("trusted-a") }
        val keyB = File(b, "signing.key").apply { writeText("trusted-b") }
        val refused = File(a, "refused-release").apply { writeText("v2|key") }
        val metadata = File(b, "custom-options.json").apply { writeText("settings") }
        val apk = File(root, "never-remove.apk").apply { writeText("user-data") }
        val patchA = File(a, "v2.1.0__patches.mpp").apply { writeText("test mpp") }
        val patchB = File(b, "v1.0__old.JAR").apply { writeText("test jar") }
        val nested = File(b, "nested").apply { mkdirs() }
        val patchC = File(nested, "bundle.mpp").apply { writeText("nested mpp") }
        val removedBytes = listOf(patchA, patchB, patchC).sumOf { it.length() }

        assertEquals(removedBytes, CacheManager.getPatchDownloadSize(root))
        val result = CacheManager.clearPatchDownloads("Patches", root)

        assertEquals(0, result.failedFiles)
        assertEquals(3, result.filesDeleted)
        assertEquals(removedBytes, result.bytesFreed)
        assertEquals(0L, CacheManager.getPatchDownloadSize(root))
        assertFalse(patchA.exists())
        assertFalse(patchB.exists())
        assertFalse(patchC.exists())
        assertEquals("trusted-a", keyA.readText())
        assertEquals("trusted-b", keyB.readText())
        assertEquals("v2|key", refused.readText())
        assertEquals("settings", metadata.readText())
        assertEquals("user-data", apk.readText())
        assertTrue(a.isDirectory && b.isDirectory && nested.isDirectory)
    }

    @Test
    fun `empty source clears successfully without creating a missing root`() {
        val absent = File(root, "absent-source")
        val result = CacheManager.clearPatchDownloads("Patches", absent)
        assertEquals(0, result.filesDeleted)
        assertEquals(0, result.failedFiles)
        assertFalse(absent.exists())
    }

    @Test
    fun `not-a-directory is reported as failure without deleting it`() {
        val file = File(root, "not-dir").apply { writeText("keep") }
        val result = CacheManager.clearPatchDownloads("Patches", file)
        assertEquals(1, result.failedFiles)
        assertEquals("keep", file.readText())
    }
}
