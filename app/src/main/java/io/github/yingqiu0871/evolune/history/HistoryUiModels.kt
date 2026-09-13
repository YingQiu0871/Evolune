package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Observable state of the History screen (A-03 §3/§4).
 *
 * It holds the loaded domain read model ([loadedDays]) plus the navigation state. It never
 * holds occurrence collections that are not history: `futureOccurrences` is not part of a
 * [HistoricalDay] at all.
 */
data class HistoryUiState(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val displayZone: ZoneId,
    /** The month [loadedDays] actually describes; null before the first successful load. */
    val loadedMonth: YearMonth? = null,
    val loadedDays: Map<LocalDate, HistoricalDay> = emptyMap(),
    val loading: Boolean = false,
    val failed: Boolean = false
)

/** Phase of the selected-day section (A-03 §14). */
enum class HistoryDayPhase { LOADING, CONTENT, EMPTY, ERROR }

/** Kind of historical fact an entry card represents. */
enum class HistoryEntryKind { MATCHED, UNRECORDED, UNMATCHED }

/** Resolved actual-intake timestamp: instant + the zone it must be rendered in. */
data class HistoryActualTimeUiModel(
    val instant: Instant,
    val zone: ZoneId,
    /**
     * True when the actual intake's local date **in [zone]** differs from the entry's display
     * date. Always produced by `HistoryFormatting.actualTimestampPresentation`, never from the
     * domain's `crossesLocalDateBoundary`.
     */
    val needsFullDate: Boolean
)

/** Resolved current-schedule timestamp (never a historical planned snapshot). */
data class HistoryScheduleTimeUiModel(
    val instant: Instant,
    val zone: ZoneId,
    val needsFullDate: Boolean
)

/**
 * One entry card. Carries formatting-ready values, label resource ids and visual semantic
 * flags only — it never re-states the domain classification in its own words.
 */
data class HistoryEntryUiModel(
    val key: String,
    val kind: HistoryEntryKind,
    val statusLabelRes: Int,
    val planName: String?,
    val routeLabelRes: Int?,
    val routeFallback: String?,
    val medicationLabelRes: Int?,
    val medicationFallback: String?,
    val doseAmount: Double?,
    val actualTime: HistoryActualTimeUiModel?,
    val scheduleTime: HistoryScheduleTimeUiModel?,
    /** Primary auxiliary line: inferred-match context, no-intake explanation, plan unavailable. */
    val noteRes: Int?,
    /** Secondary neutral caveat: "not necessarily missed", "date shown in current time zone". */
    val secondaryNoteRes: Int?,
    val sourceLabelRes: Int?,
    val isManualSource: Boolean,
    /** True for inferred/legacy match provenance: exact and inferred must not look alike. */
    val isInferredMatch: Boolean = false
)

/**
 * One day cell of the month grid. A blank cell has a null [date].
 *
 * The three day counts are the **single source of truth** for both the indicator dots and the
 * accessibility description: the indicator booleans are derived from them, so a screen reader
 * can never hear a fabricated "1" while the domain reported three facts (A-03-UI-R1).
 */
data class HistoryCalendarCellUiModel(
    val date: LocalDate?,
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = false,
    val recordedCount: Int = 0,
    val unrecordedCount: Int = 0,
    val unmatchedActualCount: Int = 0
) {
    val hasRecorded: Boolean get() = recordedCount > 0
    val hasUnrecorded: Boolean get() = unrecordedCount > 0
    val hasUnmatchedActual: Boolean get() = unmatchedActualCount > 0
}

/** Selected-day section of the screen. */
data class HistoryDayUiModel(
    val date: LocalDate,
    val recordedCount: Int,
    val unrecordedCount: Int,
    val unmatchedActualCount: Int,
    val entries: List<HistoryEntryUiModel>
)

/** Fully derived month presentation consumed by the screen. */
data class HistoryMonthUiModel(
    val month: YearMonth,
    val today: LocalDate,
    val selectedDate: LocalDate,
    val canGoToNextMonth: Boolean,
    val cells: List<HistoryCalendarCellUiModel>,
    val phase: HistoryDayPhase,
    val day: HistoryDayUiModel?
)
