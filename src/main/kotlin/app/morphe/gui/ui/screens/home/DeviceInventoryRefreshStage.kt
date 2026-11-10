package app.morphe.gui.ui.screens.home

/** Explicit state of a read-only inventory scan of one selected ADB serial. */
enum class DeviceInventoryRefreshStage {
    IDLE,
    READING_PACKAGES,
    READING_VERSIONS,
    VERIFYING_BUILDS,
    READING_CATALOG_APPS,
    FAILED;

    val inProgress: Boolean
        get() = this != IDLE && this != FAILED
}

/**
 * Never publish a result for a device that is no longer selected, even when
 * the initial package read was already successful.
 */
internal fun mayPublishDeviceScan(
    activeSerial: String?,
    scanSerial: String,
    refreshSerial: String?,
    stage: DeviceInventoryRefreshStage,
    requestGeneration: Long,
    currentGeneration: Long,
): Boolean =
    activeSerial == scanSerial &&
        refreshSerial == scanSerial &&
        stage.inProgress &&
        requestGeneration == currentGeneration


/** A late label batch may update presentation only for the same completed scan. */
internal fun mayPublishDeviceLabels(
    activeSerial: String?,
    indexedSerial: String,
    inventorySerial: String?,
    indexedPackages: Set<String>,
    inventoryPackages: Set<String>,
    requestGeneration: Long,
    currentGeneration: Long,
): Boolean = activeSerial == indexedSerial &&
    inventorySerial == indexedSerial &&
    indexedPackages == inventoryPackages &&
    requestGeneration == currentGeneration


/** Independent attempt guard: restarted label-only passes discard older batches. */
internal fun mayPublishDeviceLabelAttempt(
    activeSerial: String?,
    indexedSerial: String,
    inventorySerial: String?,
    indexedPackages: Set<String>,
    inventoryPackages: Set<String>,
    requestGeneration: Long,
    currentGeneration: Long,
    requestAttempt: Long,
    currentAttempt: Long,
): Boolean = requestAttempt == currentAttempt && mayPublishDeviceLabels(
    activeSerial, indexedSerial, inventorySerial, indexedPackages, inventoryPackages,
    requestGeneration, currentGeneration,
)
