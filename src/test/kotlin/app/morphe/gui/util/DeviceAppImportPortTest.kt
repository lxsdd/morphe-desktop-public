/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.util

import app.morphe.engine.apk.ApkInspectionResult
import app.morphe.engine.installation.AdbCommandResult
import app.morphe.engine.installation.AdbCommandRunner
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceAppImportPortTest {
    private val targetSerial = "TEST-DEVICE-A"
    private val pkg = "org.example.audio"
    private val foreignSigner = "b".repeat(64)
    private val morpheSigner = "a".repeat(64)

    private fun request(serial: String = targetSerial) = DeviceAppImportRequest(
        adbPath = "test-adb",
        deviceSerial = serial,
        packageName = pkg,
        expectedVersionCode = 42,
        expectedVersionName = "1.0",
        morpheDeviceSignatureId = "00000abc",
        morpheSignerSha256 = morpheSigner,
        compatibleVersionNames = setOf("1.0"),
    )

    @Test
    fun `installed device split set is copied read only with immutable serial`() = runBlocking {
        val commands = mutableListOf<List<String>>()
        val fake = AdbCommandRunner { command, _ ->
            commands.add(command)
            assertEquals(listOf("test-adb", "-s", targetSerial), command.take(3))
            when (command.drop(3).firstOrNull()) {
                "get-state" -> AdbCommandResult(0, "device\n")
                "shell" -> when (command.getOrNull(4)) {
                    "dumpsys" -> AdbCommandResult(0, "signatures:[123456]")
                    "pm" -> AdbCommandResult(0,
                        "package:/data/app/org.example.audio/base.apk\n" +
                        "package:/data/app/org.example.audio/split_config.arm64.apk\n"
                    )
                    else -> error("Unexpected ADB command $command")
                }
                "pull" -> {
                    File(command.last()).writeText("synthetic test fixture")
                    AdbCommandResult(0, "1 file pulled")
                }
                else -> error("Unexpected ADB command $command")
            }
        }
        val service = DeviceAppImportService(
            runner = fake,
            manifestReader = { file -> ApkInspectionResult(
                file = file,
                packageName = pkg,
                versionName = "1.0",
                versionCode = 42,
                minSdkVersion = 21,
                applicationLabel = null,
                architectures = setOf("universal"),
                isBundle = false,
            ) },
            signerReader = { setOf(foreignSigner) },
        )
        val result = assertIs<DeviceAppImportResult.Success>(service.import(request()))
        try {
            assertEquals(DeviceAppImportSource.DEVICE, result.input.source)
            assertTrue(result.input.deviceSpecificSplitSet)
            assertEquals(targetSerial, result.input.sourceDeviceSerial)
            assertTrue(result.input.file.isFile)
            ZipFile(result.input.file).use { zip ->
                assertNotNull(zip.getEntry("base.apk"))
                assertNotNull(zip.getEntry("split-001.apk"))
            }
            assertTrue(commands.all { it.take(3) == listOf("test-adb", "-s", targetSerial) })
            assertFalse(commands.any { cmd ->
                cmd.any { it in setOf("install", "uninstall", "push", "rm", "mount") }
            })
            assertNotNull(crossDeviceInstallBlockReason(true, targetSerial, "TEST-DEVICE-B"))
            assertNull(crossDeviceInstallBlockReason(true, targetSerial, targetSerial))
            assertNotNull(crossDeviceInstallBlockReason(true, null, targetSerial))
        } finally {
            assertTrue(cleanupDeviceImportRoot(assertNotNull(result.input.cleanupRoot)))
        }
    }

    @Test
    fun `missing serial or signer rejects before ADB can be invoked`() = runBlocking {
        val fake = AdbCommandRunner { _, _ -> error("Invalid input must not invoke ADB") }
        val service = DeviceAppImportService(runner = fake)
        val bad = assertIs<DeviceAppImportResult.Failure>(service.import(request(serial = "  ")))
        assertEquals(DeviceAppImportFailureKind.VALIDATION_FAILED, bad.kind)
        val missingSigner = assertIs<DeviceAppImportResult.Failure>(
            service.import(request().copy(morpheSignerSha256 = "unknown"))
        )
        assertEquals(DeviceAppImportFailureKind.VALIDATION_FAILED, missingSigner.kind)
    }

    @Test
    fun `unavailable device stops without trying pull or modifying package`() = runBlocking {
        val seen = mutableListOf<List<String>>()
        val service = DeviceAppImportService(runner = AdbCommandRunner { command, _ ->
            seen.add(command)
            AdbCommandResult(1, "offline")
        })
        val result = assertIs<DeviceAppImportResult.Failure>(service.import(request()))
        assertEquals(DeviceAppImportFailureKind.DEVICE_UNAVAILABLE, result.kind)
        assertEquals(1, seen.size)
        assertEquals("get-state", seen.single().last())
    }

    @Test
    fun `unsafe package paths duplicate paths and patch incompatibility reject`() {
        assertTrue(DeviceAppImportService.isSafePackageApkPath("/data/app/package/base.apk"))
        assertFalse(DeviceAppImportService.isSafePackageApkPath("/sdcard/Downloads/base.apk"))
        assertFalse(DeviceAppImportService.isSafePackageApkPath("/data/app/../other/base.apk"))
        assertFalse(DeviceAppImportService.isSafePackageApkPath("C:/Temp/base.apk"))
        assertEquals(
            listOf("/data/app/pkg/base.apk", "/data/app/pkg/base.apk"),
            DeviceAppImportService.parsePackagePaths(
                "package:/data/app/pkg/base.apk\npackage:/data/app/pkg/base.apk\n"
            )
        )
        assertFalse(DeviceAppImportService.isPatchable(emptySet(), false, "1.0"))
        assertFalse(DeviceAppImportService.isPatchable(setOf("2.0"), false, "1.0"))
        assertTrue(DeviceAppImportService.isPatchable(setOf("1.0"), false, "1.0"))
        assertTrue(DeviceAppImportService.isPatchable(emptySet(), true, "1.0"))
    }

    @Test
    fun `temp cleanup does not delete an arbitrary directory`() {
        val unrelated = Files.createTempDirectory("unrelated-app-data-").toFile()
        try {
            File(unrelated, "keep.txt").writeText("not ours")
            assertFalse(cleanupDeviceImportRoot(unrelated))
            assertTrue(File(unrelated, "keep.txt").isFile)
        } finally {
            unrelated.deleteRecursively()
        }
    }
}
