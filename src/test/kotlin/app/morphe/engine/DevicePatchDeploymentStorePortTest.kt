package app.morphe.engine

import app.morphe.engine.model.DevicePatchDeploymentRecord
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.FileChecksum
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class DevicePatchDeploymentStorePortTest {
    @Test
    fun `receipts persist independently for different synthetic device serials`() = runBlocking {
        val dir = Files.createTempDirectory("morphe-receipt-port-test").toFile()
        try {
            val file = File(dir, "receipts.json")
            val store = DevicePatchDeploymentStore(file)
            val base = DevicePatchDeploymentRecord(
                deviceSerial = "fixture-a",
                packageName = "app.example.patched",
                installedPackageName = "app.example.patched",
                apkVersion = "1.0",
                outputApkSha256 = "a".repeat(64),
                sourcesSnapshot = emptyList(),
                installedAt = 1000,
            )
            store.upsert(base)
            store.upsert(base.copy(deviceSerial = "fixture-b"))
            val reloaded = DevicePatchDeploymentStore(file)
            assertEquals(base, reloaded.get("fixture-a", base.packageName))
            assertEquals("fixture-b", reloaded.get("fixture-b", base.packageName)?.deviceSerial)
            reloaded.delete("fixture-a", base.packageName)
            assertNull(reloaded.get("fixture-a", base.packageName))
            assertNotNull(reloaded.get("fixture-b", base.packageName))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `stale local output hash cannot generate a new receipt`() = runBlocking {
        val dir = Files.createTempDirectory("morphe-hash-port-test").toFile()
        try {
            val apk = File(dir, "fixture.apk").apply { writeText("synthetic-old-data") }
            val record = PatchedAppRecord(
                packageName = "app.example.patched",
                displayName = "Test App",
                apkVersion = "1.0",
                inputApkPath = apk.absolutePath,
                outputApkPath = apk.absolutePath,
                outputApkSha256 = FileChecksum.sha256(apk),
                patchedAt = 1000L,
                patchedWithMorpheVersion = "1.19.0",
            )
            apk.writeText("synthetic-changed-data")
            val store = DevicePatchDeploymentStore(File(dir, "receipts.json"))
            assertNull(store.recordSuccessfulInstall("fixture-a", record))
            assertNull(store.get("fixture-a", record.packageName))
        } finally {
            dir.deleteRecursively()
        }
    }
}
