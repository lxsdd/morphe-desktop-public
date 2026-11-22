/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.engine

import app.morphe.engine.model.PatchedAppRecord
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.util.Locale

/** Absent/null means OFF. MOVE additionally requires an explicit user choice. */
enum class SaveOriginalApk { COPY, MOVE }

/**
 * Safe retained-original store. This is NOT a general cache. It neither deletes
 * previous versions nor overwrites existing content, even across app upgrades.
 */
class OriginalApkStore(
    private val dir: File = MorpheData.originalApksDir,
    private val patchedApps: PatchedAppStore = PatchedAppStore.shared,
) {
    private val unsafe = Regex("[^A-Za-z0-9._-]")
    private val allowedExtensions = setOf("apk", "apkm", "apks", "xapk", "zip")

    /** Trust only regular, non-link files directly beneath our non-link store. */
    fun contains(file: File): Boolean = runCatching {
        dir.isDirectory && !Files.isSymbolicLink(dir.toPath()) &&
            file.isFile && !Files.isSymbolicLink(file.toPath()) &&
            file.absoluteFile.parentFile?.canonicalFile == dir.canonicalFile
    }.getOrDefault(false)

    /** Explicit successful-patch action. Never modifies or deletes the selected input APK. */
    @Synchronized
    fun save(apk: File, packageName: String, version: String): File? {
        if (contains(apk)) return null
        require(apk.isFile && !Files.isSymbolicLink(apk.toPath())) { "Original input is missing or a symlink" }
        require(packageName.isNotBlank() && packageName != "." && packageName != "..") { "Package identity is missing" }
        val safePackage = packageName.replace(unsafe, "_").trim('.', '_', '-')
        val safeVersion = version.ifBlank { "unknown" }.replace(unsafe, "_").trim('.', '_', '-')
        require(safePackage.isNotBlank() && safeVersion.isNotBlank()) { "Invalid package/version identity" }
        val extension = apk.extension.lowercase(Locale.ROOT)
        require(extension in allowedExtensions) { "Unsupported original bundle extension" }
        require(dir.isDirectory || dir.mkdirs()) { "Cannot create original APK store" }
        require(!Files.isSymbolicLink(dir.toPath())) { "Refusing redirected original APK store" }
        val target = File(dir, "$safePackage-$safeVersion.$extension")
        require(target.canonicalFile.parentFile == dir.canonicalFile) { "Unsafe original APK destination" }
        if (target.exists()) {
            if (contains(target) && identical(apk, target)) return target
            throw java.nio.file.FileAlreadyExistsException(target.path, null, "Different original exists; refusing overwrite")
        }
        val staging = Files.createTempFile(dir.toPath(), ".morphe-original-", ".part")
        try {
            Files.copy(apk.toPath(), staging, StandardCopyOption.REPLACE_EXISTING)
            check(identical(apk, staging.toFile())) { "Original changed while copying" }
            // No REPLACE_EXISTING: a user's earlier kept copy must never be overwritten.
            check(!target.exists()) { "Original target appeared while copying" }
            try {
                Files.move(staging, target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging, target.toPath())
            }
            check(identical(apk, target)) { "Retained copy verification failed" }
            return target
        } finally {
            Files.deleteIfExists(staging)
        }
    }

    /**
     * Only for a consciously opted-in MOVE after history has been persisted.
     * Refuse symlinks, edits since copying, the store itself and missing files.
     */
    @Synchronized
    fun removeVerifiedSource(source: File, kept: File): Boolean = runCatching {
        if (!contains(kept) || contains(source) || !source.isFile ||
            Files.isSymbolicLink(source.toPath()) || source.canonicalFile == kept.canonicalFile ||
            !identical(source, kept)) return@runCatching false
        Files.delete(source.toPath())
        true
    }.getOrDefault(false)

    /** Per-record delete is exclusively called after user confirmation. */
    fun delete(file: File): Boolean = contains(file) && file.delete()

    /**
     * Called only after an explicit deletion checkbox and successful history removal.
     * Never delete a retained file still referenced by another history entry, or a
     * different file at the same package name after a dialog became stale.
     */
    suspend fun deleteUnreferencedAfterForget(
        oldRecord: PatchedAppRecord,
        expectedPath: String?,
    ): Boolean {
        if (expectedPath == null || expectedPath != oldRecord.inputApkPath) return false
        val kept = keptOriginalOf(oldRecord) ?: return false
        val path = kept.canonicalPath
        if (patchedApps.getAll().any { other ->
                runCatching { File(other.inputApkPath).canonicalPath == path }.getOrDefault(false)
            }) return false
        return delete(kept)
    }

    /** A kept input must never use original-apks as its patched output directory. */
    suspend fun outputBaseFor(input: File): File? {
        if (!contains(input)) return null
        val canonical = input.canonicalFile
        return patchedApps.getAll().firstOrNull { record ->
            runCatching { File(record.inputApkPath).canonicalFile == canonical }.getOrDefault(false)
        }?.let { File(it.outputApkPath).parentFile?.parentFile }
            ?: File(System.getProperty("user.home"))
    }

    /** Existing history entry is authoritative when showing a safe delete option. */
    fun keptOriginalOf(record: PatchedAppRecord): File? =
        File(record.inputApkPath).takeIf(::contains)

    private fun identical(a: File, b: File): Boolean {
        if (!a.isFile || !b.isFile || a.length() != b.length()) return false
        fun digest(file: File): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(bytes)
                    if (n < 0) break
                    md.update(bytes, 0, n)
                }
            }
            return md.digest()
        }
        return digest(a).contentEquals(digest(b))
    }

    companion object {
        val shared: OriginalApkStore by lazy { OriginalApkStore() }
    }
}
