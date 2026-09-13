package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A-03 preflight contract, now satisfied by the domain fix: the History read service
 * must not present a future occurrence as a historical "No recorded intake".
 *
 * A past occurrence without a recorded intake may be reported as
 * [UnrecordedHistoricalOccurrence]; an occurrence that is still in the future belongs to
 * the schedule, not to history, and must never be shown as "no recorded intake". The
 * horizon is decided inside the shared projection on `Instant`s, so the app adapter needs
 * no filtering of its own — these tests pin the observable range-level behaviour.
 *
 * Restored from `docs/evolune/v1.7/evidence/a-03/preflight-contract-test-source.kt.txt`
 * (the red preflight reproduction) with its expectations unchanged.
 */
class HistoryFutureOccurrenceContractTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun doseEvent(
        id: Long,
        occurredAt: Instant,
        slotId: UUID?,
        localDate: LocalDate?,
        source: DoseEventSource = DoseEventSource.REMINDER,
        zoneId: ZoneId? = utc
    ): DoseEvent = DoseEvent(
        id = UUID(7L, id),
        route = Route.ORAL,
        occurredAt = occurredAt,
        zoneId = zoneId,
        localDate = localDate,
        doseMG = 2.0,
        ester = Ester.E2,
        slotId = slotId,
        source = source
    )

    @Test
    fun `future occurrence of today is not presented as an unrecorded history entry`() = runBlocking {
        val day = LocalDate.of(2025, 1, 5)
        val now = Instant.parse("2025-01-05T12:00:00Z")
        val plan = syntheticPlan(slots = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val service = HistoryReadService(
            FakeMedicationPlanRepository(listOf(plan)),
            FakeDoseEventRepository()
        )

        val range = service.readRange(day, day, utc, now)
        val entries: List<HistoricalEntry> = range.days.single().entries

        val pastEntry = entries.singleOrNull {
            it is UnrecordedHistoricalOccurrence &&
                it.occurrence.scheduledLocalDateTime.toLocalTime() == LocalTime.of(8, 0)
        }
        assertTrue("a past occurrence without an intake must stay history's 'no recorded intake'", pastEntry != null)

        val futureAsUnrecorded = entries.singleOrNull {
            it is UnrecordedHistoricalOccurrence &&
                it.occurrence.scheduledLocalDateTime.toLocalTime() == LocalTime.of(20, 0)
        }
        assertEquals(
            "a future occurrence of the same day must not be reported as 'no recorded intake'",
            null,
            futureAsUnrecorded
        )
    }

    @Test
    fun `today keeps only its arrived slots in history and reports no missing intake for later ones`() = runBlocking {
        val day = LocalDate.of(2025, 1, 5)
        val now = Instant.parse("2025-01-05T12:00:00Z")
        val plan = syntheticPlan(slots = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        val service = HistoryReadService(
            FakeMedicationPlanRepository(listOf(plan)),
            FakeDoseEventRepository()
        )

        val range = service.readRange(day, day, utc, now)

        assertEquals(listOf(day), range.days.map { it.date })
        assertEquals(
            listOf(LocalTime.of(8, 0)),
            range.days.single().entries.map { it.sortInstant.atZone(utc).toLocalTime() }
        )
        assertEquals(1, range.unrecordedCount)
        assertEquals(0, range.recordedCount)
        assertEquals(0, range.unmatchedActualCount)
    }

    @Test
    fun `an intake recorded before its scheduled time is still history`() = runBlocking {
        val day = LocalDate.of(2025, 1, 5)
        val now = Instant.parse("2025-01-05T12:00:00Z")
        val plan = syntheticPlan(slots = listOf(LocalTime.of(20, 0)))
        val eveningSlot = plan.slots.single().id
        // Reminder-style record: the planned day is persisted while the actual intake
        // happened in the morning, nine hours before the scheduled time.
        val earlyIntake = doseEvent(
            id = 1L,
            occurredAt = Instant.parse("2025-01-05T11:00:00Z"),
            slotId = eveningSlot,
            localDate = day
        )
        val service = HistoryReadService(
            FakeMedicationPlanRepository(listOf(plan)),
            FakeDoseEventRepository(listOf(earlyIntake))
        )

        val range = service.readRange(day, day, utc, now)

        val matched = range.days.single().entries.single() as MatchedHistoricalOccurrence
        assertEquals(earlyIntake.id, matched.event.eventId)
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, matched.matchProvenance)
        assertTrue(
            "the occurrence is still in the future while the intake already happened",
            matched.occurrence.scheduledAt.isAfter(now)
        )
        assertEquals(1, range.recordedCount)
        assertEquals(0, range.unrecordedCount)
    }

    @Test
    fun `previous days keep every unmatched occurrence as unrecorded`() = runBlocking {
        val now = Instant.parse("2025-01-10T12:00:00Z")
        val plan = syntheticPlan(slots = listOf(LocalTime.of(8, 0)))
        val service = HistoryReadService(
            FakeMedicationPlanRepository(listOf(plan)),
            FakeDoseEventRepository()
        )

        val range = service.readRange(
            startDate = LocalDate.of(2025, 1, 7),
            endDate = LocalDate.of(2025, 1, 8),
            displayZone = utc,
            now = now
        )

        assertEquals(
            listOf(LocalDate.of(2025, 1, 7), LocalDate.of(2025, 1, 8)),
            range.days.map { it.date }
        )
        assertEquals(2, range.unrecordedCount)
        assertEquals(0, range.recordedCount)
        range.days.forEach { day ->
            assertEquals(1, day.unrecordedCount)
            assertTrue(day.entries.single() is UnrecordedHistoricalOccurrence)
        }
    }
}
