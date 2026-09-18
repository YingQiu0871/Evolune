package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.ui.components.SettingsNavigationRow

/**
 * v1.7.2 Slice C — retained content/navigation rows (Guide / Privacy / Tutorial / About)
 * keep their existing routes and behavior; they are not flattened.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsNavigationRowsSection(
    onOpenGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenFeatureTutorial: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-navigation-rows"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsNavigationRow(
            modifier = Modifier.testTag("settings-guide-entry"),
            title = stringResource(R.string.settings_guide_title),
            description = stringResource(R.string.settings_guide_desc),
            icon = Icons.Outlined.Info,
            onClick = onOpenGuide
        )
        SettingsNavigationRow(
            modifier = Modifier.testTag("settings-privacy-entry"),
            title = stringResource(R.string.settings_privacy_title),
            description = stringResource(R.string.settings_privacy_desc),
            icon = Icons.Outlined.Lock,
            onClick = onOpenPrivacy
        )
        SettingsNavigationRow(
            modifier = Modifier.testTag("settings-feature-tutorial-entry"),
            title = stringResource(R.string.settings_feature_tutorial_title),
            description = stringResource(R.string.settings_feature_tutorial_desc),
            icon = Icons.Outlined.Info,
            onClick = onOpenFeatureTutorial
        )
        SettingsNavigationRow(
            modifier = Modifier.testTag("settings-about-entry"),
            title = stringResource(R.string.settings_about_title),
            description = stringResource(R.string.settings_about_desc),
            icon = Icons.Outlined.Info,
            onClick = onOpenAbout
        )
    }
}
