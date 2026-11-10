/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.gui.data.model.SupportedApp
import app.morphe.engine.model.Release

/**
 * The "bucket" an APK's version falls into relative to a [SupportedApp]'s
 * stable + experimental version lists.
 */
enum class VersionStatus {
    /** Current version is the latest stable. Happy path. */
    LATEST_STABLE,

    /** In the stable list but older than the latest stable. */
    OLDER_STABLE,

    /** Current version is the latest experimental. */
    LATEST_EXPERIMENTAL,

    /** In the experimental list but older than the latest experimental. */
    OLDER_EXPERIMENTAL,

    /** Newer than every known version (stable + experimental). */
    TOO_NEW,

    /** Older than every known stable version. */
    TOO_OLD,

    /** Between supported versions but not in either list. */
    UNSUPPORTED_BETWEEN,

    /** The app version is recognized, but its build code is excluded by patch metadata. */
    BUILD_UNSUPPORTED,

    /** No patch metadata, can't determine. */
    UNKNOWN
}

/**
 * The result of resolving a current APK version against a [SupportedApp].
 *
 * @param status which bucket the current version falls into.
 * @param suggestedVersion the version most relevant to surface in UI for this
 *   status — e.g. the latest stable for [VersionStatus.OLDER_STABLE], the
 *   latest experimental for [VersionStatus.OLDER_EXPERIMENTAL], the newest
 *   known version for [VersionStatus.TOO_NEW], etc.
 */
data class VersionResolution(
    val status: VersionStatus,
    val suggestedVersion: String?
)

/**
 * Numeric comparator for dotted version strings (e.g. "20.40.45" vs "21.01.23").
 * Returns negative if v1 < v2, 0 if equal, positive if v1 > v2.
 * Returns 0 if either string can't be parsed.
 */
fun compareVersionStrings(v1: String, v2: String): Int {
    return compareVersions(v1, v2)
}

fun String.normalizeVersion(): String = removePrefix("v").removePrefix("V").trim()

/** Compare dotted app versions and semver-like release tags, including prereleases. */
fun compareVersions(v1: String?, v2: String?): Int {
    if (v1 == null && v2 == null) return 0
    if (v1 == null) return -1
    if (v2 == null) return 1
    val first = v1.normalizeVersion()
    val second = v2.normalizeVersion()
    if (first == second) return 0
    data class Parts(val base: String, val prerelease: String?)
    fun parts(value: String): Parts {
        val prerelease = Regex("""^([\d.]+)[-._]?(dev|beta|rc|alpha|preview)""", RegexOption.IGNORE_CASE).find(value)
        if (prerelease != null) return Parts(prerelease.groupValues[1], prerelease.groupValues[2])
        val numeric = Regex("""^([\d.]+)(.*)$""").find(value)
        return if (numeric != null && numeric.groupValues[2].isNotEmpty()) {
            Parts(numeric.groupValues[1], numeric.groupValues[2])
        } else Parts(value, null)
    }
    val a = parts(first)
    val b = parts(second)
    val baseA = a.base.split(".").map { it.toIntOrNull() ?: 0 }
    val baseB = b.base.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(baseA.size, baseB.size)) {
        val left = baseA.getOrElse(i) { 0 }
        val right = baseB.getOrElse(i) { 0 }
        if (left != right) return left.compareTo(right)
    }
    return when {
        a.prerelease == null && b.prerelease == null -> 0
        a.prerelease == null -> 1
        b.prerelease == null -> -1
        else -> {
            val ordinalA = first.substringAfterLast('.').toLongOrNull()
            val ordinalB = second.substringAfterLast('.').toLongOrNull()
            if (ordinalA != null && ordinalB != null) ordinalA.compareTo(ordinalB) else first.compareTo(second)
        }
    }
}

fun isNewerVersion(current: String?, baseline: String?): Boolean =
    !current.isNullOrBlank() && !baseline.isNullOrBlank() &&
        !current.equals("unknown", true) && !baseline.equals("unknown", true) &&
        compareVersions(current, baseline) > 0

fun String?.isDevTag(): Boolean = this?.let {
    it.contains("dev", true) || it.contains("alpha", true) || it.contains("beta", true)
} ?: false

fun newerRelease(dev: Release?, stable: Release?): Release? = when {
    dev == null -> stable
    stable == null -> dev
    compareVersions(dev.tagName, stable.tagName) >= 0 -> dev
    else -> stable
}

/**
 * Determine the status of [currentVersion] relative to the stable and
 * experimental versions known for [app].
 */
fun resolveVersionStatus(currentVersion: String, app: SupportedApp, versionCode: Int? = null): VersionResolution {
    val stableList = app.supportedVersions
    val experimentalList = app.experimentalVersions

    val latestStable = stableList.firstOrNull()
    val oldestStable = stableList.lastOrNull()
    val latestExperimental = experimentalList.firstOrNull()

    if (latestStable == null && latestExperimental == null) {
        return VersionResolution(VersionStatus.UNKNOWN, null)
    }

    val knownVersion = currentVersion in stableList || currentVersion in experimentalList
    if (knownVersion && !app.buildCodeSupported(currentVersion, versionCode)) {
        return VersionResolution(VersionStatus.BUILD_UNSUPPORTED, currentVersion)
    }

    // Exact matches in either bucket
    if (latestStable != null && currentVersion == latestStable) {
        return VersionResolution(VersionStatus.LATEST_STABLE, latestStable)
    }
    if (latestExperimental != null && currentVersion == latestExperimental) {
        return VersionResolution(VersionStatus.LATEST_EXPERIMENTAL, latestExperimental)
    }
    if (currentVersion in stableList) {
        return VersionResolution(VersionStatus.OLDER_STABLE, latestStable)
    }
    if (currentVersion in experimentalList) {
        return VersionResolution(VersionStatus.OLDER_EXPERIMENTAL, latestExperimental)
    }

    // Not in either list — figure out where it sits relative to known range.
    val newestKnown = when {
        latestStable == null -> latestExperimental
        latestExperimental == null -> latestStable
        compareVersions(latestStable, latestExperimental) >= 0 -> latestStable
        else -> latestExperimental
    }

    if (newestKnown != null && compareVersions(currentVersion, newestKnown) > 0) {
        return VersionResolution(VersionStatus.TOO_NEW, newestKnown)
    }
    if (oldestStable != null && compareVersions(currentVersion, oldestStable) < 0) {
        return VersionResolution(VersionStatus.TOO_OLD, oldestStable)
    }

    return VersionResolution(
        VersionStatus.UNSUPPORTED_BETWEEN,
        latestStable ?: latestExperimental
    )
}
