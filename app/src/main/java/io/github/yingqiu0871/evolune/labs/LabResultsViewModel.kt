package io.github.yingqiu0871.evolune.labs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LabResultRepository
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID

/** What the lab results list shows (PK 2.0 slice 3). */
sealed interface LabResultsListState {
    data object Loading : LabResultsListState
    data class Content(val results: List<LabResult>) : LabResultsListState
    data object Error : LabResultsListState
}

sealed interface LabResultOperationState {
    data object Idle : LabResultOperationState
    data object Running : LabResultOperationState
    /** The editor can close: the change is stored. */
    data object Saved : LabResultOperationState
    data object Deleted : LabResultOperationState
    data class Invalid(val issue: LabResultDraftIssue) : LabResultOperationState
    /** The result changed or disappeared since the editor opened. */
    data object Stale : LabResultOperationState
    data object StorageFailure : LabResultOperationState
}

class LabResultsViewModel internal constructor(
    private val repository: LabResultRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val idSupplier: () -> UUID = UUID::randomUUID,
    operationScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = operationScope ?: viewModelScope

    val listState: StateFlow<LabResultsListState> = repository.observeAll()
        .map<List<LabResult>, LabResultsListState> { LabResultsListState.Content(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(LabResultsListState.Error)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LabResultsListState.Loading)

    private val _operation = MutableStateFlow<LabResultOperationState>(LabResultOperationState.Idle)
    val operation: StateFlow<LabResultOperationState> = _operation.asStateFlow()

    fun newDraft(): LabResultDraft = LabResultDraft(
        measuredAt = clock.instant(),
        valueText = "",
        unit = LabUnit.PG_PER_ML
    )

    fun save(draft: LabResultDraft) {
        if (_operation.value == LabResultOperationState.Running) return
        val valid = when (val validation = LabResultDraftValidator.validate(draft, clock.instant())) {
            is LabResultDraftValidation.Invalid -> {
                _operation.value = LabResultOperationState.Invalid(validation.issue)
                return
            }
            is LabResultDraftValidation.Valid -> validation
        }
        _operation.value = LabResultOperationState.Running
        scope.launch {
            _operation.value = runStorage {
                val id = draft.id
                val revision = draft.revision
                if (id == null || revision == null) {
                    val result = LabResult(idSupplier(), valid.measuredAt, valid.value, valid.unit)
                    when (repository.insert(result)) {
                        InsertResult.Inserted, InsertResult.Idempotent -> LabResultOperationState.Saved
                        InsertResult.Conflict -> LabResultOperationState.Stale
                        InsertResult.Invalid -> LabResultOperationState.StorageFailure
                    }
                } else {
                    val result = LabResult(id, valid.measuredAt, valid.value, valid.unit, revision)
                    when (repository.update(result, expectedRevision = revision)) {
                        UpdateResult.Updated, UpdateResult.NoChange -> LabResultOperationState.Saved
                        UpdateResult.NotFound, UpdateResult.RevisionConflict -> LabResultOperationState.Stale
                        UpdateResult.Invalid -> LabResultOperationState.StorageFailure
                    }
                }
            }
        }
    }

    fun delete(result: LabResult) {
        if (_operation.value == LabResultOperationState.Running) return
        _operation.value = LabResultOperationState.Running
        scope.launch {
            _operation.value = runStorage {
                when (repository.deleteIfRevisionMatches(result.id, result.revision)) {
                    ConditionalDeleteResult.Deleted -> LabResultOperationState.Deleted
                    ConditionalDeleteResult.NotFound,
                    ConditionalDeleteResult.RevisionConflict -> LabResultOperationState.Stale
                    ConditionalDeleteResult.Invalid -> LabResultOperationState.StorageFailure
                }
            }
        }
    }

    /** The editor calls this once it has reacted to a terminal state. */
    fun acknowledgeOperation() {
        if (_operation.value != LabResultOperationState.Running) {
            _operation.value = LabResultOperationState.Idle
        }
    }

    private suspend fun runStorage(block: suspend () -> LabResultOperationState): LabResultOperationState =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            LabResultOperationState.StorageFailure
        }
}

class LabResultsViewModelFactory(
    private val repository: LabResultRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LabResultsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LabResultsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
