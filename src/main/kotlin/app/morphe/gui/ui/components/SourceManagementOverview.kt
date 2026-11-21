/*
 * Morphe Desktop Custom — read-only patch-source overview for the unified Sources page/dialog.
 * Filters never persist or mutate source order, trust, update eligibility or device state.
 */
package app.morphe.gui.ui.components

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import java.io.File
import java.util.Locale

internal enum class SourceViewFilter { ALL, ENABLED, ATTENTION }

internal data class SourceOverview(
    val total: Int,
    val active: Int,
    val needsAttention: Int,
    val signerChanges: Int,
    val failedSources: Int,
    val availableSourceVersions: Int,
)

internal fun sourceNeedsAttention(
    source: PatchSource,
    errorIds: Set<String>,
    signerChangedIds: Set<String>,
): Boolean = source.enabled && (source.id in errorIds || source.id in signerChangedIds)

/**
 * A source-version notice is not evidence of an app-compatible patch update.
 * Ignore disabled, unknown and failed/signer-changed sources, including stale status maps.
 */
internal fun sourceOverview(
    sources: List<PatchSource>,
    errorIds: Set<String>,
    signerChangedIds: Set<String>,
    updateIds: Set<String>,
): SourceOverview {
    val active = sources.filter(PatchSource::enabled)
    return SourceOverview(
        total = sources.size,
        active = active.size,
        needsAttention = active.count { sourceNeedsAttention(it, errorIds, signerChangedIds) },
        signerChanges = active.count { it.id in signerChangedIds },
        failedSources = active.count { it.id in errorIds && it.id !in signerChangedIds },
        availableSourceVersions = active.count {
            it.id in updateIds && !sourceNeedsAttention(it, errorIds, signerChangedIds)
        },
    )
}

/** Search only visible source identity, never signing keys, local directory paths or user data. */
internal fun visibleManagedSources(
    orderedSources: List<PatchSource>,
    filter: SourceViewFilter,
    query: String,
    errorIds: Set<String>,
    signerChangedIds: Set<String>,
): List<PatchSource> {
    val terms = query.trim().lowercase(Locale.ROOT)
        .split(Regex("\\s+")).filter(String::isNotBlank)
    return orderedSources.filter { source ->
        val permittedByFilter = when (filter) {
            SourceViewFilter.ALL -> true
            SourceViewFilter.ENABLED -> source.enabled
            SourceViewFilter.ATTENTION -> sourceNeedsAttention(source, errorIds, signerChangedIds)
        }
        if (!permittedByFilter) return@filter false
        val visibleType = when (source.type) {
            PatchSourceType.DEFAULT -> "default built-in official"
            PatchSourceType.GITHUB -> "github remote"
            PatchSourceType.GITLAB -> "gitlab remote"
            PatchSourceType.LOCAL -> "local"
        }
        val shownRemote = if (source.type == PatchSourceType.GITHUB ||
            source.type == PatchSourceType.GITLAB || source.type == PatchSourceType.DEFAULT
        ) source.url.orEmpty().substringBefore('?').substringBefore('#') else ""
        val localBasename = if (source.type == PatchSourceType.LOCAL)
            source.filePath?.let { File(it).name }.orEmpty() else ""
        val corpus = "${source.name} $visibleType $shownRemote $localBasename"
            .lowercase(Locale.ROOT)
        terms.all { it in corpus }
    }
}

/** Filtered views must not persist a partial or reordered source list. */
internal fun sourceOrderEditable(
    enabled: Boolean,
    totalSources: Int,
    filter: SourceViewFilter,
    query: String,
): Boolean = enabled && totalSources > 1 &&
    filter == SourceViewFilter.ALL && query.isBlank()
