package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A-02 read-model contract: unrecorded occurrences, projection completeness,
 * day grouping and date-range behaviour.
 */
class HistoricalReadModelTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Instant = Instant.parse("2025-01-05T12:00:00Z")

    // ---------- completeness invariant ----------

    @Test
    fun `occurrence with a matching event becomes a recorded entry`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(8, 0)))
        val occurrence = occurrences(plan, "2025-01-02", "2025-01-03", utc).single()
        val event = event(
            id = UUID(9L, 1L),
            instant = "2025-01-02T08:05:00Z",
            slotId = occurrence.slotId,
            localDate = LocalDate.of(2025, 1, 2)
        )

        val projection = project(listOf(occurrence), listOf(event), utc)

        assertEquals(1, projection.matchedOccurrences.size)
        assertTrue(projection.unrecordedOccurrences.isEmpty())
        assertTrue(projection.unmatchedIntakes.isEmpty())
    }

    @Test
    fun `occurrence without any recorded intake becomes an unrecorded entry`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(8, 0)))
        val occurrence = occurrences(plan, "2025-01-02", "2025-01-03", utc).single()

        val projection = project(listOf(occurrence), emptyList(), utc)

        val unrecorded = projection.unrecordedOccurrences.single()
        assertEquals(occurrence.id, unrecorded.occurrence.id)
        assertEquals(LocalDate.of(2025, 1, 2), unrecorded.displayDate)
        assertTrue(projection.matchedOccurrences.isEmpty())
        assertTrue(projection.unmatchedIntakes.isEmpty())
    }

    @Test
    fun `event without any occurrence becomes an unmatched intake`() {
        val event = event(UUID(9L, 2L), "2025-01-02T08:05:00Z", slotId = null, localDate = LocalDate.of(2025, 1, 2))

        val projection = project(emptyList(), listOf(event), utc)

        assertEquals(event.eventId, projection.unmatchedIntakes.single().event.eventId)
        assertTrue(projection.matchedOccurrences.isEmpty())
        assertTrue(projection.unrecordedOccurrences.isEmpty())
    }

    @Test
    fun `mixed batch is complete and consumes nothing twice`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val generated = occurrences(plan, "2025-01-02", "2025-01-03", utc)
        assertEquals(2, generated.size)
        val first = generated.first { it.scheduledLocalDateTime.toLocalTime() == LocalTime.of(8, 0) }
        val second = generated.first { it.scheduledLocalDateTime.toLocalTime() == LocalTime.of(20, 0) }

        val recorded = event(UUID(9L, 3L), "2025-01-02T08:05:00Z", first.slotId, LocalDate.of(2025, 1, 2))
        // A different dose never matches these occurrences, so it stays a true orphan.
        val orphan = event(UUID(9L, 4L), "2025-01-02T13:00:00Z", slotId = null, localDate = LocalDate.of(2025, 1, 2), doseAmount = 5.0)

        val projection = project(generated, listOf(recorded, orphan), utc)

        // Every occurrence exactly once, in one of the two occurrence states.
        assertEquals(
            generated.map { it.id }.toSet(),
            (projection.matchedOccurrences.map { it.occurrence.id } + projection.unrecordedOccurrences.map { it.occurrence.id }).toSet()
        )
        assertEquals(1, projection.matchedOccurrences.size)
        assertEquals(second.id, projection.unrecordedOccurrences.single().occurrence.id)
        // Every event exactly once, in one of the two event states.
        assertEquals(
            setOf(recorded.eventId, orphan.eventId),
            (projection.matchedOccurrences.map { it.event.eventId } + projection.unmatchedIntakes.map { it.event.eventId }).toSet()
        )
        assertEquals(1, projection.unmatchedIntakes.size)
        assertEquals(3, projection.entries.size)
    }

    @Test
    fun `a second event for the same occurrence is not consumed twice`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(8, 0)))
        val occurrence = occurrences(plan, "2025-01-02", "2025-01-03", utc).single()
        val winner = event(UUID(9L, 5L), "2025-01-02T08:05:00Z", occurrence.slotId, LocalDate.of(2025, 1, 2))
        val loser = event(UUID(9L, 6L), "2025-01-02T08:20:00Z", occurrence.slotId, LocalDate.of(2025, 1, 2))

        val projection = project(listOf(occurrence), listOf(winner, loser), utc)

        assertEquals(1, projection.matchedOccurrences.size)
        assertEquals(winner.eventId, projection.matchedOccurrences.single().event.eventId)
        assertEquals(loser.eventId, projection.unmatchedIntakes.single().event.eventId)
    }

    // ---------- day grouping and order ----------

    @Test
    fun `day groups matched unrecorded and unmatched entries together`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val generated = occurrences(plan, "2025-01-02", "2025-01-03", utc)
        val morning = generated.first { it.scheduledLocalDateTime.toLocalTime() == LocalTime.of(8, 0) }
        val recorded = event(UUID(9L, 7L), "2025-01-02T08:10:00Z", morning.slotId, LocalDate.of(2025, 1, 2))
        val orphan = event(UUID(9L, 8L), "2025-01-02T13:00:00Z", slotId = null, localDate = LocalDate.of(2025, 1, 2), doseAmount = 5.0)

        val day = HistoricalReadModel.day(project(generated, listOf(recorded, orphan), utc), LocalDate.of(2025, 1, 2))

        assertEquals(1, day.recordedCount)
        assertEquals(1, day.unrecordedCount)
        assertEquals(1, day.unmatchedActualCount)
        assertEquals(3, day.entries.size)
        assertFalse(day.isEmpty)
    }

    @Test
    fun `entry order is deterministic and uses stable tie-breaks`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(0, 0)))
        val occurrence = occurrences(plan, "2025-01-02", "2025-01-03", utc).single()
        val tied = listOf(
            event(UUID(9L, 11L), "2025-01-02T00:00:00Z", slotId = null, localDate = LocalDate.of(2025, 1, 2)),
            event(UUID(9L, 10L), "2025-01-02T00:00:00Z", slotId = null, localDate = LocalDate.of(2025, 1, 2))
        )

        val first = project(listOf(occurrence), tied, utc).entries
        val second = project(listOf(occurrence), tied.reversed(), utc).entries

        assertEquals(
            "input order must not affect the projection order",
            first.map { it.sortKey },
            second.map { it.sortKey }
        )
        // Rank 0 (the occurrence) sorts before rank 2 (the remaining intake) at the same instant.
        assertEquals(listOf("0", "2"), first.map { it.sortKey.substringBefore(':') })
        assertEquals(
            "the lowest event id wins the same-day fallback deterministically",
            UUID(9L, 10L),
            (first.first { it is MatchedHistoricalOccurrence } as MatchedHistoricalOccurrence).event.eventId
        )
        assertEquals(
            UUID(9L, 11L),
            first.filterIsInstance<UnmatchedHistoricalIntake>().single().event.eventId
        )
    }

    // ---------- range behaviour ----------

    @Test
    fun `range returns only days with entries in ascending order`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(8, 0)))
        val generated = occurrences(plan, "2025-01-01", "2025-01-05", utc)
        val projection = project(generated, emptyList(), utc)

        val range = HistoricalReadModel.range(projection, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 4))

        assertEquals(listOf(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 4)), range.days.map { it.date })
        assertEquals(4, range.unrecordedCount)
        assertTrue(range.days.all { it.recordedCount == 0 })
    }

    @Test
    fun `range with identical bounds returns that single day`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(8, 0)))
        val generated = occurrences(plan, "2025-01-02", "2025-01-03", utc)
        val day = LocalDate.of(2025, 1, 2)

        val range = HistoricalReadModel.range(project(generated, emptyList(), utc), day, day)

        assertEquals(listOf(day), range.days.map { it.date })
        assertEquals(day, range.startDate)
        assertEquals(day, range.endDate)
    }

    @Test
    fun `range with no entries returns an empty day list`() {
        val range = HistoricalReadModel.range(
            HistoricalProjection(emptyList()),
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 3)
        )

        assertTrue(range.days.isEmpty())
        assertEquals(0, range.recordedCount)
        assertEquals(0, range.unrecordedCount)
        assertEquals(0, range.unmatchedActualCount)
    }

    @Test
    fun `invalid range fails fast`() {
        val error = runCatching {
            HistoricalReadModel.range(
                HistoricalProjection(emptyList()),
                LocalDate.of(2025, 1, 3),
                LocalDate.of(2025, 1, 1)
            )
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
    }

    // ---------- timezone / cross-midnight / plan lifecycle ----------

    @Test
    fun `unrecorded occurrence keeps current schedule context`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(9, 0)))
        val occurrence = occurrences(plan, "2025-01-02", "2025-01-03", utc).single()

        val unrecorded = project(listOf(occurrence), emptyList(), utc).unrecordedOccurrences.single()

        assertEquals(HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT, unrecorded.scheduleTimeContext)
        assertEquals(LocalTime.of(9, 0), unrecorded.occurrence.scheduledLocalDateTime.toLocalTime())
        assertEquals(listOf(HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT), HistoricalScheduleTimeContext.entries)
    }

    @Test
    fun `orphan intake from a deleted plan still appears on its recorded day`() {
        val orphan = event(UUID(9L, 12L), "2025-01-02T08:05:00Z", slotId = UUID(4L, 4L), localDate = LocalDate.of(2025, 1, 2))

        val day = HistoricalReadModel.day(project(emptyList(), listOf(orphan), utc), LocalDate.of(2025, 1, 2))

        assertEquals(1, day.unmatchedActualCount)
        assertEquals(orphan.eventId, (day.entries.single() as UnmatchedHistoricalIntake).event.eventId)
        assertTrue(HistoricalScheduleTimeContext.entries.size == 1)
    }

    @Test
    fun `true legacy orphan display day follows the display zone`() {
        val orphan = event(UUID(9L, 13L), "2025-01-02T20:00:00Z", slotId = null, localDate = null, zoneId = null)

        val parisDay = HistoricalReadModel.day(project(emptyList(), listOf(orphan), paris), LocalDate.of(2025, 1, 2))
        val shanghaiDay = HistoricalReadModel.day(project(emptyList(), listOf(orphan), shanghai), LocalDate.of(2025, 1, 3))

        assertEquals(1, parisDay.unmatchedActualCount)
        assertEquals(HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED, parisDay.entries.single().displayDateProvenance)
        assertEquals(1, shanghaiDay.unmatchedActualCount)
        assertEquals(0, HistoricalReadModel.day(project(emptyList(), listOf(orphan), shanghai), LocalDate.of(2025, 1, 2)).entries.size)
    }

    @Test
    fun `persisted recording date stays on the recorded day across display zones`() {
        val recorded = event(UUID(9L, 14L), "2025-01-02T20:00:00Z", slotId = null, localDate = LocalDate.of(2025, 1, 3), zoneId = shanghai)

        val shanghaiDay = HistoricalReadModel.day(project(emptyList(), listOf(recorded), shanghai), LocalDate.of(2025, 1, 3))
        val utcDay = HistoricalReadModel.day(project(emptyList(), listOf(recorded), utc), LocalDate.of(2025, 1, 3))

        assertEquals(1, shanghaiDay.unmatchedActualCount)
        assertEquals(1, utcDay.unmatchedActualCount)
        assertEquals(HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE, utcDay.entries.single().displayDateProvenance)
    }

    @Test
    fun `cross-midnight legacy event lands on the occurrence day`() {
        val plan = schedule(number = 1L, times = listOf(LocalTime.of(0, 0)))
        val occurrence = occurrences(plan, "2025-01-02", "2025-01-03", utc).single()
        // Recorded the previous evening, one hour before the requested day's first occurrence.
        val previousDayEvent = event(UUID(9L, 15L), "2025-01-01T23:00:00Z", slotId = null, localDate = null, zoneId = null)

        val day = HistoricalReadModel.day(project(listOf(occurrence), listOf(previousDayEvent), utc), LocalDate.of(2025, 1, 2))

        assertEquals(1, day.recordedCount)
        val entry = day.entries.single() as MatchedHistoricalOccurrence
        assertEquals(MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, entry.matchProvenance)
        assertTrue(entry.crossesLocalDateBoundary)
        assertEquals(0, HistoricalReadModel.day(project(listOf(occurrence), listOf(previousDayEvent), utc), LocalDate.of(2025, 1, 1)).entries.size)
    }

    // ---------- helpers ----------

    private fun occurrences(
        plan: MedicationSchedule,
        startDate: String,
        endDateExclusive: String,
        zone: ZoneId
    ): List<MedicationOccurrence> = occurrences(
        schedules = listOf(plan),
        start = "${startDate}T00:00:00Z",
        end = "${endDateExclusive}T00:00:00Z",
        zoneId = zone
    )

    private fun event(
        id: UUID,
        instant: String,
        slotId: UUID?,
        localDate: LocalDate?,
        zoneId: ZoneId? = utc,
        doseAmount: Double = 2.0
    ): RecordedMedicationEvent = RecordedMedicationEvent(
        eventId = id,
        occurredAt = Instant.parse(instant),
        slotId = slotId,
        matchKey = MedicationMatchKey(routeKey = "ORAL", medicationKey = "E2", doseAmount = doseAmount),
        source = MedicationIntakeSource.LEGACY,
        localDate = localDate,
        zoneId = zoneId
    )

    private fun project(
        occurrences: List<MedicationOccurrence>,
        events: List<RecordedMedicationEvent>,
        zone: ZoneId
    ): HistoricalProjection = HistoricalProjectionBuilder.derive(
        occurrences = occurrences,
        events = events,
        now = now,
        displayZone = zone
    )
}
