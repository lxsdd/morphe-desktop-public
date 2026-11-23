/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.deleteRecursivelyInParallel
import app.morphe.engine.workspace.WorkspaceManager
import java.io.File

/** Manages cache sizing and deletion, preserving patch-source signer trust data. */
object CacheManager {

    /** Read-only directory accounting; unlike cache clearing, never removes files. */
    data class DirStats(val bytes: Long, val fileCount: Int)

    /** Summary of files and bytes cleared from a single directory. */
    data class DirResult(
        val label: String,
        val bytesFreed: Long,
        val filesDeleted: Int,
        val failedFiles: Int,
    )

    /** Overall outcome of a [clearCaches] run. */
    data class ClearResult(
        val perDirectory: List<DirResult>,
    ) {
        val bytesFreed: Long get() = perDirectory.sumOf { it.bytesFreed }
        val failedFiles: Int get() = perDirectory.sumOf { it.failedFiles }
        val success: Boolean get() = failedFiles == 0
    }

    /** Calculates total byte size across patches, logs, and scratch directories. */
    fun getCacheSizeBytes(): Long =
        getPatchDownloadSize(MorpheData.patchesDir) +
            getDirectorySize(MorpheData.logsDir) +
            getDirectorySize(MorpheData.avatarsDir) +
            getDirectorySize(MorpheData.appIconsDir) +
            WorkspaceManager.getScratchSize()

    /** Computes total byte size of files inside [dir]. */
    fun getDirectorySize(dir: File): Long = getDirectoryStats(dir).bytes

    /** Count normal directory contents without applying deletion semantics. */
    fun getDirectoryStats(dir: File): DirStats = try {
        var bytes = 0L
        var count = 0
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            bytes += file.length()
            count++
        }
        DirStats(bytes, count)
    } catch (_: Exception) {
        DirStats(0L, 0)
    }

    /** Read-only counts of downloadable .mpp/.jar bundles only, excluding signer pins. */
    fun getPatchDownloadStats(dir: File): DirStats = try {
        var bytes = 0L
        var count = 0
        if (dir.isDirectory) dir.walkTopDown()
            .filter { it.isFile && (it.extension.equals("mpp", true) || it.extension.equals("jar", true)) }
            .forEach { file ->
                bytes += file.length()
                count++
            }
        DirStats(bytes, count)
    } catch (_: Exception) {
        DirStats(0L, 0)
    }

    /** Deletes cached patches, logs, and temporary scratch files. */
    fun clearCaches(): ClearResult = ClearResult(
        listOf(
            clearPatchDownloads("Patches", MorpheData.patchesDir),
            clearDirectoryContents("Logs", MorpheData.logsDir),
            WorkspaceManager.clearScratch(),
            clearDirectoryContents("Avatars", MorpheData.avatarsDir),
            clearDirectoryContents("App icons", MorpheData.appIconsDir),
        ),
    )

    /**
     * Only delete downloaded .mpp/.jar files. Trust pins (signing.key),
     * signer refusal evidence, and unrelated source metadata are NOT cache.
     * This shared primitive covers both per-source and global cache clearing.
     */
    fun clearPatchDownloads(label: String, dir: File): DirResult {
        val downloads = try {
            if (!dir.exists()) emptyList()
            else if (!dir.isDirectory) return DirResult(label, 0L, 0, 1)
            else dir.walkTopDown().filter { file ->
                file.isFile && (file.extension.equals("mpp", ignoreCase = true) ||
                    file.extension.equals("jar", ignoreCase = true))
            }.toList()
        } catch (_: Exception) {
            return DirResult(label, 0L, 0, 1)
        }
        var bytesFreed = 0L
        var filesDeleted = 0
        var failedFiles = 0
        downloads.forEach { file ->
            try {
                val bytes = file.length()
                if (file.delete()) {
                    bytesFreed += bytes
                    filesDeleted++
                } else {
                    failedFiles++
                }
            } catch (_: Exception) {
                failedFiles++
            }
        }
        return DirResult(label, bytesFreed, filesDeleted, failedFiles)
    }

    /** Only count downloadable patch cache bytes, not persistent signer pins. */
    fun getPatchDownloadSize(dir: File): Long = try {
        if (!dir.isDirectory) 0L else dir.walkTopDown()
            .filter { it.isFile && (it.extension.equals("mpp", true) || it.extension.equals("jar", true)) }
            .sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }

    /** Deletes general cache contents, but protects signer pins in the patch cache. */
    fun clearDirectoryContents(label: String, dir: File): DirResult {
        // Storage UI callers may still address the root cache directly.
        if (runCatching { dir.canonicalFile == MorpheData.patchesDir.canonicalFile }.getOrDefault(false)) {
            return clearPatchDownloads(label, dir)
        }
        var bytesFreed = 0L
        var filesDeleted = 0
        var failedFiles = 0

        val entries = try {
            dir.listFiles() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        entries.forEach { entry ->
            runCatching {
                val files = try {
                    entry.walkTopDown().filter { it.isFile }.toList()
                } catch (_: Exception) {
                    emptyList()
                }
                val size = files.sumOf { it.length() }
                val deleted = if (entry.isDirectory) {
                    entry.deleteRecursivelyInParallel()
                } else {
                    entry.delete()
                }
                if (deleted) {
                    bytesFreed += size
                    filesDeleted += if (files.isNotEmpty()) files.size else 1
                } else {
                    failedFiles++
                }
            }.onFailure {
                failedFiles++
            }
        }
        return DirResult(label, bytesFreed, filesDeleted, failedFiles)
    }
}
