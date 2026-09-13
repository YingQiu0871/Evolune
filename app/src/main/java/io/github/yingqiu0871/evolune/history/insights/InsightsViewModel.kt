package io.github.yingqiu0871.evolune.history.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.yingqiu0871.evolune.experience.insights.InsightsContractViolationException
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsAggregator
import io.github.yingqiu0871.evolune.experience.insights.ReadOnlyMedicationInsightsAggregator
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.HistoryReadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Orchestrates the Insights surface: range selection, one authoritative read, one aggregation.
 *
 * Data flow (v1.7-B-02 section 2) — the ViewModel never reaches past the seam:
 *
 * ```
 * InsightsViewModel -> HistoryRangeSource -> HistoricalRange -> MedicationInsightsAggregator -> MedicationInsightsSummary
 * ```
 *
 * Query discipline (section 11/12/13): a valid selection change performs **exactly one** read and
 * **exactly one** aggregation; an identical selection performs none; an invalid custom range
 * performs none. Recomposition never queries.
 *
 * Refresh (sections 19-22): the surface entry and a real foreground return refresh the current
 * selection once, and a refresh arriving while a load is running is **not dropped** — it is
 * coalesced into exactly one follow-up load after the current one finishes. An explicit selection
 * change, in contrast, supersedes the in-flight load (the user asked for a different range).
 *
 * Race safety (section 14): the generation token is claimed before the previous job is cancelled,
 * and success, ordinary failure and contract failure all verify it before writing state.
 */
class InsightsViewModel(
    private val rangeSource: HistoryRangeSource,
    private val aggregator: MedicationInsightsAggregator = ReadOnlyMedicationInsightsAggregator,
    private val clock: Clock = Clock.systemUTC(),
    private val displayZone: () -> ZoneId = ZoneId::systemDefault,
    private val savedStateHandle: SavedStateHandle? = null,
    operationScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = operationScope ?: viewModelScope
    private val initialSelection = restoreSelection()
    private val _uiState = MutableStateFlow(
        InsightsUiState(
            selection = initialSelection,
            today = currentToday(),
            displayZone = displayZone()
        )
    )
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var generation = 0
    private var surfaceShownOnce = false
    private var pendingRefresh = false
    private var pendingSelection: InsightsRangeSelection? = null

    init {
        load(_uiState.value.selection)
    }

    // ---------- intents ----------

    /**
     * Selects a range. An identical selection performs no read.
     *
     * A user-selected range change **supersedes** an in-flight load instead of waiting for it: the
     * new load cancels the old one and its generation makes any superseded response stale, so exactly one
     * read belongs to the new selection.
     */
    fun selectRange(selection: InsightsRangeSelection) {
        if (selection == _uiState.value.selection) return
        persistSelection(selection)
        startLoad(selection)
    }

    /** Retries the current selection exactly once (coalesced if a load is already running). */
    fun retry() {
        load(_uiState.value.selection)
    }

    /**
     * The Insights surface entered composition. The first entry is owned by the initial load;
     * every later entry refreshes the current selection once.
     */
    fun onSurfaceShown() {
        if (!surfaceShownOnce) {
            surfaceShownOnce = true
            return
        }
        load(_uiState.value.selection)
    }

    /**
     * The app returned to the foreground while Insights is the active surface (the host only calls
     * this after a real stop → start transition, so a cold start cannot double-load).
     */
    fun onAppForegrounded() {
        load(_uiState.value.selection)
    }

    // ---------- loading ----------

    private fun load(selection: InsightsRangeSelection) {
        if (loadJob?.isActive == true) {
            // Coalesce: never drop a refresh (the A-04 boundary this round avoids copying).
            pendingRefresh = true
            pendingSelection = selection
            if (selection != _uiState.value.selection) {
                applyPendingSelectionPreview(selection)
            }
            return
        }
        startLoad(selection)
    }

    private fun startLoad(selection: InsightsRangeSelection) {
        val token = ++generation
        loadJob?.cancel()
        val zone = displayZone()
        val today = currentToday(zone)
        when (val resolution = InsightsRangeResolver.resolve(selection, today)) {
            is InsightsRangeResolution.Invalid -> {
                // No read and no aggregation for an invalid range.
                _uiState.value = _uiState.value.copy(
                    selection = selection,
                    startDate = null,
                    endDate = null,
                    today = today,
                    displayZone = zone,
                    phase = InsightsPhase.INVALID_RANGE,
                    summary = null,
                    failure = null,
                    validationError = resolution.error
                )
            }

            is InsightsRangeResolution.Resolved -> {
                _uiState.value = _uiState.value.copy(
                    selection = selection,
                    startDate = resolution.startDate,
                    endDate = resolution.endDate,
                    today = today,
                    displayZone = zone,
                    phase = InsightsPhase.LOADING,
                    summary = null,
                    failure = null,
                    validationError = null
                )
                loadJob = scope.launch {
                    try {
                        val range = rangeSource.read(
                            startDate = resolution.startDate,
                            endDate = resolution.endDate,
                            displayZone = zone,
                            now = clock.instant()
                        )
                        val summary = aggregator.aggregate(range)
                        if (token != generation) return@launch
                        _uiState.value = _uiState.value.copy(
                            phase = if (summary.recordedIntakeCount == 0) {
                                InsightsPhase.EMPTY
                            } else {
                                InsightsPhase.CONTENT
                            },
                            summary = summary,
                            failure = null,
                            validationError = null
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (violation: InsightsContractViolationException) {
                        if (token == generation) {
                            _uiState.value = _uiState.value.copy(
                                phase = InsightsPhase.ERROR,
                                summary = null,
                                failure = InsightsLoadFailure.ContractViolation(violation)
                            )
                        }
                    } catch (error: Throwable) {
                        if (token == generation) {
                            _uiState.value = _uiState.value.copy(
                                phase = InsightsPhase.ERROR,
                                summary = null,
                                failure = InsightsLoadFailure.ReadFailure(error)
                            )
                        }
                    } finally {
                        if (token == generation) runPendingRefresh()
                    }
                }
            }
        }
    }

    /** Runs at most one follow-up load for any number of coalesced refresh requests. */
    private fun runPendingRefresh() {
        if (!pendingRefresh) return
        pendingRefresh = false
        val target = pendingSelection ?: _uiState.value.selection
        pendingSelection = null
        startLoad(target)
    }

    private fun applyPendingSelectionPreview(selection: InsightsRangeSelection) {
        val zone = displayZone()
        val today = currentToday(zone)
        val resolution = InsightsRangeResolver.resolve(selection, today)
        _uiState.value = when (resolution) {
            is InsightsRangeResolution.Resolved -> _uiState.value.copy(
                selection = selection,
                startDate = resolution.startDate,
                endDate = resolution.endDate,
                today = today,
                displayZone = zone,
                phase = InsightsPhase.LOADING,
                summary = null,
                failure = null,
                validationError = null
            )

            is InsightsRangeResolution.Invalid -> _uiState.value.copy(
                selection = selection,
                startDate = null,
                endDate = null,
                today = today,
                displayZone = zone,
                phase = InsightsPhase.INVALID_RANGE,
                summary = null,
                failure = null,
                validationError = resolution.error
            )
        }
    }

    private fun currentToday(zone: ZoneId = displayZone()): LocalDate =
        LocalDate.now(clock.withZone(zone))

    // ---------- state restoration (selection only; summaries are always recomputed) ----------

    private fun restoreSelection(): InsightsRangeSelection {
        val handle = savedStateHandle ?: return InsightsRangeSelection.DEFAULT
        return when (handle.get<String>(KEY_SELECTION_TYPE)) {
            TYPE_LAST_7 -> InsightsRangeSelection.Last7Days
            TYPE_LAST_30 -> InsightsRangeSelection.Last30Days
            TYPE_LAST_90 -> InsightsRangeSelection.Last90Days
            TYPE_CURRENT_MONTH -> InsightsRangeSelection.CurrentMonth
            TYPE_CUSTOM -> {
                val start = handle.get<String>(KEY_CUSTOM_START)?.let(::parseDate)
                val end = handle.get<String>(KEY_CUSTOM_END)?.let(::parseDate)
                if (start != null && end != null) {
                    InsightsRangeSelection.Custom(start, end)
                } else {
                    InsightsRangeSelection.DEFAULT
                }
            }

            else -> InsightsRangeSelection.DEFAULT
        }
    }

    private fun persistSelection(selection: InsightsRangeSelection) {
        val handle = savedStateHandle ?: return
        when (selection) {
            InsightsRangeSelection.Last7Days -> {
                handle.set(KEY_SELECTION_TYPE, TYPE_LAST_7)
                clearCustom(handle)
            }

            InsightsRangeSelection.Last30Days -> {
                handle.set(KEY_SELECTION_TYPE, TYPE_LAST_30)
                clearCustom(handle)
            }

            InsightsRangeSelection.Last90Days -> {
                handle.set(KEY_SELECTION_TYPE, TYPE_LAST_90)
                clearCustom(handle)
            }

            InsightsRangeSelection.CurrentMonth -> {
                handle.set(KEY_SELECTION_TYPE, TYPE_CURRENT_MONTH)
                clearCustom(handle)
            }

            is InsightsRangeSelection.Custom -> {
                handle.set(KEY_SELECTION_TYPE, TYPE_CUSTOM)
                handle.set(KEY_CUSTOM_START, selection.startDate.toString())
                handle.set(KEY_CUSTOM_END, selection.endDate.toString())
            }
        }
    }

    private fun clearCustom(handle: SavedStateHandle) {
        handle.remove<String>(KEY_CUSTOM_START)
        handle.remove<String>(KEY_CUSTOM_END)
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private companion object {
        const val KEY_SELECTION_TYPE = "insights.selectionType"
        const val KEY_CUSTOM_START = "insights.customStartDate"
        const val KEY_CUSTOM_END = "insights.customEndDate"

        const val TYPE_LAST_7 = "LAST_7_DAYS"
        const val TYPE_LAST_30 = "LAST_30_DAYS"
        const val TYPE_LAST_90 = "LAST_90_DAYS"
        const val TYPE_CURRENT_MONTH = "CURRENT_MONTH"
        const val TYPE_CUSTOM = "CUSTOM"
    }
}

/**
 * Factory for the Insights surface.
 *
 * The read seam is the same authoritative [HistoryRangeSource] the History surface uses (wired to
 * [HistoryReadService] at the composition root): Insights never gets a second history reader. The
 * saved-state handle is requested from [CreationExtras] when the owner provides a saved-state
 * registry, and the screen still works when it cannot.
 */
class InsightsViewModelFactory(
    private val historyReadService: HistoryReadService,
    private val aggregator: MedicationInsightsAggregator = ReadOnlyMedicationInsightsAggregator,
    private val clock: Clock = Clock.systemUTC(),
    private val displayZone: () -> ZoneId = ZoneId::systemDefault
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(modelClass, CreationExtras.Empty)

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(InsightsViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
        val savedStateHandle = runCatching { extras.createSavedStateHandle() }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        return InsightsViewModel(
            rangeSource = HistoryRangeSource { startDate, endDate, zone, now ->
                historyReadService.readRange(startDate, endDate, zone, now)
            },
            aggregator = aggregator,
            clock = clock,
            displayZone = displayZone,
            savedStateHandle = savedStateHandle
        ) as T
    }
}
