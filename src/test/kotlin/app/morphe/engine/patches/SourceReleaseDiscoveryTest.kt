package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceReleaseDiscoveryTest {
    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        asset: String = "patches.mpp",
    ) = Release(
        tagName = tag, isPrerelease = prerelease, draft = draft,
        assets = listOf(ReleaseAsset(name = asset, downloadUrl = "https://example.test/$tag/$asset")),
    )

    @Test fun `Froggo higher stable tag is discoverable despite earlier publication order`() {
        val publishedNewestFirst = listOf(
            release("v1.2.4"), // published later; preferred by provider manifest
            release("v1.3.0"), // higher version, published earlier
            release("v1.4.0-dev.2", prerelease = true),
        )
        val candidates = additionalPublishedReleases("v1.2.4", publishedNewestFirst)
        assertEquals("v1.3.0", candidates.stableTag)
        assertEquals("v1.4.0-dev.2", candidates.experimentalTag)
        assertTrue(candidates.hasCandidates)
        // Fallback is only for sources WITHOUT a usable publisher manifest.
        assertEquals("v1.3.0", highestPublishedBundleRelease(publishedNewestFirst, false)?.tagName)
    }

    @Test fun `Piko and official experimental releases remain visible without switching selected stable`() {
        assertEquals("v3.12.0-dev.6",
            additionalPublishedReleases("v3.11.1",
                listOf(release("v3.12.0-dev.6", prerelease = true))).experimentalTag)
        val official = additionalPublishedReleases("v2.3.0", listOf(
            release("v2.4.0-dev.8", prerelease = true),
            release("v2.4.0-dev.9", prerelease = true),
        ))
        assertEquals("v2.4.0-dev.9", official.experimentalTag)
        assertNull(official.stableTag)
    }

    @Test fun `drafts incomplete downloads and nonversion tags are not candidate bundles`() {
        val releases = listOf(
            release("v9.0.0", draft = true),
            release("v8.0.0", asset = "notes.zip"),
            release("nightly-main", asset = "patches.mpp"),
            release("v1.2.0"), release("v1.2.4"),
        )
        assertFalse(additionalPublishedReleases("v1.2.4", releases).hasCandidates)
        assertNull(highestPublishedBundleRelease(releases, true))
        assertEquals("v1.2.4", highestPublishedBundleRelease(releases, false)?.tagName)
    }

    @Test fun `not checked or unparsable version is never a false update`() {
        assertFalse(additionalPublishedReleases(null, listOf(release("v2.0.0"))).hasCandidates)
        assertFalse(additionalPublishedReleases("custom-unknown", listOf(release("v2.0.0"))).hasCandidates)
        assertFalse(additionalPublishedReleases("v3.0.0", listOf(release("v2.9.0"))).hasCandidates)
    }

    @Test fun `release candidate channel recognizes rc and preview`() {
        assertTrue(release("v2.0.0-rc.1").isDevRelease())
        assertTrue(release("v2.0.0-preview.2").isDevRelease())
        assertEquals("v2.0.0-rc.1", additionalPublishedReleases(
            "v1.0.0", listOf(release("v2.0.0-rc.1"))).experimentalTag)
    }
}
