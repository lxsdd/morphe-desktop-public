/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.util

import app.morphe.engine.PatchEngine.PatchStep

/** The 2.0 engine's step-weighted progress calculation; no UI or device mutation. */
fun runProgress(steps: List<PatchStep>, step: PatchStep, appliedPatches: Int, totalPatches: Int): Float {
    val index = steps.indexOf(step)
    if (index < 0) return 0f
    var done = steps.subList(0, index).fold(0f) { sum, it -> sum + it.weight }
    if (step == PatchStep.PATCHING && totalPatches > 0) done += step.weight * appliedPatches / totalPatches
    return (done / steps.fold(0f) { sum, it -> sum + it.weight }).coerceIn(0f, 1f)
}

private val PatchStep.weight: Float
    get() = when (this) {
        PatchStep.MERGING_SPLITS -> 0.10f
        PatchStep.LOADING_PATCHES -> 0.05f
        PatchStep.READING_APK -> 0.05f
        PatchStep.PATCHING -> 0.40f
        PatchStep.REBUILDING -> 0.40f
        PatchStep.SIGNING -> 0.10f
    }
