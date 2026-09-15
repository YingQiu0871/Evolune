package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * V17-C-01 §13.1: all-history reader capability of HistoryReadService.
 */
class HistoryReadServiceAllAvailableTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val base: Instant = Instant.parse("2025-01-01T00:00:00Z")

    // ---------- R1: no 30d / recent-20 selection ----------

    @Test
    fun `R1 all history is consumed without any 30-day or recent-20 selection`() = runBlocking {
        val events = (0 until 25).map { index ->
            event(
                id = UUID(0L, index.toLong() + 1),
                at = base.minus(Duration.ofDays((85 - index).toLong())),
                doseMG = 1.0 + index
            )
        }
        val history = service(events = events).readAllAvailable(
            upperBoundInclusive = base,
            displayZone = utc,
            policy = MedicationOccurrencePolicy()
        )

        val referenced = eventIdsOf(history)
        assertEquals(25, referenced.size)
        assertEquals(events.map { it.id }.toSet(), referenced.toSet())
        assertEquals(base.minus(Duration.ofDays(85)), history.lookbackStart)
    }

    // ---------- R2: inclusive upper bound is passed exactly ----------

    @Test
    fun `R2 upper bound is passed exactly to the all-history channel`() = runBlocking {
        val repository = FakeDoseEventRepository(emptyList())
        val service = HistoryReadService(FakeMedicationPlanRepository(), repository)

        service.readAllAvailable(base, utc, MedicationOccurrencePolicy())

        assertEquals(base, repository.lastAllEventsUpperBound)
        assertEquals(1, repository.allEventsUpperBoundCalls)
    }

    // ---------- R3: MAX_RANGE_DAYS is not a retrospective cutoff ----------

    @Test
    fun `R3 events older than 4000 days remain in the consumed projection`() = runBlocking {
        val ancient = event(UUID(0L, 11L), at = base.minus(Duration.ofDays(4000)), doseMG = 3.0)
        val history = service(events = listOf(ancient)).readAllAvailable(
            base,
            utc,
            MedicationOccurrencePolicy()
        )

        assertEquals(setOf(ancient.id), eventIdsOf(history))
        assertEquals(ancient.occurredAt, history.lookbackStart)
    }

    // ---------- R4: > 3660-day occurrence context is chunked ----------

    @Test
    fun `R4 occurrence context beyond 3660 days is chunked without gaps or duplicates`() = runBlocking {
        val plan = plan(
            id = UUID(0L, 21L),
            slots = listOf(LocalTime.of(8, 0)),
            createdAt = base
        )
        val event = event(UUID(0L, 22L), at = base.plus(Duration.ofHours(1)), doseMG = 2.0)
        val upperBound = base.plus(Duration.ofDays(4000))

        val history = service(plans = listOf(plan), events = listOf(event))
            .readAllAvailable(upperBound, utc, MedicationOccurrencePolicy())

        val occurrenceIds = history.projection.entries.mapNotNull { entry ->
            when (entry) {
                is MatchedHistoricalOccurrence -> entry.occurrence.id.value
                is UnrecordedHistoricalOccurrence -> entry.occurrence.id.value
                is UnmatchedHistoricalIntake -> null
            }
        }
        assertEquals(occurrenceIds.size, occurrenceIds.toSet().size)
        val expectedEntries = expectedEntryCount(
            slotTimes = listOf(LocalTime.of(8, 0)),
            anchorDate = base.atZone(utc).toLocalDate(),
            upperBound = upperBound
        )
        assertEquals(expectedEntries, occurrenceIds.size.toLong())
    }

    // ---------- R5: single generator call over the per-call guard ----------

    @Test
    fun `R5 a single generator call beyond 100000 occurrences fails as the history diagnostic`() = runBlocking {
        val plan = plan(
            id = UUID(0L, 31L),
            slots = (0 until 30).map { LocalTime.of(it / 2, (it % 2) * 30) },
            createdAt = base
        )
        val event = event(UUID(0L, 32L), at = base, doseMG = 2.0)
        val upperBound = base.plus(Duration.ofDays(3650))

        try {
            service(plans = listOf(plan), events = listOf(event))
                .readAllAvailable(upperBound, utc, MedicationOccurrencePolicy())
            throw AssertionError("expected the per-call occurrence guard to fail")
        } catch (expected: HistoricalOccurrenceLimitExceededException) {
            assertTrue(requireNotNull(expected.message).isNotBlank())
        }
    }

    // ---------- R6: no cumulative cap across chunks ----------

    @Test
    fun `R6 multiple legal chunks may total more than 100000 occurrences`() = runBlocking {
        val slotTimes = (0 until 20).map { LocalTime.of(it / 2, (it % 2) * 30) }
        val plan = plan(
            id = UUID(0L, 41L),
            slots = slotTimes,
            createdAt = base
        )
        val event = event(UUID(0L, 42L), at = base, doseMG = 2.0)
        val upperBound = base.plus(Duration.ofDays(7500))

        val history = service(plans = listOf(plan), events = listOf(event))
            .readAllAvailable(upperBound, utc, MedicationOccurrencePolicy())

        val occurrenceRefs = history.projection.entries.count { entry ->
            entry is MatchedHistoricalOccurrence || entry is UnrecordedHistoricalOccurrence
        }
        val expectedEntries = expectedEntryCount(
            slotTimes = slotTimes,
            anchorDate = base.atZone(utc).toLocalDate(),
            upperBound = upperBound
        )
        assertEquals(expectedEntries, occurrenceRefs.toLong())
        assertTrue("the test must actually exceed 100000 occurrences", occurrenceRefs > 100_000)
    }

    // ---------- R7: plan occurrences between the last event and the bound ----------

    @Test
    fun `R7 occurrence context covers the span between the last event and the upper bound`() = runBlocking {
        val plan = plan(
            id = UUID(0L, 51L),
            slots = listOf(LocalTime.of(8, 0)),
            createdAt = base
        )
        val event = event(UUID(0L, 52L), at = base.plus(Duration.ofHours(12)), doseMG = 2.0)
        val upperBound = base.plus(Duration.ofDays(5))

        val history = service(plans = listOf(plan), events = listOf(event))
            .readAllAvailable(upperBound, utc, MedicationOccurrencePolicy())

        // Occurrences at or before the inclusive upper bound are historical facts; the
        // bound-day 08:00 occurrence lies after the midnight bound and is upcoming context.
        val unrecorded = history.projection.entries.filterIsInstance<UnrecordedHistoricalOccurrence>()
        assertEquals(5, unrecorded.size)
        val dates = unrecorded.map { it.displayDate }.toSet()
        assertTrue(base.plus(Duration.ofDays(4)).atZone(utc).toLocalDate() in dates)
        val futureDates = history.projection.futureOccurrences
            .map { it.occurrence.scheduledAt.atZone(utc).toLocalDate() }
            .toSet()
        assertTrue(
            "the bound-day plan occurrence must be materialized as upcoming context",
            base.plus(Duration.ofDays(5)).atZone(utc).toLocalDate() in futureDates
        )
    }

    // ---------- R4.2 candidateDate regression ----------

    @Test
    fun `R4p2 occurrence context follows the persisted date only and does not expand both dates`() = runBlocking {
        val day = base.atZone(utc).toLocalDate()
        val persistedDate = day.plusDays(400)
        val slotId = UUID(1L, 7L)
        val plan = plan(
            id = UUID(0L, 81L),
            slots = listOf(LocalTime.of(8, 0)),
            createdAt = base,
            slotIds = listOf(slotId)
        )
        // persisted localDate (day + 400) is intentionally far AFTER the occurredAt-derived date
        // (day); the occurrence context must follow the persisted date only.
        val event = event(
            id = UUID(0L, 82L),
            at = base,
            doseMG = 2.0,
            localDate = persistedDate,
            slotId = slotId,
            zoneId = utc
        )
        val upperBound = base.plus(Duration.ofDays(401))

        val history = service(plans = listOf(plan), events = listOf(event))
            .readAllAvailable(upperBound, utc, MedicationOccurrencePolicy())

        val occurrenceDates = (history.projection.entries.mapNotNull { entry ->
            when (entry) {
                is MatchedHistoricalOccurrence -> entry.occurrence.scheduledLocalDateTime.toLocalDate()
                is UnrecordedHistoricalOccurrence -> entry.occurrence.scheduledLocalDateTime.toLocalDate()
                is UnmatchedHistoricalIntake -> null
            }
        } + history.projection.futureOccurrences.map {
            it.occurrence.scheduledLocalDateTime.toLocalDate()
        }).toSet()

        assertTrue(
            "the derived (occurredAt) date must not expand the context: unexpected dates $occurrenceDates",
            occurrenceDates.all { !it.isBefore(persistedDate.minusDays(1)) }
        )
        assertTrue(
            "the persisted date must stay inside the context",
            persistedDate in occurrenceDates
        )
        // delayed-recording exact-slot matching is preserved
        val matched = history.projection.entries.filterIsInstance<MatchedHistoricalOccurrence>()
        assertEquals(1, matched.size)
        assertEquals(persistedDate, matched.single().displayDate)
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, matched.single().matchProvenance)
    }

    // ---------- RC-4 regression: readRange keeps its original behavior ----------

    @Test
    fun `readRange keeps its original behavior after the generator exception typing`() = runBlocking {
        val planDate = base.atZone(utc).toLocalDate()
        val slotId = UUID(1L, 9L)
        val plan = plan(
            id = UUID(0L, 95L),
            slots = listOf(LocalTime.of(8, 0)),
            createdAt = base,
            slotIds = listOf(slotId)
        )
        val event = event(
            id = UUID(0L, 96L),
            at = base.plus(Duration.ofHours(1)),
            doseMG = 2.0,
            localDate = planDate,
            slotId = slotId,
            zoneId = utc
        )
        val range = service(plans = listOf(plan), events = listOf(event))
            .readRange(planDate, planDate, utc, now = base.plus(Duration.ofDays(1)))

        assertEquals(1, range.recordedCount)
        assertEquals(0, range.unrecordedCount)
        assertEquals(0, range.unmatchedActualCount)
    }

    // ---------- R8: empty consumed set ----------

    @Test
    fun `R8 empty consumed set yields null lookbackStart and an empty projection`() = runBlocking {
        val history = service(emptyList()).readAllAvailable(base, utc, MedicationOccurrencePolicy())

        assertNull(history.lookbackStart)
        assertTrue(history.projection.entries.isEmpty())
        assertEquals(base, history.upperBoundInclusive)
    }

    // ---------- R9: duplicate authoritative ids are a contract violation ----------

    @Test
    fun `R9 duplicate authoritative event ids fail fast`() = runBlocking {
        val duplicate = event(UUID(0L, 61L), at = base.minus(Duration.ofDays(1)), doseMG = 2.0)
        val repository = FakeDoseEventRepository(emptyList()).apply {
            allEventsUpTo = listOf(duplicate, duplicate)
        }
        val service = HistoryReadService(FakeMedicationPlanRepository(), repository)

        try {
            service.readAllAvailable(base, utc, MedicationOccurrencePolicy())
            throw AssertionError("expected a duplicate-id contract violation")
        } catch (expected: IllegalStateException) {
            assertTrue(requireNotNull(expected.message).contains("duplicate"))
        }
    }

    // ---------- R10: persisted date far from the instant still matches ----------

    @Test
    fun `R10 delayed recording with far persisted date still matches phase 1`() = runBlocking {
        val planDate = base.atZone(utc).toLocalDate()
        val slotId = UUID(1L, 0L)
        val plan = plan(
            id = UUID(0L, 71L),
            slots = listOf(LocalTime.of(8, 0)),
            createdAt = base,
            slotIds = listOf(slotId)
        )
        val event = event(
            id = UUID(0L, 72L),
            at = base.plus(Duration.ofDays(400)),
            doseMG = 2.0,
            localDate = planDate,
            slotId = slotId,
            zoneId = utc
        )

        val history = service(plans = listOf(plan), events = listOf(event))
            .readAllAvailable(base.plus(Duration.ofDays(500)), utc, MedicationOccurrencePolicy())

        val matched = history.projection.entries.filterIsInstance<MatchedHistoricalOccurrence>()
        assertEquals(1, matched.size)
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, matched.single().matchProvenance)
        assertEquals(event.occurredAt, history.lookbackStart)
        assertNotNull(history.projection.entries.firstOrNull())
    }

    // ---------- helpers ----------

    /**
     * Number of generated occurrences that become historical entries: scheduled at or
     * before the inclusive upper bound and not before the plan's activation date.
     */
    private fun expectedEntryCount(
        slotTimes: List<LocalTime>,
        anchorDate: LocalDate,
        upperBound: Instant
    ): Long {
        var date = anchorDate
        var count = 0L
        while (true) {
            val atOrBeforeBound = slotTimes.count { time ->
                !date.atTime(time).atZone(utc).toInstant().isAfter(upperBound)
            }
            if (atOrBeforeBound == 0) return count
            count += atOrBeforeBound
            date = date.plusDays(1)
        }
    }

    private fun service(
        plans: List<MedicationPlan> = emptyList(),
        events: List<DoseEvent> = emptyList()
    ): HistoryReadService = HistoryReadService(
        medicationPlans = FakeMedicationPlanRepository(plans),
        doseEvents = FakeDoseEventRepository(events)
    )

    private fun eventIdsOf(history: AllAvailableHistory): Set<UUID> =
        history.projection.entries.mapNotNull { entry ->
            when (entry) {
                is MatchedHistoricalOccurrence -> entry.event.eventId
                is UnmatchedHistoricalIntake -> entry.event.eventId
                is UnrecordedHistoricalOccurrence -> null
            }
        }.toSet()

    private fun event(
        id: UUID,
        at: Instant,
        doseMG: Double,
        localDate: LocalDate? = null,
        slotId: UUID? = null,
        zoneId: ZoneId? = null,
        route: Route = Route.ORAL,
        ester: Ester = Ester.E2,
        extras: Map<ExtraKey, Double> = emptyMap()
    ): DoseEvent = DoseEvent(
        id = id,
        route = route,
        occurredAt = at,
        zoneId = zoneId,
        localDate = localDate,
        doseMG = doseMG,
        ester = ester,
        extras = extras,
        slotId = slotId,
        source = DoseEventSource.MANUAL
    )

    private fun plan(
        id: UUID,
        slots: List<LocalTime>,
        createdAt: Instant,
        slotIds: List<UUID>? = null,
        route: Route = Route.ORAL,
        ester: Ester = Ester.E2,
        doseMG: Double = 2.0,
        scheduleType: ScheduleType = ScheduleType.DAILY
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = "All-history synthetic plan",
        route = route,
        ester = ester,
        doseMG = doseMG,
        scheduleType = scheduleType,
        slots = slots.mapIndexed { position, time ->
            ScheduledDoseSlot(
                id = slotIds?.get(position) ?: UUID(2L, position.toLong()),
                planId = id,
                localTime = time,
                position = position
            )
        },
        daysOfWeek = setOf(DayOfWeek.MONDAY),
        intervalDays = 1,
        isEnabled = true,
        extras = emptyMap(),
        createdAt = createdAt
    )
}
