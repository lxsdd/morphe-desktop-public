package app.morphe.gui.util

import app.morphe.engine.apk.ApkInspectionResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExistingApkInspectorPortTest {
    @Test
    fun `rejects missing and malformed APK inputs`() {
        val dir = Files.createTempDirectory("morphe-apk-inspection-test")
        val missing = dir.resolve("missing.apk").toFile()
        val invalid = dir.resolve("garbage.apk").toFile()
        try {
            assertTrue(ExistingApkInspector.inspect(missing).isFailure)
            invalid.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(ExistingApkInspector.inspect(invalid).isFailure)
            val bundle = dir.resolve("splits.apkm").toFile().apply { writeBytes(byteArrayOf(1)) }
            assertTrue(ExistingApkInspector.inspect(bundle).isFailure)
        } finally {
            dir.toFile().listFiles()?.forEach { it.delete() }
            dir.toFile().delete()
        }
    }

    @Test
    fun `reads single APK metadata with controlled read-only inspector`() {
        val file = Files.createTempFile("morphe-safe-apk-", ".APK").toFile()
        try {
            val result = ExistingApkInspector.inspect(file) {
                ApkInspectionResult(it, "com.example.synthetic", "2.3", 23, 24,
                    "Synthetic Example", setOf("universal"), false)
            }
            assertTrue(result.isSuccess)
            val info = result.getOrThrow()
            assertEquals("com.example.synthetic", info.packageName)
            assertEquals(file.canonicalPath, info.path)
            assertEquals(23, info.versionCode)

            val bundle = ExistingApkInspector.inspect(file) {
                ApkInspectionResult(it, "com.example.synthetic", "2.3", 23, 24,
                    "Synthetic Example", setOf("universal"), true)
            }
            assertTrue(bundle.isFailure)
            assertFalse(bundle.isSuccess)
        } finally {
            file.delete()
        }
    }
}
