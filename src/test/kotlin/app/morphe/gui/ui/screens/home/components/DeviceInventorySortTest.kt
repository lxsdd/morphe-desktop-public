package app.morphe.gui.ui.screens.home.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceInventorySortTest {
    private data class Row(val name: String, val pkg: String, val status: DeviceOverviewStatus)
    private val example = listOf(
        Row("YouTube", "com.google.android.youtube", DeviceOverviewStatus.APP_UPDATE),
        Row("Amazon", "com.amazon", DeviceOverviewStatus.EXACT_BUILD_VERIFIED),
        Row("YouTube Music", "com.google.android.music", DeviceOverviewStatus.PATCH_CANDIDATE),
        Row("Reddit", "com.reddit", DeviceOverviewStatus.PATCH_UPDATE),
    )

    private fun sorted(mode: DeviceInventorySort) =
        sortDeviceInventory(example, mode, { it.status }, { it.name }, { it.pkg })
            .map { it.name }

    @Test fun `urgent statuses appear first by default`() {
        assertEquals(listOf("YouTube", "Reddit", "YouTube Music", "Amazon"),
            sorted(DeviceInventorySort.STATUS_FIRST))
    }

    @Test fun `status order may be inverted without mixing app names`() {
        assertEquals(listOf("Amazon", "YouTube Music", "Reddit", "YouTube"),
            sorted(DeviceInventorySort.STATUS_LAST))
    }

    @Test fun `app name sort is independent of status`() {
        assertEquals(listOf("Amazon", "Reddit", "YouTube", "YouTube Music"),
            sorted(DeviceInventorySort.NAME_ASC))
        assertEquals(listOf("YouTube Music", "YouTube", "Reddit", "Amazon"),
            sorted(DeviceInventorySort.NAME_DESC))
    }
}
