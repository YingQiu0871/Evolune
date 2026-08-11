package io.github.yuninggu.evolune.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.yuninggu.evolune.application.DoseEventEditCommand
import io.github.yuninggu.evolune.application.DoseEventEditSession
import io.github.yuninggu.evolune.application.DoseEventEditSessionFactory
import io.github.yuninggu.evolune.application.DoseEventEditorInput
import io.github.yuninggu.evolune.application.DoseEventEditorResult
import io.github.yuninggu.evolune.application.DoseEventInputIssue
import io.github.yuninggu.evolune.application.MahiroJsonV1ImportError
import io.github.yuninggu.evolune.application.MahiroJsonV1ImportResult
import io.github.yuninggu.evolune.application.MahiroJsonV1ImportService
import io.github.yuninggu.evolune.application.MahiroJsonV1ExportService
import io.github.yuninggu.evolune.application.toDoseEventCommand
import io.github.yuninggu.evolune.core.adapter.DomainDoseEventToPkAdapter
import io.github.yuninggu.evolune.core.dataapi.DeleteResult
import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.core.dataapi.UpdateResult
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.pk.SimulationEngine
import io.github.yuninggu.evolune.utils.MedicationPlanPredictor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil

sealed class ImportResult {
    data object Idle : ImportResult()
    data object Importing : ImportResult()
    data class Success(
        val importedCount: Int,
        val existingCount: Int = 0,
        val conflictCount: Int = 0,
        val invalidCount: Int = 0
    ) : ImportResult()
    data class Error(
        val message: String,
        val importedCount: Int = 0,
        val existingCount: Int = 0,
        val conflictCount: Int = 0,
        val invalidCount: Int = 0,
        val failedIndex: Int? = null
    ) : ImportResult()
}

enum class DoseEventOperation {
    CREATE,
    QUICK_ADD,
    UPDATE,
    DELETE,
    IMPORT
}

sealed interface DoseEventOperationError {
    data class InvalidInput(val issues: List<DoseEventInputIssue>) : DoseEventOperationError
    data object RepositoryInvalid : DoseEventOperationError
    data object Conflict : DoseEventOperationError
    data object RevisionConflict : DoseEventOperationError
    data object NotFound : DoseEventOperationError
    data object StorageFailure : DoseEventOperationError
}

sealed interface DoseEventOperationState {
    data object Idle : DoseEventOperationState
    data class Running(val operation: DoseEventOperation) : DoseEventOperationState
    data class Success(
        val operation: DoseEventOperation,
        val event: DoseEvent? = null
    ) : DoseEventOperationState
    data class Failure(
        val operation: DoseEventOperation,
        val error: DoseEventOperationError
    ) : DoseEventOperationState
}

sealed interface DoseEventUiEvent {
    data class Saved(
        val event: DoseEvent,
        val created: Boolean
    ) : DoseEventUiEvent
    data class Deleted(val id: UUID) : DoseEventUiEvent
}

class HRTViewModel(
    private val repository: DoseEventRepository,
    private val medicationPlanRepository: MedicationPlanRepository,
    private val bodyWeightKG: Double = 55.0,
    private val sessionFactory: DoseEventEditSessionFactory = DoseEventEditSessionFactory(),
    private val clock: Clock = Clock.systemUTC(),
    private val jsonImportService: MahiroJsonV1ImportService =
        MahiroJsonV1ImportService(repository),
    private val jsonExportService: MahiroJsonV1ExportService =
        MahiroJsonV1ExportService(clock = clock),
    operationScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = operationScope ?: viewModelScope
    private val operationLock = Any()
    private var operationInFlight = false
    private var pendingTerminalState: DoseEventOperationState? = null
    private var pendingUiEvent: DoseEventUiEvent? = null

    val events: StateFlow<List<DoseEvent>> = repository.observeAll()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allPlans: StateFlow<List<MedicationPlan>> = medicationPlanRepository.observeAll()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val enabledPlans: StateFlow<List<MedicationPlan>> = medicationPlanRepository.observeEnabled()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val doseTimePoints: StateFlow<List<Double>> = events
        .map { eventList ->
            DomainDoseEventToPkAdapter.adapt(eventList).map { event -> event.timeH }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _pkState = MutableStateFlow(PKState())
    val pkState: StateFlow<PKState> = _pkState.asStateFlow()

    val currentTimeH: StateFlow<Double> = flow {
        while (true) {
            emit(clock.millis() / MILLIS_PER_HOUR)
            delay(1000)
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = clock.millis() / MILLIS_PER_HOUR
    )

    private val _editSession = MutableStateFlow<DoseEventEditSession?>(null)
    val editSession: StateFlow<DoseEventEditSession?> = _editSession.asStateFlow()

    private val _operationState =
        MutableStateFlow<DoseEventOperationState>(DoseEventOperationState.Idle)
    val operationState: StateFlow<DoseEventOperationState> = _operationState.asStateFlow()

    private val uiEventChannel = Channel<DoseEventUiEvent>(Channel.BUFFERED)
    val uiEvents = uiEventChannel.receiveAsFlow()

    private val _importResult = MutableStateFlow<ImportResult>(ImportResult.Idle)
    val importResult: StateFlow<ImportResult> = _importResult.asStateFlow()

    init {
        scope.launch {
            events.collect { runSimulation() }
        }
        scope.launch {
            enabledPlans.collect { runSimulation() }
        }
    }

    fun startCreateSession() {
        if (_editSession.value == null) {
            _editSession.value = sessionFactory.createNew()
        }
    }

    fun startEditSession(event: DoseEvent) {
        if (_operationState.value !is DoseEventOperationState.Running) {
            _editSession.value = sessionFactory.edit(event)
        }
    }

    fun closeEditSession() {
        if (_operationState.value !is DoseEventOperationState.Running) {
            _editSession.value = null
        }
    }

    fun acknowledgeOperation() {
        if (_operationState.value !is DoseEventOperationState.Running) {
            _operationState.value = DoseEventOperationState.Idle
        }
    }

    fun saveEvent(input: DoseEventEditorInput) {
        val session = _editSession.value ?: return
        when (val mapped = input.toDoseEventCommand(session)) {
            is DoseEventEditorResult.Invalid -> {
                val operation = when (session.mode) {
                    io.github.yuninggu.evolune.application.DoseEventEditMode.CREATE ->
                        DoseEventOperation.CREATE
                    io.github.yuninggu.evolune.application.DoseEventEditMode.UPDATE ->
                        DoseEventOperation.UPDATE
                }
                _operationState.value = DoseEventOperationState.Failure(
                    operation,
                    DoseEventOperationError.InvalidInput(mapped.issues)
                )
            }
            is DoseEventEditorResult.Valid -> persistCommand(mapped.command)
        }
    }

    fun quickAddFromPlan(plan: MedicationPlan) {
        launchOperation(DoseEventOperation.QUICK_ADD) {
            val event = sessionFactory.createQuickEvent(plan)
            handleInsert(event, DoseEventOperation.QUICK_ADD)
        }
    }

    fun deleteEvent(id: UUID) {
        launchOperation(DoseEventOperation.DELETE) {
            when (repository.delete(id)) {
                DeleteResult.Deleted -> {
                    succeed(DoseEventOperation.DELETE)
                    pendingUiEvent = DoseEventUiEvent.Deleted(id)
                }
                DeleteResult.NotFound -> fail(
                    DoseEventOperation.DELETE,
                    DoseEventOperationError.NotFound
                )
            }
        }
    }

    fun importFromMahiroJson(
        jsonContent: String,
        onWeightImport: ((Double) -> Unit)? = null
    ) {
        if (!beginOperation(DoseEventOperation.IMPORT)) {
            return
        }
        _importResult.value = ImportResult.Importing
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    jsonImportService.import(jsonContent)
                }
                when (result) {
                    is MahiroJsonV1ImportResult.Success -> {
                        val summary = result.summary
                        summary.weight?.let { onWeightImport?.invoke(it) }
                        _importResult.value = ImportResult.Success(
                            importedCount = summary.acceptedCount,
                            existingCount = summary.idempotentCount,
                            conflictCount = summary.conflictCount,
                            invalidCount = summary.invalidCount
                        )
                        succeed(DoseEventOperation.IMPORT)
                    }
                    is MahiroJsonV1ImportResult.Failure -> {
                        val summary = result.summary
                        _importResult.value = ImportResult.Error(
                            message = "Import failed",
                            importedCount = summary.acceptedCount,
                            existingCount = summary.idempotentCount,
                            conflictCount = summary.conflictCount,
                            invalidCount = summary.invalidCount,
                            failedIndex = (result.error as? MahiroJsonV1ImportError.Storage)
                                ?.sourceIndex
                        )
                        fail(DoseEventOperation.IMPORT, DoseEventOperationError.StorageFailure)
                    }
                }
            } catch (error: CancellationException) {
                pendingTerminalState = DoseEventOperationState.Idle
                throw error
            } catch (_: RuntimeException) {
                _importResult.value = ImportResult.Error("Import failed")
                fail(DoseEventOperation.IMPORT, DoseEventOperationError.StorageFailure)
            } finally {
                finishOperation()
            }
        }
    }

    fun dismissImportResult() {
        _importResult.value = ImportResult.Idle
    }

    fun reportClipboardImportError(message: String) {
        _importResult.value = ImportResult.Error(message)
    }

    fun exportToMahiroJson(weight: Double): String =
        jsonExportService.export(weight, events.value)

    fun runSimulation() {
        scope.launch {
            try {
                _pkState.update { it.copy(isSimulating = true, error = null) }
                val now = clock.instant()
                val currentTimeH = clock.millis() / MILLIS_PER_HOUR
                val historicalEvents = DomainDoseEventToPkAdapter.adapt(
                    repository.getEventsForPk(now).filter { event ->
                        event.status == DoseEventStatus.RECORDED &&
                            event.route != Route.ANTIANDROGEN
                    }
                )
                val plans = medicationPlanRepository.observeEnabled().first()
                    .filter { it.route != Route.ANTIANDROGEN }
                val futureEvents = if (plans.isNotEmpty()) {
                    val predicted = MedicationPlanPredictor.generateFutureEventsForDomainPlans(
                        plans = plans,
                        fromDateTime = LocalDateTime.ofInstant(now, ZoneId.systemDefault()),
                        daysAhead = 15
                    )
                    MedicationPlanPredictor.filterConflictingPredictions(
                        predictedEvents = predicted,
                        actualEvents = historicalEvents
                    )
                } else {
                    emptyList()
                }

                if (historicalEvents.isEmpty() && futureEvents.isEmpty()) {
                    _pkState.update {
                        it.copy(
                            simulationResult = null,
                            baselineSimulationResult = null,
                            currentConcentration = null,
                            isSimulating = false,
                            currentTimeH = currentTimeH
                        )
                    }
                    return@launch
                }

                val startTimeH = currentTimeH - 24.0 * 15
                val endTimeH = currentTimeH + 24.0 * 15
                val stepsNeeded = ceil(
                    (endTimeH - startTimeH) * SIMULATION_POINTS_PER_HOUR
                ).toInt() + 1
                val numberOfSteps = maxOf(stepsNeeded, 1000)
                val baselineResult = if (historicalEvents.isNotEmpty()) {
                    SimulationEngine(
                        events = historicalEvents,
                        bodyWeightKG = bodyWeightKG,
                        startTimeH = startTimeH,
                        endTimeH = endTimeH,
                        numberOfSteps = numberOfSteps
                    ).run()
                } else {
                    null
                }
                val allEvents = historicalEvents + futureEvents
                val fullResult = if (allEvents.isNotEmpty()) {
                    SimulationEngine(
                        events = allEvents,
                        bodyWeightKG = bodyWeightKG,
                        startTimeH = startTimeH,
                        endTimeH = endTimeH,
                        numberOfSteps = numberOfSteps
                    ).run()
                } else {
                    null
                }
                val currentConcentration = fullResult?.concentration(currentTimeH)
                    ?: baselineResult?.concentration(currentTimeH)

                _pkState.update {
                    it.copy(
                        simulationResult = fullResult,
                        baselineSimulationResult = baselineResult,
                        currentConcentration = currentConcentration,
                        currentTimeH = currentTimeH,
                        isSimulating = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: RuntimeException) {
                _pkState.update {
                    it.copy(isSimulating = false, error = "Simulation unavailable")
                }
            }
        }
    }

    private fun persistCommand(command: DoseEventEditCommand) {
        when (command) {
            is DoseEventEditCommand.Create -> launchOperation(DoseEventOperation.CREATE) {
                handleInsert(command.event, DoseEventOperation.CREATE)
            }
            is DoseEventEditCommand.Update -> launchOperation(DoseEventOperation.UPDATE) {
                when (repository.update(command.event, command.expectedRevision)) {
                    UpdateResult.Updated,
                    UpdateResult.NoChange -> {
                        val stored = repository.getById(command.event.id)
                            ?: throw IllegalStateException("Updated dose event is missing")
                        succeed(DoseEventOperation.UPDATE, stored)
                        pendingUiEvent = DoseEventUiEvent.Saved(stored, created = false)
                    }
                    UpdateResult.NotFound -> fail(
                        DoseEventOperation.UPDATE,
                        DoseEventOperationError.NotFound
                    )
                    UpdateResult.RevisionConflict -> fail(
                        DoseEventOperation.UPDATE,
                        DoseEventOperationError.RevisionConflict
                    )
                    UpdateResult.Invalid -> fail(
                        DoseEventOperation.UPDATE,
                        DoseEventOperationError.RepositoryInvalid
                    )
                }
            }
        }
    }

    private suspend fun handleInsert(event: DoseEvent, operation: DoseEventOperation) {
        when (repository.insert(event)) {
            InsertResult.Inserted,
            InsertResult.Idempotent -> {
                val stored = repository.getById(event.id)
                    ?: throw IllegalStateException("Inserted dose event is missing")
                succeed(operation, stored)
                pendingUiEvent = DoseEventUiEvent.Saved(stored, created = true)
            }
            InsertResult.Conflict -> fail(operation, DoseEventOperationError.Conflict)
            InsertResult.Invalid -> fail(operation, DoseEventOperationError.RepositoryInvalid)
        }
    }

    private fun launchOperation(
        operation: DoseEventOperation,
        block: suspend () -> Unit
    ) {
        if (!beginOperation(operation)) {
            return
        }
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                pendingTerminalState = DoseEventOperationState.Idle
                throw error
            } catch (_: RuntimeException) {
                fail(operation, DoseEventOperationError.StorageFailure)
            } finally {
                finishOperation()
            }
        }
    }

    private fun beginOperation(operation: DoseEventOperation): Boolean =
        synchronized(operationLock) {
            if (operationInFlight) {
                false
            } else {
                operationInFlight = true
                pendingTerminalState = null
                pendingUiEvent = null
                _operationState.value = DoseEventOperationState.Running(operation)
                true
            }
        }

    private fun finishOperation() {
        val uiEvent = synchronized(operationLock) {
            operationInFlight = false
            _operationState.value = pendingTerminalState ?: DoseEventOperationState.Idle
            pendingTerminalState = null
            pendingUiEvent.also { pendingUiEvent = null }
        }
        uiEvent?.let(uiEventChannel::trySend)
    }

    private fun succeed(operation: DoseEventOperation, event: DoseEvent? = null) {
        pendingTerminalState = DoseEventOperationState.Success(operation, event)
    }

    private fun fail(operation: DoseEventOperation, error: DoseEventOperationError) {
        pendingTerminalState = DoseEventOperationState.Failure(operation, error)
    }

    private companion object {
        const val SIMULATION_POINTS_PER_HOUR = 12.0
        const val MILLIS_PER_HOUR = 3_600_000.0
    }
}

class HRTViewModelFactory(
    private val repository: DoseEventRepository,
    private val medicationPlanRepository: MedicationPlanRepository,
    private val bodyWeightKG: Double = 65.0
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HRTViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HRTViewModel(repository, medicationPlanRepository, bodyWeightKG) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
