package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Historical projection semantics (v1.7-A mandatory backlog items M2, M3, M5, M6, M8).
 *
 * Covers: unmatched/orphan intake retention, deleted plan survival, edited plan
 * schedule-context labelling, display-date attribution across timezones and the
 * DST gap/overlap limits of the existing generator.
 */
class HistoricalProjectionIntakeTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

    // ---------- M8: unmatched intake retention ----------

    @Test
    fun `unmatched intake keeps every recoverable field`() {
        val event = RecordedMedicationEvent(
            eventId = UUID(7L, 1L),
            occurredAt = Instant.parse("2025-01-02T06:30:00Z"),
            slotId = UUID(3L, 3L),
            matchKey = MedicationMatchKey("INJECTION", "EV", 5.0),
            source = MedicationIntakeSource.WEAR,
            localDate = LocalDate.of(2025, 1, 2),
            zoneId = shanghai
        )

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = emptyList(),
            events = listOf(event),
            now = Instant.parse("2025-01-02T12:00:00Z"),
            displayZone = utc
        )

        val intake = projection.unmatchedIntakes.single()
        assertEquals(event.eventId, intake.event.eventId)
        assertEquals(event.occurredAt, intake.event.occurredAt)
        assertEquals(event.slotId, intake.event.slotId)
        assertEquals(event.matchKey, intake.event.matchKey)
        assertEquals(MedicationIntakeSource.WEAR, intake.source)
        assertEquals(event.localDate, intake.event.localDate)
        assertEquals(event.zoneId, intake.event.zoneId)
        assertEquals(HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE, intake.displayDateProvenance)
    }

    @Test
    fun `only manual source may be presented as a manual intake`() {
        val sources = MedicationIntakeSource.entries
        val events = sources.mapIndexed { index, source ->
            RecordedMedicationEvent(
                eventId = UUID(7L, 100L + index),
                occurredAt = Instant.parse("2025-01-02T06:00:00Z"),
                slotId = null,
                matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
                source = source,
                localDate = LocalDate.of(2025, 1, 2)
            )
        }

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = emptyList(),
            events = events,
            now = Instant.parse("2025-01-02T12:00:00Z"),
            displayZone = utc
        )

        assertEquals(sources.size, projection.unmatchedIntakes.size)
        assertEquals(
            listOf(MedicationIntakeSource.MANUAL),
            projection.unmatchedIntakes.filter { it.isManualIntake }.map { it.source }
        )
    }

    // ---------- M2: deleted plan survival ----------

    @Test
    fun `recorded event survives plan deletion`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(8, 0)))
        val generated = occurrences(listOf(plan), "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z", utc).single()
        val event = RecordedMedicationEvent(
            eventId = UUID(7L, 2L),
            occurredAt = Instant.parse("2025-01-02T08:05:00Z"),
            slotId = generated.slotId,
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
            source = MedicationIntakeSource.REMINDER,
            localDate = LocalDate.of(2025, 1, 2),
            zoneId = utc
        )

        // The plan is gone: no occurrences can be generated from the current state.
        val projection = HistoricalProjectionBuilder.derive(
            occurrences = emptyList(),
            events = listOf(event),
            now = Instant.parse("2025-01-02T12:00:00Z"),
            displayZone = utc
        )

        assertTrue("the actual intake must not disappear", projection.matchedOccurrences.isEmpty())
        val intake = projection.unmatchedIntakes.single()
        assertEquals(event.eventId, intake.event.eventId)
        assertEquals(event.occurredAt, intake.event.occurredAt)
        assertEquals("event identity and its persisted date stay available", event.localDate, intake.event.localDate)
        assertEquals(HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE, intake.displayDateProvenance)
    }

    @Test
    fun `legacy orphan without persisted context reports a low confidence display date`() {
        val event = RecordedMedicationEvent(
            eventId = UUID(7L, 3L),
            occurredAt = Instant.parse("2025-01-02T20:00:00Z"),
            slotId = null,
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
            source = MedicationIntakeSource.LEGACY,
            localDate = null,
            zoneId = null
        )

        val parisProjection = HistoricalProjectionBuilder.derive(emptyList(), listOf(event), Instant.parse("2025-01-03T12:00:00Z"), paris)
        val shanghaiProjection = HistoricalProjectionBuilder.derive(emptyList(), listOf(event), Instant.parse("2025-01-03T12:00:00Z"), shanghai)

        val parisIntake = parisProjection.unmatchedIntakes.single()
        val shanghaiIntake = shanghaiProjection.unmatchedIntakes.single()

        assertEquals(HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED, parisIntake.displayDateProvenance)
        assertFalse(parisIntake.isOriginalLocalDate)
        assertEquals(LocalDate.of(2025, 1, 2), parisIntake.displayDate)
        assertEquals(LocalDate.of(2025, 1, 3), shanghaiIntake.displayDate)
        assertEquals("the absolute instant never changes", event.occurredAt, shanghaiIntake.event.occurredAt)
        assertEquals(event.occurredAt, parisIntake.event.occurredAt)
    }

    // ---------- M5: display date attribution ----------

    @Test
    fun `bound occurrence uses the intended local date`() {
        val plan = schedule(number = 2L, times = listOf(LocalTime.of(8, 0)))
        val generated = occurrences(listOf(plan), "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z", utc).single()
        val event = RecordedMedicationEvent(
            eventId = UUID(7L, 4L),
            occurredAt = Instant.parse("2025-01-02T08:05:00Z"),
            slotId = generated.slotId,
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
            source = MedicationIntakeSource.WIDGET,
            localDate = LocalDate.of(2025, 1, 2),
            zoneId = utc
        )

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = listOf(generated),
            events = listOf(event),
            now = Instant.parse("2025-01-02T12:00:00Z"),
            displayZone = shanghai
        )

        val entry = projection.matchedOccurrences.single()
        assertEquals(LocalDate.of(2025, 1, 2), entry.displayDate)
        assertEquals(HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE, entry.displayDateProvenance)
        assertTrue(entry.isOriginalLocalDate)
        assertEquals("persisted recording context stays available", event.localDate, entry.event.localDate)
    }

    @Test
    fun `persisted recording date is preserved when the display zone differs`() {
        // Recorded in Shanghai on 2025-01-03 (instant is still 2025-01-02 in UTC).
        val event = RecordedMedicationEvent(
            eventId = UUID(7L, 5L),
            occurredAt = Instant.parse("2025-01-02T20:00:00Z"),
            slotId = null,
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
            source = MedicationIntakeSource.MANUAL,
            localDate = LocalDate.of(2025, 1, 3),
            zoneId = shanghai
        )

        val projection = HistoricalProjectionBuilder.derive(emptyList(), listOf(event), Instant.parse("2025-01-03T12:00:00Z"), utc)

        val intake = projection.unmatchedIntakes.single()
        assertEquals(LocalDate.of(2025, 1, 3), intake.displayDate)
        assertEquals(HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE, intake.displayDateProvenance)
        assertEquals(shanghai, intake.event.zoneId)
    }

    // ---------- M3: edited plan semantics ----------

    @Test
    fun `edited slot time is current schedule context not a historical planned time`() {
        val occurrenceDate = LocalDate.of(2025, 1, 2)
        val planBefore = schedule(number = 3L, times = listOf(LocalTime.of(8, 0)))
        val occurrenceBefore = occurrences(listOf(planBefore), "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z", utc).single()

        // The user records the dose against that occurrence...
        val event = RecordedMedicationEvent(
            eventId = UUID(7L, 6L),
            occurredAt = Instant.parse("2025-01-02T08:05:00Z"),
            slotId = occurrenceBefore.slotId,
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
            source = MedicationIntakeSource.MANUAL,
            localDate = occurrenceDate,
            zoneId = utc
        )

        // ...and later edits the slot time from 08:00 to 09:00 (same slot identity).
        val planAfter = schedule(number = 3L, times = listOf(LocalTime.of(9, 0)))
        val occurrenceAfter = occurrences(listOf(planAfter), "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z", utc).single()

        assertEquals("slot identity survives a time edit", occurrenceBefore.slotId, occurrenceAfter.slotId)
        assertEquals(LocalTime.of(9, 0), occurrenceAfter.scheduledLocalDateTime.toLocalTime())

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = listOf(occurrenceAfter),
            events = listOf(event),
            now = Instant.parse("2025-01-02T12:00:00Z"),
            displayZone = utc
        )

        val entry = projection.matchedOccurrences.single()
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertEquals(occurrenceDate, entry.displayDate)
        assertEquals(
            "a regenerated scheduled time must be labelled as current schedule context",
            HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
            entry.scheduleTimeContext
        )
        assertEquals(
            "no historical planned snapshot concept may exist",
            listOf(HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT),
            HistoricalScheduleTimeContext.entries
        )
        assertEquals("the actual intake keeps its own instant", Instant.parse("2025-01-02T08:05:00Z"), entry.event.occurredAt)
    }

    // ---------- M6: DST gap and overlap ----------

    @Test
    fun `dst gap keeps wall clock intent separate from the resolved instant`() {
        val plan = schedule(number = 4L, times = listOf(LocalTime.of(2, 30)), createdAt = Instant.parse("2026-01-01T00:00:00Z"))
        val generated = occurrences(listOf(plan), "2026-03-28T00:00:00Z", "2026-03-30T00:00:00Z", paris)
            .single { it.scheduledLocalDateTime.toLocalDate() == LocalDate.of(2026, 3, 29) }

        assertEquals("wall clock intent is preserved", LocalTime.of(2, 30), generated.scheduledLocalDateTime.toLocalTime())
        assertEquals("resolved instant is the post-gap instant", Instant.parse("2026-03-29T01:30:00Z"), generated.scheduledAt)

        val event = RecordedMedicationEvent(
            eventId = UUID(7L, 7L),
            occurredAt = Instant.parse("2026-03-29T01:30:00Z"),
            slotId = generated.slotId,
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
            source = MedicationIntakeSource.REMINDER,
            localDate = LocalDate.of(2026, 3, 29),
            zoneId = paris
        )

        val projection = HistoricalProjectionBuilder.derive(listOf(generated), listOf(event), Instant.parse("2026-03-29T12:00:00Z"), paris)
        val entry = projection.matchedOccurrences.single()

        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertEquals(
            "matching and timing deltas use the resolved instant",
            Duration.ZERO,
            Duration.between(entry.occurrence.scheduledAt, entry.event.occurredAt)
        )
        assertEquals(LocalDate.of(2026, 3, 29), entry.displayDate)
    }

    @Test
    fun `dst overlap materializes one occurrence and exposes the second local clock hour as a delta`() {
        val plan = schedule(number = 5L, times = listOf(LocalTime.of(2, 30)), createdAt = Instant.parse("2026-01-01T00:00:00Z"))
        val overlapDay = occurrences(listOf(plan), "2026-10-24T00:00:00Z", "2026-10-26T00:00:00Z", paris)
            .filter { it.scheduledLocalDateTime.toLocalDate() == LocalDate.of(2026, 10, 25) }

        assertEquals("the repeated local clock hour must not create a second occurrence", 1, overlapDay.size)
        val occurrence = overlapDay.single()
        assertEquals("earlier offset is the materialized instant", Instant.parse("2026-10-25T00:30:00Z"), occurrence.scheduledAt)

        // A real intake during the second 02:30 (CET) still binds to the single occurrence.
        val secondTwoThirty = RecordedMedicationEvent(
            eventId = UUID(7L, 8L),
            occurredAt = Instant.parse("2026-10-25T01:30:00Z"),
            slotId = occurrence.slotId,
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0),
            source = MedicationIntakeSource.WEAR,
            localDate = LocalDate.of(2026, 10, 25),
            zoneId = paris
        )

        val projection = HistoricalProjectionBuilder.derive(listOf(occurrence), listOf(secondTwoThirty), Instant.parse("2026-10-25T12:00:00Z"), paris)
        val entry = projection.matchedOccurrences.single()

        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertFalse(entry.crossesLocalDateBoundary)
        assertEquals(
            "the documented overlap limitation shows up as a +1h instant difference",
            Duration.ofHours(1),
            Duration.between(entry.occurrence.scheduledAt, entry.event.occurredAt)
        )
        assertEquals("still exactly one occurrence for that date", 1, projection.entriesOn(LocalDate.of(2026, 10, 25)).count { it is MatchedHistoricalOccurrence })
    }
}
