package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.ui.components.SettingsNavigationRow
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme

/** Pure navigation hub for the Settings information architecture. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onOpenBasicData: () -> Unit,
    onOpenAppearanceAndFormat: () -> Unit,
    onOpenSyncAndBackup: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenAbout: () -> Unit,
    showTopBar: Boolean = true,
    onOpenGuide: () -> Unit = {},
    onOpenFeatureTutorial: () -> Unit = {}
) {
    Scaffold(
        contentWindowInsets = if (showTopBar) {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            )
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMediumEmphasized
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsNavigationRow(
                modifier = Modifier.testTag("settings-basic-data-entry"),
                title = stringResource(R.string.settings_basic_data_title),
                description = stringResource(R.string.settings_basic_data_desc),
                icon = Icons.Outlined.PhoneAndroid,
                onClick = onOpenBasicData
            )
            SettingsNavigationRow(
                modifier = Modifier.testTag("settings-appearance-format-entry"),
                title = stringResource(R.string.settings_appearance_format_title),
                description = stringResource(R.string.settings_appearance_format_desc),
                icon = Icons.Outlined.Palette,
                onClick = onOpenAppearanceAndFormat
            )
            SettingsNavigationRow(
                modifier = Modifier.testTag("settings-sync-backup-entry"),
                title = stringResource(R.string.settings_sync_backup_title),
                description = stringResource(R.string.settings_sync_backup_desc),
                icon = Icons.Outlined.Sync,
                onClick = onOpenSyncAndBackup
            )
            SettingsNavigationRow(
                modifier = Modifier.testTag("settings-update-entry"),
                title = stringResource(R.string.settings_update_title),
                description = stringResource(R.string.settings_update_desc),
                icon = Icons.Outlined.SystemUpdate,
                onClick = onOpenUpdate
            )
            SettingsNavigationRow(
                modifier = Modifier.testTag("settings-guide-entry"),
                title = stringResource(R.string.settings_guide_title),
                description = stringResource(R.string.settings_guide_desc),
                icon = Icons.Outlined.Info,
                onClick = onOpenGuide
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
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    EvoluneTheme {
        SettingsScreen({}, {}, {}, {}, {})
    }
}
