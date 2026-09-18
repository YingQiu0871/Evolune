package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * v1.7.2 Slice C — Health Connect controls inline in Sync & backup. Presentation only:
 * permission handling, consent flows and the existing SettingsDataStore owner are unchanged.
 */
@Composable
internal fun SettingsHealthConnectSection(
    settings: UserSettings,
    state: HealthConnectWeightSyncState,
    onWeightSyncEnabledChange: (Boolean) -> Unit,
    onReauthorize: () -> Unit,
    onManagePermissions: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-health-connect-section"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_sync_backup_health_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = connectionStatusText(state.status),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag("health-connect-sync-connection-status")
        )
        Text(
            text = connectionDescriptionText(state.status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        HorizontalDivider()
        Text(
            text = stringResource(R.string.settings_health_connect_sync_items),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        ListItem(
            modifier = Modifier.testTag("health-connect-weight-sync-row"),
            headlineContent = {
                Text(stringResource(R.string.settings_health_connect_sync_weight))
            },
            supportingContent = {
                Text(stringResource(R.string.settings_health_connect_sync_weight_desc))
            },
            trailingContent = {
                Switch(
                    modifier = Modifier.testTag("health-connect-weight-sync-switch"),
                    checked = settings.healthConnectWeightSyncEnabled,
                    onCheckedChange = onWeightSyncEnabledChange
                )
            }
        )
        if (state.lastWeightKg != null && state.lastAdoptedAt != null) {
            Text(
                text = stringResource(
                    R.string.settings_health_connect_sync_last,
                    state.lastWeightKg,
                    dateFormatter.format(state.lastAdoptedAt)
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("health-connect-sync-last")
            )
        }
        if (state.status == HealthConnectWeightSyncStatus.NO_DATA) {
            Text(
                text = stringResource(R.string.settings_health_connect_sync_no_data),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("health-connect-sync-no-data")
            )
        }

        ListItem(
            modifier = Modifier.testTag("health-connect-medication-sync-row"),
            headlineContent = {
                Text(stringResource(R.string.settings_health_connect_sync_medication))
            },
            supportingContent = {
                Text(stringResource(R.string.settings_health_connect_sync_coming_soon))
            },
            trailingContent = {
                Switch(
                    modifier = Modifier.testTag("health-connect-medication-sync-switch"),
                    checked = false,
                    onCheckedChange = null,
                    enabled = false
                )
            }
        )

        HorizontalDivider()
        Text(
            text = stringResource(R.string.settings_health_connect_sync_permissions),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = permissionStatusText(state.status),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag("health-connect-weight-permission-status")
        )
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("health-connect-manage-permissions"),
            onClick = onManagePermissions
        ) {
            Text(stringResource(R.string.settings_health_connect_sync_manage_permissions))
        }
        if (
            settings.healthConnectWeightSyncEnabled &&
            state.status == HealthConnectWeightSyncStatus.PERMISSION_REQUIRED
        ) {
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("health-connect-reauthorize"),
                onClick = onReauthorize
            ) {
                Text(stringResource(R.string.settings_health_connect_sync_reauthorize))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_health_connect_sync_deferred_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun connectionStatusText(status: HealthConnectWeightSyncStatus): String =
    when (status) {
        HealthConnectWeightSyncStatus.DISABLED ->
            stringResource(R.string.settings_health_connect_sync_status_disabled)
        HealthConnectWeightSyncStatus.CHECKING ->
            stringResource(R.string.settings_health_connect_sync_status_checking)
        HealthConnectWeightSyncStatus.SYNCING ->
            stringResource(R.string.settings_health_connect_sync_status_syncing)
        HealthConnectWeightSyncStatus.CONNECTED,
        HealthConnectWeightSyncStatus.NO_DATA ->
            stringResource(R.string.settings_health_connect_sync_status_connected)
        HealthConnectWeightSyncStatus.PERMISSION_REQUIRED ->
            stringResource(R.string.settings_health_connect_sync_status_permission)
        HealthConnectWeightSyncStatus.UNAVAILABLE ->
            stringResource(R.string.settings_health_connect_sync_status_unavailable)
        HealthConnectWeightSyncStatus.UPDATE_REQUIRED ->
            stringResource(R.string.settings_health_connect_sync_status_update_required)
        HealthConnectWeightSyncStatus.ERROR ->
            stringResource(R.string.settings_health_connect_sync_status_error)
    }

@Composable
private fun connectionDescriptionText(status: HealthConnectWeightSyncStatus): String =
    when (status) {
        HealthConnectWeightSyncStatus.CONNECTED,
        HealthConnectWeightSyncStatus.NO_DATA ->
            stringResource(R.string.settings_health_connect_sync_connected_desc)
        HealthConnectWeightSyncStatus.PERMISSION_REQUIRED ->
            stringResource(R.string.settings_health_connect_sync_permission_desc)
        HealthConnectWeightSyncStatus.UNAVAILABLE ->
            stringResource(R.string.settings_health_connect_sync_unavailable_desc)
        HealthConnectWeightSyncStatus.UPDATE_REQUIRED ->
            stringResource(R.string.settings_health_connect_sync_update_required_desc)
        else -> stringResource(R.string.settings_health_connect_sync_status_desc)
    }

@Composable
private fun permissionStatusText(status: HealthConnectWeightSyncStatus): String =
    when (status) {
        HealthConnectWeightSyncStatus.CONNECTED,
        HealthConnectWeightSyncStatus.CHECKING,
        HealthConnectWeightSyncStatus.SYNCING,
        HealthConnectWeightSyncStatus.NO_DATA ->
            stringResource(R.string.settings_health_connect_sync_permission_granted)
        else -> stringResource(R.string.settings_health_connect_sync_permission_not_granted)
    }
