package app.morphe.engine

import java.io.File
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Isolated scratch files, never the user's real Morphe root or signing material. */
class CacheManagerStatsTest {
    @TempDir lateinit var dir: File

    @Test fun downloadedPatchStatisticsExcludeTrustAndUserMetadata() {
        val patch = File(dir, "release.mpp").apply { writeText("official") }
        val legacy = File(dir, "community.JAR").apply { writeText("legacy") }
        val pin = File(dir, "signing.key").apply { writeText("pinned-fingerprint") }
        val refusal = File(dir, "signer-refusal.json").apply { writeText("explicit refusal") }
        val metadata = File(dir, "source.json").apply { writeText("persistent preferences") }

        val before = CacheManager.getPatchDownloadStats(dir)
        assertEquals(2, before.fileCount)
        assertEquals(patch.length() + legacy.length(), before.bytes)

        val result = CacheManager.clearPatchDownloads("Patches", dir)
        assertEquals(0, result.failedFiles)
        assertEquals(2, result.filesDeleted)
        assertFalse(patch.exists())
        assertFalse(legacy.exists())
        assertTrue(pin.isFile)
        assertTrue(refusal.isFile)
        assertTrue(metadata.isFile)
        assertEquals(CacheManager.DirStats(0L, 0), CacheManager.getPatchDownloadStats(dir))
        assertEquals(3, CacheManager.getDirectoryStats(dir).fileCount)
    }

    @Test fun readOnlyDirectoryStatisticsCountNestedCacheFilesWithoutChangingThem() {
        val child = File(dir, "nested").apply { mkdirs() }
        val avatar = File(child, "avatar.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val log = File(dir, "debug.log").apply { writeBytes(byteArrayOf(1, 2)) }
        val before = CacheManager.getDirectoryStats(dir)
        assertEquals(2, before.fileCount)
        assertEquals(5L, before.bytes)
        assertEquals(before, CacheManager.getDirectoryStats(dir))
        assertTrue(avatar.isFile)
        assertTrue(log.isFile)
        assertEquals(CacheManager.DirStats(0L, 0), CacheManager.getDirectoryStats(File(dir, "missing")))
    }
}
