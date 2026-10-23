package app.morphe.gui.util

import app.morphe.engine.installation.AdbCommandResult
import app.morphe.engine.installation.AdbCommandRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadOnlyDeviceBuildVerifierTest {
    private val digest = "a".repeat(64)
    private val path = "/data/app/~~SYNTHETIC/com.example.test-ABCD==/base.apk"

    @Test fun `single base APK and checksum are accepted`() {
        val commands = mutableListOf<List<String>>()
        val reader = ReadOnlyDeviceBuildVerifier(AdbCommandRunner { argv, _ ->
            commands += argv
            when (argv.takeLast(3)) {
                listOf("pm", "path", "com.example.test") ->
                    AdbCommandResult(0, "package:$path\n")
                else -> if (argv.takeLast(2) == listOf("sha256sum", path))
                    AdbCommandResult(0, "$digest  $path\n")
                else AdbCommandResult(1, "unexpected call")
            }
        })
        assertEquals(digest, reader.installedSingleApkSha256("synthetic-adb", "serial-fixture", "com.example.test"))
        assertTrue(commands.all { it.take(3) == listOf("synthetic-adb", "-s", "serial-fixture") })
        assertTrue(commands.none { it.contains("pull") || it.contains("install") || it.contains("uninstall") })
    }

    @Test fun `split app topology stays unknown`() {
        assertNull(ReadOnlyDeviceBuildVerifier.safeSingleBaseApkPath(
            "package:$path\npackage:/data/app/~~SYNTHETIC/com.example.test-ABCD==/split_config.apk\n"))
    }

    @Test fun `unsafe paths cannot become shell arguments`() {
        assertNull(ReadOnlyDeviceBuildVerifier.safeSingleBaseApkPath(
            "package:/data/app/~~bad;echo/evil/base.apk"))
        assertNull(ReadOnlyDeviceBuildVerifier.safeSingleBaseApkPath(
            "package:/data/app/../../base.apk"))
        assertNull(ReadOnlyDeviceBuildVerifier.safeSingleBaseApkPath(
            "package:/sdcard/test/base.apk"))
    }

    @Test fun `unverified and malformed checksums fail closed`() {
        assertNull(ReadOnlyDeviceBuildVerifier.parseDeviceSha256("permission denied"))
        assertNull(ReadOnlyDeviceBuildVerifier.parseDeviceSha256("abcd"))
        assertEquals(digest, ReadOnlyDeviceBuildVerifier.parseDeviceSha256("$digest  $path"))
    }

    @Test fun `ambiguous path list and empty identity fail closed`() {
        assertNull(ReadOnlyDeviceBuildVerifier.safeSingleBaseApkPath(""))
        val reader = ReadOnlyDeviceBuildVerifier(AdbCommandRunner { _, _ ->
            AdbCommandResult(0, "package:$path\n")
        })
        assertNull(reader.installedSingleApkSha256("", "serial-fixture", "com.example.test"))
        assertNull(reader.installedSingleApkSha256("synthetic-adb", "", "com.example.test"))
        assertNull(reader.installedSingleApkSha256("synthetic-adb", "serial-fixture", "invalid package"))
    }
}
