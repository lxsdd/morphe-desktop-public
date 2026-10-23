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
