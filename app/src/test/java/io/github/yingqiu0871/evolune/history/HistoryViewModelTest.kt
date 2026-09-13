package io.github.yingqiu0871.evolune.history

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A-03 §18: History ViewModel state and month-query discipline.
 *
 * Every read goes through a counting fake seam, so the tests assert the exact number of
 * reads (one per month load, zero per selection) instead of trusting the UI.
 */
class HistoryViewModelTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val kiritimati: ZoneId = ZoneId.of("Pacific/Kiritimati")
    private val today: LocalDate = LocalDate.of(2025, 1, 5)
    private val now: Instant = today.atTime(4, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun `initial load queries the current month up to today`() {
        val fixture = fixture()
        try {
            assertEquals(1, fixture.source.calls.size)
            val call = fixture.source.calls.single()
            assertEquals(LocalDate.of(2025, 1, 1), call.start)
            assertEquals(today, call.end)
            assertEquals(YearMonth.of(2025, 1), fixture.viewModel.uiState.value.visibleMonth)
            assertEquals(YearMonth.of(2025, 1), fixture.viewModel.uiState.value.loadedMonth)
            assertFalse(fixture.viewModel.uiState.value.loading)
            assertFalse(fixture.viewModel.uiState.value.failed)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `initial selection is today`() {
        val fixture = fixture()
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(today, state.selectedDate)
            assertEquals(today, state.today)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `queries forward the display zone and now`() {
        val fixture = fixture()
        try {
            val call = fixture.source.calls.single()
            assertEquals(shanghai, call.zone)
            assertEquals(now, call.now)
            assertEquals(shanghai, fixture.viewModel.uiState.value.displayZone)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `today follows the display zone`() {
        // 12:00Z on 2025-01-05 is already 2025-01-06 in Kiritimati (+14).
        val fixture = fixture(zone = kiritimati, instant = Instant.parse("2025-01-05T12:00:00Z"))
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(LocalDate.of(2025, 1, 6), state.today)
            assertEquals(LocalDate.of(2025, 1, 6), state.selectedDate)
            assertEquals(LocalDate.of(2025, 1, 6), fixture.source.calls.single().end)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting another day of the same month never reads again`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectDate(LocalDate.of(2025, 1, 3))

            assertEquals(1, fixture.source.calls.size)
            assertEquals(LocalDate.of(2025, 1, 3), fixture.viewModel.uiState.value.selectedDate)
            assertEquals(YearMonth.of(2025, 1), fixture.viewModel.uiState.value.loadedMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting a future day is ignored`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectDate(LocalDate.of(2025, 1, 6))

            assertEquals(today, fixture.viewModel.uiState.value.selectedDate)
            assertEquals(1, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting a day outside the visible month is ignored`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectDate(LocalDate.of(2024, 12, 20))

            assertEquals(today, fixture.viewModel.uiState.value.selectedDate)
            assertEquals(1, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `previous month loads exactly once and ends at month end`() {
        val fixture = fixture()
        try {
            fixture.viewModel.showPreviousMonth()

            assertEquals(2, fixture.source.calls.size)
            val call = fixture.source.calls[1]
            assertEquals(LocalDate.of(2024, 12, 1), call.start)
            assertEquals(LocalDate.of(2024, 12, 31), call.end)
            val state = fixture.viewModel.uiState.value
            assertEquals(YearMonth.of(2024, 12), state.visibleMonth)
            assertEquals(LocalDate.of(2024, 12, 1), state.selectedDate)
            assertFalse(state.loading)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `next month from a past month loads the current month up to today`() {
        val fixture = fixture()
        try {
            fixture.viewModel.showPreviousMonth()
            fixture.viewModel.showNextMonth()

            assertEquals(3, fixture.source.calls.size)
            val call = fixture.source.calls[2]
            assertEquals(LocalDate.of(2025, 1, 1), call.start)
            assertEquals(today, call.end)
            assertEquals(YearMonth.of(2025, 1), fixture.viewModel.uiState.value.visibleMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `cannot navigate beyond the current month`() {
        val fixture = fixture()
        try {
            fixture.viewModel.showNextMonth()

            assertEquals(1, fixture.source.calls.size)
            assertEquals(YearMonth.of(2025, 1), fixture.viewModel.uiState.value.visibleMonth)
            assertFalse(HistoryPresentation.present(fixture.viewModel.uiState.value).canGoToNextMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `stale response cannot replace a newer month`() {
        val fixture = fixture()
        try {
            val firstLoad = CompletableDeferred<Unit>()
            fixture.source.gate = firstLoad

            fixture.viewModel.showPreviousMonth()
            val secondLoad = CompletableDeferred<Unit>()
            fixture.source.gate = secondLoad
            fixture.viewModel.showPreviousMonth()

            assertEquals(3, fixture.source.calls.size)
            secondLoad.complete(Unit)
            assertEquals(YearMonth.of(2024, 11), fixture.viewModel.uiState.value.visibleMonth)

            // The very first (stale) response resolves only now.
            firstLoad.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertEquals(YearMonth.of(2024, 11), state.visibleMonth)
            assertEquals(YearMonth.of(2024, 11), state.loadedMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a stale response that ignores cancellation still cannot replace a newer month`() {
        val fixture = fixture(ignoreCancellation = true)
        try {
            val stale = CompletableDeferred<Unit>()
            fixture.source.gate = stale

            fixture.viewModel.showPreviousMonth()
            fixture.source.gate = null
            fixture.viewModel.showPreviousMonth()

            assertEquals(YearMonth.of(2024, 11), fixture.viewModel.uiState.value.visibleMonth)

            stale.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertEquals(YearMonth.of(2024, 11), state.visibleMonth)
            assertEquals(YearMonth.of(2024, 11), state.loadedMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `failure keeps month and selection and surfaces an error`() {
        val fixture = fixture()
        try {
            fixture.source.failure = IllegalStateException("synthetic read failure")
            fixture.viewModel.retry()

            val state = fixture.viewModel.uiState.value
            assertTrue(state.failed)
            assertFalse(state.loading)
            assertEquals(YearMonth.of(2025, 1), state.visibleMonth)
            assertEquals(today, state.selectedDate)
            assertEquals(HistoryDayPhase.ERROR, HistoryPresentation.present(state).phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `retry reloads the visible month exactly once`() {
        val fixture = fixture()
        try {
            fixture.source.failure = IllegalStateException("synthetic read failure")
            fixture.viewModel.retry()
            assertEquals(2, fixture.source.calls.size)

            fixture.source.failure = null
            fixture.viewModel.retry()

            assertEquals(3, fixture.source.calls.size)
            assertEquals(LocalDate.of(2025, 1, 1), fixture.source.calls[2].start)
            assertEquals(today, fixture.source.calls[2].end)
            val state = fixture.viewModel.uiState.value
            assertFalse(state.failed)
            assertEquals(YearMonth.of(2025, 1), state.loadedMonth)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `loaded days come from the returned range`() {
        val fixture = fixture()
        try {
            fixture.source.days = { call ->
                listOf(testDay(date = call.end, entries = listOf(unrecordedEntry())))
            }
            fixture.viewModel.retry()

            val state = fixture.viewModel.uiState.value
            assertEquals(setOf(today), state.loadedDays.keys)
            assertEquals(1, state.loadedDays.getValue(today).entries.size)
            assertEquals(HistoryDayPhase.CONTENT, HistoryPresentation.present(state).phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `restored month and selection are used`() {
        val handle = SavedStateHandle(
            mapOf(
                "history.visibleMonth" to "2024-12",
                "history.selectedDate" to "2024-12-20"
            )
        )
        val fixture = fixture(handle = handle)
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(YearMonth.of(2024, 12), state.visibleMonth)
            assertEquals(LocalDate.of(2024, 12, 20), state.selectedDate)
            assertEquals(LocalDate.of(2024, 12, 1), fixture.source.calls.single().start)
            assertEquals(LocalDate.of(2024, 12, 31), fixture.source.calls.single().end)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `saved state tracks selection and month changes`() {
        val handle = SavedStateHandle()
        val first = fixture(handle = handle)
        val restored: Fixture
        try {
            first.viewModel.selectDate(LocalDate.of(2025, 1, 2))
            first.viewModel.showPreviousMonth()

            assertEquals("2024-12", handle.get<String>("history.visibleMonth"))
            assertEquals("2024-12-01", handle.get<String>("history.selectedDate"))

            restored = fixture(handle = handle)
        } finally {
            first.close()
        }
        try {
            assertEquals(YearMonth.of(2024, 12), restored.viewModel.uiState.value.visibleMonth)
            assertEquals(LocalDate.of(2024, 12, 1), restored.viewModel.uiState.value.selectedDate)
        } finally {
            restored.close()
        }
    }

    @Test
    fun `restored future month is clamped to the current month`() {
        val handle = SavedStateHandle(
            mapOf(
                "history.visibleMonth" to "2026-05",
                "history.selectedDate" to "2026-05-20"
            )
        )
        val fixture = fixture(handle = handle)
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(YearMonth.of(2025, 1), state.visibleMonth)
            assertEquals(today, state.selectedDate)
            assertEquals(today, fixture.source.calls.single().end)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `empty range keeps the loading to empty transition explicit`() {
        val fixture = fixture()
        try {
            val state = fixture.viewModel.uiState.value
            assertTrue(state.loadedDays.isEmpty())
            assertEquals(HistoryDayPhase.EMPTY, HistoryPresentation.present(state).phase)
            assertNull(state.loadedDays[today])
        } finally {
            fixture.close()
        }
    }

    // ---------- fixture ----------

    private fun fixture(
        zone: ZoneId = shanghai,
        handle: SavedStateHandle? = null,
        ignoreCancellation: Boolean = false,
        instant: Instant = now
    ): Fixture {
        val clock = Clock.fixed(instant, zone)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val source = FakeHistoryRangeSource(ignoreCancellation)
        val viewModel = HistoryViewModel(
            rangeSource = source,
            clock = clock,
            displayZone = { zone },
            savedStateHandle = handle,
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
            gate?.let { pending ->
                try {
                    pending.await()
                } catch (cancellation: CancellationException) {
                    if (!ignoreCancellation) throw cancellation
                }
            }
            failure?.let { throw it }
            return HistoricalRange(
                startDate = startDate,
                endDate = endDate,
                days = days(call)
            )
        }
    }
}
