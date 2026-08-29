package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.backup.BackupRestoreUiState
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupGeneration
import io.github.yingqiu0871.evolune.ui.components.BackupRestoreSection

@Composable
fun GoogleDriveBackupRestoreScreen(
    connected: Boolean,
    state: BackupRestoreUiState,
    onBackupNow: () -> Unit,
    onRestoreFromBackup: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectGeneration: (CloudBackupGeneration) -> Unit,
    onSubmitBackupPassphrase: (CharArray, CharArray) -> Unit,
    onSubmitRestorePassphrase: (CharArray) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancel: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BackupRestoreSection(
            connected = connected,
            state = state,
            onBackupNow = onBackupNow,
            onRestoreFromBackup = onRestoreFromBackup,
            onDisconnect = onDisconnect,
            onSelectGeneration = onSelectGeneration,
            onSubmitBackupPassphrase = onSubmitBackupPassphrase,
            onSubmitRestorePassphrase = onSubmitRestorePassphrase,
            onConfirmRestore = onConfirmRestore,
            onCancel = onCancel,
            onDismissMessage = onDismissMessage
        )
    }
}
