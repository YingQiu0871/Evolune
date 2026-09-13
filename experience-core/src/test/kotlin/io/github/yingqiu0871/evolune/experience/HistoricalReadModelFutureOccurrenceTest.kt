package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A-03-PRE-01 read-model contract: [HistoricalReadModel] consumes `HistoricalEntry`
 * values only.
 *
 * Upcoming occurrences live in [HistoricalProjection.futureOccurrences], so day and
 * range read models can neither render them nor count them — not as "no recorded
 * intake", not as an empty future day. Past days keep obeying the A-02 completeness
 * rule: every occurrence of a finished day is still matched or unrecorded.
 */
class HistoricalReadModelFutureOccurrenceTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    private val day: LocalDate = LocalDate.of(2025, 1, 5)
    private val tomorrow: LocalDate = day.plusDays(1)
    private val now: Instant = Instant.parse("2025-01-05T12:00:00Z")

    private fun planOf(times: List<LocalTime>, number: Long = 1L): MedicationSchedule =
        schedule(number = number, times = times)

    private fun occurrencesOn(
        plan: MedicationSchedule,
        date: LocalDate = day,
        days: Long = 1L
    ): List<MedicationOccurrence> = occurrences(
        schedules = listOf(plan),
        start = date.atStartOfDay(utc).toInstant().toString(),
        end = date.plusDays(days).atStartOfDay(utc).toInstant().toString()
    )

    private fun List<MedicationOccurrence>.at(time: LocalTime): MedicationOccurrence =
        single { it.scheduledLocalDateTime.toLocalTime() == time }

    private fun localTimes(values: List<HistoricalEntry>): List<LocalTime> =
        values.map { it.sortInstant.atZone(utc).toLocalTime() }

    private fun reminderEvent(occurrence: MedicationOccurrence, occurredAt: Instant, id: Long) =
        RecordedMedicationEvent(
            eventId = UUID(9L, id),
            occurredAt = occurredAt,
            slotId = occurrence.slotId,
            matchKey = occurrence.presentation.matchKey,
            source = MedicationIntakeSource.MANUAL,
            localDate = occurrence.scheduledLocalDateTime.toLocalDate(),
            zoneId = utc
        )

    /** 08:00 unrecorded, 10:00 recorded, 14:00 recorded early, 20:00 not yet arrived. */
    private fun mixedDayProjection(): HistoricalProjection {
        val plan = planOf(
            listOf(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
        )
        val dayOccurrences = occurrencesOn(plan)
        val events = listOf(
            reminderEvent(
                dayOccurrences.at(LocalTime.of(10, 0)),
                occurredAt = Instant.parse("2025-01-05T10:00:00Z"),
                id = 1L
            ),
            reminderEvent(
                dayOccurrences.at(LocalTime.of(14, 0)),
                occurredAt = Instant.parse("2025-01-05T11:00:00Z"),
                id = 2L
            )
        )
        return HistoricalProjectionBuilder.derive(
            occurrences = dayOccurrences,
            events = events,
            now = now,
            displayZone = utc
        )
    }

    @Test
    fun `a day of history ignores the occurrences that have not arrived`() {
        val projection = mixedDayProjection()
        assertEquals(1, projection.futureOccurrences.size)

        val historyDay = HistoricalReadModel.day(projection, day)

        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(14, 0)),
            localTimes(historyDay.entries)
        )
        assertEquals(2, historyDay.recordedCount)
        assertEquals(1, historyDay.unrecordedCount)
        assertEquals(0, historyDay.unmatchedActualCount)
    }

    @Test
    fun `range totals never count an upcoming occurrence as unrecorded`() {
        val projection = mixedDayProjection()

        val range = HistoricalReadModel.range(projection, day, day)

        assertEquals(listOf(day), range.days.map { it.date })
        assertEquals(2, range.recordedCount)
        assertEquals(1, range.unrecordedCount)
        assertEquals(0, range.unmatchedActualCount)
        assertTrue(
            "the 20:00 occurrence is upcoming context, not a history entry",
            localTimes(range.days.single().entries).none { it == LocalTime.of(20, 0) }
        )
    }

    @Test
    fun `history never invents an entry or a day for a future date`() {
        val plan = planOf(listOf(LocalTime.of(8, 0)))
        val span = occurrencesOn(plan, date = day.minusDays(1), days = 3L)

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = span,
            events = emptyList(),
            now = now,
            displayZone = utc
        )

        // Yesterday and today have arrived; tomorrow's 08:00 has not.
        assertEquals(2, projection.unrecordedOccurrences.size)
        assertEquals(1, projection.futureOccurrences.size)
        assertEquals(tomorrow, projection.futureOccurrences.single().occurrence.scheduledLocalDateTime.toLocalDate())

        val futureDay = HistoricalReadModel.day(projection, tomorrow)
        assertTrue("a future date has no history", futureDay.entries.isEmpty())
        assertTrue(futureDay.isEmpty)
        assertEquals(0, futureDay.recordedCount + futureDay.unrecordedCount + futureDay.unmatchedActualCount)

        val range = HistoricalReadModel.range(projection, day.minusDays(1), tomorrow)
        assertEquals(
            "only days that actually carry history are returned",
            listOf(day.minusDays(1), day),
            range.days.map { it.date }
        )
        assertEquals(2, range.unrecordedCount)
    }

    @Test
    fun `previous dates keep every unmatched occurrence as unrecorded`() {
        val plan = planOf(listOf(LocalTime.of(8, 0)))
        val span = occurrencesOn(plan, date = day.minusDays(3), days = 3L)

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = span,
            events = emptyList(),
            now = now,
            displayZone = utc
        )

        assertEquals(3, projection.unrecordedOccurrences.size)
        assertTrue(
            "finished days may not lose an occurrence to the upcoming horizon",
            projection.futureOccurrences.isEmpty()
        )

        val range = HistoricalReadModel.range(projection, day.minusDays(3), day.minusDays(1))
        assertEquals(
            listOf(day.minusDays(3), day.minusDays(2), day.minusDays(1)),
            range.days.map { it.date }
        )
        assertEquals(3, range.unrecordedCount)
        assertEquals(0, range.recordedCount)
        range.days.forEach { assertEquals(1, it.unrecordedCount) }
    }
}
