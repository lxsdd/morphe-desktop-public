/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.SupportedAbi

/**
 * Adapters for the preserved private Quick/CLI presentation on the Patcher 2.3
 * non-generic Patch API. Compatibility must come from the signed bundle's typed
 * declarations; the removed Patcher 1.x fallback fields cannot be synthesized.
 */
typealias VersionMap = LinkedHashMap<String, Int>
typealias CompatibleVersionsMap = Map<String, VersionMap>

fun Patch.versionCodesFor(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Int>? = compatibility.orEmpty()
    .asSequence()
    .filter { packageName == null || it.packageName == null || it.packageName == packageName }
    .flatMap { it.targets.asSequence() }
    .firstOrNull { it.version == versionName && !it.versionCodes.isNullOrEmpty() }
    ?.versionCodes

fun Iterable<Patch>.versionCodesFor(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Int>? = firstNotNullOfOrNull { it.versionCodesFor(packageName, versionName) }

fun Patch.versionsFor(
    packageName: String?,
    includeExperimental: Boolean,
): List<String> = compatibility.orEmpty()
    .filter { packageName == null || it.packageName == null || it.packageName == packageName }
    .flatMap { entry ->
        entry.targets.filter { includeExperimental || !it.isExperimental }
            .mapNotNull { it.version }
    }

fun Patch.isCompatibleWith(
    packageName: String,
    includeExperimental: Boolean,
    includeUniversalPatches: Boolean,
): Boolean {
    val entries = compatibility.orEmpty()
    if (entries.isEmpty()) return includeUniversalPatches
    return entries.any { entry ->
        when {
            entry.packageName == null -> includeUniversalPatches
            entry.packageName != packageName -> false
            else -> entry.targets.isEmpty() ||
                entry.targets.any { includeExperimental || !it.isExperimental }
        }
    }
}

/** Null means unrestricted or universal, empty means explicitly incompatible. */
fun Patch.supportedVersionsFor(packageName: String): List<String>? {
    val entries = compatibility.orEmpty()
    val matching = entries.filter { it.packageName == packageName }
    if (matching.isEmpty()) {
        return if (entries.isEmpty() || entries.any { it.packageName == null }) null else emptyList()
    }
    return matching.flatMap { entry -> entry.targets.mapNotNull { it.version } }
        .takeUnless { it.isEmpty() }
}

fun Patch.compatibleVersionsForDisplay(
    includeExperimental: Boolean,
): List<Pair<String?, List<String>>> = compatibility.orEmpty().map { entry ->
    entry.packageName to entry.targets
        .filter { includeExperimental || !it.isExperimental }
        .mapNotNull { it.version }
}

/** Keep the private source-count display logic, using only modern typed metadata. */
fun Iterable<Patch>.mostCommonCompatibleVersions(
    packageNames: Set<String>?,
    countUnusedPatches: Boolean,
    includeExperimental: Boolean = false,
): CompatibleVersionsMap {
    val patches = toList()
    val allPackages = patches.flatMap { it.compatibility.orEmpty() }
        .mapNotNull { it.packageName }.toSet()
    val targets = packageNames?.let { allPackages.intersect(it) } ?: allPackages
    val counts = linkedMapOf<String, VersionMap>()
    for (pkg in targets) {
        val versions = VersionMap()
        for (patch in patches) {
            if (!countUnusedPatches && !patch.default) continue
            val entries = patch.compatibility.orEmpty()
            val matching = entries.filter { it.packageName == null || it.packageName == pkg }
            if (matching.isEmpty()) continue
            val list = matching.flatMap { entry ->
                entry.targets.filter { includeExperimental || !it.isExperimental }
                    .mapNotNull { it.version }
            }
            for (v in list) versions[v] = (versions[v] ?: 0) + 1
        }
        if (versions.isNotEmpty()) {
            counts[pkg] = versions.entries.sortedByDescending { it.value }
                .associateTo(VersionMap()) { it.key to it.value }
        }
    }
    return counts.entries.sortedBy { it.key }
        .associateTo(linkedMapOf()) { it.key to it.value }
}
