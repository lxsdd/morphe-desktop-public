package app.morphe.gui.util

import app.morphe.engine.apk.ApkInspectionResult
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test-only fake APK manifest/fingerprint; never contacts a device. */
class DeviceInstallConfirmationGateTest {
    private val dir = Files.createTempDirectory("morphe-install-consent-test").toFile()
    private val apk = File(dir, "selected.apk").also { it.writeText("original fixture") }
    private val target = DeviceOperationTarget("SERIAL_A", "Phone")
    private var pkg = "com.example.app"
    private var version = "5.1"
    private var code: Int? = 501
    private var bundle = false
    private val inspect: (File) -> ApkInspectionResult? = { file ->
        ApkInspectionResult(
            file = file, packageName = pkg, versionName = version,
            versionCode = code, minSdkVersion = 24, applicationLabel = "Fixture",
            architectures = emptySet(), isBundle = bundle,
        )
    }
    private val checksum: (File) -> String = { file ->
        if (file.readText() == "original fixture") "a".repeat(64) else "b".repeat(64)
    }

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test fun `confirmed unchanged APK matches only the original serial`() {
        val request = DeviceInstallConfirmationGate.prepare(apk, target, inspect, checksum).getOrThrow()
        assertEquals("com.example.app", request.packageName)
        assertEquals("5.1", request.versionName)
        assertTrue(DeviceInstallConfirmationGate.stillMatches(request, target, inspect, checksum))
        assertFalse(DeviceInstallConfirmationGate.stillMatches(request, null, inspect, checksum))
        assertFalse(DeviceInstallConfirmationGate.stillMatches(
            request, DeviceOperationTarget("SERIAL_B", "Phone"), inspect, checksum))
    }

    @Test fun `swapped APK is rejected after consent was shown`() {
        val request = DeviceInstallConfirmationGate.prepare(apk, target, inspect, checksum).getOrThrow()
        apk.writeText("changed fixture")
        assertFalse(DeviceInstallConfirmationGate.stillMatches(request, target, inspect, checksum))
    }

    @Test fun `package and version changes invalidate consent`() {
        val request = DeviceInstallConfirmationGate.prepare(apk, target, inspect, checksum).getOrThrow()
        pkg = "com.example.other"
        assertFalse(DeviceInstallConfirmationGate.stillMatches(request, target, inspect, checksum))
        pkg = "com.example.app"
        version = "6.0"
        assertFalse(DeviceInstallConfirmationGate.stillMatches(request, target, inspect, checksum))
    }

    @Test fun `history consent must match the exact saved output fingerprint and path`() {
        val request = DeviceInstallConfirmationGate.prepare(apk, target, inspect, checksum).getOrThrow()
        assertTrue(DeviceInstallConfirmationGate.matchesHistoryOutput(
            request, apk.canonicalPath, "a".repeat(64), "5.1"
        ))
        assertFalse(DeviceInstallConfirmationGate.matchesHistoryOutput(
            request, apk.path, null, "5.1"
        ))
        assertFalse(DeviceInstallConfirmationGate.matchesHistoryOutput(
            request, apk.path, "b".repeat(64), "5.1"
        ))
        assertFalse(DeviceInstallConfirmationGate.matchesHistoryOutput(
            request, apk.path, "a".repeat(64), "5.2"
        ))
        assertFalse(DeviceInstallConfirmationGate.matchesHistoryOutput(
            request, File(dir, "different.apk").path, "a".repeat(64), "5.1"
        ))
    }

    @Test fun `invalid inputs are rejected before any install confirmation`() {
        assertTrue(DeviceInstallConfirmationGate.prepare(
            File(dir, "missing.apk"), target, inspect, checksum).isFailure)
        bundle = true
        assertTrue(DeviceInstallConfirmationGate.prepare(apk, target, inspect, checksum).isFailure)
        bundle = false
        code = null
        assertTrue(DeviceInstallConfirmationGate.prepare(apk, target, inspect, checksum).isFailure)
        code = 501
        assertTrue(DeviceInstallConfirmationGate.prepare(
            apk, DeviceOperationTarget("", "unknown"), inspect, checksum).isFailure)
    }
}
