package app.morphe.gui.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/** Migration is decode-only: user config files must never be rewritten in tests. */
class PrivateConfig211MigrationTest {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "#kind" }

    @Test
    fun private118QuickSourceAndStoragePreferencesSurvive211Decode() {
        val previous = """
            {
                "useSimplifiedMode": false,
                "useSimpleMode": false,
                "autoStartAdb": false,
                "autoRouteLinksAfterInstall": false,
                "disableStockLinksAfterInstall": false,
                "excludedMppPatterns": ["*-debug.mpp"],
                "keystorePath": "keys/owner.keystore",
                "keystoreAlias": "Owner",
                "homeAppSortPreferences": {"YOURS": {"criterion": "ACTION_PRIORITY", "direction": "DESCENDING"}},
                "patchSource": [{
                    "id": "custom",
                    "name": "Custom Patches",
                    "type": "GITHUB",
                    "url": "https://github.com/example/custom-patches",
                    "useExperimentalVersions": true
                }]
            }
        """.trimIndent()
        val result = json.decodeFromString(AppConfig.serializer(), previous)
        assertFalse(result.useSimplifiedMode)
        assertFalse(result.useSimpleMode)
        assertFalse(result.effectiveSimpleMode())
        assertFalse(result.autoStartAdb)
        assertFalse(result.autoRouteLinksAfterInstall)
        assertFalse(result.disableStockLinksAfterInstall)
        assertEquals(listOf("*-debug.mpp"), result.excludedMppPatterns)
        assertEquals("keys/owner.keystore", result.keystorePath)
        assertEquals("Owner", result.keystoreAlias)
        assertEquals("ACTION_PRIORITY", result.homeAppSortPreferences["YOURS"]?.criterion)
        assertTrue(result.patchSource.single().useExperimentalVersions)
        assertEquals("custom", result.patchSource.single().id)
    }

    @Test
    fun absentHistoricalPreferencesStayOptInAndDoNotSelectOrTrustNewSources() {
        val minimal = json.decodeFromString(AppConfig.serializer(), """{"language":"system"}""")
        assertTrue(minimal.effectiveSimpleMode())
        assertFalse(minimal.autoStartAdb)
        assertFalse(minimal.autoRouteLinksAfterInstall)
        assertFalse(minimal.disableStockLinksAfterInstall)
        assertNull(minimal.keystorePath)
        assertTrue(minimal.excludedMppPatterns.isEmpty())
        assertFalse(minimal.patchSource.first().useExperimentalVersions)
    }
}
