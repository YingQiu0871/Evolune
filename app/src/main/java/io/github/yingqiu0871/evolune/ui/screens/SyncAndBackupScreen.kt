package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncStatus
import io.github.yingqiu0871.evolune.ui.components.SettingsNavigationRow

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncAndBackupScreen(
    settings: UserSettings,
    healthConnectWeightSyncState: HealthConnectWeightSyncState,
    backupRestoreConnected: Boolean,
    onOpenData: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onOpenGoogleDrive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SettingsNavigationGroup(
            title = stringResource(R.string.settings_sync_backup_local_title),
            rowTag = "settings-sync-backup-data-entry",
            rowTitle = stringResource(R.string.settings_sync_backup_data_title),
            rowDescription = stringResource(R.string.settings_sync_backup_data_desc),
            icon = Icons.Outlined.SwapVert,
            onClick = onOpenData
        )

        SettingsNavigationGroup(
            title = stringResource(R.string.settings_sync_backup_health_title),
            rowTag = "settings-sync-backup-health-connect-entry",
            rowTitle = stringResource(R.string.settings_health_connect_sync_title),
            rowDescription = healthConnectSummary(
                enabled = settings.healthConnectWeightSyncEnabled,
                state = healthConnectWeightSyncState
            ),
            icon = Icons.Outlined.HealthAndSafety,
            onClick = onOpenHealthConnect
        )

        SettingsNavigationGroup(
            title = stringResource(R.string.settings_sync_backup_cloud_title),
            rowTag = "settings-sync-backup-google-drive-entry",
            rowTitle = stringResource(R.string.settings_sync_backup_google_drive_title),
            rowDescription = stringResource(
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsNavigationGroup(
    title: String,
    rowTag: String,
    rowTitle: String,
    rowDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SettingsNavigationRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(rowTag),
            title = rowTitle,
            description = rowDescription,
            icon = icon,
            onClick = onClick
        )
    }
}

@Composable
private fun healthConnectSummary(
    enabled: Boolean,
    state: HealthConnectWeightSyncState
): String = when {
    !enabled -> stringResource(R.string.settings_health_connect_sync_status_disabled)
    state.status == HealthConnectWeightSyncStatus.PERMISSION_REQUIRED ->
        stringResource(R.string.settings_health_connect_sync_status_permission)
    state.status == HealthConnectWeightSyncStatus.UNAVAILABLE ->
        stringResource(R.string.settings_health_connect_sync_status_unavailable)
    state.status == HealthConnectWeightSyncStatus.UPDATE_REQUIRED ->
        stringResource(R.string.settings_health_connect_sync_status_update_required)
    state.status == HealthConnectWeightSyncStatus.NO_DATA ->
        stringResource(R.string.settings_health_connect_sync_status_no_data)
    state.status == HealthConnectWeightSyncStatus.ERROR ->
        stringResource(R.string.settings_health_connect_sync_status_error)
    state.status == HealthConnectWeightSyncStatus.CHECKING ||
        state.status == HealthConnectWeightSyncStatus.SYNCING ->
        stringResource(R.string.settings_health_connect_sync_status_checking)
    else -> stringResource(R.string.settings_health_connect_sync_status_connected)
}
