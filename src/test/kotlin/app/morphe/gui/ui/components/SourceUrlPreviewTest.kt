package app.morphe.gui.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceUrlPreviewTest {
    @Test fun recognizedProvidersAreSourceFormatsNotTrustedKeys() {
        assertEquals(RemoteSourceInputKind.EMPTY, remoteSourceInputKind("   "))
        assertEquals(RemoteSourceInputKind.UNRECOGNIZED, remoteSourceInputKind("this is not a source"))
        assertEquals(RemoteSourceInputKind.UNRECOGNIZED, remoteSourceInputKind("/private/app-patches.mpp"))
        assertEquals(RemoteSourceInputKind.GITHUB, remoteSourceInputKind("MorpheApp/morphe-patches"))
        assertEquals(RemoteSourceInputKind.GITHUB, remoteSourceInputKind("https://github.com/MorpheApp/morphe-patches"))
        assertEquals(RemoteSourceInputKind.GITLAB, remoteSourceInputKind("https://gitlab.com/example/patches"))
        assertEquals(RemoteSourceInputKind.GITHUB_PR, remoteSourceInputKind("https://github.com/example/patches/pull/25"))
    }

    @Test fun canonicalSourceEditsWarnOnlyWhenIdentityActuallyChanges() {
        assertFalse(remoteSourceIdentityChanged(
            "https://github.com/MorpheApp/morphe-patches",
            "MorpheApp/morphe-patches",
        ))
        assertFalse(remoteSourceIdentityChanged(
            "https://github.com/example/patches", "https://github.com/example/patches/",
        ))
        assertTrue(remoteSourceIdentityChanged(
            "https://github.com/example/patches", "https://gitlab.com/example/patches",
        ))
        assertTrue(remoteSourceIdentityChanged(
            "https://github.com/example/patches", "https://github.com/example/other",
        ))
        assertTrue(remoteSourceIdentityChanged(
            "https://github.com/example/patches", "https://github.com/example/patches/pull/25",
        ))
    }
}
