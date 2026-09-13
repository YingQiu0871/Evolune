package io.github.yingqiu0871.evolune.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * State holder of the History screen (A-03 §4/§5).
 *
 * Query discipline:
 * - one `readRange` per visible month at most; selecting a day inside the same month never
 *   re-reads (the loaded month's days are already in [HistoryUiState.loadedDays]);
 * - the current month is queried up to `today`; a past month up to its last day; a future
 *   month can never be loaded;
 * - switching months cancels the in-flight request **and** guards the response with a
 *   generation token, so a stale response can never replace a newer month;
 * - `retry()` re-loads the current month exactly once.
 *
 * Time and zone are injectable ([clock], [displayZone]) so the whole state machine is
 * testable on the JVM; Compose never decides historical day attribution itself.
 */
class HistoryViewModel(
    private val rangeSource: HistoryRangeSource,
    private val clock: Clock = Clock.systemUTC(),
    private val displayZone: () -> ZoneId = ZoneId::systemDefault,
    private val savedStateHandle: SavedStateHandle? = null,
    operationScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = operationScope ?: viewModelScope
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadToken = 0

    init {
        loadMonth(_uiState.value.visibleMonth)
    }

    // ---------- intents ----------

    /** Selects a day of the visible month. Never triggers a read. */
    fun selectDate(date: LocalDate) {
        val state = _uiState.value
        if (YearMonth.from(date) != state.visibleMonth) return
        if (date.isAfter(state.today)) return
        if (date == state.selectedDate) return
        _uiState.value = state.copy(selectedDate = date)
        persistSelection(state.visibleMonth, date)
    }

    fun showPreviousMonth() {
        loadMonth(_uiState.value.visibleMonth.minusMonths(1))
    }

    /** Browsing stops at the current month: the future is not history. */
    fun showNextMonth() {
        val state = _uiState.value
        val next = state.visibleMonth.plusMonths(1)
        if (next > YearMonth.from(state.today)) return
        loadMonth(next)
    }

    /** Re-loads the visible month once, keeping month and selection. */
    fun retry() {
        loadMonth(_uiState.value.visibleMonth)
    }

    // ---------- loading ----------

    private fun loadMonth(month: YearMonth) {
        loadJob?.cancel()
        val token = ++loadToken
        val zone = displayZone()
        val today = LocalDate.now(clock.withZone(zone))
        val start = month.atDay(1)
        // The current month is history only up to today; a past month is complete.
        val end = if (month == YearMonth.from(today)) today else month.atEndOfMonth()
        val selection = resolveSelection(month, today, _uiState.value.selectedDate)

        _uiState.value = _uiState.value.copy(
            visibleMonth = month,
            selectedDate = selection,
            today = today,
            displayZone = zone,
            loading = true,
            failed = false
        )
        persistSelection(month, selection)

        loadJob = scope.launch {
            val range = try {
                rangeSource.read(start, end, zone, clock.instant())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (token == loadToken) {
                    _uiState.value = _uiState.value.copy(loading = false, failed = true)
                }
                return@launch
            }
            if (token != loadToken) return@launch
            _uiState.value = _uiState.value.copy(
                loading = false,
                failed = false,
                loadedMonth = month,
                loadedDays = range.days.associateBy { it.date }
            )
        }
    }

    // ---------- state restoration ----------

    private fun initialState(): HistoryUiState {
        val zone = displayZone()
        val today = LocalDate.now(clock.withZone(zone))
        val currentMonth = YearMonth.from(today)
        val restoredMonth = savedStateHandle?.get<String>(KEY_VISIBLE_MONTH)
            ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        // A restored future month would mean the clock moved backwards; never open it.
        val month = when {
            restoredMonth == null -> currentMonth
            restoredMonth.isAfter(currentMonth) -> currentMonth
            else -> restoredMonth
        }
        val restoredDate = savedStateHandle?.get<String>(KEY_SELECTED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val selection = restoredDate
            ?.takeIf { YearMonth.from(it) == month && !it.isAfter(today) }
            ?: if (month == currentMonth) today else month.atDay(1)
        return HistoryUiState(
            visibleMonth = month,
            selectedDate = selection,
            today = today,
            displayZone = zone
        )
    }

    private fun persistSelection(month: YearMonth, date: LocalDate) {
        savedStateHandle?.set(KEY_VISIBLE_MONTH, month.toString())
        savedStateHandle?.set(KEY_SELECTED_DATE, date.toString())
    }

    private fun resolveSelection(month: YearMonth, today: LocalDate, current: LocalDate): LocalDate = when {
        YearMonth.from(current) == month && !current.isAfter(today) -> current
        month == YearMonth.from(today) -> today
        else -> month.atDay(1)
    }

    private companion object {
        const val KEY_VISIBLE_MONTH = "history.visibleMonth"
        const val KEY_SELECTED_DATE = "history.selectedDate"
    }
}

/**
 * Factory for the History screen.
 *
 * The read seam is the authoritative [HistoryReadService]; the UI layer gets no other way to
 * obtain historical data. `SavedStateHandle` is requested from [CreationExtras] when the owner
 * provides a saved-state registry (an Activity or a NavBackStackEntry does), so month/selection
 * survive process death; when the extras cannot provide one, the screen still works, it just
 * does not restore.
 */
class HistoryViewModelFactory(
    private val historyReadService: HistoryReadService,
    private val clock: Clock = Clock.systemUTC(),
    private val displayZone: () -> ZoneId = ZoneId::systemDefault
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(modelClass, CreationExtras.Empty)

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
        val savedStateHandle = runCatching { extras.createSavedStateHandle() }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(
            rangeSource = HistoryRangeSource { startDate, endDate, zone, now ->
                historyReadService.readRange(startDate, endDate, zone, now)
            },
            clock = clock,
            displayZone = displayZone,
            savedStateHandle = savedStateHandle
        ) as T
    }
}
