package app.morphe.gui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatUtilsVersionBuildTest {
    @Test
    fun emptyBuildCodesKeepTheOriginalVersion() {
        assertEquals("v21.45.82", FormatUtils.formatVersionWithBuild("v21.45.82", emptySet()))
    }

    @Test
    fun oneBuildCodeIsVisibleInTheVersionChip() {
        assertEquals("v21.45.82 (1561310996)",
            FormatUtils.formatVersionWithBuild("v21.45.82", setOf(1561310996)))
    }

    @Test
    fun multipleBuildCodesAreSortedAndNoneIsSilentlyDiscarded() {
        assertEquals("21.45.82 (110, 120, 130)",
            FormatUtils.formatVersionWithBuild("21.45.82", setOf(130, 110, 120)))
    }
}
