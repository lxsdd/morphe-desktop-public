package app.morphe.gui.data.repository

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class PatchSourceDuplicateGuardPortTest {
    private fun github(id: String, url: String) = PatchSource(
        id = id, name = id, type = PatchSourceType.GITHUB, url = url
    )

    @Test
    fun `case and .git suffix do not create duplicate repositories`() {
        val original = github("first", "https://github.com/Example/Patches")
        val equivalent = github("second", "https://github.com/example/patches.git")
        val result = PatchSourceDuplicateGuard.addIfUnique(listOf(original), equivalent)
        assertFalse(result.added)
        assertEquals(listOf(original), result.sources)
    }

    @Test
    fun `distinct repositories and hosting providers remain separate`() {
        val original = github("one", "https://github.com/Example/Patches")
        val other = github("two", "https://github.com/Example/Different")
        val differentProvider = PatchSource(
            id = "three", name = "three", type = PatchSourceType.GITLAB,
            url = "https://gitlab.com/Example/Patches"
        )
        assertTrue(PatchSourceDuplicateGuard.addIfUnique(listOf(original), other).added)
        assertTrue(PatchSourceDuplicateGuard.addIfUnique(listOf(original), differentProvider).added)
    }

    @Test
    fun `identical local path is not added twice`() {
        val original = PatchSource(
            id = "local-one", name = "local-one", type = PatchSourceType.LOCAL, filePath = "safe-fixture.mpp"
        )
        val same = original.copy(id = "local-two", name = "local-two")
        assertFalse(PatchSourceDuplicateGuard.addIfUnique(listOf(original), same).added)
    }
}
