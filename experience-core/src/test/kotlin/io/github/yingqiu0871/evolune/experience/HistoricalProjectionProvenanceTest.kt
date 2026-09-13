package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Match provenance contract (v1.7-A mandatory backlog items M4 and M7).
 *
 * The provenance must describe the *real* decision of the single four-phase
 * matcher; it may not be re-derived by an independent matching implementation.
 */
class HistoricalProjectionProvenanceTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2025-01-02T12:00:00Z")

    @Test
    fun `phase one slot and local date match is exact`() {
        val plan = schedule(times = listOf(LocalTime.of(8, 0)))
        val occurrences = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z")
        val occurrence = occurrences.single()
        val event = eventAt(
            id = UUID(9L, 1L),
            instant = "2025-01-02T11:30:00Z",
            slotId = occurrence.slotId,
            localDate = LocalDate.of(2025, 1, 2),
            source = MedicationIntakeSource.REMINDER
        )

        val entry = matchedEntry(plan, listOf(event), occurrence.id)

        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertFalse(entry.crossesLocalDateBoundary)
    }

    @Test
    fun `phase two slot without local date is a bounded window match`() {
        val plan = schedule(times = listOf(LocalTime.of(8, 0)))
        val occurrence = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z").single()
        val event = eventAt(
            id = UUID(9L, 2L),
            instant = "2025-01-02T08:30:00Z",
            slotId = occurrence.slotId,
            localDate = null,
            source = MedicationIntakeSource.LEGACY
        )

        val entry = matchedEntry(plan, listOf(event), occurrence.id)

        assertEquals(MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE, entry.matchProvenance)
    }

    @Test
    fun `phase three legacy null slot inside the window is a window match`() {
        val plan = schedule(times = listOf(LocalTime.of(8, 0)))
        val occurrence = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z").single()
        val event = eventAt(
            id = UUID(9L, 3L),
            instant = "2025-01-02T08:30:00Z",
            slotId = null,
            localDate = null,
            source = MedicationIntakeSource.LEGACY
        )

        val entry = matchedEntry(plan, listOf(event), occurrence.id)

        assertEquals(MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, entry.matchProvenance)
        assertFalse(entry.crossesLocalDateBoundary)
    }

    @Test
    fun `phase four null slot outside the window is a same-day fallback match`() {
        val plan = schedule(times = listOf(LocalTime.of(8, 0)))
        val occurrence = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z").single()
        val event = eventAt(
            id = UUID(9L, 4L),
            instant = "2025-01-02T18:45:00Z",
            slotId = null,
            localDate = LocalDate.of(2025, 1, 2),
            source = MedicationIntakeSource.MANUAL
        )

        val entry = matchedEntry(plan, listOf(event), occurrence.id)

        assertEquals(MedicationMatchProvenance.NULL_SLOT_SAME_DAY, entry.matchProvenance)
    }

    @Test
    fun `cross date legacy match is inferred and flagged as crossing the local date boundary`() {
        val plan = schedule(times = listOf(LocalTime.of(0, 0)))
        val occurrence = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z").single()
        // Legacy row: no slot identity, no persisted local date, instant exactly one hour before.
        val event = eventAt(
            id = UUID(9L, 5L),
            instant = "2025-01-01T23:00:00Z",
            slotId = null,
            localDate = null,
            source = MedicationIntakeSource.LEGACY
        )

        val entry = matchedEntry(plan, listOf(event), occurrence.id)

        assertEquals(MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, entry.matchProvenance)
        assertTrue(
            "a 23:00 event bound to a 00:00 occurrence must be flagged as crossing the local date boundary",
            entry.crossesLocalDateBoundary
        )
    }

    @Test
    fun `closed window boundary is inclusive in both directions while one second outside stays unmatched`() {
        val plan = schedule(times = listOf(LocalTime.of(0, 0)))

        // Legacy rows carry no persisted local date, so only the bounded window can match them.
        val minusOneHour = eventAt(UUID(9L, 6L), "2025-01-01T23:00:00Z", null, null)
        val plusOneHour = eventAt(UUID(9L, 7L), "2025-01-02T01:00:00Z", null, null)
        val oneSecondLate = eventAt(UUID(9L, 8L), "2025-01-02T01:00:01Z", null, null)

        assertEquals(
            MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW,
            projection(plan, listOf(minusOneHour)).matchedOccurrences.single().matchProvenance
        )
        assertEquals(
            MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW,
            projection(plan, listOf(plusOneHour)).matchedOccurrences.single().matchProvenance
        )

        val outside = projection(plan, listOf(oneSecondLate))
        assertTrue("one second outside the closed window must stay unmatched", outside.matchedOccurrences.isEmpty())
        assertEquals(1, outside.unmatchedIntakes.size)
        assertEquals(HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED, outside.unmatchedIntakes.single().displayDateProvenance)
    }

    @Test
    fun `same-day fallback explains why a late null-slot candidate with a persisted date still matches`() {
        val plan = schedule(times = listOf(LocalTime.of(0, 0)))
        // Same persisted local date as the occurrence, but far outside the window:
        // this is the documented v1.2.2 same-day fallback, not a window match.
        val lateSameDay = eventAt(UUID(9L, 80L), "2025-01-02T01:00:01Z", null, LocalDate.of(2025, 1, 2))

        val entry = projection(plan, listOf(lateSameDay)).matchedOccurrences.single()
        assertEquals(MedicationMatchProvenance.NULL_SLOT_SAME_DAY, entry.matchProvenance)
    }

    @Test
    fun `ambiguous candidates stay unmatched instead of upgrading to certainty`() {
        // Two occurrences inside the same closed window around the legacy event.
        val plan = schedule(times = listOf(LocalTime.of(0, 0), LocalTime.of(0, 30)))
        val generated = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z")
        assertEquals(2, generated.size)
        val event = eventAt(UUID(9L, 9L), "2025-01-02T00:15:00Z", null, null)

        val projection = projection(plan, listOf(event))

        assertTrue("ambiguous legacy event must not be assigned", projection.matchedOccurrences.isEmpty())
        assertEquals(1, projection.unmatchedIntakes.size)
        assertEquals(HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED, projection.unmatchedIntakes.single().displayDateProvenance)
    }

    @Test
    fun `an inferred match is never reported as an exact match`() {
        val plan = schedule(times = listOf(LocalTime.of(8, 0)))
        val occurrence = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z").single()
        val inferred = eventAt(UUID(9L, 10L), "2025-01-02T08:30:00Z", null, null)

        val projection = projection(plan, listOf(inferred))
        val entry = projection.matchedOccurrences.single()

        assertEquals(occurrence.id, entry.occurrence.id)
        assertTrue(
            "null-slot inference must never be reported as exact",
            entry.matchProvenance != MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE
        )
    }

    @Test
    fun `projection provenance agrees with the presentation matcher for the same input`() {
        val plan = schedule(times = listOf(LocalTime.of(8, 0)))
        val generated = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z")
        val exact = eventAt(
            UUID(9L, 11L),
            "2025-01-02T08:05:00Z",
            generated.single().slotId,
            LocalDate.of(2025, 1, 2),
            MedicationIntakeSource.WIDGET
        )
        val loose = eventAt(UUID(9L, 12L), "2025-01-02T08:30:00Z", null, null)

        val events = listOf(exact, loose)
        val presentation = MedicationOccurrencePresentation.derive(generated, events, now)
        val projection = projection(plan, events)

        assertEquals(
            presentation.single { it.occurrence.id == generated.single().id }.recordedEventId,
            projection.matchedOccurrences.single().event.eventId
        )
        // The loser of the phase-one reservation must not be silently re-matched.
        assertEquals(1, projection.unmatchedIntakes.size)
        assertEquals(loose.eventId, projection.unmatchedIntakes.single().event.eventId)
    }

    // ---------- helpers ----------

    private fun schedule(times: List<LocalTime>): MedicationSchedule =
        schedule(number = 1L, times = times)

    private fun occurrences(
        plan: MedicationSchedule,
        start: String,
        end: String
    ): List<MedicationOccurrence> = occurrences(listOf(plan), start, end, zone)

    private fun eventAt(
        id: UUID,
        instant: String,
        slotId: UUID?,
        localDate: LocalDate?,
        source: MedicationIntakeSource = MedicationIntakeSource.LEGACY
    ): RecordedMedicationEvent = RecordedMedicationEvent(
        eventId = id,
        occurredAt = Instant.parse(instant),
        slotId = slotId,
        matchKey = MedicationMatchKey(routeKey = "ORAL", medicationKey = "E2", doseAmount = 2.0),
        source = source,
        localDate = localDate,
        zoneId = zone
    )

    private fun projection(
        plan: MedicationSchedule,
        events: List<RecordedMedicationEvent>
    ): HistoricalProjection = HistoricalProjectionBuilder.derive(
        occurrences = occurrences(plan, "2025-01-02T00:00:00Z", "2025-01-03T00:00:00Z"),
        events = events,
        now = now,
        displayZone = zone
    )

    private fun matchedEntry(
        plan: MedicationSchedule,
        events: List<RecordedMedicationEvent>,
        occurrenceId: MedicationOccurrenceId
    ): MatchedHistoricalOccurrence {
        val projection = projection(plan, events)
        val entry = projection.matchedOccurrences.singleOrNull { it.occurrence.id == occurrenceId }
        assertNotNull("expected occurrence ${occurrenceId.value} to be matched", entry)
        assertNull(projection.unmatchedIntakes.singleOrNull())
        return entry!!
    }
}
