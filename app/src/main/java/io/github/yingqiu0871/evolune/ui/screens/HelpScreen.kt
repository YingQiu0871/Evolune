package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
 * Settings → 使用帮助: one place for the two replayable walkthroughs, the feature tutorial
 * (how to use) and the first-run guide (terms, data boundary, disclaimers). Both keep their
 * existing routes; this page only links to them.
 */
@Composable
fun HelpScreen(
    onOpenFeatureTutorial: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings-help-screen"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
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
            position = SettingsListItemPosition.BOTTOM
        )
    }
}
