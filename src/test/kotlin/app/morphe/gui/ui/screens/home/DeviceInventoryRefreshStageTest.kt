package app.morphe.gui.ui.screens.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceInventoryRefreshStageTest {
    @Test fun `loading indicators remain active through partial inventory enrichment`() {
        assertTrue(DeviceInventoryRefreshStage.READING_PACKAGES.inProgress)
        assertTrue(DeviceInventoryRefreshStage.READING_VERSIONS.inProgress)
        assertTrue(DeviceInventoryRefreshStage.VERIFYING_BUILDS.inProgress)
        assertTrue(DeviceInventoryRefreshStage.READING_CATALOG_APPS.inProgress)
        assertFalse(DeviceInventoryRefreshStage.IDLE.inProgress)
        assertFalse(DeviceInventoryRefreshStage.FAILED.inProgress)
    }

    @Test fun `switching from Pixel 6a to Pixel 9 discards stale scan updates`() {
        val phase = DeviceInventoryRefreshStage.READING_VERSIONS
        assertTrue(mayPublishDeviceScan("PIXEL6A", "PIXEL6A", "PIXEL6A", phase, 4, 4))
        assertFalse(mayPublishDeviceScan("PIXEL9", "PIXEL6A", "PIXEL6A", phase, 4, 4))
        assertFalse(mayPublishDeviceScan("PIXEL9", "PIXEL9", "PIXEL6A", phase, 4, 4))
        assertFalse(mayPublishDeviceScan("PIXEL9", "PIXEL9", "PIXEL9", phase, 3, 4))
    }


    @Test fun `late labels are accepted only for exact selected completed scan`() {
        val installed = setOf("com.youtube", "app.clone")
        assertTrue(mayPublishDeviceLabels("pixel6a", "pixel6a", "pixel6a",
            installed, installed, 8, 8))
        assertFalse(mayPublishDeviceLabels("pixel9", "pixel6a", "pixel6a",
            installed, installed, 8, 8))
        assertFalse(mayPublishDeviceLabels("pixel9", "pixel9", "pixel6a",
            installed, installed, 8, 8))
        assertFalse(mayPublishDeviceLabels("pixel6a", "pixel6a", "pixel6a",
            installed, installed, 7, 8))
        assertFalse(mayPublishDeviceLabels("pixel6a", "pixel6a", "pixel6a",
            installed, setOf("com.youtube"), 8, 8))
        assertFalse(mayPublishDeviceLabels("pixel6a", "pixel6a", null,
            installed, installed, 8, 8))
    }


    @Test fun `label-only retries reject stale batches from earlier attempts`() {
        val installed = setOf("app.a", "app.b")
        assertTrue(mayPublishDeviceLabelAttempt("A", "A", "A",
            installed, installed, 1, 1, 2, 2))
        assertFalse(mayPublishDeviceLabelAttempt("A", "A", "A",
            installed, installed, 1, 1, 1, 2))
        assertFalse(mayPublishDeviceLabelAttempt("B", "A", "A",
            installed, installed, 1, 1, 2, 2))
    }

    @Test fun `completed and failed scans reject late publications`() {
        assertFalse(mayPublishDeviceScan("A", "A", "A",
            DeviceInventoryRefreshStage.FAILED, 8, 8))
        assertFalse(mayPublishDeviceScan("A", "A", null,
            DeviceInventoryRefreshStage.IDLE, 8, 8))
    }
}
