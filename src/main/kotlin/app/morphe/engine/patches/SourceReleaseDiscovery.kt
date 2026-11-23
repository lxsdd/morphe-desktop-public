/*
 * Copyright 2026 Morphe.
 * Read-only discovery of releases outside a publisher's preferred bundle manifest.
 */
package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.util.compareVersions

/** A catalog hit is NOT a verified app patch update or an approved replacement bundle. */
data class SourceReleaseCandidates(
    val stableTag: String? = null,
    val experimentalTag: String? = null,
) {
    val hasCandidates: Boolean get() = stableTag != null || experimentalTag != null
}

private val comparableTag = Regex(
    """^[vV]?\d+(?:\.\d+)+(?:[-._](?:dev|beta|rc|alpha|preview)[-._]?\d*)?$""",
    RegexOption.IGNORE_CASE,
)

/** A published bundle must expose the same downloadable first .mpp asset used by the installer. */
fun Release.isUsablePublishedBundleRelease(): Boolean =
    !draft && (assets.firstOrNull { it.isPatchFile() }?.downloadUrl?.isNotBlank() == true)

/** Preserve provider-specific tags, but never fall back to a draft or unusable asset. */
fun fallbackPublishedBundleRelease(releases: List<Release>, prerelease: Boolean): Release? =
    highestPublishedBundleRelease(releases, prerelease)
        ?: releases.firstOrNull { it.isUsablePublishedBundleRelease() && it.isDevRelease() == prerelease }

/** Preserve publisher choice; report only higher tagged, published .mpp release candidates. */
fun additionalPublishedReleases(
    selectedTag: String?,
    releases: List<Release>,
): SourceReleaseCandidates {
    if (selectedTag.isNullOrBlank() || !comparableTag.matches(selectedTag)) {
        return SourceReleaseCandidates()
    }
    val candidates = releases.asSequence().filter {
        it.isUsablePublishedBundleRelease() && comparableTag.matches(it.tagName) &&
            compareVersions(it.tagName, selectedTag) > 0
    }.toList()
    fun highest(dev: Boolean): String? =
        candidates.filter { it.isDevRelease() == dev }
            .maxWithOrNull { left, right -> compareVersions(left.tagName, right.tagName) }
            ?.tagName
    return SourceReleaseCandidates(
        stableTag = highest(false),
        experimentalTag = highest(true),
    )
}

/** For manifest-free sources, use semantic release order rather than GitHub publish order. */
fun highestPublishedBundleRelease(releases: List<Release>, prerelease: Boolean): Release? =
    releases.filter {
        it.isUsablePublishedBundleRelease() && it.isDevRelease() == prerelease &&
            comparableTag.matches(it.tagName)
    }.maxWithOrNull { a, b -> compareVersions(a.tagName, b.tagName) }
