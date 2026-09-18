package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.export.PortableExportRange
import io.github.yingqiu0871.evolune.ui.screens.settings.PortableDialogMessage
import io.github.yingqiu0871.evolune.ui.screens.settings.SettingsImportExportBlock
import io.github.yingqiu0871.evolune.viewmodel.ImportResult

/** Scrollable host mirroring the inline Settings layout for section-level Compose tests. */
@Composable
internal fun TestScrollHost(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        content = content
    )
}

/** Host for the inline import/export block: supplies the shared snackbar host the screen owns. */
@Composable
internal fun TestDataImportExportHost(
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
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
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
}
