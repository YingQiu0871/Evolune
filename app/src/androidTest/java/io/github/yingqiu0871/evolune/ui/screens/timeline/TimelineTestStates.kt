package io.github.yingqiu0871.evolune.ui.screens.timeline

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.history.timeline.TimelineDay
import io.github.yingqiu0871.evolune.history.timeline.TimelineProjectionBuilder
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeFailure
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangePhase
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeState
import io.github.yingqiu0871.evolune.history.timeline.TimelineReadModel
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * V17-D-04 §32 — synthetic published states for the Timeline instrumentation tests.
 *
 * Rows are produced by the **real** closed D-01 projector from production-shaped entries, so a
 * screen test renders exactly what a real published model carries.
 */
internal object TimelineTestStates {

    val utc: ZoneOffset = ZoneOffset.UTC
    val today: LocalDate = LocalDate.of(2026, 9, 16)
    val month: YearMonth = YearMonth.of(2026, 9)

    fun daysFrom(vararg days: Pair<LocalDate, List<HistoricalEntry>>): List<TimelineDay> =
        TimelineProjectionBuilder.build(
            HistoricalRange(
                startDate = days.first().first.withDayOfMonth(1),
                endDate = days.last().first,
                days = days.map { (date, entries) -> HistoricalDay(date = date, entries = entries) }
            )
        ).days

    fun state(
        phase: TimelineRangePhase = TimelineRangePhase.CONTENT,
        days: List<TimelineDay> = emptyList(),
        selectedDate: LocalDate = today,
        snapshotToday: LocalDate = today,
        displayZone: ZoneId = utc,
        requestedMonth: YearMonth = month,
        effectiveStart: LocalDate? = requestedMonth.atDay(1),
        effectiveEnd: LocalDate? = snapshotToday,
        failure: TimelineRangeFailure? = null
    ): TimelineRangeState = TimelineRangeState(
        requestedMonth = requestedMonth,
        effectiveStartDate = effectiveStart,
        effectiveEndDate = effectiveEnd,
        selectedDate = selectedDate,
        today = snapshotToday,
        displayZone = displayZone,
        phase = phase,
        timelineReadModel = if (days.isEmpty()) null else TimelineReadModel(days),
        selectedDay = days.firstOrNull { it.date == selectedDate },
        failure = failure,
        generation = 1
    )

    /** 08:00 scheduled E2 dose 2.0 matched by an 08:05 recorded intake of dose 3.0 (distinct sides). */
    fun matchedOccurrence(date: LocalDate = today, slotId: Long = 201L) =
        testOccurrence(slotId = slotId, date = date, time = LocalTime.of(8, 0), doseAmount = 2.0)

    fun matchedEvent(date: LocalDate = today, occurrence: io.github.yingqiu0871.evolune.experience.MedicationOccurrence) =
        testEvent(
            id = 201L,
            slotId = occurrence.slotId,
            occurredAt = date.atTime(8, 5).toInstant(utc),
            doseAmount = 3.0
        )

    fun matchedRow(date: LocalDate = today, slotId: Long = 201L): HistoricalEntry {
        val occurrence = matchedOccurrence(date, slotId)
        return matchedEntry(occurrence = occurrence, event = matchedEvent(date, occurrence))
    }

    fun unrecordedRow(
        date: LocalDate = today,
        slotId: Long = 202L,
        medicationKey: String = "EV",
        routeKey: String = "ORAL",
        time: LocalTime = LocalTime.of(16, 0)
    ): HistoricalEntry = unrecordedEntry(
        occurrence = testOccurrence(
            slotId = slotId,
            date = date,
            time = time,
            medicationKey = medicationKey,
            routeKey = routeKey
        )
    )

    fun unmatchedRow(date: LocalDate = today, id: Long = 203L): HistoricalEntry = unmatchedEntry(
        event = testEvent(
            id = id,
            occurredAt = date.atTime(21, 30).toInstant(utc),
            localDate = null,
            zoneId = null,
            medicationKey = "EV",
            doseAmount = 5.0
        ),
        displayDate = date
    )

    /** Rich CONTENT state: matched + unrecorded on today, a matched + partial day before. */
    fun contentState(displayZone: ZoneId = utc, snapshotToday: LocalDate = today): TimelineRangeState {
        val yesterday = today.minusDays(1)
        val days = daysFrom(
            yesterday to listOf(
                matchedRow(yesterday, slotId = 211L),
                unrecordedRow(yesterday, slotId = 212L, medicationKey = "MYSTERY")
            ),
            today to listOf(
                matchedRow(today, slotId = 213L),
                unrecordedRow(today, slotId = 214L),
                unmatchedRow(today)
            )
        )
        return state(days = days, displayZone = displayZone, snapshotToday = snapshotToday)
    }

    fun emptyRangeState(): TimelineRangeState = state(phase = TimelineRangePhase.EMPTY_RANGE)

    fun emptyDayState(): TimelineRangeState {
        val days = daysFrom(today to listOf(unrecordedRow(today, slotId = 221L)))
        return state(
            phase = TimelineRangePhase.EMPTY_DAY,
            days = days,
            selectedDate = LocalDate.of(2026, 9, 10)
        )
    }

    fun invalidRequestState(): TimelineRangeState = state(
        phase = TimelineRangePhase.INVALID_REQUEST,
        selectedDate = LocalDate.of(2026, 10, 3),
        effectiveStart = null,
        effectiveEnd = null
    )

    fun notLoadableState(): TimelineRangeState = state(
        phase = TimelineRangePhase.NOT_LOADABLE,
        requestedMonth = YearMonth.of(2026, 10),
        selectedDate = LocalDate.of(2026, 10, 1),
        effectiveStart = null,
        effectiveEnd = null
    )

    fun errorState(): TimelineRangeState = state(
        phase = TimelineRangePhase.ERROR,
        failure = TimelineRangeFailure.ReadFailure(IllegalStateException("synthetic")),
        effectiveStart = null,
        effectiveEnd = null
    )

    fun loadingState(withRange: Boolean = false): TimelineRangeState = state(
        phase = TimelineRangePhase.LOADING,
        effectiveStart = if (withRange) month.atDay(1) else null,
        effectiveEnd = if (withRange) today else null
    )

    fun pastMonthState(): TimelineRangeState = state(
        requestedMonth = YearMonth.of(2026, 8),
        days = daysFrom(LocalDate.of(2026, 8, 20) to listOf(unrecordedRow(LocalDate.of(2026, 8, 20), slotId = 231L))),
        selectedDate = LocalDate.of(2026, 8, 20),
        effectiveStart = LocalDate.of(2026, 8, 1),
        effectiveEnd = LocalDate.of(2026, 8, 31)
    )

    fun zoneState(zone: ZoneId): TimelineRangeState = contentState(displayZone = zone)

    /** Six one-row sections: the oldest section is off-screen on the Pixel 7 viewport. */
    fun manyDaysState(): TimelineRangeState {
        val days = (11..16).map { day ->
            val date = LocalDate.of(2026, 9, day)
            date to listOf(unrecordedRow(date, slotId = 500L + day))
        }
        return state(days = daysFrom(*days.toTypedArray()))
    }

    val matchedInstant: Instant = matchedOccurrence().scheduledAt

    fun singleSectionState(date: LocalDate): TimelineRangeState = state(
        days = daysFrom(date to listOf(matchedRow(date, slotId = 301L)))
    )
}
