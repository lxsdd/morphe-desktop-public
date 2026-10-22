/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.AppSortPreference
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.util.DiscoveredDeviceApp
import app.morphe.gui.util.DevicePatchability
import app.morphe.gui.util.DevicePatchSourceAvailability
import app.morphe.gui.util.AppLabelProvenance
import app.morphe.gui.ui.screens.home.HomePrimaryAction
import app.morphe.gui.ui.screens.home.UnifiedHomeAppState
import java.util.Locale

internal enum class AppSortCriterion(val label: String) {
    NAME("Name"),
    PACKAGE("Package"),
    LAST_PATCHED("Last patched"),
    INSTALLED_VERSION("Installed version"),
    ACTION_PRIORITY("Action priority"),
}

internal enum class AppSortDirection {
    ASCENDING,
    DESCENDING,
}

internal data class AppSortState(
    val criterion: AppSortCriterion,
    val direction: AppSortDirection,
)

internal enum class DeviceAppsFilter {
    PATCHABLE,
    KNOWN,
    NO_PATCH_SOURCE,
    ALL,
}

internal enum class YourAppsFilter {
    ALL,
    ACTION_REQUIRED,
    APP_UPDATE,
    PATCH_UPDATE,
    INSTALL_READY,
}

internal fun availableSortCriteria(filter: AppListFilter): List<AppSortCriterion> = when (filter) {
    AppListFilter.ALL -> listOf(AppSortCriterion.NAME, AppSortCriterion.PACKAGE)
    AppListFilter.YOURS -> listOf(
        AppSortCriterion.NAME,
        AppSortCriterion.PACKAGE,
        AppSortCriterion.LAST_PATCHED,
        AppSortCriterion.ACTION_PRIORITY,
    )
    AppListFilter.DEVICE -> listOf(
        AppSortCriterion.NAME,
        AppSortCriterion.PACKAGE,
        AppSortCriterion.INSTALLED_VERSION,
    )
}

internal fun defaultSortState(@Suppress("UNUSED_PARAMETER") filter: AppListFilter): AppSortState =
    AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING)

/** One-way compatibility bridge for configs written by the former shared sort enum. */
internal fun migrateLegacyHomeSortPreference(value: String?): Map<String, AppSortPreference> {
    val yourState = when (value) {
        "NAME_DESC" -> AppSortState(AppSortCriterion.NAME, AppSortDirection.DESCENDING)
        "UPDATES_FIRST" -> AppSortState(AppSortCriterion.ACTION_PRIORITY, AppSortDirection.ASCENDING)
        "RECENTLY_PATCHED" -> AppSortState(AppSortCriterion.LAST_PATCHED, AppSortDirection.DESCENDING)
        else -> AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING)
    }
    val allState = when (value) {
        "NAME_DESC" -> AppSortState(AppSortCriterion.NAME, AppSortDirection.DESCENDING)
        else -> AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING)
    }
    return mapOf(
        AppListFilter.ALL.name to allState.toPreference(),
        AppListFilter.YOURS.name to yourState.toPreference(),
        AppListFilter.DEVICE.name to defaultSortState(AppListFilter.DEVICE).toPreference(),
    )
}

internal fun resolveSortState(
    filter: AppListFilter,
    preference: AppSortPreference?,
): AppSortState {
    val criterion = preference?.criterion
        ?.let { runCatching { AppSortCriterion.valueOf(it) }.getOrNull() }
        ?.takeIf { it in availableSortCriteria(filter) }
        ?: return defaultSortState(filter)
    val direction = preference.direction
        .let { runCatching { AppSortDirection.valueOf(it) }.getOrNull() }
        ?: return defaultSortState(filter)
    return AppSortState(criterion, direction)
}

internal fun AppSortState.toPreference(): AppSortPreference = AppSortPreference(
    criterion = criterion.name,
    direction = direction.name,
)

internal fun sortSupportedApps(apps: List<SupportedApp>, state: AppSortState): List<SupportedApp> =
    apps.sortedFor(state) {
        when (state.criterion) {
            AppSortCriterion.PACKAGE -> normalized(it.packageName)
            else -> normalized(it.displayName)
        }
    }

internal fun sortPatchedApps(
    apps: List<PatchedAppRecord>,
    state: AppSortState,
    unifiedStates: Map<String, UnifiedHomeAppState> = emptyMap(),
): List<PatchedAppRecord> =
    when (state.criterion) {
        AppSortCriterion.LAST_PATCHED -> apps.sortedFor(state) { it.patchedAt }
        AppSortCriterion.ACTION_PRIORITY -> apps.sortedWithDirection(
            state,
            compareBy<PatchedAppRecord> { actionPriority(unifiedStates[it.packageName]) }
                .thenBy { normalized(it.displayName) }
                .thenBy { normalized(it.packageName) },
        )
        AppSortCriterion.PACKAGE -> apps.sortedFor(state) { normalized(it.packageName) }
        else -> apps.sortedFor(state) { normalized(it.displayName) }
    }

private fun actionPriority(state: UnifiedHomeAppState?): Int = when (state?.primaryAction) {
    HomePrimaryAction.INSTALL_LOCAL_BUILD -> 0
    HomePrimaryAction.NEW_APK_AND_REPATCH_REQUIRED -> 1
    HomePrimaryAction.NEW_APK_REQUIRED -> 2
    HomePrimaryAction.REPATCH_CURRENT_INPUT -> 3
    HomePrimaryAction.PATCH_CURRENT_DEVICE_APP -> 4
    HomePrimaryAction.NONE -> if (state.optionalOfferAvailable) 5 else 6
    null -> 7
}

internal fun filterPatchedApps(
    apps: List<PatchedAppRecord>,
    filter: YourAppsFilter,
    unifiedStates: Map<String, UnifiedHomeAppState>,
    searchQuery: String,
): List<PatchedAppRecord> = apps.filter { app ->
    val state = unifiedStates[app.packageName]
    val matchesFilter = when (filter) {
        YourAppsFilter.ALL -> true
        YourAppsFilter.ACTION_REQUIRED -> state?.requiresAction == true
        YourAppsFilter.APP_UPDATE -> state?.appUpdateAvailable == true
        YourAppsFilter.PATCH_UPDATE -> state?.relevantPatchUpdate == true
        YourAppsFilter.INSTALL_READY -> state?.installReady == true
    }
    matchesFilter && (
        searchQuery.isBlank() ||
            app.displayName.contains(searchQuery, ignoreCase = true) ||
            app.packageName.contains(searchQuery, ignoreCase = true)
        )
}

internal fun sortDeviceApps(apps: List<DiscoveredDeviceApp>, state: AppSortState): List<DiscoveredDeviceApp> =
    when (state.criterion) {
        AppSortCriterion.INSTALLED_VERSION -> apps.sortedWithDirection(state,
            compareBy<DiscoveredDeviceApp> { it.versionCode ?: Long.MIN_VALUE }
                .thenBy { normalized(it.packageName) },
        )
        AppSortCriterion.PACKAGE -> apps.sortedFor(state) { normalized(it.packageName) }
        else -> apps.sortedWithDirection(state,
            compareBy<DiscoveredDeviceApp> { normalized(it.trustworthyLabelOrPackage()) }
                .thenBy { normalized(it.packageName) },
        )
    }

internal fun filterDeviceApps(
    apps: List<DiscoveredDeviceApp>,
    filter: DeviceAppsFilter,
    searchQuery: String,
): List<DiscoveredDeviceApp> = apps.filter { app ->
    val matchesFilter = when (filter) {
        DeviceAppsFilter.PATCHABLE -> app.patchability == DevicePatchability.PATCHABLE
        DeviceAppsFilter.KNOWN ->
            app.patchSourceAvailability == DevicePatchSourceAvailability.AVAILABLE
        DeviceAppsFilter.NO_PATCH_SOURCE ->
            app.patchSourceAvailability == DevicePatchSourceAvailability.NONE
        DeviceAppsFilter.ALL -> true
    }
    matchesFilter && (
        searchQuery.isBlank() ||
            (app.hasTrustworthyLabel() && app.displayName.contains(searchQuery, ignoreCase = true)) ||
            app.packageName.contains(searchQuery, ignoreCase = true)
        )
}

internal fun deviceAppsTotalCount(apps: List<DiscoveredDeviceApp>): Int = apps.size

private fun normalized(value: String): String = value.lowercase(Locale.ROOT)

internal fun DiscoveredDeviceApp.hasTrustworthyLabel(): Boolean = labelProvenance in setOf(
    AppLabelProvenance.TRUSTED_PRODUCT_METADATA,
    AppLabelProvenance.ANDROID_LITERAL,
    AppLabelProvenance.ANDROID_RESOURCE,
    AppLabelProvenance.CACHE_OF_ANDROID_RESOURCE,
)

private fun DiscoveredDeviceApp.trustworthyLabelOrPackage(): String =
    displayName.takeIf { hasTrustworthyLabel() } ?: packageName

private fun <T, K : Comparable<K>> List<T>.sortedFor(
    state: AppSortState,
    selector: (T) -> K,
): List<T> {
    val comparator = compareBy<T>(selector)
    return if (state.direction == AppSortDirection.ASCENDING) sortedWith(comparator)
    else sortedWith(comparator.reversed())
}

private fun <T> List<T>.sortedWithDirection(state: AppSortState, comparator: Comparator<T>): List<T> =
    sortedWith(if (state.direction == AppSortDirection.ASCENDING) comparator else comparator.reversed())
