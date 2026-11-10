/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.Logger
import app.morphe.engine.util.isNewerVersion
import java.io.File
import java.util.zip.ZipInputStream

/** Checks patch bundle compatibility against the bundled morphe-patcher version. */
object PatcherCompatibility {

    /** Version of morphe-patcher on the classpath. */
    val currentPatcherVersion: String? get() = MorpheComponents.patcherVersion

    /** Reads the required patcher version from [mpp]'s manifest, or null if missing. */
    fun requiredPatcherVersion(mpp: File): String? = runCatching {
        mpp.inputStream().use { fis ->
            ZipInputStream(fis).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "META-INF/MANIFEST.MF") {
                        return@runCatching patcherVersionFromManifest(zip.bufferedReader().readText())
                    }
                    entry = zip.nextEntry
                }
                null
            }
        }
    }.getOrNull()

    /** Returns an explanation if [mpp] requires a newer patcher than the active build, or null if compatible. */
    fun incompatibilityMessage(mpp: File): String? {
        val required = requiredPatcherVersion(mpp) ?: return null
        val current = currentPatcherVersion ?: return null
        if (!isIncompatiblePatcherVersion(required, current)) return null
        Logger.info("Bundle ${mpp.name} needs patcher $required, this build ships $current")
        return "This patch bundle requires Morphe Patcher $required, but this build ships $current. " +
            "Different major versions are incompatible; use a patch source built for this Patcher version."
    }

    /**
     * A Patcher 1.x bundle cannot be loaded by Patcher 2.x even if its
     * numeric requirement is lower. Fail closed on invalid requirements,
     * newer versions, or any major-version mismatch.
     */
    internal fun isIncompatiblePatcherVersion(required: String, current: String): Boolean {
        val requiredMajor = required.trim().removePrefix("v").substringBefore('.').toIntOrNull()
        val currentMajor = current.trim().removePrefix("v").substringBefore('.').toIntOrNull()
        if (requiredMajor == null || currentMajor == null) return true
        return requiredMajor != currentMajor || isNewerVersion(required, current)
    }

    private fun patcherVersionFromManifest(manifest: String): String? =
        manifest.lineSequence()
            .firstOrNull { it.substringBefore(':', "").trim().equals("Patcher-Version", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?.takeUnless { it.isBlank() || it.equals("na", ignoreCase = true) }
}

/** Actionable diagnostic only. It never changes the fail-closed bundle gate. */
enum class PatcherRequirementDirection { LEGACY_MAJOR, NEWER_REQUIRED, UNKNOWN }

/** Distinguishes old-major community bundles from genuinely newer requirements. */
internal fun patcherRequirementDirection(required: String?, current: String?): PatcherRequirementDirection {
    fun major(version: String?): Int? = version?.trim()?.removePrefix("v")
        ?.substringBefore('.')?.toIntOrNull()
    val requiredMajor = major(required) ?: return PatcherRequirementDirection.UNKNOWN
    val currentMajor = major(current) ?: return PatcherRequirementDirection.UNKNOWN
    return when {
        requiredMajor < currentMajor -> PatcherRequirementDirection.LEGACY_MAJOR
        requiredMajor > currentMajor -> PatcherRequirementDirection.NEWER_REQUIRED
        required != null && current != null && isNewerVersion(required, current) ->
            PatcherRequirementDirection.NEWER_REQUIRED
        else -> PatcherRequirementDirection.UNKNOWN
    }
}

/** Thrown when a patch bundle is incompatible with the current patcher version. */
class PatchBundleIncompatibleException(
    message: String,
    val requiredPatcherVersion: String? = null,
    val currentPatcherVersion: String? = null,
    val appVersion: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
