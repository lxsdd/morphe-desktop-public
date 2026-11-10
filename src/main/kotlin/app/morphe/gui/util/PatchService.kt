/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.PatchEngine
import app.morphe.engine.PatchEvent
import app.morphe.engine.patches.SupportedAppCatalog.toMetadata
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.gui.data.model.CompatiblePackage
import app.morphe.gui.data.model.ExplicitOptionKind
import app.morphe.gui.data.model.ImageSize
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchOption
import app.morphe.gui.data.model.PatchOptionType
import app.morphe.gui.data.model.SliderBounds
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.patch.ColorOption
import app.morphe.patcher.patch.FilePathOption
import app.morphe.patcher.patch.FilesOption
import app.morphe.patcher.patch.FloatRangeOption
import app.morphe.patcher.patch.FloatSliderOption
import app.morphe.patcher.patch.FolderOption
import app.morphe.patcher.patch.ImageOption
import app.morphe.patcher.patch.IntRangeOption
import app.morphe.patcher.patch.IntSliderOption
import app.morphe.patcher.patch.Patch as LibraryPatch
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.patcher.resource.CpuArchitecture
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlin.reflect.KType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Bridge between GUI and morphe-patcher library.
 * Replaces CliRunner with direct library calls.
 */
class PatchService {

    /**
     * Load patches from an .mpp file and convert to GUI model.
     * Optionally filter by package name.
     */
    suspend fun listPatches(
        patchesFilePath: String,
        packageName: String? = null
    ): Result<List<Patch>> = withContext(Dispatchers.IO) {
        try {
            val patchFile = File(patchesFilePath)
            if (!patchFile.exists()) {
                return@withContext Result.failure(PatchException("Patch file not found: $patchesFilePath", Res.string.error_patch_file_not_found, listOf(patchesFilePath)))
            }

            Logger.info("Loading patches from: $patchesFilePath")

            // Copy to temp file so URLClassLoader locks the copy, not the cached original.
            // On Windows, the classloader holds the file locked and prevents deletion.
            val tempCopy = File.createTempFile("morphe-patches-", ".mpp")
            try {
                patchFile.copyTo(tempCopy, overwrite = true)
                val patches = PatchBundleLoader.loadFlat(listOf(patchFile))

                // Convert library patches to GUI model
                val guiPatches = patches.map { it.toGuiPatch() }

                // Filter by package name if specified
                val filtered = if (packageName != null) {
                    guiPatches.filter { patch ->
                        patch.compatiblePackages.isEmpty() || // Universal patches
                        patch.compatiblePackages.any { it.name == packageName }
                    }
                } else {
                    guiPatches
                }

                Logger.info("Loaded ${filtered.size} patches" + (packageName?.let { " for $it" } ?: ""))
                Result.success(filtered)
            } finally {
                tempCopy.deleteOnExit()
            }
        } catch (e: Exception) {
            Logger.error("Failed to load patches", e)
            Result.failure(e)
        }
    }

    /**
     * Execute patching operation with progress callbacks.
     * Delegates to PatchEngine for the actual pipeline.
     */
    suspend fun patch(
        patchesFilePaths: List<String>,
        inputApkPath: String,
        outputApkPath: String,
        enabledPatches: List<String> = emptyList(),
        disabledPatches: List<String> = emptyList(),
        options: Map<String, String> = emptyMap(),
        exclusiveMode: Boolean = false,
        keepArchitectures: Set<CpuArchitecture> = emptySet(),
        continueOnError: Boolean = false,
        forceCompatibility: Boolean = true,
        keystorePath: String? = null,
        keystorePassword: String? = null,
        keystoreAlias: String? = null,
        keystoreEntryPassword: String? = null,
        onProgress: (String) -> Unit = {}
    ): Result<PatchResult> = withContext(Dispatchers.IO) {
        try {
            if (patchesFilePaths.isEmpty()) {
                return@withContext Result.failure(PatchException("No patch files supplied", Res.string.error_no_patches_files_supplied))
            }
            val patchFiles = patchesFilePaths.map { File(it) }
            val inputApk = File(inputApkPath)
            val outputFile = File(outputApkPath)

            patchFiles.firstOrNull { !it.exists() }?.let {
                return@withContext Result.failure(PatchException("Patch file not found: ${it.name}", Res.string.error_patch_file_not_found, listOf(it.name)))
            }
            if (!inputApk.exists()) {
                return@withContext Result.failure(PatchException("Input APK file not found: $inputApkPath", Res.string.error_input_apk_not_found))
            }

            // Load patches (copy each to temp to avoid Windows file lock)
            onProgress("Loading patches...")
            val tempCopies = patchFiles.map { src ->
                val tmp = File.createTempFile("morphe-patches-", ".mpp")
                src.copyTo(tmp, overwrite = true)
                tmp
            }
            try {
                val loadedPatches = PatchBundleLoader.loadFlat(tempCopies)

                // Preserve the patcher's native option types when converting the
                // GUI's persisted string representation back into engine values.
                val patchOptionTypes: Map<String, Map<String, KType>> = loadedPatches
                    .filter { it.name != null }
                    .associate { patch ->
                        patch.name!! to patch.options.mapValues { (_, option) -> option.type }
                    }

                // Convert GUI's flat "patchName.optionKey" -> value map
                // to engine's Map<patchName, Map<optionKey, value>> format
                val patchOptions = enabledPatches.associateWith { patchName ->
                    options.filterKeys { it.startsWith("$patchName.") }
                        .mapKeys { it.key.removePrefix("$patchName.") }
                        .mapNotNull { (optKey, strValue) ->
                            val kType = patchOptionTypes[patchName]?.get(optKey)
                            val coerced = if (kType != null) {
                                coerceOptionValue(kType, strValue) ?: strValue
                            } else {
                                strValue
                            }
                            optKey to coerced
                        }.toMap()
                }.filter { it.value.isNotEmpty() }

                val keystoreDetails = if (keystorePath != null) {
                    ApkUtils.KeyStoreDetails(
                        keyStore = File(keystorePath),
                        keyStorePassword = keystorePassword,
                        alias = keystoreAlias ?: PatchEngine.Config.DEFAULT_KEYSTORE_ALIAS,
                        password = keystoreEntryPassword ?: PatchEngine.Config.DEFAULT_KEYSTORE_PASSWORD,
                    )
                } else null

                val config = PatchEngine.Config(
                    inputApk = inputApk,
                    patches = loadedPatches,
                    outputApk = outputFile,
                    enabledPatches = enabledPatches.toSet(),
                    disabledPatches = disabledPatches.toSet(),
                    exclusiveMode = exclusiveMode,
                    forceCompatibility = forceCompatibility,
                    patchOptions = patchOptions,
                    architecturesToKeep = keepArchitectures,
                    failOnError = !continueOnError,
                    keystoreDetails = keystoreDetails,
                )

                val engineResult = PatchEngine.patch(config) { event ->
                    when (event) {
                        is PatchEvent.Log -> onProgress(event.message)
                        is PatchEvent.StepStarted -> onProgress("Step: ${event.step.name}")
                        is PatchEvent.PatchApplied -> onProgress("Applied: ${event.name}")
                        is PatchEvent.TaskProgress -> onProgress(
                            "${event.task}: ${event.done}/${event.total}"
                        )
                    }
                }

                val failureDetail: String? = if (engineResult.success) null else buildString {
                    if (engineResult.failedPatches.isNotEmpty()) {
                        engineResult.failedPatches.forEach { fp ->
                            appendLine("Patch '${fp.name}' failed:")
                            appendLine(fp.error)
                        }
                    } else {
                        engineResult.stepResults
                            .filter { !it.success && it.error != null }
                            .forEach { appendLine("Step ${it.step.name} failed:"); appendLine(it.error) }
                    }
                }.takeIf { it.isNotBlank() }
                failureDetail?.let { Logger.error("Patching failed - full detail:\n$it") }

                val (failureReason, failureReasonRes, failureReasonArgs) = if (engineResult.success) {
                    Triple(null, null, emptyList())
                } else {
                    // Prefer a specific failed-patch error, else the last failed
                    // step's error (rebuild/sign), else a generic fallback.
                    // First line only — this is the short UI-banner summary; the
                    // full traces are already logged above.
                    engineResult.failedPatches.firstOrNull()?.let { fp ->
                        Triple("${fp.name}: ${fp.error.lineSequence().first()}", null, emptyList())
                    }
                        ?: engineResult.stepResults.lastOrNull { !it.success && it.error != null }
                            ?.let {
                                val stepDisplay = it.step.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                                val stepError = it.error ?: ""
                                Triple("Step $stepDisplay failed: $stepError", Res.string.error_step_failed, listOf(stepDisplay, stepError))
                            }
                        ?: Triple("Unknown patching error", Res.string.error_patching_unknown, emptyList())
                }
                Result.success(PatchResult(
                    success = engineResult.success,
                    outputPath = engineResult.outputPath,
                    appliedPatches = engineResult.appliedPatches,
                    failedPatches = engineResult.failedPatches.map { it.name },
                    failureReason = failureReason,
                    failureDetail = failureDetail,
                    packageName = engineResult.packageName,
                    packageVersion = engineResult.packageVersion,
                    failureReasonRes = failureReasonRes,
                    failureReasonArgs = failureReasonArgs,
                ))
            } finally {
                tempCopies.forEach { runCatching { it.delete() } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Catch Throwable, not just Exception: a mismatched patch bundle can
            // throw java.lang.Error (e.g. NoSuchMethodError when two sources ship
            // the same class compiled against different patcher versions). Those
            // are Errors, not Exceptions, so catch(Exception) would let them escape and the UI
            // would hang on "Loading patches" forever instead of surfacing a failure.
            Logger.error("Patching failed", e)
            Result.failure(e)
        }
    }

    /**
     * Convert a set of already-loaded library patches into GUI patches.
     * Used by EnabledSourcesLoader / MultiSourceLoader paths so we don't have to
     * re-open the .mpp file just to convert.
     */
    fun convertToGuiPatches(loaded: Set<LibraryPatch>): List<Patch> =
        loaded.map { it.toGuiPatch() }

    /**
     * Convert 2.3's typed Patcher model into the legacy Quick UI model.
     * The modern catalog owns compatibility and option interpretation, so
     * legacy-only Patcher 1.x fields cannot leak into this path.
     */
    private fun LibraryPatch.toGuiPatch(): Patch {
        val metadata = toMetadata()
        return Patch(
            name = metadata.name,
            description = metadata.description,
            compatiblePackages = metadata.compatiblePackages.map { pkg ->
                CompatiblePackage(
                    name = pkg.name, displayName = pkg.displayName,
                    versions = pkg.versions, experimentalVersions = pkg.experimentalVersions,
                    appIconColor = pkg.appIconColor, versionBuildCodes = pkg.versionBuildCodes,
                )
            },
            options = metadata.options.map { opt ->
                PatchOption(
                    key = opt.key, title = opt.title, description = opt.description,
                    type = PatchOptionType.valueOf(opt.type.name),
                    default = opt.default, required = opt.required,
                    valueType = opt.valueType,
                    explicitKind = opt.explicitKind?.let { ExplicitOptionKind.valueOf(it.name) },
                    allowedExtensions = opt.allowedExtensions,
                    recommendedSize = opt.recommendedSize?.let { ImageSize(it.width, it.height) },
                    sliderBounds = opt.sliderBounds?.let { SliderBounds(it.min, it.max, it.step) },
                    presets = opt.presets, rawDefault = opt.rawDefault,
                )
            },
            isEnabled = metadata.isEnabled,
            category = metadata.category,
        )
    }

    /**
     * Map Kotlin KType to GUI PatchOptionType.
     */
    private fun mapKTypeToOptionType(kType: KType, key: String, title: String): PatchOptionType {
        val typeName = kType.toString()
        return when {
            typeName.contains("Boolean") -> PatchOptionType.BOOLEAN
            typeName.contains("Int") -> PatchOptionType.INT
            typeName.contains("Long") -> PatchOptionType.LONG
            typeName.contains("Float") || typeName.contains("Double") -> PatchOptionType.FLOAT
            typeName.contains("List") || typeName.contains("Array") || typeName.contains("Set") -> PatchOptionType.LIST
            typeName.contains("File") || typeName.contains("Path") || typeName.contains("InputStream") -> PatchOptionType.FILE
            else -> {
                val combined = "$key $title".lowercase()
                val fileKeywords = listOf("icon", "image", "logo", "banner", "path", "file", "png", "jpg")
                if (fileKeywords.any { it in combined }) PatchOptionType.FILE else PatchOptionType.STRING
            }
        }
    }
}

/**
 * Result of a patching operation.
 */
data class PatchResult(
    val success: Boolean,
    val outputPath: String,
    val appliedPatches: List<String>,
    val failedPatches: List<String>,
    // Human-readable reason for [success == false]. Populated from the first
    // failed patch's error or — when patching succeeded but a later step
    // (rebuild, sign) blew up — that step's error. Null on success.
    val failureReason: String? = null,
    // Full failure detail: complete stack traces (incl. nested "Caused by:"
    // causes) for every failed patch and step. The expandable "Details"
    // counterpart to the one-line [failureReason]. Null on success.
    val failureDetail: String? = null,
    // Surfaced from the engine so callers (e.g. patched-app history) can record
    // what was actually patched. Empty when the patcher didn't report them.
    val packageName: String = "",
    val packageVersion: String = "",
    val failureReasonRes: StringResource? = null,
    val failureReasonArgs: List<Any> = emptyList(),
) {
    suspend fun getLocalizedFailureReason(): String? =
        failureReasonRes?.let { getString(it, *failureReasonArgs.toTypedArray()) } ?: failureReason
}

