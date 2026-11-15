package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.CompatiblePackage
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicePatchAvailabilityTest {
    private val youtube = "com.google.android.youtube"
    private val music = "com.google.android.apps.youtube.music"

    private val patches = listOf(
        PatchMetadata("YouTube-compatible", "A catalog patch", compatiblePackages = listOf(
            CompatiblePackage(youtube, versions = listOf("21.20.405")),
        )),
        PatchMetadata("Music-only", compatiblePackages = listOf(
            CompatiblePackage(music, versions = listOf("9.20.53")),
        )),
        PatchMetadata("YouTube-compatible", "A catalog patch", compatiblePackages = listOf(
            CompatiblePackage(youtube, versions = listOf("21.20.405")),
        )),
        PatchMetadata("Universal only", compatiblePackages = emptyList()),
    )

    @Test
    fun `YouTube is visible without any patched history`() {
        val result = availableDevicePatches(patches, youtube, "21.20.405")
        assertEquals(listOf("YouTube-compatible"), result.map { it.name })
        assertTrue(result.single().compatibleWithInstalledVersion == true)
    }

    @Test
    fun `YouTube Music uses its own package compatibility`() {
        val result = availableDevicePatches(patches, music, "9.20.53")
        assertEquals(listOf("Music-only"), result.map { it.name })
        assertTrue(result.single().compatibleWithInstalledVersion == true)
    }

    @Test
    fun `older installed app does not inherit new-version compatibility`() {
        val result = availableDevicePatches(patches, youtube, "21.18.164")
        assertFalse(result.single().compatibleWithInstalledVersion!!)
    }

    @Test
    fun `missing device version stays unknown rather than compatible`() {
        val result = availableDevicePatches(patches, youtube, null)
        assertNull(result.single().compatibleWithInstalledVersion)
    }

    @Test
    fun `source availability does not guarantee compatibility with installed version`() {
        assertEquals(DevicePatchCompatibility.COMPATIBLE,
            devicePatchCompatibility(patches, youtube, "v21.20.405"))
        assertEquals(DevicePatchCompatibility.INCOMPATIBLE,
            devicePatchCompatibility(patches, youtube, "21.18.164"))
        assertEquals(DevicePatchCompatibility.UNKNOWN_VERSION,
            devicePatchCompatibility(patches, youtube, null))
        assertEquals(DevicePatchCompatibility.NO_SOURCE,
            devicePatchCompatibility(patches, "com.example.unknown", "1.0"))
        assertEquals(DevicePatchCompatibility.NO_SOURCE,
            devicePatchCompatibility(patches, "", "1.0"))
    }

    @Test
    fun `original and renamed Android packages must be linked without merging patch evidence`() {
        val history = listOf(PatchedAppRecord(
            packageName = youtube,
            currentPackageName = "app.morphe.android.youtube",
            displayName = "YouTube",
            apkVersion = "21.45.82",
            inputApkPath = "input.apk",
            outputApkPath = "output.apk",
            patchedAt = 1L,
            patchedWithMorpheVersion = "2.1.1",
        ))
        assertEquals("app.morphe.android.youtube",
            installedSeparateHistoryPackage(youtube,
                setOf(youtube, "app.morphe.android.youtube"), history))
        assertNull(installedSeparateHistoryPackage(youtube, setOf(youtube), history))
        assertNull(installedSeparateHistoryPackage("com.instagram.android",
            setOf(youtube, "app.morphe.android.youtube"), history))
        assertNull(installedSeparateHistoryPackage(youtube,
            setOf("app.morphe.android.youtube"), emptyList()))
    }

    @Test
    fun `unrelated and uncatalogued packages do not inherit patches`() {
        assertTrue(availableDevicePatches(patches, "com.example.other", "1.0").isEmpty())
        assertTrue(availableDevicePatches(patches, "", "1.0").isEmpty())
    }

    @Test
    fun `device inventory separates stock YouTube from renamed Morphe YouTube`() {
        val original = "com.google.android.youtube"
        val renamed = "app.morphe.android.youtube"
        val history = PatchedAppRecord(
            packageName = original,
            currentPackageName = renamed,
            displayName = "YouTube",
            apkVersion = "21.45.82",
            inputApkPath = "input.apk",
            outputApkPath = "output.apk",
            patchedAt = 1L,
            patchedWithMorpheVersion = "2.1.1",
        )
        val clone = DeviceAppInfo(
            installed = true, installedVersion = "21.45.82",
            signedByMorphe = false, exactBuildVerified = true,
        )
        val stock = DeviceAppInfo(
            installed = true, installedVersion = "21.46.100",
            signedByMorphe = true, exactBuildVerified = null,
        )
        val historicByOriginal = mapOf(original to clone)
        val installed = projectHistoryInfoByInstalledPackage(listOf(history), historicByOriginal)
        assertNull(installed[original]) // No stock package information has been read yet.
        assertEquals(clone, installed[renamed])
        val afterStockProbe = installed + (original to stock)
        assertEquals("21.46.100", afterStockProbe[original]?.installedVersion)
        assertNull(afterStockProbe[original]?.exactBuildVerified)
        assertEquals(true, afterStockProbe[renamed]?.exactBuildVerified)
        assertEquals(clone, historicByOriginal[original]) // Historical detail still tracks renamed APK.
    }

    @Test
    fun `renamed package alone must never make stock app appear installed`() {
        val original = "com.google.android.youtube"
        val renamed = "app.morphe.android.youtube"
        val history = PatchedAppRecord(
            packageName = original, currentPackageName = renamed, displayName = "YouTube",
            apkVersion = "21.45.82", inputApkPath = "input.apk", outputApkPath = "output.apk",
            patchedAt = 1L, patchedWithMorpheVersion = "2.1.1",
        )
        val info = DeviceAppInfo(installed = true, installedVersion = "21.45.82")
        val actual = projectHistoryInfoByInstalledPackage(listOf(history), mapOf(original to info))
        assertEquals(setOf(renamed), actual.keys)
        assertNull(actual[original])
    }

    @Test
    fun `same-package history keeps exact proof and conflicting installed IDs fail closed`() {
        fun record(original: String, installed: String) = PatchedAppRecord(
            packageName = original, currentPackageName = installed, displayName = original,
            apkVersion = "1.0", inputApkPath = "input.apk", outputApkPath = "output.apk",
            patchedAt = 1L, patchedWithMorpheVersion = "2.1.1",
        )
        val original = "com.reddit.frontpage"
        val snapshot = DeviceAppInfo(installed = true, installedVersion = "1.0", exactBuildVerified = true)
        assertEquals(snapshot, projectHistoryInfoByInstalledPackage(
            listOf(record(original, original)), mapOf(original to snapshot))[original])
        val conflict = listOf(record("com.google.android.youtube", "app.morphe.android.youtube"),
            record("com.example.another", "app.morphe.android.youtube"))
        assertTrue(projectHistoryInfoByInstalledPackage(conflict,
            mapOf("com.google.android.youtube" to snapshot,
                "com.example.another" to snapshot)).isEmpty())
    }
}
