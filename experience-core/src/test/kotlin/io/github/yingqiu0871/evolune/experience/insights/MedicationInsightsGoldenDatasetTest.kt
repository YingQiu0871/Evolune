package io.github.yingqiu0871.evolune.experience.insights

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
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
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * v1.7-B-01 golden dataset: the math must match the frozen B-00 worked example
 * (`evidence/b-00-r1/denominator-examples.md`, reproducible through its script).
 *
 * Dataset shape (range 2025-01-01 .. 2025-01-07, current plan = daily 23:00):
 * - 4 exact matched (REMINDER x2, WIDGET, WEAR), E2 2.0 mg each
 * - 1 null-slot time-window matched (MANUAL), E2 2.0 mg  -> MEDIUM
 * - 1 null-slot same-day matched (MANUAL), EV 1.0 mg      -> LOW
 * - 1 unrecorded occurrence (schedule dose must never contribute)
 * - unmatched: MANUAL EV 5.0 mg, LEGACY E2 1.0 mg (current-time-zone-derived date),
 *   JSON_V1 3.0 mg with an unmappable medication key
 */
class MedicationInsightsGoldenDatasetTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val start: LocalDate = LocalDate.of(2025, 1, 1)

    private fun date(offset: Long): LocalDate = start.plusDays(offset)

    @Test
    fun `the golden dataset reproduces the frozen B-00 worked example`() {
        val range = HistoricalRange(
            startDate = start,
            endDate = date(6),
            days = listOf(
                day(0, matched(date(0), 1, MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, MedicationIntakeSource.REMINDER)),
                day(
                    1,
                    matched(date(1), 2, MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, MedicationIntakeSource.REMINDER),
                    unmatched(date(1), 9, MedicationIntakeSource.LEGACY, dose = 1.0, medicationKey = "E2")
                ),
                day(
                    2,
                    matched(date(2), 3, MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, MedicationIntakeSource.WIDGET),
                    unmatched(date(2), 10, MedicationIntakeSource.MANUAL, dose = 5.0, medicationKey = "EV")
                ),
                day(
                    3,
                    matched(date(3), 4, MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, MedicationIntakeSource.WEAR),
                    unmatched(date(3), 11, MedicationIntakeSource.JSON_V1, dose = 3.0, medicationKey = "UNMAPPED")
                ),
                day(4, matched(date(4), 5, MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, MedicationIntakeSource.MANUAL)),
                day(
                    5,
                    matched(
                        date(5), 6, MedicationMatchProvenance.NULL_SLOT_SAME_DAY, MedicationIntakeSource.MANUAL,
                        dose = 1.0, medicationKey = "EV"
                    )
                ),
                day(6, unrecorded(date(6), 7))
            )
        )

        val summary = ReadOnlyMedicationInsightsAggregator.aggregate(range)

        // counts
        assertEquals(9, summary.recordedIntakeCount)
        assertEquals(6, summary.recordedDayCount)
        assertEquals(6, summary.matchedOccurrenceCount)
        assertEquals(1, summary.unrecordedOccurrenceCount)
        assertEquals(3, summary.unmatchedActualIntakeCount)

        // sources
        assertEquals(1, summary.sourceCounts.getValue(MedicationIntakeSource.JSON_V1))
        assertEquals(1, summary.sourceCounts.getValue(MedicationIntakeSource.LEGACY))
        assertEquals(3, summary.sourceCounts.getValue(MedicationIntakeSource.MANUAL))
        assertEquals(2, summary.sourceCounts.getValue(MedicationIntakeSource.REMINDER))
        assertEquals(1, summary.sourceCounts.getValue(MedicationIntakeSource.WEAR))
        assertEquals(1, summary.sourceCounts.getValue(MedicationIntakeSource.WIDGET))
        assertEquals(summary.recordedIntakeCount, summary.sourceCounts.values.sum())

        // binding confidence (recorded intakes only, four buckets)
        assertEquals(4, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.HIGH))
        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.MEDIUM))
        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.LOW))
        assertEquals(3, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.NONE))
        assertEquals(summary.recordedIntakeCount, summary.bindingConfidenceCounts.values.sum())

        // per-medication dose totals (known identities only) and the unknown-identity count
        assertEquals(11.0, summary.perMedicationDoseTotalsMg.getValue(MedicationIdentityKey.E2), 0.0)
        assertEquals(6.0, summary.perMedicationDoseTotalsMg.getValue(MedicationIdentityKey.EV), 0.0)
        assertEquals(2, summary.perMedicationDoseTotalsMg.size)
        assertEquals(1, summary.unknownIdentityRecordedIntakeCount)

        // date attribution disclosure
        assertTrue(summary.containsCurrentTimezoneDerivedDates)
    }

    @Test
    fun `a fully attributed dataset raises no timezone disclosure`() {
        val range = HistoricalRange(
            startDate = start,
            endDate = start,
            days = listOf(
                day(0, matched(date(0), 1, MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, MedicationIntakeSource.REMINDER))
            )
        )

        val summary = ReadOnlyMedicationInsightsAggregator.aggregate(range)

        assertFalse(summary.containsCurrentTimezoneDerivedDates)
    }

    // ---------- fixtures ----------

    private fun day(offset: Long, vararg entries: HistoricalEntry): HistoricalDay =
        HistoricalDay(date = date(offset), entries = entries.toList())

    private fun matched(
        date: LocalDate,
        tag: Long,
        provenance: MedicationMatchProvenance,
        source: MedicationIntakeSource,
        dose: Double = 2.0,
        medicationKey: String = "E2"
    ): MatchedHistoricalOccurrence = MatchedHistoricalOccurrence(
        occurrence = occurrence(date, tag, dose, medicationKey),
        event = event(date, tag, source, dose, medicationKey),
        matchProvenance = provenance,
        status = MedicationOccurrenceStatus.RECORDED,
        actionAvailability = MedicationActionAvailability.ALREADY_RECORDED,
        scheduleTimeContext = HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
        crossesLocalDateBoundary = false,
        displayDate = date,
        displayDateProvenance = HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE
    )

    private fun unmatched(
        date: LocalDate,
        tag: Long,
        source: MedicationIntakeSource,
        dose: Double,
        medicationKey: String
    ): UnmatchedHistoricalIntake = UnmatchedHistoricalIntake(
        event = event(date, tag, source, dose, medicationKey),
        source = source,
        displayDate = date,
        displayDateProvenance = if (source == MedicationIntakeSource.LEGACY) {
            HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED
        } else {
            HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE
        }
    )

    private fun unrecorded(date: LocalDate, tag: Long): UnrecordedHistoricalOccurrence =
        UnrecordedHistoricalOccurrence(
            occurrence = occurrence(date, tag, 2.0, "E2"),
            status = MedicationOccurrenceStatus.PAST_UNRECORDED,
            actionAvailability = MedicationActionAvailability.WINDOW_EXPIRED,
            displayDate = date
        )

    private fun occurrence(date: LocalDate, tag: Long, dose: Double, medicationKey: String): MedicationOccurrence =
        MedicationOccurrence(
            id = MedicationOccurrenceId(UUID(3L, tag)),
            planId = UUID(0L, 1L),
            slotId = UUID(1L, tag),
            slotPosition = 0,
            presentation = MedicationPresentation("Synthetic plan", MedicationMatchKey("ORAL", medicationKey, dose)),
            scheduledAt = date.atTime(LocalTime.of(23, 0)).atZone(zone).toInstant(),
            scheduledLocalDateTime = date.atTime(LocalTime.of(23, 0)),
            zoneId = zone
        )

    private fun event(
        date: LocalDate,
        tag: Long,
        source: MedicationIntakeSource,
        dose: Double,
        medicationKey: String
    ): RecordedMedicationEvent = RecordedMedicationEvent(
        eventId = UUID(7L, tag),
        occurredAt = date.atTime(LocalTime.of(23, 5)).atZone(zone).toInstant(),
        slotId = UUID(1L, tag),
        matchKey = MedicationMatchKey("ORAL", medicationKey, dose),
        source = source,
        localDate = date,
        zoneId = zone
    )
}
