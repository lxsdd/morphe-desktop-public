package app.morphe.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression for upstream 2.1.1: relative split-bundle paths must survive staging. */
class SplitBundleStagingTest {
    // Test-only build directory, never the user's Morphe data, APK, or device.
    private val root: File = Files.createTempDirectory(
        File("build/test-morphe-data").also { it.mkdirs() }.toPath(),
        "split-path-",
    ).toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `relative input remains readable from a separate merge workspace`() {
        val source = File(root, "selected files/My video.apkm").also {
            it.parentFile.mkdirs()
            it.writeText("fixture split payload")
        }
        val relative = Path.of("").toAbsolutePath().relativize(source.toPath().toAbsolutePath())
        assertFalse(relative.isAbsolute)
        val workspace = File(root, "workspace").also { it.mkdirs() }

        val staged = PatchEngine.stageSplitBundleInput(File(relative.toString()), workspace)
        assertTrue(staged.isFile)
        assertEquals("fixture split payload", staged.readText())
        assertEquals(workspace.canonicalFile, staged.parentFile.canonicalFile)
        staged.delete()
        assertEquals("fixture split payload", source.readText())
    }

    @Test
    fun `input already inside merge workspace is preserved`() {
        val workspace = File(root, "workspace").also { it.mkdirs() }
        val source = File(workspace, "selected.apkm").also { it.writeText("keep source") }

        val staged = PatchEngine.stageSplitBundleInput(source, workspace)
        assertEquals(source.absoluteFile, staged)
        assertTrue(source.isFile)
        assertEquals("keep source", source.readText())
    }

    @Test
    fun `missing split source cannot create a dangling staging link`() {
        val workspace = File(root, "workspace").also { it.mkdirs() }
        val missing = File(root, "missing.apkm")
        assertFailsWith<IllegalArgumentException> {
            PatchEngine.stageSplitBundleInput(missing, workspace)
        }
        assertFalse(File(workspace, "missing.apkm").exists())
    }
}
