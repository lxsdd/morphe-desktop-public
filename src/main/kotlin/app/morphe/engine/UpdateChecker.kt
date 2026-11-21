/*
 * Morphe Desktop Custom — public Custom application releases only.
 * Official Morphe Desktop and patch-bundle updates are separate products.
 */
package app.morphe.engine

import app.morphe.engine.util.Logger
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

enum class ReleaseChannel { STABLE, DEV }

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val crossesDevToStable: Boolean,
    val downloadLink: String,
    /** Exact Custom GitHub release notes; never point at an unrelated official changelog. */
    val releaseNotesLink: String? = null,
)

/** Outcomes are about THIS Custom desktop distribution, never patch-source updates. */
sealed interface AppUpdateCheck {
    data object UpToDate : AppUpdateCheck
    data class Available(val info: UpdateInfo) : AppUpdateCheck
    data object Failed : AppUpdateCheck
}

/**
 * Pure, offline-testable GitHub release evidence classifier.
 * Malformed JSON and responses with only rejected release identities are UNKNOWN,
 * not a success result to be cached forever.
 */
internal fun classifyCustomDesktopUpdateResponse(
    response: String,
    currentVersion: String,
    channel: ReleaseChannel,
): AppUpdateCheck {
    if (!isRecognizedCustomDesktopVersion(currentVersion)) return AppUpdateCheck.Failed
    val payload = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(response) }.getOrNull()
    val entries = payload as? kotlinx.serialization.json.JsonArray ?: return AppUpdateCheck.Failed
    if (entries.isNotEmpty() && parseCustomDesktopReleases(response).isEmpty()) {
        return AppUpdateCheck.Failed
    }
    val candidate = selectNewerCustomDesktopRelease(response, currentVersion, channel)
        ?: return AppUpdateCheck.UpToDate
    return AppUpdateCheck.Available(
        UpdateInfo(
            currentVersion = currentVersion,
            latestVersion = candidate.version,
            crossesDevToStable = isCustomPrerelease(currentVersion) && !candidate.prerelease,
            downloadLink = candidate.downloadLink,
            releaseNotesLink = candidate.changelogLink,
        )
    )
}

object UpdateChecker {
    private const val CUSTOM_RELEASE_API =
        "https://api.github.com/repos/lxsdd/morphe-desktop-public/releases?per_page=30"

    /**
     * Check ONLY owner-published complete Custom distribution releases.
     * A network or metadata failure must never be confused with "up to date".
     * No official Morphe Desktop or patch-bundle release can satisfy this check.
     */
    fun checkStatus(channel: ReleaseChannel? = null): AppUpdateCheck {
        return try {
            val current = currentVersion() ?: return AppUpdateCheck.Failed
            if (!isRecognizedCustomDesktopVersion(current)) return AppUpdateCheck.Failed
            val resolvedChannel = channel ?: if (isCustomPrerelease(current)) {
                ReleaseChannel.DEV
            } else ReleaseChannel.STABLE

            val connection = URI(CUSTOM_RELEASE_API).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Morphe-Desktop-Custom-public-update-check")
            val response = try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return AppUpdateCheck.Failed
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            classifyCustomDesktopUpdateResponse(response, current, resolvedChannel)
        } catch (e: Exception) {
            Logger.debug("Custom desktop release check unavailable: $e")
            AppUpdateCheck.Failed
        }
    }

    /** Retains the existing UI/CLI API; null means either current or unknown. */
    fun checkInfo(channel: ReleaseChannel? = null): UpdateInfo? =
        (checkStatus(channel) as? AppUpdateCheck.Available)?.info

    /** Resolved version of the runnable distribution, not the official upstream project. */
    fun currentVersion(): String? =
        javaClass.getResourceAsStream("/app/morphe/cli/version.properties")
            ?.use { stream -> Properties().apply { load(stream) }.getProperty("version") }

    /** Human-readable CLI notice without leaking users into official downloads. */
    fun check(): String? {
        val info = checkInfo() ?: return null
        val channelNote = if (info.crossesDevToStable) {
            "\nThe new Custom release is stable; selecting it leaves the preview channel."
        } else ""
        return "Custom update available: v${info.latestVersion} (current: v${info.currentVersion})" +
            "$channelNote\nDownload from ${info.downloadLink}"
    }
}
