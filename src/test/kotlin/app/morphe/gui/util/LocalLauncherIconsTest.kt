package app.morphe.gui.util

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** No ADB, device, signing materials or actual user data are used by these regressions. */
class LocalLauncherIconsTest {
    @Test fun unknownOrMissingSourcesKeepInitials() = runBlocking {
        assertNull(LocalLauncherIcons.key(null))
        assertNull(LocalLauncherIcons.load(""))
        assertNull(LocalLauncherIcons.load("/not-a-real-user-file/morphe.apk"))
        val unsupported = File.createTempFile("icon-file-", ".txt")
        try {
            unsupported.writeText("no apk")
            assertNull(LocalLauncherIcons.key(unsupported.path))
            assertNull(LocalLauncherIcons.load(unsupported.path))
        } finally {
            unsupported.delete()
        }
    }

    @Test fun corruptApkOrBundleFailsClosed() = runBlocking {
        for (suffix in listOf(".apk", ".apkm", ".xapk", ".apks")) {
            val source = File.createTempFile("icon-file-", suffix)
            try {
                source.writeText("corrupt and untrusted archive")
                assertNotNull(LocalLauncherIcons.key(source.path))
                assertNull(LocalLauncherIcons.load(source.path))
            } finally {
                source.delete()
            }
        }
    }

    @Test fun changedFileIdentityInvalidatesFailedCache() = runBlocking {
        val source = File.createTempFile("icon-file-", ".apk")
        try {
            source.writeText("invalid zip")
            val firstKey = LocalLauncherIcons.key(source.path)
            assertNotNull(firstKey)
            assertNull(LocalLauncherIcons.load(source.path))
            source.appendText("updated bytes")
            assertNotEquals(firstKey, LocalLauncherIcons.key(source.path))
            assertNull(LocalLauncherIcons.load(source.path))
        } finally {
            source.delete()
        }
    }

    @Test fun validZipWithNoLauncherIconUsesFallback() = runBlocking {
        val source = File.createTempFile("icon-file-", ".apk")
        try {
            ZipOutputStream(source.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("not a launcher icon".toByteArray())
                zip.closeEntry()
            }
            assertNull(LocalLauncherIcons.load(source.path))
        } finally {
            source.delete()
        }
    }
}
