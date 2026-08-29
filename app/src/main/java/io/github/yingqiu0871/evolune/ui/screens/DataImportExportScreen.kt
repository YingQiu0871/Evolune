package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.ui.components.settingsListItemColors
import io.github.yingqiu0871.evolune.ui.components.stableSegmentedShapes
import io.github.yingqiu0871.evolune.viewmodel.ImportResult
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DataImportExportScreen(
    importResult: ImportResult,
    onDismissImportResult: () -> Unit,
    clipboardExportMessage: String?,
    onClipboardExportMessageShown: () -> Unit,
    onImportClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onExportClick: () -> Unit,
    onExportToClipboard: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(clipboardExportMessage) {
        if (clipboardExportMessage != null) {
            snackbarHostState.showSnackbar(clipboardExportMessage)
            onClipboardExportMessageShown()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DataSection(
                onImportClick = onImportClick,
                onImportFromClipboard = onImportFromClipboard,
                onExportClick = onExportClick,
                onExportToClipboard = onExportToClipboard
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (val result = importResult) {
            is ImportResult.Success -> ImportResultDialog(
                title = stringResource(R.string.settings_import_json),
                message = stringResource(R.string.import_success, result.importedCount),
                onDismiss = onDismissImportResult
            )
            is ImportResult.Error -> ImportResultDialog(
                title = stringResource(R.string.settings_import_json),
                message = stringResource(R.string.import_error, result.message),
                onDismiss = onDismissImportResult
            )
            ImportResult.Idle,
            ImportResult.Importing -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DataSection(
    onImportClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onExportClick: () -> Unit,
    onExportToClipboard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_data_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedListItem(
                modifier = Modifier.testTag("settings-import-json"),
                onClick = onImportClick,
                shapes = stableSegmentedShapes(index = 0, count = 4),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                supportingContent = { Text(stringResource(R.string.settings_import_json_desc)) }
            ) { Text(stringResource(R.string.settings_import_json)) }
            SegmentedListItem(
                modifier = Modifier.testTag("settings-import-clipboard"),
                onClick = onImportFromClipboard,
                shapes = stableSegmentedShapes(index = 1, count = 4),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.ContentPaste, null) },
                supportingContent = { Text(stringResource(R.string.settings_import_clipboard_desc)) }
            ) { Text(stringResource(R.string.settings_import_clipboard)) }
            SegmentedListItem(
                modifier = Modifier.testTag("settings-export-json"),
                onClick = onExportClick,
                shapes = stableSegmentedShapes(index = 2, count = 4),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.Upload, null) },
                supportingContent = { Text(stringResource(R.string.settings_export_json_desc)) }
            ) { Text(stringResource(R.string.settings_export_json)) }
            SegmentedListItem(
                modifier = Modifier.testTag("settings-export-clipboard"),
                onClick = onExportToClipboard,
                shapes = stableSegmentedShapes(index = 3, count = 4),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.ContentCopy, null) },
                supportingContent = { Text(stringResource(R.string.settings_export_clipboard_desc)) }
            ) { Text(stringResource(R.string.settings_export_clipboard)) }
        }
    }
}

@Composable
private fun ImportResultDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_confirm))
            }
        }
    )
}
