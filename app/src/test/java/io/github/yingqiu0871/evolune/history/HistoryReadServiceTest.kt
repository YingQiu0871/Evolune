package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.experience.HistoricalScheduleTimeContext
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A-02 app-adapter contract: bounded repository/context queries, explicit display
 * zone, projection reuse and provenance-based final range filtering.
 */
class HistoryReadServiceTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Instant = Instant.parse("2025-01-10T12:00:00Z")

    @Test
    fun `event query uses exactly one day of bounded context on each side`() = runBlocking {
        val events = FakeDoseEventRepository()
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        service.readRange(
            startDate = LocalDate.of(2025, 1, 5),
            endDate = LocalDate.of(2025, 1, 5),
            displayZone = utc,
            now = now
        )

        assertEquals(
            Instant.parse("2025-01-04T00:00:00Z") to Instant.parse("2025-01-07T00:00:00Z"),
            events.lastRange
        )
        assertEquals(HistoryReadService.CONTEXT_DAYS, 1L)
    }

    @Test
    fun `occurrence generation covers the requested day through current plans`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(syntheticPlan(slots = listOf(LocalTime.of(8, 30)))))
        val service = HistoryReadService(plans, FakeDoseEventRepository())

        val range = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), utc, now)

        assertEquals(listOf(LocalDate.of(2025, 1, 5)), range.days.map { it.date })
        val unrecorded = range.days.single().entries.single()
        assertTrue(unrecorded is UnrecordedHistoricalOccurrence)
        assertEquals(
            HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
            (unrecorded as UnrecordedHistoricalOccurrence).scheduleTimeContext
        )
    }

    @Test
    fun `adjacent context occurrences never leak into the returned range`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(syntheticPlan(slots = listOf(LocalTime.of(8, 30)))))
        val service = HistoryReadService(plans, FakeDoseEventRepository())

        val range = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), utc, now)

        assertEquals(1, range.days.size)
        assertEquals(LocalDate.of(2025, 1, 5), range.days.single().date)
        assertEquals(0, range.recordedCount)
        assertEquals(1, range.unrecordedCount)
    }

    @Test
    fun `multi-day range returns ascending days that have entries only`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(syntheticPlan(slots = listOf(LocalTime.of(8, 30)))))
        val service = HistoryReadService(plans, FakeDoseEventRepository())

        val range = service.readRange(LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 6), utc, now)

        assertEquals(
            listOf(
                LocalDate.of(2025, 1, 3),
                LocalDate.of(2025, 1, 4),
                LocalDate.of(2025, 1, 5),
                LocalDate.of(2025, 1, 6)
            ),
            range.days.map { it.date }
        )
        assertEquals(4, range.unrecordedCount)
    }

    @Test
    fun `context events outside the requested range are filtered out`() = runBlocking {
        val contextOnly = doseEvent(
            id = UUID(7L, 1L),
            occurredAt = "2025-01-04T09:00:00Z",
            localDate = LocalDate.of(2025, 1, 4)
        )
        val events = FakeDoseEventRepository(listOf(contextOnly)).apply { rangeEvents = listOf(contextOnly) }
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val range = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), utc, now)

        assertEquals(0, range.unmatchedActualCount)
        assertTrue(range.days.isEmpty())
    }

    @Test
    fun `cross-midnight legacy event is reported on the requested day`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(syntheticPlan(slots = listOf(LocalTime.of(0, 0)))))
        val legacy = doseEvent(
            id = UUID(7L, 2L),
            occurredAt = "2025-01-04T23:00:00Z",
            localDate = null,
            zoneId = null,
            source = DoseEventSource.LEGACY
        )
        val events = FakeDoseEventRepository(listOf(legacy)).apply { rangeEvents = listOf(legacy) }
        val service = HistoryReadService(plans, events)

        val range = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), utc, now)

        assertEquals(1, range.recordedCount)
        val entry = range.days.single().entries.single() as MatchedHistoricalOccurrence
        assertEquals(MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, entry.matchProvenance)
        assertTrue(entry.crossesLocalDateBoundary)
        assertEquals(LocalDate.of(2025, 1, 5), entry.displayDate)
    }

    @Test
    fun `display zone drives the final day attribution of a true legacy orphan`() = runBlocking {
        // 20:00Z is still 2025-01-05 in Paris but already 2025-01-06 in Shanghai.
        val legacy = doseEvent(
            id = UUID(7L, 3L),
            occurredAt = "2025-01-05T20:00:00Z",
            localDate = null,
            zoneId = null,
            source = DoseEventSource.LEGACY
        )
        val events = FakeDoseEventRepository(listOf(legacy)).apply { rangeEvents = listOf(legacy) }
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val parisRange = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), paris, now)
        val shanghaiRange = service.readRange(LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 6), shanghai, now)
        val shanghaiSameDay = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), shanghai, now)

        assertEquals(1, parisRange.unmatchedActualCount)
        assertEquals(1, shanghaiRange.unmatchedActualCount)
        assertTrue(shanghaiSameDay.days.isEmpty())
    }

    @Test
    fun `persisted recording date wins over the instant date`() = runBlocking {
        val recorded = doseEvent(
            id = UUID(7L, 4L),
            occurredAt = "2025-01-05T20:00:00Z",
            localDate = LocalDate.of(2025, 1, 6),
            zoneId = shanghai,
            source = DoseEventSource.MANUAL
        )
        val events = FakeDoseEventRepository(listOf(recorded)).apply { rangeEvents = listOf(recorded) }
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val instantDay = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), utc, now)
        val persistedDay = service.readRange(LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 6), utc, now)

        assertTrue(instantDay.days.isEmpty())
        assertEquals(1, persistedDay.unmatchedActualCount)
        assertTrue(persistedDay.days.single().entries.single() is UnmatchedHistoricalIntake)
    }

    @Test
    fun `deleted plan keeps its orphan intake in the requested range`() = runBlocking {
        val orphan = doseEvent(
            id = UUID(7L, 5L),
            occurredAt = "2025-01-05T08:05:00Z",
            localDate = LocalDate.of(2025, 1, 5),
            slotId = UUID(3L, 3L),
            source = DoseEventSource.WEAR
        )
        val events = FakeDoseEventRepository(listOf(orphan)).apply { rangeEvents = listOf(orphan) }
        val service = HistoryReadService(FakeMedicationPlanRepository(emptyList()), events)

        val range = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), utc, now)

        assertEquals(1, range.unmatchedActualCount)
        assertEquals(0, range.unrecordedCount)
    }

    @Test
    fun `invalid range fails fast without touching repositories`() = runBlocking {
        val events = FakeDoseEventRepository()
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val error = runCatching {
            service.readRange(LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 5), utc, now)
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
        assertNull(events.lastRange)
    }

    @Test
    fun `start equals end returns that single day`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(syntheticPlan(slots = listOf(LocalTime.of(8, 30)))))
        val service = HistoryReadService(plans, FakeDoseEventRepository())
        val day = LocalDate.of(2025, 1, 5)

        val range = service.readRange(day, day, utc, now)

        assertEquals(day, range.startDate)
        assertEquals(day, range.endDate)
        assertEquals(listOf(day), range.days.map { it.date })
    }

    private fun doseEvent(
        id: UUID,
        occurredAt: String,
        localDate: LocalDate?,
        zoneId: ZoneId? = utc,
        slotId: UUID? = null,
        source: DoseEventSource = DoseEventSource.LEGACY
    ): DoseEvent = DoseEvent(
        id = id,
        route = Route.ORAL,
        occurredAt = Instant.parse(occurredAt),
        zoneId = zoneId,
        localDate = localDate,
        doseMG = 2.0,
        ester = Ester.E2,
        slotId = slotId,
        source = source
    )
}
