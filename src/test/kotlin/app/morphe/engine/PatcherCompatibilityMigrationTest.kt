package app.morphe.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Prevent Patcher 1.x bundles being accepted just because 1.x < 2.x. */
class PatcherCompatibilityMigrationTest {
    @Test
    fun `older major bundle must not load under Patcher 2`() {
        assertTrue(PatcherCompatibility.isIncompatiblePatcherVersion("1.9.0", "2.2.0"))
    }

    @Test
    fun `same major earlier bundle is allowed`() {
        assertFalse(PatcherCompatibility.isIncompatiblePatcherVersion("2.0.0", "2.2.0"))
    }

    @Test
    fun `future bundle is rejected`() {
        assertTrue(PatcherCompatibility.isIncompatiblePatcherVersion("2.3.0", "2.2.0"))
    }

    @Test
    fun `malformed requirements are not assumed compatible`() {
        assertTrue(PatcherCompatibility.isIncompatiblePatcherVersion("unknown", "2.2.0"))
    }
}
