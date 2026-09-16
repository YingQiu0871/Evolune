package io.github.yingqiu0871.evolune.history.timeline

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testRange
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * V17-D-03 §16 — deterministic orchestration coverage (TR1–TR18, TR20–TR28, TR31–TR34 and the
 * implementation combination test). TR19/TR22–TR24 live in [TimelineRangePurityTest]; the
 * architecture guards live in [TimelineRangeArchitectureGuardTest].
 */
class TimelineRangeCoordinatorTest {

    private val utc: ZoneOffset = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 16)
    private val september: YearMonth = YearMonth.of(2026, 9)

    private fun instant(date: LocalDate, hour: Int = 12, minute: Int = 0): Instant =
        date.atTime(hour, minute).toInstant(utc)

    private class RecordingRangeSource : HistoryRangeSource {
        data class Call(
            val startDate: LocalDate,
            val endDate: LocalDate,
            val displayZone: ZoneId,
            val now: Instant
        )

        val calls = mutableListOf<Call>()
        var resultProvider: (LocalDate, LocalDate) -> HistoricalRange = { start, end ->
            testRange(startDate = start, endDate = end, days = emptyList())
        }
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(
            startDate: LocalDate,
            endDate: LocalDate,
            displayZone: ZoneId,
            now: Instant
        ): HistoricalRange {
            calls += Call(startDate, endDate, displayZone, now)
            gate?.await()
            failure?.let { throw it }
            return resultProvider(startDate, endDate)
        }
    }

    private class Fixture(
        val source: RecordingRangeSource,
        request: TimelineMonthRequest
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = TimelineRangeCoordinator(rangeSource = source, scope = scope, initialRequest = request)

        fun close() = scope.cancel()
    }

    private fun fixture(
        month: YearMonth = september,
        selectedDate: LocalDate = today,
        capturedAt: Instant = instant(today),
        source: RecordingRangeSource = RecordingRangeSource()
    ): Fixture = Fixture(
        source = source,
        request = TimelineMonthRequest(
            month = month,
            selectedDate = selectedDate,
            displayZone = utc,
            capturedAt = capturedAt
        )
    )

    private fun dayWithUnrecorded(date: LocalDate, slotId: Long = 1L, time: LocalTime = LocalTime.of(8, 0)): HistoricalDay =
        testDay(
            date = date,
            entries = listOf(
                unrecordedEntry(
                    occurrence = io.github.yingqiu0871.evolune.history.testOccurrence(
                        slotId = slotId,
                        date = date,
                        time = time
                    )
                )
            )
        )

    // ---------- TR1–TR5: boundaries and validation ----------

    @Test
    fun `TR1 past month reads the complete inclusive month exactly once`() {
        val fixture = fixture(month = YearMonth.of(2026, 6), selectedDate = LocalDate.of(2026, 6, 15))
        try {
            val call = fixture.source.calls.single()
            assertEquals(LocalDate.of(2026, 6, 1), call.startDate)
            assertEquals(LocalDate.of(2026, 6, 30), call.endDate)
            assertEquals(utc, call.displayZone)
            assertEquals(instant(today), call.now)

            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.EMPTY_RANGE, state.phase)
            assertEquals(LocalDate.of(2026, 6, 1), state.effectiveStartDate)
            assertEquals(LocalDate.of(2026, 6, 30), state.effectiveEndDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR2 and TR21 current month reads from the first through today and never sends future dates`() {
        val fixture = fixture()
        try {
            val call = fixture.source.calls.single()
            assertEquals(today.withDayOfMonth(1), call.startDate)
            assertEquals(today, call.endDate)
            assertTrue("future dates must never reach the source", !call.endDate.isAfter(today))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR3 future month is NOT_LOADABLE with zero reads`() {
        val fixture = fixture(
            month = YearMonth.of(2026, 10),
            selectedDate = LocalDate.of(2026, 10, 1)
        )
        try {
            assertTrue(fixture.source.calls.isEmpty())
            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.NOT_LOADABLE, state.phase)
            assertNull(state.effectiveStartDate)
            assertNull(state.effectiveEndDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR4 selection outside the requested month is INVALID_REQUEST with zero reads`() {
        val fixture = fixture(selectedDate = LocalDate.of(2026, 8, 31))
        try {
            assertTrue(fixture.source.calls.isEmpty())
            assertEquals(TimelineRangePhase.INVALID_REQUEST, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR5 selection after today is NOT_LOADABLE with zero reads`() {
        val fixture = fixture(selectedDate = today.plusDays(1))
        try {
            assertTrue(fixture.source.calls.isEmpty())
            assertEquals(TimelineRangePhase.NOT_LOADABLE, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    // ---------- TR6–TR8: content / empty taxonomy ----------

    @Test
    fun `TR6 a successful load with rows on the selected date is CONTENT`() {
        val source = RecordingRangeSource()
        source.resultProvider = { start, end -> testRange(start, end, listOf(dayWithUnrecorded(today))) }
        val fixture = fixture(source = source)
        try {
            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.CONTENT, state.phase)
            assertEquals(today, state.selectedDay?.date)
            assertEquals(1, state.timelineReadModel?.days?.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR7 a range with history but an empty selected date is EMPTY_DAY`() {
        val source = RecordingRangeSource()
        source.resultProvider = { start, end ->
            testRange(start, end, listOf(dayWithUnrecorded(today.minusDays(6))))
        }
        val fixture = fixture(source = source)
        try {
            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.EMPTY_DAY, state.phase)
            assertNull(state.selectedDay)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR8 a completely empty range is EMPTY_RANGE`() {
        val fixture = fixture()
        try {
            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.EMPTY_RANGE, state.phase)
            assertTrue(state.timelineReadModel!!.days.isEmpty())
        } finally {
            fixture.close()
        }
    }

    // ---------- TR9–TR11: selection without re-read ----------

    @Test
    fun `TR9 TR10 and HC6 in-month selection performs zero reads and switches CONTENT to EMPTY_DAY`() {
        val source = RecordingRangeSource()
        source.resultProvider = { start, end -> testRange(start, end, listOf(dayWithUnrecorded(today))) }
        val fixture = fixture(source = source)
        try {
            assertEquals(TimelineRangePhase.CONTENT, fixture.coordinator.state.value.phase)

            fixture.coordinator.selectDate(today.minusDays(1))

            assertEquals(1, fixture.source.calls.size)
            val state = fixture.coordinator.state.value
            assertEquals(today.minusDays(1), state.selectedDate)
            assertEquals(TimelineRangePhase.EMPTY_DAY, state.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR11 selection switches EMPTY_DAY back to CONTENT without a re-read`() {
        val source = RecordingRangeSource()
        source.resultProvider = { start, end -> testRange(start, end, listOf(dayWithUnrecorded(today))) }
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.selectDate(today.minusDays(1))
            assertEquals(TimelineRangePhase.EMPTY_DAY, fixture.coordinator.state.value.phase)

            fixture.coordinator.selectDate(today)

            assertEquals(1, fixture.source.calls.size)
            assertEquals(TimelineRangePhase.CONTENT, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    // ---------- TR12–TR15: refresh / retry / failure / cancellation ----------

    @Test
    fun `TR12 refresh performs exactly one additional read with the fresh capture`() {
        val fixture = fixture()
        try {
            val secondCapture = instant(today, hour = 13)
            fixture.coordinator.refresh(secondCapture)

            assertEquals(2, fixture.source.calls.size)
            assertEquals(secondCapture, fixture.source.calls[1].now)
            assertEquals(today, fixture.source.calls[1].endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR13 retry after ERROR performs exactly one additional read`() {
        val source = RecordingRangeSource()
        source.failure = IllegalStateException("read failed")
        val fixture = fixture(source = source)
        try {
            assertTrue(fixture.coordinator.state.value.failure is TimelineRangeFailure.ReadFailure)
            assertEquals(TimelineRangePhase.ERROR, fixture.coordinator.state.value.phase)

            source.failure = null
            fixture.coordinator.retry(instant(today, hour = 13))

            assertEquals(2, fixture.source.calls.size)
            assertEquals(TimelineRangePhase.EMPTY_RANGE, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR14 read failure is ERROR and never an empty state`() {
        val source = RecordingRangeSource()
        source.failure = IllegalStateException("read failed")
        val fixture = fixture(source = source)
        try {
            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.ERROR, state.phase)
            assertTrue(state.failure is TimelineRangeFailure.ReadFailure)
            assertNull(state.timelineReadModel)
            assertTrue(state.phase != TimelineRangePhase.EMPTY_RANGE)
            assertTrue(state.phase != TimelineRangePhase.EMPTY_DAY)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR15 cancellation propagates and never becomes ERROR`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            assertEquals(TimelineRangePhase.LOADING, fixture.coordinator.state.value.phase)
            fixture.close() // cancels the in-flight read
            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.LOADING, state.phase)
            assertNull(state.failure)
        } finally {
            fixture.close()
        }
    }

    // ---------- TR16, TR17, TR17a: stale and selection survival ----------

    @Test
    fun `TR16 a stale older generation cannot publish after a newer context was accepted`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        source.resultProvider = { start, end ->
            testRange(start, end, listOf(dayWithUnrecorded(LocalDate.of(2026, 8, 10))))
        }
        val fixture = fixture(source = source)
        try {
            assertEquals(1, fixture.source.calls.size)

            fixture.coordinator.load(
                TimelineMonthRequest(
                    month = YearMonth.of(2026, 8),
                    selectedDate = LocalDate.of(2026, 8, 10),
                    displayZone = utc,
                    capturedAt = instant(today)
                )
            )
            assertEquals("no parallel read while pending", 1, fixture.source.calls.size)

            gate.complete(Unit)

            assertEquals(2, fixture.source.calls.size)
            assertEquals(LocalDate.of(2026, 8, 1), fixture.source.calls[1].startDate)
            val state = fixture.coordinator.state.value
            assertEquals(YearMonth.of(2026, 8), state.requestedMonth)
            assertEquals(TimelineRangePhase.CONTENT, state.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR17 selection changed during an in-flight read survives completion`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        source.resultProvider = { start, end ->
            testRange(
                start,
                end,
                listOf(
                    dayWithUnrecorded(today, slotId = 1L, time = LocalTime.of(8, 0)),
                    dayWithUnrecorded(today.minusDays(1), slotId = 2L, time = LocalTime.of(9, 0))
                )
            )
        }
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.selectDate(today.minusDays(1))
            assertEquals(TimelineRangePhase.LOADING, fixture.coordinator.state.value.phase)

            gate.complete(Unit)

            val state = fixture.coordinator.state.value
            assertEquals(today.minusDays(1), state.selectedDate)
            assertEquals(TimelineRangePhase.CONTENT, state.phase)
            assertEquals(today.minusDays(1), state.selectedDay?.date)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR17a a zero-read terminal generation claims a newer generation and defeats the old read`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.load(
                TimelineMonthRequest(
                    month = september,
                    selectedDate = LocalDate.of(2026, 8, 31),
                    displayZone = utc,
                    capturedAt = instant(today)
                )
            )
            gate.complete(Unit)

            assertEquals(1, fixture.source.calls.size)
            assertEquals(TimelineRangePhase.INVALID_REQUEST, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    // ---------- TR18 / TR27: coalescing and latest capture ----------

    @Test
    fun `TR18 and TR27 coalesced refreshes produce exactly one follow-up using the latest capture`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            val second = instant(today, hour = 13)
            val third = instant(today, hour = 14)
            fixture.coordinator.refresh(second)
            fixture.coordinator.refresh(third)
            assertEquals(1, fixture.source.calls.size)

            gate.complete(Unit)

            assertEquals("exactly one follow-up read", 2, fixture.source.calls.size)
            assertEquals("latest capture wins", third, fixture.source.calls[1].now)
        } finally {
            fixture.close()
        }
    }

    // ---------- TR20 / TR22: displayDate preservation, no future rows ----------

    @Test
    fun `TR20 displayDate is preserved from the projection and never recomputed`() {
        val displayDate = today.minusDays(2)
        val source = RecordingRangeSource()
        source.resultProvider = { start, end ->
            testRange(
                start,
                end,
                listOf(
                    testDay(
                        date = displayDate,
                        entries = listOf(
                            unrecordedEntry(
                                occurrence = io.github.yingqiu0871.evolune.history.testOccurrence(
                                    slotId = 5L,
                                    date = today,
                                    time = LocalTime.of(8, 0)
                                ),
                                displayDate = displayDate
                            )
                        )
                    )
                )
            )
        }
        val fixture = fixture(source = source, selectedDate = displayDate)
        try {
            val state = fixture.coordinator.state.value
            assertEquals(displayDate, state.selectedDay?.date)
            assertEquals(displayDate, state.timelineReadModel?.days?.single()?.date)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR22 no future rows are synthesized`() {
        val source = RecordingRangeSource()
        source.resultProvider = { start, end ->
            testRange(start, end, listOf(dayWithUnrecorded(today), dayWithUnrecorded(today.minusDays(3), slotId = 3L)))
        }
        val fixture = fixture(source = source)
        try {
            val days = fixture.coordinator.state.value.timelineReadModel!!.days
            assertTrue(days.all { !it.date.isAfter(today) })
        } finally {
            fixture.close()
        }
    }

    // ---------- TR25 / TR26: midnight and month rollover ----------

    @Test
    fun `TR25 refresh across local midnight uses the fresh capture and expands the current month end`() {
        val late = instant(today, hour = 23, minute = 59)
        val fixture = fixture(capturedAt = late)
        try {
            assertEquals(today, fixture.source.calls.single().endDate)

            val nextDay = today.plusDays(1)
            fixture.coordinator.refresh(instant(nextDay, hour = 0, minute = 1))

            assertEquals(2, fixture.source.calls.size)
            assertEquals(nextDay, fixture.source.calls[1].endDate)
            val state = fixture.coordinator.state.value
            assertEquals(nextDay, state.today)
            assertEquals(nextDay, state.effectiveEndDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR26 refresh across the month boundary reclassifies the previous current month as past`() {
        val fixture = fixture(capturedAt = instant(today, hour = 15))
        try {
            assertEquals(today, fixture.source.calls.single().endDate)

            fixture.coordinator.refresh(instant(LocalDate.of(2026, 10, 1)))

            assertEquals(2, fixture.source.calls.size)
            assertEquals(LocalDate.of(2026, 9, 30), fixture.source.calls[1].endDate)
        } finally {
            fixture.close()
        }
    }

    // ---------- TR28–TR34: replacement, revalidation, zero-read pending ----------

    @Test
    fun `TR28 an accepted zero-read generation defeats the old active publication`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.load(
                TimelineMonthRequest(
                    month = YearMonth.of(2026, 10),
                    selectedDate = LocalDate.of(2026, 10, 1),
                    displayZone = utc,
                    capturedAt = instant(today)
                )
            )
            gate.complete(Unit)

            assertEquals("only the original read ever happened", 1, fixture.source.calls.size)
            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.NOT_LOADABLE, state.phase)
            assertEquals(YearMonth.of(2026, 10), state.requestedMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR29 a new load while reading does not start a parallel read and executes afterward`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.load(
                TimelineMonthRequest(
                    month = YearMonth.of(2026, 8),
                    selectedDate = LocalDate.of(2026, 8, 10),
                    displayZone = utc,
                    capturedAt = instant(today)
                )
            )
            assertEquals(1, fixture.source.calls.size)

            gate.complete(Unit)

            assertEquals(2, fixture.source.calls.size)
            assertEquals(LocalDate.of(2026, 8, 1), fixture.source.calls[1].startDate)
            assertEquals(YearMonth.of(2026, 8), fixture.coordinator.state.value.requestedMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR30 latest accepted context wins regardless of operation kind`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.refresh(instant(today, hour = 13))
            fixture.coordinator.load(
                TimelineMonthRequest(
                    month = YearMonth.of(2026, 8),
                    selectedDate = LocalDate.of(2026, 8, 10),
                    displayZone = utc,
                    capturedAt = instant(today, hour = 14)
                )
            )
            gate.complete(Unit)

            assertEquals(2, fixture.source.calls.size)
            assertEquals(LocalDate.of(2026, 8, 1), fixture.source.calls[1].startDate)
            assertEquals(YearMonth.of(2026, 8), fixture.coordinator.state.value.requestedMonth)

            // Conversely: a refresh after a load targets the latest logical context.
            fixture.coordinator.refresh(instant(today, hour = 15))
            assertEquals(3, fixture.source.calls.size)
            assertEquals(LocalDate.of(2026, 8, 1), fixture.source.calls[2].startDate)
            assertEquals(instant(today, hour = 15), fixture.source.calls[2].now)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR31 a future month becomes loadable after the month rollover refresh`() {
        val october = YearMonth.of(2026, 10)
        val fixture = fixture(month = october, selectedDate = LocalDate.of(2026, 10, 1), capturedAt = instant(today))
        try {
            assertTrue(fixture.source.calls.isEmpty())
            assertEquals(TimelineRangePhase.NOT_LOADABLE, fixture.coordinator.state.value.phase)

            val octoberFirst = LocalDate.of(2026, 10, 1)
            fixture.coordinator.refresh(instant(octoberFirst))

            val call = fixture.source.calls.single()
            assertEquals(octoberFirst, call.startDate)
            assertEquals(octoberFirst, call.endDate)
            assertEquals(TimelineRangePhase.EMPTY_RANGE, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR32 a future selected date becomes loadable when it becomes today`() {
        val tomorrow = today.plusDays(1)
        val fixture = fixture(selectedDate = tomorrow)
        try {
            assertTrue(fixture.source.calls.isEmpty())
            assertEquals(TimelineRangePhase.NOT_LOADABLE, fixture.coordinator.state.value.phase)

            fixture.coordinator.refresh(instant(tomorrow))

            val call = fixture.source.calls.single()
            assertEquals(tomorrow, call.endDate)
            assertEquals(TimelineRangePhase.EMPTY_RANGE, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR33 a pending zero-read context validates before reading and starts no follow-up read`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.load(
                TimelineMonthRequest(
                    month = september,
                    selectedDate = LocalDate.of(2026, 8, 31),
                    displayZone = utc,
                    capturedAt = instant(today)
                )
            )
            gate.complete(Unit)

            assertEquals("no source read for the 0-read generation", 1, fixture.source.calls.size)
            assertEquals(TimelineRangePhase.INVALID_REQUEST, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `TR34 a pending request replaced by a newer one never executes`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.load(
                TimelineMonthRequest(YearMonth.of(2026, 8), LocalDate.of(2026, 8, 10), utc, instant(today))
            )
            fixture.coordinator.load(
                TimelineMonthRequest(YearMonth.of(2026, 7), LocalDate.of(2026, 7, 10), utc, instant(today))
            )
            gate.complete(Unit)

            assertEquals(2, fixture.source.calls.size)
            assertEquals(LocalDate.of(2026, 7, 1), fixture.source.calls[1].startDate)
            assertEquals(YearMonth.of(2026, 7), fixture.coordinator.state.value.requestedMonth)
        } finally {
            fixture.close()
        }
    }

    // ---------- Implementation combination test (§22) ----------

    @Test
    fun `combination test - pending load keeps the latest valid selection and adds no extra read`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        source.resultProvider = { start, end ->
            testRange(start, end, listOf(dayWithUnrecorded(LocalDate.of(2026, 8, 20), slotId = 7L)))
        }
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.load(
                TimelineMonthRequest(
                    month = YearMonth.of(2026, 8),
                    selectedDate = LocalDate.of(2026, 8, 10),
                    displayZone = utc,
                    capturedAt = instant(today)
                )
            )
            // B is pending; a valid selection inside B's month must survive B's execution.
            fixture.coordinator.selectDate(LocalDate.of(2026, 8, 20))
            assertEquals(1, fixture.source.calls.size)

            gate.complete(Unit)

            assertEquals("selectDate adds no extra read", 2, fixture.source.calls.size)
            val state = fixture.coordinator.state.value
            assertEquals(YearMonth.of(2026, 8), state.requestedMonth)
            assertEquals(LocalDate.of(2026, 8, 20), state.selectedDate)
            assertEquals(LocalDate.of(2026, 8, 20), state.selectedDay?.date)
            assertEquals(TimelineRangePhase.CONTENT, state.phase)
        } finally {
            fixture.close()
        }
    }

    // ---------- R1: structural-validation precedence (INVALID_REQUEST before NOT_LOADABLE) ----------

    @Test
    fun `R1-A future month with out-of-month selection is INVALID_REQUEST not NOT_LOADABLE`() {
        val fixture = fixture(
            month = YearMonth.of(2026, 10),
            selectedDate = LocalDate.of(2026, 9, 30),
            capturedAt = instant(LocalDate.of(2026, 9, 30))
        )
        try {
            assertTrue(fixture.source.calls.isEmpty())
            assertEquals(TimelineRangePhase.INVALID_REQUEST, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `R1-B current month with future out-of-month selection is INVALID_REQUEST`() {
        val fixture = fixture(selectedDate = LocalDate.of(2026, 10, 1))
        try {
            assertTrue(fixture.source.calls.isEmpty())
            assertEquals(TimelineRangePhase.INVALID_REQUEST, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `R1-C structurally valid future month remains NOT_LOADABLE`() {
        val fixture = fixture(
            month = YearMonth.of(2026, 10),
            selectedDate = LocalDate.of(2026, 10, 1),
            capturedAt = instant(LocalDate.of(2026, 9, 30))
        )
        try {
            assertTrue(fixture.source.calls.isEmpty())
            assertEquals(TimelineRangePhase.NOT_LOADABLE, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `R1-D invalid request stays INVALID_REQUEST across rollover and performs no reads`() {
        val fixture = fixture(
            month = YearMonth.of(2026, 10),
            selectedDate = LocalDate.of(2026, 9, 30),
            capturedAt = instant(LocalDate.of(2026, 9, 30))
        )
        try {
            assertEquals(TimelineRangePhase.INVALID_REQUEST, fixture.coordinator.state.value.phase)

            fixture.coordinator.refresh(instant(LocalDate.of(2026, 10, 1)))

            assertEquals("no read for a structurally invalid request", 0, fixture.source.calls.size)
            assertEquals(TimelineRangePhase.INVALID_REQUEST, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `R1-E a pending structurally invalid context claims a generation and defeats the old read`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            fixture.coordinator.load(
                TimelineMonthRequest(
                    month = YearMonth.of(2026, 10),
                    selectedDate = LocalDate.of(2026, 9, 30),
                    displayZone = utc,
                    capturedAt = instant(LocalDate.of(2026, 9, 30))
                )
            )
            gate.complete(Unit)

            assertEquals(1, fixture.source.calls.size)
            val state = fixture.coordinator.state.value
            assertEquals(TimelineRangePhase.INVALID_REQUEST, state.phase)
            assertEquals(YearMonth.of(2026, 10), state.requestedMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `R1 constructor establishes the logical context so an immediate refresh is honored`() {
        val source = RecordingRangeSource()
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val fixture = fixture(source = source)
        try {
            assertEquals(1, fixture.source.calls.size)

            val fresh = instant(today, hour = 15)
            fixture.coordinator.refresh(fresh)

            gate.complete(Unit)

            assertEquals(2, fixture.source.calls.size)
            assertEquals("the immediate refresh must not be discarded", fresh, fixture.source.calls[1].now)
        } finally {
            fixture.close()
        }
    }

    // ---------- HC1–HC7: explicit History-consistency cross assertions ----------

    @Test
    fun `HC1 projection displayDate is preserved unchanged`() {
        val displayDate = today.minusDays(4)
        val source = RecordingRangeSource()
        source.resultProvider = { start, end ->
            testRange(
                start,
                end,
                listOf(
                    testDay(
                        date = displayDate,
                        entries = listOf(
                            unrecordedEntry(
                                occurrence = io.github.yingqiu0871.evolune.history.testOccurrence(
                                    slotId = 9L,
                                    date = today,
                                    time = LocalTime.of(8, 0)
                                ),
                                displayDate = displayDate
                            )
                        )
                    )
                )
            )
        }
        val fixture = fixture(source = source, selectedDate = displayDate)
        try {
            assertEquals(displayDate, fixture.coordinator.state.value.timelineReadModel?.days?.single()?.date)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `HC2 date boundaries are inclusive`() {
        val fixture = fixture(month = YearMonth.of(2026, 6), selectedDate = LocalDate.of(2026, 6, 15))
        try {
            val call = fixture.source.calls.single()
            assertEquals(LocalDate.of(2026, 6, 1), call.startDate)
            assertEquals(LocalDate.of(2026, 6, 30), call.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `HC3 future month performs zero reads`() {
        val fixture = fixture(month = YearMonth.of(2026, 12))
        try {
            assertTrue(fixture.source.calls.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `HC4 current month read ends at today`() {
        val fixture = fixture()
        try {
            assertEquals(today, fixture.source.calls.single().endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `HC5 past month reads the complete month`() {
        val fixture = fixture(month = YearMonth.of(2026, 5), selectedDate = LocalDate.of(2026, 5, 15))
        try {
            assertEquals(LocalDate.of(2026, 5, 31), fixture.source.calls.single().endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `HC7 an empty selected date is the neutral EMPTY_DAY phase`() {
        val source = RecordingRangeSource()
        source.resultProvider = { start, end ->
            testRange(start, end, listOf(dayWithUnrecorded(today.minusDays(6))))
        }
        val fixture = fixture(source = source)
        try {
            assertEquals(TimelineRangePhase.EMPTY_DAY, fixture.coordinator.state.value.phase)
        } finally {
            fixture.close()
        }
    }
}
