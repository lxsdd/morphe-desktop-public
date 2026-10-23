package app.morphe.gui.ui.screens.home.components

/** Local UI ordering only. Does not affect any device query or device state. */
internal enum class DeviceInventorySort { STATUS_FIRST, STATUS_LAST, NAME_ASC, NAME_DESC }

internal fun <T> sortDeviceInventory(
    rows: List<T>,
    mode: DeviceInventorySort,
    status: (T) -> DeviceOverviewStatus,
    name: (T) -> String,
    packageName: (T) -> String,
): List<T> {
    val names = compareBy<T>({ name(it).lowercase() }, { packageName(it) })
    val order = when (mode) {
        DeviceInventorySort.STATUS_FIRST ->
            compareBy<T> { deviceOverviewPriority(status(it)) }.then(names)
        DeviceInventorySort.STATUS_LAST ->
            compareByDescending<T> { deviceOverviewPriority(status(it)) }.then(names)
        DeviceInventorySort.NAME_ASC -> names
        DeviceInventorySort.NAME_DESC ->
            compareByDescending<T> { name(it).lowercase() }.thenBy { packageName(it) }
    }
    return rows.sortedWith(order)
}
