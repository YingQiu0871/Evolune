package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * A-02-R1 regression suite: the event query bound must not drop authoritative events
 * whose persisted recording zone is far from the display zone.
 *
 * These tests use the JVM's own tzdb through `ZoneId`, never hand-written offsets.
 * The fake repository filters by the requested instant window on purpose: a fake that
 * returned every event regardless of the window would hide exactly this defect.
 */
class HistoryReadServiceExtremeZoneTest {

    // CE1: UTC-12 display zone, event recorded at local midnight in UTC+14.
    @Test
    fun `lower bound keeps an event whose persisted zone is far east of the display zone`() = runBlocking {
        val occurredAt = instantOf(LocalDate.of(2025, 6, 15), "00:00", ZoneId.of("Pacific/Kiritimati"))
        assertEquals(Instant.parse("2025-06-14T10:00:00Z"), occurredAt)

        val event = persistedEvent(UUID(7L, 1L), occurredAt, LocalDate.of(2025, 6, 15), ZoneId.of("Pacific/Kiritimati"))
        val events = RangeFilteringDoseEvents(listOf(event))
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val range = service.readRange(
            startDate = LocalDate.of(2025, 6, 15),
            endDate = LocalDate.of(2025, 6, 17),
            displayZone = ZoneId.of("Etc/GMT+12"),
            now = Instant.parse("2025-07-01T00:00:00Z")
        )

        assertNotNull("repository query must have run", events.recordedRange)
        val queryStart = requireNotNull(events.recordedRange).first
        assertTrue(
            "query start $queryStart must include the authoritative instant $occurredAt",
            !queryStart.isAfter(occurredAt)
        )
        // A-02-R2: this row is guaranteed by the persisted-date channel, not by padding.
        val persistedRange = requireNotNull(events.recordedLocalDateRange)
        assertTrue(
            "persisted-date channel $persistedRange must cover persisted localDate 2025-06-15",
            !persistedRange.first.isAfter(LocalDate.of(2025, 6, 15)) &&
                !persistedRange.second.isBefore(LocalDate.of(2025, 6, 15))
        )
        val intake = range.days.flatMap { it.entries }.filterIsInstance<UnmatchedHistoricalIntake>().singleOrNull()
        assertNotNull("the persisted event must not disappear from the requested range", intake)
        assertEquals(LocalDate.of(2025, 6, 15), intake!!.displayDate)
        assertEquals(HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE, intake.displayDateProvenance)
    }

    // CE2: UTC+13 display zone in January, event recorded late evening in UTC-12.
    @Test
    fun `upper bound keeps an event whose persisted zone is far west of the display zone`() = runBlocking {
        val occurredAt = instantOf(LocalDate.of(2025, 1, 12), "23:59:59", ZoneId.of("Etc/GMT+12"))
        assertEquals(Instant.parse("2025-01-13T11:59:59Z"), occurredAt)

        val event = persistedEvent(UUID(7L, 2L), occurredAt, LocalDate.of(2025, 1, 12), ZoneId.of("Etc/GMT+12"))
        val events = RangeFilteringDoseEvents(listOf(event))
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val range = service.readRange(
            startDate = LocalDate.of(2025, 1, 10),
            endDate = LocalDate.of(2025, 1, 12),
            displayZone = ZoneId.of("Pacific/Auckland"),
            now = Instant.parse("2025-02-01T00:00:00Z")
        )

        assertNotNull("repository query must have run", events.recordedRange)
        val queryEnd = requireNotNull(events.recordedRange).second
        assertTrue(
            "query end $queryEnd must be after the authoritative instant $occurredAt",
            queryEnd.isAfter(occurredAt)
        )
        // A-02-R2: this row is guaranteed by the persisted-date channel, not by padding.
        val persistedRange = requireNotNull(events.recordedLocalDateRange)
        assertTrue(
            "persisted-date channel $persistedRange must cover persisted localDate 2025-01-12",
            !persistedRange.first.isAfter(LocalDate.of(2025, 1, 12)) &&
                !persistedRange.second.isBefore(LocalDate.of(2025, 1, 12))
        )
        val intake = range.days.flatMap { it.entries }.filterIsInstance<UnmatchedHistoricalIntake>().singleOrNull()
        assertNotNull("the persisted event must not disappear from the requested range", intake)
        assertEquals(LocalDate.of(2025, 1, 12), intake!!.displayDate)
        assertEquals(HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE, intake.displayDateProvenance)
    }

    @Test
    fun `event query context is strictly wider than the occurrence context`() {
        assertTrue(
            "event query padding must exceed occurrence generation padding",
            HistoryReadService.EVENT_QUERY_CONTEXT_DAYS > HistoryReadService.OCCURRENCE_CONTEXT_DAYS
        )
        assertEquals(1L, HistoryReadService.OCCURRENCE_CONTEXT_DAYS)
        assertEquals(2L, HistoryReadService.EVENT_QUERY_CONTEXT_DAYS)
    }

    @Test
    fun `widening the event query does not change legacy orphan attribution`() = runBlocking {
        // True legacy orphan: no persisted localDate/zoneId, derived from the display zone.
        val orphan = DoseEvent(
            id = UUID(7L, 3L),
            route = Route.ORAL,
            occurredAt = Instant.parse("2025-01-05T20:00:00Z"),
            zoneId = null,
            localDate = null,
            doseMG = 2.0,
            ester = Ester.E2,
            slotId = null,
            source = DoseEventSource.LEGACY
        )
        val events = RangeFilteringDoseEvents(listOf(orphan))
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val paris = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), ZoneId.of("Europe/Paris"), Instant.parse("2025-02-01T00:00:00Z"))
        val shanghai = service.readRange(LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 6), ZoneId.of("Asia/Shanghai"), Instant.parse("2025-02-01T00:00:00Z"))
        val shanghaiSameDay = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), ZoneId.of("Asia/Shanghai"), Instant.parse("2025-02-01T00:00:00Z"))

        assertEquals(1, paris.unmatchedActualCount)
        assertEquals(HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED, (paris.days.single().entries.single() as UnmatchedHistoricalIntake).displayDateProvenance)
        assertEquals(1, shanghai.unmatchedActualCount)
        assertTrue("final inclusion must still come from the projection display date", shanghaiSameDay.days.isEmpty())
    }

    @Test
    fun `cross-midnight matching still works with a one day occurrence context`() = runBlocking {
        val plan = io.github.yingqiu0871.evolune.application.syntheticPlan(
            slots = listOf(java.time.LocalTime.of(0, 0))
        )
        val previousDay = DoseEvent(
            id = UUID(7L, 4L),
            route = Route.ORAL,
            occurredAt = Instant.parse("2025-01-04T23:00:00Z"),
            zoneId = null,
            localDate = null,
            doseMG = 2.0,
            ester = Ester.E2,
            slotId = null,
            source = DoseEventSource.LEGACY
        )
        val events = RangeFilteringDoseEvents(listOf(previousDay))
        val service = HistoryReadService(FakeMedicationPlanRepository(listOf(plan)), events)

        val range = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), ZoneId.of("UTC"), Instant.parse("2025-02-01T00:00:00Z"))

        assertEquals(1, range.recordedCount)
        val entry = range.days.single().entries.filterIsInstance<MatchedHistoricalOccurrence>().single()
        assertTrue(entry.crossesLocalDateBoundary)
    }

    // ---------- range span limits ----------

    @Test
    fun `inclusive range of exactly the maximum span is allowed`() = runBlocking {
        val start = LocalDate.of(2025, 1, 1)
        val end = start.plusDays(HistoryReadService.MAX_RANGE_DAYS - 1)
        val events = RangeFilteringDoseEvents(emptyList())
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val range = service.readRange(start, end, ZoneId.of("UTC"), Instant.parse("2036-01-01T00:00:00Z"))

        assertEquals(start, range.startDate)
        assertEquals(end, range.endDate)
        assertNotNull("query must have run", events.recordedRange)
    }

    @Test
    fun `inclusive range one day beyond the maximum span is rejected before repository access`() = runBlocking {
        val start = LocalDate.of(2025, 1, 1)
        val end = start.plusDays(HistoryReadService.MAX_RANGE_DAYS)
        val events = RangeFilteringDoseEvents(emptyList())
        val service = HistoryReadService(FakeMedicationPlanRepository(), events)

        val error = runCatching {
            service.readRange(start, end, ZoneId.of("UTC"), Instant.parse("2036-01-01T00:00:00Z"))
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
        assertNull("no repository query may run for an over-long range", events.recordedRange)
    }

    // ---------- helpers ----------

    private fun instantOf(date: LocalDate, time: String, zone: ZoneId): Instant =
        date.atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant()

    private fun persistedEvent(
        id: UUID,
        occurredAt: Instant,
        localDate: LocalDate,
        zone: ZoneId
    ): DoseEvent = DoseEvent(
        id = id,
        route = Route.ORAL,
        occurredAt = occurredAt,
        zoneId = zone,
        localDate = localDate,
        doseMG = 2.0,
        ester = Ester.E2,
        slotId = null,
        source = DoseEventSource.MANUAL
    )

    /**
     * Fake that honours both requested query channels, so a bad bound is observable.
     *
     * `findOccurredBetween` filters by the half-open instant window and
     * `findRecordedLocalDateBetween` filters by the inclusive persisted-date window: a
     * fake that returned every event regardless of the window would hide exactly the
     * defect these tests exist for.
     */
    private class RangeFilteringDoseEvents(
        private val all: List<DoseEvent>
    ) : DoseEventRepository by FakeDoseEventRepository() {
        private val recorder = FakeDoseEventRepository()
        var recordedRange: Pair<Instant, Instant>? = null
            private set
        var recordedLocalDateRange: Pair<LocalDate, LocalDate>? = null
            private set

        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> {
            recordedRange = startInclusive to endExclusive
            recorder.lastRange = startInclusive to endExclusive
            return all.filter { !it.occurredAt.isBefore(startInclusive) && it.occurredAt.isBefore(endExclusive) }
        }

        override suspend fun findRecordedLocalDateBetween(
            startInclusive: LocalDate,
            endInclusive: LocalDate
        ): List<DoseEvent> {
            recordedLocalDateRange = startInclusive to endInclusive
            return all.filter { event ->
                val date = event.localDate ?: return@filter false
                !date.isBefore(startInclusive) && !date.isAfter(endInclusive)
            }
        }
    }
}
