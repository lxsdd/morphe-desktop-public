/*
 * Morphe Desktop Custom — public-release identity and version selection.
 * No private, local APK, device or signer data enters this catalog.
 */
package app.morphe.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

private const val RELEASE_ROOT = "https://github.com/lxsdd/morphe-desktop-public/releases"
private val modernTag = Regex("^custom-v(\\d+\\.\\d+\\.\\d+(?:-(?:dev|beta|core-preview|rc)\\.\\d+)?)-[0-9a-f]{12}$")
private val oldCoreTag = Regex("^custom-v2\\.2-core-preview-[0-9a-f]{12}$")
private val versionPattern = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(dev|beta|core-preview|rc)\\.(\\d+))?$")

internal data class CustomDesktopRelease(
    val version: String,
    val tag: String,
    val prerelease: Boolean,
    val changelogLink: String,
    val downloadLink: String,
)

private data class CustomVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val stage: Int,
    val stageNumber: Int,
) : Comparable<CustomVersion> {
    override fun compareTo(other: CustomVersion): Int =
        compareValuesBy(this, other,
            CustomVersion::major, CustomVersion::minor, CustomVersion::patch,
            CustomVersion::stage, CustomVersion::stageNumber)
}

private fun parsedVersion(text: String): CustomVersion? {
    val match = versionPattern.matchEntire(text) ?: return null
    val major = match.groupValues[1].toIntOrNull() ?: return null
    val minor = match.groupValues[2].toIntOrNull() ?: return null
    val patch = match.groupValues[3].toIntOrNull() ?: return null
    val phase = match.groupValues[4]
    val order = when (phase) {
        "" -> 4
        "dev" -> 0
        // The historical reduced Core Preview predates a complete Custom beta.
        // Both have the same numeric 2.2.0 prefix; never trap an installed
        // Core Preview on an older build when the full beta becomes available.
        "core-preview" -> 1
        "beta" -> 2
        "rc" -> 3
        else -> return null
    }
    val number = if (phase.isEmpty()) 0 else match.groupValues[5].toIntOrNull() ?: return null
    return CustomVersion(major, minor, patch, order, number)
}

/** Only a valid Custom distribution version can be classified as current. */
internal fun isRecognizedCustomDesktopVersion(version: String): Boolean = parsedVersion(version) != null

internal fun isCustomPrerelease(version: String): Boolean =
    parsedVersion(version)?.stage?.let { it < 4 } ?: false

private fun releaseVersion(tag: String): String? = when {
    tag == "custom-v2.1.1-beta.4" -> "2.1.1-beta.4"
    oldCoreTag.matches(tag) -> "2.2.0-core-preview.1"
    else -> modernTag.matchEntire(tag)?.groupValues?.get(1)
        ?.takeIf { parsedVersion(it) != null }
}

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull

/**
 * Only owner-published, explicitly named Custom releases and one exact same-repository ZIP.
 * Unknown tags, draft releases, foreign URLs and malformed API data fail closed.
 */
internal fun parseCustomDesktopReleases(raw: String): List<CustomDesktopRelease> {
    val releases = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
        ?: return emptyList()
    return releases.mapNotNull { element ->
        val release = element as? JsonObject ?: return@mapNotNull null
        if (release.flag("draft") != false) return@mapNotNull null
        val prerelease = release.flag("prerelease") ?: return@mapNotNull null
        val tag = release.str("tag_name") ?: return@mapNotNull null
        val version = releaseVersion(tag) ?: return@mapNotNull null
        if (prerelease != isCustomPrerelease(version)) return@mapNotNull null
        val page = "$RELEASE_ROOT/tag/$tag"
        if (release.str("html_url") != page) return@mapNotNull null
        val assets = release["assets"] as? JsonArray ?: return@mapNotNull null
        val executable = assets.mapNotNull { it as? JsonObject }.singleOrNull { asset ->
            val name = asset.str("name") ?: return@singleOrNull false
            val url = asset.str("browser_download_url") ?: return@singleOrNull false
            name.startsWith("Morphe-Desktop-Custom-") &&
                name.matches(Regex("[A-Za-z0-9_.-]+\\.zip")) &&
                url == "$RELEASE_ROOT/download/$tag/$name"
        } ?: return@mapNotNull null
        CustomDesktopRelease(
            version = version,
            tag = tag,
            prerelease = prerelease,
            changelogLink = page,
            downloadLink = executable.str("browser_download_url")!!,
        )
    }
}

/** Stable users never receive a preview; an existing preview can advance or become stable. */
internal fun selectNewerCustomDesktopRelease(
    raw: String,
    currentVersion: String,
    channel: ReleaseChannel,
): CustomDesktopRelease? {
    val running = parsedVersion(currentVersion) ?: return null
    return parseCustomDesktopReleases(raw).asSequence()
        .filter { channel == ReleaseChannel.DEV || !it.prerelease }
        .mapNotNull { candidate ->
            val parsed = parsedVersion(candidate.version) ?: return@mapNotNull null
            if (parsed > running) candidate to parsed else null
        }
        .maxWithOrNull(compareBy<Pair<CustomDesktopRelease, CustomVersion>> { it.second })
        ?.first
}
