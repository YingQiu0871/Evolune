package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
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
import io.github.yingqiu0871.evolune.ui.components.settingsListItemColors
import io.github.yingqiu0871.evolune.ui.components.stableSegmentedShapes
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Health Connect weight source, shown next to the body weight it feeds. Presentation only:
 * permission handling, consent flows and the existing SettingsDataStore owner are unchanged.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val showReauthorize = settings.healthConnectWeightSyncEnabled &&
        state.status == HealthConnectWeightSyncStatus.PERMISSION_REQUIRED
    val rowCount = if (showReauthorize) 3 else 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-health-connect-section"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SegmentedListItem(
            modifier = Modifier.testTag("health-connect-weight-sync-row"),
            onClick = { onWeightSyncEnabledChange(!settings.healthConnectWeightSyncEnabled) },
            shapes = stableSegmentedShapes(index = 0, count = rowCount),
            colors = settingsListItemColors(),
            leadingContent = { Icon(Icons.Outlined.MonitorWeight, contentDescription = null) },
            trailingContent = {
                Switch(
                    modifier = Modifier.testTag("health-connect-weight-sync-switch"),
                    checked = settings.healthConnectWeightSyncEnabled,
                    onCheckedChange = onWeightSyncEnabledChange
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.settings_health_connect_sync_weight_desc))
                    Text(
                        text = connectionStatusText(state.status) + " · " +
                            connectionDescriptionText(state.status),
                        modifier = Modifier.testTag("health-connect-sync-connection-status")
                    )
                    if (state.lastWeightKg != null && state.lastAdoptedAt != null) {
                        Text(
                            text = stringResource(
                                R.string.settings_health_connect_sync_last,
                                state.lastWeightKg,
                                dateFormatter.format(state.lastAdoptedAt)
                            ),
                            modifier = Modifier.testTag("health-connect-sync-last")
                        )
                    }
                    if (state.status == HealthConnectWeightSyncStatus.NO_DATA) {
                        Text(
                            text = stringResource(R.string.settings_health_connect_sync_no_data),
                            modifier = Modifier.testTag("health-connect-sync-no-data")
                        )
                    }
                }
            }
        ) { Text(stringResource(R.string.settings_health_connect_sync_weight)) }

        if (showReauthorize) {
            SegmentedListItem(
                modifier = Modifier.testTag("health-connect-reauthorize"),
                onClick = onReauthorize,
                shapes = stableSegmentedShapes(index = 1, count = rowCount),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) }
            ) { Text(stringResource(R.string.settings_health_connect_sync_reauthorize)) }
        }

        SegmentedListItem(
            modifier = Modifier.testTag("health-connect-manage-permissions"),
            onClick = onManagePermissions,
            shapes = stableSegmentedShapes(index = rowCount - 1, count = rowCount),
            colors = settingsListItemColors(),
            leadingContent = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null) },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
            supportingContent = {
                Text(
                    text = permissionStatusText(state.status),
                    modifier = Modifier.testTag("health-connect-weight-permission-status")
                )
            }
        ) { Text(stringResource(R.string.settings_health_connect_sync_manage_permissions)) }
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
