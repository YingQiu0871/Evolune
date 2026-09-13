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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * v1.7-B-01 focused semantics of [ReadOnlyMedicationInsightsAggregator].
 *
 * The aggregator consumes frozen history values only, so these tests build [HistoricalRange]
 * values directly (never through the matcher or a persistence layer) and then assert the frozen
 * counting, coverage, confidence, source, identity, dose, disclosure and invariant behaviour.
 */
class MedicationInsightsAggregatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val day: LocalDate = LocalDate.of(2025, 1, 5)

    // ---------- counts ----------

    @Test
    fun `matched exact inferred unrecorded and unmatched are counted per frozen rules`() {
        val summary = aggregate(
            dayOf(
                matched(tag = 1L, provenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, source = MedicationIntakeSource.REMINDER),
                matched(tag = 2L, provenance = MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE, source = MedicationIntakeSource.WEAR),
                matched(tag = 3L, provenance = MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, source = MedicationIntakeSource.MANUAL),
                matched(tag = 4L, provenance = MedicationMatchProvenance.NULL_SLOT_SAME_DAY, source = MedicationIntakeSource.MANUAL),
                unrecorded(tag = 5L),
                unmatched(tag = 6L, source = MedicationIntakeSource.LEGACY)
            )
        )

        assertEquals(5, summary.recordedIntakeCount)
        assertEquals(4, summary.matchedOccurrenceCount)
        assertEquals(1, summary.unrecordedOccurrenceCount)
        assertEquals(1, summary.unmatchedActualIntakeCount)
    }

    // ---------- days ----------

    @Test
    fun `recorded days count distinct display dates and ignore unrecorded-only days`() {
        val range = HistoricalRange(
            startDate = day,
            endDate = day.plusDays(2),
            days = listOf(
                dayOf(matched(), matched(tag = 2L), unmatched(tag = 3L), date = day),
                dayOf(unrecorded(tag = 4L, date = day.plusDays(1)), date = day.plusDays(1)),
                dayOf(matched(tag = 5L, displayDate = day.plusDays(2)), date = day.plusDays(2))
            )
        )

        val summary = ReadOnlyMedicationInsightsAggregator.aggregate(range)

        assertEquals(4, summary.recordedIntakeCount)
        assertEquals(2, summary.recordedDayCount)
        assertEquals(1, summary.unrecordedOccurrenceCount)
    }

    // ---------- coverage ----------

    @Test
    fun `coverage counts include every matched provenance but never unmatched intakes`() {
        val summary = aggregate(
            dayOf(
                matched(provenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE),
                matched(tag = 2L, provenance = MedicationMatchProvenance.NULL_SLOT_SAME_DAY),
                unmatched(tag = 3L)
            )
        )

        assertEquals(2, summary.matchedOccurrenceCount)
        assertEquals(0, summary.unrecordedOccurrenceCount)
    }

    // ---------- confidence ----------

    @Test
    fun `binding confidence maps the four provenances and unmatched to NONE`() {
        val summary = aggregate(
            dayOf(
                matched(provenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE),
                matched(tag = 2L, provenance = MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE),
                matched(tag = 3L, provenance = MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW),
                matched(tag = 4L, provenance = MedicationMatchProvenance.NULL_SLOT_SAME_DAY),
                unmatched(tag = 5L),
                unrecorded(tag = 6L)
            )
        )

        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.HIGH))
        assertEquals(2, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.MEDIUM))
        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.LOW))
        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.NONE))
        assertEquals(InsightsBindingConfidence.entries.toSet(), summary.bindingConfidenceCounts.keys)
    }

    // ---------- sources ----------

    @Test
    fun `all six authoritative sources are reported and sum to the recorded intakes`() {
        var tag = 1L
        val entries = MedicationIntakeSource.entries.map { source ->
            val entry = matched(tag = tag++, source = source)
            entry
        }
        val summary = aggregate(dayOf(*entries.toTypedArray()))

        assertEquals(6, summary.recordedIntakeCount)
        MedicationIntakeSource.entries.forEach { source ->
            assertEquals("source $source", 1, summary.sourceCounts.getValue(source))
        }
        assertEquals(summary.recordedIntakeCount, summary.sourceCounts.values.sum())
    }

    @Test
    fun `legacy is never merged into manual`() {
        val summary = aggregate(
            dayOf(
                unmatched(tag = 1L, source = MedicationIntakeSource.LEGACY),
                unmatched(tag = 2L, source = MedicationIntakeSource.MANUAL)
            )
        )

        assertEquals(1, summary.sourceCounts.getValue(MedicationIntakeSource.LEGACY))
        assertEquals(1, summary.sourceCounts.getValue(MedicationIntakeSource.MANUAL))
    }

    // ---------- source / provenance independence ----------

    @Test
    fun `source and binding confidence are independent dimensions`() {
        val summary = aggregate(
            dayOf(
                matched(provenance = MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, source = MedicationIntakeSource.MANUAL),
                matched(tag = 2L, provenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, source = MedicationIntakeSource.WEAR),
                matched(tag = 3L, provenance = MedicationMatchProvenance.NULL_SLOT_SAME_DAY, source = MedicationIntakeSource.WEAR),
                unmatched(tag = 4L, source = MedicationIntakeSource.LEGACY)
            )
        )

        assertEquals(2, summary.sourceCounts.getValue(MedicationIntakeSource.WEAR))
        assertEquals(1, summary.sourceCounts.getValue(MedicationIntakeSource.MANUAL))
        assertEquals(1, summary.sourceCounts.getValue(MedicationIntakeSource.LEGACY))
        // the same source spans two different confidences
        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.HIGH))
        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.MEDIUM))
        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.LOW))
        assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.NONE))
    }

    // ---------- identity classifier ----------

    @Test
    fun `known ester keys classify as known`() {
        listOf("E2", "EB", "EV", "EC", "EN").forEach { key ->
            val identity = MedicationIdentityClassifier.classify(MedicationMatchKey("ORAL", key, 2.0))
            assertEquals(MedicationIdentityStatus.KNOWN, identity.status)
            assertEquals(key, identity.key?.name)
        }
    }

    @Test
    fun `unknown or foreign keys classify as partial without inventing a drug`() {
        listOf("CPA", "spironolactone", "UNKNOWN").forEach { key ->
            val identity = MedicationIdentityClassifier.classify(MedicationMatchKey("ORAL", key, 2.0))
            assertEquals(MedicationIdentityStatus.PARTIAL, identity.status)
            assertEquals(null, identity.key)
        }
    }

    @Test
    fun `anti-androgen routes are unavailable even when they carry an ester placeholder`() {
        val identity = MedicationIdentityClassifier.classify(MedicationMatchKey("ANTIANDROGEN", "E2", 50.0))

        assertEquals(MedicationIdentityStatus.UNAVAILABLE, identity.status)
        assertEquals(null, identity.key)
    }

    // ---------- dose ----------

    @Test
    fun `dose totals use the authoritative event dose per known medication`() {
        val summary = aggregate(
            dayOf(
                matched(dose = 2.0, medicationKey = "E2"),
                unmatched(tag = 2L, dose = 5.0, medicationKey = "EV")
            )
        )

        assertEquals(2.0, summary.perMedicationDoseTotalsMg.getValue(MedicationIdentityKey.E2), 0.0)
        assertEquals(5.0, summary.perMedicationDoseTotalsMg.getValue(MedicationIdentityKey.EV), 0.0)
    }

    @Test
    fun `the authoritative event dose wins over the generated schedule dose`() {
        // generated occurrence carries the current plan dose (2 mg) while the recorded intake is 3 mg
        val entry = matched(occurrenceDose = 2.0, dose = 3.0)

        val summary = aggregate(dayOf(entry))

        assertEquals(3.0, summary.perMedicationDoseTotalsMg.getValue(MedicationIdentityKey.E2), 0.0)
    }

    @Test
    fun `unrecorded schedule doses never enter dose totals`() {
        val summary = aggregate(dayOf(unrecorded(dose = 99.0)))

        assertEquals(0, summary.recordedIntakeCount)
        assertTrue(summary.perMedicationDoseTotalsMg.isEmpty())
    }

    @Test
    fun `unknown identity intakes are counted but never summed into a dose`() {
        val summary = aggregate(
            dayOf(
                matched(tag = 1L, dose = 50.0, routeKey = "ANTIANDROGEN", medicationKey = "E2"),
                unmatched(tag = 2L, dose = 3.0, medicationKey = "UNMAPPED")
            )
        )

        assertEquals(2, summary.unknownIdentityRecordedIntakeCount)
        assertTrue(summary.perMedicationDoseTotalsMg.isEmpty())
    }

    // ---------- disclosure ----------

    @Test
    fun `current timezone derived dates raise the disclosure flag`() {
        val flagged = aggregate(
            dayOf(unmatched(tag = 1L, displayDateProvenance = HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED))
        )
        val clean = aggregate(dayOf(matched(), unrecorded(tag = 2L)))

        assertTrue(flagged.containsCurrentTimezoneDerivedDates)
        assertFalse(clean.containsCurrentTimezoneDerivedDates)
    }

    // ---------- invariants / error policy ----------

    @Test
    fun `a duplicated authoritative event is rejected instead of double counted`() {
        // the same authoritative event id on two different (internally consistent) days
        val first = matched(tag = 7L, displayDate = day)
        val second = matched(tag = 7L, displayDate = day.plusDays(1))
        val range = HistoricalRange(
            startDate = day,
            endDate = day.plusDays(1),
            days = listOf(
                dayOf(first, date = day),
                dayOf(second, date = day.plusDays(1))
            )
        )

        val failure = runCatching { ReadOnlyMedicationInsightsAggregator.aggregate(range) }.exceptionOrNull()

        assertTrue("expected a contract violation, got $failure", failure is InsightsContractViolationException)
        assertTrue(failure!!.message!!.contains("appears more than once"))
    }

    @Test
    fun `a duplicated day is rejected`() {
        val range = HistoricalRange(
            startDate = day,
            endDate = day,
            days = listOf(dayOf(matched()), dayOf(matched(tag = 2L)))
        )

        val failure = runCatching { ReadOnlyMedicationInsightsAggregator.aggregate(range) }.exceptionOrNull()

        assertTrue(failure is InsightsContractViolationException)
        assertTrue(failure!!.message!!.contains("duplicate history day"))
    }

    @Test
    fun `an entry outside its day is rejected`() {
        val range = HistoricalRange(
            startDate = day,
            endDate = day,
            days = listOf(HistoricalDay(date = day, entries = listOf(matched(displayDate = day.plusDays(1)))))
        )

        val failure = runCatching { ReadOnlyMedicationInsightsAggregator.aggregate(range) }.exceptionOrNull()

        assertTrue(failure is InsightsContractViolationException)
        assertTrue(failure!!.message!!.contains("does not match its history day"))
    }

    @Test
    fun `day counts are derived from entries so the recomputation is defence in depth`() {
        // The read model derives its per-day counts from the entries themselves, so an
        // inconsistent day cannot be constructed through the public model. The aggregator still
        // recomputes and compares them (fail fast) in case that model ever stores counts.
        val entries = listOf(matched(), unrecorded(tag = 2L), unmatched(tag = 3L))
        val derived = HistoricalDay(date = day, entries = entries)

        assertEquals(entries.count { it is MatchedHistoricalOccurrence }, derived.recordedCount)
        assertEquals(entries.count { it is UnrecordedHistoricalOccurrence }, derived.unrecordedCount)
        assertEquals(entries.count { it is UnmatchedHistoricalIntake }, derived.unmatchedActualCount)

        // and the aggregator agrees with that derivation
        val summary = aggregate(dayOf(*entries.toTypedArray()))
        assertEquals(derived.recordedCount, summary.matchedOccurrenceCount)
        assertEquals(derived.unrecordedCount, summary.unrecordedOccurrenceCount)
        assertEquals(derived.unmatchedActualCount, summary.unmatchedActualIntakeCount)
    }

    @Test
    fun `an inverted range is rejected`() {
        val range = HistoricalRange(startDate = day.plusDays(1), endDate = day, days = emptyList())

        val failure = runCatching { ReadOnlyMedicationInsightsAggregator.aggregate(range) }.exceptionOrNull()

        assertTrue(failure is InsightsContractViolationException)
    }

    @Test
    fun `an empty range aggregates to zeros without failing`() {
        val summary = ReadOnlyMedicationInsightsAggregator.aggregate(
            HistoricalRange(startDate = day, endDate = day.plusDays(6), days = emptyList())
        )

        assertEquals(day, summary.startDate)
        assertEquals(day.plusDays(6), summary.endDate)
        assertEquals(0, summary.recordedIntakeCount)
        assertEquals(0, summary.recordedDayCount)
        assertEquals(0, summary.matchedOccurrenceCount)
        assertEquals(0, summary.unrecordedOccurrenceCount)
        assertEquals(0, summary.unmatchedActualIntakeCount)
        assertEquals(0, summary.unknownIdentityRecordedIntakeCount)
        assertFalse(summary.containsCurrentTimezoneDerivedDates)
        assertTrue(summary.perMedicationDoseTotalsMg.isEmpty())
        assertEquals(MedicationIntakeSource.entries.map { 0 }, summary.sourceCounts.values.toList())
        assertEquals(InsightsBindingConfidence.entries.map { 0 }, summary.bindingConfidenceCounts.values.toList())
    }

    // ---------- helpers ----------

    private fun aggregate(vararg days: HistoricalDay): MedicationInsightsSummary =
        ReadOnlyMedicationInsightsAggregator.aggregate(
            HistoricalRange(
                startDate = days.minOf { it.date },
                endDate = days.maxOf { it.date },
                days = days.toList()
            )
        )

    private fun dayOf(vararg entries: HistoricalEntry, date: LocalDate = day): HistoricalDay =
        HistoricalDay(date = date, entries = entries.toList())

    private fun matched(
        tag: Long = 1L,
        provenance: MedicationMatchProvenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE,
        source: MedicationIntakeSource = MedicationIntakeSource.REMINDER,
        dose: Double = 2.0,
        occurrenceDose: Double = 2.0,
        routeKey: String = "ORAL",
        medicationKey: String = "E2",
        displayDate: LocalDate = day,
        displayDateProvenance: HistoricalDisplayDateProvenance = HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE
    ): MatchedHistoricalOccurrence {
        val occurrence = occurrence(
            tag = tag,
            dose = occurrenceDose,
            routeKey = routeKey,
            medicationKey = medicationKey,
            date = displayDate
        )
        val event = event(
            tag = tag,
            source = source,
            dose = dose,
            routeKey = routeKey,
            medicationKey = medicationKey,
            date = displayDate
        )
        return MatchedHistoricalOccurrence(
            occurrence = occurrence,
            event = event,
            matchProvenance = provenance,
            status = MedicationOccurrenceStatus.RECORDED,
            actionAvailability = MedicationActionAvailability.ALREADY_RECORDED,
            scheduleTimeContext = HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
            crossesLocalDateBoundary = false,
            displayDate = displayDate,
            displayDateProvenance = displayDateProvenance
        )
    }

    private fun unrecorded(
        tag: Long = 1L,
        dose: Double = 2.0,
        routeKey: String = "ORAL",
        medicationKey: String = "E2",
        date: LocalDate = day
    ): UnrecordedHistoricalOccurrence = UnrecordedHistoricalOccurrence(
        occurrence = occurrence(tag = tag, dose = dose, routeKey = routeKey, medicationKey = medicationKey, date = date),
        status = MedicationOccurrenceStatus.PAST_UNRECORDED,
        actionAvailability = MedicationActionAvailability.WINDOW_EXPIRED,
        displayDate = date
    )

    private fun unmatched(
        tag: Long = 1L,
        source: MedicationIntakeSource = MedicationIntakeSource.MANUAL,
        dose: Double = 2.0,
        routeKey: String = "ORAL",
        medicationKey: String = "E2",
        date: LocalDate = day,
        displayDateProvenance: HistoricalDisplayDateProvenance =
            HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE
    ): UnmatchedHistoricalIntake = UnmatchedHistoricalIntake(
        event = event(tag = tag, source = source, dose = dose, routeKey = routeKey, medicationKey = medicationKey, date = date),
        source = source,
        displayDate = date,
        displayDateProvenance = displayDateProvenance
    )

    private fun occurrence(
        tag: Long,
        dose: Double,
        routeKey: String,
        medicationKey: String,
        date: LocalDate
    ): MedicationOccurrence = MedicationOccurrence(
        id = MedicationOccurrenceId(UUID(3L, tag)),
        planId = UUID(0L, 1L),
        slotId = UUID(1L, tag),
        slotPosition = 0,
        presentation = MedicationPresentation(
            planName = "Synthetic plan",
            matchKey = MedicationMatchKey(routeKey, medicationKey, dose)
        ),
        scheduledAt = date.atTime(LocalTime.of(23, 0)).atZone(zone).toInstant(),
        scheduledLocalDateTime = date.atTime(LocalTime.of(23, 0)),
        zoneId = zone
    )

    private fun event(
        tag: Long,
        source: MedicationIntakeSource,
        dose: Double,
        routeKey: String,
        medicationKey: String,
        date: LocalDate
    ): RecordedMedicationEvent = RecordedMedicationEvent(
        eventId = UUID(7L, tag),
        occurredAt = date.atTime(LocalTime.of(23, 5)).atZone(zone).toInstant(),
        slotId = UUID(1L, tag),
        matchKey = MedicationMatchKey(routeKey, medicationKey, dose),
        source = source,
        localDate = date,
        zoneId = zone
    )
}
