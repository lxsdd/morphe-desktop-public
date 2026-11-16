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

object UpdateChecker {
    private const val CUSTOM_RELEASE_API =
        "https://api.github.com/repos/lxsdd/morphe-desktop-public/releases?per_page=30"

    /**
     * Compare actual Custom distribution versions against GitHub's published Custom ZIPs.
     * No official Morphe Desktop version or experimental patches-bundle version is
     * ever treated as an update to this program.
     */
    fun checkInfo(channel: ReleaseChannel? = null): UpdateInfo? {
        return try {
            val current = currentVersion() ?: return null
            val resolvedChannel = channel ?: if (isCustomPrerelease(current)) {
                ReleaseChannel.DEV
            } else ReleaseChannel.STABLE

            val connection = URI(CUSTOM_RELEASE_API).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Morphe-Desktop-Custom-public-update-check")
            val response = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val available = selectNewerCustomDesktopRelease(response, current, resolvedChannel)
                ?: return null
            UpdateInfo(
                currentVersion = current,
                latestVersion = available.version,
                crossesDevToStable = isCustomPrerelease(current) && !available.prerelease,
                downloadLink = available.downloadLink,
                releaseNotesLink = available.changelogLink,
            )
        } catch (e: Exception) {
            // A failed network/metadata probe is unknown, never a fabricated update.
            Logger.debug("Custom desktop release check unavailable: $e")
            null
        }
    }

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
