package io.github.yingqiu0871.evolune.history.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.yingqiu0871.evolune.experience.insights.InsightsContractViolationException
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsAggregator
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
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
import java.time.Instant
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
 * Query discipline (sections 11/12/13): a valid selection change performs **exactly one** read and
 * **exactly one** aggregation; a selection that still resolves to the same endpoints performs none;
 * an invalid custom range performs none. Recomposition never queries.
 *
 * Refresh (sections 19-22): the surface entry and a real foreground return refresh the current
 * selection once, and a refresh arriving while a load is running is **not dropped** — it is
 * coalesced into exactly one follow-up load after the current one finishes. An explicit selection
 * change supersedes both the in-flight load and any refresh intent queued before it
 * (v1.7-B-02-R1 sections 3-5).
 *
 * Empty semantics (v1.7-B-02-R1 section 1): a successful load is `EMPTY` only when the range
 * carried no historical entry at all — neither a recorded intake nor an unrecorded occurrence.
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

    /**
     * One request's frozen view of "now" (v1.7-B-02-R1 section 11): the display zone, the instant
     * and the local date derived from that same instant. A request resolves its endpoints, forwards
     * `now` and publishes `today` from this single snapshot, so endpoints and `now` can never come
     * from two different clock reads.
     */
    private data class RequestSnapshot(val zone: ZoneId, val now: Instant, val today: LocalDate)

    private fun snapshot(): RequestSnapshot {
        val zone = displayZone()
        val now = clock.instant()
        return RequestSnapshot(zone = zone, now = now, today = now.atZone(zone).toLocalDate())
    }

    private val initialSnapshot = snapshot()
    private val _uiState = MutableStateFlow(
        InsightsUiState(
            selection = restoreSelection(),
            today = initialSnapshot.today,
            displayZone = initialSnapshot.zone
        )
    )
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var generation = 0
    private var surfaceShownOnce = false

    /**
     * True when a refresh arrived while a load was running.
     *
     * The intent owns **no** selection (v1.7-B-02-R1 section 3): the follow-up refreshes whatever
     * selection is authoritative when the running load settles, so a refresh queued before an
     * explicit selection change can never replay the older range.
     */
    private var pendingRefresh = false

    init {
        startLoad(_uiState.value.selection, initialSnapshot)
    }

    // ---------- intents ----------

    /**
     * Selects a range.
     *
     * Reselecting the currently active range is only a no-op while it still resolves to the same
     * endpoints (v1.7-B-02-R1 section 13): a relative preset whose day rolled over resolves
     * differently and therefore reads again.
     *
     * A user-selected range change **supersedes** an in-flight load *and* any refresh intent queued
     * before it (sections 3-5): that queued refresh belongs to the older selection and must not
     * replay it, while a refresh arriving *after* the change is still honoured.
     */
    fun selectRange(selection: InsightsRangeSelection) {
        if (selection == _uiState.value.selection) {
            val candidate = snapshot()
            if (resolvesLikeCurrentState(selection, candidate)) return
            startLoad(selection, candidate)
            return
        }
        persistSelection(selection)
        startLoad(selection)
    }

    /** Retries the current selection exactly once (coalesced if a load is already running). */
    fun retry() {
        refresh()
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
        refresh()
    }

    /**
     * The app returned to the foreground while Insights is the active surface (the host only calls
     * this after a real stop → start transition, so a cold start cannot double-load).
     */
    fun onAppForegrounded() {
        refresh()
    }

    // ---------- loading ----------

    /**
     * Refreshes the authoritative selection. A refresh that arrives while a load is running is never
     * dropped: it becomes exactly one follow-up (v1.7-B-02 section 12), and it carries no selection
     * of its own.
     */
    private fun refresh() {
        if (loadJob?.isActive == true) {
            pendingRefresh = true
            return
        }
        startLoad(_uiState.value.selection)
    }

    /** True when [selection] still resolves to the endpoints — or the verdict — already shown. */
    private fun resolvesLikeCurrentState(
        selection: InsightsRangeSelection,
        candidate: RequestSnapshot
    ): Boolean = when (val resolution = InsightsRangeResolver.resolve(selection, candidate.today)) {
        is InsightsRangeResolution.Invalid ->
            _uiState.value.phase == InsightsPhase.INVALID_RANGE &&
                _uiState.value.validationError == resolution.error

        is InsightsRangeResolution.Resolved ->
            _uiState.value.startDate == resolution.startDate &&
                _uiState.value.endDate == resolution.endDate
    }

    private fun startLoad(
        selection: InsightsRangeSelection,
        requestSnapshot: RequestSnapshot = snapshot()
    ) {
        // A load that starts now is newer than any refresh intent queued before it
        // (v1.7-B-02-R1 sections 4/5): the intent belonged to the superseded load/selection.
        pendingRefresh = false
        val token = ++generation
        loadJob?.cancel()
        when (val resolution = InsightsRangeResolver.resolve(selection, requestSnapshot.today)) {
            is InsightsRangeResolution.Invalid -> {
                // No read and no aggregation for an invalid range.
                _uiState.value = _uiState.value.copy(
                    selection = selection,
                    startDate = null,
                    endDate = null,
                    today = requestSnapshot.today,
                    displayZone = requestSnapshot.zone,
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
                    today = requestSnapshot.today,
                    displayZone = requestSnapshot.zone,
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
                            displayZone = requestSnapshot.zone,
                            now = requestSnapshot.now
                        )
                        val summary = aggregator.aggregate(range)
                        if (token != generation) return@launch
                        _uiState.value = _uiState.value.copy(
                            phase = if (summary.hasNoHistoricalEntries()) {
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

    /**
     * True when the authoritative range carried no historical entry at all.
     *
     * Recorded intakes are matched entries plus unmatched actual intakes (B-01), so a range that
     * only carries unrecorded occurrences has `recordedIntakeCount == 0` while still holding real
     * history: it is content, not empty (v1.7-B-02-R1 section 1, review item 9).
     */
    private fun MedicationInsightsSummary.hasNoHistoricalEntries(): Boolean =
        recordedIntakeCount == 0 && unrecordedOccurrenceCount == 0

    /** Runs at most one follow-up load for any number of coalesced refresh requests. */
    private fun runPendingRefresh() {
        if (!pendingRefresh) return
        pendingRefresh = false
        startLoad(_uiState.value.selection)
    }

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
