package app.morphe.engine

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomDesktopReleaseCatalogTest {
    private val base = "https://github.com/lxsdd/morphe-desktop-public/releases"

    private fun release(
        tag: String,
        preview: Boolean = true,
        draft: Boolean = false,
        repoUrl: String = base,
        assetName: String = "Morphe-Desktop-Custom-Windows.zip",
        assetUrl: String = "$repoUrl/download/$tag/$assetName",
        duplicate: Boolean = false,
    ): String = buildJsonObject {
        put("tag_name", tag)
        put("draft", draft)
        put("prerelease", preview)
        put("html_url", "$repoUrl/tag/$tag")
        put("assets", buildJsonArray {
            add(buildJsonObject {
                put("name", assetName)
                put("browser_download_url", assetUrl)
            })
            if (duplicate) add(buildJsonObject {
                put("name", "Morphe-Desktop-Custom-Duplicate.zip")
                put("browser_download_url", "$repoUrl/download/$tag/Morphe-Desktop-Custom-Duplicate.zip")
            })
        })
    }.toString()

    private fun list(vararg releases: String): String = releases.joinToString(",", "[", "]")
    private val oldPreview = "custom-v2.2-core-preview-970ddcf0b271"
    private val newPreview = "custom-v2.2.0-core-preview.2-aabbccddeeff"

    @Test fun currentCustomPreviewDoesNotOfferOfficial22OrOldPreview() {
        assertNull(selectNewerCustomDesktopRelease(
            list(release(oldPreview)), "2.2.0-core-preview.2", ReleaseChannel.DEV
        ))
        assertNull(selectNewerCustomDesktopRelease(
            list(release("v2.2.0")), "2.1.1", ReleaseChannel.DEV
        ))
    }

    @Test fun exactNewerPublicPreviewHasRealDownloadAndChangelogLinks() {
        val actual = selectNewerCustomDesktopRelease(
            list(release(oldPreview), release(newPreview)),
            "2.1.1-beta.4", ReleaseChannel.DEV
        )
        assertEquals("2.2.0-core-preview.2", actual?.version)
        assertEquals("$base/download/$newPreview/Morphe-Desktop-Custom-Windows.zip", actual?.downloadLink)
        assertEquals("$base/tag/$newPreview", actual?.changelogLink)
        assertTrue(actual?.prerelease == true)
    }

    @Test fun completeBetaSupersedesEveryHistoricalCorePreviewWithoutAFalseDowngrade() {
        val fullTag = "custom-v2.2.0-beta.5-aabbccddeeff"
        val mixed = list(release(oldPreview), release(newPreview), release(fullTag))
        for (installed in listOf("2.2.0-core-preview.1", "2.2.0-core-preview.2")) {
            val selected = selectNewerCustomDesktopRelease(mixed, installed, ReleaseChannel.DEV)
            assertEquals("2.2.0-beta.5", selected?.version)
            assertEquals("$base/download/$fullTag/Morphe-Desktop-Custom-Windows.zip", selected?.downloadLink)
        }
        assertNull(selectNewerCustomDesktopRelease(
            list(release(oldPreview), release(newPreview)),
            "2.2.0-beta.5", ReleaseChannel.DEV
        ))
        assertNull(selectNewerCustomDesktopRelease(mixed, "2.2.0-beta.5", ReleaseChannel.STABLE))
        assertEquals("2.2.0", selectNewerCustomDesktopRelease(
            list(release(fullTag), release("custom-v2.2.0-aabbccddeeff", preview = false)),
            "2.2.0-beta.5", ReleaseChannel.DEV
        )?.version)
    }

    @Test fun newPreviewNeverShowsAsStableUpdate() {
        assertNull(selectNewerCustomDesktopRelease(
            list(release(newPreview)), "2.1.1", ReleaseChannel.STABLE
        ))
    }

    @Test fun stableReleaseCanReplacePreviewButNotTheOtherWayAround() {
        val stableTag = "custom-v2.2.0-aabbccddeeff"
        val stable = release(stableTag, preview = false)
        val result = selectNewerCustomDesktopRelease(
            list(release(newPreview), stable), "2.2.0-core-preview.2", ReleaseChannel.DEV
        )
        assertEquals("2.2.0", result?.version)
        assertFalse(result!!.prerelease)
        assertEquals("2.2.0", selectNewerCustomDesktopRelease(
            list(stable), "2.1.1", ReleaseChannel.STABLE
        )?.version)
    }

    @Test fun userOnCurrentPreviewIsNotOfferedSamePreviewAgain() {
        assertNull(selectNewerCustomDesktopRelease(
            list(release(newPreview)), "2.2.0-core-preview.2", ReleaseChannel.DEV
        ))
    }

    @Test fun historicalPublicBetaFourStillParses() {
        val tag = "custom-v2.1.1-beta.4"
        assertEquals("2.1.1-beta.4", parseCustomDesktopReleases(
            list(release(tag))
        ).single().version)
        assertEquals("2.2.0-core-preview.1", parseCustomDesktopReleases(
            list(release(oldPreview))
        ).single().version)
    }

    @Test fun rejectsForeignLinksInEitherReleaseOrAsset() {
        assertTrue(parseCustomDesktopReleases(list(release(newPreview,
            repoUrl = "https://github.com/MorpheApp/morphe-desktop/releases"))).isEmpty())
        assertTrue(parseCustomDesktopReleases(list(release(newPreview,
            assetUrl = "https://untrusted.invalid/release.zip"))).isEmpty())
        assertTrue(parseCustomDesktopReleases(list(release(newPreview,
            assetName = "../Morphe-Desktop-Custom-Windows.zip"))).isEmpty())
    }

    @Test fun rejectsUnknownOrTamperedReleaseIdentity() {
        assertTrue(parseCustomDesktopReleases(list(release(newPreview, draft = true))).isEmpty())
        assertTrue(parseCustomDesktopReleases(list(release(newPreview, preview = false))).isEmpty())
        assertTrue(parseCustomDesktopReleases(list(release("custom-v2.2-core-preview-notashaaaaa"))).isEmpty())
        assertTrue(parseCustomDesktopReleases(list(release(newPreview, duplicate = true))).isEmpty())
        assertTrue(parseCustomDesktopReleases(list(release(newPreview, assetName = "SHA256SUMS.txt"))).isEmpty())
    }

    @Test fun invalidApiOrCurrentVersionProducesNoUpdate() {
        assertTrue(parseCustomDesktopReleases("not-json").isEmpty())
        assertNull(selectNewerCustomDesktopRelease(list(release(newPreview)),
            "unknown", ReleaseChannel.DEV))
        assertNull(selectNewerCustomDesktopRelease("{}", "2.1.1", ReleaseChannel.DEV))
    }

    @Test fun knowsPreviewVersusStableWithoutRelyingOnOfficialVersion() {
        assertTrue(isCustomPrerelease("2.2.0-core-preview.2"))
        assertTrue(isCustomPrerelease("2.1.1-beta.4"))
        assertFalse(isCustomPrerelease("2.2.0"))
        assertFalse(isCustomPrerelease("not-semver"))
    }
}
