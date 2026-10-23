package app.morphe.gui.util

import app.morphe.engine.util.AdbDevice
import app.morphe.engine.util.DeviceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceDeploymentStateTest {
    @Test
    fun `device transitions stay isolated by serial`() {
        var states = emptyMap<String, DeviceDeploymentState>()
        states = states + ("A" to states.forSerial("A").installed("Installed on A"))

        assertTrue(states.forSerial("A").installed == true)
        assertEquals(DeviceDeploymentState.InstallPhase.INSTALLED, states.forSerial("A").installPhase)
        assertNull(states.forSerial("B").installed)
        assertEquals(DeviceDeploymentState.InstallPhase.IDLE, states.forSerial("B").installPhase)
    }

    @Test
    fun `link success on one serial does not affect another`() {
        val states = mapOf(
            "A" to DeviceDeploymentState("A").linksConfigured("Links routed"),
        )

        assertEquals(DeviceDeploymentState.LinkPhase.CONFIGURED, states.forSerial("A").linkPhase)
        assertEquals(DeviceDeploymentState.LinkPhase.UNKNOWN, states.forSerial("B").linkPhase)
    }

    @Test
    fun `failed install remains retryable without marking installed`() {
        val state = DeviceDeploymentState("A")
            .installing("Installing")
            .installFailed("failed")

        assertFalse(state.installed == true)
        assertEquals(DeviceDeploymentState.InstallPhase.FAILED, state.installPhase)
        assertEquals("failed", state.installError)
    }

    @Test
    fun `observed package is not called installed until exact output is verified`() {
        val different = DeviceDeploymentState("A").observed(
            isInstalled = true,
            version = "10",
            outputMatchesCurrent = false,
        )
        val exact = DeviceDeploymentState("A").observed(
            isInstalled = true,
            version = "10",
            outputMatchesCurrent = true,
        )

        assertEquals(DeviceDeploymentState.InstallPhase.PRESENT, different.installPhase)
        assertEquals(false, different.installedOutputMatchesCurrent)
        assertEquals(DeviceDeploymentState.InstallPhase.INSTALLED, exact.installPhase)
    }
    @Test
    fun `switching from Pixel 6a to Pixel 9 never reattributes success`() {
        val a = "PIXEL6A-SERIAL"
        val b = "PIXEL9-SERIAL"
        var states = mapOf(a to DeviceDeploymentState(a).installed("Installed on Pixel 6a"))
        assertEquals(DeviceDeploymentState.InstallPhase.IDLE, states.forSerial(b).installPhase)
        assertNull(states.forSerial(b).installed)
        states = states + (b to states.forSerial(b).installed("Installed on Pixel 9"))
        assertEquals("Installed on Pixel 6a", states.forSerial(a).installMessage)
        assertEquals("Installed on Pixel 9", states.forSerial(b).installMessage)
    }

    @Test
    fun `selection is by ready serial not model name or stale disconnection`() {
        val a = AdbDevice("A", DeviceStatus.DEVICE, model = "Pixel")
        val b = AdbDevice("B", DeviceStatus.DEVICE, model = "Pixel")
        assertEquals("B", resolveExplicitDevice(listOf(a, b), "B")?.id)
        assertNull(resolveSelectedDevice(listOf(a, b), null))
        assertEquals("B", resolveSelectedDevice(listOf(a, b), b)?.id)
        assertNull(resolveSelectedDevice(listOf(a, b), AdbDevice("OLD", DeviceStatus.OFFLINE)))
        assertEquals("B", resolveSelectedDevice(listOf(b), a)?.id)
    }

    @Test
    fun `stale reads cannot replace next device inventory`() {
        assertFalse(shouldApplyDeviceResult("B", "A", 5, 5))
        assertFalse(shouldApplyDeviceResult("B", "B", 4, 5))
        assertTrue(shouldApplyDeviceResult("B", "B", 5, 5))
    }

}
