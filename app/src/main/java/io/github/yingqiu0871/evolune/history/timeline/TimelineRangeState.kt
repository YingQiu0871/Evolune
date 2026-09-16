package io.github.yingqiu0871.evolune.history.timeline

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * V17-D-03 §4/§6 — month-scoped orchestration request.
 *
 * All ambient inputs are explicit: the caller supplies the month, the selected date, the display
 * zone and a freshly captured [capturedAt]. The coordinator never reads `Instant.now()`,
 * `Clock.system*` or `ZoneId.systemDefault()`.
 *
 * The request type is deliberately permissive: an out-of-month or after-today selection must be
 * representable so the coordinator can publish the typed `INVALID_REQUEST` / `NOT_LOADABLE`
 * phases instead of failing construction.
 */
data class TimelineMonthRequest(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val displayZone: ZoneId,
    val capturedAt: Instant
)

/** V17-D-03 §7 — the exactly-seven orchestration phases. */
enum class TimelineRangePhase {
    /** An accepted range read is in flight. */
    LOADING,

    /** The loaded range contains rows for the selected date. */
    CONTENT,

    /** The authoritative read succeeded but the projected model has no date sections at all. */
    EMPTY_RANGE,

    /** The range carries history somewhere, but the selected date has no TimelineDay rows. */
    EMPTY_DAY,

    /** Structurally inconsistent request (e.g. selection outside the requested month). */
    INVALID_REQUEST,

    /** Future historical context: not loadable, zero reads. */
    NOT_LOADABLE,

    /** The authoritative read (or the projection step) failed. */
    ERROR
}

/**
 * V17-D-03 §14 — typed failure taxonomy.
 *
 * Precedent (Insights / Retrospective): `CancellationException` always rethrows; known
 * source/read failures become [ReadFailure]; dedicated typed contract violations may become
 * [ContractViolation]. The history read layer currently exposes no dedicated violation type, so
 * today every non-cancellation throwable maps to [ReadFailure] — unexpected exceptions are never
 * swallowed into an EMPTY phase.
 */
sealed interface TimelineRangeFailure {
    /** The authoritative [HistoryRangeSource] read (or the projection step) failed. */
    data class ReadFailure(val cause: Throwable) : TimelineRangeFailure

    /** Reserved for explicitly typed contract/invariant violations; none exist today. */
    data class ContractViolation(val cause: Throwable) : TimelineRangeFailure
}

/**
 * V17-D-03 §8 — immutable range/date state.
 *
 * Terminal phases always describe one consistent generation: CONTENT/EMPTY_RANGE/EMPTY_DAY carry
 * the newly projected model; INVALID_REQUEST/NOT_LOADABLE/ERROR carry no model (a superseded
 * month's data is never presented as current state).
 */
data class TimelineRangeState(
    val requestedMonth: YearMonth,
    /** Null when no read was issued for this generation (INVALID_REQUEST / NOT_LOADABLE). */
    val effectiveStartDate: LocalDate?,
    val effectiveEndDate: LocalDate?,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val displayZone: ZoneId,
    val phase: TimelineRangePhase,
    val timelineReadModel: TimelineReadModel?,
    /** Derived: `timelineReadModel.days.firstOrNull { it.date == selectedDate }`. */
    val selectedDay: TimelineDay?,
    val failure: TimelineRangeFailure?,
    val generation: Int
)
