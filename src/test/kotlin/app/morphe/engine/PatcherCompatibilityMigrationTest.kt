package app.morphe.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

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

    @Test
    fun `old major gets source-update advice, not Morphe upgrade`() {
        assertEquals(PatcherRequirementDirection.LEGACY_MAJOR, patcherRequirementDirection("1.8.0", "2.3.0"))
        assertEquals(PatcherRequirementDirection.LEGACY_MAJOR, patcherRequirementDirection("v1.5.0", "2.3.0"))
    }

    @Test
    fun `new major and newer same-major get newer-engine advice`() {
        assertEquals(PatcherRequirementDirection.NEWER_REQUIRED, patcherRequirementDirection("3.0.0", "2.3.0"))
        assertEquals(PatcherRequirementDirection.NEWER_REQUIRED, patcherRequirementDirection("2.4.0", "2.3.0"))
    }

    @Test
    fun `known compatible requirement is not falsely called legacy`() {
        assertEquals(PatcherRequirementDirection.UNKNOWN, patcherRequirementDirection("2.0.0", "2.3.0"))
        assertEquals(PatcherRequirementDirection.UNKNOWN, patcherRequirementDirection("2.3.0", "2.3.0"))
    }

    @Test
    fun `missing or malformed major does not trigger update advice`() {
        assertEquals(PatcherRequirementDirection.UNKNOWN, patcherRequirementDirection(null, "2.3.0"))
        assertEquals(PatcherRequirementDirection.UNKNOWN, patcherRequirementDirection("unknown", "2.3.0"))
        assertEquals(PatcherRequirementDirection.UNKNOWN, patcherRequirementDirection("1.8.0", null))
    }
}
