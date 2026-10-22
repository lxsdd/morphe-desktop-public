/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.util.DeviceAppDiscoverySnapshot
import app.morphe.gui.util.DevicePatchability
import app.morphe.gui.util.DiscoveredDeviceApp
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.InstalledPatchStatus
import app.morphe.gui.util.UpdateMetadataResolution
import app.morphe.gui.util.compareVersionStrings

enum class HomeAppVersionChannel { STABLE, EXPERIMENTAL, UNKNOWN }

enum class HomePatchBundleChannel { STABLE, DEV, LOCAL, UNKNOWN }

enum class HomeAppVersionStatus {
    CURRENT,
    OPTIONAL_UPDATE_AVAILABLE,
    NEW_APK_REQUIRED,
    UNKNOWN,
}

data class HomeAppVersionAxis(
    val patchedVersion: String?,
    val installedVersion: String?,
    val selectedChannel: HomeAppVersionChannel,
    val latestStableVersion: String?,
    val latestExperimentalVersion: String?,
    val currentVersionSupported: Boolean?,
    val selectedChannelUpdateAvailable: Boolean,
    val stableOfferAvailable: Boolean,
    val experimentalOfferAvailable: Boolean,
    val newApkRequired: Boolean,
    val status: HomeAppVersionStatus,
)

data class HomePatchSourceAxis(
    val sourceId: String,
    val sourceName: String,
    val channel: HomePatchBundleChannel,
    val usedVersion: String,
    val currentVersion: String?,
    val latestAvailableVersion: String?,
    val newerReleaseAvailable: Boolean,
    val hasRelevantAppChanges: Boolean,
    val metadataKnown: Boolean,
)

enum class HomePatchBundleStatus {
    CURRENT,
    RELEVANT_STABLE_PATCH_AVAILABLE,
    RELEVANT_DEV_PATCH_AVAILABLE,
    RELEVANT_STABLE_AND_DEV_PATCH_AVAILABLE,
    RELEVANT_LOCAL_PATCH_AVAILABLE,
    NEWER_RELEASE_BUT_NO_RELEVANT_APP_CHANGE,
    UNKNOWN,
}

data class HomePatchBundleAxis(
    val sources: List<HomePatchSourceAxis>,
    val status: HomePatchBundleStatus,
) {
    val relevantUpdateAvailable: Boolean get() = status in setOf(
        HomePatchBundleStatus.RELEVANT_STABLE_PATCH_AVAILABLE,
        HomePatchBundleStatus.RELEVANT_DEV_PATCH_AVAILABLE,
        HomePatchBundleStatus.RELEVANT_STABLE_AND_DEV_PATCH_AVAILABLE,
        HomePatchBundleStatus.RELEVANT_LOCAL_PATCH_AVAILABLE,
    )
}

enum class HomeLocalResultState {
    NO_LOCAL_BUILD,
    LOCAL_BUILD_READY,
    REPATCH_REQUIRED,
    OUTPUT_MISSING,
    MODIFIED_EXTERNALLY,
    UNKNOWN,
}

data class HomeLocalResultAxis(
    val state: HomeLocalResultState,
    val outputPath: String? = null,
)

enum class HomeSelectedDeviceState {
    NO_DEVICE,
    NOT_INSTALLED,
    EXACT_LOCAL_BUILD_INSTALLED,
    DIFFERENT_BUILD_INSTALLED,
    INSTALLED_STATE_UNKNOWN,
}

data class HomeSelectedDeviceAxis(
    val state: HomeSelectedDeviceState,
    val serial: String?,
    val installedPackageName: String,
    val installedVersion: String?,
    val installedPatchStatus: InstalledPatchStatus?,
)

enum class HomePrimaryAction {
    NONE,
    INSTALL_LOCAL_BUILD,
    REPATCH_CURRENT_INPUT,
    NEW_APK_REQUIRED,
    NEW_APK_AND_REPATCH_REQUIRED,
    PATCH_CURRENT_DEVICE_APP,
}

data class UnifiedHomeAppState(
    val originalPackageName: String,
    val installedPackageName: String,
    val appVersion: HomeAppVersionAxis,
    val patchBundle: HomePatchBundleAxis,
    val localResult: HomeLocalResultAxis,
    val selectedDevice: HomeSelectedDeviceAxis,
    val primaryAction: HomePrimaryAction,
) {
    val requiresAction: Boolean get() = primaryAction in setOf(
        HomePrimaryAction.INSTALL_LOCAL_BUILD,
        HomePrimaryAction.REPATCH_CURRENT_INPUT,
        HomePrimaryAction.NEW_APK_REQUIRED,
        HomePrimaryAction.NEW_APK_AND_REPATCH_REQUIRED,
    )
    val installReady: Boolean get() = primaryAction == HomePrimaryAction.INSTALL_LOCAL_BUILD
    val relevantPatchUpdate: Boolean get() = patchBundle.relevantUpdateAvailable
    val appUpdateAvailable: Boolean get() =
        appVersion.newApkRequired || appVersion.selectedChannelUpdateAvailable
    val optionalOfferAvailable: Boolean get() =
        !appVersion.newApkRequired &&
            (appVersion.stableOfferAvailable || appVersion.experimentalOfferAvailable)
}

data class UnifiedHomeAppStateIndex(
    val byOriginalPackage: Map<String, UnifiedHomeAppState>,
    val originalPackageByInstalledPackage: Map<String, String>,
) {
    fun forOriginalPackage(packageName: String): UnifiedHomeAppState? = byOriginalPackage[packageName]

    fun forInstalledPackage(packageName: String): UnifiedHomeAppState? =
        originalPackageByInstalledPackage[packageName]?.let(byOriginalPackage::get)
            ?: byOriginalPackage[packageName]
}

data class UnifiedHomeAppInput(
    val originalPackageName: String,
    val installedPackageName: String = originalPackageName,
    val patchedVersion: String? = null,
    val installedVersion: String? = null,
    val selectedAppChannel: HomeAppVersionChannel = HomeAppVersionChannel.UNKNOWN,
    val latestStableVersion: String? = null,
    val latestExperimentalVersion: String? = null,
    val currentVersionSupported: Boolean? = null,
    val appMetadataKnown: Boolean = false,
    val patchSources: List<HomePatchSourceAxis> = emptyList(),
    val patchedState: PatchedAppState? = null,
    val outputPath: String? = null,
    val selectedDeviceSerial: String? = null,
    val deviceInfo: DeviceAppInfo? = null,
    val discoveredDeviceApp: DiscoveredDeviceApp? = null,
)

fun resolveUnifiedHomeAppState(input: UnifiedHomeAppInput): UnifiedHomeAppState {
    val currentVersion = input.patchedVersion ?: input.installedVersion
    val stableOffer = isNewer(input.latestStableVersion, currentVersion)
    val experimentalOffer = isNewer(input.latestExperimentalVersion, currentVersion)
    val selectedOffer = when (input.selectedAppChannel) {
        HomeAppVersionChannel.STABLE -> stableOffer
        HomeAppVersionChannel.EXPERIMENTAL -> experimentalOffer
        HomeAppVersionChannel.UNKNOWN -> false
    }
    val newApkRequired = input.appMetadataKnown && input.currentVersionSupported == false
    val appStatus = when {
        !input.appMetadataKnown -> HomeAppVersionStatus.UNKNOWN
        newApkRequired -> HomeAppVersionStatus.NEW_APK_REQUIRED
        selectedOffer -> HomeAppVersionStatus.OPTIONAL_UPDATE_AVAILABLE
        else -> HomeAppVersionStatus.CURRENT
    }
    val appAxis = HomeAppVersionAxis(
        patchedVersion = input.patchedVersion,
        installedVersion = input.installedVersion,
        selectedChannel = input.selectedAppChannel,
        latestStableVersion = input.latestStableVersion,
        latestExperimentalVersion = input.latestExperimentalVersion,
        currentVersionSupported = input.currentVersionSupported,
        selectedChannelUpdateAvailable = selectedOffer,
        stableOfferAvailable = stableOffer,
        experimentalOfferAvailable = experimentalOffer,
        newApkRequired = newApkRequired,
        status = appStatus,
    )

    val relevantStable = input.patchSources.any {
        it.newerReleaseAvailable && it.hasRelevantAppChanges && it.channel == HomePatchBundleChannel.STABLE
    }
    val relevantDev = input.patchSources.any {
        it.newerReleaseAvailable && it.hasRelevantAppChanges && it.channel == HomePatchBundleChannel.DEV
    }
    val anyRelevant = input.patchSources.any { it.newerReleaseAvailable && it.hasRelevantAppChanges }
    val anyIrrelevantNewer = input.patchSources.any { it.newerReleaseAvailable && !it.hasRelevantAppChanges }
    val anyUnknown = input.patchSources.any { !it.metadataKnown }
    val patchStatus = when {
        relevantStable && relevantDev -> HomePatchBundleStatus.RELEVANT_STABLE_AND_DEV_PATCH_AVAILABLE
        relevantStable -> HomePatchBundleStatus.RELEVANT_STABLE_PATCH_AVAILABLE
        relevantDev -> HomePatchBundleStatus.RELEVANT_DEV_PATCH_AVAILABLE
        anyRelevant && input.patchSources.any {
            it.newerReleaseAvailable && it.hasRelevantAppChanges && it.channel == HomePatchBundleChannel.LOCAL
        } -> HomePatchBundleStatus.RELEVANT_LOCAL_PATCH_AVAILABLE
        anyRelevant -> HomePatchBundleStatus.UNKNOWN
        anyIrrelevantNewer -> HomePatchBundleStatus.NEWER_RELEASE_BUT_NO_RELEVANT_APP_CHANGE
        anyUnknown -> HomePatchBundleStatus.UNKNOWN
        else -> HomePatchBundleStatus.CURRENT
    }
    val patchAxis = HomePatchBundleAxis(input.patchSources, patchStatus)

    val localState = when (input.patchedState) {
        null, PatchedAppState.NEVER_PATCHED -> HomeLocalResultState.NO_LOCAL_BUILD
        PatchedAppState.APK_MISSING -> HomeLocalResultState.OUTPUT_MISSING
        PatchedAppState.MODIFIED_EXTERNALLY -> HomeLocalResultState.MODIFIED_EXTERNALLY
        PatchedAppState.PATCHED,
        PatchedAppState.PATCHED_WITH_UPDATES -> if (patchAxis.relevantUpdateAvailable) {
            HomeLocalResultState.REPATCH_REQUIRED
        } else {
            HomeLocalResultState.LOCAL_BUILD_READY
        }
    }
    val localAxis = HomeLocalResultAxis(localState, input.outputPath)

    val deviceState = when {
        input.selectedDeviceSerial == null -> HomeSelectedDeviceState.NO_DEVICE
        input.deviceInfo == null -> HomeSelectedDeviceState.INSTALLED_STATE_UNKNOWN
        !input.deviceInfo.installed -> HomeSelectedDeviceState.NOT_INSTALLED
        input.deviceInfo.installedOutputMatchesCurrent == true ->
            HomeSelectedDeviceState.EXACT_LOCAL_BUILD_INSTALLED
        input.deviceInfo.installedOutputMatchesCurrent == false ->
            HomeSelectedDeviceState.DIFFERENT_BUILD_INSTALLED
        else -> HomeSelectedDeviceState.INSTALLED_STATE_UNKNOWN
    }
    val deviceAxis = HomeSelectedDeviceAxis(
        state = deviceState,
        serial = input.selectedDeviceSerial,
        installedPackageName = input.installedPackageName,
        installedVersion = input.deviceInfo?.installedVersion ?: input.installedVersion,
        installedPatchStatus = input.deviceInfo?.installedPatchStatus,
    )

    val installableLocal = localState == HomeLocalResultState.LOCAL_BUILD_READY
    val deviceNeedsLocal = deviceState in setOf(
        HomeSelectedDeviceState.NOT_INSTALLED,
        HomeSelectedDeviceState.DIFFERENT_BUILD_INSTALLED,
    )
    val patchableDeviceInput = input.discoveredDeviceApp?.patchability == DevicePatchability.PATCHABLE
    val primaryAction = when {
        installableLocal && deviceNeedsLocal -> HomePrimaryAction.INSTALL_LOCAL_BUILD
        newApkRequired && patchAxis.relevantUpdateAvailable -> HomePrimaryAction.NEW_APK_AND_REPATCH_REQUIRED
        newApkRequired -> HomePrimaryAction.NEW_APK_REQUIRED
        patchAxis.relevantUpdateAvailable -> HomePrimaryAction.REPATCH_CURRENT_INPUT
        localState in setOf(HomeLocalResultState.OUTPUT_MISSING, HomeLocalResultState.MODIFIED_EXTERNALLY) ->
            HomePrimaryAction.REPATCH_CURRENT_INPUT
        localState == HomeLocalResultState.NO_LOCAL_BUILD && patchableDeviceInput ->
            HomePrimaryAction.PATCH_CURRENT_DEVICE_APP
        else -> HomePrimaryAction.NONE
    }

    return UnifiedHomeAppState(
        originalPackageName = input.originalPackageName,
        installedPackageName = input.installedPackageName,
        appVersion = appAxis,
        patchBundle = patchAxis,
        localResult = localAxis,
        selectedDevice = deviceAxis,
        primaryAction = primaryAction,
    )
}

fun buildUnifiedHomeAppStateIndex(
    records: List<PatchedAppRecord>,
    patchedStates: Map<String, PatchedAppState>,
    updateInfoByPackage: Map<String, RecallUpdateInfo>,
    deviceAppInfo: Map<String, DeviceAppInfo>,
    supportedApps: List<SupportedApp>,
    selectedDeviceSerial: String?,
    deviceDiscovery: DeviceAppDiscoverySnapshot?,
): UnifiedHomeAppStateIndex {
    val originalByInstalled = records.associate { it.installedPackageName to it.packageName }
    val discoveredByInstalled = deviceDiscovery?.apps.orEmpty().associateBy { it.packageName }
    val supportedByPackage = supportedApps.associateBy { it.packageName }
    val states = linkedMapOf<String, UnifiedHomeAppState>()

    records.forEach { record ->
        val update = updateInfoByPackage[record.packageName]
        val supported = supportedByPackage[record.packageName]
        states[record.packageName] = resolveUnifiedHomeAppState(
            UnifiedHomeAppInput(
                originalPackageName = record.packageName,
                installedPackageName = record.installedPackageName,
                patchedVersion = record.apkVersion,
                installedVersion = deviceAppInfo[record.packageName]?.installedVersion,
                selectedAppChannel = update?.appChannel.toHomeChannel(),
                latestStableVersion = update?.latestStableVersion ?: supported?.recommendedVersion,
                latestExperimentalVersion = update?.latestExperimentalVersion
                    ?: supported?.experimentalVersions?.firstOrNull(),
                currentVersionSupported = update?.appUsedSupported,
                appMetadataKnown = update?.metadataResolution == UpdateMetadataResolution.READY,
                patchSources = update?.sources.orEmpty().map { it.toHomePatchSourceAxis() },
                patchedState = patchedStates[record.packageName],
                outputPath = record.outputApkPath,
                selectedDeviceSerial = selectedDeviceSerial,
                deviceInfo = deviceAppInfo[record.packageName],
                discoveredDeviceApp = discoveredByInstalled[record.installedPackageName],
            )
        )
    }

    deviceDiscovery?.apps.orEmpty().forEach { discovered ->
        val original = originalByInstalled[discovered.packageName] ?: discovered.packageName
        if (original in states) return@forEach
        val supported = supportedByPackage[original]
        states[original] = resolveUnifiedHomeAppState(
            UnifiedHomeAppInput(
                originalPackageName = original,
                installedPackageName = discovered.packageName,
                installedVersion = discovered.versionName,
                latestStableVersion = supported?.recommendedVersion,
                latestExperimentalVersion = supported?.experimentalVersions?.firstOrNull(),
                currentVersionSupported = when (discovered.patchability) {
                    DevicePatchability.PATCHABLE -> true
                    DevicePatchability.INCOMPATIBLE_VERSION -> false
                    else -> null
                },
                appMetadataKnown = supported != null,
                selectedDeviceSerial = selectedDeviceSerial,
                discoveredDeviceApp = discovered,
            )
        )
    }

    return UnifiedHomeAppStateIndex(states, originalByInstalled)
}

private fun RecallUpdateInfo.AppChannel?.toHomeChannel(): HomeAppVersionChannel = when (this) {
    RecallUpdateInfo.AppChannel.STABLE -> HomeAppVersionChannel.STABLE
    RecallUpdateInfo.AppChannel.EXPERIMENTAL -> HomeAppVersionChannel.EXPERIMENTAL
    else -> HomeAppVersionChannel.UNKNOWN
}

private fun RecallUpdateInfo.SourceUpdate.toHomePatchSourceAxis(): HomePatchSourceAxis = HomePatchSourceAxis(
    sourceId = sourceId,
    sourceName = name,
    channel = channel.toHomePatchChannel(),
    usedVersion = usedVersion,
    currentVersion = resolvedVersion,
    latestAvailableVersion = latestAvailableVersion,
    newerReleaseAvailable = outdated,
    hasRelevantAppChanges = hasRelevantChanges,
    metadataKnown = metadataKnown,
)

private fun EnabledSourcesLoader.Channel.toHomePatchChannel(): HomePatchBundleChannel = when (this) {
    EnabledSourcesLoader.Channel.STABLE_LATEST,
    EnabledSourcesLoader.Channel.STABLE_OLDER -> HomePatchBundleChannel.STABLE
    EnabledSourcesLoader.Channel.DEV_LATEST,
    EnabledSourcesLoader.Channel.DEV_OLDER -> HomePatchBundleChannel.DEV
    EnabledSourcesLoader.Channel.LOCAL -> HomePatchBundleChannel.LOCAL
    EnabledSourcesLoader.Channel.UNKNOWN -> HomePatchBundleChannel.UNKNOWN
}

private fun isNewer(candidate: String?, current: String?): Boolean =
    !candidate.isNullOrBlank() && !current.isNullOrBlank() &&
        compareVersionStrings(candidate.removePrefix("v"), current.removePrefix("v")) > 0
