package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.ui.components.SettingsListItemPosition
import io.github.yingqiu0871.evolune.ui.components.SettingsNavigationRow

/**
 * Help & about section: 使用帮助 (links to the tutorial and first-run guide), privacy and About
 * keep their existing routes and behavior; [updates] renders the update controls between the
 * help rows and About.
 */
@Composable
internal fun SettingsNavigationRowsSection(
    onOpenHelp: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    updates: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-navigation-rows"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionHeader(title = stringResource(R.string.settings_help_about_title))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsNavigationRow(
                modifier = Modifier.testTag("settings-help-entry"),
                title = stringResource(R.string.settings_help_title),
                description = stringResource(R.string.settings_help_desc),
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                onClick = onOpenHelp,
                position = SettingsListItemPosition.TOP
            )
            SettingsNavigationRow(
                modifier = Modifier.testTag("settings-privacy-entry"),
                title = stringResource(R.string.settings_privacy_title),
                description = stringResource(R.string.settings_privacy_desc),
                icon = Icons.Outlined.Lock,
                onClick = onOpenPrivacy,
                position = SettingsListItemPosition.BOTTOM
            )
        }
        updates()
        SettingsNavigationRow(
            modifier = Modifier.testTag("settings-about-entry"),
            title = stringResource(R.string.settings_about_title),
            description = stringResource(R.string.settings_about_desc),
            icon = Icons.Outlined.Info,
            onClick = onOpenAbout
        )
    }
}
