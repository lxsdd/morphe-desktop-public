package app.morphe.gui.util

import app.morphe.engine.installation.AdbCommandRunner
import app.morphe.engine.installation.AdbCommandResult
import java.util.concurrent.TimeUnit

/** Bound shell reads: an unresponsive ADB endpoint must not hang Home indefinitely. */
private class BoundedReadOnlyAdbRunner : AdbCommandRunner {
    override fun run(command: List<String>, onOutput: (String) -> Unit): AdbCommandResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(12, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return AdbCommandResult(-1, "")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            return AdbCommandResult(process.exitValue(), output.take(16384))
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}

/**
 * Ported and tightened from private Custom 1.18.1 read-only base APK SHA-256 probing.
 * Only one installed base APK can be equated to a single saved output APK. Split
 * installations remain unknown; no fallback guesses or device writes are allowed.
 */
internal class ReadOnlyDeviceBuildVerifier(
    private val runner: AdbCommandRunner = BoundedReadOnlyAdbRunner(),
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

/**
 * Regression-testable proof gate used by production read-only scan.
 * Current signer selection is intentionally NOT an input: identical signed APK
 * bytes are stronger proof than a match with the currently configured key.
 * Missing, ambiguous or malformed inputs fail closed.
 */
internal fun canCheckRecordedDeviceApk(
    installed: Boolean,
    deviceVersion: String?,
    recordedVersion: String,
    recordedOutputSha256: String?,
): Boolean {
    if (!installed) return false
    val device = deviceVersion?.removePrefix("v")?.takeUnless {
        it.isBlank() || it.equals("unknown", ignoreCase = true)
    } ?: return false
    val historical = recordedVersion.removePrefix("v")
    if (historical.isBlank() || historical.equals("unknown", ignoreCase = true)) return false
    return device.equals(historical, ignoreCase = true) &&
        recordedOutputSha256?.matches(Regex("[0-9a-fA-F]{64}")) == true
}
