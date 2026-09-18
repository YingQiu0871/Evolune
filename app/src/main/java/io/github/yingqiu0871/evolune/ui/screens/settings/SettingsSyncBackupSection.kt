package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.export.PortableExportRange
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.ui.components.SettingsNavigationRow
import io.github.yingqiu0871.evolune.viewmodel.ImportResult

/**
 * v1.7.2 Slice C — Sync & backup inline section: local import/export actions, Health Connect
 * controls and the retained Google Drive backup/restore entry (its secure multi-step route is
 * kept and only linked from here).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSyncBackupSection(
    settings: UserSettings,
    healthConnectWeightSyncState: HealthConnectWeightSyncState,
    backupRestoreConnected: Boolean,
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
    onWeightSyncEnabledChange: (Boolean) -> Unit,
    onReauthorize: () -> Unit,
    onManagePermissions: () -> Unit,
    onOpenGoogleDrive: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-sync-backup-section"),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SettingsSectionHeader(title = stringResource(R.string.settings_sync_backup_title))

        SettingsImportExportBlock(
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
            snackbarHostState = snackbarHostState
        )

        SettingsHealthConnectSection(
            settings = settings,
            state = healthConnectWeightSyncState,
            onWeightSyncEnabledChange = onWeightSyncEnabledChange,
            onReauthorize = onReauthorize,
            onManagePermissions = onManagePermissions
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_sync_backup_cloud_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            SettingsNavigationRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-sync-backup-google-drive-entry"),
                title = stringResource(R.string.settings_sync_backup_google_drive_title),
                description = stringResource(
                    if (backupRestoreConnected) {
                        R.string.settings_backup_restore_connected
                    } else {
                        R.string.settings_backup_restore_authorization_required
                    }
                ),
                icon = Icons.Outlined.Cloud,
                onClick = onOpenGoogleDrive
            )
        }
    }
}
