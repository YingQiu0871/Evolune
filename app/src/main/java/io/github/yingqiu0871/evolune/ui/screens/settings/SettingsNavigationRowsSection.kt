package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.ui.components.SettingsListItemPosition
import io.github.yingqiu0871.evolune.ui.components.SettingsNavigationRow

/**
 * Help & about section: the retained content/navigation rows (Tutorial / Guide / Privacy /
 * About) keep their existing routes and behavior; [updates] renders the update controls between
 * the help rows and About.
 */
@Composable
internal fun SettingsNavigationRowsSection(
    onOpenGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenFeatureTutorial: () -> Unit,
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
                modifier = Modifier.testTag("settings-feature-tutorial-entry"),
                title = stringResource(R.string.settings_feature_tutorial_title),
                description = stringResource(R.string.settings_feature_tutorial_desc),
                icon = Icons.Outlined.School,
                onClick = onOpenFeatureTutorial,
                position = SettingsListItemPosition.TOP
            )
            SettingsNavigationRow(
                modifier = Modifier.testTag("settings-guide-entry"),
                title = stringResource(R.string.settings_guide_title),
                description = stringResource(R.string.settings_guide_desc),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                onClick = onOpenGuide,
                position = SettingsListItemPosition.MIDDLE
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
