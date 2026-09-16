package io.github.yingqiu0871.evolune.history.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * V17-D-04 §3/§6 — Timeline UI ViewModel (activity-scoped; exactly one coordinator).
 *
 * Data path (frozen): `HistoryRangeSource -> TimelineRangeCoordinator (closed D-03) ->
 * TimelineProjectionBuilder (closed D-01) -> TimelineReadModel -> TimelineRangeState`. This class
 * owns no read path of its own: it never touches `HistoryReadService`, repositories, DAOs or Room.
 *
 * Command / render split (D-04 §6.2/§6.3/§23.5, frozen):
 * - [latestLogicalIntent] is a narrow VM-owned request-intent mirror used ONLY to construct the
 *   next D-03 command and to validate `selectDate`; it is not a second state machine and holds no
 *   generation, pending flag, phase, model or failure;
 * - the published [state] flow is the sole authority for what is rendered (including
 *   `state.today` relative labels and the current-month control).
 *
 * Generation capture (D-04 §6.2 R3): every generation-producing command captures a fresh
 * `capturedAt` + `currentDisplayZone` PAIR and derives `commandToday` from exactly that pair;
 * `requestToday` mirrors the captured command context. `selectDate` performs zero captures, so a
 * later wall clock can never leak into selection validity.
 *
 * Refresh vs zone change (D-04 §6.1 R1/R2): same-zone activation/retry uses the frozen D-03
 * `refresh`/`retry` API; a changed display zone resolves the month/date per the frozen Case A/B
 * rules and submits exactly one new D-03 `load`.
 */
class TimelineViewModel(
    rangeSource: HistoryRangeSource,
    private val clock: Clock = Clock.systemUTC(),
    private val displayZone: () -> ZoneId = ZoneId::systemDefault,
    operationScope: CoroutineScope? = null
) : ViewModel() {

    /**
     * The narrow VM-owned logical request intent (D-04 §3.2). Command metadata only; the exact
     * name is a planning value. It deliberately carries no generation counter, no pending or
     * read-in-flight flag, no phase, no `TimelineReadModel`, no `TimelineDay` and no failure.
     */
    data class TimelineUiRequestIntent(
        val requestedMonth: YearMonth,
        val selectedDate: LocalDate,
        val displayZone: ZoneId,
        val requestToday: LocalDate
    )

    private val scope = operationScope ?: viewModelScope

    private val initialCapture = capture()

    /** Latest accepted D-04 logical intent; see class KDoc for the frozen ownership boundary. */
    var latestLogicalIntent: TimelineUiRequestIntent = TimelineUiRequestIntent(
        requestedMonth = YearMonth.from(initialCapture.today),
        selectedDate = initialCapture.today,
        displayZone = initialCapture.zone,
        requestToday = initialCapture.today
    )
        private set

    private val coordinator = TimelineRangeCoordinator(
        rangeSource = rangeSource,
        scope = scope,
        initialRequest = latestLogicalIntent.toRequest(initialCapture.capturedAt)
    )

    /** Published D-03 state: the sole authority for rendering the CURRENT surface. */
    val state: StateFlow<TimelineRangeState> = coordinator.state

    private var surfaceShownOnce = false

    // ---------- lifecycle intents (D-04 §6) ----------

    /**
     * The Timeline surface entered composition. The first entry is owned by the VM construction's
     * initial D-03 load, so a cold start performs exactly one read; every later entry performs one
     * activation refresh/load with a fresh capture.
     */
    fun onSurfaceShown() {
        if (!surfaceShownOnce) {
            surfaceShownOnce = true
            return
        }
        activationCommand()
    }

    /** The app returned to the foreground while Timeline is active (real stop → start only). */
    fun onAppForegrounded() {
        activationCommand()
    }

    // ---------- month navigation (D-04 §7) ----------

    /** Previous month: selected day resolves to the first of that past month. */
    fun showPreviousMonth() {
        loadMonth(latestLogicalIntent.requestedMonth.minusMonths(1))
    }

    /**
     * Next month. The rendered control is disabled at the published current month; this command
     * additionally re-checks against the fresh capture so a stale control can never reach a
     * future month.
     */
    fun showNextMonth() {
        val capture = capture()
        val from = latestLogicalIntent.requestedMonth
        if (from >= YearMonth.from(capture.today)) return
        loadMonth(from.plusMonths(1), capture)
    }

    // ---------- selection (D-04 §6.5 R3) ----------

    /**
     * Validates and applies a day selection. Valid iff the candidate belongs to the latest
     * logical requested month AND is not after [TimelineUiRequestIntent.requestToday].
     *
     * Zero source reads; zero clock/zone capture; the published state is never a validity
     * authority (it may lag a pending logical request).
     */
    fun selectDate(date: LocalDate) {
        val intent = latestLogicalIntent
        if (YearMonth.from(date) != intent.requestedMonth) return
        if (date > intent.requestToday) return
        latestLogicalIntent = intent.copy(selectedDate = date)
        coordinator.selectDate(date)
    }

    // ---------- retry / defensive recovery (D-04 §6.4) ----------

    /** Retry after ERROR: fresh capture against the latest logical intent (refresh or zone-change load). */
    fun retry() {
        val capture = capture()
        val intent = latestLogicalIntent
        if (capture.zone == intent.displayZone) {
            latestLogicalIntent = intent.copy(requestToday = capture.today)
            coordinator.retry(capture.capturedAt)
        } else {
            latestLogicalIntent = resolveZoneChange(capture)
            coordinator.load(latestLogicalIntent.toRequest(capture.capturedAt))
        }
    }

    /**
     * Defensive recovery for INVALID_REQUEST / NOT_LOADABLE: return to the fresh current month
     * with exactly one D-03 load. Never derives the target from a stale published state.
     */
    fun returnToCurrentMonth() {
        val capture = capture()
        latestLogicalIntent = TimelineUiRequestIntent(
            requestedMonth = YearMonth.from(capture.today),
            selectedDate = capture.today,
            displayZone = capture.zone,
            requestToday = capture.today
        )
        coordinator.load(latestLogicalIntent.toRequest(capture.capturedAt))
    }

    // ---------- command construction ----------

    /**
     * Ordinary activation (re-entry / foreground) or retry-shaped command. Same zone → the frozen
     * D-03 refresh path with `requestToday = commandToday`; changed zone → frozen Case A/B
     * resolution followed by exactly one new D-03 load.
     */
    private fun activationCommand() {
        val capture = capture()
        val intent = latestLogicalIntent
        if (capture.zone == intent.displayZone) {
            latestLogicalIntent = intent.copy(requestToday = capture.today)
            coordinator.refresh(capture.capturedAt)
        } else {
            latestLogicalIntent = resolveZoneChange(capture)
            coordinator.load(latestLogicalIntent.toRequest(capture.capturedAt))
        }
    }

    /**
     * Month change: one D-03 load; the selection resolves to [capture]'s today when the target is
     * the fresh current month, otherwise to the first of the target month (D-04 §7).
     */
    private fun loadMonth(month: YearMonth, capture: Capture = capture()) {
        val selectedDate = if (month == YearMonth.from(capture.today)) {
            capture.today
        } else {
            month.atDay(1)
        }
        latestLogicalIntent = TimelineUiRequestIntent(
            requestedMonth = month,
            selectedDate = selectedDate,
            displayZone = capture.zone,
            requestToday = capture.today
        )
        coordinator.load(latestLogicalIntent.toRequest(capture.capturedAt))
    }

    /**
     * Frozen zone-change month/date resolution (D-04 §6.1 R1 Case A/B). Only the explicit
     * generation-producing commands call this; selection never does.
     */
    private fun resolveZoneChange(capture: Capture): TimelineUiRequestIntent {
        val old = latestLogicalIntent
        val newCurrentMonth = YearMonth.from(capture.today)
        if (old.requestedMonth > newCurrentMonth) {
            // Case B: the old requested month is future in the new zone.
            return TimelineUiRequestIntent(
                requestedMonth = newCurrentMonth,
                selectedDate = capture.today,
                displayZone = capture.zone,
                requestToday = capture.today
            )
        }
        // Case A: the old requested month stays loadable; keep the old selection only while it is
        // in that month and not after the new today.
        val keepSelection = YearMonth.from(old.selectedDate) == old.requestedMonth &&
            old.selectedDate <= capture.today
        val selectedDate = when {
            keepSelection -> old.selectedDate
            old.requestedMonth == newCurrentMonth -> capture.today
            else -> old.requestedMonth.atDay(1)
        }
        return TimelineUiRequestIntent(
            requestedMonth = old.requestedMonth,
            selectedDate = selectedDate,
            displayZone = capture.zone,
            requestToday = capture.today
        )
    }

    /** One fresh capture: `capturedAt` + `currentDisplayZone` are always bound together. */
    private data class Capture(val capturedAt: Instant, val zone: ZoneId, val today: LocalDate)

    private fun capture(): Capture {
        val zone = displayZone()
        val now = clock.instant()
        return Capture(capturedAt = now, zone = zone, today = now.atZone(zone).toLocalDate())
    }

    private fun TimelineUiRequestIntent.toRequest(capturedAt: Instant): TimelineMonthRequest =
        TimelineMonthRequest(
            month = requestedMonth,
            selectedDate = selectedDate,
            displayZone = displayZone,
            capturedAt = capturedAt
        )
}

/**
 * Factory for the Timeline surface (D-04 §3).
 *
 * The factory receives only the approved [HistoryRangeSource] seam plus the clock and the
 * display-zone provider; the concrete `HistoryReadService` is bound to that seam at the
 * composition root (MainActivity) and is never passed here.
 */
class TimelineViewModelFactory(
    private val rangeSource: HistoryRangeSource,
    private val clock: Clock = Clock.systemUTC(),
    private val displayZone: () -> ZoneId = ZoneId::systemDefault
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(modelClass, CreationExtras.Empty)

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
        @Suppress("UNCHECKED_CAST")
        return TimelineViewModel(
            rangeSource = rangeSource,
            clock = clock,
            displayZone = displayZone
        ) as T
    }
}
