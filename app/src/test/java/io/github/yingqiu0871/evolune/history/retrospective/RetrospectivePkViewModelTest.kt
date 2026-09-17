package io.github.yingqiu0871.evolune.history.retrospective

import io.github.yingqiu0871.evolune.experience.HistoricalProjection
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.history.insights.MutableTestClock
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkContractViolationException
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkLimitation
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkResult
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkUnavailableReason
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * V17-C-04 §4/§6/§8/§14 — ViewModel orchestration: single capture, body-weight gate, the frozen
 * read-count matrix (0 / 1 / 3), policy sharing, race behavior, coalesced refresh and typed
 * failure/ unavailable mapping.
 *
 * All count/capture assertions come from recording fakes at the three seam boundaries.
 */
class RetrospectivePkViewModelTest {

    private val utc = ZoneOffset.UTC

    private class Fixture(
        val viewModel: RetrospectivePkViewModel,
        val scope: CoroutineScope,
        val pkSource: RecordingRetrospectivePkSource,
        val allSource: RecordingAllAvailableHistorySource,
        val rangeSource: RecordingHistoryRangeSource,
        val settings: FakeSettingsStore,
        val clock: MutableTestClock
    ) {
        fun close() = scope.cancel()
    }

    private fun fixture(
        weight: Double = 55.0,
        available: RetrospectivePkResult = c04AvailableResult(c04Window()),
        projection: HistoricalProjection = HistoricalProjection(entries = emptyList()),
        range: io.github.yingqiu0871.evolune.experience.HistoricalRange = c04EmptyRange(c04Window()),
        pkFailure: Throwable? = null,
        allFailure: Throwable? = null,
        rangeFailure: Throwable? = null,
        gate: CompletableDeferred<Unit>? = null
    ): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val clock = MutableTestClock(C04_NOW, utc)
        val pkSource = RecordingRetrospectivePkSource(available).apply {
            failure = pkFailure
            this.gate = gate
        }
        val allSource = RecordingAllAvailableHistorySource(c04AllAvailable(projection)).apply {
            failure = allFailure
        }
        val rangeSource = RecordingHistoryRangeSource(range).apply {
            failure = rangeFailure
        }
        val settings = FakeSettingsStore(weight)
        val viewModel = RetrospectivePkViewModel(
            retrospectivePkSource = pkSource,
            allAvailableHistorySource = allSource,
            historyRangeSource = rangeSource,
            settingsStore = settings,
            clock = clock,
            displayZone = { utc },
            operationScope = scope
        )
        return Fixture(viewModel, scope, pkSource, allSource, rangeSource, settings, clock)
    }

    // ---------- initial load & capture ----------

    @Test
    fun `initial content load performs exactly three history reads in the frozen order`() {
        val fixture = fixture()
        try {
            assertEquals(1, fixture.pkSource.requests.size)
            assertEquals(1, fixture.allSource.calls.size)
            assertEquals(1, fixture.rangeSource.calls.size)
            assertEquals(RetrospectivePhase.CONTENT, fixture.viewModel.uiState.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the once-per-load capture is shared by all three reads`() {
        val fixture = fixture()
        try {
            val window = c04Window()
            val request = fixture.pkSource.requests.single()
            val allCall = fixture.allSource.calls.single()
            val rangeCall = fixture.rangeSource.calls.single()

            assertEquals(window.startInclusive, request.visibleWindow.startInclusive)
            assertEquals(window.endInclusive, request.visibleWindow.endInclusive)
            assertEquals(utc, request.displayZone)
            assertEquals(55.0, request.bodyWeightKG, 0.0)
            assertEquals(window.endInclusive, request.capturedAt)
            assertNull("cursor must stay null", request.cursor)

            assertEquals(request.capturedAt, allCall.upperBoundInclusive)
            assertEquals(request.displayZone, allCall.displayZone)
            assertSame("Read1 and Read2 must share the captured policy value", request.policy, allCall.policy)

            assertEquals(window.startInclusive.atZone(utc).toLocalDate().minusDays(1), rangeCall.startDate)
            assertEquals(window.endInclusive.atZone(utc).toLocalDate().plusDays(1), rangeCall.endDate)
            assertEquals(utc, rangeCall.displayZone)
            assertEquals(request.capturedAt, rangeCall.now)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the default window is exactly 168 hours, millisecond aligned and has no future segment`() {
        val fixture = fixture()
        try {
            val request = fixture.pkSource.requests.single()
            val window = request.visibleWindow
            assertEquals(Duration.ofDays(7), window.duration)
            assertEquals(window.endInclusive, request.capturedAt)
            assertEquals(
                window.endInclusive,
                Instant.ofEpochMilli(window.endInclusive.toEpochMilli())
            )
            assertEquals(
                window.startInclusive,
                Instant.ofEpochMilli(window.startInclusive.toEpochMilli())
            )
            assertFalse(window.startInclusive.isAfter(window.endInclusive))
            assertEquals(RetrospectivePkRange.LAST_7_DAYS, fixture.viewModel.uiState.value.selectedRange)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selecting another range reloads exactly once with the selected window`() {
        val fixture = fixture()
        try {
            assertEquals(1, fixture.pkSource.requests.size)
            assertEquals(1, fixture.allSource.calls.size)
            assertEquals(1, fixture.rangeSource.calls.size)

            fixture.viewModel.selectRange(RetrospectivePkRange.LAST_30_DAYS)

            assertEquals(2, fixture.pkSource.requests.size)
            assertEquals(2, fixture.allSource.calls.size)
            assertEquals(2, fixture.rangeSource.calls.size)
            val window = fixture.pkSource.requests.last().visibleWindow
            assertEquals(Duration.ofDays(30), window.duration)
            assertEquals(c04Window(days = 30).startInclusive, window.startInclusive)
            assertEquals(c04Window(days = 30).endInclusive, window.endInclusive)
            assertEquals(RetrospectivePkRange.LAST_30_DAYS, fixture.viewModel.uiState.value.selectedRange)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `re-selecting the current range performs no extra load`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(RetrospectivePkRange.LAST_7_DAYS)
            assertEquals(1, fixture.pkSource.requests.size)
            assertEquals(1, fixture.allSource.calls.size)
            assertEquals(1, fixture.rangeSource.calls.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the selected range survives a retry load`() {
        val fixture = fixture()
        try {
            fixture.viewModel.selectRange(RetrospectivePkRange.LAST_90_DAYS)
            assertEquals(2, fixture.pkSource.requests.size)

            fixture.viewModel.retry()

            assertEquals(3, fixture.pkSource.requests.size)
            assertEquals(
                Duration.ofDays(90),
                fixture.pkSource.requests.last().visibleWindow.duration
            )
            assertEquals(RetrospectivePkRange.LAST_90_DAYS, fixture.viewModel.uiState.value.selectedRange)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the body weight is read exactly once per load`() {
        val fixture = fixture()
        try {
            assertEquals(1, fixture.settings.readCount)
        } finally {
            fixture.close()
        }
    }

    // ---------- body-weight gate (§6.3) ----------

    @Test
    fun `an invalid body weight performs zero history reads and reports the defensive error`() {
        listOf(0.0, -1.0, 300.000_1, Double.NaN).forEach { invalid ->
            val fixture = fixture(weight = invalid)
            try {
                assertTrue("weight $invalid must be rejected", fixture.pkSource.requests.isEmpty())
                assertTrue(fixture.allSource.calls.isEmpty())
                assertTrue(fixture.rangeSource.calls.isEmpty())

                val state = fixture.viewModel.uiState.value
                assertEquals(RetrospectivePhase.ERROR, state.phase)
                assertEquals(RetrospectiveLoadFailure.InvalidBodyWeight, state.failure)
                assertNull(state.result)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `a corrected body weight recovers through retry`() {
        val fixture = fixture(weight = 0.0)
        try {
            assertEquals(RetrospectivePhase.ERROR, fixture.viewModel.uiState.value.phase)

            kotlinx.coroutines.runBlocking { fixture.settings.updateBodyWeight(60.0) }
            fixture.viewModel.retry()

            assertEquals(RetrospectivePhase.CONTENT, fixture.viewModel.uiState.value.phase)
            assertEquals(1, fixture.pkSource.requests.size)
            assertEquals(1, fixture.allSource.calls.size)
            assertEquals(1, fixture.rangeSource.calls.size)
            assertEquals(60.0, fixture.pkSource.requests.single().bodyWeightKG, 0.0)
        } finally {
            fixture.close()
        }
    }

    // ---------- unavailable / failure matrix ----------

    @Test
    fun `Read1 Unavailable stops the load with exactly one history read`() {
        val window = c04Window()
        val fixture = fixture(
            available = c04UnavailableResult(
                window = window,
                reason = RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE,
                limitations = setOf(RetrospectivePkLimitation.EXCLUDED_RECORDED_INTAKES)
            )
        )
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(RetrospectivePhase.UNAVAILABLE, state.phase)
            assertEquals(1, fixture.pkSource.requests.size)
            assertTrue("no marker read after Unavailable", fixture.allSource.calls.isEmpty())
            assertTrue(fixture.rangeSource.calls.isEmpty())

            val result = state.result as RetrospectivePkResult.Unavailable
            assertEquals(
                RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE,
                result.reason
            )
            assertEquals(
                setOf(RetrospectivePkLimitation.EXCLUDED_RECORDED_INTAKES),
                result.limitations
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a Read1 read failure yields ERROR with no marker reads`() {
        val fixture = fixture(pkFailure = IllegalStateException("read failed"))
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(RetrospectivePhase.ERROR, state.phase)
            assertTrue(state.failure is RetrospectiveLoadFailure.ReadFailure)
            assertEquals(1, fixture.pkSource.requests.size)
            assertTrue(fixture.allSource.calls.isEmpty())
            assertTrue(fixture.rangeSource.calls.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a Read1 contract violation stays typed and stops the load`() {
        val fixture = fixture(
            pkFailure = RetrospectivePkContractViolationException("frozen contract")
        )
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(RetrospectivePhase.ERROR, state.phase)
            assertTrue(state.failure is RetrospectiveLoadFailure.ContractViolation)
            assertTrue(fixture.allSource.calls.isEmpty())
            assertTrue(fixture.rangeSource.calls.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a Read2 failure yields ERROR and never runs Read3`() {
        val fixture = fixture(allFailure = IllegalStateException("event read failed"))
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(RetrospectivePhase.ERROR, state.phase)
            assertEquals(1, fixture.pkSource.requests.size)
            assertEquals(1, fixture.allSource.calls.size)
            assertTrue("no partial marker read after a Read2 failure", fixture.rangeSource.calls.isEmpty())
            assertNull("no partial CONTENT is published", state.result)
            assertTrue(state.markers.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a Read3 failure yields ERROR with no partial content`() {
        val fixture = fixture(rangeFailure = IllegalStateException("range read failed"))
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(RetrospectivePhase.ERROR, state.phase)
            assertEquals(1, fixture.pkSource.requests.size)
            assertEquals(1, fixture.allSource.calls.size)
            assertEquals(1, fixture.rangeSource.calls.size)
            assertNull(state.result)
            assertTrue(state.markers.isEmpty())
        } finally {
            fixture.close()
        }
    }

    // ---------- refresh / retry / stale serialization ----------

    @Test
    fun `retry starts a completely new load with a fresh rolling capture`() {
        val fixture = fixture()
        try {
            fixture.clock.instant = C04_NOW.plus(Duration.ofHours(1))
            fixture.viewModel.retry()

            assertEquals(2, fixture.pkSource.requests.size)
            val second = fixture.pkSource.requests.last()
            assertEquals(C04_NOW.plus(Duration.ofHours(1)), second.capturedAt)
            assertEquals(second.visibleWindow.endInclusive, second.capturedAt)
            assertEquals(RetrospectivePhase.CONTENT, fixture.viewModel.uiState.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `retry while a load is running is coalesced into exactly one follow-up`() {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(gate = gate)
        try {
            // The initial load is gated inside Read1.
            assertEquals(1, fixture.pkSource.requests.size)

            fixture.viewModel.retry()
            fixture.viewModel.retry()
            // Still gated: no concurrent generation was started.
            assertEquals(1, fixture.pkSource.requests.size)

            gate.complete(Unit)

            assertEquals("exactly one follow-up load", 2, fixture.pkSource.requests.size)
            assertEquals(2, fixture.allSource.calls.size)
            assertEquals(2, fixture.rangeSource.calls.size)
            assertEquals(RetrospectivePhase.CONTENT, fixture.viewModel.uiState.value.phase)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the first surface entry adds no load while a re-entry refreshes once`() {
        val fixture = fixture()
        try {
            fixture.viewModel.onSurfaceShown()
            assertEquals(1, fixture.pkSource.requests.size)

            fixture.viewModel.onSurfaceShown()
            assertEquals(2, fixture.pkSource.requests.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a real foreground return refreshes exactly once`() {
        val fixture = fixture()
        try {
            fixture.viewModel.onAppForegrounded()
            assertEquals(2, fixture.pkSource.requests.size)
            assertEquals(RetrospectivePhase.CONTENT, fixture.viewModel.uiState.value.phase)
        } finally {
            fixture.close()
        }
    }

    // ---------- policy invariant ----------

    @Test
    fun `the captured policy value is the frozen canonical default`() {
        val fixture = fixture()
        try {
            val request = fixture.pkSource.requests.single()
            val frozen = MedicationOccurrencePolicy(
                dueBefore = Duration.ofHours(1),
                dueAfter = Duration.ofHours(1),
                matchBefore = Duration.ofHours(1),
                matchAfter = Duration.ofHours(1),
                doseTolerance = 0.000_001
            )
            assertEquals(frozen, request.policy)
            assertEquals(frozen, fixture.allSource.calls.single().policy)
        } finally {
            fixture.close()
        }
    }

    // ---------- same-ID mutation races (MI6 / MI7 / MI9) ----------

    @Test
    fun `MI6 an event appearing only in Read2 after Read1 is not rendered`() {
        val occurred = c04Window().startInclusive.plusSeconds(1200)
        val readOneAccepted = UUID.randomUUID()
        val appearance = testEvent(id = 90L, occurredAt = occurred)

        val fixture = fixture(
            available = c04AvailableResult(
                window = c04Window(),
                engineInputEventIds = listOf(readOneAccepted)
            ),
            projection = HistoricalProjection(entries = listOf(unmatchedEntry(event = appearance)))
        )
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(RetrospectivePhase.CONTENT, state.phase)
            assertTrue(state.markers.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `MI7 a Read1 accepted id that disappeared before Read2 renders no marker and no error`() {
        val disappeared = UUID.randomUUID()
        val fixture = fixture(
            available = c04AvailableResult(
                window = c04Window(),
                engineInputEventIds = listOf(disappeared)
            ),
            projection = HistoricalProjection(entries = emptyList())
        )
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(RetrospectivePhase.CONTENT, state.phase)
            assertNull(state.failure)
            assertTrue(state.markers.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `MI9 a same-id edit between reads uses the current Read2 facts while the curve stays Read1`() {
        val original = testEvent(
            id = 91L,
            occurredAt = c04Window().startInclusive.plusSeconds(600),
            medicationKey = "EV",
            doseAmount = 5.0
        )
        val mutatedInstant = c04Window().startInclusive.plusSeconds(1200)
        val mutated = testEvent(
            id = 91L,
            occurredAt = mutatedInstant,
            medicationKey = "EV",
            doseAmount = 3.0
        )
        val available = c04AvailableResult(
            window = c04Window(),
            engineInputEventIds = listOf(original.eventId)
        )
        val rangeWithOccurrences = io.github.yingqiu0871.evolune.experience.HistoricalRange(
            startDate = c04Window().startInclusive.atZone(utc).toLocalDate().minusDays(1),
            endDate = c04Window().endInclusive.atZone(utc).toLocalDate().plusDays(1),
            days = listOf(
                io.github.yingqiu0871.evolune.experience.HistoricalDay(
                    date = mutatedInstant.atZone(utc).toLocalDate(),
                    entries = listOf(
                        matchedEntry(
                            occurrence = testOccurrence(
                                slotId = 77L,
                                date = mutatedInstant.atZone(utc).toLocalDate(),
                                time = mutatedInstant.atZone(utc).toLocalTime().withNano(0)
                            ),
                            event = mutated
                        )
                    )
                )
            )
        )

        val fixture = fixture(
            available = available,
            projection = HistoricalProjection(entries = listOf(unmatchedEntry(event = mutated))),
            range = rangeWithOccurrences
        )
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(RetrospectivePhase.CONTENT, state.phase)

            val intake = state.markers.filterIsInstance<RecordedIntakeMarker>().single()
            assertEquals("the marker shows the current Read2 payload", mutatedInstant, intake.occurredAt)
            assertEquals(mutated.eventId, intake.eventId)

            val result = state.result as RetrospectivePkResult.Available
            assertSame("the curve stays the immutable Read1 estimate", available.series, result.series)
            assertNull(state.failure)
        } finally {
            fixture.close()
        }
    }

    // ---------- MS scope end-to-end ----------

    @Test
    fun `MS2 schedule identity scope applies end-to-end`() {
        val knownInstant = c04Window().startInclusive.plusSeconds(600)
        val antiInstant = knownInstant.plusSeconds(600)
        val range = io.github.yingqiu0871.evolune.experience.HistoricalRange(
            startDate = c04Window().startInclusive.atZone(utc).toLocalDate().minusDays(1),
            endDate = c04Window().endInclusive.atZone(utc).toLocalDate().plusDays(1),
            days = listOf(
                io.github.yingqiu0871.evolune.experience.HistoricalDay(
                    date = knownInstant.atZone(utc).toLocalDate(),
                    entries = listOf(
                        unrecordedEntry(
                            occurrence = testOccurrence(
                                slotId = 1L,
                                date = knownInstant.atZone(utc).toLocalDate(),
                                time = knownInstant.atZone(utc).toLocalTime().withNano(0),
                                medicationKey = "EB"
                            )
                        ),
                        unrecordedEntry(
                            occurrence = testOccurrence(
                                slotId = 2L,
                                date = antiInstant.atZone(utc).toLocalDate(),
                                time = antiInstant.atZone(utc).toLocalTime().withNano(0),
                                routeKey = "ANTIANDROGEN",
                                medicationKey = "E2"
                            )
                        )
                    )
                )
            )
        )
        val fixture = fixture(range = range)
        try {
            val schedule = fixture.viewModel.uiState.value.markers.filterIsInstance<ScheduleContextMarker>()
            assertEquals(1, schedule.size)
            assertEquals(knownInstant, schedule.single().scheduledAt)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `markers are published in deterministic instant order`() {
        val early = c04Window().startInclusive.plusSeconds(300)
        val late = c04Window().startInclusive.plusSeconds(900)
        val range = io.github.yingqiu0871.evolune.experience.HistoricalRange(
            startDate = c04Window().startInclusive.atZone(utc).toLocalDate().minusDays(1),
            endDate = c04Window().endInclusive.atZone(utc).toLocalDate().plusDays(1),
            days = listOf(
                io.github.yingqiu0871.evolune.experience.HistoricalDay(
                    date = early.atZone(utc).toLocalDate(),
                    entries = listOf(
                        unrecordedEntry(
                            occurrence = testOccurrence(
                                slotId = 2L,
                                date = late.atZone(utc).toLocalDate(),
                                time = late.atZone(utc).toLocalTime().withNano(0)
                            )
                        ),
                        unrecordedEntry(
                            occurrence = testOccurrence(
                                slotId = 1L,
                                date = early.atZone(utc).toLocalDate(),
                                time = early.atZone(utc).toLocalTime().withNano(0)
                            )
                        )
                    )
                )
            )
        )
        val fixture = fixture(range = range)
        try {
            val instants = fixture.viewModel.uiState.value.markers.map { it.instant }
            assertEquals(listOf(early, late), instants)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the unavailable reason mapping stays inside the frozen taxonomy`() {
        RetrospectivePkUnavailableReason.entries.forEach { reason ->
            val fixture = fixture(available = c04UnavailableResult(c04Window(), reason))
            try {
                val state = fixture.viewModel.uiState.value
                assertEquals(RetrospectivePhase.UNAVAILABLE, state.phase)
                assertEquals(reason, (state.result as RetrospectivePkResult.Unavailable).reason)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `the initial state carries the current window even before the load settles`() {
        val fixture = fixture()
        try {
            val state = fixture.viewModel.uiState.value
            assertEquals(LocalDate.of(2026, 9, 16), state.windowEnd.atZone(utc).toLocalDate())
            assertEquals(c04Window().startInclusive, state.windowStart)
            assertEquals(utc, state.displayZone)
        } finally {
            fixture.close()
        }
    }
}
