package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.AppConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class VerifiedLocalIconSourceTest {
    private fun record(
        sha: String? = "a".repeat(64),
        renamed: String? = "com.example.patched",
    ): PatchedAppRecord = PatchedAppRecord(
        packageName = "com.example.original",
        currentPackageName = renamed,
        displayName = "Example",
        apkVersion = "1.2.3",
        inputApkPath = "/example/original.apk",
        outputApkPath = "/example/patched.apk",
        outputApkSha256 = sha,
        patchedAt = 1L,
        patchedWithMorpheVersion = "2.1.1",
    )

    @Test fun exactMatchingBuildOnlyMayDisplayTheRecordedIcon() {
        val r = record()
        assertSame(r, verifiedLocalIconSource(r, "com.example.patched", "v1.2.3", true))
        assertNull(verifiedLocalIconSource(null, "com.example.patched", "1.2.3", true))
        assertNull(verifiedLocalIconSource(r, "com.example.patched", "1.2.3", false))
        assertNull(verifiedLocalIconSource(r, "com.example.original", "1.2.3", true))
        assertNull(verifiedLocalIconSource(r, "com.example.patched", "1.2.4", true))
        assertNull(verifiedLocalIconSource(r, "com.example.patched", null, true))
        assertNull(verifiedLocalIconSource(record(sha = null), "com.example.patched", "1.2.3", true))
        assertNull(verifiedLocalIconSource(record(sha = ""), "com.example.patched", "1.2.3", true))
        assertNull(verifiedLocalIconSource(record(sha = "invalid"), "com.example.patched", "1.2.3", true))
        val unchanged = record(renamed = null)
        assertSame(unchanged, verifiedLocalIconSource(unchanged, "com.example.original", "1.2.3", true))
    }

    @Test fun persistedVisibilityDefaultsToPriorEnabledBehaviorAndRoundTrips() {
        assertTrue(AppConfig().showLocalAppIcons)
        val old = Json.decodeFromString(AppConfig.serializer(), "{}")
        assertTrue(old.showLocalAppIcons)
        val disabled = AppConfig(showLocalAppIcons = false)
        assertFalse(Json.decodeFromString(
            AppConfig.serializer(),
            Json.encodeToString(AppConfig.serializer(), disabled),
        ).showLocalAppIcons)
    }
}
