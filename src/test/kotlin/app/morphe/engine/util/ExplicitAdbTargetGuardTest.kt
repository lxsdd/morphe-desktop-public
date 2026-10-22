/*
 * Regression contract for explicit-target ADB behavior.
 * Uses synthetic values only; no device connection or mutations are performed.
 */
package app.morphe.engine.util

import app.morphe.desktop.command.utility.validatedDeviceSerials
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertEquals

class ExplicitAdbTargetGuardTest {
    @Test
    fun `CLI validation only accepts unique, unpadded and nonblank synthetic targets`() {
        assertNull(validatedDeviceSerials(null))
        assertNull(validatedDeviceSerials(emptyArray()))
        assertNull(validatedDeviceSerials(arrayOf("")))
        assertNull(validatedDeviceSerials(arrayOf(" ")))
        assertNull(validatedDeviceSerials(arrayOf("virtual-device-a", "virtual-device-a")))
        assertNull(validatedDeviceSerials(arrayOf(" virtual-device-a")))
        assertNull(validatedDeviceSerials(arrayOf("virtual-device-a ")))
        assertEquals(
            listOf("virtual-device-a", "192.0.2.10:5555"),
            validatedDeviceSerials(arrayOf("virtual-device-a", "192.0.2.10:5555"))
        )
    }


    @Test
    fun `blank explicit serial fails without invoking ADB discovery`() = runBlocking {
        val manager = AdbManager()
        listOf("", " ", "\t").forEach { serial ->
            val result = manager.resolveTargetDevice(serial)
            assertTrue(result.isFailure, "A blank explicit serial must never auto-select")
            val exception = assertIs<AdbException>(result.exceptionOrNull())
            assertEquals(AdbErrorCode.DEVICE_NOT_FOUND, exception.errorCode)
        }
    }
}
