package io.github.yuninggu.evolune.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.yuninggu.evolune.application.DraftIssue
import io.github.yuninggu.evolune.application.DraftMappingResult
import io.github.yuninggu.evolune.application.MedicationPlanDraft
import io.github.yuninggu.evolune.application.MedicationPlanEditSession
import io.github.yuninggu.evolune.application.MedicationPlanEditSessionFactory
import io.github.yuninggu.evolune.application.toDomainMedicationPlan
import io.github.yuninggu.evolune.core.dataapi.DeleteResult
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.core.dataapi.PlanSaveResult
import io.github.yuninggu.evolune.core.dataapi.PlanUpdateResult
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.reminder.MedicationPlanReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class MedicationPlanOperation {
    SAVE,
    DELETE,
    SET_ENABLED,
    RESCHEDULE
}

enum class ReminderSideEffectResult {
    APPLIED,
    FAILED
}

sealed interface MedicationPlanOperationSuccess {
    val operation: MedicationPlanOperation
    val reminder: ReminderSideEffectResult

    data class Saved(
        val repositoryResult: PlanSaveResult,
        override val reminder: ReminderSideEffectResult
    ) : MedicationPlanOperationSuccess {
        override val operation: MedicationPlanOperation = MedicationPlanOperation.SAVE
    }

    data class Deleted(
        override val reminder: ReminderSideEffectResult
    ) : MedicationPlanOperationSuccess {
        override val operation: MedicationPlanOperation = MedicationPlanOperation.DELETE
    }

    data class EnabledStateChanged(
        val repositoryResult: PlanUpdateResult,
        val enabled: Boolean,
        override val reminder: ReminderSideEffectResult
    ) : MedicationPlanOperationSuccess {
        override val operation: MedicationPlanOperation = MedicationPlanOperation.SET_ENABLED
    }

    data class Rescheduled(
        override val reminder: ReminderSideEffectResult
    ) : MedicationPlanOperationSuccess {
        override val operation: MedicationPlanOperation = MedicationPlanOperation.RESCHEDULE
    }
}

sealed interface MedicationPlanOperationError {
    data class InvalidDraft(val issues: List<DraftIssue>) : MedicationPlanOperationError
    data object RepositoryInvalid : MedicationPlanOperationError
    data object NotFound : MedicationPlanOperationError
    data object StorageFailure : MedicationPlanOperationError
    data object UnexpectedFailure : MedicationPlanOperationError
}

sealed interface MedicationPlanOperationState {
    data object Idle : MedicationPlanOperationState
    data class Running(val operation: MedicationPlanOperation) : MedicationPlanOperationState
    data class Success(val result: MedicationPlanOperationSuccess) : MedicationPlanOperationState
    data class Failure(
        val operation: MedicationPlanOperation,
        val error: MedicationPlanOperationError
    ) : MedicationPlanOperationState
}

class MedicationPlanViewModel(
    private val repository: MedicationPlanRepository,
    private val reminderScheduler: MedicationPlanReminderScheduler,
    private val sessionFactory: MedicationPlanEditSessionFactory = MedicationPlanEditSessionFactory(),
    operationScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = operationScope ?: viewModelScope
    private val operationLock = Any()
    private var operationInFlight = false
    private var pendingTerminalState: MedicationPlanOperationState? = null

    val plans: StateFlow<List<MedicationPlan>> = repository.observeAll()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val enabledPlans: StateFlow<List<MedicationPlan>> = repository.observeEnabled()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _editSession = MutableStateFlow<MedicationPlanEditSession?>(null)
    val editSession: StateFlow<MedicationPlanEditSession?> = _editSession.asStateFlow()

    private val _operationState =
        MutableStateFlow<MedicationPlanOperationState>(MedicationPlanOperationState.Idle)
    val operationState: StateFlow<MedicationPlanOperationState> = _operationState.asStateFlow()

    fun startCreateSession() {
        if (_editSession.value == null) {
            _editSession.value = sessionFactory.createNew()
        }
    }

    fun startEditSession(plan: MedicationPlan) {
        _editSession.value = sessionFactory.edit(plan)
    }

    fun closeEditSession() {
        if (_operationState.value !is MedicationPlanOperationState.Running) {
            _editSession.value = null
        }
    }

    fun acknowledgeOperation() {
        if (_operationState.value !is MedicationPlanOperationState.Running) {
            _operationState.value = MedicationPlanOperationState.Idle
        }
    }

    fun saveDraft(draft: MedicationPlanDraft) {
        launchOperation(MedicationPlanOperation.SAVE) {
            val plan = when (val mapped = draft.toDomainMedicationPlan()) {
                is DraftMappingResult.Success -> mapped.value
                is DraftMappingResult.InvalidDraft -> {
                    fail(
                        MedicationPlanOperation.SAVE,
                        MedicationPlanOperationError.InvalidDraft(mapped.issues)
                    )
                    return@launchOperation
                }
            }
            val result = try {
                repository.save(plan)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalStateException) {
                fail(MedicationPlanOperation.SAVE, MedicationPlanOperationError.StorageFailure)
                return@launchOperation
            }
            when (result) {
                PlanSaveResult.Created,
                PlanSaveResult.Updated,
                PlanSaveResult.NoChange -> succeed(
                    MedicationPlanOperationSuccess.Saved(
                        repositoryResult = result,
                        reminder = applyReminder(plan)
                    )
                )
                PlanSaveResult.Invalid -> fail(
                    MedicationPlanOperation.SAVE,
                    MedicationPlanOperationError.RepositoryInvalid
                )
            }
        }
    }

    fun deletePlan(id: UUID) {
        launchOperation(MedicationPlanOperation.DELETE) {
            val result = try {
                repository.delete(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalStateException) {
                fail(MedicationPlanOperation.DELETE, MedicationPlanOperationError.StorageFailure)
                return@launchOperation
            }
            when (result) {
                DeleteResult.Deleted -> succeed(
                    MedicationPlanOperationSuccess.Deleted(
                        reminder = cancelReminder(id)
                    )
                )
                DeleteResult.NotFound -> fail(
                    MedicationPlanOperation.DELETE,
                    MedicationPlanOperationError.NotFound
                )
            }
        }
    }

    fun setPlanEnabled(id: UUID, enabled: Boolean) {
        launchOperation(MedicationPlanOperation.SET_ENABLED) {
            val result = try {
                repository.setEnabled(id, enabled)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalStateException) {
                fail(MedicationPlanOperation.SET_ENABLED, MedicationPlanOperationError.StorageFailure)
                return@launchOperation
            }
            when (result) {
                PlanUpdateResult.Updated,
                PlanUpdateResult.NoChange -> succeed(
                    MedicationPlanOperationSuccess.EnabledStateChanged(
                        repositoryResult = result,
                        enabled = enabled,
                        reminder = if (enabled) {
                            scheduleUpdatedPlan(id)
                        } else {
                            cancelReminder(id)
                        }
                    )
                )
                PlanUpdateResult.NotFound -> fail(
                    MedicationPlanOperation.SET_ENABLED,
                    MedicationPlanOperationError.NotFound
                )
                PlanUpdateResult.Invalid -> fail(
                    MedicationPlanOperation.SET_ENABLED,
                    MedicationPlanOperationError.RepositoryInvalid
                )
            }
        }
    }

    suspend fun getPlanById(id: UUID): MedicationPlan? = repository.getById(id)

    fun rescheduleAllReminders() {
        launchOperation(MedicationPlanOperation.RESCHEDULE) {
            val plans = try {
                repository.observeAll().first()
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalStateException) {
                fail(MedicationPlanOperation.RESCHEDULE, MedicationPlanOperationError.StorageFailure)
                return@launchOperation
            }
            val reminderResult = try {
                reminderScheduler.reschedule(plans)
                ReminderSideEffectResult.APPLIED
            } catch (error: CancellationException) {
                throw error
            } catch (_: RuntimeException) {
                ReminderSideEffectResult.FAILED
            }
            succeed(MedicationPlanOperationSuccess.Rescheduled(reminderResult))
        }
    }

    private fun launchOperation(
        operation: MedicationPlanOperation,
        block: suspend () -> Unit
    ) {
        scope.launch {
            val started = synchronized(operationLock) {
                if (operationInFlight) {
                    false
                } else {
                    operationInFlight = true
                    pendingTerminalState = null
                    _operationState.value = MedicationPlanOperationState.Running(operation)
                    true
                }
            }
            if (!started) {
                return@launch
            }
            try {
                block()
            } catch (error: CancellationException) {
                pendingTerminalState = MedicationPlanOperationState.Idle
                throw error
            } catch (_: RuntimeException) {
                pendingTerminalState = MedicationPlanOperationState.Failure(
                    operation,
                    MedicationPlanOperationError.UnexpectedFailure
                )
            } finally {
                synchronized(operationLock) {
                    operationInFlight = false
                    _operationState.value =
                        pendingTerminalState ?: MedicationPlanOperationState.Idle
                    pendingTerminalState = null
                }
            }
        }
    }

    private suspend fun scheduleUpdatedPlan(id: UUID): ReminderSideEffectResult {
        val plan = try {
            repository.getById(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalStateException) {
            null
        } ?: return ReminderSideEffectResult.FAILED
        return applyReminder(plan)
    }

    private fun applyReminder(plan: MedicationPlan): ReminderSideEffectResult = try {
        if (plan.isEnabled) {
            reminderScheduler.schedule(plan)
        } else {
            reminderScheduler.cancel(plan.id)
        }
        ReminderSideEffectResult.APPLIED
    } catch (_: RuntimeException) {
        ReminderSideEffectResult.FAILED
    }

    private fun cancelReminder(id: UUID): ReminderSideEffectResult = try {
        reminderScheduler.cancel(id)
        ReminderSideEffectResult.APPLIED
    } catch (_: RuntimeException) {
        ReminderSideEffectResult.FAILED
    }

    private fun succeed(result: MedicationPlanOperationSuccess) {
        pendingTerminalState = MedicationPlanOperationState.Success(result)
    }

    private fun fail(
        operation: MedicationPlanOperation,
        error: MedicationPlanOperationError
    ) {
        pendingTerminalState = MedicationPlanOperationState.Failure(operation, error)
    }
}

class MedicationPlanViewModelFactory(
    private val repository: MedicationPlanRepository,
    private val reminderScheduler: MedicationPlanReminderScheduler,
    private val sessionFactory: MedicationPlanEditSessionFactory = MedicationPlanEditSessionFactory()
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationPlanViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MedicationPlanViewModel(
                repository = repository,
                reminderScheduler = reminderScheduler,
                sessionFactory = sessionFactory
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
