package app.morphe.engine.installation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Simulated ADB results only. No physical phone or install commands. */
class AdbExactSerialTargetPortTest {
    private val listing = "List of devices attached\nDEVICE-A\tdevice\nDEVICE-B\tdevice\n"

    @Test
    fun `explicit ready serial remains immutable`() {
        val calls = mutableListOf<List<String>>()
        val runner = AdbCommandRunner { cmd, _ ->
            calls += cmd
            require(cmd == listOf("adb", "devices"))
            AdbCommandResult(0, listing)
        }
        assertEquals("DEVICE-A", resolveAdbDeviceTarget("adb", "DEVICE-A", runner).serial)
        assertEquals(listOf(listOf("adb", "devices")), calls)
    }

    @Test
    fun `missing or blank target fails before ADB discovery`() {
        val calls = mutableListOf<List<String>>()
        val runner = AdbCommandRunner { cmd, _ ->
            calls += cmd
            error("Unexpected ADB command: $cmd")
        }
        assertFailsWith<AdbInstallException> { resolveAdbDeviceTarget("adb", null, runner) }
        assertFailsWith<AdbInstallException> { resolveAdbDeviceTarget("adb", "", runner) }
        assertFailsWith<AdbInstallException> { resolveAdbDeviceTarget("adb", "  ", runner) }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `offline requested device cannot fall back to another phone`() {
        val runner = AdbCommandRunner { cmd, _ ->
            require(cmd == listOf("adb", "devices"))
            AdbCommandResult(0, listing.replace("DEVICE-A\tdevice", "DEVICE-A\toffline"))
        }
        assertFailsWith<AdbInstallException> { resolveAdbDeviceTarget("adb", "DEVICE-A", runner) }
        assertEquals("DEVICE-B", resolveAdbDeviceTarget("adb", "DEVICE-B", runner).serial)
    }

    @Test
    fun `batch preflight does not begin mutations if any serial is absent`() {
        val calls = mutableListOf<List<String>>()
        val runner = AdbCommandRunner { cmd, _ ->
            calls += cmd
            assertFalse("-s" in cmd)
            AdbCommandResult(0, listing)
        }
        assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTargets("adb", listOf("DEVICE-A", "UNKNOWN"), runner)
        }
        assertEquals(listOf(listOf("adb", "devices")), calls)
    }
}
