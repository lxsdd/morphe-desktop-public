package app.morphe.gui.util

import app.morphe.engine.PatchBundleIncompatibleException
import app.morphe.engine.PatchSourceLoadException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatchSourceLegacyFailureClassificationTest {
    private fun bundle(required: String, current: String = "2.3.0") =
        PatchBundleIncompatibleException(
            message = "Incompatible bundle",
            requiredPatcherVersion = required,
            currentPatcherVersion = current,
        )

    @Test
    fun `all legacy failures get explanatory banner`() {
        assertTrue(onlyLegacyPatcherFailures(
            setOf("old-1", "old-2"),
            mapOf("old-1" to bundle("1.8.0"), "old-2" to bundle("1.5.0")),
        ))
    }

    @Test
    fun `network failure does not get misleading legacy banner`() {
        assertFalse(onlyLegacyPatcherFailures(
            setOf("old", "offline"),
            mapOf("old" to bundle("1.8.0"), "offline" to java.io.IOException("Connection reset")),
        ))
    }

    @Test
    fun `missing resolved source is not treated as old Patcher bundle`() {
        assertFalse(onlyLegacyPatcherFailures(
            setOf("old", "resolution-failed"),
            mapOf("old" to bundle("1.8.0")),
        ))
    }

    @Test
    fun `newer Patcher requirement does not get old-source advice`() {
        assertFalse(onlyLegacyPatcherFailures(setOf("new"), mapOf("new" to bundle("2.4.0"))))
    }

    @Test
    fun `wrapped legacy error preserves classification`() {
        assertTrue(onlyLegacyPatcherFailures(setOf("wrapped"), mapOf(
            "wrapped" to PatchSourceLoadException("Could not load", bundle("1.8.0")),
        )))
    }

    @Test
    fun `no failures never display legacy warning`() {
        assertFalse(onlyLegacyPatcherFailures(emptySet(), emptyMap()))
    }
}
