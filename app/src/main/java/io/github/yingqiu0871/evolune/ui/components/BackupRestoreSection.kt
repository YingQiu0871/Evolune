package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.backup.BackupRestoreErrorCode
import io.github.yingqiu0871.evolune.backup.BackupRestoreOperation
import io.github.yingqiu0871.evolune.backup.BackupRestorePreview
import io.github.yingqiu0871.evolune.backup.BackupRestoreUiState
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupGeneration

@Composable
fun BackupRestoreSection(
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_backup_restore_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(R.string.settings_backup_restore_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(R.string.settings_backup_restore_google_drive),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(
                if (connected) {
                    R.string.settings_backup_restore_connected
                } else {
                    R.string.settings_backup_restore_authorization_required
                }
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onBackupNow,
                enabled = state == BackupRestoreUiState.Idle,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_backup_now))
            }
            Button(
                onClick = onRestoreFromBackup,
                enabled = state == BackupRestoreUiState.Idle,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_restore_from_backup))
            }
        }
        if (connected) {
            TextButton(
                onClick = onDisconnect,
                enabled = state == BackupRestoreUiState.Idle
            ) {
                Text(stringResource(R.string.settings_disconnect_google_drive))
            }
        }

        when (state) {
            is BackupRestoreUiState.BackupSuccess -> {
                Text(
                    stringResource(
                        if (state.cleanupPending) {
                            R.string.settings_backup_success_cleanup_pending
                        } else {
                            R.string.settings_backup_success
                        }
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                TextButton(onClick = onDismissMessage) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
            BackupRestoreUiState.RestoreSuccess -> {
                Text(
                    stringResource(R.string.settings_restore_success),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                TextButton(onClick = onDismissMessage) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
            BackupRestoreUiState.RestoreSuccessRefreshWarning -> {
                Text(
                    stringResource(R.string.settings_restore_success_refresh_warning),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                TextButton(onClick = onDismissMessage) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
            is BackupRestoreUiState.Error -> {
                Text(
                    backupRestoreErrorText(state.error.code),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                TextButton(onClick = onDismissMessage) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
            else -> Unit
        }
    }

    when (val current = state) {
        BackupRestoreUiState.AwaitingBackupPassphrase -> PassphraseDialog(
            operation = BackupRestoreOperation.BACKUP,
            onSubmitBackup = onSubmitBackupPassphrase,
            onSubmitRestore = onSubmitRestorePassphrase,
            onCancel = onCancel
        )
        is BackupRestoreUiState.SelectingBackup -> GenerationPickerDialog(
            generations = current.generations,
            onSelect = onSelectGeneration,
            onCancel = onCancel
        )
        is BackupRestoreUiState.AwaitingRestorePassphrase -> PassphraseDialog(
            operation = BackupRestoreOperation.RESTORE,
            onSubmitBackup = onSubmitBackupPassphrase,
            onSubmitRestore = onSubmitRestorePassphrase,
            onCancel = onCancel
        )
        is BackupRestoreUiState.Preview -> RestorePreviewDialog(
            preview = current.preview,
            onConfirm = onConfirmRestore,
            onCancel = onCancel
        )
        else -> Unit
    }
}

@Composable
private fun PassphraseDialog(
    operation: BackupRestoreOperation,
    onSubmitBackup: (CharArray, CharArray) -> Unit,
    onSubmitRestore: (CharArray) -> Unit,
    onCancel: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val isBackup = operation == BackupRestoreOperation.BACKUP
    val canSubmit = passphrase.isNotEmpty() && (!isBackup || passphrase == confirmation)

    AlertDialog(
        onDismissRequest = {
            passphrase = ""
            confirmation = ""
            onCancel()
        },
        title = {
            Text(
                stringResource(
                    if (isBackup) {
                        R.string.settings_backup_passphrase_title
                    } else {
                        R.string.settings_restore_passphrase_title
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (isBackup) {
                            R.string.settings_backup_passphrase_desc
                        } else {
                            R.string.settings_restore_passphrase_desc
                        }
                    )
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.settings_passphrase_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                if (isBackup) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.settings_passphrase_confirm_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        isError = confirmation.isNotEmpty() && passphrase != confirmation
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val secret = passphrase.toCharArray()
                    val confirmed = confirmation.toCharArray()
                    passphrase = ""
                    confirmation = ""
                    if (isBackup) onSubmitBackup(secret, confirmed) else onSubmitRestore(secret)
                },
                enabled = canSubmit
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                passphrase = ""
                confirmation = ""
                onCancel()
            }) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun GenerationPickerDialog(
    generations: List<CloudBackupGeneration>,
    onSelect: (CloudBackupGeneration) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_restore_choose_backup)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(generations, key = { it.id.value }) { generation ->
                    TextButton(
                        onClick = { onSelect(generation) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(generation.createdAt)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun RestorePreviewDialog(
    preview: BackupRestorePreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_restore_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_restore_preview_created, preview.createdAt))
                preview.producerAppVersionName?.let {
                    Text(stringResource(R.string.settings_restore_preview_version, it))
                }
                Text(stringResource(R.string.settings_restore_preview_plans, preview.medicationPlanCount))
                Text(stringResource(R.string.settings_restore_preview_slots, preview.scheduledDoseSlotCount))
                Text(stringResource(R.string.settings_restore_preview_events, preview.doseEventCount))
                Text(stringResource(R.string.settings_restore_preview_weight, preview.bodyWeightKg))
                Text(stringResource(R.string.settings_restore_preview_settings, preview.themeMode, preview.colorTheme))
                Text(stringResource(R.string.settings_restore_destructive_warning))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.settings_restore_and_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun backupRestoreErrorText(code: BackupRestoreErrorCode): String = when (code) {
    BackupRestoreErrorCode.AUTHORIZATION_REQUIRED -> stringResource(R.string.settings_backup_error_authorization_required)
    BackupRestoreErrorCode.AUTHORIZATION_CANCELLED -> stringResource(R.string.settings_backup_error_authorization_cancelled)
    BackupRestoreErrorCode.AUTHORIZATION_UNAVAILABLE,
    BackupRestoreErrorCode.AUTHORIZATION_FAILED -> stringResource(R.string.settings_backup_error_authorization_failed)
    BackupRestoreErrorCode.NETWORK_UNAVAILABLE -> stringResource(R.string.settings_backup_error_network)
    BackupRestoreErrorCode.NO_BACKUPS -> stringResource(R.string.settings_backup_error_no_backups)
    BackupRestoreErrorCode.BACKUP_TOO_LARGE -> stringResource(R.string.settings_backup_error_too_large)
    BackupRestoreErrorCode.LOCAL_DATA_INVALID -> stringResource(R.string.settings_backup_error_local_data)
    BackupRestoreErrorCode.BACKUP_UPLOAD_FAILED -> stringResource(R.string.settings_backup_error_upload)
    BackupRestoreErrorCode.BACKUP_VERIFICATION_FAILED -> stringResource(R.string.settings_backup_error_verification)
    BackupRestoreErrorCode.WRONG_SECRET_OR_TAMPERED -> stringResource(R.string.settings_backup_error_unlock)
    BackupRestoreErrorCode.UNSUPPORTED_FUTURE_BACKUP -> stringResource(R.string.settings_backup_error_unsupported)
    BackupRestoreErrorCode.INVALID_OR_CORRUPT_BACKUP -> stringResource(R.string.settings_backup_error_corrupt)
    BackupRestoreErrorCode.RESTORE_FAILED -> stringResource(R.string.settings_backup_error_restore)
    BackupRestoreErrorCode.RECOVERY_REQUIRED -> stringResource(R.string.settings_backup_error_recovery)
    BackupRestoreErrorCode.DISCONNECT_FAILED -> stringResource(R.string.settings_backup_error_disconnect)
}
