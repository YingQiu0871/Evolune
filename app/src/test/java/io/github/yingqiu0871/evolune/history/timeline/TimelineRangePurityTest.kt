package io.github.yingqiu0871.evolune.history.timeline

import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.testRange
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testOccurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * V17-D-03 §16 — TR19 (single projection), TR22 (no future rows), TR23 (no delta/adherence
 * fields) and TR24 (determinism). Purity/reflection style mirrors D-01's TLM coverage.
 */
class TimelineRangePurityTest {

    private val utc = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 16)
    private val september: YearMonth = YearMonth.of(2026, 9)

    private fun request(capturedAt: Instant = today.atTime(12, 0).toInstant(utc)) = TimelineMonthRequest(
        month = september,
        selectedDate = today,
        displayZone = utc,
        capturedAt = capturedAt
    )

    private class ResultSource(var result: HistoricalRange) : HistoryRangeSource {
        override suspend fun read(
            startDate: LocalDate,
            endDate: LocalDate,
            displayZone: java.time.ZoneId,
            now: Instant
        ): HistoricalRange = result
    }

    // ---------- TR19 ----------

    @Test
    fun `TR19 a successful range is projected exactly once through the closed D-01 builder`() {
        val range = testRange(
            startDate = today.withDayOfMonth(1),
            endDate = today,
            days = listOf(
                testDay(
                    date = today,
                    entries = listOf(
                        unrecordedEntry(
                            occurrence = testOccurrence(slotId = 1L, date = today, time = LocalTime.of(8, 0))
                        )
                    )
                )
            )
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = TimelineRangeCoordinator(ResultSource(range), scope, request())
            val expected = TimelineProjectionBuilder.build(range)
            assertEquals(expected, coordinator.state.value.timelineReadModel)
        } finally {
            scope.cancel()
        }
    }

    // ---------- TR22 ----------

    @Test
    fun `TR22 no future rows exist in the published model`() {
        val range = testRange(
            startDate = today.withDayOfMonth(1),
            endDate = today,
            days = listOf(
                testDay(
                    date = today.minusDays(2),
                    entries = listOf(
                        unrecordedEntry(
                            occurrence = testOccurrence(slotId = 2L, date = today.minusDays(2), time = LocalTime.of(8, 0))
                        )
                    )
                )
            )
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = TimelineRangeCoordinator(ResultSource(range), scope, request())
            val days = coordinator.state.value.timelineReadModel!!.days
            assertTrue(days.isNotEmpty())
            assertTrue(days.all { !it.date.isAfter(today) })
        } finally {
            scope.cancel()
        }
    }

    // ---------- TR23 ----------

    @Test
    fun `TR23 no delta timing or adherence fields exist in the D-03 model`() {
        val forbidden = listOf(
            "delta", "timing", "late", "early", "ontime", "on_time", "overdue",
            "missed", "skipped", "adherence", "compliance", "coverage", "lateness", "delay", "offset"
        )
        val types = listOf(
            TimelineMonthRequest::class.java,
            TimelineRangeState::class.java,
            TimelineRangeFailure::class.java
        )
        types.forEach { type ->
            type.declaredFields.forEach { field ->
                forbidden.forEach { term ->
                    assertTrue(
                        "${type.simpleName}.${field.name} must not expose '$term'",
                        !field.name.lowercase().contains(term)
                    )
                }
            }
        }
        TimelineRangePhase.entries.forEach { phase ->
            forbidden.forEach { term ->
                assertTrue(phase.name.lowercase(), !phase.name.lowercase().contains(term))
            }
        }
        assertEquals(
            listOf(
                "LOADING",
                "CONTENT",
                "EMPTY_RANGE",
                "EMPTY_DAY",
                "INVALID_REQUEST",
                "NOT_LOADABLE",
                "ERROR"
            ),
            TimelineRangePhase.entries.map { it.name }
        )
    }

    // ---------- TR24 ----------

    @Test
    fun `TR24 identical request and identical source result produce identical state`() {
        val range = testRange(
            startDate = today.withDayOfMonth(1),
            endDate = today,
            days = listOf(
                testDay(
                    date = today,
                    entries = listOf(
                        unrecordedEntry(
                            occurrence = testOccurrence(slotId = 3L, date = today, time = LocalTime.of(8, 0))
                        )
                    )
                )
            )
        )
        val scopeOne = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scopeTwo = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val first = TimelineRangeCoordinator(ResultSource(range), scopeOne, request())
            val second = TimelineRangeCoordinator(ResultSource(range), scopeTwo, request())
            assertEquals(first.state.value, second.state.value)
        } finally {
            scopeOne.cancel()
            scopeTwo.cancel()
        }
    }
}
