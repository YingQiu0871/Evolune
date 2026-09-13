package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * A-02-R2: chunked occurrence generation must not change recurrence behaviour.
 *
 * `HistoryReadService` splits the occurrence context into chronological half-open chunks
 * whenever the requested range approaches [HistoryReadService.MAX_RANGE_DAYS], because
 * the generator caps a single window at [OccurrenceGenerationWindow.MAX_WINDOW_DAYS].
 * These tests use a **real plan** and a range long enough to force chunking, then assert
 * the recurrence phase, the absence of gaps and the absence of duplicates across the
 * chunk boundary.
 */
class HistoryReadServiceOccurrenceChunkingTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val anchorInstant: Instant = Instant.parse("2024-01-02T03:04:05Z")
    private val anchorDate: LocalDate = anchorInstant.atZone(utc).toLocalDate()
    private val planId: UUID = UUID(0L, 950L)
    private val slotId: UUID = UUID(1L, 0L)
    private val start: LocalDate = LocalDate.of(2025, 1, 1)
    private val end: LocalDate = start.plusDays(HistoryReadService.MAX_RANGE_DAYS - 1)
    private val now: Instant = Instant.parse("2036-01-01T00:00:00Z")

    @Test
    fun `the occurrence context really exceeds the generator window cap, so chunking happens`() {
        val contextDays = ChronoUnit.DAYS.between(
            start.minusDays(HistoryReadService.OCCURRENCE_CONTEXT_DAYS),
            end.plusDays(HistoryReadService.OCCURRENCE_CONTEXT_DAYS + 1)
        )
        assertTrue(
            "context of $contextDays days must exceed the generator cap so this suite exercises chunking",
            contextDays > OccurrenceGenerationWindow.MAX_WINDOW_DAYS
        )
    }

    @Test
    fun `custom interval schedule keeps its phase across the chunk boundary`() = runBlocking {
        val intervalDays = 3
        val plan = plan(ScheduleType.CUSTOM, intervalDays = intervalDays, daysOfWeek = emptySet())
        val range = readRange(plan)

        val dates = range.days.map { it.date }
        assertEquals("exactly one occurrence per plan day", range.days.size, range.days.count { it.entries.size == 1 })
        assertEquals("no date may repeat", dates.size, dates.toSet().size)
        assertTrue(
            "every occurrence date must keep the anchor phase",
            dates.all { ChronoUnit.DAYS.between(anchorDate, it) % intervalDays == 0L }
        )
        dates.zipWithNext().forEach { (previous, next) ->
            assertEquals(
                "no gap and no extra occurrence across the chunk boundary",
                intervalDays.toLong(),
                ChronoUnit.DAYS.between(previous, next)
            )
        }

        val expected = expectedDates(intervalDays)
        assertTrue("a 3660-day range must produce many occurrences, got ${dates.size}", dates.size > 1_000)
        assertEquals("occurrence count must match an independent recurrence computation", expected, dates)
        assertBoundariesPresent(dates)
    }

    @Test
    fun `weekly schedule keeps its phase across the chunk boundary`() = runBlocking {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        val plan = plan(ScheduleType.WEEKLY, intervalDays = 1, daysOfWeek = days)
        val range = readRange(plan)

        val dates = range.days.map { it.date }
        assertEquals(range.days.size, range.days.count { it.entries.size == 1 })
        assertEquals(dates.size, dates.toSet().size)
        assertTrue(
            "weekly occurrences must only land on the configured weekdays",
            dates.all { it.dayOfWeek in days }
        )
        dates.zipWithNext().forEach { (previous, next) ->
            val delta = ChronoUnit.DAYS.between(previous, next)
            assertTrue("weekly delta must be 3 or 4 days, saw $delta", delta == 3L || delta == 4L)
        }

        val expected = expectedDates(intervalDays = 1, daysOfWeek = days)
        assertTrue("a 3660-day range must produce many occurrences, got ${dates.size}", dates.size > 900)
        assertEquals("occurrence count must match an independent recurrence computation", expected, dates)
        assertBoundariesPresent(dates)
    }

    // ---------- helpers ----------

    private suspend fun readRange(plan: MedicationPlan) = HistoryReadService(
        FakeMedicationPlanRepository(listOf(plan)),
        FakeDoseEventRepository()
    ).readRange(start, end, utc, now)

    /** Independently recomputes the expected occurrence dates from the schedule anchor. */
    private fun expectedDates(
        intervalDays: Int,
        daysOfWeek: Set<DayOfWeek> = emptySet()
    ): List<LocalDate> = generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end) }
        .filter { date ->
            when {
                daysOfWeek.isNotEmpty() -> date.dayOfWeek in daysOfWeek
                else -> ChronoUnit.DAYS.between(anchorDate, date) % intervalDays == 0L
            }
        }
        .toList()

    /** The first and the last requested occurrences must survive the chunk split. */
    private fun assertBoundariesPresent(dates: List<LocalDate>) {
        assertTrue("first occurrence must not be dropped", ChronoUnit.DAYS.between(start, dates.first()) < 7)
        assertTrue("last occurrence must not be dropped", ChronoUnit.DAYS.between(dates.last(), end) < 7)
    }

    private fun plan(
        scheduleType: ScheduleType,
        intervalDays: Int,
        daysOfWeek: Set<DayOfWeek>
    ): MedicationPlan = MedicationPlan(
        id = planId,
        name = "Chunking plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = scheduleType,
        slots = listOf(
            ScheduledDoseSlot(id = slotId, planId = planId, localTime = LocalTime.of(8, 0), position = 0)
        ),
        daysOfWeek = daysOfWeek,
        intervalDays = intervalDays,
        isEnabled = true,
        extras = emptyMap(),
        createdAt = anchorInstant
    )
}
