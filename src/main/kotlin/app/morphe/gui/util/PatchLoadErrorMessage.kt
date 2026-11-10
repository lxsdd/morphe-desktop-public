/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.PatchBundleIncompatibleException
import app.morphe.engine.PatchSourceLoadException
import app.morphe.engine.PatcherCompatibility
import app.morphe.engine.PatcherRequirementDirection
import app.morphe.engine.patcherRequirementDirection
import app.morphe.engine.patches.PatchBundleSignerChangedException
import app.morphe.engine.readableMessage
import app.morphe.morphe_desktop.generated.resources.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.UnknownHostException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private fun incompatibleBundleDisplay(e: PatchBundleIncompatibleException): Pair<StringResource, Array<out Any>> {
    val required = e.requiredPatcherVersion ?: "?"
    val current = e.currentPatcherVersion ?: PatcherCompatibility.currentPatcherVersion ?: "?"
    return when (patcherRequirementDirection(required, current)) {
        PatcherRequirementDirection.LEGACY_MAJOR ->
            Res.string.source_error_patcher_legacy_major to arrayOf(required, current)
        PatcherRequirementDirection.NEWER_REQUIRED ->
            Res.string.source_error_patcher_requires_newer to arrayOf(required, current)
        PatcherRequirementDirection.UNKNOWN ->
            Res.string.source_error_patcher_unknown to arrayOf(current)
    }
}

/** True only when every failed source has a verified older-major Patcher bundle.
 *  Resolution, signer or network failures never get mislabeled as legacy. */
internal fun onlyLegacyPatcherFailures(
    failedSourceIds: Set<String>,
    loadErrorsBySource: Map<String, Throwable?>,
): Boolean = failedSourceIds.isNotEmpty() && failedSourceIds.all { sourceId ->
    val exception = when (val error = loadErrorsBySource[sourceId]) {
        is PatchBundleIncompatibleException -> error
        is PatchSourceLoadException -> error.cause as? PatchBundleIncompatibleException
        else -> null
    }
    exception != null && patcherRequirementDirection(
        exception.requiredPatcherVersion,
        exception.currentPatcherVersion,
    ) == PatcherRequirementDirection.LEGACY_MAJOR
}

/** Maps load failure exceptions to localized or human-readable strings. */
private inline fun <T> mapPatchLoadError(
    e: Throwable,
    onResource: (StringResource, Array<out Any>) -> T,
    onText: (String) -> T,
): T = when (e) {
    is GitHubPatMissingException -> onResource(Res.string.source_error_github_pat_required, emptyArray())

    is HttpRequestTimeoutException,
    is SocketTimeoutException,
    is ConnectTimeoutException -> onResource(Res.string.error_network_timeout, emptyArray())

    is UnknownHostException -> onResource(Res.string.error_could_not_reach_patch_server, emptyArray())

    is IOException -> {
        val msg = e.message.orEmpty()
        when {
            msg.contains("rate limit", ignoreCase = true) ->
                onResource(Res.string.error_github_rate_limit, emptyArray())
            msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("connection closed", ignoreCase = true) ->
                onResource(Res.string.error_connection_dropped, emptyArray())
            else -> msg.ifBlank { null }?.let(onText) ?: onResource(Res.string.error_network_loading_patches, emptyArray())
        }
    }

    is PatchBundleSignerChangedException ->
        onResource(Res.string.sources_management_signing_key_changed_hint, emptyArray())

    is PatchBundleIncompatibleException -> {
        val (resource, args) = incompatibleBundleDisplay(e)
        onResource(resource, args)
    }

    is PatchSourceLoadException -> {
        val cause = e.cause
        when {
            cause is PatchBundleSignerChangedException ->
                onResource(Res.string.sources_management_signing_key_changed_hint, emptyArray())
            cause is PatchBundleIncompatibleException -> {
                val (resource, args) = incompatibleBundleDisplay(cause)
                onResource(resource, args)
            }
            cause is LinkageError ->
                onResource(Res.string.source_error_patcher_unknown, arrayOf(PatcherCompatibility.currentPatcherVersion ?: "?"))
            e.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
                onResource(Res.string.source_error_github_pat_required, emptyArray())
            e.message?.contains("No artifacts found", ignoreCase = true) == true ||
                e.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
                onResource(Res.string.source_error_pr_no_artifact, emptyArray())
            else -> onText(e.message?.takeIf { it.isNotBlank() } ?: e.readableMessage())
        }
    }

    is LinkageError ->
        onResource(Res.string.source_error_patcher_unknown, arrayOf(PatcherCompatibility.currentPatcherVersion ?: "?"))

    else -> when {
        e.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
            onResource(Res.string.source_error_github_pat_required, emptyArray())
        e.message?.contains("No artifacts found", ignoreCase = true) == true ||
            e.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
            onResource(Res.string.source_error_pr_no_artifact, emptyArray())
        else -> onText(e.readableMessage())
    }
}

suspend fun humanizePatchLoadError(e: Throwable): String =
    mapPatchLoadError(e, onResource = { res, args -> getString(res, *args) }, onText = { it })

@Composable
fun resolvePatchLoadError(e: Throwable): String =
    mapPatchLoadError(e, onResource = { res, args -> stringResource(res, *args) }, onText = { it })
