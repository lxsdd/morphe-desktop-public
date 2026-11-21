/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.ui.components.settings

import androidx.compose.runtime.Composable
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/** Display-only search metadata. Never index the values of keys, PATs, paths or device records. */
internal data class SettingsSearchItem(
    val category: String,
    val categoryLabel: String,
    val label: String,
    val hint: String = "",
    val targetLabel: String = label,
    val expandSection: String? = null,
)

internal object SettingsSearchRouting {
    /** Matches every whitespace-separated term against localized public labels and help text. */
    fun find(query: String, entries: List<SettingsSearchItem>): List<SettingsSearchItem> {
        val terms = query.trim().lowercase(Locale.ROOT)
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList() // navigation is displayed instead
        return entries.filter { entry ->
            val corpus = "${entry.categoryLabel} ${entry.label} ${entry.hint}".lowercase(Locale.ROOT)
            terms.all { it in corpus }
        }.distinctBy { it.category to it.label }
    }
}

/** Indexes *only the real settings controls* exposed by the current Custom dialog. */
@Composable
internal fun settingsSearchItems(
    cardColorsAvailable: Boolean,
    advancedLinkOptionsVisible: Boolean,
): List<SettingsSearchItem> {
    val appearance = stringResource(Res.string.settings_nav_appearance)
    val advanced = stringResource(Res.string.settings_nav_advanced)
    val system = stringResource(Res.string.settings_nav_system)
    fun item(category: String, categoryLabel: String, label: String, hint: String = "",
             target: String = label, expand: String? = null) =
        SettingsSearchItem(category, categoryLabel, label, hint, target, expand)

    val themeTitle = stringResource(Res.string.settings_section_theme)
    val linkTitle = stringResource(Res.string.settings_toggle_route_links_label)
    return listOf(
        item("Appearance", appearance, stringResource(Res.string.settings_section_language)),
        item("Appearance", appearance, themeTitle),
        item("Appearance", appearance, stringResource(Res.string.settings_toggle_sharp_corners_label),
            stringResource(Res.string.settings_toggle_sharp_corners_desc)),
        item("Appearance", appearance, stringResource(Res.string.settings_toggle_outlines_label),
            stringResource(Res.string.settings_toggle_outlines_desc)),
        item("Appearance", appearance, stringResource(Res.string.settings_section_accent_color)),
        item("Appearance", appearance, stringResource(Res.string.settings_section_app_cards),
            target = if (cardColorsAvailable) stringResource(Res.string.settings_section_app_cards) else themeTitle),
        item("Appearance", appearance, stringResource(Res.string.settings_toggle_local_icons_label),
            stringResource(Res.string.settings_toggle_local_icons_desc)),
        item("Appearance", appearance, stringResource(Res.string.settings_toggle_more_apps_label),
            stringResource(Res.string.settings_toggle_more_apps_desc)),
        item("Appearance", appearance, stringResource(Res.string.settings_section_background_animation)),
        item("Appearance", appearance, stringResource(Res.string.settings_toggle_parallax_label),
            stringResource(Res.string.settings_toggle_parallax_desc)),

        item("Advanced", advanced, stringResource(Res.string.settings_toggle_expert_mode_label),
            stringResource(Res.string.settings_toggle_expert_mode_desc)),
        item("Advanced", advanced, linkTitle, stringResource(Res.string.settings_toggle_route_links_desc)),
        item("Advanced", advanced, stringResource(Res.string.settings_toggle_disable_stock_links_label),
            target = if (advancedLinkOptionsVisible) stringResource(Res.string.settings_toggle_disable_stock_links_label) else linkTitle),
        item("Advanced", advanced, stringResource(Res.string.settings_section_signing), expand = "Signing"),
        item("Advanced", advanced, stringResource(Res.string.settings_section_strip_libs), expand = "Strip libs"),
        item("Advanced", advanced, stringResource(Res.string.settings_toggle_developer_options_label)),
        item("Advanced", advanced, stringResource(Res.string.settings_runtime_logs_title), expand = "Runtime logs"),
        item("Advanced", advanced, stringResource(Res.string.settings_section_github_pat), expand = "GitHub PAT"),

        item("System", system, stringResource(Res.string.settings_toggle_auto_cleanup_label),
            stringResource(Res.string.settings_toggle_auto_cleanup_desc)),
        item("System", system, stringResource(Res.string.settings_channel_label)),
        item("System", system, stringResource(Res.string.settings_section_output_folder)),
        item("System", system, stringResource(Res.string.settings_toggle_auto_start_adb_label),
            stringResource(Res.string.settings_toggle_auto_start_adb_desc)),
        item("System", system, stringResource(Res.string.settings_section_storage)),
        item("System", system, stringResource(Res.string.settings_section_about)),
        item("System", system, stringResource(Res.string.settings_about_changelogs_title)),
    )
}
