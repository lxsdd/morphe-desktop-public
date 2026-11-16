/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.screens.home

import app.morphe.gui.util.UpdateMetadataResolution

/** A negative finding is only definitive when ALL relevant sources were checked. */
internal enum class VerifiedUpdateFact { AVAILABLE, CURRENT, UNCHECKED }

internal data class SourceChangeProof(
    val relevantChanges: Boolean,
    val checkComplete: Boolean,
)

/**
 * Per-application changelog proof, not a repository-wide "new tag" heuristic.
 * One complete positive proof remains positive despite another missing source.
 * A negative requires ALL sources to be complete; empty sources are UNKNOWN.
 */
internal fun verifiedPatchUpdateFact(
    sources: List<SourceChangeProof>,
    metadataReady: Boolean = true,
): VerifiedUpdateFact = when {
    !metadataReady -> VerifiedUpdateFact.UNCHECKED
    sources.any { it.checkComplete && it.relevantChanges } -> VerifiedUpdateFact.AVAILABLE
    sources.isNotEmpty() && sources.all { it.checkComplete } -> VerifiedUpdateFact.CURRENT
    else -> VerifiedUpdateFact.UNCHECKED
}

internal data class VerifiedAppUpdates(
    val app: VerifiedUpdateFact,
    val patch: VerifiedUpdateFact,
    /** Other unchecked sources must still be explained even after a positive result. */
    val sourceChecksIncomplete: Boolean = false,
) {
    val hasConfirmedAppUpdate: Boolean get() = app == VerifiedUpdateFact.AVAILABLE
    val hasConfirmedPatchUpdate: Boolean get() = patch == VerifiedUpdateFact.AVAILABLE
    val checkIncomplete: Boolean get() = app == VerifiedUpdateFact.UNCHECKED ||
        patch == VerifiedUpdateFact.UNCHECKED || sourceChecksIncomplete
}

/** Single evidence source for saved history badges, top tabs, details and actions. */
internal fun verifiedAppUpdates(info: RecallUpdateInfo?): VerifiedAppUpdates {
    if (info == null || info.metadataResolution != UpdateMetadataResolution.READY) {
        return VerifiedAppUpdates(VerifiedUpdateFact.UNCHECKED, VerifiedUpdateFact.UNCHECKED)
    }
    val app = if (info.appOutdated && !info.appSuggestedVersion.isNullOrBlank()) {
        VerifiedUpdateFact.AVAILABLE
    } else {
        // No newer compatible target is not a failed lookup if metadata was checked.
        VerifiedUpdateFact.CURRENT
    }
    val patch = verifiedPatchUpdateFact(info.sources.map {
        SourceChangeProof(
            relevantChanges = it.hasRelevantChanges,
            checkComplete = it.metadataKnown && !it.checkIncomplete,
        )
    })
    return VerifiedAppUpdates(
        app = app,
        patch = patch,
        sourceChecksIncomplete = info.sources.isEmpty() ||
            info.sources.any { !it.metadataKnown || it.checkIncomplete },
    )
}
