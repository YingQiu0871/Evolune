package io.github.yingqiu0871.evolune.experience

import java.time.LocalDate

/**
 * One display day of the historical read model.
 *
 * Entries come straight from [HistoricalProjection]; this type never re-runs
 * occurrence matching and never re-derives display dates.
 */
data class HistoricalDay(
    val date: LocalDate,
    val entries: List<HistoricalEntry>
) {
    /** Occurrences with an authoritative recorded intake matched to them. */
    val recordedCount: Int get() = entries.count { it is MatchedHistoricalOccurrence }

    /**
     * Occurrences with **no recorded intake found** in the available authoritative
     * data. Never render this as missed/skipped/non-adherent.
     */
    val unrecordedCount: Int get() = entries.count { it is UnrecordedHistoricalOccurrence }

    /** Authoritative intakes that no occurrence claimed (deleted plan, manual, legacy, ...). */
    val unmatchedActualCount: Int get() = entries.count { it is UnmatchedHistoricalIntake }

    val isEmpty: Boolean get() = entries.isEmpty()
}

/**
 * Inclusive date range over the historical read model.
 *
 * Frozen range contract:
 * - [startDate] and [endDate] are both **inclusive**;
 * - [days] is **ascending** by date;
 * - only dates that actually have entries are returned — the calendar/UI layer is
 *   responsible for rendering empty days in a grid;
 * - entry order inside a day is the projection order (see the ordering rule below);
 * - an invalid range (`endDate` before `startDate`) fails fast with
 *   [IllegalArgumentException] instead of silently swapping or clamping.
 *
 * Frozen entry ordering rule (defined by [HistoricalProjectionBuilder] and reused here):
 * 1. `displayDate` ascending;
 * 2. `sortInstant` ascending — matched/unrecorded occurrences use the occurrence's
 *    scheduled instant, unmatched intakes use the event's occurred instant;
 * 3. kind rank — matched occurrence (0) before unrecorded occurrence (1) before
 *    unmatched intake (2);
 * 4. stable id tie-break — occurrence id, or event id for intakes.
 */
data class HistoricalRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: List<HistoricalDay>
) {
    val recordedCount: Int get() = days.sumOf { it.recordedCount }
    val unrecordedCount: Int get() = days.sumOf { it.unrecordedCount }
    val unmatchedActualCount: Int get() = days.sumOf { it.unmatchedActualCount }
}

/** Day/range projections over an existing [HistoricalProjection]. */
object HistoricalReadModel {

    fun day(projection: HistoricalProjection, date: LocalDate): HistoricalDay =
        HistoricalDay(date = date, entries = projection.entriesOn(date))

    fun range(
        projection: HistoricalProjection,
        startDate: LocalDate,
        endDate: LocalDate
    ): HistoricalRange {
        require(!endDate.isBefore(startDate)) {
            "history range endDate $endDate must not be before startDate $startDate"
        }
        val days = projection.entries
            .groupBy { it.displayDate }
            .filterKeys { !it.isBefore(startDate) && !it.isAfter(endDate) }
            .toSortedMap()
            .map { (date, entries) -> HistoricalDay(date = date, entries = entries) }
        return HistoricalRange(startDate = startDate, endDate = endDate, days = days)
    }
}
