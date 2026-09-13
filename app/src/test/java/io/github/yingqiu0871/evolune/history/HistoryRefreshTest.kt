package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A-04 §6/§7: History refresh policy and race safety.
 *
 * The refresh contract is: cold start loads once, returning to the surface refreshes once, a real
 * background → foreground transition refreshes once, a day rollover re-derives now/today and moves
 * the view forward when the visible month *was* the current month, recomposition/selection never
 * read, and a stale response (success **or** failure) that ignores cancellation can never replace
 * a newer request.
 */
class HistoryRefreshTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2025, 1, 5)
    private val now: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun `a cold start performs exactly one read`() {
        val fixture = fixture()
        try {
            assertEquals(1, fixture.source.calls.size)
            assertEquals(YearMonth.of(2025, 1), fixture.viewModel.uiState.value.loadedMonth)

            // The screen calls this once per composition entry; the first entry is owned by the
            // initial load, so it must not add a second read.
            fixture.viewModel.onSurfaceShown()
            assertEquals(1, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `returning to the surface refreshes the visible month exactly once`() {
        val fixture = fixture()
        try {
            fixture.viewModel.onSurfaceShown() // first entry: no read
            fixture.viewModel.onSurfaceShown() // tab return #1
            assertEquals(2, fixture.source.calls.size)
            fixture.viewModel.onSurfaceShown() // tab return #2
            assertEquals(3, fixture.source.calls.size)

            val last = fixture.source.calls.last()
            assertEquals(LocalDate.of(2025, 1, 1), last.start)
            assertEquals(today, last.end)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `returning to the surface while a load is in flight is coalesced`() {
        val fixture = fixture()
        try {
            val pending = CompletableDeferred<Unit>()
            fixture.source.gate = pending

            fixture.viewModel.onSurfaceShown() // first entry (marks activation, no read)
            fixture.viewModel.onSurfaceShown() // refresh → suspends on the gate
            fixture.viewModel.onSurfaceShown() // must be coalesced by the in-flight guard

            assertEquals(2, fixture.source.calls.size)

            pending.complete(Unit)
            assertEquals(2, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a background to foreground transition refreshes once`() {
        val fixture = fixture()
        try {
            fixture.viewModel.onSurfaceShown() // composition entry on cold start
            assertEquals(1, fixture.source.calls.size)

            fixture.viewModel.onAppForegrounded()

            assertEquals(2, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting a day performs no additional read`() {
        val fixture = fixture()
        try {
            fixture.viewModel.onSurfaceShown()
            val reads = fixture.source.calls.size

            fixture.viewModel.selectDate(LocalDate.of(2025, 1, 3))

            assertEquals(reads, fixture.source.calls.size)
            assertEquals(LocalDate.of(2025, 1, 3), fixture.viewModel.uiState.value.selectedDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `day rollover advances the visible month and today`() {
        val clock = MutableClock(now, utc)
        val fixture = fixture(clock = clock)
        try {
            fixture.viewModel.onSurfaceShown()
            assertEquals(LocalDate.of(2025, 1, 5), fixture.viewModel.uiState.value.today)
            assertEquals(YearMonth.of(2025, 1), fixture.viewModel.uiState.value.visibleMonth)

            // Midnight passes while the app is backgrounded on History.
            clock.instant = LocalDate.of(2025, 2, 1).atTime(0, 5).toInstant(ZoneOffset.UTC)
            fixture.viewModel.onAppForegrounded()

            val state = fixture.viewModel.uiState.value
            assertEquals(LocalDate.of(2025, 2, 1), state.today)
            assertEquals(YearMonth.of(2025, 2), state.visibleMonth)
            assertEquals(LocalDate.of(2025, 2, 1), state.selectedDate)
            assertEquals(LocalDate.of(2025, 2, 1), fixture.source.calls.last().end)
            assertEquals(
                LocalDate.of(2025, 2, 1).atTime(0, 5).toInstant(ZoneOffset.UTC),
                fixture.source.calls.last().now
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `day rollover leaves a historical month being browsed alone`() {
        val clock = MutableClock(now, utc)
        val fixture = fixture(clock = clock)
        try {
            fixture.viewModel.onSurfaceShown()
            fixture.viewModel.showPreviousMonth()
            assertEquals(YearMonth.of(2024, 12), fixture.viewModel.uiState.value.visibleMonth)

            clock.instant = LocalDate.of(2025, 2, 1).atTime(0, 5).toInstant(ZoneOffset.UTC)
            fixture.viewModel.onAppForegrounded()

            val state = fixture.viewModel.uiState.value
            assertEquals(LocalDate.of(2025, 2, 1), state.today)
            assertEquals(YearMonth.of(2024, 12), state.visibleMonth)
            assertEquals(LocalDate.of(2024, 12, 1), fixture.source.calls.last().start)
            assertEquals(LocalDate.of(2024, 12, 31), fixture.source.calls.last().end)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a stale success that ignores cancellation cannot replace the newer request`() {
        val fixture = fixture(ignoreCancellation = true)
        try {
            val stale = CompletableDeferred<Unit>()
            fixture.source.gate = stale

            fixture.viewModel.onSurfaceShown() // first entry
            fixture.viewModel.onSurfaceShown() // call #2, gated (stale candidate)

            // A newer request supersedes it: the user browses to the previous month while the
            // refresh is still in flight.
            fixture.source.gate = null
            fixture.source.days = { call -> listOf(testDay(date = call.end)) }
            fixture.viewModel.showPreviousMonth() // call #3 (newer)

            assertEquals(3, fixture.source.calls.size)
            assertEquals(YearMonth.of(2024, 12), fixture.viewModel.uiState.value.loadedMonth)

            // The stale success resolves only now and must not touch the state.
            fixture.source.failure = IllegalStateException("stale failure payload")
            stale.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertFalse(state.failed)
            assertEquals(YearMonth.of(2024, 12), state.loadedMonth)
            assertEquals(setOf(LocalDate.of(2024, 12, 31)), state.loadedDays.keys)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a stale failure that ignores cancellation cannot poison the newer request`() {
        val fixture = fixture(ignoreCancellation = true)
        try {
            val stale = CompletableDeferred<Unit>()
            fixture.source.gate = stale

            fixture.viewModel.onSurfaceShown()
            fixture.viewModel.onSurfaceShown() // call #2 (will fail, stale)

            fixture.source.gate = null
            fixture.source.days = { call -> listOf(testDay(date = call.end)) }
            fixture.viewModel.showPreviousMonth() // call #3 succeeds

            assertEquals(3, fixture.source.calls.size)

            // Now let the stale request fail.
            fixture.source.failureForCall = 2
            stale.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertFalse("a stale failure must not surface as the current error", state.failed)
            assertEquals(YearMonth.of(2024, 12), state.loadedMonth)
            assertEquals(setOf(LocalDate.of(2024, 12, 31)), state.loadedDays.keys)
        } finally {
            fixture.close()
        }
    }

    // ---------- fixture ----------

    private fun fixture(
        clock: Clock = Clock.fixed(now, utc),
        ignoreCancellation: Boolean = false
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val source = FakeHistoryRangeSource(ignoreCancellation)
        val viewModel = HistoryViewModel(
            rangeSource = source,
            clock = clock,
            displayZone = { utc },
            operationScope = scope
        )
        return Fixture(viewModel, scope, source)
    }

    private data class Fixture(
        val viewModel: HistoryViewModel,
        val scope: CoroutineScope,
        val source: FakeHistoryRangeSource
    ) {
        fun close() = scope.cancel()
    }

    /** Test clock whose instant can be moved to emulate a day rollover. */
    private class MutableClock(
        var instant: Instant,
        private val zone: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
    }

    private class FakeHistoryRangeSource(
        private val ignoreCancellation: Boolean
    ) : HistoryRangeSource {

        data class Call(
            val start: LocalDate,
            val end: LocalDate,
            val zone: ZoneId,
            val now: Instant
        )

        val calls = mutableListOf<Call>()
        var failure: Throwable? = null
        var failureForCall: Int? = null
        var gate: CompletableDeferred<Unit>? = null
        var days: (Call) -> List<HistoricalDay> = { emptyList() }

        override suspend fun read(
            startDate: LocalDate,
            endDate: LocalDate,
            displayZone: ZoneId,
            now: Instant
        ): HistoricalRange {
            val call = Call(startDate, endDate, displayZone, now)
            calls += call
            val index = calls.size
            gate?.let { pending ->
                try {
                    pending.await()
                } catch (cancellation: CancellationException) {
                    if (!ignoreCancellation) throw cancellation
                }
            }
            if (failureForCall == index) throw IllegalStateException("stale failure")
            failure?.let { throw it }
            return HistoricalRange(startDate, endDate, days(call))
        }
    }
}
