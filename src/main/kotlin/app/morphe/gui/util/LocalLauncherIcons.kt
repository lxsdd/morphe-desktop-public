/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.morphe.engine.apk.BundleBaseApkSelection
import app.morphe.engine.apk.BundleFormats
import app.morphe.gui.LocalShowLocalAppIcons
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Narrow port of official 2.2 local launcher icons.
 *
 * Never reads Android devices or persistent user history, and never writes a
 * cache, keystore, file or device. The selected icon is decorative only.
 * Unavailable/malformed resources leave the existing avatar initial visible.
 */
internal object LocalLauncherIcons {
    private const val MAX_RESOURCE_BYTES = 16 * 1024 * 1024
    private const val MAX_ICON_SOURCES = 7
    private val mutex = Mutex()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()
    private val failures = ConcurrentHashMap.newKeySet<String>()

    internal fun key(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        if (!BundleFormats.isApkOrBundle(file) || file.length() <= 0L) return null
        return runCatching {
            "${file.canonicalPath}|${file.length()}|${file.lastModified()}"
        }.getOrNull()
    }

    internal suspend fun load(filePath: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        val identity = key(filePath) ?: return@withContext null
        icons[identity]?.let { return@withContext it }
        if (identity in failures) return@withContext null
        mutex.withLock {
            icons[identity]?.let { return@withLock it }
            if (identity in failures) return@withLock null
            val bitmap = try {
                val file = File(filePath!!)
                val image = if (BundleFormats.isBundle(file)) fromBundle(file) else fromApk(file)
                image?.toComposeImageBitmap()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (bitmap == null) failures.add(identity) else icons[identity] = bitmap
            bitmap
        }
    }

    /** Max 16 MiB uncompressed per resource, including ZIP entries reporting unknown size. */
    private fun readEntry(zip: ZipFile, name: String): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        if (entry.isDirectory || entry.size > MAX_RESOURCE_BYTES) return null
        return zip.getInputStream(entry).use { it.readNBytes(MAX_RESOURCE_BYTES + 1) }
            .takeIf { it.size <= MAX_RESOURCE_BYTES }
    }

    private suspend fun fromApk(file: File): org.jetbrains.skia.Image? =
        ZipFile(file).use { zip ->
            AppIconExtractor.extract(192) { path -> readEntry(zip, path) }
        }

    /**
     * APKM/XAPK/APKS may nest base and density APKs. Read their resources as
     * streams; do not unpack even temporary copies or inspect user device state.
     */
    private suspend fun fromBundle(file: File): org.jetbrains.skia.Image? =
        ZipFile(file).use { outer ->
            val names = outer.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
            val base = BundleBaseApkSelection.select(names) ?: return@use null
            val density = names.filter {
                it != base && it.endsWith(".apk", ignoreCase = true) &&
                    it.substringAfterLast('/').contains("dpi", ignoreCase = true)
            }.take(MAX_ICON_SOURCES - 1)
            AppIconExtractor.extract(192) { path ->
                (listOf(base) + density).firstNotNullOfOrNull { candidate ->
                    val entry = outer.getEntry(candidate)
                    if (entry == null || entry.size > 1024L * 1024L * 1024L) return@firstNotNullOfOrNull null
                    outer.getInputStream(entry).use { nestedBytes ->
                        ZipInputStream(nestedBytes).use { nested ->
                            var resource = nested.nextEntry
                            while (resource != null) {
                                if (resource.name == path && !resource.isDirectory && resource.size <= MAX_RESOURCE_BYTES) {
                                    val bytes = nested.readNBytes(MAX_RESOURCE_BYTES + 1)
                                    return@firstNotNullOfOrNull bytes.takeIf { it.size <= MAX_RESOURCE_BYTES }
                                }
                                nested.closeEntry()
                                resource = nested.nextEntry
                            }
                            null
                        }
                    }
                }
            }
        }
}

@Composable
internal fun rememberLocalLauncherIcon(outputPath: String?, inputPath: String?): ImageBitmap? {
    val enabled = LocalShowLocalAppIcons.current.value
    // Disabled mode never probes local files, launches ADB or exposes a cached icon.
    val outputKey = if (enabled) LocalLauncherIcons.key(outputPath) else null
    val inputKey = if (enabled) LocalLauncherIcons.key(inputPath) else null
    val icon by produceState<ImageBitmap?>(null, enabled, outputKey, inputKey) {
        value = if (enabled) {
            LocalLauncherIcons.load(outputPath) ?: LocalLauncherIcons.load(inputPath)
        } else null
    }
    return icon
}
