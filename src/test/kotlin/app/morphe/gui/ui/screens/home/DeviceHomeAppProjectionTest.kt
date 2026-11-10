/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import app.morphe.gui.util.DevicePatchability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceHomeAppProjectionTest {
    @Test
    fun `stock patchable app exposes patch input action`() {
        val projection = projectDeviceHomeApp(DevicePatchability.PATCHABLE, null)
        assertEquals(DeviceMorpheActionStatus.PATCHABLE_INSTALLED_APP, projection.actionStatus)
        assertTrue(projection.showUseInstalledAppAction)
        assertFalse(projection.showInstallAction)
    }

    @Test
    fun `unsupported device-only app reports no patch source`() {
        val projection = projectDeviceHomeApp(
            DevicePatchability.NO_PATCH_SOURCE,
            state(
                action = HomePrimaryAction.NONE,
                localState = HomeLocalResultState.NO_LOCAL_BUILD,
            ),
        )
        assertEquals(DeviceMorpheActionStatus.NO_PATCH_SOURCE, projection.actionStatus)
    }

    @Test
    fun `local ready build exposes install action`() = assertProjection(
        HomePrimaryAction.INSTALL_LOCAL_BUILD,
        DeviceMorpheActionStatus.INSTALLATION_REQUIRED,
        install = true,
    )

    @Test
    fun `exact installed local build is explicit`() {
        val projection = projectDeviceHomeApp(
            DevicePatchability.PATCHABLE,
            state(HomePrimaryAction.NONE, HomeSelectedDeviceState.EXACT_LOCAL_BUILD_INSTALLED),
        )
        assertEquals(DeviceMorpheActionStatus.EXACT_BUILD_INSTALLED, projection.actionStatus)
        assertFalse(projection.showInstallAction)
    }

    @Test
    fun `different installed build with ready local result requires install`() = assertProjection(
        HomePrimaryAction.INSTALL_LOCAL_BUILD,
        DeviceMorpheActionStatus.INSTALLATION_REQUIRED,
        install = true,
    )

    @Test
    fun `repatch and newer APK requirements stay distinct`() {
        assertProjection(HomePrimaryAction.REPATCH_CURRENT_INPUT, DeviceMorpheActionStatus.REPATCH_REQUIRED)
        assertProjection(HomePrimaryAction.NEW_APK_REQUIRED, DeviceMorpheActionStatus.NEW_APK_REQUIRED)
    }

    @Test
    fun `renamed installed package resolves through central index`() {
        val state = state(HomePrimaryAction.NONE, installedPackageName = "clone.example")
        val index = UnifiedHomeAppStateIndex(
            byOriginalPackage = mapOf("original.example" to state),
            originalPackageByInstalledPackage = mapOf("clone.example" to "original.example"),
        )
        assertEquals(state, index.forInstalledPackage("clone.example"))
    }

    @Test
    fun `unknown state remains fail closed`() {
        val projection = projectDeviceHomeApp(DevicePatchability.VERSION_NOT_CONFIRMED, null)
        assertEquals(DeviceMorpheActionStatus.UNKNOWN, projection.actionStatus)
        assertFalse(projection.showInstallAction)
        assertFalse(projection.showUseInstalledAppAction)
    }

    private fun assertProjection(
        action: HomePrimaryAction,
        expected: DeviceMorpheActionStatus,
        install: Boolean = false,
    ) {
        val projection = projectDeviceHomeApp(DevicePatchability.PATCHABLE, state(action))
        assertEquals(expected, projection.actionStatus)
        assertEquals(install, projection.showInstallAction)
    }

    private fun state(
        action: HomePrimaryAction,
        deviceState: HomeSelectedDeviceState = HomeSelectedDeviceState.DIFFERENT_BUILD_INSTALLED,
        installedPackageName: String = "example.app",
        localState: HomeLocalResultState = HomeLocalResultState.LOCAL_BUILD_READY,
    ) = UnifiedHomeAppState(
        originalPackageName = "original.example",
        installedPackageName = installedPackageName,
        appVersion = HomeAppVersionAxis(null, null, HomeAppVersionChannel.UNKNOWN, null, null, null, false, false, false, false, HomeAppVersionStatus.UNKNOWN),
        patchBundle = HomePatchBundleAxis(emptyList(), HomePatchBundleStatus.CURRENT),
        localResult = HomeLocalResultAxis(localState),
        selectedDevice = HomeSelectedDeviceAxis(deviceState, "pixel-6a", installedPackageName, "1", null),
        primaryAction = action,
    )
}
