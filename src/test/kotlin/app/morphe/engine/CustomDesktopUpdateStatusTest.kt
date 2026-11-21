package app.morphe.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CustomDesktopUpdateStatusTest {
    private val root = "https://github.com/lxsdd/morphe-desktop-public/releases"

    private fun release(
        version: String = "2.2.0-beta.7",
        preview: Boolean = true,
        repo: String = root,
        draft: Boolean = false,
    ): String {
        val tag = "custom-v$version-aabbccddeeff"
        return """{"tag_name":"$tag","draft":$draft,"prerelease":$preview,"html_url":"$repo/tag/$tag","assets":[{"name":"Morphe-Desktop-Custom-Windows.zip","browser_download_url":"$repo/download/$tag/Morphe-Desktop-Custom-Windows.zip"}]}"""
    }

    @Test fun validCurrentCustomReleaseIsNotAnUpdate() {
        assertIs<AppUpdateCheck.UpToDate>(
            classifyCustomDesktopUpdateResponse("[${release(version = "2.2.0-beta.6")}]", "2.2.0-beta.6", ReleaseChannel.DEV)
        )
    }

    @Test fun newerVerifiedCompleteCustomBetaHasOriginalDownloadAndReleaseIdentity() {
        val outcome = assertIs<AppUpdateCheck.Available>(
            classifyCustomDesktopUpdateResponse("[${release()}]", "2.2.0-beta.6", ReleaseChannel.DEV)
        )
        assertEquals("2.2.0-beta.7", outcome.info.latestVersion)
        assertEquals("$root/download/custom-v2.2.0-beta.7-aabbccddeeff/Morphe-Desktop-Custom-Windows.zip", outcome.info.downloadLink)
        assertEquals("$root/tag/custom-v2.2.0-beta.7-aabbccddeeff", outcome.info.releaseNotesLink)
    }

    @Test fun stableOnlyNeverTreatsAPreviewAsANewStable() {
        assertIs<AppUpdateCheck.UpToDate>(
            classifyCustomDesktopUpdateResponse("[${release()}]", "2.2.0-beta.6", ReleaseChannel.STABLE)
        )
    }

    @Test fun previewCanAdvanceToGenuineStableCustomRelease() {
        val outcome = assertIs<AppUpdateCheck.Available>(
            classifyCustomDesktopUpdateResponse("[${release(version = "2.3.0", preview = false)}]", "2.2.0-beta.6", ReleaseChannel.DEV)
        )
        assertEquals("2.3.0", outcome.info.latestVersion)
        assertEquals(true, outcome.info.crossesDevToStable)
    }

    @Test fun malformedAndForeignOnlyPayloadsFailRatherThanClaimingUpToDate() {
        for (raw in listOf("", "not json", "{}", "null", "[{}]", "[${release(repo = "https://github.com/MorpheApp/morphe-desktop/releases")}]", "[${release(draft = true)}]")) {
            assertIs<AppUpdateCheck.Failed>(
                classifyCustomDesktopUpdateResponse(raw, "2.2.0-beta.6", ReleaseChannel.DEV),
                "The GitHub API reply must not be treated as a verified successful check: $raw",
            )
        }
    }

    @Test fun invalidInstalledVersionFailsClosed() {
        assertIs<AppUpdateCheck.Failed>(
            classifyCustomDesktopUpdateResponse("[${release()}]", "not-a-custom-version", ReleaseChannel.DEV)
        )
    }

    @Test fun genuineEmptyReleaseInventoryDoesNotFabricateAnUpdate() {
        assertIs<AppUpdateCheck.UpToDate>(
            classifyCustomDesktopUpdateResponse("[]", "2.2.0-beta.6", ReleaseChannel.DEV)
        )
    }
}
