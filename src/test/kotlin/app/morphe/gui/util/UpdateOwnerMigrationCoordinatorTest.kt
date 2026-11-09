package app.morphe.gui.util

import app.morphe.engine.installation.AdbMigrationRequiredException
import app.morphe.engine.installation.InstalledPackageOwnership
import app.morphe.engine.installation.MigrationRequiredReason
import app.morphe.engine.util.FileChecksum
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure synthetic ADB callbacks. No devices, user files or real APK installation. */
class UpdateOwnerMigrationCoordinatorTest {
    private val root = Files.createTempDirectory("morphe-owner-migration-test").toFile()
    private val source = File(root, "patched.apk").apply { writeText("patched apk bytes") }
    private val expectedOwner = InstalledPackageOwnership.OwnedBy("com.android.vending")
    private var owner: InstalledPackageOwnership = expectedOwner
    private var ready = true
    private var removeResult: Result<Unit> = Result.success(Unit)
    private var installResult: Result<Unit> = Result.success(Unit)
    private val calls = mutableListOf<String>()
    private val identity = MigrationApkIdentity("test.package", "9.8.7")

    @AfterTest
    fun cleanup() { root.deleteRecursively() }

    private fun request() = migrationRequestOrNull(
        AdbMigrationRequiredException(
            "test.package",
            MigrationRequiredReason.ForeignOwner("com.android.vending"),
            "Owner migration required",
        ),
        DeviceOperationTarget("SERIAL_A", "Phone A"),
        source.path,
        inspect = { identity },
    )!!

    private fun coordinator() = UpdateOwnerMigrationCoordinator(
        isDeviceReady = { serial -> calls += "ready:$serial"; ready && serial == "SERIAL_A" },
        readOwnership = { serial, pkg ->
            calls += "owner:$serial:$pkg"
            Result.success(owner)
        },
        uninstall = { pkg, serial ->
            calls += "uninstall:$serial:$pkg"
            removeResult
        },
        install = { apk, serial, pkg ->
            calls += "install:$serial:$pkg"
            assertEquals("patched apk bytes", apk.readText())
            assertTrue(apk.absolutePath != source.absolutePath)
            assertEquals(FileChecksum.sha256(source), FileChecksum.sha256(apk))
            installResult
        },
        onPackageStateChanged = { serial, pkg -> calls += "notify:$serial:$pkg" },
        inspect = { identity },
        scratchDirectory = { File(root, "scratch") },
    )

    @Test
    fun `only true migration exception with matching APK identity can offer migration`() {
        assertNull(migrationRequestOrNull(
            IllegalArgumentException("ordinary failure"), DeviceOperationTarget("SERIAL_A", "A"), source.path,
            inspect = { identity },
        ))
        assertNull(migrationRequestOrNull(
            AdbMigrationRequiredException("another.package", MigrationRequiredReason.NoOwner, "mismatch"),
            DeviceOperationTarget("SERIAL_A", "A"), source.path, inspect = { identity },
        ))
        assertNull(migrationRequestOrNull(
            AdbMigrationRequiredException("test.package", MigrationRequiredReason.NoOwner, "missing"),
            DeviceOperationTarget("SERIAL_A", "A"), File(root, "missing.apk").path, inspect = { identity },
        ))
        assertEquals(FileChecksum.sha256(source), request().patchedApkSha256)
    }

    @Test
    fun `no data-loss consent means no read probe and no mutation`() = runBlocking {
        assertIs<UpdateOwnerMigrationResult.Blocked>(coordinator().execute(request(), false))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `changed source blocks before uninstall`() = runBlocking {
        val req = request()
        source.writeText("new unsafe bytes")
        assertIs<UpdateOwnerMigrationResult.Blocked>(coordinator().execute(req, true))
        assertFalse(calls.any { it.startsWith("uninstall:") })
    }

    @Test
    fun `disconnected target blocks all mutation`() = runBlocking {
        ready = false
        assertIs<UpdateOwnerMigrationResult.Blocked>(coordinator().execute(request(), true))
        assertFalse(calls.any { it.startsWith("uninstall:") })
    }

    @Test
    fun `changed owner blocks all mutation`() = runBlocking {
        owner = InstalledPackageOwnership.OwnedBy("app.morphe.manager")
        assertIs<UpdateOwnerMigrationResult.Blocked>(coordinator().execute(request(), true))
        assertFalse(calls.any { it.startsWith("uninstall:") })
    }

    @Test
    fun `already uninstalled package blocks migration`() = runBlocking {
        owner = InstalledPackageOwnership.NotInstalled
        assertIs<UpdateOwnerMigrationResult.Blocked>(coordinator().execute(request(), true))
        assertFalse(calls.any { it.startsWith("uninstall:") })
    }

    @Test
    fun `verified snapshot and unchanged owner permit only the selected serial`() = runBlocking {
        assertEquals(UpdateOwnerMigrationResult.Success, coordinator().execute(request(), true))
        assertEquals(1, calls.count { it == "uninstall:SERIAL_A:test.package" })
        assertEquals(1, calls.count { it == "install:SERIAL_A:test.package" })
        assertEquals(2, calls.count { it == "notify:SERIAL_A:test.package" })
        assertTrue(calls.none { it.contains("SERIAL_B") })
        assertTrue(File(root, "scratch").listFiles().isNullOrEmpty())
    }

    @Test
    fun `failed uninstall never reinstalls or reports success`() = runBlocking {
        removeResult = Result.failure(IllegalStateException("no uninstall permission"))
        val outcome = coordinator().execute(request(), true)
        assertIs<UpdateOwnerMigrationResult.UninstallFailed>(outcome)
        assertTrue(calls.none { it.startsWith("install:") })
        assertEquals(1, calls.count { it.startsWith("notify:") })
    }

    @Test
    fun `failed reinstall reports possible data loss and refreshes package state`() = runBlocking {
        installResult = Result.failure(IllegalStateException("signature rejection"))
        val outcome = coordinator().execute(request(), true)
        assertIs<UpdateOwnerMigrationResult.ReinstallFailed>(outcome)
        assertTrue(outcome.message.contains("uninstalled"))
        assertEquals(2, calls.count { it.startsWith("notify:") })
    }

    @Test
    fun `no-owner migration must still observe no-owner state`() = runBlocking {
        val req = request().copy(reason = MigrationRequiredReason.NoOwner)
        owner = InstalledPackageOwnership.NoOwner
        assertEquals(UpdateOwnerMigrationResult.Success, coordinator().execute(req, true))
    }
}
