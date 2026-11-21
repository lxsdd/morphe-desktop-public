package app.morphe.gui.ui.components.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchRoutingTest {
    private val entries = listOf(
        SettingsSearchItem("Appearance", "Darstellung", "App-Karten", "Kartenfarben"),
        SettingsSearchItem("Appearance", "Darstellung", "Umrandungen", "Dekorative Ränder einblenden"),
        SettingsSearchItem("Advanced", "Erweitert", "Signierung", "Schlüsselverwaltung", expandSection = "Signing"),
        SettingsSearchItem("Advanced", "Erweitert", "GitHub-PAT", "Zugriff auf GitHub", expandSection = "GitHub PAT"),
        SettingsSearchItem("System", "System", "Speicher & Cache", "Heruntergeladene Patchpakete"),
    )

    @Test fun multiWordSearchMatchesAcrossLocalizedLabelAndDescription() {
        val matches = SettingsSearchRouting.find("  speicher  patchpakete ", entries)
        assertEquals(listOf("Speicher & Cache"), matches.map { it.label })
        assertEquals("System", matches.single().category)
    }

    @Test fun outlinesAreSearchableOnlyByStaticPublicLabels() {
        assertEquals("Umrandungen", SettingsSearchRouting.find("ränder", entries).single().label)
        assertEquals("Appearance", SettingsSearchRouting.find("umrandungen", entries).single().category)
    }

    @Test fun accentedNamesAndMixedCaseRemainSearchable() {
        assertEquals("Signierung", SettingsSearchRouting.find("SCHLÜSSELverwaltung", entries).single().label)
        assertEquals("GitHub-PAT", SettingsSearchRouting.find("github-pat", entries).single().label)
    }

    @Test fun categorySearchRetainsOnlyMatchingCategories() {
        assertEquals(listOf("Signierung", "GitHub-PAT"),
            SettingsSearchRouting.find("erweitert", entries).map { it.label })
    }

    @Test fun blankOrUnknownSearchDoesNotOpenControlsOrExposeSecrets() {
        assertTrue(SettingsSearchRouting.find(" ", entries).isEmpty())
        assertTrue(SettingsSearchRouting.find("private-access-token-value", entries).isEmpty())
        assertTrue(SettingsSearchRouting.find("nonexistent setting", entries).isEmpty())
    }

    @Test fun duplicateEntriesCannotRenderDuplicateRoutes() {
        assertEquals(1, SettingsSearchRouting.find("signierung", entries + entries[1]).size)
    }

    @Test fun hiddenDependentSettingNavigatesToItsVisibleParentWithoutMutation() {
        val item = SettingsSearchItem(
            category = "Advanced", categoryLabel = "Erweitert", label = "Standardlinks deaktivieren",
            targetLabel = "Links weiterleiten",
        )
        assertEquals("Links weiterleiten", SettingsSearchRouting.find("standardlinks", listOf(item)).single().targetLabel)
        assertEquals(null, item.expandSection)
    }

    @Test fun aboutSettingsSearchOpensTheActualAboutTab() {
        val about = SettingsSearchItem(
            category = "About", categoryLabel = "Über",
            label = "Öffentliche Custom-Releases",
            hint = "Nur bestätigte Custom-Versionen",
        )
        assertEquals("About", SettingsSearchRouting.find("Custom-Releases", listOf(about)).single().category)
        assertEquals("About", SettingsSearchRouting.find("bestätigte", listOf(about)).single().category)
        assertTrue(SettingsSearchRouting.find("private-access-token-value", listOf(about)).isEmpty())
    }
}
