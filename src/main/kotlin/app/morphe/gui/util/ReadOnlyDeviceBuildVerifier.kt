package app.morphe.gui.util

import app.morphe.engine.installation.AdbCommandRunner
import app.morphe.engine.installation.ProcessAdbCommandRunner

/**
 * Ported and tightened from private Custom 1.18.1 read-only base APK SHA-256 probing.
 * Only one installed base APK can be equated to a single saved output APK. Split
 * installations remain unknown; no fallback guesses or device writes are allowed.
 */
internal class ReadOnlyDeviceBuildVerifier(
    private val runner: AdbCommandRunner = ProcessAdbCommandRunner(),
) {
    fun installedSingleApkSha256(
        adbPath: String,
        serial: String,
        packageName: String,
    ): String? {
        if (adbPath.isBlank() || serial.isBlank() ||
            !packageName.matches(Regex("""[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"""))) return null
        val prefix = listOf(adbPath, "-s", serial, "shell")
        return try {
            val pathResponse = runner.run(prefix + listOf("pm", "path", packageName)) {}
            if (pathResponse.exitCode != 0) return null
            val remote = safeSingleBaseApkPath(pathResponse.output) ?: return null
            for (command in listOf(listOf("sha256sum", remote), listOf("toybox", "sha256sum", remote))) {
                val result = runner.run(prefix + command) {}
                if (result.exitCode != 0) continue
                parseDeviceSha256(result.output)?.let { return it }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** The only accepted topology is ONE safely named base APK. */
        internal fun safeSingleBaseApkPath(pmPathOutput: String): String? {
            val raw = pmPathOutput.lineSequence().map(String::trim)
                .filter { it.isNotBlank() }.toList()
            if (raw.size != 1 || !raw.single().startsWith("package:")) return null
            val path = raw.single().removePrefix("package:")
            if (path.length !in 20..1024 || !path.endsWith("/base.apk")) return null
            if (!(path.startsWith("/data/app/") || path.startsWith("/mnt/expand/"))) return null
            if (!path.matches(Regex("""/[A-Za-z0-9_~+./=\-]+"""))) return null
            if (path.contains("//") || path.split('/').any { it == ".." || it == "." }) return null
            return path
        }

        internal fun parseDeviceSha256(output: String): String? =
            Regex("""(?i)^([a-f0-9]{64})(?:\s|$)""")
                .find(output.trim())?.groupValues?.get(1)?.lowercase()
    }
}
