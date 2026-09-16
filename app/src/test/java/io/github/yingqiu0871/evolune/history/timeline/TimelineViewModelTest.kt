package io.github.yingqiu0871.evolune.history.timeline

import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.history.insights.MutableTestClock
import io.github.yingqiu0871.evolune.history.insights.RecordingRangeSource
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * V17-D-04 §31 — deterministic JVM coverage for the Timeline ViewModel command boundary:
 * UI1–UI8, UI27, UI28, UI40–UI43, UI48–UI58 and the defensive recovery command.
 *
 * The ViewModel is exercised against the closed D-03 coordinator through the approved
 * [io.github.yingqiu0871.evolune.history.HistoryRangeSource] seam; no D-03 internals are faked and
 * no UI is involved.
 */
class TimelineViewModelTest {

    private val utc: ZoneOffset = ZoneOffset.UTC
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val pagoPago: ZoneId = ZoneId.of("Pacific/Pago_Pago")
    private val kiritimati: ZoneId = ZoneId.of("Pacific/Kiritimati")

    private val today: LocalDate = LocalDate.of(2026, 9, 16)
    private val september: YearMonth = YearMonth.of(2026, 9)

    private fun instant(date: LocalDate, hour: Int = 12, minute: Int = 0): Instant =
        date.atTime(hour, minute).toInstant(utc)

    private class Harness(
        now: Instant,
        var zone: ZoneId = ZoneOffset.UTC,
        result: ((RecordingRangeSource.Call) -> HistoricalRange)? = null,
        configure: (RecordingRangeSource) -> Unit = {}
    ) {
        val clock = MutableTestClock(now, ZoneOffset.UTC)
        val source = RecordingRangeSource().apply {
            if (result != null) this.result = result
            configure(this)
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = TimelineViewModel(
            rangeSource = source,
            clock = clock,
            displayZone = { zone },
            operationScope = scope
        )

        fun close() = scope.cancel()
    }

    // ---------- UI1–UI3: initial load and activation discipline ----------

    @Test
    fun `UI1 first construction performs exactly one load for the current month and today`() {
        val harness = Harness(instant(today))
        try {
            val call = harness.source.calls.single()
            assertEquals(today.withDayOfMonth(1), call.startDate)
            assertEquals(today, call.endDate)
            assertEquals(utc, call.displayZone)
            assertEquals(instant(today), call.now)

            val intent = harness.viewModel.latestLogicalIntent
            assertEquals(september, intent.requestedMonth)
            assertEquals(today, intent.selectedDate)
            assertEquals(utc, intent.displayZone)
            assertEquals(today, intent.requestToday)

            val state = harness.viewModel.state.value
            assertEquals(september, state.requestedMonth)
            assertEquals(today, state.selectedDate)
            assertEquals(today, state.today)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI1 the first surface-shown entry never adds a duplicate read`() {
        val harness = Harness(instant(today))
        try {
            harness.viewModel.onSurfaceShown()
            assertEquals(1, harness.source.calls.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI2 a later surface re-entry refreshes once with a fresh capturedAt`() {
        val harness = Harness(instant(today))
        try {
            harness.viewModel.onSurfaceShown()
            val later = today.atTime(15, 30).toInstant(utc)
            harness.clock.instant = later
            harness.viewModel.onSurfaceShown()

            assertEquals(2, harness.source.calls.size)
            val refresh = harness.source.calls[1]
            assertEquals(later, refresh.now)
            assertEquals(utc, refresh.displayZone)
            assertEquals(today.withDayOfMonth(1), refresh.startDate)
            assertEquals(today, refresh.endDate)
            assertEquals(later.atZone(utc).toLocalDate(), harness.viewModel.latestLogicalIntent.requestToday)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI3 a foreground return refreshes once with a fresh capturedAt`() {
        val harness = Harness(instant(today))
        try {
            val later = today.atTime(18, 0).toInstant(utc)
            harness.clock.instant = later
            harness.viewModel.onAppForegrounded()

            assertEquals(2, harness.source.calls.size)
            assertEquals(later, harness.source.calls[1].now)
        } finally {
            harness.close()
        }
    }

    // ---------- UI5–UI8: month controls and selection ----------

    @Test
    fun `UI5 previous month performs exactly one load and selects the first of that month`() {
        val harness = Harness(instant(today))
        try {
            harness.viewModel.showPreviousMonth()

            assertEquals(2, harness.source.calls.size)
            val previous = harness.source.calls[1]
            assertEquals(LocalDate.of(2026, 8, 1), previous.startDate)
            assertEquals(LocalDate.of(2026, 8, 31), previous.endDate)

            val state = harness.viewModel.state.value
            assertEquals(YearMonth.of(2026, 8), state.requestedMonth)
            assertEquals(LocalDate.of(2026, 8, 1), state.selectedDate)
            assertEquals(LocalDate.of(2026, 8, 1), harness.viewModel.latestLogicalIntent.selectedDate)
            assertEquals(today, harness.viewModel.latestLogicalIntent.requestToday)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI6 next month is a no-op at the current month`() {
        val harness = Harness(instant(today))
        try {
            harness.viewModel.showNextMonth()
            assertEquals(1, harness.source.calls.size)
            assertEquals(september, harness.viewModel.latestLogicalIntent.requestedMonth)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI7 from a past month the next month resolves to today`() {
        val harness = Harness(instant(today))
        try {
            harness.viewModel.showPreviousMonth()
            harness.viewModel.showNextMonth()

            assertEquals(3, harness.source.calls.size)
            val next = harness.source.calls[2]
            assertEquals(today.withDayOfMonth(1), next.startDate)
            assertEquals(today, next.endDate)

            val state = harness.viewModel.state.value
            assertEquals(september, state.requestedMonth)
            assertEquals(today, state.selectedDate)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI8 valid selection is zero-read while out-of-month and future selections are ignored`() {
        val harness = Harness(instant(today))
        try {
            val before = harness.source.calls.size
            harness.viewModel.selectDate(LocalDate.of(2026, 9, 10))

            assertEquals("selection must never read the source", before, harness.source.calls.size)
            assertEquals(LocalDate.of(2026, 9, 10), harness.viewModel.state.value.selectedDate)
            assertEquals(LocalDate.of(2026, 9, 10), harness.viewModel.latestLogicalIntent.selectedDate)

            harness.viewModel.selectDate(LocalDate.of(2026, 8, 15))
            assertEquals(
                "an out-of-month selection must not replace the logical intent",
                LocalDate.of(2026, 9, 10),
                harness.viewModel.latestLogicalIntent.selectedDate
            )
            assertEquals(LocalDate.of(2026, 9, 10), harness.viewModel.state.value.selectedDate)

            harness.viewModel.selectDate(today.plusDays(1))
            assertEquals(
                "a future selection must not replace the logical intent",
                LocalDate.of(2026, 9, 10),
                harness.viewModel.latestLogicalIntent.selectedDate
            )
            assertEquals(before, harness.source.calls.size)
        } finally {
            harness.close()
        }
    }

    // ---------- UI27 / UI40: retry and same-zone refresh ----------

    @Test
    fun `UI40 same-zone activation refreshes with a fresh capture and preserves month and date`() {
        val harness = Harness(instant(today))
        try {
            harness.viewModel.selectDate(LocalDate.of(2026, 9, 10))
            val later = today.atTime(23, 45).toInstant(utc)
            harness.clock.instant = later
            harness.viewModel.onAppForegrounded()

            assertEquals("activation is exactly one additional read", 2, harness.source.calls.size)
            assertEquals(later, harness.source.calls[1].now)

            val state = harness.viewModel.state.value
            assertEquals(september, state.requestedMonth)
            assertEquals(LocalDate.of(2026, 9, 10), state.selectedDate)
            assertEquals(utc, state.displayZone)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI27 and UI58 retry after an error supplies a fresh capture for the same logical request`() {
        val harness = Harness(instant(today))
        try {
            harness.source.failure = IllegalStateException("read failed")
            val later = today.plusDays(1).atTime(9, 0).toInstant(utc)
            harness.clock.instant = later
            harness.viewModel.retry()

            assertEquals(2, harness.source.calls.size)
            val retryCall = harness.source.calls[1]
            assertEquals(later, retryCall.now)
            assertEquals(today.withDayOfMonth(1), retryCall.startDate)
            assertEquals(today.plusDays(1), retryCall.endDate)
            assertEquals(
                "retry updates requestToday from the same capture",
                today.plusDays(1),
                harness.viewModel.latestLogicalIntent.requestToday
            )
            assertEquals(september, harness.viewModel.latestLogicalIntent.requestedMonth)
        } finally {
            harness.close()
        }
    }

    // ---------- UI41–UI43: display-zone changes ----------

    @Test
    fun `UI41 a display-zone change submits exactly one load carrying the new zone`() {
        val harness = Harness(instant(today), zone = paris)
        try {
            harness.zone = tokyo
            harness.viewModel.onAppForegrounded()

            assertEquals(2, harness.source.calls.size)
            assertEquals(tokyo, harness.source.calls[1].displayZone)
            assertEquals(tokyo, harness.viewModel.latestLogicalIntent.displayZone)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI42 Case A keeps a past requested month and a still-valid selection`() {
        val harness = Harness(instant(today), zone = paris)
        try {
            harness.viewModel.showPreviousMonth()
            assertEquals(LocalDate.of(2026, 8, 1), harness.viewModel.latestLogicalIntent.selectedDate)

            harness.zone = tokyo
            harness.viewModel.onAppForegrounded()

            assertEquals(3, harness.source.calls.size)
            val load = harness.source.calls[2]
            assertEquals(tokyo, load.displayZone)
            assertEquals(LocalDate.of(2026, 8, 1), load.startDate)
            assertEquals(LocalDate.of(2026, 8, 31), load.endDate)

            val intent = harness.viewModel.latestLogicalIntent
            assertEquals(YearMonth.of(2026, 8), intent.requestedMonth)
            assertEquals(LocalDate.of(2026, 8, 1), intent.selectedDate)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI43 Case B resolves a now-future requested month to the new current month`() {
        val kiritimatiToday = LocalDate.of(2026, 10, 1)
        val harness = Harness(instant(kiritimatiToday, hour = 5), zone = kiritimati)
        try {
            assertEquals(YearMonth.of(2026, 10), harness.viewModel.latestLogicalIntent.requestedMonth)

            harness.zone = pagoPago
            harness.viewModel.onAppForegrounded()

            assertEquals(2, harness.source.calls.size)
            val load = harness.source.calls[1]
            assertEquals(pagoPago, load.displayZone)
            assertEquals(LocalDate.of(2026, 9, 1), load.startDate)
            assertEquals(LocalDate.of(2026, 9, 30), load.endDate)

            val intent = harness.viewModel.latestLogicalIntent
            assertEquals(september, intent.requestedMonth)
            assertEquals(LocalDate.of(2026, 9, 30), intent.selectedDate)
            assertEquals(LocalDate.of(2026, 9, 30), intent.requestToday)
        } finally {
            harness.close()
        }
    }

    // ---------- UI48–UI50: pending-zone command targeting ----------

    @Test
    fun `UI48 activation targets the pending logical Tokyo context instead of the published Paris state`() {
        val harness = Harness(instant(today), zone = paris)
        try {
            harness.source.gateOnlyForCall = 2
            harness.source.gate = CompletableDeferred()

            harness.clock.instant = instant(today, hour = 13)
            harness.zone = tokyo
            harness.viewModel.onAppForegrounded()
            assertEquals("the Tokyo zone-change load is in flight", 2, harness.source.calls.size)

            harness.clock.instant = instant(today, hour = 14)
            harness.viewModel.onAppForegrounded()
            assertEquals("same-zone activation must not start another read yet", 2, harness.source.calls.size)

            harness.source.gate!!.complete(Unit)

            assertEquals(3, harness.source.calls.size)
            assertEquals(
                "the follow-up context must target the logical Tokyo request, never published Paris",
                tokyo,
                harness.source.calls[2].displayZone
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI49 a zone change back to the published zone performs a new load rather than a plain refresh`() {
        val harness = Harness(instant(today), zone = paris)
        try {
            harness.source.gateOnlyForCall = 2
            harness.source.gate = CompletableDeferred()

            harness.clock.instant = instant(today, hour = 13)
            harness.zone = tokyo
            harness.viewModel.onAppForegrounded()
            assertEquals(2, harness.source.calls.size)

            harness.clock.instant = instant(today, hour = 14)
            harness.zone = paris
            harness.viewModel.onAppForegrounded()
            assertEquals("the new Paris load is pending behind the in-flight read", 2, harness.source.calls.size)

            harness.source.gate!!.complete(Unit)

            assertEquals(3, harness.source.calls.size)
            assertEquals(
                "a plain refresh would have kept the logical Tokyo zone",
                paris,
                harness.source.calls[2].displayZone
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI50 a valid selection updates the logical intent immediately and survives a refresh`() {
        val harness = Harness(instant(today))
        try {
            harness.source.gateOnlyForCall = 2
            harness.source.gate = CompletableDeferred()
            harness.clock.instant = instant(today.plusDays(1), hour = 8)
            harness.viewModel.onAppForegrounded()
            assertEquals(2, harness.source.calls.size)

            val selection = LocalDate.of(2026, 9, 10)
            harness.viewModel.selectDate(selection)
            assertEquals(
                "the logical intent mirrors the accepted selection immediately",
                selection,
                harness.viewModel.latestLogicalIntent.selectedDate
            )
            assertEquals(selection, harness.viewModel.state.value.selectedDate)
            assertEquals(2, harness.source.calls.size)

            harness.source.gate!!.complete(Unit)
            assertEquals(
                "the selection must survive the pending refresh publication",
                selection,
                harness.viewModel.state.value.selectedDate
            )
            assertEquals(selection, harness.viewModel.latestLogicalIntent.selectedDate)
        } finally {
            harness.close()
        }
    }

    // ---------- UI54–UI58: requestToday ownership ----------

    @Test
    fun `UI54 a same-zone refresh across midnight advances requestToday and preserves month and date`() {
        val monthEnd = LocalDate.of(2026, 9, 30)
        val harness = Harness(instant(monthEnd))
        try {
            val afterMidnight = LocalDate.of(2026, 10, 1).atTime(0, 30).toInstant(utc)
            harness.clock.instant = afterMidnight
            harness.viewModel.onAppForegrounded()

            assertEquals(2, harness.source.calls.size)
            assertEquals(afterMidnight, harness.source.calls[1].now)

            val intent = harness.viewModel.latestLogicalIntent
            assertEquals(september, intent.requestedMonth)
            assertEquals(monthEnd, intent.selectedDate)
            assertEquals(LocalDate.of(2026, 10, 1), intent.requestToday)

            val state = harness.viewModel.state.value
            assertEquals(LocalDate.of(2026, 10, 1), state.today)
            assertEquals(september, state.requestedMonth)
            assertEquals(monthEnd, state.selectedDate)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI55 and UI57 selection validity follows requestToday, not an independently observed wall clock`() {
        val day10 = LocalDate.of(2026, 9, 10)
        val harness = Harness(instant(day10))
        try {
            // The wall clock already says Sep 12, but no generation-producing command captured it:
            // requestToday is still Sep 10, so a Sep 12 selection must be rejected.
            harness.clock.instant = instant(LocalDate.of(2026, 9, 12), hour = 9)
            harness.viewModel.selectDate(LocalDate.of(2026, 9, 12))
            assertEquals(day10, harness.viewModel.latestLogicalIntent.selectedDate)
            assertEquals(day10, harness.viewModel.latestLogicalIntent.requestToday)

            // A refresh captures the new today once, so the same selection becomes valid with 0 reads.
            harness.viewModel.onAppForegrounded()
            assertEquals(2, harness.source.calls.size)
            assertEquals(LocalDate.of(2026, 9, 12), harness.viewModel.latestLogicalIntent.requestToday)

            harness.viewModel.selectDate(LocalDate.of(2026, 9, 12))
            assertEquals(
                LocalDate.of(2026, 9, 12),
                harness.viewModel.latestLogicalIntent.selectedDate
            )
            harness.viewModel.selectDate(LocalDate.of(2026, 9, 13))
            assertEquals(
                "the day after requestToday stays rejected",
                LocalDate.of(2026, 9, 12),
                harness.viewModel.latestLogicalIntent.selectedDate
            )
            assertEquals(2, harness.source.calls.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI56 selectDate follows the pending logical requestToday while the published state still lags`() {
        val gate = CompletableDeferred<Unit>()
        val harness = Harness(
            instant(today),
            configure = { source ->
                // The initial load is still in flight, so the refresh stays pending behind it and
                // the published state legitimately keeps the older today.
                source.gateOnlyForCall = 1
                source.gate = gate
            }
        )
        try {
            val nextDay = today.plusDays(1)
            harness.clock.instant = instant(nextDay, hour = 10)
            harness.viewModel.onAppForegrounded()
            assertEquals("only the initial read is in flight", 1, harness.source.calls.size)

            assertEquals(
                "published state still carries the old today",
                today,
                harness.viewModel.state.value.today
            )
            assertEquals(
                "the logical request already carries the fresh today",
                nextDay,
                harness.viewModel.latestLogicalIntent.requestToday
            )

            harness.viewModel.selectDate(nextDay)
            assertEquals(nextDay, harness.viewModel.latestLogicalIntent.selectedDate)
            assertEquals(1, harness.source.calls.size)

            gate.complete(Unit)
            assertEquals("the pending refresh executes once behind the initial read", 2, harness.source.calls.size)
            assertEquals(nextDay, harness.viewModel.state.value.selectedDate)
            assertEquals(nextDay, harness.viewModel.state.value.today)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `UI58 a month change updates requestToday from the same capture that produced the read`() {
        val harness = Harness(instant(today))
        try {
            val later = LocalDate.of(2026, 9, 20).atTime(21, 15).toInstant(utc)
            harness.clock.instant = later
            harness.viewModel.showPreviousMonth()

            val call = harness.source.calls[1]
            assertEquals(later, call.now)
            assertEquals(LocalDate.of(2026, 9, 20), harness.viewModel.latestLogicalIntent.requestToday)
            assertEquals(LocalDate.of(2026, 8, 1), call.startDate)
        } finally {
            harness.close()
        }
    }

    // ---------- UI28: no UI-local pending machine; published state stays authoritative ----------

    @Test
    fun `UI28 while a refresh is pending only the D-03 published state is visible`() {
        val harness = Harness(instant(today))
        try {
            harness.source.gateOnlyForCall = 2
            harness.source.gate = CompletableDeferred()
            harness.clock.instant = instant(today.plusDays(1), hour = 6)
            harness.viewModel.onAppForegrounded()

            val pending = harness.viewModel.state.value
            assertEquals("published LOADING is rendered as is", TimelineRangePhase.LOADING, pending.phase)
            assertNull("no synthetic model may exist while pending", pending.timelineReadModel)
            assertEquals(2, harness.source.calls.size)

            harness.source.gate!!.complete(Unit)
            assertEquals(TimelineRangePhase.EMPTY_RANGE, harness.viewModel.state.value.phase)
        } finally {
            harness.close()
        }
    }

    // ---------- defensive recovery (D-04 §6.4) ----------

    @Test
    fun `returnToCurrentMonth loads the fresh current month exactly once`() {
        val harness = Harness(instant(today))
        try {
            harness.viewModel.showPreviousMonth()
            harness.clock.instant = instant(today, hour = 20)
            harness.viewModel.returnToCurrentMonth()

            assertEquals(3, harness.source.calls.size)
            val call = harness.source.calls[2]
            assertEquals(today.withDayOfMonth(1), call.startDate)
            assertEquals(today, call.endDate)
            assertEquals(instant(today, hour = 20), call.now)

            val intent = harness.viewModel.latestLogicalIntent
            assertEquals(september, intent.requestedMonth)
            assertEquals(today, intent.selectedDate)
            assertEquals(today, intent.requestToday)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `the intent mirror never exposes a generation, phase or read status`() {
        val fields = TimelineViewModel.TimelineUiRequestIntent::class.java.declaredFields
            .filter { field ->
                !java.lang.reflect.Modifier.isStatic(field.modifiers) && !field.isSynthetic
            }
            .map { it.name }
            .toSet()
        assertEquals(setOf("requestedMonth", "selectedDate", "displayZone", "requestToday"), fields)
    }
}
