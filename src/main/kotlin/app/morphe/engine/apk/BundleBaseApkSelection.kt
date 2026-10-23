/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.engine.apk

/**
 * Fail-closed selection of the base APK *for read-only bundle inspection*.
 *
 * Never guess from the largest ZIP entry. If a bundle has no unique base identity,
 * callers cannot infer an app package/version from an arbitrary split APK.
 */
internal object BundleBaseApkSelection {
    private val knownBasePaths = setOf("base.apk", "splits/base-master.apk", "splits/base.apk")
    private val splitPrefixes = listOf("split_config", "split_", "config.", "config_")
    private const val MAX_APK_ENTRIES = 2048

    fun select(entryNames: List<String>): String? {
        if (entryNames.size > MAX_APK_ENTRIES) return null
        // Do not accept malicious ZIP entry names, including traversal and backslashes.
        if (entryNames.any { !isSafeZipPath(it) }) return null
        val apks = entryNames.filter { it.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty() || apks.distinct().size != apks.size) return null

        val explicit = apks.filter { it.lowercase() in knownBasePaths }
        if (explicit.size == 1) return explicit.single()
        if (explicit.size > 1) return null

        // XAPK packages may name the base after the package ID. This fallback is
        // acceptable ONLY when exactly one entry is not recognizably a config split.
        val candidates = apks.filter { path ->
            val name = path.substringAfterLast('/').lowercase()
            splitPrefixes.none { name.startsWith(it) }
        }
        return candidates.singleOrNull()
    }

    private fun isSafeZipPath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || '\\' in path || '\u0000' in path) return false
        val segments = path.split('/')
        return segments.none { it.isEmpty() || it == "." || it == ".." || ':' in it }
    }
}
