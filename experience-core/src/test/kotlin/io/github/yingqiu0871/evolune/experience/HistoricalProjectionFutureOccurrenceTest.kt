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
 * A-03-PRE-01 temporal horizon contract of the shared historical projection.
 *
 * A-03 preflight found that a *future* occurrence of the current day was reported as an
 * [UnrecordedHistoricalOccurrence] ("no recorded intake"), i.e. history claimed that a
 * dose had not been taken while its scheduled time had not even arrived. The frozen
 * contract implemented here is:
 *
 * - a matched occurrence is history no matter where `scheduledAt` sits — an early intake
 *   recorded before its scheduled time must survive;
 * - an unmatched occurrence with `scheduledAt <= now` is [UnrecordedHistoricalOccurrence]
 *   (`scheduledAt == now` counts as arrived);
 * - an unmatched occurrence with `scheduledAt > now` is upcoming context, reported
 *   separately in [HistoricalProjection.futureOccurrences] and **not** a
 *   [HistoricalEntry].
 *
 * The horizon is always an `Instant` vs `Instant` comparison: never `LocalDate`, never
 * presentation status (`UPCOMING` / `DUE`), never UI filtering. The single four-phase
 * matcher always runs first, so a future occurrence is never excluded before matching.
 */
class HistoricalProjectionFutureOccurrenceTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val kiritimati: ZoneId = ZoneId.of("Pacific/Kiritimati")

    private val day: LocalDate = LocalDate.of(2025, 1, 5)
    private val now: Instant = Instant.parse("2025-01-05T12:00:00Z")

    // ---------- fixtures ----------

    private fun planOf(
        times: List<LocalTime>,
        number: Long = 1L
    ): MedicationSchedule = schedule(number = number, times = times)

    private fun occurrencesOn(
        plan: MedicationSchedule,
        date: LocalDate = day,
        zone: ZoneId = utc,
        days: Long = 1L
    ): List<MedicationOccurrence> = occurrences(
        schedules = listOf(plan),
        start = date.atStartOfDay(zone).toInstant().toString(),
        end = date.plusDays(days).atStartOfDay(zone).toInstant().toString(),
        zoneId = zone
    )

    private fun List<MedicationOccurrence>.at(time: LocalTime): MedicationOccurrence =
        single { it.scheduledLocalDateTime.toLocalTime() == time }

    private fun localTimes(values: List<MedicationOccurrence>): List<LocalTime> =
        values.map { it.scheduledLocalDateTime.toLocalTime() }

    /** An exact slot/date reminder-style record: the planned day is persisted with the event. */
    private fun reminderEvent(
        occurrence: MedicationOccurrence,
        occurredAt: Instant,
        id: Long = 1L,
        zone: ZoneId = utc
    ): RecordedMedicationEvent = RecordedMedicationEvent(
        eventId = UUID(9L, id),
        occurredAt = occurredAt,
        slotId = occurrence.slotId,
        matchKey = occurrence.presentation.matchKey,
        source = MedicationIntakeSource.MANUAL,
        localDate = occurrence.scheduledLocalDateTime.toLocalDate(),
        zoneId = zone
    )

    private fun derive(
        occurrences: List<MedicationOccurrence>,
        events: List<RecordedMedicationEvent> = emptyList(),
        instant: Instant = now,
        zone: ZoneId = utc
    ): HistoricalProjection = HistoricalProjectionBuilder.derive(
        occurrences = occurrences,
        events = events,
        now = instant,
        displayZone = zone
    )

    // ---------- the horizon split ----------

    @Test
    fun `unmatched occurrence strictly after now is upcoming context, not history`() {
        val evening = occurrencesOn(planOf(listOf(LocalTime.of(20, 0)))).at(LocalTime.of(20, 0))

        val projection = derive(listOf(evening))

        assertTrue(
            "a future occurrence must not be history at all",
            projection.entries.isEmpty()
        )
        assertTrue(
            "a future occurrence must not be counted as 'no recorded intake'",
            projection.unrecordedOccurrences.isEmpty()
        )
        val future = projection.futureOccurrences.single()
        assertEquals(evening.id, future.occurrence.id)
        assertEquals(Instant.parse("2025-01-05T20:00:00Z"), future.occurrence.scheduledAt)
        assertEquals(
            HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
            future.scheduleTimeContext
        )
        assertTrue(projection.entriesOn(day).isEmpty())
    }

    @Test
    fun `occurrence exactly at now counts as arrived and stays unrecorded`() {
        val noon = occurrencesOn(planOf(listOf(LocalTime.of(12, 0)))).at(LocalTime.of(12, 0))

        val projection = derive(listOf(noon))

        val unrecorded = projection.unrecordedOccurrences.single()
        assertEquals(noon.id, unrecorded.occurrence.id)
        assertEquals(noon.scheduledAt, now)
        assertTrue("the horizon itself belongs to history", projection.futureOccurrences.isEmpty())
    }

    @Test
    fun `one second on either side of the horizon is classified differently`() {
        // Plan slots are minute-aligned (MedicationScheduleSlot), so the ±1s boundary is
        // exercised on `now` around a single 12:00 occurrence.
        val noon = occurrencesOn(planOf(listOf(LocalTime.of(12, 0)))).at(LocalTime.of(12, 0))

        val oneSecondBefore = derive(listOf(noon), instant = Instant.parse("2025-01-05T11:59:59Z"))
        assertEquals(0, oneSecondBefore.entries.size)
        assertEquals(1, oneSecondBefore.futureOccurrences.size)

        val exactlyNow = derive(listOf(noon), instant = Instant.parse("2025-01-05T12:00:00Z"))
        assertEquals(1, exactlyNow.unrecordedOccurrences.size)
        assertTrue(exactlyNow.futureOccurrences.isEmpty())

        val oneSecondAfter = derive(listOf(noon), instant = Instant.parse("2025-01-05T12:00:01Z"))
        assertEquals(1, oneSecondAfter.unrecordedOccurrences.size)
        assertTrue(oneSecondAfter.futureOccurrences.isEmpty())
    }

    @Test
    fun `an occurrence at or before now is always history`() {
        val morning = occurrencesOn(planOf(listOf(LocalTime.of(8, 0)))).at(LocalTime.of(8, 0))

        listOf("2025-01-05T08:00:00Z", "2025-01-05T08:00:01Z", "2025-01-05T09:00:00Z").forEach { instant ->
            val projection = derive(listOf(morning), instant = Instant.parse(instant))
            assertEquals(
                "occurrence at ${morning.scheduledAt} must be history for now=$instant",
                1,
                projection.unrecordedOccurrences.size
            )
            assertTrue(projection.futureOccurrences.isEmpty())
        }
    }

    @Test
    fun `early intake before its scheduled time stays a matched history entry`() {
        val evening = occurrencesOn(planOf(listOf(LocalTime.of(20, 0)))).at(LocalTime.of(20, 0))
        val earlyIntake = reminderEvent(evening, occurredAt = Instant.parse("2025-01-05T11:00:00Z"))

        val projection = derive(listOf(evening), listOf(earlyIntake))

        val matched = projection.matchedOccurrences.single()
        assertEquals(evening.id, matched.occurrence.id)
        assertEquals(earlyIntake.eventId, matched.event.eventId)
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, matched.matchProvenance)
        assertTrue(
            "the occurrence is still in the future while the intake already happened",
            matched.occurrence.scheduledAt.isAfter(now)
        )
        assertTrue(
            "a recorded future occurrence is not upcoming context",
            projection.futureOccurrences.isEmpty()
        )
    }

    @Test
    fun `only unmatched occurrences leave history - a recorded future occurrence stays`() {
        val pair = occurrencesOn(planOf(listOf(LocalTime.of(14, 0), LocalTime.of(20, 0))))
        val earlyIntake = reminderEvent(
            pair.at(LocalTime.of(20, 0)),
            occurredAt = Instant.parse("2025-01-05T11:00:00Z")
        )

        val projection = derive(pair, listOf(earlyIntake))

        assertEquals(
            listOf(LocalTime.of(20, 0)),
            localTimes(projection.matchedOccurrences.map { it.occurrence })
        )
        assertEquals(
            listOf(LocalTime.of(14, 0)),
            localTimes(projection.futureOccurrences.map { it.occurrence })
        )
        assertEquals(1, projection.entries.size)
    }

    @Test
    fun `mixed current-day occurrences split at the horizon without losing early intake`() {
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

        val projection = derive(dayOccurrences, events)

        // 08:00 unrecorded, 10:00 recorded, 14:00 recorded early -> three history entries.
        assertEquals(3, projection.entries.size)
        assertEquals(
            listOf(LocalTime.of(8, 0)),
            localTimes(projection.unrecordedOccurrences.map { it.occurrence })
        )
        assertEquals(
            listOf(LocalTime.of(10, 0), LocalTime.of(14, 0)),
            localTimes(projection.matchedOccurrences.map { it.occurrence })
        )
        // 20:00 has not arrived: excluded from history, retained as upcoming context.
        assertEquals(
            listOf(LocalTime.of(20, 0)),
            localTimes(projection.futureOccurrences.map { it.occurrence })
        )
        assertEquals(
            "history is ordered by scheduled instant",
            listOf(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(14, 0)),
            projection.entries.map { it.sortInstant.atZone(utc).toLocalTime() }
        )
    }

    @Test
    fun `future context is ordered by scheduled instant`() {
        val plan = planOf(
            listOf(LocalTime.of(23, 0), LocalTime.of(13, 0), LocalTime.of(18, 0)),
            number = 3L
        )

        val projection = derive(occurrencesOn(plan))

        assertEquals(
            listOf(LocalTime.of(13, 0), LocalTime.of(18, 0), LocalTime.of(23, 0)),
            localTimes(projection.futureOccurrences.map { it.occurrence })
        )
    }

    // ---------- timezone / DST regressions ----------

    @Test
    fun `horizon classification is an instant comparison independent of the display zone`() {
        val pair = occurrencesOn(planOf(listOf(LocalTime.of(11, 0), LocalTime.of(13, 0))))

        val projections = listOf(utc, kiritimati).map { zone -> derive(pair, zone = zone) }

        projections.forEach { projection ->
            assertEquals(1, projection.entries.size)
            assertEquals(
                listOf(LocalTime.of(11, 0)),
                localTimes(projection.unrecordedOccurrences.map { it.occurrence })
            )
            assertEquals(
                listOf(LocalTime.of(13, 0)),
                localTimes(projection.futureOccurrences.map { it.occurrence })
            )
        }
        // The display zone may still differ on the *display date* of a past entry, but
        // never on whether an occurrence is history.
        assertEquals(
            HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE,
            projections.first().unrecordedOccurrences.single().displayDateProvenance
        )
    }

    @Test
    fun `DST gap - the horizon uses the resolved instant, not the nominal wall clock`() {
        // Europe/Paris 2026-03-29: 02:00 -> 03:00 local. A 02:30 slot does not exist and
        // resolves to 03:30 CEST == 01:30Z while keeping 02:30 as the nominal local time.
        val gapDay = LocalDate.of(2026, 3, 29)
        val occurrence = occurrencesOn(
            planOf(listOf(LocalTime.of(2, 30))),
            date = gapDay,
            zone = paris
        ).single()
        assertEquals(LocalTime.of(2, 30), occurrence.scheduledLocalDateTime.toLocalTime())
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), occurrence.scheduledAt)

        val beforeTheShift = Instant.parse("2026-03-29T01:00:00Z")
        assertEquals(LocalTime.of(3, 0), beforeTheShift.atZone(paris).toLocalTime())
        assertTrue(
            "premise of the regression: wall-clock reading would call this occurrence arrived",
            occurrence.scheduledLocalDateTime.toLocalTime() < beforeTheShift.atZone(paris).toLocalTime()
        )
        assertTrue(
            "the resolved instant says the occurrence has not arrived",
            occurrence.scheduledAt.isAfter(beforeTheShift)
        )

        val projection = derive(listOf(occurrence), instant = beforeTheShift, zone = paris)

        assertTrue(projection.entries.isEmpty())
        assertEquals(1, projection.futureOccurrences.size)
    }

    @Test
    fun `DST overlap - a repeated wall-clock hour still splits on instants`() {
        // Europe/Paris 2026-10-25: 03:00 -> 02:00 local. 02:30 is ambiguous and resolves
        // to the first pass (00:30Z), i.e. one hour before the second 02:30.
        val overlapDay = LocalDate.of(2026, 10, 25)
        val occurrence = occurrencesOn(
            planOf(listOf(LocalTime.of(2, 30))),
            date = overlapDay,
            zone = paris
        ).single()
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), occurrence.scheduledAt)

        val firstPassArrived = Instant.parse("2026-10-25T00:45:00Z")
        val projection = derive(listOf(occurrence), instant = firstPassArrived, zone = paris)

        assertEquals(1, projection.unrecordedOccurrences.size)
        assertTrue(projection.futureOccurrences.isEmpty())
    }
}
