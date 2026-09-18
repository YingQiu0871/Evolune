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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.export.PortableExportRange
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import io.github.yingqiu0871.evolune.ui.screens.settings.PortableDialogMessage
import io.github.yingqiu0871.evolune.ui.screens.settings.SettingsAppearanceSection
import io.github.yingqiu0871.evolune.ui.screens.settings.SettingsBasicDataSection
import io.github.yingqiu0871.evolune.ui.screens.settings.SettingsNavigationRowsSection
import io.github.yingqiu0871.evolune.ui.screens.settings.SettingsSyncBackupSection
import io.github.yingqiu0871.evolune.ui.screens.settings.SettingsUpdateSection
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.ImportResult
import io.github.yingqiu0871.evolune.viewmodel.UpdateCheckResult

/**
 * v1.7.2 Slice C — one vertically scrollable Settings screen. Common controls are inline
 * sections consuming hoisted canonical state; the four content/workflow entries stay
 * navigation rows. No section re-collects a flow and no section owns persisted state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    userSettings: UserSettings,
    healthConnectWeightSyncState: HealthConnectWeightSyncState,
    backupRestoreConnected: Boolean,
    updateCheckResult: UpdateCheckResult,
    onBodyWeightChange: (Double) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSelectDynamicSource: () -> Unit,
    onSelectPresetSource: () -> Unit,
    onPresetPaletteChange: (PresetPalette) -> Unit,
    onTimeFormatChange: (TimeFormat) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onHealthConnectWeightSyncEnabledChange: (Boolean) -> Unit,
    onHealthConnectReauthorize: () -> Unit,
    onHealthConnectManagePermissions: () -> Unit,
    importResult: ImportResult,
    onDismissImportResult: () -> Unit,
    clipboardExportMessage: String?,
    onClipboardExportMessageShown: () -> Unit,
    onImportClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onExportClick: () -> Unit,
    onExportToClipboard: () -> Unit,
    portableBusy: Boolean,
    onExportPortableJson: (PortableExportRange) -> Unit,
    onExportPortableCsv: (PortableExportRange) -> Unit,
    onImportPortableJson: () -> Unit,
    portableDialog: PortableDialogMessage?,
    onDismissPortableDialog: () -> Unit,
    onOpenGoogleDrive: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenFeatureTutorial: () -> Unit,
    onOpenAbout: () -> Unit,
    showTopBar: Boolean = true
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        contentWindowInsets = if (showTopBar) {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            )
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .padding(16.dp)
                .testTag("settings-root"),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsBasicDataSection(
                bodyWeight = userSettings.bodyWeight,
                onBodyWeightChange = onBodyWeightChange
            )
            SettingsAppearanceSection(
                settings = userSettings,
                onThemeModeChange = onThemeModeChange,
                onSelectDynamicSource = onSelectDynamicSource,
                onSelectPresetSource = onSelectPresetSource,
                onPresetPaletteChange = onPresetPaletteChange,
                onTimeFormatChange = onTimeFormatChange
            )
            SettingsSyncBackupSection(
                settings = userSettings,
                healthConnectWeightSyncState = healthConnectWeightSyncState,
                backupRestoreConnected = backupRestoreConnected,
                importResult = importResult,
                onDismissImportResult = onDismissImportResult,
                clipboardExportMessage = clipboardExportMessage,
                onClipboardExportMessageShown = onClipboardExportMessageShown,
                onImportClick = onImportClick,
                onImportFromClipboard = onImportFromClipboard,
                onExportClick = onExportClick,
                onExportToClipboard = onExportToClipboard,
                portableBusy = portableBusy,
                onExportPortableJson = onExportPortableJson,
                onExportPortableCsv = onExportPortableCsv,
                onImportPortableJson = onImportPortableJson,
                portableDialog = portableDialog,
                onDismissPortableDialog = onDismissPortableDialog,
                onWeightSyncEnabledChange = onHealthConnectWeightSyncEnabledChange,
                onReauthorize = onHealthConnectReauthorize,
                onManagePermissions = onHealthConnectManagePermissions,
                onOpenGoogleDrive = onOpenGoogleDrive,
                snackbarHostState = snackbarHostState
            )
            SettingsUpdateSection(
                autoCheckUpdates = userSettings.autoCheckUpdates,
                onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
                onCheckForUpdates = onCheckForUpdates,
                updateCheckResult = updateCheckResult,
                snackbarHostState = snackbarHostState
            )
            SettingsNavigationRowsSection(
                onOpenGuide = onOpenGuide,
                onOpenPrivacy = onOpenPrivacy,
                onOpenFeatureTutorial = onOpenFeatureTutorial,
                onOpenAbout = onOpenAbout
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    EvoluneTheme {
        SettingsScreen(
            userSettings = UserSettings(),
            healthConnectWeightSyncState = HealthConnectWeightSyncState(),
            backupRestoreConnected = false,
            updateCheckResult = UpdateCheckResult.Idle,
            onBodyWeightChange = {},
            onThemeModeChange = {},
            onSelectDynamicSource = {},
            onSelectPresetSource = {},
            onPresetPaletteChange = {},
            onTimeFormatChange = {},
            onAutoCheckUpdatesChange = {},
            onCheckForUpdates = {},
            onHealthConnectWeightSyncEnabledChange = {},
            onHealthConnectReauthorize = {},
            onHealthConnectManagePermissions = {},
            importResult = ImportResult.Idle,
            onDismissImportResult = {},
            clipboardExportMessage = null,
            onClipboardExportMessageShown = {},
            onImportClick = {},
            onImportFromClipboard = {},
            onExportClick = {},
            onExportToClipboard = {},
            portableBusy = false,
            onExportPortableJson = {},
            onExportPortableCsv = {},
            onImportPortableJson = {},
            portableDialog = null,
            onDismissPortableDialog = {},
            onOpenGoogleDrive = {},
            onOpenGuide = {},
            onOpenPrivacy = {},
            onOpenFeatureTutorial = {},
            onOpenAbout = {},
            showTopBar = false
        )
    }
}
