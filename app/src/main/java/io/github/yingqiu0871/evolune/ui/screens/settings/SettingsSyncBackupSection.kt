package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.export.PortableExportRange
import io.github.yingqiu0871.evolune.ui.components.SettingsNavigationRow
import io.github.yingqiu0871.evolune.viewmodel.ImportResult

/**
 * Backup & data section: the Google Drive backup/restore entry first (its secure multi-step
 * route is kept and only linked from here), then local export/import.
 */
@Composable
internal fun SettingsSyncBackupSection(
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
    onOpenGoogleDrive: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-sync-backup-section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionHeader(title = stringResource(R.string.settings_sync_backup_title))

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
    }
}
