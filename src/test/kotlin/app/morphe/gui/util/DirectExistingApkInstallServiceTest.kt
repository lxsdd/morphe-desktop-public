package app.morphe.gui.util

import app.morphe.engine.installation.AdbInstallMode
import app.morphe.engine.installation.AdbInstallRequest
import app.morphe.engine.installation.InstalledPackageOwnership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectExistingApkInstallServiceTest {
    private val target = DeviceOperationTarget("serial-A", "Pixel")
    private val request = DeviceInstallConfirmationRequest(
        target, "/tmp/patched.apk", "a".repeat(64), "com.instagram.android", "452.1.0.53.64"
    )
    private var count = 0
    private var observed: AdbInstallRequest? = null
    private var owner: InstalledPackageOwnership = InstalledPackageOwnership.NotInstalled
    private var matches = true
    private val service get() = DirectExistingApkInstallService(
        matches = { _, _ -> matches },
        ownership = { _, _, _ -> owner },
        installer = { req -> count++; observed = req; Result.success(AdbInstallMode.LEGACY) },
    )
    @Test fun `new install is single targeted and never permits downgrade`() {
        assertTrue(service.install(request, { target }, "adb").isSuccess)
        assertEquals(1, count)
        assertEquals("serial-A", observed?.deviceSerial)
        assertEquals("com.instagram.android", observed?.expectedPackageName)
        assertEquals(false, observed?.allowDowngrade)
    }
    @Test fun `preflight is read-only and performs no installation`() {
        assertTrue(service.preflight(request, { target }, "adb").isSuccess)
        assertEquals(0, count)
    }
    @Test fun `existing and foreign-owned apps cannot be overwritten`() {
        owner = InstalledPackageOwnership.NoOwner
        assertTrue(service.install(request, { target }, "adb").isFailure)
        owner = InstalledPackageOwnership.OwnedBy("com.android.vending")
        assertTrue(service.install(request, { target }, "adb").isFailure)
        assertEquals(0, count)
    }
    @Test fun `wrong disconnected or missing selected device fails closed`() {
        assertTrue(service.install(request, { DeviceOperationTarget("serial-B", "Pixel") }, "adb").isFailure)
        assertTrue(service.install(request, { null }, "adb").isFailure)
        assertEquals(0, count)
    }
    @Test fun `mutated source APK identity and missing ADB block installation`() {
        matches = false
        assertTrue(service.install(request, { target }, "adb").isFailure)
        matches = true
        assertTrue(service.install(request, { target }, null).isFailure)
        assertEquals(0, count)
    }
    @Test fun `device switch during read-only probe blocks mutation`() {
        var calls = 0
        val r = service.install(request, {
            calls++
            if (calls >= 2) DeviceOperationTarget("serial-B", "Pixel") else target
        }, "adb")
        assertTrue(r.isFailure)
        assertEquals(0, count)
    }
    @Test fun `failed ownership probe is never considered not installed`() {
        val broken = DirectExistingApkInstallService(
            matches = { _, _ -> true },
            ownership = { _, _, _ -> error("ADB read failed") },
            installer = { count++; Result.success(AdbInstallMode.LEGACY) }
        )
        assertTrue(broken.install(request, { target }, "adb").isFailure)
        assertEquals(0, count)
    }
}
