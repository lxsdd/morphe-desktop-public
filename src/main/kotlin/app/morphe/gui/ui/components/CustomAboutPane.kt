/*
 * Morphe Desktop Custom — first-class About view integrated into existing Settings.
 * No upstream app-update result is mistaken for a Custom distribution update.
 */
package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.morphe.engine.MorpheComponents
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.ui.components.settings.AboutRow
import app.morphe.gui.ui.components.settings.SectionLabel
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CustomAboutPane(onShowChangelog: () -> Unit) {
    val links = LocalUriHandler.current
    val unknown = stringResource(Res.string.unknown)
    Column(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(stringResource(Res.string.settings_section_about), icon = MorpheIcons.Info)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MorpheIcons.MorpheLogo,
                contentDescription = null,
                tint = MorpheTheme.colors.primary,
                modifier = Modifier.size(46.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MorpheTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.custom_about_downstream_label),
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(Res.string.app_info_version_gui, AppConstants.APP_VERSION),
                style = MorpheTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    Res.string.app_info_version_patcher, MorpheComponents.patcherVersion ?: unknown,
                ),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    Res.string.app_info_version_library, MorpheComponents.libraryVersion ?: unknown,
                ),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(Res.string.custom_about_update_provenance),
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AboutRow(
            title = stringResource(Res.string.settings_about_changelogs_title),
            subtitle = stringResource(Res.string.settings_about_changelogs_desc),
            onClick = onShowChangelog,
        ) {
            Icon(MorpheIcons.Article, contentDescription = null, tint = MorpheTheme.colors.primary)
        }
        AboutRow(
            title = stringResource(Res.string.settings_about_documentation_title),
            subtitle = stringResource(Res.string.settings_about_documentation_desc),
            onClick = { links.openUri(AppConstants.DOCUMENTATION_URL) },
        ) {
            Icon(MorpheIcons.MenuBook, contentDescription = null, tint = MorpheTheme.colors.primary)
        }
        AboutRow(
            title = stringResource(Res.string.settings_about_website_title),
            subtitle = stringResource(Res.string.settings_about_website_desc),
            onClick = { links.openUri(AppConstants.WEBSITE_URL) },
        ) {
            Icon(MorpheIcons.Public, contentDescription = null, tint = MorpheTheme.colors.primary)
        }
        AboutRow(
            title = stringResource(Res.string.custom_about_public_releases),
            subtitle = stringResource(Res.string.custom_about_public_releases_desc),
            onClick = { links.openUri("https://github.com/lxsdd/morphe-desktop-public/releases") },
        ) {
            Icon(MorpheIcons.Github, contentDescription = null, tint = MorpheTheme.colors.primary)
        }
    }
}
