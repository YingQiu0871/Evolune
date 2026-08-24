package io.github.yingqiu0871.evolune.backup

import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationResolution
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationOutcome
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupGeneration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface BackupRestoreUiState {
    data object Idle : BackupRestoreUiState
    data class Authorizing(val operation: BackupRestoreOperation) : BackupRestoreUiState
    data object AwaitingBackupPassphrase : BackupRestoreUiState
    data object PreparingBackup : BackupRestoreUiState
    data object Uploading : BackupRestoreUiState
    data class BackupSuccess(val cleanupPending: Boolean) : BackupRestoreUiState
    data class LoadingBackups(val operation: BackupRestoreOperation = BackupRestoreOperation.RESTORE) :
        BackupRestoreUiState
    data class SelectingBackup(val generations: List<CloudBackupGeneration>) : BackupRestoreUiState
    data class AwaitingRestorePassphrase(val generation: CloudBackupGeneration) : BackupRestoreUiState
    data object PreparingRestorePreview : BackupRestoreUiState
    data class Preview(val preview: BackupRestorePreview) : BackupRestoreUiState
    data object Restoring : BackupRestoreUiState
    data object RestoreSuccess : BackupRestoreUiState
    data object RestoreSuccessRefreshWarning : BackupRestoreUiState
    data class Error(val error: BackupRestoreError) : BackupRestoreUiState
}

sealed interface BackupRestoreUiEvent {
    data class LaunchAuthorization(val operation: BackupRestoreOperation, val resolution: AuthorizationResolution) :
        BackupRestoreUiEvent
}

class BackupRestoreViewModel internal constructor(
    private val coordinator: BackupRestoreCoordinator
) : ViewModel() {
    private val _uiState = MutableStateFlow<BackupRestoreUiState>(BackupRestoreUiState.Idle)
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val events = Channel<BackupRestoreUiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    private var operationJob: Job? = null
    private var pendingAuthorizationOperation: BackupRestoreOperation? = null
    private var pendingRestore: PreparedRestore? = null
    private var selectedGeneration: CloudBackupGeneration? = null

    fun backUpNow() {
        authorize(BackupRestoreOperation.BACKUP)
    }

    fun restoreFromBackup() {
        authorize(BackupRestoreOperation.RESTORE)
    }

    fun onAuthorizationOutcome(outcome: CloudAuthorizationOutcome) {
        val operation = pendingAuthorizationOperation ?: return
        operationJob = null
        when (outcome) {
            is CloudAuthorizationOutcome.Authorized -> {
                _connected.value = true
                pendingAuthorizationOperation = null
                if (operation == BackupRestoreOperation.BACKUP) {
                    _uiState.value = BackupRestoreUiState.AwaitingBackupPassphrase
                } else {
                    loadBackupsAfterAuthorization()
                }
            }
            CloudAuthorizationOutcome.Cancelled -> {
                pendingAuthorizationOperation = null
                _uiState.value = BackupRestoreUiState.Error(
                    BackupRestoreError(operation, BackupRestoreErrorCode.AUTHORIZATION_CANCELLED)
                )
            }
            CloudAuthorizationOutcome.Unavailable,
            is CloudAuthorizationOutcome.Error -> {
                pendingAuthorizationOperation = null
                _uiState.value = BackupRestoreUiState.Error(
                    BackupRestoreError(operation, BackupRestoreErrorCode.AUTHORIZATION_FAILED)
                )
            }
            is CloudAuthorizationOutcome.UserResolutionRequired -> {
                events.trySend(BackupRestoreUiEvent.LaunchAuthorization(operation, outcome.resolution))
            }
        }
    }

    fun submitBackupPassphrase(passphrase: CharArray, confirmation: CharArray) {
        if (_uiState.value != BackupRestoreUiState.AwaitingBackupPassphrase || operationJob?.isActive == true) {
            clear(passphrase)
            clear(confirmation)
            return
        }
        _uiState.value = BackupRestoreUiState.PreparingBackup
        operationJob = viewModelScope.launch {
            try {
                val result = coordinator.createBackup(passphrase, confirmation)
                _uiState.value = when (result) {
                    BackupCreationResult.InvalidPassphrase -> BackupRestoreUiState.AwaitingBackupPassphrase
                    BackupCreationResult.InvalidLocalData -> BackupRestoreUiState.Error(
                        BackupRestoreError(
                            BackupRestoreOperation.BACKUP,
                            BackupRestoreErrorCode.LOCAL_DATA_INVALID
                        )
                    )
                    is BackupCreationResult.AuthorizationRequired -> {
                        pendingAuthorizationOperation = BackupRestoreOperation.BACKUP
                        events.trySend(
                            BackupRestoreUiEvent.LaunchAuthorization(
                                BackupRestoreOperation.BACKUP,
                                result.resolution
                            )
                        )
                        BackupRestoreUiState.Authorizing(BackupRestoreOperation.BACKUP)
                    }
                    is BackupCreationResult.Success -> {
                        _connected.value = true
                        BackupRestoreUiState.BackupSuccess(result.cleanupPending)
                    }
                    is BackupCreationResult.Failure -> BackupRestoreUiState.Error(result.error)
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                clear(passphrase)
                clear(confirmation)
            }
        }
    }

    fun selectGeneration(generation: CloudBackupGeneration) {
        if (_uiState.value !is BackupRestoreUiState.SelectingBackup) return
        selectedGeneration = generation
        _uiState.value = BackupRestoreUiState.AwaitingRestorePassphrase(generation)
    }

    fun submitRestorePassphrase(passphrase: CharArray) {
        val generation = selectedGeneration
            ?: return clear(passphrase)
        if (_uiState.value !is BackupRestoreUiState.AwaitingRestorePassphrase || operationJob?.isActive == true) {
            clear(passphrase)
            return
        }
        _uiState.value = BackupRestoreUiState.PreparingRestorePreview
        operationJob = viewModelScope.launch {
            try {
                when (val result = coordinator.prepareRestore(generation, passphrase)) {
                    RestorePreparationResult.InvalidPassphrase ->
                        _uiState.value = BackupRestoreUiState.AwaitingRestorePassphrase(generation)
                    is RestorePreparationResult.AuthorizationRequired -> {
                        pendingAuthorizationOperation = BackupRestoreOperation.RESTORE
                        events.trySend(
                            BackupRestoreUiEvent.LaunchAuthorization(
                                BackupRestoreOperation.RESTORE,
                                result.resolution
                            )
                        )
                        _uiState.value = BackupRestoreUiState.Authorizing(BackupRestoreOperation.RESTORE)
                    }
                    is RestorePreparationResult.Success -> {
                        pendingRestore = result.prepared
                        _uiState.value = BackupRestoreUiState.Preview(result.preview)
                    }
                    is RestorePreparationResult.Failure -> _uiState.value =
                        BackupRestoreUiState.Error(result.error)
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                clear(passphrase)
            }
        }
    }

    fun confirmRestore() {
        val prepared = pendingRestore ?: return
        if (_uiState.value !is BackupRestoreUiState.Preview || operationJob?.isActive == true) return
        _uiState.value = BackupRestoreUiState.Restoring
        operationJob = viewModelScope.launch {
            when (val result = coordinator.confirmRestore(prepared)) {
                RestoreCompletionResult.Success -> {
                    pendingRestore = null
                    selectedGeneration = null
                    _uiState.value = BackupRestoreUiState.RestoreSuccess
                }
                RestoreCompletionResult.SuccessWithRefreshWarning -> {
                    pendingRestore = null
                    selectedGeneration = null
                    _uiState.value = BackupRestoreUiState.RestoreSuccessRefreshWarning
                }
                is RestoreCompletionResult.Failure -> _uiState.value =
                    BackupRestoreUiState.Error(result.error)
            }
        }
    }

    fun disconnect() {
        if (operationJob?.isActive == true || _uiState.value !is BackupRestoreUiState.Idle) return
        operationJob = viewModelScope.launch {
            if (coordinator.disconnect()) {
                _connected.value = false
                _uiState.value = BackupRestoreUiState.Idle
            } else {
                _uiState.value = BackupRestoreUiState.Error(
                    BackupRestoreError(
                        BackupRestoreOperation.BACKUP,
                        BackupRestoreErrorCode.DISCONNECT_FAILED
                    )
                )
            }
        }
    }

    fun cancelInteractiveOperation() {
        operationJob?.cancel()
        operationJob = null
        pendingAuthorizationOperation = null
        pendingRestore = null
        selectedGeneration = null
        _uiState.value = BackupRestoreUiState.Idle
    }

    fun dismissMessage() {
        if (_uiState.value is BackupRestoreUiState.Error ||
            _uiState.value is BackupRestoreUiState.BackupSuccess ||
            _uiState.value is BackupRestoreUiState.RestoreSuccess ||
            _uiState.value is BackupRestoreUiState.RestoreSuccessRefreshWarning
        ) {
            _uiState.value = BackupRestoreUiState.Idle
        }
    }

    private fun authorize(operation: BackupRestoreOperation) {
        if (operationJob?.isActive == true || _uiState.value !is BackupRestoreUiState.Idle) return
        pendingAuthorizationOperation = operation
        _uiState.value = BackupRestoreUiState.Authorizing(operation)
        operationJob = viewModelScope.launch {
            when (val result = coordinator.authorizeFor(operation)) {
                AuthorizationGateResult.Authorized -> {
                    _connected.value = true
                    pendingAuthorizationOperation = null
                    operationJob = null
                    if (operation == BackupRestoreOperation.BACKUP) {
                        _uiState.value = BackupRestoreUiState.AwaitingBackupPassphrase
                    } else {
                        loadBackupsAfterAuthorization()
                    }
                }
                is AuthorizationGateResult.ResolutionRequired -> {
                    events.send(BackupRestoreUiEvent.LaunchAuthorization(operation, result.resolution))
                }
                AuthorizationGateResult.Cancelled -> {
                    pendingAuthorizationOperation = null
                    _uiState.value = BackupRestoreUiState.Error(
                        BackupRestoreError(operation, BackupRestoreErrorCode.AUTHORIZATION_CANCELLED)
                    )
                }
                is AuthorizationGateResult.Failure -> {
                    pendingAuthorizationOperation = null
                    _uiState.value = BackupRestoreUiState.Error(
                        BackupRestoreError(operation, result.code)
                    )
                }
            }
            if (operationJob === coroutineContext[Job]) {
                operationJob = null
            }
        }
    }

    private fun loadBackupsAfterAuthorization() {
        _uiState.value = BackupRestoreUiState.LoadingBackups()
        operationJob = viewModelScope.launch {
            when (val result = coordinator.listBackups()) {
                BackupListResult.NoBackups -> _uiState.value = BackupRestoreUiState.Error(
                    BackupRestoreError(
                        BackupRestoreOperation.RESTORE,
                        BackupRestoreErrorCode.NO_BACKUPS
                    )
                )
                is BackupListResult.Success -> _uiState.value =
                    BackupRestoreUiState.SelectingBackup(result.generations)
                is BackupListResult.AuthorizationRequired -> {
                    pendingAuthorizationOperation = BackupRestoreOperation.RESTORE
                    events.send(
                        BackupRestoreUiEvent.LaunchAuthorization(
                            BackupRestoreOperation.RESTORE,
                            result.resolution
                        )
                    )
                    _uiState.value = BackupRestoreUiState.Authorizing(BackupRestoreOperation.RESTORE)
                }
                is BackupListResult.Failure -> _uiState.value = BackupRestoreUiState.Error(result.error)
            }
            if (operationJob === coroutineContext[Job]) {
                operationJob = null
            }
        }
    }

    private fun clear(value: CharArray) {
        value.fill('\u0000')
    }
}

class BackupRestoreViewModelFactory internal constructor(
    private val coordinator: BackupRestoreCoordinator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupRestoreViewModel::class.java)) {
            return BackupRestoreViewModel(coordinator) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
