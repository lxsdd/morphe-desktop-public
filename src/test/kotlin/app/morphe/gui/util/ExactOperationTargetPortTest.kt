package app.morphe.gui.util

import app.morphe.engine.util.AdbDevice
import app.morphe.engine.util.DeviceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExactOperationTargetPortTest {
    private val a = AdbDevice("fixture-a", DeviceStatus.DEVICE, model = "Test Device")
    private val b = AdbDevice("fixture-b", DeviceStatus.DEVICE, model = "Test Device")

    @Test
    fun `target is captured by immutable serial even when display names match`() {
        val selected = DeviceMonitorState(devices = listOf(a, b), selectedDevice = a)
        val target = selected.captureOperationTarget()
        assertEquals("fixture-a", target?.serial)
        assertEquals("Test Device", target?.displayName)
        val switched = selected.copy(selectedDevice = b)
        assertEquals("fixture-b", switched.captureOperationTarget()?.serial)
        assertEquals("fixture-a", target?.serial)
    }

    @Test
    fun `missing unauthorized and stale targets fail closed`() {
        assertNull(DeviceMonitorState(devices = listOf(a, b)).captureOperationTarget())
        assertNull(DeviceMonitorState(devices = listOf(b), selectedDevice = a).captureOperationTarget())
        assertNull(DeviceMonitorState(devices = listOf(a.copy(status = DeviceStatus.UNAUTHORIZED), b), selectedDevice = a).captureOperationTarget())
        assertNull(DeviceMonitorState(devices = listOf(a.copy(status = DeviceStatus.OFFLINE)), selectedDevice = a).captureOperationTarget())
        assertNull(resolveExplicitDevice(listOf(a, b), "fixture-c"))
    }

    @Test
    fun `device generation counters cannot cross selection boundaries`() {
        val guard = PerDeviceGenerationGuard()
        val firstA = guard.next(a.id)
        val firstB = guard.next(b.id)
        val secondA = guard.next(a.id)
        assertFalse(guard.isCurrent(a.id, firstA))
        assertTrue(guard.isCurrent(a.id, secondA))
        assertTrue(guard.isCurrent(b.id, firstB))
        assertFalse(shouldApplyDeviceResult(a.id, b.id, 1, 1))
    }
}
