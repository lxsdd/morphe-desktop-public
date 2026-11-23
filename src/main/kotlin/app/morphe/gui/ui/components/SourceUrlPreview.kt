/*
 * Morphe Desktop Custom — read-only upstream 2.3 source URL recognition.
 * A syntactically recognized URL is NOT verified source ownership or signer trust.
 */
package app.morphe.gui.ui.components

import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.RemotePatchSourceFactory

internal enum class RemoteSourceInputKind { EMPTY, UNRECOGNIZED, GITHUB, GITLAB, GITHUB_PR }

internal fun remoteSourceInputKind(input: String): RemoteSourceInputKind {
    if (input.isBlank()) return RemoteSourceInputKind.EMPTY
    val parsed = RemotePatchSourceFactory.parse(input) ?: return RemoteSourceInputKind.UNRECOGNIZED
    return when (parsed.provider) {
        PatchProvider.GITHUB -> RemoteSourceInputKind.GITHUB
        PatchProvider.GITLAB -> RemoteSourceInputKind.GITLAB
        PatchProvider.GITHUB_PR -> RemoteSourceInputKind.GITHUB_PR
    }
}

/**
 * Editing a source's canonical identity must be made visible before explicit Save.
 * Simple shorthand vs full URL of the SAME source must not trigger a false warning.
 */
internal fun remoteSourceIdentityChanged(original: String?, proposed: String): Boolean {
    val old = RemotePatchSourceFactory.parse(original.orEmpty())?.canonicalUrl
        ?: original.orEmpty().trim()
    val next = RemotePatchSourceFactory.parse(proposed)?.canonicalUrl ?: proposed.trim()
    return old != next
}
