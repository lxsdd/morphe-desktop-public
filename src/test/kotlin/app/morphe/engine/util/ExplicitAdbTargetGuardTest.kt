/*
 * Regression contract for explicit-target ADB behavior.
 * Uses synthetic values only; no device connection or mutations are performed.
 */
package app.morphe.engine.util

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExplicitAdbTargetGuardTest {
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
