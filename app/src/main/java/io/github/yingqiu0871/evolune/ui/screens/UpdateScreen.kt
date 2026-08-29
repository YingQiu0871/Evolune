package io.github.yingqiu0871.evolune.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.ui.components.settingsListItemColors
import io.github.yingqiu0871.evolune.viewmodel.UpdateCheckResult
import kotlinx.coroutines.launch

private const val CLIPBOARD_LABEL_VERSION = "version"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreen(
    autoCheckUpdates: Boolean,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    updateCheckResult: UpdateCheckResult
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        UpdateSection(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings-update-screen")
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            autoCheckUpdates = autoCheckUpdates,
            onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
            onCheckForUpdates = onCheckForUpdates,
            updateCheckResult = updateCheckResult,
            snackbarHostState = snackbarHostState
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSection(
    modifier: Modifier,
    autoCheckUpdates: Boolean,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    updateCheckResult: UpdateCheckResult,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val scope = rememberCoroutineScope()
    val isChecking = updateCheckResult is UpdateCheckResult.Checking
    val checkingStatusText = when (updateCheckResult) {
        is UpdateCheckResult.Checking -> stringResource(R.string.update_checking)
        is UpdateCheckResult.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateCheckResult.Error -> stringResource(R.string.update_check_error)
        is UpdateCheckResult.UpdateAvailable ->
            stringResource(R.string.update_available_hint, updateCheckResult.tagName)
        is UpdateCheckResult.UpdateAvailableDismissed ->
            stringResource(R.string.update_available_hint, updateCheckResult.tagName)
        is UpdateCheckResult.DebugBuild ->
            stringResource(R.string.update_debug_hint, updateCheckResult.tagName)
        is UpdateCheckResult.DebugBuildDismissed ->
            stringResource(R.string.update_debug_hint, updateCheckResult.tagName)
        else -> stringResource(R.string.update_idle_hint)
    }
    val versionCopiedText = stringResource(R.string.version_copied)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_update_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedListItem(
                modifier = Modifier.testTag("settings-auto-check-updates"),
                onClick = { onAutoCheckUpdatesChange(!autoCheckUpdates) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                colors = settingsListItemColors(),
                leadingContent = {
                    Icon(imageVector = Icons.Outlined.SystemUpdate, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = autoCheckUpdates,
                        onCheckedChange = onAutoCheckUpdatesChange,
                        thumbContent = if (autoCheckUpdates) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            ) { Text(stringResource(R.string.settings_auto_check_updates)) }

            SegmentedListItem(
                modifier = Modifier.testTag("settings-check-updates-now"),
                onClick = { if (!isChecking) onCheckForUpdates() },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = settingsListItemColors(),
                leadingContent = {
                    if (isChecking) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                    }
                },
                supportingContent = { Text(checkingStatusText) }
            ) {
                Text(
                    text = stringResource(R.string.settings_check_updates_now),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                        alpha = if (isChecking) 0.38f else 1f
                    )
                )
            }

            SegmentedListItem(
                modifier = Modifier.testTag("settings-current-version"),
                onClick = {
                    val clipboardManager =
                        context.getSystemService(ClipboardManager::class.java)
                    val clip = ClipData.newPlainText(CLIPBOARD_LABEL_VERSION, currentVersion)
                    clipboardManager?.setPrimaryClip(clip)
                    scope.launch { snackbarHostState.showSnackbar(versionCopiedText) }
                },
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                colors = settingsListItemColors(),
                leadingContent = {
                    Icon(imageVector = Icons.Outlined.PhoneAndroid, contentDescription = null)
                },
                trailingContent = {
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null)
                },
                supportingContent = { Text(currentVersion) }
            ) { Text(stringResource(R.string.settings_current_version)) }
        }
    }
}
