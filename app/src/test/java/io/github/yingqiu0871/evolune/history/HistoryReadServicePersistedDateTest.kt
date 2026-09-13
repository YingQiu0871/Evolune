package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A-02-R2 regression suite: History completeness must not depend on a finite instant
 * padding, because the persisted `localDate` is written independently of `occurredAt`.
 *
 * A reminder confirmation stores the *planned* day while `occurredAt` is the action
 * instant, and a Wear confirmation stores the day carried by the command; neither write
 * path bounds the difference. These tests build such authoritative rows directly (no UI
 * involved) and assert that the persisted-date channel still brings them into History.
 */
class HistoryReadServicePersistedDateTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val planDay: LocalDate = LocalDate.of(2025, 6, 15)
    private val slotId: UUID = UUID(1L, 0L)
    private val planId: UUID = UUID(0L, 900L)

    // ---------- delayed recording: reminder ----------

    @Test
    fun `delayed reminder confirmation is matched on the planned day, not reported unrecorded`() = runBlocking {
        val plan = plan(time = LocalTime.of(8, 0))
        // Reminder confirmed six days late: persisted localDate is the planned day.
        val event = recordedEvent(
            id = UUID(7L, 1L),
            occurredAt = parisInstant(planDay.plusDays(6), LocalTime.of(9, 30)),
            localDate = planDay,
            zoneId = paris,
            slotId = slotId,
            source = DoseEventSource.REMINDER
        )
        val events = DualChannelDoseEvents(listOf(event))
        val service = HistoryReadService(FakeMedicationPlanRepository(listOf(plan)), events)

        val range = service.readRange(planDay, planDay, paris, now = parisInstant(planDay.plusDays(30), LocalTime.of(12, 0)))

        assertEquals("occurrence must be recorded, not unrecorded", 1, range.recordedCount)
        assertEquals(0, range.unrecordedCount)
        assertEquals(0, range.unmatchedActualCount)
        val entry = range.days.single().entries.single() as MatchedHistoricalOccurrence
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertEquals(planDay, entry.displayDate)
        assertEquals("actual instant is preserved", event.occurredAt, entry.event.occurredAt)
        // The row is outside any sensible instant window for that day, so it can only
        // have arrived through the persisted-date channel.
        assertTrue(
            "delayed instant must be outside the instant context",
            requireNotNull(events.lastInstantRange).second.isBefore(event.occurredAt)
        )
        assertNotNull("persisted-date channel must have run", events.lastLocalDateRange)
    }

    // ---------- delayed recording: Wear confirm ----------

    @Test
    fun `delayed wear confirmation is matched on the carried day`() = runBlocking {
        val plan = plan(time = LocalTime.of(8, 0))
        val event = recordedEvent(
            id = UUID(7L, 2L),
            occurredAt = parisInstant(planDay.plusDays(30), LocalTime.of(7, 15)),
            localDate = planDay,
            zoneId = paris,
            slotId = slotId,
            source = DoseEventSource.WEAR
        )
        val events = DualChannelDoseEvents(listOf(event))
        val service = HistoryReadService(FakeMedicationPlanRepository(listOf(plan)), events)

        val range = service.readRange(planDay, planDay, paris, now = parisInstant(planDay.plusDays(60), LocalTime.of(12, 0)))

        assertEquals(1, range.recordedCount)
        assertEquals(0, range.unrecordedCount)
        val entry = range.days.single().entries.single() as MatchedHistoricalOccurrence
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertEquals(planDay, entry.displayDate)
    }

    @Test
    fun `arbitrarily large divergence still reaches History`() = runBlocking {
        val plan = plan(time = LocalTime.of(8, 0))
        val event = recordedEvent(
            id = UUID(7L, 3L),
            occurredAt = parisInstant(planDay.plusDays(400), LocalTime.of(6, 0)),
            localDate = planDay,
            zoneId = paris,
            slotId = slotId,
            source = DoseEventSource.REMINDER
        )
        val events = DualChannelDoseEvents(listOf(event))
        val service = HistoryReadService(FakeMedicationPlanRepository(listOf(plan)), events)

        val range = service.readRange(planDay, planDay, paris, now = parisInstant(planDay.plusDays(500), LocalTime.of(12, 0)))

        assertEquals("completeness must not depend on any padding value", 1, range.recordedCount)
        assertEquals(0, range.unrecordedCount)
    }

    // ---------- dual channel union ----------

    @Test
    fun `a row returned by both channels is projected exactly once`() = runBlocking {
        val plan = plan(time = LocalTime.of(8, 0))
        // Same-day recording: persisted date inside the date context AND occurredAt
        // inside the instant context, so both channels return it.
        val event = recordedEvent(
            id = UUID(7L, 4L),
            occurredAt = parisInstant(planDay, LocalTime.of(8, 5)),
            localDate = planDay,
            zoneId = paris,
            slotId = slotId,
            source = DoseEventSource.REMINDER
        )
        val events = DualChannelDoseEvents(listOf(event))
        val service = HistoryReadService(FakeMedicationPlanRepository(listOf(plan)), events)

        val range = service.readRange(planDay, planDay, paris, now = parisInstant(planDay, LocalTime.of(12, 0)))

        assertTrue("event must be visible through both channels", events.instantHits > 0 && events.localDateHits > 0)
        assertEquals("recorded count must not double", 1, range.recordedCount)
        assertEquals(1, range.days.single().entries.size)
        assertEquals(1, range.days.single().entries.count { it is MatchedHistoricalOccurrence })
    }

    @Test
    fun `conflicting rows for the same event id fail fast instead of picking a winner`() = runBlocking {
        val plan = plan(time = LocalTime.of(8, 0))
        val viaDate = recordedEvent(
            id = UUID(7L, 5L),
            occurredAt = parisInstant(planDay, LocalTime.of(8, 5)),
            localDate = planDay,
            zoneId = paris,
            slotId = slotId,
            source = DoseEventSource.REMINDER,
            doseMG = 2.0
        )
        val viaInstant = viaDate.copy(doseMG = 3.0)
        val events = ConflictingChannelDoseEvents(viaDate, viaInstant)
        val service = HistoryReadService(FakeMedicationPlanRepository(listOf(plan)), events)

        val error = runCatching {
            service.readRange(planDay, planDay, paris, now = parisInstant(planDay, LocalTime.of(12, 0)))
        }.exceptionOrNull()

        assertTrue("expected fail-fast on conflicting channel rows, got $error", error is IllegalStateException)
    }

    // ---------- null-localDate legacy rows stay on the instant channel ----------

    @Test
    fun `true legacy orphan is still read through the instant channel and keeps display-zone attribution`() = runBlocking {
        val orphan = DoseEvent(
            id = UUID(7L, 6L),
            route = Route.ORAL,
            occurredAt = Instant.parse("2025-01-05T20:00:00Z"),
            zoneId = null,
            localDate = null,
            doseMG = 2.0,
            ester = Ester.E2,
            slotId = null,
            source = DoseEventSource.LEGACY
        )
        val events = DualChannelDoseEvents(listOf(orphan))
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val parisRange = service.readRange(
            LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5),
            ZoneId.of("Europe/Paris"), Instant.parse("2025-02-01T00:00:00Z")
        )
        val shanghaiRange = service.readRange(
            LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 6),
            ZoneId.of("Asia/Shanghai"), Instant.parse("2025-02-01T00:00:00Z")
        )

        assertEquals(0, events.localDateHits)
        assertEquals(1, parisRange.unmatchedActualCount)
        assertEquals(
            HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED,
            (parisRange.days.single().entries.single() as UnmatchedHistoricalIntake).displayDateProvenance
        )
        assertEquals(1, shanghaiRange.unmatchedActualCount)
    }

    @Test
    fun `cross-midnight legacy inference still works with the persisted-date channel present`() = runBlocking {
        val plan = plan(time = LocalTime.of(0, 0))
        val previousDayEvent = DoseEvent(
            id = UUID(7L, 7L),
            route = Route.ORAL,
            occurredAt = Instant.parse("2025-01-04T23:00:00Z"),
            zoneId = null,
            localDate = null,
            doseMG = 2.0,
            ester = Ester.E2,
            slotId = null,
            source = DoseEventSource.LEGACY
        )
        val events = DualChannelDoseEvents(listOf(previousDayEvent))
        val service = HistoryReadService(FakeMedicationPlanRepository(listOf(plan)), events)

        val range = service.readRange(
            LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5),
            ZoneId.of("UTC"), Instant.parse("2025-02-01T00:00:00Z")
        )

        assertEquals(1, range.recordedCount)
        val entry = range.days.single().entries.single() as MatchedHistoricalOccurrence
        assertTrue(entry.crossesLocalDateBoundary)
        assertEquals(MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, entry.matchProvenance)
    }

    // ---------- context-only rows must not leak ----------

    @Test
    fun `persisted-date context rows outside the requested range do not leak`() = runBlocking {
        val plan = plan(time = LocalTime.of(8, 0))
        val inside = recordedEvent(
            id = UUID(7L, 8L),
            occurredAt = parisInstant(planDay, LocalTime.of(8, 5)),
            localDate = planDay,
            zoneId = paris,
            slotId = slotId,
            source = DoseEventSource.MANUAL
        )
        val before = recordedEvent(
            id = UUID(7L, 9L),
            occurredAt = parisInstant(planDay.minusDays(1), LocalTime.of(8, 5)),
            localDate = planDay.minusDays(1),
            zoneId = paris,
            slotId = slotId,
            source = DoseEventSource.MANUAL
        )
        val after = recordedEvent(
            id = UUID(7L, 10L),
            occurredAt = parisInstant(planDay.plusDays(1), LocalTime.of(8, 5)),
            localDate = planDay.plusDays(1),
            zoneId = paris,
            slotId = slotId,
            source = DoseEventSource.MANUAL
        )
        val events = DualChannelDoseEvents(listOf(before, inside, after))
        val service = HistoryReadService(FakeMedicationPlanRepository(listOf(plan)), events)

        val range = service.readRange(planDay, planDay, paris, now = parisInstant(planDay.plusDays(5), LocalTime.of(12, 0)))

        assertEquals(1, range.days.size)
        assertEquals(planDay, range.days.single().date)
        assertEquals(1, range.recordedCount)
        // All three rows were fetched by the persisted-date channel (context is ±1 day),
        // yet only the requested day is returned.
        assertEquals(3, events.localDateHits)
        assertTrue(
            "persisted-date channel must span the occurrence context",
            requireNotNull(events.lastLocalDateRange).first == planDay.minusDays(1) &&
                requireNotNull(events.lastLocalDateRange).second == planDay.plusDays(1)
        )
    }

    // ---------- helpers ----------

    private fun plan(
        time: LocalTime,
        scheduleType: ScheduleType = ScheduleType.DAILY,
        intervalDays: Int = 1,
        daysOfWeek: Set<DayOfWeek> = emptySet(),
        createdAt: Instant = Instant.parse("2024-01-02T03:04:05Z")
    ): MedicationPlan = MedicationPlan(
        id = planId,
        name = "Persisted date plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = scheduleType,
        slots = listOf(
            ScheduledDoseSlot(id = slotId, planId = planId, localTime = time, position = 0)
        ),
        daysOfWeek = daysOfWeek,
        intervalDays = intervalDays,
        isEnabled = true,
        extras = emptyMap(),
        createdAt = createdAt
    )

    private fun recordedEvent(
        id: UUID,
        occurredAt: Instant,
        localDate: LocalDate?,
        zoneId: ZoneId?,
        slotId: UUID?,
        source: DoseEventSource,
        doseMG: Double = 2.0
    ): DoseEvent = DoseEvent(
        id = id,
        route = Route.ORAL,
        occurredAt = occurredAt,
        zoneId = zoneId,
        localDate = localDate,
        doseMG = doseMG,
        ester = Ester.E2,
        slotId = slotId,
        source = source
    )

    private fun parisInstant(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(paris).toInstant()

    /**
     * Fake that honours **both** production query channels: a fake returning everything
     * regardless of the requested window would hide exactly the defect under test.
     */
    private class DualChannelDoseEvents(
        private val all: List<DoseEvent>
    ) : DoseEventRepository by FakeDoseEventRepository() {
        var lastInstantRange: Pair<Instant, Instant>? = null
            private set
        var lastLocalDateRange: Pair<LocalDate, LocalDate>? = null
            private set
        var instantHits = 0
            private set
        var localDateHits = 0
            private set

        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> {
            lastInstantRange = startInclusive to endExclusive
            val hits = all.filter { !it.occurredAt.isBefore(startInclusive) && it.occurredAt.isBefore(endExclusive) }
            instantHits = hits.size
            return hits
        }

        override suspend fun findRecordedLocalDateBetween(
            startInclusive: LocalDate,
            endInclusive: LocalDate
        ): List<DoseEvent> {
            lastLocalDateRange = startInclusive to endInclusive
            val hits = all.filter { event ->
                val date = event.localDate ?: return@filter false
                !date.isBefore(startInclusive) && !date.isAfter(endInclusive)
            }
            localDateHits = hits.size
            return hits
        }
    }

    private class ConflictingChannelDoseEvents(
        private val viaDate: DoseEvent,
        private val viaInstant: DoseEvent
    ) : DoseEventRepository by FakeDoseEventRepository() {
        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> = listOf(viaInstant)

        override suspend fun findRecordedLocalDateBetween(
            startInclusive: LocalDate,
            endInclusive: LocalDate
        ): List<DoseEvent> = listOf(viaDate)
    }
}
