package io.github.yingqiu0871.evolune.history.retrospective

import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalProjection
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityClassifier
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityStatus
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.history.testRange
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * V17-C-04 §14.1 marker mapping and family scope (M / MC / MS / MI derivation level).
 *
 * Every case drives the pure derivation against synthetic projection facts; inclusion is always
 * driven by the family's own instant and by the frozen scope rules — never by display date,
 * persisted local date or a re-run eligibility decision.
 */
class RetrospectiveMarkerDerivationTest {

    private val window = c04Window()
    private val start = window.startInclusive
    private val end = window.endInclusive

    // ---------- local builders ----------

    private fun occurrenceAt(
        instant: Instant,
        slotId: Long = 1L,
        routeKey: String = "ORAL",
        medicationKey: String = "E2",
        doseAmount: Double = 2.0
    ): MedicationOccurrence = testOccurrence(
        slotId = slotId,
        date = instant.atZone(ZoneOffset.UTC).toLocalDate(),
        time = instant.atZone(ZoneOffset.UTC).toLocalTime(),
        routeKey = routeKey,
        medicationKey = medicationKey,
        doseAmount = doseAmount
    )

    private fun eventAt(
        instant: Instant,
        id: Long,
        routeKey: String = "ORAL",
        medicationKey: String = "E2",
        doseAmount: Double = 2.0,
        localDate: LocalDate? = null
    ): RecordedMedicationEvent = testEvent(
        id = id,
        occurredAt = instant,
        routeKey = routeKey,
        medicationKey = medicationKey,
        doseAmount = doseAmount,
        localDate = localDate
    )

    private fun rangeOf(instant: Instant, vararg entries: HistoricalEntry): io.github.yingqiu0871.evolune.experience.HistoricalRange =
        testRange(
            startDate = start.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1),
            endDate = end.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1),
            days = listOf(testDay(date = instant.atZone(ZoneOffset.UTC).toLocalDate(), entries = entries.toList()))
        )

    private fun projectionOf(vararg entries: HistoricalEntry): HistoricalProjection =
        HistoricalProjection(entries = entries.toList())

    private fun scheduleMarkers(range: io.github.yingqiu0871.evolune.experience.HistoricalRange) =
        RetrospectiveMarkerDerivation.scheduleMarkers(range, window)

    private fun intakeMarkers(projection: HistoricalProjection, eligible: Set<UUID>) =
        RetrospectiveMarkerDerivation.recordedIntakeMarkers(projection, eligible, window)

    private fun eventIdOf(instant: Instant, id: Long = 1L, routeKey: String = "ORAL", medicationKey: String = "E2", doseAmount: Double = 2.0, localDate: LocalDate? = null) =
        eventAt(instant, id, routeKey, medicationKey, doseAmount, localDate).eventId

    // ---------- M ----------

    @Test
    fun `M1 matched occurrence yields one schedule marker and one intake marker by own instants`() {
        val scheduled = start.plusSeconds(3600)
        val occurred = scheduled.plusSeconds(300)
        val occurrence = occurrenceAt(scheduled)
        val event = eventAt(occurred, id = 1L)
        val entry = matchedEntry(occurrence = occurrence, event = event)

        val schedule = scheduleMarkers(rangeOf(scheduled, entry))
        val intake = intakeMarkers(projectionOf(entry), setOf(event.eventId))

        assertEquals(1, schedule.size)
        assertEquals(1, intake.size)
        assertEquals(occurrence.id, schedule.single().occurrenceId)
        assertEquals(scheduled, schedule.single().scheduledAt)
        assertEquals(event.eventId, intake.single().eventId)
        assertEquals(occurred, intake.single().occurredAt)
    }

    @Test
    fun `M2 unmatched accepted intake yields an intake marker only, never a synthetic schedule marker`() {
        val occurred = start.plusSeconds(7200)
        val event = eventAt(occurred, id = 2L)
        val entry = unmatchedEntry(event = event)

        val schedule = scheduleMarkers(rangeOf(occurred, entry))
        val intake = intakeMarkers(projectionOf(entry), setOf(event.eventId))

        assertTrue(schedule.isEmpty())
        assertEquals(1, intake.size)
        assertEquals(IntakeMarkerProvenance.UNMATCHED_INTAKE, intake.single().provenance)
    }

    @Test
    fun `M3 known-estrogen unrecorded occurrence yields a schedule marker only and never an intake marker`() {
        val scheduled = start.plusSeconds(10800)
        val occurrence = occurrenceAt(scheduled, slotId = 2L)
        val entry = unrecordedEntry(occurrence = occurrence)

        val schedule = scheduleMarkers(rangeOf(scheduled, entry))
        val intake = intakeMarkers(projectionOf(entry), emptySet())

        assertEquals(1, schedule.size)
        assertEquals(ScheduleMarkerProvenance.UNRECORDED_OCCURRENCE, schedule.single().provenance)
        assertTrue(intake.isEmpty())
    }

    @Test
    fun `M4 markers derive only from entries - future occurrences can never become markers`() {
        val scheduled = start.plusSeconds(14400)
        val occurrence = occurrenceAt(scheduled)
        val projection = HistoricalProjection(entries = emptyList())
        assertTrue(intakeMarkers(projection, setOf(UUID.randomUUID())).isEmpty())

        // Schedule markers derive from the Read 3 range only: an empty range (which never carries
        // future occurrences by type) yields none.
        assertTrue(scheduleMarkers(c04EmptyRange(window)).isEmpty())
    }

    @Test
    fun `M5 the window is inclusive on both ends for both families`() {
        val occurrenceAtStart = occurrenceAt(start, slotId = 1L)
        val occurrenceAtEnd = occurrenceAt(end, slotId = 2L)
        val eventAtStart = eventAt(start, id = 1L)
        val eventAtEnd = eventAt(end, id = 2L)

        val schedule = scheduleMarkers(
            testRange(
                startDate = start.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1),
                endDate = end.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1),
                days = listOf(
                    testDay(date = start.atZone(ZoneOffset.UTC).toLocalDate(), entries = listOf(matchedEntry(occurrence = occurrenceAtStart, event = eventAtStart))),
                    testDay(date = end.atZone(ZoneOffset.UTC).toLocalDate(), entries = listOf(unrecordedEntry(occurrence = occurrenceAtEnd)))
                )
            )
        )
        val intake = intakeMarkers(
            projectionOf(matchedEntry(occurrence = occurrenceAtStart, event = eventAtStart), unmatchedEntry(event = eventAtEnd)),
            setOf(eventAtStart.eventId, eventAtEnd.eventId)
        )

        assertEquals(2, schedule.size)
        assertEquals(2, intake.size)
    }

    @Test
    fun `M6 counts follow the frozen invariants - no dedup, no merge, no synthesis, deterministic order`() {
        val scheduled = start.plusSeconds(18000)
        val occurred = scheduled
        val occurrence = occurrenceAt(scheduled)
        val event = eventAt(occurred, id = 3L)
        val entry = matchedEntry(occurrence = occurrence, event = event)

        val schedule = scheduleMarkers(rangeOf(scheduled, entry))
        val intake = intakeMarkers(projectionOf(entry), setOf(event.eventId))

        // A matched pair may stand as two markers on the same instant: never merged or compensated.
        assertEquals(1, schedule.size)
        assertEquals(1, intake.size)

        val ordered = (schedule + intake).sortedWith(RETROSPECTIVE_MARKER_ORDER)
        assertEquals(2, ordered.size)
        assertTrue(ordered.first() is ScheduleContextMarker)
        assertTrue(ordered.last() is RecordedIntakeMarker)
    }

    // ---------- MC ----------

    @Test
    fun `MC1 schedule markers exist across the whole 30 day window even when accepted intakes are recent`() {
        // Read 3 is the complete current-schedule context: occurrences exist for the full window.
        val early = start.plusSeconds(600)
        val late = end.minusSeconds(600)
        val earlyEntry = unrecordedEntry(occurrence = occurrenceAt(early, slotId = 1L))
        val lateEntry = unrecordedEntry(occurrence = occurrenceAt(late, slotId = 2L))

        val schedule = scheduleMarkers(
            testRange(
                startDate = start.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1),
                endDate = end.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1),
                days = listOf(
                    testDay(date = early.atZone(ZoneOffset.UTC).toLocalDate(), entries = listOf(earlyEntry)),
                    testDay(date = late.atZone(ZoneOffset.UTC).toLocalDate(), entries = listOf(lateEntry))
                )
            )
        )
        assertEquals(2, schedule.size)
        assertEquals(early, schedule.first().scheduledAt)
        assertFalse(schedule.any { it.scheduledAt.isBefore(start) })
    }

    @Test
    fun `MC2 intake membership uses occurredAt only - a persisted date outside the local span still shows`() {
        val occurred = start.plusSeconds(1200)
        val persistedOutside = start.atZone(ZoneOffset.UTC).toLocalDate().minusDays(10)
        val event = eventAt(occurred, id = 4L, localDate = persistedOutside)
        val entry = unmatchedEntry(event = event)

        val intake = intakeMarkers(projectionOf(entry), setOf(event.eventId))
        assertEquals(1, intake.size)
        assertEquals(occurred, intake.single().occurredAt)
    }

    @Test
    fun `MC3 and MC4 boundary instants at start and end are included while 1ms outside is excluded`() {
        val outsideStart = start.minusMillis(1)
        val outsideEnd = end.plusMillis(1)

        val range = testRange(
            startDate = start.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1),
            endDate = end.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1),
            days = listOf(
                testDay(date = start.atZone(ZoneOffset.UTC).toLocalDate(), entries = listOf(matchedEntry(occurrence = occurrenceAt(outsideStart, slotId = 9L), event = eventAt(outsideStart, id = 9L)))),
                testDay(date = end.atZone(ZoneOffset.UTC).toLocalDate(), entries = listOf(unrecordedEntry(occurrence = occurrenceAt(outsideEnd, slotId = 10L))))
            )
        )
        assertEquals(0, scheduleMarkers(range).size)

        val events = projectionOf(
            unmatchedEntry(event = eventAt(outsideStart, id = 11L)),
            unmatchedEntry(event = eventAt(outsideEnd, id = 12L))
        )
        assertEquals(0, intakeMarkers(events, setOf(eventAt(outsideStart, id = 11L).eventId, eventAt(outsideEnd, id = 12L).eventId)).size)
    }

    @Test
    fun `MC5 MC6 MC7 family counts per projection shape`() {
        val scheduled = start.plusSeconds(2400)
        val matched = matchedEntry(occurrence = occurrenceAt(scheduled, slotId = 5L), event = eventAt(scheduled.plusSeconds(60), id = 5L))
        val unrecorded = unrecordedEntry(occurrence = occurrenceAt(scheduled.plusSeconds(120), slotId = 6L))
        val unmatched = unmatchedEntry(event = eventAt(scheduled.plusSeconds(180), id = 6L))

        val range = testRange(
            startDate = start.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1),
            endDate = end.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1),
            days = listOf(testDay(date = scheduled.atZone(ZoneOffset.UTC).toLocalDate(), entries = listOf(matched, unrecorded, unmatched)))
        )

        val schedule = scheduleMarkers(range)
        val intake = intakeMarkers(
            projectionOf(matched, unrecorded, unmatched),
            setOf(matched.event.eventId, unmatched.event.eventId)
        )

        assertEquals("one matched + one unrecorded occurrence contribute schedule markers", 2, schedule.size)
        assertEquals("one matched + one unmatched intake contribute intake markers", 2, intake.size)
        assertTrue(schedule.all { it.scheduledAt != unmatched.event.occurredAt })
    }

    // ---------- MS ----------

    @Test
    fun `MS1 every known estrogen key yields exactly one schedule marker`() {
        listOf("E2", "EB", "EV", "EC", "EN").forEachIndexed { index, key ->
            val scheduled = start.plusSeconds(3600L + index * 600L)
            val entry = unrecordedEntry(
                occurrence = occurrenceAt(scheduled, slotId = 20L + index, medicationKey = key)
            )
            val markers = scheduleMarkers(rangeOf(scheduled, entry))
            assertEquals("key $key must be visible as current schedule context", 1, markers.size)
        }
    }

    @Test
    fun `MS2 antiandrogen schedule occurrences are hidden`() {
        val scheduled = start.plusSeconds(3600)
        val entry = unrecordedEntry(
            occurrence = occurrenceAt(scheduled, routeKey = "ANTIANDROGEN", medicationKey = "E2")
        )
        assertEquals(1, MedicationIdentityClassifier.classify(
            occurrenceAt(scheduled, routeKey = "ANTIANDROGEN").presentation.matchKey
        ).status.let { if (it == MedicationIdentityStatus.UNAVAILABLE) 1 else 0 })
        assertTrue(scheduleMarkers(rangeOf(scheduled, entry)).isEmpty())
    }

    @Test
    fun `MS3 partial or foreign medication identities are hidden`() {
        val scheduled = start.plusSeconds(3600)
        val entry = unrecordedEntry(
            occurrence = occurrenceAt(scheduled, medicationKey = "UNKNOWN_ESTER")
        )
        assertEquals(
            MedicationIdentityStatus.PARTIAL,
            MedicationIdentityClassifier.classify(
                occurrenceAt(scheduled, medicationKey = "UNKNOWN_ESTER").presentation.matchKey
            ).status
        )
        assertTrue(scheduleMarkers(rangeOf(scheduled, entry)).isEmpty())
    }

    @Test
    fun `MS4 unrecorded known-estrogen occurrence has a schedule marker and no classification side effect`() {
        val scheduled = start.plusSeconds(3600)
        val entry = unrecordedEntry(occurrence = occurrenceAt(scheduled, slotId = 30L))
        val markers = scheduleMarkers(rangeOf(scheduled, entry))
        assertEquals(1, markers.size)
        // Provenance is internal only; the marker carries no missed/skipped/adherence field.
        assertEquals(ScheduleMarkerProvenance.UNRECORDED_OCCURRENCE, markers.single().provenance)
    }

    @Test
    fun `MS5 PATCH_REMOVE schedule occurrences are explicitly hidden even though the key classifies KNOWN`() {
        val scheduled = start.plusSeconds(3600)
        val occurrence = occurrenceAt(scheduled, routeKey = "PATCH_REMOVE", medicationKey = "E2")
        // The classifier alone would accept {PATCH_REMOVE, E2} as KNOWN; the frozen route guard hides it.
        assertEquals(
            MedicationIdentityStatus.KNOWN,
            MedicationIdentityClassifier.classify(occurrence.presentation.matchKey).status
        )
        val entry = unrecordedEntry(occurrence = occurrence)
        assertTrue(scheduleMarkers(rangeOf(scheduled, entry)).isEmpty())
    }

    // ---------- MI ----------

    @Test
    fun `MI1 an accepted non-control ID present in Read2 inside the window yields one intake marker`() {
        val occurred = start.plusSeconds(900)
        val event = eventAt(occurred, id = 40L)
        val entry = unmatchedEntry(event = event)
        val markers = intakeMarkers(projectionOf(entry), setOf(event.eventId))
        assertEquals(1, markers.size)
        assertEquals(event.eventId, markers.single().eventId)
    }

    @Test
    fun `MI2 patch control IDs never become intake markers`() {
        val occurred = start.plusSeconds(900)
        val removal = eventAt(occurred, id = 41L, routeKey = "PATCH_REMOVE", doseAmount = 0.0)
        val entry = unmatchedEntry(event = removal)
        // eligible set = engineInputEventIds - patchControlEventIds: a control ID is not eligible.
        val markers = intakeMarkers(projectionOf(entry), emptySet())
        assertTrue(markers.isEmpty())
    }

    @Test
    fun `MI3 excluded identities absent from the accepted set never become intake markers`() {
        val occurred = start.plusSeconds(900)
        val antiandrogen = eventAt(occurred, id = 42L, routeKey = "ANTIANDROGEN")
        val entry = unmatchedEntry(event = antiandrogen)
        assertTrue(intakeMarkers(projectionOf(entry), emptySet()).isEmpty())
    }

    @Test
    fun `MI4 retained zero-contribution non-control inputs remain markers when the summary retains them`() {
        val occurred = start.plusSeconds(900)
        val retained = eventAt(occurred, id = 43L, doseAmount = 0.0)
        val entry = unmatchedEntry(event = retained)
        // engineInputEventIds contains it; concentrationProducingEventIds does not: still eligible.
        val markers = intakeMarkers(projectionOf(entry), setOf(retained.eventId))
        assertEquals(1, markers.size)
    }

    @Test
    fun `MI5 unmatched accepted actual yields one intake marker and no synthetic schedule marker`() {
        val occurred = start.plusSeconds(900)
        val event = eventAt(occurred, id = 44L)
        val entry = unmatchedEntry(event = event)
        assertEquals(1, intakeMarkers(projectionOf(entry), setOf(event.eventId)).size)
        assertTrue(scheduleMarkers(rangeOf(occurred, entry)).isEmpty())
    }

    @Test
    fun `MI10 same accepted ID mutated to ANTIANDROGEN is hidden by the current-payload guard`() {
        val occurred = start.plusSeconds(900)
        val mutated = eventAt(occurred, id = 45L, routeKey = "ANTIANDROGEN")
        val entry = unmatchedEntry(event = mutated)
        // The ID is still in the immutable accepted set, but the CURRENT payload fails the guard.
        assertTrue(intakeMarkers(projectionOf(entry), setOf(mutated.eventId)).isEmpty())
    }

    @Test
    fun `MI11 same accepted ID mutated to PATCH_REMOVE is hidden`() {
        val occurred = start.plusSeconds(900)
        val mutated = eventAt(occurred, id = 46L, routeKey = "PATCH_REMOVE", doseAmount = 0.0)
        val entry = unmatchedEntry(event = mutated)
        assertTrue(intakeMarkers(projectionOf(entry), setOf(mutated.eventId)).isEmpty())
    }

    @Test
    fun `MI12 same accepted ID mutated to a foreign identity is hidden`() {
        val occurred = start.plusSeconds(900)
        val mutated = eventAt(occurred, id = 47L, medicationKey = "FOREIGN")
        val entry = unmatchedEntry(event = mutated)
        assertTrue(intakeMarkers(projectionOf(entry), setOf(mutated.eventId)).isEmpty())
    }
}
