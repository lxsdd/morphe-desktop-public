package app.morphe.gui.ui.screens.home

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.util.DeviceAppDiscoverySnapshot
import app.morphe.gui.util.DevicePatchSourceAvailability
import app.morphe.gui.util.DevicePatchability
import app.morphe.gui.util.DiscoveredDeviceApp
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.InstalledAppType
import app.morphe.gui.util.DeviceUpdateOwner
import app.morphe.gui.util.resolveInstalledOutputMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedHomeAppStateTest {
    @Test
    fun `exact local build installed requires no action`() {
        val state = resolveUnifiedHomeAppState(input(deviceInfo = deviceInfo(match = true)))

        assertEquals(HomeSelectedDeviceState.EXACT_LOCAL_BUILD_INSTALLED, state.selectedDevice.state)
        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
        assertFalse(state.requiresAction)
    }

    @Test
    fun `local build and app not installed installs local build`() {
        val state = resolveUnifiedHomeAppState(input(deviceInfo = DeviceAppInfo(false, null)))

        assertEquals(HomePrimaryAction.INSTALL_LOCAL_BUILD, state.primaryAction)
        assertTrue(state.requiresAction)
    }

    @Test
    fun `different installed build installs local build`() {
        val state = resolveUnifiedHomeAppState(input(deviceInfo = deviceInfo(match = false)))

        assertEquals(HomeSelectedDeviceState.DIFFERENT_BUILD_INSTALLED, state.selectedDevice.state)
        assertEquals(HomePrimaryAction.INSTALL_LOCAL_BUILD, state.primaryAction)
    }

    @Test
    fun `relevant stable patch update repatches compatible input`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            patchSources = listOf(source(HomePatchBundleChannel.STABLE, relevant = true)),
        ))

        assertEquals(HomePatchBundleStatus.RELEVANT_STABLE_PATCH_AVAILABLE, state.patchBundle.status)
        assertEquals(HomePrimaryAction.REPATCH_CURRENT_INPUT, state.primaryAction)
    }

    @Test
    fun `relevant dev patch update retains dev channel`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            patchSources = listOf(source(HomePatchBundleChannel.DEV, relevant = true)),
        ))

        assertEquals(HomePatchBundleStatus.RELEVANT_DEV_PATCH_AVAILABLE, state.patchBundle.status)
        assertEquals(HomePrimaryAction.REPATCH_CURRENT_INPUT, state.primaryAction)
    }

    @Test
    fun `irrelevant newer bundle is informational only`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            patchSources = listOf(source(HomePatchBundleChannel.DEV, relevant = false)),
        ))

        assertEquals(HomePatchBundleStatus.NEWER_RELEASE_BUT_NO_RELEVANT_APP_CHANGE, state.patchBundle.status)
        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
        assertFalse(state.requiresAction)
    }

    @Test
    fun `unsupported current apk requires newer apk`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            currentVersionSupported = false,
            latestStableVersion = "2",
        ))

        assertEquals(HomePrimaryAction.NEW_APK_REQUIRED, state.primaryAction)
    }

    @Test
    fun `unsupported apk and relevant patch require apk and repatch`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            currentVersionSupported = false,
            latestStableVersion = "2",
            patchSources = listOf(source(HomePatchBundleChannel.STABLE, relevant = true)),
        ))

        assertEquals(HomePrimaryAction.NEW_APK_AND_REPATCH_REQUIRED, state.primaryAction)
    }

    @Test
    fun `experimental offer is optional under stable policy`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            selectedAppChannel = HomeAppVersionChannel.STABLE,
            latestStableVersion = "1",
            latestExperimentalVersion = "2",
        ))

        assertTrue(state.appVersion.experimentalOfferAvailable)
        assertFalse(state.appVersion.selectedChannelUpdateAvailable)
        assertFalse(state.requiresAction)
    }

    @Test
    fun `stable offer does not force experimental policy to switch channel`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            selectedAppChannel = HomeAppVersionChannel.EXPERIMENTAL,
            latestStableVersion = "2",
            latestExperimentalVersion = "1",
        ))

        assertTrue(state.appVersion.stableOfferAvailable)
        assertFalse(state.appVersion.selectedChannelUpdateAvailable)
        assertFalse(state.requiresAction)
    }

    @Test
    fun `unknown app metadata fails conservatively`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            appMetadataKnown = false,
            currentVersionSupported = null,
        ))

        assertEquals(HomeAppVersionStatus.UNKNOWN, state.appVersion.status)
        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
    }

    @Test
    fun `unknown installed build does not claim installation is required`() {
        val state = resolveUnifiedHomeAppState(input(deviceInfo = deviceInfo(match = null)))

        assertEquals(HomeSelectedDeviceState.INSTALLED_STATE_UNKNOWN, state.selectedDevice.state)
        assertEquals(HomePrimaryAction.NONE, state.primaryAction)
        assertFalse(state.requiresAction)
    }

    @Test
    fun `patchable device app without local build can use installed input`() {
        val discovered = discovered("app.example", DevicePatchability.PATCHABLE)
        val state = resolveUnifiedHomeAppState(UnifiedHomeAppInput(
            originalPackageName = "app.example",
            installedPackageName = "app.example",
            installedVersion = "1",
            selectedDeviceSerial = "pixel-6a",
            discoveredDeviceApp = discovered,
        ))

        assertEquals(HomeLocalResultState.NO_LOCAL_BUILD, state.localResult.state)
        assertEquals(HomePrimaryAction.PATCH_CURRENT_DEVICE_APP, state.primaryAction)
        assertFalse(state.requiresAction)
    }

    @Test
    fun `renamed package maps device package back to original metadata identity`() {
        val record = record(original = "stock.app", installed = "clone.app")
        val index = buildUnifiedHomeAppStateIndex(
            records = listOf(record),
            patchedStates = mapOf("stock.app" to PatchedAppState.PATCHED),
            updateInfoByPackage = emptyMap(),
            deviceAppInfo = mapOf("stock.app" to deviceInfo(match = false)),
            supportedApps = listOf(supported("stock.app")),
            selectedDeviceSerial = "pixel-6a",
            deviceDiscovery = DeviceAppDiscoverySnapshot(
                deviceSerial = "pixel-6a",
                apps = listOf(discovered("clone.app", DevicePatchability.PATCHABLE)),
            ),
        )

        val state = index.forInstalledPackage("clone.app")
        assertEquals("stock.app", state?.originalPackageName)
        assertEquals("clone.app", state?.installedPackageName)
        assertEquals(HomePrimaryAction.INSTALL_LOCAL_BUILD, state?.primaryAction)
    }

    @Test
    fun `fresh hash proves exact build without receipt`() {
        val hash = "a".repeat(64)
        val match = resolveInstalledOutputMatch(hash, hash, null, "1", "time")
        val state = resolveUnifiedHomeAppState(input(deviceInfo = deviceInfo(match = match)))

        assertEquals(true, match)
        assertEquals(HomeSelectedDeviceState.EXACT_LOCAL_BUILD_INSTALLED, state.selectedDevice.state)
    }

    @Test
    fun `fresh differing hash overrides stale assumptions`() {
        val match = resolveInstalledOutputMatch("a".repeat(64), "b".repeat(64), null, "1", "time")
        val state = resolveUnifiedHomeAppState(input(deviceInfo = deviceInfo(match = match)))

        assertEquals(false, match)
        assertEquals(HomeSelectedDeviceState.DIFFERENT_BUILD_INSTALLED, state.selectedDevice.state)
    }

    @Test
    fun `no selected device leaves all non-device axes intact`() {
        val state = resolveUnifiedHomeAppState(input(
            selectedDeviceSerial = null,
            patchSources = listOf(source(HomePatchBundleChannel.DEV, relevant = true)),
        ))

        assertEquals(HomeSelectedDeviceState.NO_DEVICE, state.selectedDevice.state)
        assertEquals(HomePrimaryAction.REPATCH_CURRENT_INPUT, state.primaryAction)
    }

    private fun input(
        selectedDeviceSerial: String? = "pixel-6a",
        selectedAppChannel: HomeAppVersionChannel = HomeAppVersionChannel.STABLE,
        latestStableVersion: String? = "1",
        latestExperimentalVersion: String? = null,
        currentVersionSupported: Boolean? = true,
        appMetadataKnown: Boolean = true,
        patchSources: List<HomePatchSourceAxis> = emptyList(),
        deviceInfo: DeviceAppInfo? = null,
    ) = UnifiedHomeAppInput(
        originalPackageName = "app.example",
        installedPackageName = "app.example",
        patchedVersion = "1",
        installedVersion = deviceInfo?.installedVersion,
        selectedAppChannel = selectedAppChannel,
        latestStableVersion = latestStableVersion,
        latestExperimentalVersion = latestExperimentalVersion,
        currentVersionSupported = currentVersionSupported,
        appMetadataKnown = appMetadataKnown,
        patchSources = patchSources,
        patchedState = PatchedAppState.PATCHED,
        outputPath = "out.apk",
        selectedDeviceSerial = selectedDeviceSerial,
        deviceInfo = deviceInfo,
    )

    private fun source(channel: HomePatchBundleChannel, relevant: Boolean) = HomePatchSourceAxis(
        sourceId = "source",
        sourceName = "Source",
        channel = channel,
        usedVersion = "1",
        currentVersion = "2",
        latestAvailableVersion = "2",
        newerReleaseAvailable = true,
        hasRelevantAppChanges = relevant,
        metadataKnown = true,
    )

    private fun deviceInfo(match: Boolean?) = DeviceAppInfo(
        installed = true,
        installedVersion = "1",
        exactBuildVerified = match,
    )

    private fun discovered(packageName: String, patchability: DevicePatchability) = DiscoveredDeviceApp(
        packageName = packageName,
        displayName = packageName,
        versionCode = 1,
        versionName = "1",
        patchability = patchability,
        patchNames = listOf("Patch"),
        sourceNames = listOf("Source"),
        updateOwner = DeviceUpdateOwner.NoOwner,
        patchSourceAvailability = DevicePatchSourceAvailability.AVAILABLE,
        installedAppType = InstalledAppType.USER,
    )

    private fun record(original: String, installed: String) = PatchedAppRecord(
        packageName = original,
        currentPackageName = installed,
        displayName = "Example",
        apkVersion = "1",
        inputApkPath = "in.apk",
        outputApkPath = "out.apk",
        patchedAt = 1,
        patchedWithMorpheVersion = "test",
    )

    private fun supported(packageName: String) = SupportedApp(
        packageName = packageName,
        displayName = "Example",
        supportedVersions = listOf("1"),
        recommendedVersion = "1",
    )
}
