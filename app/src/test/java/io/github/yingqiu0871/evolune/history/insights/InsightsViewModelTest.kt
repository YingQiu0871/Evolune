package io.github.yingqiu0871.evolune.history.insights

import androidx.lifecycle.SavedStateHandle
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.insights.InsightsContractViolationException
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * v1.7-B-02 ViewModel contract: range orchestration, query discipline, failure taxonomy,
 * cancellation/generation safety, coalesced refresh, rollover and state restoration.
 *
 * Every assertion about "how many reads" comes from recording fakes, so the discipline is counted
 * rather than assumed.
 */
class InsightsViewModelTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 13)
    private val now: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC)

    // ---------- default and initial load ----------

    @Test
    fun `the default selection is last 30 days`() {
        val fixture = fixture()
        try {
            assertEquals(InsightsRangeSelection.Last30Days, fixture.viewModel.uiState.value.selection)
            assertEquals(LocalDate.of(2026, 8, 15), fixture.viewModel.uiState.value.startDate)
            assertEquals(today, fixture.viewModel.uiState.value.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the initial load performs exactly one read and one aggregation`() {
        val fixture = fixture()
        try {
            assertEquals(1, fixture.source.calls.size)
            assertEquals(1, fixture.aggregator.calls)
            assertSame(
                "the aggregator must receive the range the read returned",
                fixture.source.returned.single(),
                fixture.aggregator.receivedRanges.single()
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the read receives the resolved endpoints, the display zone and now`() {
        val fixture = fixture()
        try {
            val call = fixture.source.calls.single()
            assertEquals(LocalDate.of(2026, 8, 15), call.startDate)
            assertEquals(today, call.endDate)
            assertEquals(utc, call.displayZone)
            assertEquals(now, call.now)
            assertEquals(today, fixture.viewModel.uiState.value.today)
            assertEquals(utc, fixture.viewModel.uiState.value.displayZone)
        } finally {
            fixture.close()
        }
    }

    // ---------- preset endpoints ----------

    @Test
    fun `last 7 days resolves to today - 6 through today`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)

            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 9, 7), call.startDate)
            assertEquals(today, call.endDate)
            assertEquals(2, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `last 30 days resolves to today - 29 through today`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.Last30Days)

            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 8, 15), call.startDate)
            assertEquals(today, call.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `last 90 days resolves to today - 89 through today`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.Last90Days)

            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 6, 16), call.startDate)
            assertEquals(today, call.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `current month resolves to the first of the month through today`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.CurrentMonth)

            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 9, 1), call.startDate)
            assertEquals(today, call.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a valid custom range is forwarded unchanged`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10))
            )

            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 9, 1), call.startDate)
            assertEquals(LocalDate.of(2026, 9, 10), call.endDate)
        } finally {
            fixture.close()
        }
    }

    // ---------- custom validation ----------

    @Test
    fun `a custom range with start after end is invalid and reads nothing`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1))
            )

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.INVALID_RANGE, state.phase)
            assertEquals(InsightsRangeValidationError.START_AFTER_END, state.validationError)
            assertNull(state.summary)
            assertEquals(1, fixture.source.calls.size) // only the initial load
            assertEquals(1, fixture.aggregator.calls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a custom range ending in the future is invalid and reads nothing`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), today.plusDays(1))
            )

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.INVALID_RANGE, state.phase)
            assertEquals(InsightsRangeValidationError.END_IN_FUTURE, state.validationError)
            assertEquals(1, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    // ---------- query discipline ----------

    @Test
    fun `selecting the already active range performs no read`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.Last30Days)
            fixture.viewModel.selectRange(InsightsRangeSelection.Last30Days)

            assertEquals(1, fixture.source.calls.size)
            assertEquals(1, fixture.aggregator.calls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `reselecting the same preset after midnight re-resolves it and reads once`() {
        val clock = MutableTestClock(now, utc)
        val fixture = fixture(clock = clock)
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)
            val readsAfterSelection = fixture.source.calls.size
            assertEquals(LocalDate.of(2026, 9, 7), fixture.source.calls.last().startDate)

            // same day, same resolved range: still a no-op
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)
            assertEquals(readsAfterSelection, fixture.source.calls.size)

            // the day rolls over, so the same preset resolves to different endpoints
            clock.instant = today.plusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC)
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)

            assertEquals("a changed resolved range must read once", readsAfterSelection + 1, fixture.source.calls.size)
            assertEquals(readsAfterSelection + 1, fixture.aggregator.calls)
            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 9, 8), call.startDate)
            assertEquals(LocalDate.of(2026, 9, 14), call.endDate)
            assertEquals(LocalDate.of(2026, 9, 14), fixture.viewModel.uiState.value.today)
            assertEquals(LocalDate.of(2026, 9, 8), fixture.viewModel.uiState.value.startDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `reselecting the same custom range that became invalid reads nothing`() {
        val clock = MutableTestClock(today.atTime(2, 0).toInstant(ZoneOffset.UTC), utc)
        var zone: ZoneId = utc
        val fixture = fixture(clock = clock, zoneProvider = { zone })
        try {
            fixture.source.result = { call -> rangeWith(call, matched = 1) }
            val selection = InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 13))
            fixture.viewModel.selectRange(selection)
            val readsAfterSelection = fixture.source.calls.size
            assertEquals(InsightsPhase.CONTENT, fixture.viewModel.uiState.value.phase)

            // the same instant is an earlier local date in the new zone, so the custom end is future
            zone = ZoneOffset.ofHours(-12)
            fixture.viewModel.selectRange(selection)

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.INVALID_RANGE, state.phase)
            assertEquals(InsightsRangeValidationError.END_IN_FUTURE, state.validationError)
            assertEquals(readsAfterSelection, fixture.source.calls.size)
            assertEquals(readsAfterSelection, fixture.aggregator.calls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a range change performs exactly one read`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)

            assertEquals(2, fixture.source.calls.size)
            assertEquals(2, fixture.aggregator.calls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting custom endpoints identical to the active selection performs no read`() {
        val fixture = fixture()
        try {
            val selection = InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10))
            fixture.viewModel.selectRange(selection)
            val readsAfterFirst = fixture.source.calls.size

            fixture.viewModel.selectRange(selection)

            assertEquals(readsAfterFirst, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `retry performs exactly one read`() {
        val fixture = fixture()
        try {
            fixture.viewModel.retry()

            assertEquals(2, fixture.source.calls.size)
            assertEquals(2, fixture.aggregator.calls)
        } finally {
            fixture.close()
        }
    }

    // ---------- failure taxonomy ----------

    @Test
    fun `an ordinary read failure keeps the selection and endpoints and offers retry`() {
        val fixture = fixture()
        try {
            val failure = IllegalStateException("synthetic read failure")
            fixture.source.failure = failure

            fixture.viewModel.retry()

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.ERROR, state.phase)
            assertTrue(state.failure is InsightsLoadFailure.ReadFailure)
            assertSame(failure, (state.failure as InsightsLoadFailure.ReadFailure).cause)
            assertEquals(InsightsRangeSelection.Last30Days, state.selection)
            assertEquals(LocalDate.of(2026, 8, 15), state.startDate)
            assertEquals(today, state.endDate)
            assertNull(state.summary)
            assertEquals("the failed load must not aggregate", 1, fixture.aggregator.calls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a contract violation is surfaced as its own failure and never as empty data`() {
        val fixture = fixture()
        try {
            val violation = InsightsContractViolationException("synthetic contract violation")
            fixture.aggregator.failure = violation

            fixture.viewModel.retry()

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.ERROR, state.phase)
            assertTrue(state.failure is InsightsLoadFailure.ContractViolation)
            assertSame(violation, (state.failure as InsightsLoadFailure.ContractViolation).cause)
            assertNull(state.summary)
        } finally {
            fixture.close()
        }
    }

    // ---------- empty semantics (v1.7-B-02-R1 section 1/2) ----------

    @Test
    fun `an empty successful range is Empty rather than Error`() {
        val fixture = fixture()
        try {
            fixture.source.result = fixture.source.emptyDays(3)

            fixture.viewModel.retry()

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.EMPTY, state.phase)
            val summary = requireNotNull(state.summary)
            assertEquals(0, summary.recordedIntakeCount)
            assertEquals(0, summary.unrecordedOccurrenceCount)
            assertNull(state.failure)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `an unrecorded-only range is content because it carries historical facts`() {
        val fixture = fixture()
        try {
            fixture.source.result = { call -> rangeWith(call, unrecorded = 4) }

            fixture.viewModel.retry()

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.CONTENT, state.phase)
            val summary = requireNotNull(state.summary)
            assertEquals(0, summary.recordedIntakeCount)
            assertEquals(4, summary.unrecordedOccurrenceCount)
            assertNull(state.failure)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `an unmatched-only range is content`() {
        val fixture = fixture()
        try {
            fixture.source.result = { call -> rangeWith(call, unmatched = 2) }

            fixture.viewModel.retry()

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.CONTENT, state.phase)
            val summary = requireNotNull(state.summary)
            assertEquals(2, summary.recordedIntakeCount)
            assertEquals(2, summary.unmatchedActualIntakeCount)
            assertEquals(0, summary.unrecordedOccurrenceCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a mixed recorded and unrecorded range is content`() {
        val fixture = fixture()
        try {
            fixture.source.result = { call -> rangeWith(call, matched = 1, unrecorded = 2) }

            fixture.viewModel.retry()

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.CONTENT, state.phase)
            val summary = requireNotNull(state.summary)
            assertEquals(1, summary.recordedIntakeCount)
            assertEquals(2, summary.unrecordedOccurrenceCount)
        } finally {
            fixture.close()
        }
    }

    // ---------- stale responses ----------

    @Test
    fun `a stale success that ignores cancellation cannot replace the newer selection`() {
        val stale = CompletableDeferred<Unit>()
        val fixture = fixture(ignoreCancellation = true, gate = stale, gateOnlyForCall = 1)
        try {
            // the two requests answer with clearly different facts, so "which summary survived"
            // is decidable and not just "which selection survived"
            fixture.source.result = { call ->
                if (call.startDate == today.minusDays(29)) {
                    rangeWith(call, matched = 1)
                } else {
                    rangeWith(call, matched = 1, unmatched = 1, unrecorded = 5)
                }
            }

            // the gated initial load is the stale candidate; the user then picks another range
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)

            assertEquals(2, fixture.source.calls.size)
            assertEquals(InsightsRangeSelection.Last7Days, fixture.viewModel.uiState.value.selection)

            stale.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsRangeSelection.Last7Days, state.selection)
            assertEquals(LocalDate.of(2026, 9, 7), state.startDate)
            assertEquals(LocalDate.of(2026, 9, 13), state.endDate)
            assertEquals(InsightsPhase.CONTENT, state.phase)
            val summary = requireNotNull(state.summary)
            assertEquals("the surviving summary must be the newer request's", 2, summary.recordedIntakeCount)
            assertEquals(5, summary.unrecordedOccurrenceCount)
            assertNull(state.failure)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a stale failure cannot turn the newer success into an error`() {
        val stale = CompletableDeferred<Unit>()
        val fixture = fixture(ignoreCancellation = true, gate = stale, gateOnlyForCall = 1)
        try {
            // call #1 (the gated initial load) will fail; the superseding load #2 succeeds
            fixture.source.failureForCall = 1
            fixture.source.result = { call -> rangeWith(call, matched = 1, unmatched = 1) }

            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)

            assertEquals(2, fixture.source.calls.size)
            val afterNewerLoad = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.CONTENT, afterNewerLoad.phase)
            assertEquals(2, afterNewerLoad.summary!!.recordedIntakeCount)
            assertNull(afterNewerLoad.failure)

            // now the stale request finally fails and must not poison the current state
            stale.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsRangeSelection.Last7Days, state.selection)
            assertEquals(LocalDate.of(2026, 9, 7), state.startDate)
            assertEquals(InsightsPhase.CONTENT, state.phase)
            assertEquals("the newer summary must survive the stale failure", 2, state.summary!!.recordedIntakeCount)
            assertNull(state.failure)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a stale contract violation cannot replace the newer content`() {
        val stale = CompletableDeferred<Unit>()
        val fixture = fixture(ignoreCancellation = true, gate = stale, gateOnlyForCall = 1)
        try {
            // call #1 (the gated initial load) violates the history contract once it finally runs
            fixture.source.typedFailureForCall =
                1 to InsightsContractViolationException("synthetic stale contract violation")
            fixture.source.result = { call -> rangeWith(call, matched = 1, unrecorded = 3) }

            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)

            val afterNewerLoad = fixture.viewModel.uiState.value
            assertEquals(2, fixture.source.calls.size)
            assertEquals(InsightsPhase.CONTENT, afterNewerLoad.phase)
            assertEquals(1, afterNewerLoad.summary!!.recordedIntakeCount)

            stale.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsRangeSelection.Last7Days, state.selection)
            assertEquals(LocalDate.of(2026, 9, 7), state.startDate)
            assertEquals(InsightsPhase.CONTENT, state.phase)
            assertEquals(3, state.summary!!.unrecordedOccurrenceCount)
            assertNull(state.failure)
        } finally {
            fixture.close()
        }
    }

    // ---------- surface / foreground refresh ----------

    @Test
    fun `the first surface entry does not add a second read`() {
        val fixture = fixture()
        try {
            fixture.viewModel.onSurfaceShown()

            assertEquals(1, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a tab return refreshes the current selection exactly once`() {
        val fixture = fixture()
        try {
            fixture.viewModel.onSurfaceShown() // first entry
            fixture.viewModel.onSurfaceShown() // tab return
            fixture.viewModel.onSurfaceShown() // second tab return

            assertEquals(3, fixture.source.calls.size)
            assertEquals(3, fixture.aggregator.calls)
            assertEquals(InsightsRangeSelection.Last30Days, fixture.viewModel.uiState.value.selection)
            assertEquals(today, fixture.source.calls.last().endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a foreground return refreshes exactly once`() {
        val fixture = fixture()
        try {
            fixture.viewModel.onSurfaceShown()
            fixture.viewModel.onAppForegrounded()

            assertEquals(2, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    // ---------- coalesced pending refresh ----------

    @Test
    fun `refresh requests during a load coalesce into exactly one follow-up load`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate)
        try {
            // the initial load is in flight; three refresh requests arrive
            fixture.viewModel.onSurfaceShown()
            fixture.viewModel.onSurfaceShown()
            fixture.viewModel.onAppForegrounded()

            assertEquals(1, fixture.source.calls.size)

            // the source changes while the first load is still running, then it completes
            fixture.source.result = { call ->
                HistoricalRange(
                    startDate = call.startDate,
                    endDate = call.endDate,
                    days = listOf(
                        testDay(date = call.endDate, entries = listOf(matchedEntry(displayDate = call.endDate)))
                    )
                )
            }
            gate.complete(Unit)

            assertEquals("original read plus exactly one coalesced follow-up", 2, fixture.source.calls.size)
            assertEquals(2, fixture.aggregator.calls)
            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.CONTENT, state.phase)
            assertEquals(1, state.summary!!.recordedIntakeCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a selection change during a load supersedes it instead of waiting`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate)
        try {
            // the initial (gated) load is superseded by an explicit selection change
            fixture.viewModel.selectRange(InsightsRangeSelection.Last90Days)

            assertEquals(2, fixture.source.calls.size)
            assertEquals(LocalDate.of(2026, 6, 16), fixture.source.calls.last().startDate)
            assertEquals(InsightsRangeSelection.Last90Days, fixture.viewModel.uiState.value.selection)

            gate.complete(Unit)

            assertEquals("the superseded load must not add a read", 2, fixture.source.calls.size)
            assertEquals(InsightsRangeSelection.Last90Days, fixture.viewModel.uiState.value.selection)
        } finally {
            fixture.close()
        }
    }

    // ---------- pending refresh ownership (v1.7-B-02-R1 sections 3-8) ----------

    @Test
    fun `an explicit selection change supersedes a pending refresh and still reads exactly twice`() {
        val gate = CompletableDeferred<Unit>()
        val handle = SavedStateHandle()
        val fixture = fixture(gate = gate, handle = handle)
        try {
            // A (Last30Days) is in flight and two refresh intents arrive for it
            fixture.viewModel.onSurfaceShown()
            fixture.viewModel.onAppForegrounded()
            fixture.viewModel.onAppForegrounded()
            assertEquals(1, fixture.source.calls.size)

            // the two requests answer with clearly different facts
            fixture.source.result = { call ->
                if (call.startDate == today.minusDays(29)) {
                    rangeWith(call, matched = 1)
                } else {
                    rangeWith(call, matched = 1, unmatched = 1, unrecorded = 4)
                }
            }

            // the user then asks for B: it must win over the refresh intent queued for A
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)
            assertEquals(2, fixture.source.calls.size)

            gate.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertEquals("A and B only; no replay of the superseded refresh", 2, fixture.source.calls.size)
            assertEquals("the superseded read never reaches the aggregator", 1, fixture.aggregator.calls)
            assertEquals(InsightsRangeSelection.Last7Days, state.selection)
            assertEquals(LocalDate.of(2026, 9, 7), state.startDate)
            assertEquals(LocalDate.of(2026, 9, 13), state.endDate)
            assertEquals(InsightsPhase.CONTENT, state.phase)
            assertEquals("the summary must belong to B", 2, state.summary!!.recordedIntakeCount)
            assertEquals(4, state.summary!!.unrecordedOccurrenceCount)
            assertNull(state.failure)
            assertEquals(
                "live state and SavedState must agree",
                "LAST_7_DAYS",
                handle.get<String>("insights.selectionType")
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a refresh that arrives after a new selection is preserved`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate)
        try {
            // A (Last30Days) is in flight; B supersedes it, and *then* a foreground refresh arrives
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)
            fixture.viewModel.onAppForegrounded()

            assertEquals(2, fixture.source.calls.size)

            gate.complete(Unit)

            assertEquals("A, B and one refresh of B", 3, fixture.source.calls.size)
            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsRangeSelection.Last7Days, state.selection)
            assertEquals(LocalDate.of(2026, 9, 7), state.startDate)
            assertEquals(LocalDate.of(2026, 9, 13), state.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `repeated selection changes leave only the newest selection authoritative`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate, gateOnlyForCall = 1)
        try {
            fixture.source.result = { call ->
                when (call.startDate) {
                    today.minusDays(29) -> rangeWith(call, matched = 1)
                    today.minusDays(6) -> rangeWith(call, matched = 1, unmatched = 1)
                    else -> rangeWith(call, matched = 1, unmatched = 2)
                }
            }

            // a refresh intent is queued for A, then B and C are selected in turn
            fixture.viewModel.onAppForegrounded()
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)
            fixture.viewModel.selectRange(InsightsRangeSelection.Last90Days)

            assertEquals("A, B and C only", 3, fixture.source.calls.size)

            // the superseded A finally answers and must not revive itself
            gate.complete(Unit)

            val state = fixture.viewModel.uiState.value
            assertEquals(3, fixture.source.calls.size)
            assertEquals(InsightsRangeSelection.Last90Days, state.selection)
            assertEquals(LocalDate.of(2026, 6, 16), state.startDate)
            assertEquals(LocalDate.of(2026, 9, 13), state.endDate)
            assertEquals(InsightsPhase.CONTENT, state.phase)
            assertEquals("only C may describe the state", 3, state.summary!!.recordedIntakeCount)
            assertNull(state.failure)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a pending refresh still runs after an ordinary read failure`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate)
        try {
            // call #1 fails once released; the coalesced follow-up succeeds
            fixture.source.failureForCall = 1
            fixture.source.result = { call -> rangeWith(call, matched = 1, unrecorded = 2) }

            fixture.viewModel.onAppForegrounded()
            gate.complete(Unit)

            assertEquals("the failed load plus exactly one follow-up", 2, fixture.source.calls.size)
            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.CONTENT, state.phase)
            assertEquals(1, state.summary!!.recordedIntakeCount)
            assertNull("the follow-up must clear the failure", state.failure)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a pending refresh still runs after a contract violation`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate)
        try {
            fixture.source.typedFailureForCall =
                1 to InsightsContractViolationException("synthetic contract violation")
            fixture.source.result = { call -> rangeWith(call, matched = 2) }

            fixture.viewModel.onSurfaceShown()
            fixture.viewModel.onAppForegrounded()
            gate.complete(Unit)

            assertEquals(2, fixture.source.calls.size)
            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.CONTENT, state.phase)
            assertEquals(2, state.summary!!.recordedIntakeCount)
            assertNull("the follow-up must clear the contract violation", state.failure)
        } finally {
            fixture.close()
        }
    }

    // ---------- B-04 carry-over closures (v1.7-B-04 §31) ----------

    @Test
    fun `a superseding selection that is invalid leaves no refresh latch behind`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate)
        try {
            fixture.source.result = { call -> rangeWith(call, matched = 1) }

            // A (Last30Days) is in flight and a refresh intent is queued for it
            fixture.viewModel.onAppForegrounded()
            assertEquals(1, fixture.source.calls.size)

            // the user reselects a custom range that is invalid: no coroutine, no finally, so the
            // queued intent must be cleared by the supersede itself
            fixture.viewModel.selectRange(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1))
            )

            val invalid = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.INVALID_RANGE, invalid.phase)
            assertEquals(InsightsRangeValidationError.START_AFTER_END, invalid.validationError)
            assertEquals("the invalid selection must not read", 1, fixture.source.calls.size)

            gate.complete(Unit)
            assertEquals("the superseded load must not read again", 1, fixture.source.calls.size)

            // and a later refresh of the now-invalid selection still reads nothing
            fixture.viewModel.onAppForegrounded()
            assertEquals("an invalid selection never reads", 1, fixture.source.calls.size)
            assertEquals("the superseded read was cancelled before aggregating", 0, fixture.aggregator.calls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a preserved refresh publishes the follow-up summary of the new selection`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate)
        try {
            // B answers with a distinct signature; the follow-up answers with another one
            var answers = 0
            fixture.source.result = { call ->
                answers += 1
                if (answers == 1) rangeWith(call, matched = 1) else rangeWith(call, matched = 2, unrecorded = 5)
            }

            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)
            fixture.viewModel.onAppForegrounded()
            gate.complete(Unit)

            assertEquals("A, B and one refresh of B", 3, fixture.source.calls.size)
            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsRangeSelection.Last7Days, state.selection)
            val summary = requireNotNull(state.summary)
            assertEquals("the follow-up summary must win", 2, summary.recordedIntakeCount)
            assertEquals(5, summary.unrecordedOccurrenceCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a new custom selection performs exactly one read`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10))
            )

            assertEquals("initial load plus one custom read", 2, fixture.source.calls.size)
            assertEquals(2, fixture.aggregator.calls)
            assertEquals(LocalDate.of(2026, 9, 1), fixture.source.calls.last().startDate)
            assertEquals(LocalDate.of(2026, 9, 10), fixture.source.calls.last().endDate)

            // and reselecting the identical custom endpoints stays a no-op
            fixture.viewModel.selectRange(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10))
            )
            assertEquals(2, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    // ---------- request snapshot (v1.7-B-02-R1 sections 11/12) ----------

    @Test
    fun `one request captures exactly one zone and instant snapshot`() {
        val clock = SteppingTestClock(listOf(now, now.plusSeconds(86_400L)))
        val fixture = fixture(clock = clock)
        try {
            assertEquals("construction takes one snapshot", 1, clock.reads)
            val initialCall = fixture.source.calls.single()
            assertEquals(now, initialCall.now)
            assertEquals(LocalDate.of(2026, 9, 13), initialCall.endDate)
            assertEquals(LocalDate.of(2026, 9, 13), fixture.viewModel.uiState.value.today)

            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)

            assertEquals("a request must read the clock exactly once", 2, clock.reads)
            val call = fixture.source.calls.last()
            assertEquals(
                "the forwarded now must come from the same snapshot as the endpoints",
                now.plusSeconds(86_400L),
                call.now
            )
            assertEquals(LocalDate.of(2026, 9, 8), call.startDate)
            assertEquals(LocalDate.of(2026, 9, 14), call.endDate)
            assertEquals(LocalDate.of(2026, 9, 14), fixture.viewModel.uiState.value.today)
            assertEquals(LocalDate.of(2026, 9, 8), fixture.viewModel.uiState.value.startDate)
        } finally {
            fixture.close()
        }
    }

    // ---------- rollover ----------

    @Test
    fun `a relative preset rolls with the day`() {
        val clock = MutableTestClock(now, utc)
        val fixture = fixture(clock = clock)
        try {
            fixture.viewModel.onSurfaceShown()
            assertEquals(LocalDate.of(2026, 9, 7), resolvedStartFor(fixture, InsightsRangeSelection.Last7Days))

            clock.instant = today.plusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC)
            fixture.viewModel.onAppForegrounded()

            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 9, 8), call.startDate)
            assertEquals(LocalDate.of(2026, 9, 14), call.endDate)
            assertEquals(LocalDate.of(2026, 9, 14), fixture.viewModel.uiState.value.today)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `current month rolls into the new month`() {
        val clock = MutableTestClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC), utc)
        val fixture = fixture(clock = clock)
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.CurrentMonth)
            assertEquals(LocalDate.of(2026, 9, 1), fixture.source.calls.last().startDate)

            clock.instant = LocalDate.of(2026, 10, 1).atTime(9, 0).toInstant(ZoneOffset.UTC)
            fixture.viewModel.onAppForegrounded()

            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 10, 1), call.startDate)
            assertEquals(LocalDate.of(2026, 10, 1), call.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `custom endpoints stay fixed across a rollover`() {
        val clock = MutableTestClock(now, utc)
        val fixture = fixture(clock = clock)
        try {
            fixture.viewModel.selectRange(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10))
            )

            clock.instant = today.plusDays(3).atTime(12, 0).toInstant(ZoneOffset.UTC)
            fixture.viewModel.onAppForegrounded()

            val call = fixture.source.calls.last()
            assertEquals(LocalDate.of(2026, 9, 1), call.startDate)
            assertEquals(LocalDate.of(2026, 9, 10), call.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a timezone change re-resolves today before reading`() {
        val kiritimati: ZoneId = ZoneId.of("Pacific/Kiritimati")
        val clock = MutableTestClock(now, utc)
        var zone: ZoneId = utc
        val fixture = fixture(clock = clock, zoneProvider = { zone })
        try {
            fixture.viewModel.selectRange(InsightsRangeSelection.Last7Days)
            assertEquals(LocalDate.of(2026, 9, 13), fixture.viewModel.uiState.value.today)

            // same instant, but the display zone moved to +14, so "today" is the next date
            zone = kiritimati
            clock.zoneId = kiritimati
            fixture.viewModel.onAppForegrounded()

            val call = fixture.source.calls.last()
            assertEquals(kiritimati, call.displayZone)
            assertEquals(LocalDate.of(2026, 9, 14), fixture.viewModel.uiState.value.today)
            assertEquals(LocalDate.of(2026, 9, 8), call.startDate)
            assertEquals(LocalDate.of(2026, 9, 14), call.endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a live timezone change that makes a custom range invalid reads nothing`() {
        val clock = MutableTestClock(today.atTime(2, 0).toInstant(ZoneOffset.UTC), utc)
        var zone: ZoneId = utc
        val fixture = fixture(clock = clock, zoneProvider = { zone })
        try {
            fixture.source.result = { call -> rangeWith(call, matched = 1) }
            val selection = InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 13))
            fixture.viewModel.selectRange(selection)
            val reads = fixture.source.calls.size
            val aggregates = fixture.aggregator.calls
            assertEquals(InsightsPhase.CONTENT, fixture.viewModel.uiState.value.phase)

            // same instant, but the display zone moved to -12, so today is the previous date and the
            // fixed custom end now lies in the future
            zone = ZoneOffset.ofHours(-12)
            fixture.viewModel.onAppForegrounded()

            val state = fixture.viewModel.uiState.value
            assertEquals(InsightsPhase.INVALID_RANGE, state.phase)
            assertEquals(InsightsRangeValidationError.END_IN_FUTURE, state.validationError)
            assertNull(state.summary)
            assertEquals("an invalidated range must not read again", reads, fixture.source.calls.size)
            assertEquals("an invalidated range must not aggregate again", aggregates, fixture.aggregator.calls)
        } finally {
            fixture.close()
        }
    }

    // ---------- saved state ----------

    @Test
    fun `a restored relative preset is re-resolved against today`() {
        val handle = SavedStateHandle(mapOf("insights.selectionType" to "LAST_7_DAYS"))

        val fixture = fixture(handle = handle)

        try {
            assertEquals(InsightsRangeSelection.Last7Days, fixture.viewModel.uiState.value.selection)
            assertEquals(LocalDate.of(2026, 9, 7), fixture.source.calls.single().startDate)
            assertEquals(today, fixture.source.calls.single().endDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a restored custom range is restored and validated`() {
        val handle = SavedStateHandle(
            mapOf(
                "insights.selectionType" to "CUSTOM",
                "insights.customStartDate" to "2026-09-01",
                "insights.customEndDate" to "2026-09-10"
            )
        )

        val fixture = fixture(handle = handle)

        try {
            assertEquals(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)),
                fixture.viewModel.uiState.value.selection
            )
            assertEquals(LocalDate.of(2026, 9, 1), fixture.source.calls.single().startDate)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a restored custom range that is no longer valid reads nothing`() {
        val handle = SavedStateHandle(
            mapOf(
                "insights.selectionType" to "CUSTOM",
                "insights.customStartDate" to "2026-09-01",
                "insights.customEndDate" to "2026-09-20"
            )
        )

        val fixture = fixture(handle = handle)

        try {
            assertEquals(0, fixture.source.calls.size)
            assertEquals(0, fixture.aggregator.calls)
            assertEquals(InsightsPhase.INVALID_RANGE, fixture.viewModel.uiState.value.phase)
            assertEquals(
                InsightsRangeValidationError.END_IN_FUTURE,
                fixture.viewModel.uiState.value.validationError
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `malformed saved state falls back to the default selection`() {
        val handle = SavedStateHandle(
            mapOf(
                "insights.selectionType" to "CUSTOM",
                "insights.customStartDate" to "not-a-date",
                "insights.customEndDate" to "2026-09-10"
            )
        )

        val fixture = fixture(handle = handle)

        try {
            assertEquals(InsightsRangeSelection.Last30Days, fixture.viewModel.uiState.value.selection)
            assertEquals(1, fixture.source.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selection changes are persisted for restoration`() {
        val handle = SavedStateHandle()
        val first = fixture(handle = handle)
        try {
            first.viewModel.selectRange(InsightsRangeSelection.Last90Days)
            assertEquals("LAST_90_DAYS", handle.get<String>("insights.selectionType"))

            first.viewModel.selectRange(
                InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))
            )
            assertEquals("CUSTOM", handle.get<String>("insights.selectionType"))
            assertEquals("2026-09-01", handle.get<String>("insights.customStartDate"))
            assertEquals("2026-09-05", handle.get<String>("insights.customEndDate"))
        } finally {
            first.close()
        }
    }

    @Test
    fun `no summary or range is ever persisted`() {
        val handle = SavedStateHandle()
        val fixture = fixture(handle = handle)
        try {
            fixture.viewModel.retry()

            val keys = handle.keys()
            assertTrue("saved keys: $keys", keys.all { it.startsWith("insights.selectionType") || it.startsWith("insights.custom") })
            assertNull(handle.get<MedicationInsightsSummary>("summary"))
        } finally {
            fixture.close()
        }
    }

    // ---------- fixture ----------

    private fun resolvedStartFor(fixture: Fixture, selection: InsightsRangeSelection): LocalDate {
        fixture.viewModel.selectRange(selection)
        return fixture.source.calls.last().startDate
    }

    /**
     * Range that carries exactly the requested facts on the call's last day.
     *
     * Every entry gets its own event/occurrence identity, so a range with several entries cannot
     * trip the frozen B-01 duplicate contract, and the resulting summary is a distinct signature
     * that a test can recognise (v1.7-B-02-R1 sections 6/7).
     */
    private fun rangeWith(
        call: RecordingRangeSource.Call,
        matched: Int = 0,
        unmatched: Int = 0,
        unrecorded: Int = 0,
        date: LocalDate = call.endDate
    ): HistoricalRange {
        val entries = buildList {
            repeat(matched) { index ->
                val slot = 100L + index
                add(
                    matchedEntry(
                        occurrence = testOccurrence(slotId = slot, date = date, time = LocalTime.of(8, 0)),
                        event = testEvent(
                            id = slot,
                            slotId = UUID(1L, slot),
                            localDate = date,
                            occurredAt = date.atTime(8, 5).toInstant(ZoneOffset.UTC)
                        ),
                        displayDate = date
                    )
                )
            }
            repeat(unmatched) { index ->
                add(
                    unmatchedEntry(
                        event = testEvent(
                            id = 200L + index,
                            occurredAt = date.atTime(9, 5).toInstant(ZoneOffset.UTC),
                            localDate = null,
                            zoneId = null
                        ),
                        displayDate = date,
                        provenance = HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED
                    )
                )
            }
            repeat(unrecorded) { index ->
                add(
                    unrecordedEntry(
                        occurrence = testOccurrence(slotId = 300L + index, date = date, time = LocalTime.of(16, 0)),
                        displayDate = date
                    )
                )
            }
        }
        return HistoricalRange(
            startDate = call.startDate,
            endDate = call.endDate,
            days = listOf(testDay(date = date, entries = entries))
        )
    }

    private fun fixture(
        clock: Clock = MutableTestClock(now, utc),
        zoneProvider: () -> ZoneId = { clock.zone },
        handle: SavedStateHandle? = null,
        ignoreCancellation: Boolean = false,
        gate: CompletableDeferred<Unit>? = null,
        gateOnlyForCall: Int? = null
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val source = RecordingRangeSource(ignoreCancellation).apply {
            this.gate = gate
            this.gateOnlyForCall = gateOnlyForCall
        }
        val aggregator = RecordingAggregator()
        val viewModel = InsightsViewModel(
            rangeSource = source,
            aggregator = aggregator,
            clock = clock,
            displayZone = zoneProvider,
            savedStateHandle = handle,
            operationScope = scope
        )
        return Fixture(viewModel, scope, source, aggregator)
    }

    private data class Fixture(
        val viewModel: InsightsViewModel,
        val scope: CoroutineScope,
        val source: RecordingRangeSource,
        val aggregator: RecordingAggregator
    ) {
        fun close() = scope.cancel()
    }
}
