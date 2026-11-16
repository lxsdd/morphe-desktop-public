package app.morphe.gui.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real private AppConfig serialization, not just a public-mirror preference mock. */
class OutlineConfigMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun oldPrivateConfigKeepsOriginalAppearanceAndQuickModePreferences() {
        val previous = json.decodeFromString<AppConfig>(
            """{"useSharpCorners":true,"useSimpleMode":false,"homeAppSortMode":"RECOMMENDED",
               "preferredPatchChannel":"STABLE","legacyExtraSetting":"ignored"}"""
        )
        assertTrue(previous.useOutlines, "Absent flag must retain prior visible borders")
        assertTrue(previous.useSharpCorners)
        assertFalse(previous.useSimpleMode)
        assertEquals("STABLE", previous.preferredPatchChannel)
    }

    @Test
    fun hidingDecorativeBordersRoundTripsWithoutChangingPrivateSettings() {
        val previous = AppConfig(
            useOutlines = false,
            useSharpCorners = true,
            useSimpleMode = false,
            homeAppSortMode = "NAME",
            preferredPatchChannel = "EXPERIMENTAL",
        )
        val restored = json.decodeFromString<AppConfig>(json.encodeToString(previous))
        assertFalse(restored.useOutlines)
        assertTrue(restored.useSharpCorners)
        assertFalse(restored.useSimpleMode)
        assertEquals("NAME", restored.homeAppSortMode)
        assertEquals("EXPERIMENTAL", restored.preferredPatchChannel)
    }

    @Test
    fun explicitlyEnabledPreferenceAlsoSurvivesSerialization() {
        val restored = json.decodeFromString<AppConfig>(
            json.encodeToString(AppConfig(useOutlines = true))
        )
        assertTrue(restored.useOutlines)
    }
}
