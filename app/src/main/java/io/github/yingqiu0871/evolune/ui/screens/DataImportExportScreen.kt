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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.export.PortableExportFormat
import io.github.yingqiu0871.evolune.export.PortableExportRange
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

/** Typed, already-localized canonical dialog payload (V17-E §34). */
data class PortableDialogMessage(
    val title: String,
    val body: String
)

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
    onExportToClipboard: () -> Unit,
    portableBusy: Boolean = false,
    onExportPortableJson: (PortableExportRange) -> Unit = {},
    onExportPortableCsv: (PortableExportRange) -> Unit = {},
    onImportPortableJson: () -> Unit = {},
    portableDialog: PortableDialogMessage? = null,
    onDismissPortableDialog: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var rangeDialogFormat by remember { mutableStateOf<PortableExportFormat?>(null) }
    var showClipboardWarning by remember { mutableStateOf(false) }

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
            PortableSection(
                busy = portableBusy,
                onExportJsonClick = { rangeDialogFormat = PortableExportFormat.JSON },
                onExportCsvClick = { rangeDialogFormat = PortableExportFormat.CSV },
                onImportJsonClick = onImportPortableJson
            )
            if (portableBusy) {
                Text(
                    text = stringResource(R.string.portable_busy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag("portable-busy")
                )
            }
            LegacySection(
                busy = portableBusy,
                onImportClick = onImportClick,
                onImportFromClipboard = onImportFromClipboard,
                onExportClick = onExportClick,
                onExportToClipboard = { showClipboardWarning = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        rangeDialogFormat?.let { format ->
            ExportRangeDialog(
                onChoose = { range ->
                    rangeDialogFormat = null
                    when (format) {
                        PortableExportFormat.JSON -> onExportPortableJson(range)
                        PortableExportFormat.CSV -> onExportPortableCsv(range)
                    }
                },
                onDismiss = { rangeDialogFormat = null }
            )
        }

        if (showClipboardWarning) {
            AlertDialog(
                modifier = Modifier.testTag("legacy-clipboard-warning"),
                onDismissRequest = { showClipboardWarning = false },
                title = { Text(stringResource(R.string.portable_clipboard_warning_title)) },
                text = { Text(stringResource(R.string.portable_clipboard_warning_message)) },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag("legacy-clipboard-confirm"),
                        onClick = {
                            showClipboardWarning = false
                            onExportToClipboard()
                        }
                    ) {
                        Text(stringResource(R.string.common_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.testTag("legacy-clipboard-cancel"),
                        onClick = { showClipboardWarning = false }
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        portableDialog?.let { dialog ->
            ImportResultDialog(
                title = dialog.title,
                message = dialog.body,
                onDismiss = onDismissPortableDialog
            )
        }

        when (val result = importResult) {
            is ImportResult.Success -> ImportResultDialog(
                title = stringResource(R.string.settings_import_json),
                message = stringResource(R.string.import_success, result.importedCount),
                onDismiss = onDismissImportResult
            )
            is ImportResult.Error -> ImportResultDialog(
                title = stringResource(R.string.settings_import_json),
                message = if (result.tooLarge) {
                    stringResource(R.string.portable_import_too_large)
                } else {
                    stringResource(R.string.import_error, result.message)
                },
                onDismiss = onDismissImportResult
            )
            ImportResult.Idle,
            ImportResult.Importing -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PortableSection(
    busy: Boolean,
    onExportJsonClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onImportJsonClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.portable_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedListItem(
                modifier = Modifier.testTag("portable-export-json"),
                onClick = { if (!busy) onExportJsonClick() },
                shapes = stableSegmentedShapes(index = 0, count = 3),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.Upload, null) },
                supportingContent = { Text(stringResource(R.string.portable_export_json_desc)) }
            ) { Text(stringResource(R.string.portable_export_json)) }
            SegmentedListItem(
                modifier = Modifier.testTag("portable-export-csv"),
                onClick = { if (!busy) onExportCsvClick() },
                shapes = stableSegmentedShapes(index = 1, count = 3),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.Upload, null) },
                supportingContent = { Text(stringResource(R.string.portable_export_csv_desc)) }
            ) { Text(stringResource(R.string.portable_export_csv)) }
            SegmentedListItem(
                modifier = Modifier.testTag("portable-import-json"),
                onClick = { if (!busy) onImportJsonClick() },
                shapes = stableSegmentedShapes(index = 2, count = 3),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                supportingContent = { Text(stringResource(R.string.portable_import_json_desc)) }
            ) { Text(stringResource(R.string.portable_import_json)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LegacySection(
    busy: Boolean,
    onImportClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onExportClick: () -> Unit,
    onExportToClipboard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_data_legacy_title),
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
                onClick = { if (!busy) onImportClick() },
                shapes = stableSegmentedShapes(index = 0, count = 4),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                supportingContent = { Text(stringResource(R.string.settings_import_json_desc)) }
            ) { Text(stringResource(R.string.settings_import_json)) }
            SegmentedListItem(
                modifier = Modifier.testTag("settings-import-clipboard"),
                onClick = { if (!busy) onImportFromClipboard() },
                shapes = stableSegmentedShapes(index = 1, count = 4),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.ContentPaste, null) },
                supportingContent = { Text(stringResource(R.string.settings_import_clipboard_desc)) }
            ) { Text(stringResource(R.string.settings_import_clipboard)) }
            SegmentedListItem(
                modifier = Modifier.testTag("settings-export-json"),
                onClick = { if (!busy) onExportClick() },
                shapes = stableSegmentedShapes(index = 2, count = 4),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.Upload, null) },
                supportingContent = { Text(stringResource(R.string.settings_export_json_desc)) }
            ) { Text(stringResource(R.string.settings_export_json)) }
            SegmentedListItem(
                modifier = Modifier.testTag("settings-export-clipboard"),
                onClick = { if (!busy) onExportToClipboard() },
                shapes = stableSegmentedShapes(index = 3, count = 4),
                colors = settingsListItemColors(),
                leadingContent = { Icon(Icons.Outlined.ContentCopy, null) },
                supportingContent = { Text(stringResource(R.string.settings_export_clipboard_desc)) }
            ) { Text(stringResource(R.string.settings_export_clipboard)) }
        }
    }
}

@Composable
private fun ExportRangeDialog(
    onChoose: (PortableExportRange) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("portable-range-dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.portable_range_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(
                    Triple("portable-range-30d", R.string.portable_range_30_days, PortableExportRange.LAST_30_DAYS),
                    Triple("portable-range-90d", R.string.portable_range_90_days, PortableExportRange.LAST_90_DAYS),
                    Triple("portable-range-all", R.string.portable_range_all, PortableExportRange.ALL)
                ).forEach { (tag, label, range) ->
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(tag),
                        onClick = { onChoose(range) }
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
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
