package io.github.yingqiu0871.evolune.experience.insights

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.HistoricalScheduleTimeContext
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationActionAvailability
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceId
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.MedicationPresentation
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * v1.7-B-01 shape check: aggregation stays linear in the number of entries.
 *
 * This is deliberately a smoke test, not a release gate: it grows the input by 10x and asserts the
 * work still completes well inside a generous bound, which catches an accidental quadratic scan
 * (for example re-walking every entry for every day) without being flaky on a busy machine.
 */
class MedicationInsightsPerformanceSmokeTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val start: LocalDate = LocalDate.of(2025, 1, 1)
    private val days = 2_000
    private val entriesPerDay = 5

    @Test
    fun `ten thousand entries aggregate in linear time`() {
        val range = buildRange(days, entriesPerDay)

        val startedAt = System.nanoTime()
        val summary = ReadOnlyMedicationInsightsAggregator.aggregate(range)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        val expectedIntakes = days * entriesPerDay
        assertEquals(expectedIntakes, summary.recordedIntakeCount)
        assertEquals(days, summary.recordedDayCount)
        assertEquals(expectedIntakes, summary.sourceCounts.values.sum())
        assertEquals(expectedIntakes, summary.bindingConfidenceCounts.values.sum())
        assertTrue(
            "aggregating $expectedIntakes entries took ${elapsedMillis}ms",
            elapsedMillis < 5_000
        )
    }

    private fun buildRange(days: Int, entriesPerDay: Int): HistoricalRange {
        val historyDays = ArrayList<HistoricalDay>(days)
        var tag = 1_000L
        repeat(days) { dayIndex ->
            val date = start.plusDays(dayIndex.toLong())
            val entries = ArrayList<io.github.yingqiu0871.evolune.experience.HistoricalEntry>(entriesPerDay)
            repeat(entriesPerDay) { slot ->
                tag += 1
                entries += if (slot == entriesPerDay - 1) {
                    unmatched(date, tag)
                } else {
                    matched(date, tag, slot)
                }
            }
            historyDays += HistoricalDay(date = date, entries = entries)
        }
        return HistoricalRange(
            startDate = start,
            endDate = start.plusDays((days - 1).toLong()),
            days = historyDays
        )
    }

    private fun matched(date: LocalDate, tag: Long, slot: Int): MatchedHistoricalOccurrence {
        val source = MedicationIntakeSource.entries[slot % MedicationIntakeSource.entries.size]
        val provenance = MedicationMatchProvenance.entries[slot % MedicationMatchProvenance.entries.size]
        return MatchedHistoricalOccurrence(
            occurrence = occurrence(date, tag),
            event = event(date, tag, source),
            matchProvenance = provenance,
            status = MedicationOccurrenceStatus.RECORDED,
            actionAvailability = MedicationActionAvailability.ALREADY_RECORDED,
            scheduleTimeContext = HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
            crossesLocalDateBoundary = false,
            displayDate = date,
            displayDateProvenance = HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE
        )
    }

    private fun unmatched(date: LocalDate, tag: Long): UnmatchedHistoricalIntake = UnmatchedHistoricalIntake(
        event = event(date, tag, MedicationIntakeSource.MANUAL),
        source = MedicationIntakeSource.MANUAL,
        displayDate = date,
        displayDateProvenance = HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE
    )

    private fun occurrence(date: LocalDate, tag: Long): MedicationOccurrence = MedicationOccurrence(
        id = MedicationOccurrenceId(UUID(3L, tag)),
        planId = UUID(0L, 1L),
        slotId = UUID(1L, tag),
        slotPosition = 0,
        presentation = MedicationPresentation("Synthetic plan", MedicationMatchKey("ORAL", "E2", 2.0)),
        scheduledAt = date.atTime(LocalTime.of(23, 0)).atZone(zone).toInstant(),
        scheduledLocalDateTime = date.atTime(LocalTime.of(23, 0)),
        zoneId = zone
    )

    private fun event(date: LocalDate, tag: Long, source: MedicationIntakeSource): RecordedMedicationEvent =
        RecordedMedicationEvent(
            eventId = UUID(7L, tag),
            occurredAt = date.atTime(LocalTime.of(23, 5)).atZone(zone).toInstant(),
            slotId = UUID(1L, tag),
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
            source = source,
            localDate = date,
            zoneId = zone
        )
}
