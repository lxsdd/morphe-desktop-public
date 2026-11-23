/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.engine.MultiSourceLoader
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.PatchBundleSignerChangedException
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PullRequestPatchSource
import app.morphe.engine.patches.SourceReleaseCandidates
import app.morphe.engine.patches.additionalPublishedReleases
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.patches.SupportedAppCatalog.toMetadata
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Resolves enabled patch sources to `.mpp` files and loads them via [MultiSourceLoader]. */
object EnabledSourcesLoader {

    data class ResolvedSource(
        val source: PatchSource,
        val patchFile: File? = null,
        val resolvedVersion: String? = null,
        val latestAvailableVersion: String? = null,
        val isOffline: Boolean = false,
        val error: String? = null,
        val channel: PatchResolver.Channel = PatchResolver.Channel.UNKNOWN,
        /** Unselected alternatives from an explicit online refresh, not app update proof. */
        val otherPublishedReleases: SourceReleaseCandidates? = null,
        val errorRes: StringResource? = null,
        val errorArgs: List<Any> = emptyList(),
        val signingKey: String? = null,
        val signingKeyFingerprint: String? = null,
        val signerChangedException: PatchBundleSignerChangedException? = null,
    ) {
        suspend fun getUserErrorMessage(): String? =
            errorRes?.let { getString(it, *errorArgs.toTypedArray()) } ?: error
    }

    /** Source resolution progress, NOT evidence of an app or patch update. */
    data class SourceLoadProgress(
        val completed: Int,
        val total: Int,
        val activeSource: PatchSource?,
        val downloadFraction: Float? = null,
    )

    data class Result(
        /** Resolution outcome per source (success or failure). */
        val resolved: List<ResolvedSource>,
        /** MultiSourceLoader output across the successfully-resolved sources. */
        val loaded: MultiSourceLoader.Result,
        /** Union of patches across all sources, for SupportedAppCatalog / UI. */
        val unionPatches: List<PatchMetadata>,
        /** Patches grouped by sourceId, for badging UI in PatchSelectionScreen. */
        val patchesBySource: Map<String, List<PatchMetadata>>,
    ) {
        val anyLoaded: Boolean get() = loaded.allPatches.isNotEmpty()

        /** Only successfully loaded nonempty MPPs may enter a patch config.
         * Failed downloads remain in [resolved] for visible diagnostics. */
        val usableResolvedSources: List<ResolvedSource>
            get() {
                val loadedIds = loaded.perSource
                    .filter { it.isSuccess && it.patches.isNotEmpty() }
                    .map { it.sourceId }
                    .toSet()
                return resolved.filter { it.patchFile != null && it.source.id in loadedIds }
            }
    }

    /** Resolves and loads every enabled source in parallel. */
    suspend fun loadAll(
        enabled: List<Pair<PatchSource, PatchRepository?>>,
        prefsBySource: Map<String, SourceVersionPref> = emptyMap(),
        excludedMppPatterns: List<String> = emptyList(),
        onDownloadProgress: ((String, Float) -> Unit)? = null,
        forceRefresh: Boolean = false,
        onSourceProgress: ((SourceLoadProgress) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) { supervisorScope {
        val total = enabled.size
        val completed = AtomicInteger(0)
        val active = ConcurrentHashMap<String, PatchSource>().apply {
            enabled.forEach { (source, _) -> put(source.id, source) }
        }
        val fractions = ConcurrentHashMap<String, Float>()
        if (total > 0) {
            onSourceProgress?.invoke(SourceLoadProgress(0, total, enabled.first().first))
        }
        val resolved = enabled.map { (source, repo) ->
            async(Dispatchers.IO) {
                try {
                    val resolvedSource = resolve(
                        source = source,
                        repo = repo,
                        pref = prefsBySource[source.id],
                        excludedMppPatterns = excludedMppPatterns,
                        onDownloadProgress = { name, pct ->
                            onDownloadProgress?.invoke(name, pct)
                            if (pct.isFinite() && pct in 0f..1f) {
                                fractions[source.id] = pct
                            } else {
                                fractions.remove(source.id)
                            }
                            onSourceProgress?.invoke(
                                SourceLoadProgress(
                                    completed = completed.get(),
                                    total = total,
                                    activeSource = source,
                                    downloadFraction = pct.takeIf { it.isFinite() && it > 0f && it < 1f },
                                )
                            )
                        },
                        forceRefresh = forceRefresh,
                    )
                    val file = resolvedSource.patchFile
                    val manifestName = file?.let { PatchBundleLoader.extractName(it) }
                    val effectiveName = manifestName?.takeUnless { it.isBlank() || PatchSourceNaming.isRawOrCorruptedName(it) }
                        ?: PatchSourceNaming.suggestNameFromUrl(source.url)
                        ?: source.name
                    if (effectiveName != resolvedSource.source.name) {
                        resolvedSource.copy(source = resolvedSource.source.copy(name = effectiveName))
                    } else {
                        resolvedSource
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ResolvedSource(source = source, error = e.message ?: e.javaClass.simpleName)
                } finally {
                    active.remove(source.id)
                    fractions.remove(source.id)
                    val done = completed.incrementAndGet()
                    if (isActive) {
                        val next = enabled.firstOrNull { active.containsKey(it.first.id) }?.first
                        onSourceProgress?.invoke(
                            SourceLoadProgress(
                                completed = done,
                                total = total,
                                activeSource = next,
                                downloadFraction = next?.let { fractions[it.id] }?.takeIf { it > 0f && it < 1f },
                            )
                        )
                    }
                }
            }
        }.awaitAll()

        val inputs = resolved.mapNotNull { res ->
            val file = res.patchFile ?: return@mapNotNull null
            val manifestName = PatchBundleLoader.extractName(file)
            val effectiveName = manifestName?.takeUnless { it.isBlank() }
                ?: res.source.name.takeUnless { PatchSourceNaming.isRawOrCorruptedName(it) }
                ?: PatchSourceNaming.resolveSourceName(file = file, candidateName = res.source.name)
            MultiSourceLoader.SourceInput(
                sourceId = res.source.id,
                sourceName = effectiveName,
                patchFile = file,
            )
        }

        val loaded = if (inputs.isEmpty()) {
            MultiSourceLoader.Result(
                perSource = emptyList(),
                allPatches = emptySet(),
                patchToSourceIds = emptyMap(),
            )
        } else {
            MultiSourceLoader.load(inputs)
        }

        val unionPatches = loaded.allPatches.map { it.toMetadata() }
        val patchesBySource: Map<String, List<PatchMetadata>> =
            loaded.perSource.associate { src ->
                src.sourceId to src.patches.map { it.toMetadata() }
            }

        Result(
            resolved = resolved,
            loaded = loaded,
            unionPatches = unionPatches,
            patchesBySource = patchesBySource,
        )
    } }

    private suspend fun resolve(
        source: PatchSource,
        repo: PatchRepository?,
        pref: SourceVersionPref?,
        excludedMppPatterns: List<String>,
        onDownloadProgress: ((String, Float) -> Unit)? = null,
        forceRefresh: Boolean = false,
    ): ResolvedSource = withContext(Dispatchers.IO) {
        val engineResult = when (source.type) {
            PatchSourceType.LOCAL -> PatchResolver.resolveLocal(source.filePath, excludedMppPatterns)
            PatchSourceType.DEFAULT,
            PatchSourceType.GITHUB,
            PatchSourceType.GITLAB -> {
                if (repo == null) {
                    PatchResolver.ResolutionResult(error = PatchResolver.Error.NoRepository(source.name))
                } else {
                    val pinnedTag = if (pref?.mode == FollowMode.PINNED) pref.pinnedTag else null
                    PatchResolver.resolveRemote(
                        repo = repo,
                        usePreRelease = source.usePreRelease,
                        pinnedTag = pinnedTag,
                        strictPin = false,
                        onDownloadProgress = { pct ->
                            onDownloadProgress?.invoke(source.name, pct)
                        },
                        forceRefresh = forceRefresh,
                    )
                }
            }
        }
        val selected = engineResult.toResolvedSource(source, repo)
        // Preserve the manifest-selected, signed bundle. Explicit refresh only:
        // audit alternative release tags without downloading or trusting them.
        if (!forceRefresh || repo == null || selected.patchFile == null ||
            selected.isOffline || repo.remoteSource is PullRequestPatchSource
        ) return@withContext selected
        val liveReleases = repo.auditPublishedReleases().getOrNull()
            ?: return@withContext selected
        selected.copy(otherPublishedReleases =
            additionalPublishedReleases(selected.resolvedVersion, liveReleases))
    }

    private fun PatchResolver.ResolutionResult.toResolvedSource(
        source: PatchSource,
        repo: PatchRepository?
    ): ResolvedSource {
        val signingKey = repo?.signingKey
        val signingKeyFingerprint = repo?.signingKeyFingerprint
        val err = error ?: return ResolvedSource(
            source = source,
            patchFile = patchFile,
            resolvedVersion = resolvedVersion,
            latestAvailableVersion = latestAvailableVersion,
            isOffline = isOffline,
            channel = channel,
            signingKey = signingKey,
            signingKeyFingerprint = signingKeyFingerprint,
        )

        val signerChangedEx = if (err is PatchResolver.Error.SignerChanged) {
            PatchBundleSignerChangedException(err.signer)
        } else null

        val (res, args) = when (err) {
            is PatchResolver.Error.LocalPathEmpty -> Res.string.source_error_local_no_path to emptyList()
            is PatchResolver.Error.LocalFileNotFound -> Res.string.source_error_local_not_found to listOf(err.fileName)
            is PatchResolver.Error.LocalNoMppInDirectory -> Res.string.source_error_local_no_mpp to listOf(err.dirName)
            is PatchResolver.Error.NoRepository -> Res.string.source_error_no_repository to emptyList()
            is PatchResolver.Error.GitHubPatRequired -> Res.string.source_error_github_pat_required to emptyList()
            is PatchResolver.Error.PrNoArtifact -> Res.string.source_error_pr_no_artifact to emptyList()
            is PatchResolver.Error.ReleaseFetchFailed -> Res.string.source_error_fetch_releases to emptyList()
            is PatchResolver.Error.SignerChanged -> Res.string.sources_signing_key_changed_named to listOf(source.name)
            is PatchResolver.Error.PinnedTagNotFound,
            is PatchResolver.Error.DownloadFailed -> null to emptyList()
        }

        return ResolvedSource(
            source = source,
            error = err.message,
            errorRes = res,
            errorArgs = args,
            channel = channel,
            signingKey = signingKey,
            signingKeyFingerprint = signingKeyFingerprint,
            signerChangedException = signerChangedEx,
        )
    }
}

/** sourceId to resolved version label (e.g. "v1.27.0-dev.2"). */
fun EnabledSourcesLoader.Result?.sourceVersionMap(): Map<String, String?> =
    this?.resolved?.associate { it.source.id to it.resolvedVersion } ?: emptyMap()

/** Maps source IDs to resolved release channels. */
fun EnabledSourcesLoader.Result?.sourceChannelMap(): Map<String, PatchResolver.Channel?> =
    this?.resolved?.associate { it.source.id to it.channel } ?: emptyMap()

/** Maps source IDs to pinned signing key fingerprints. */
fun EnabledSourcesLoader.Result?.sourceSigningKeyFingerprintMap(): Map<String, String> =
    this?.resolved?.mapNotNull { r ->
        r.signingKeyFingerprint?.let { r.source.id to it }
    }?.toMap() ?: emptyMap()

/** Maps source IDs to full pinned signing keys. */
fun EnabledSourcesLoader.Result?.sourceSigningKeyMap(): Map<String, String> =
    this?.resolved?.mapNotNull { r ->
        r.signingKey?.let { r.source.id to it }
    }?.toMap() ?: emptyMap()

/** Maps source IDs to signer changed exceptions if a release was refused for signer mismatch. */
fun EnabledSourcesLoader.Result?.sourceSignerChangedMap(): Map<String, PatchBundleSignerChangedException> =
    this?.resolved?.mapNotNull { r ->
        r.signerChangedException?.let { r.source.id to it }
    }?.toMap() ?: emptyMap()

/** Maps source IDs to available newer version string when an update exists. */
fun EnabledSourcesLoader.Result?.sourceUpdateMap(): Map<String, String> =
    this?.resolved?.mapNotNull { r ->
        val current = r.resolvedVersion ?: return@mapNotNull null
        val latest = r.latestAvailableVersion ?: return@mapNotNull null
        if (isNewerVersion(latest, current)) r.source.id to latest else null
    }?.toMap() ?: emptyMap()

/** Published release tags are NOT confirmed app-specific patch updates. */
fun EnabledSourcesLoader.Result?.sourceAdditionalReleaseMap(): Map<String, SourceReleaseCandidates> =
    this?.resolved?.mapNotNull { result ->
        result.otherPublishedReleases?.takeIf { it.hasCandidates }?.let { result.source.id to it }
    }?.toMap() ?: emptyMap()

/** Maps source IDs to load-failure messages across resolve and load phases. */
@Composable
fun EnabledSourcesLoader.Result?.sourceErrorMap(): Map<String, String> {
    val snapshot = this ?: return emptyMap()
    return buildMap {
        snapshot.resolved.forEach { r ->
            val msg = r.errorRes?.let { stringResource(it, *r.errorArgs.toTypedArray()) } ?: r.error
            msg?.let { put(r.source.id, it) }
        }
        snapshot.loaded.perSource.forEach { s ->
            if (!s.isSuccess) {
                put(s.sourceId, s.error?.let { resolvePatchLoadError(it) } ?: stringResource(Res.string.source_error_failed_to_load))
            }
        }
    }
}

/** Logs patch source resolution failure. */
fun EnabledSourcesLoader.Result.logIfFailed(prefix: String = "") {
    if (anyLoaded) return
    val firstThrowable = loaded.perSource.firstNotNullOfOrNull { it.error }
    val rawTechnicalError = firstThrowable?.message
        ?: resolved.firstNotNullOfOrNull { it.error }
    val baseMessage = when {
        rawTechnicalError.isNullOrBlank() -> "Failed to load any patches"
        rawTechnicalError.startsWith("Failed to load", ignoreCase = true) -> rawTechnicalError
        else -> "Failed to load any patches: $rawTechnicalError"
    }
    val logMessage = if (prefix.isNotBlank()) "$prefix: $baseMessage" else baseMessage
    if (firstThrowable != null) {
        Logger.error(logMessage, firstThrowable)
    } else {
        Logger.warn(logMessage)
    }
}
