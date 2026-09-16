package io.github.yingqiu0871.evolune.history.timeline

import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * V17-D-03 — Timeline range/date read orchestration (read-only, Android-free).
 *
 * Data path (frozen): `HistoryRangeSource -> TimelineRangeCoordinator -> TimelineProjectionBuilder
 * (closed D-01) -> TimelineReadModel -> immutable TimelineRangeState`. No repositories, DAOs,
 * matchers, generators or second truth seams are involved; no UI/ViewModel/Lifecycle exists here.
 *
 * Global scheduling (V17-D-03 §12, R2):
 * - at most ONE active [HistoryRangeSource] read globally;
 * - at most ONE pending request context, always the LATEST accepted load/refresh/retry context;
 * - latest accepted context wins regardless of operation kind (no type priority);
 * - a newer acceptance claims a newer generation immediately: the older generation loses
 *   publication authority at that moment, even while the newer context is still pending;
 * - when the active slot frees, the latest pending context is validated first; a pending
 *   INVALID_REQUEST / NOT_LOADABLE context publishes a zero-read terminal phase and starts no
 *   follow-up read.
 *
 * Selection (V17-D-03 §12.2 / P3 clarification): [selectDate] performs zero reads and creates no
 * read generation. The latest VALID selected-date intent survives an active/pending source read
 * and is used when that context eventually publishes; an invalid or out-of-month selection during
 * in-flight work does not replace the current valid selected-date intent — it requires a valid
 * selection or a new load context.
 *
 * Presentation timing (immediate LOADING vs retaining the previous CONTENT while a newer context
 * is pending) is deliberately NOT frozen here; that policy belongs to D-04. This class only owns
 * generation safety, terminal semantics and read counts.
 */
class TimelineRangeCoordinator(
    private val rangeSource: HistoryRangeSource,
    private val scope: CoroutineScope,
    initialRequest: TimelineMonthRequest
) {

    /** Logical identity of the latest accepted context: month + display zone (selection is separate). */
    private data class LogicalContext(
        val month: YearMonth,
        val displayZone: ZoneId
    )

    private data class RangeContext(
        val logical: LogicalContext,
        val capturedAt: Instant,
        val requestSelection: LocalDate,
        val replaceSelectionIntent: Boolean,
        val generation: Int
    )

    private val _state: MutableStateFlow<TimelineRangeState>
    val state: StateFlow<TimelineRangeState>

    private var generationCounter = 0
    private var latestLogicalContext: LogicalContext? = null
    private var latestAcceptedContext: RangeContext? = null
    private var selectedDateIntent: LocalDate? = null
    private var pendingContext: RangeContext? = null
    private var readInFlight = false

    init {
        val today = initialRequest.capturedAt.atZone(initialRequest.displayZone).toLocalDate()
        _state = MutableStateFlow(
            TimelineRangeState(
                requestedMonth = initialRequest.month,
                effectiveStartDate = null,
                effectiveEndDate = null,
                selectedDate = initialRequest.selectedDate,
                today = today,
                displayZone = initialRequest.displayZone,
                phase = TimelineRangePhase.LOADING,
                timelineReadModel = null,
                selectedDay = null,
                failure = null,
                generation = 0
            )
        )
        state = _state.asStateFlow()
        load(initialRequest)
    }

    // ---------- intents ----------

    /**
     * Accepts a new month-scoped load. The request carries a freshly supplied [TimelineMonthRequest.capturedAt];
     * this becomes the latest logical context and resets the selected-date intent to the request's
     * selection.
     */
    fun load(request: TimelineMonthRequest) {
        accept(
            RangeContext(
                logical = LogicalContext(request.month, request.displayZone),
                capturedAt = request.capturedAt,
                requestSelection = request.selectedDate,
                replaceSelectionIntent = true,
                generation = 0
            )
        )
    }

    /**
     * Refreshes the LATEST LOGICAL context (not the last published model) with a freshly supplied
     * [capturedAt]. Never derives its target from the last successful publication. No-op before
     * the first load.
     */
    fun refresh(capturedAt: Instant) {
        val logical = latestLogicalContext ?: return
        val selection = selectedDateIntent ?: latestAcceptedContext?.requestSelection ?: return
        accept(
            RangeContext(
                logical = logical,
                capturedAt = capturedAt,
                requestSelection = selection,
                replaceSelectionIntent = false,
                generation = 0
            )
        )
    }

    /** Retry after ERROR: same semantics as [refresh] — new generation with a fresh capture. */
    fun retry(capturedAt: Instant) {
        refresh(capturedAt)
    }

    /**
     * Updates the selected-date intent. Zero reads, no new read generation. An invalid or
     * out-of-month selection for the latest accepted context is ignored (the current valid intent
     * is preserved). When a model is already published, the phase is rederived against the new
     * selection (CONTENT / EMPTY_DAY / EMPTY_RANGE).
     */
    fun selectDate(date: LocalDate) {
        val accepted = latestAcceptedContext ?: return
        val today = accepted.capturedAt.atZone(accepted.logical.displayZone).toLocalDate()
        if (YearMonth.from(date) != accepted.logical.month || date > today) return

        selectedDateIntent = date
        val current = _state.value
        val model = current.timelineReadModel
        val day = model?.days?.firstOrNull { it.date == date }
        _state.value = current.copy(
            selectedDate = date,
            selectedDay = day,
            phase = when {
                model == null -> current.phase
                model.days.isEmpty() -> TimelineRangePhase.EMPTY_RANGE
                day == null -> TimelineRangePhase.EMPTY_DAY
                else -> TimelineRangePhase.CONTENT
            }
        )
    }

    // ---------- scheduling ----------

    private fun accept(context: RangeContext) {
        val claimed = context.copy(generation = ++generationCounter)
        latestLogicalContext = claimed.logical
        latestAcceptedContext = claimed
        if (claimed.replaceSelectionIntent) {
            selectedDateIntent = claimed.requestSelection
        }
        if (readInFlight) {
            // Latest accepted context replaces any previously pending context (latest-request-wins).
            pendingContext = claimed
        } else {
            startExecution(claimed)
        }
    }

    private fun startExecution(context: RangeContext) {
        readInFlight = true
        scope.launch {
            try {
                execute(context)
            } finally {
                readInFlight = false
                val pending = pendingContext
                pendingContext = null
                if (pending != null && pending.generation == generationCounter) {
                    startExecution(pending)
                }
            }
        }
    }

    // ---------- execution ----------

    private suspend fun execute(context: RangeContext) {
        if (context.generation != generationCounter) return // superseded before start: no read

        val requestedMonth = context.logical.month
        val displayZone = context.logical.displayZone
        val today = context.capturedAt.atZone(displayZone).toLocalDate()
        val intent = validIntent(context, requestedMonth, today)

        // Generation validation BEFORE any source read (R2).
        if (requestedMonth > YearMonth.from(today)) {
            publishTerminal(context, requestedMonth, displayZone, today, intent, TimelineRangePhase.NOT_LOADABLE)
            return
        }
        if (YearMonth.from(intent) != requestedMonth) {
            publishTerminal(context, requestedMonth, displayZone, today, intent, TimelineRangePhase.INVALID_REQUEST)
            return
        }
        if (intent > today) {
            publishTerminal(context, requestedMonth, displayZone, today, intent, TimelineRangePhase.NOT_LOADABLE)
            return
        }

        // Effective historical range (contract §5): past month = full month, current month = .. today.
        val start = requestedMonth.atDay(1)
        val end = if (requestedMonth == YearMonth.from(today)) today else requestedMonth.atEndOfMonth()

        publishIfCurrent(context) {
            it.copy(
                requestedMonth = requestedMonth,
                effectiveStartDate = start,
                effectiveEndDate = end,
                selectedDate = intent,
                today = today,
                displayZone = displayZone,
                phase = TimelineRangePhase.LOADING,
                timelineReadModel = null,
                selectedDay = null,
                failure = null,
                generation = context.generation
            )
        }

        val range = try {
            rangeSource.read(start, end, displayZone, context.capturedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            publishFailure(context, requestedMonth, displayZone, today, intent, start, end, error)
            return
        }

        val model = try {
            TimelineProjectionBuilder.build(range)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            publishFailure(context, requestedMonth, displayZone, today, intent, start, end, error)
            return
        }

        publishIfCurrent(context) {
            val publishedIntent = validIntent(context, requestedMonth, today)
            val day = model.days.firstOrNull { it.date == publishedIntent }
            it.copy(
                requestedMonth = requestedMonth,
                effectiveStartDate = start,
                effectiveEndDate = end,
                selectedDate = publishedIntent,
                today = today,
                displayZone = displayZone,
                phase = when {
                    model.days.isEmpty() -> TimelineRangePhase.EMPTY_RANGE
                    day == null -> TimelineRangePhase.EMPTY_DAY
                    else -> TimelineRangePhase.CONTENT
                },
                timelineReadModel = model,
                selectedDay = day,
                failure = null,
                generation = context.generation
            )
        }
    }

    /** Latest surviving intent; falls back to the request selection if an intent is no longer valid. */
    private fun validIntent(context: RangeContext, month: YearMonth, today: LocalDate): LocalDate {
        val intent = selectedDateIntent ?: context.requestSelection
        return if (YearMonth.from(intent) == month && intent <= today) intent else context.requestSelection
    }

    private fun publishTerminal(
        context: RangeContext,
        requestedMonth: YearMonth,
        displayZone: ZoneId,
        today: LocalDate,
        intent: LocalDate,
        phase: TimelineRangePhase
    ) {
        publishIfCurrent(context) {
            it.copy(
                requestedMonth = requestedMonth,
                effectiveStartDate = null,
                effectiveEndDate = null,
                selectedDate = intent,
                today = today,
                displayZone = displayZone,
                phase = phase,
                timelineReadModel = null,
                selectedDay = null,
                failure = null,
                generation = context.generation
            )
        }
    }

    private fun publishFailure(
        context: RangeContext,
        requestedMonth: YearMonth,
        displayZone: ZoneId,
        today: LocalDate,
        intent: LocalDate,
        start: LocalDate,
        end: LocalDate,
        error: Throwable
    ) {
        publishIfCurrent(context) {
            it.copy(
                requestedMonth = requestedMonth,
                effectiveStartDate = start,
                effectiveEndDate = end,
                selectedDate = intent,
                today = today,
                displayZone = displayZone,
                phase = TimelineRangePhase.ERROR,
                timelineReadModel = null,
                selectedDay = null,
                failure = TimelineRangeFailure.ReadFailure(error),
                generation = context.generation
            )
        }
    }

    private fun publishIfCurrent(
        context: RangeContext,
        transform: (TimelineRangeState) -> TimelineRangeState
    ) {
        if (context.generation != generationCounter) return
        _state.value = transform(_state.value)
    }
}
