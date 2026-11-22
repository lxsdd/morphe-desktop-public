package app.morphe.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.morphe.engine.config.EngineConfig

class OriginalApkStoreTest {
    private val root = Files.createTempDirectory("custom-original-test").toFile()
    private val dir = File(root, "original-apks")
    private val downloads = File(root, "download").also { it.mkdirs() }
    private val history = PatchedAppStore(File(root, "history.json"))
    private val store = OriginalApkStore(dir, history)

    @AfterTest fun cleanup() { root.deleteRecursively() }
    private fun input(name: String, value: String = "original") =
        File(downloads, name).apply { writeText(value) }

    @Test fun defaultAndUnknownConfigNeverEnableWrites() {
        assertNull(EngineConfig().resolvedOriginalApkMode())
        assertNull(EngineConfig(originalApkRetention = "unknown").resolvedOriginalApkMode())
        assertEquals(SaveOriginalApk.COPY, EngineConfig(originalApkRetention = "COPY").resolvedOriginalApkMode())
        assertEquals(SaveOriginalApk.MOVE, EngineConfig(originalApkRetention = "MOVE").resolvedOriginalApkMode())
        assertFalse(dir.exists())
    }

    @Test fun copyingPreservesOriginalAndDoesNotDeleteOldVersions() {
        val first = input("a.apk", "old")
        val kept1 = store.save(first, "com.example.app", "1.0")!!
        val kept2 = store.save(input("b.apk", "new"), "com.example.app", "2.0")!!
        assertEquals("old", kept1.readText())
        assertEquals("new", kept2.readText())
        assertTrue(first.exists())
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test fun reusingIdenticalFileDoesNotOverwriteAnExistingOriginal() {
        val file = input("a.apk", "same")
        val kept = store.save(file, "com.example.app", "1")!!
        assertEquals(kept, store.save(file, "com.example.app", "1"))
        assertNull(store.save(kept, "com.example.app", "1"))
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test fun conflictingCopyFailsClosedAndPreservesBothUserFiles() {
        val file = input("a.apk", "first")
        val kept = store.save(file, "com.example.app", "1")!!
        val newer = input("b.apk", "changed")
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            store.save(newer, "com.example.app", "1")
        }
        assertEquals("first", kept.readText())
        assertEquals("changed", newer.readText())
    }

    @Test fun deletionIsRestrictedToTheStoreAndNeverRunsDuringSave() {
        val source = input("a.apk")
        val kept = store.save(source, "com.example.app", "1")!!
        assertFalse(store.delete(source))
        assertTrue(source.exists())
        assertTrue(store.delete(kept))
        assertFalse(kept.exists())
    }

    @Test fun moveCanDeleteOnlyAnUnmodifiedSourceMatchingItsRetainedCopy() {
        val original = input("a.apk", "original")
        val kept = store.save(original, "com.example.app", "1")!!
        original.writeText("modified")
        assertFalse(store.removeVerifiedSource(original, kept))
        assertTrue(original.exists())
        original.writeText("original")
        assertTrue(store.removeVerifiedSource(original, kept))
        assertFalse(original.exists())
        assertTrue(kept.exists())
        assertFalse(store.removeVerifiedSource(kept, kept))
    }

    @Test fun unsupportedInputAndUnsafeStoreCannotWriteFiles() {
        val txt = input("text.txt")
        assertFailsWith<IllegalArgumentException> { store.save(txt, "com.example.app", "1") }
        assertFalse(dir.exists())
        val missing = File(downloads, "missing.apk")
        assertFailsWith<IllegalArgumentException> { store.save(missing, "com.example.app", "1") }
        assertFalse(dir.exists())
    }

    @Test fun storedInputsCannotSuggestAnOutputInsideTheStore() = kotlinx.coroutines.runBlocking {
        val source = input("a.apk")
        assertNull(store.outputBaseFor(source))
        val kept = store.save(source, "com.example.app", "1")!!
        assertEquals(File(System.getProperty("user.home")), store.outputBaseFor(kept))
    }

    @Test fun storingAnExistingVersionCannotReplaceEarlierUserContent() {
        val first = store.save(input("a.apk", "first"), "com.example.app", "1")!!
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            store.save(input("b.apk", "second"), "com.example.app", "1")
        }
        assertEquals("first", first.readText())
    }

    @Test fun deletingAnUnreferencedOriginalRequiresAnotherHistoryEntryNotToShareIt() = kotlinx.coroutines.runBlocking {
        val kept = store.save(input("a.apk"), "com.example.app", "1")!!
        fun record(pkg: String) = app.morphe.engine.model.PatchedAppRecord(
            packageName = pkg, displayName = pkg, apkVersion = "1",
            inputApkPath = kept.absolutePath,
            outputApkPath = File(downloads, pkg + ".apk").absolutePath,
            patchedAt = 1L, patchedWithMorpheVersion = "test",
        )
        history.upsert(record("com.example.app"))
        history.upsert(record("com.example.other"))
        history.delete("com.example.app")
        assertFalse(store.deleteUnreferencedAfterForget(record("com.example.app"), kept.absolutePath))
        assertTrue(kept.exists())
        history.delete("com.example.other")
        assertTrue(store.deleteUnreferencedAfterForget(record("com.example.app"), kept.absolutePath))
    }

    @Test fun deletionOfKeptOriginalRequiresRemovedHistoryAndSameConfirmedPath() = kotlinx.coroutines.runBlocking {
        val source = input("delete.apk")
        val kept = store.save(source, "com.example.app", "1")!!
        val record = app.morphe.engine.model.PatchedAppRecord(
            packageName = "com.example.app",
            displayName = "App",
            apkVersion = "1",
            inputApkPath = kept.absolutePath,
            outputApkPath = File(downloads, "App/out.apk").absolutePath,
            patchedAt = 1L,
            patchedWithMorpheVersion = "test",
        )
        history.upsert(record)
        assertFalse(store.deleteUnreferencedAfterForget(record, record.inputApkPath))
        assertTrue(kept.exists())
        history.delete(record.packageName)
        assertFalse(store.deleteUnreferencedAfterForget(record, source.absolutePath))
        assertTrue(kept.exists())
        assertTrue(store.deleteUnreferencedAfterForget(record, record.inputApkPath))
        assertFalse(kept.exists())
        assertTrue(source.exists())
    }

}
