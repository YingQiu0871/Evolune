package io.github.yingqiu0871.evolune.history.pk

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.history.AllAvailableHistorySource
import io.github.yingqiu0871.evolune.history.HistoricalOccurrenceLimitExceededException
import io.github.yingqiu0871.evolune.history.HistoryReadService
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.pk.SimulationResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CancellationException
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors

/**
 * V17-C-01 §13.4: service orchestration, query/resource validation, cursor, limitations,
 * determinism, golden values and the R4.2 runtime-contract tests (W1-W5, W10, V-series).
 */
class RetrospectivePkServiceTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val t0: Instant = Instant.parse("2025-06-01T08:00:00Z")
    private val policy = MedicationOccurrencePolicy()

    @Test
    fun `A01 estimate runs the curve runner on the injected computation dispatcher`() {
        val callerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "retrospective-caller-test")
        }
        val computationExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "retrospective-computation-test")
        }
        val callerDispatcher = callerExecutor.asCoroutineDispatcher()
        val computationDispatcher = computationExecutor.asCoroutineDispatcher()
        var runnerThreadName: String? = null
        val runner = RetrospectivePkCurveRunner { _, _, startTimeH, endTimeH, _ ->
            runnerThreadName = Thread.currentThread().name
            SimulationResult(
                timeH = listOf(startTimeH, endTimeH),
                concPGmL = listOf(0.0, 0.0),
                auc = 0.0
            )
        }

        try {
            val result = runBlocking(callerDispatcher) {
                RetrospectivePkService(
                    history = reader(events = listOf(injectionEvent(UUID(0L, 101L), t0))),
                    curveRunner = runner,
                    computationDispatcher = computationDispatcher
                ).estimate(request())
            }

            assertTrue(result is RetrospectivePkResult.Available)
            assertTrue(runnerThreadName.orEmpty().startsWith("retrospective-computation-test"))
            assertTrue(!runnerThreadName.orEmpty().startsWith("retrospective-caller-test"))
        } finally {
            callerDispatcher.close()
            computationDispatcher.close()
        }
    }

    // ------------------------------------------------------------------
    // S1 / W1: established-style retrospective golden on the R4.2 EV fixture
    // ------------------------------------------------------------------

    @Test
    fun `S1 W1 tau-zero EV fixture is accepted and keeps the exact negative roundoff`() = runBlocking {
        val injection = injectionEvent(UUID(0L, 1L), t0)
        val runner = RecordingCurveRunner()
        val result = estimate(
            source = reader(events = listOf(injection)),
            runner = runner
        )

        val available = result as RetrospectivePkResult.Available
        assertEquals(8641, runner.lastSteps)
        assertEquals(48338.59081520633, available.curve.auc, 1e-9)
        assertEquals(8641, available.series.points.size)

        // W1 authoritative assertions: range + exact same-run engine equality.
        val first = available.series.points.first().concentrationPGmL
        assertTrue("first point must be a tiny negative artifact", first < 0.0)
        assertTrue("first point must be within the frozen tolerance", first >= -1e-9)
        assertEquals(available.curve.concPGmL[0], first, 0.0)
        // Reference/evidence assertion for the deterministic fixture (not the cross-JVM contract).
        assertEquals(-2.7255464005139244E-13, first, 0.0)

        listOf(0, 1440, 4320, 7200, 8640).forEach { index ->
            assertEquals(available.curve.concPGmL[index], available.series.points[index].concentrationPGmL, 0.0)
        }
        assertEquals(213.21107814555296, available.curve.concPGmL[1440], 1e-9)
        assertEquals(5.76256344482292, available.curve.concPGmL[4320], 1e-9)
        assertEquals(0.19353466027581312, available.curve.concPGmL[7200], 1e-9)
        assertEquals(0.03659750073613962, available.curve.concPGmL[8640], 1e-9)
    }

    // ------------------------------------------------------------------
    // W2 / W3 / W4: validator boundary via the synthetic curve seam
    // ------------------------------------------------------------------

    @Test
    fun `W2 exactly minus one nanogram-gram is accepted unchanged`() = runBlocking {
        val result = estimate(
            source = reader(events = listOf(injectionEvent(UUID(0L, 2L), t0))),
            runner = FixedCurveRunner(curveWithValues(listOf(0.0, -1e-9, 50.0)))
        )
        val available = result as RetrospectivePkResult.Available
        assertEquals(-1e-9, available.series.points[1].concentrationPGmL, 0.0)
    }

    @Test
    fun `W3 below minus one nanogram-gram is a contract violation`() = runBlocking {
        assertContractViolation {
            estimate(
                source = reader(events = listOf(injectionEvent(UUID(0L, 3L), t0))),
                runner = FixedCurveRunner(curveWithValues(listOf(0.0, -1.0000000001e-9, 50.0)))
            )
        }
    }

    @Test
    fun `W4 non-finite engine output is a contract violation`() = runBlocking {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { bad ->
            assertContractViolation {
                estimate(
                    source = reader(events = listOf(injectionEvent(UUID(0L, 4L), t0))),
                    runner = FixedCurveRunner(curveWithValues(listOf(0.0, bad, 50.0)))
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // S2: no current-plan prediction
    // ------------------------------------------------------------------

    @Test
    fun `S2 enabled plans never contribute predicted doses to the curve`() = runBlocking {
        val injection = injectionEvent(UUID(0L, 11L), t0)
        val plan = plan(UUID(0L, 12L), listOf(LocalTime.of(8, 0)), createdAt = t0.minus(Duration.ofDays(30)))

        val withPlan = estimate(reader(plans = listOf(plan), events = listOf(injection)))
        val withoutPlan = estimate(reader(events = listOf(injection)))

        val a = withPlan as RetrospectivePkResult.Available
        val b = withoutPlan as RetrospectivePkResult.Available
        assertEquals(b.curve.auc, a.curve.auc, 0.0)
        assertEquals(listOf(injection.id), a.summary.engineInputEventIds)
    }

    // ------------------------------------------------------------------
    // S3: inclusive upper bound, no future event reaches the engine
    // ------------------------------------------------------------------

    @Test
    fun `S3 the inclusive upper bound is enforced and late rows are not exclusions`() = runBlocking {
        val end = t0.plus(Duration.ofDays(30))
        val inside = injectionEvent(UUID(0L, 21L), end)
        val after = injectionEvent(UUID(0L, 22L), end.plusMillis(1))
        val repository = FakeDoseEventRepository(listOf(inside, after))
        val service = service(reader(events = emptyList(), repository = repository))

        val available = service.estimate(request(window(Duration.ofDays(30)))) as RetrospectivePkResult.Available

        assertEquals(listOf(inside.id), available.summary.engineInputEventIds)
        assertTrue(available.exclusions.isEmpty())
        assertTrue(available.summary.concentrationProducingEventIds == listOf(inside.id))
    }

    // ------------------------------------------------------------------
    // S4: prehistory
    // ------------------------------------------------------------------

    @Test
    fun `S4 doses long before the window still shape the curve`() = runBlocking {
        val old = injectionEvent(UUID(0L, 31L), t0.minus(Duration.ofDays(200)))
        val available = estimate(reader(events = listOf(old))) as RetrospectivePkResult.Available

        assertTrue(available.curve.auc > 0.0)
        assertTrue(available.curve.concPGmL.first() > 0.0)
    }

    // ------------------------------------------------------------------
    // S5 / V4 / W10: NO_ELIGIBLE_RECORDED_INTAKES
    // ------------------------------------------------------------------

    @Test
    fun `S5 no recorded intakes yields NO_ELIGIBLE_RECORDED_INTAKES`() = runBlocking {
        val result = estimate(reader())
        assertReason(result, RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES)
    }

    @Test
    fun `S5 all-excluded facts yield NO_ELIGIBLE_RECORDED_INTAKES`() = runBlocking {
        val antiAndrogen = event(UUID(0L, 41L), t0, doseMG = 25.0, route = Route.ANTIANDROGEN)
        assertReason(
            estimate(reader(events = listOf(antiAndrogen))),
            RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES
        )
    }

    @Test
    fun `V4 W10 only zero-contribution facts yield NO_ELIGIBLE_RECORDED_INTAKES`() = runBlocking {
        val zeroDose = event(UUID(0L, 42L), t0, doseMG = 0.0)
        assertReason(
            estimate(reader(events = listOf(zeroDose))),
            RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES
        )

        val zeroRatePatch = event(
            UUID(0L, 43L), t0, doseMG = 2.0, route = Route.PATCH_APPLY,
            extras = mapOf(ExtraKey.RELEASE_RATE_UG_PER_DAY to 0.0)
        )
        assertReason(
            estimate(reader(events = listOf(zeroRatePatch))),
            RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES
        )
    }

    // ------------------------------------------------------------------
    // S6 / V11 / V12 / V13: query and resource gates
    // ------------------------------------------------------------------

    @Test
    fun `S6 V13 out-of-band instants are INVALID_QUERY_INTERVAL without any read`() = runBlocking {
        val band = MAX_RETROSPECTIVE_PK_EPOCH_MILLIS
        val counting = CountingSource(reader(events = listOf(injectionEvent(UUID(0L, 51L), t0))))
        val runner = RecordingCurveRunner()
        val outside = RetrospectivePkWindow(
            Instant.ofEpochMilli(band + 1),
            Instant.ofEpochMilli(band + 2)
        )
        val result = RetrospectivePkService(counting, runner).estimate(request(outside))

        assertReason(result, RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL)
        assertEquals(0, counting.calls)
        assertEquals(0, runner.calls)
    }

    @Test
    fun `S6 V13 the safety band boundary itself is accepted`() = runBlocking {
        val band = MAX_RETROSPECTIVE_PK_EPOCH_MILLIS
        val insideBand = RetrospectivePkWindow(
            Instant.ofEpochMilli(band - 1),
            Instant.ofEpochMilli(band)
        )
        val result = RetrospectivePkService(reader()).estimate(request(insideBand))

        assertReason(result, RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES)
    }

    @Test
    fun `V11 exactly 366 days is accepted and invokes the engine once`() = runBlocking {
        val runner = RecordingCurveRunner()
        val result = estimate(
            source = reader(events = listOf(injectionEvent(UUID(0L, 52L), t0))),
            runner = runner,
            window = window(Duration.ofDays(366))
        )

        assertTrue(result is RetrospectivePkResult.Available)
        assertEquals(1, runner.calls)
    }

    @Test
    fun `V12 366 days plus one millisecond is rejected before history and engine`() = runBlocking {
        val counting = CountingSource(reader(events = listOf(injectionEvent(UUID(0L, 53L), t0))))
        val runner = RecordingCurveRunner()
        val tooLong = RetrospectivePkWindow(t0, t0.plus(Duration.ofDays(366)).plusMillis(1))

        val result = RetrospectivePkService(counting, runner).estimate(request(tooLong))

        assertReason(result, RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL)
        assertEquals(0, counting.calls)
        assertEquals(0, runner.calls)
    }

    // ------------------------------------------------------------------
    // V22-V26: grid floor and density (R4.1)
    // ------------------------------------------------------------------

    @Test
    fun `V22 V23 V24 short windows floor the grid to 1000`() = runBlocking {
        listOf(
            Duration.ofHours(24),
            Duration.ofHours(10),
            Duration.ofMinutes((83.25 * 60).toLong()) // 83.25 h == 4995 minutes exactly
        ).forEach { duration ->
            val runner = RecordingCurveRunner()
            estimate(
                source = reader(events = listOf(injectionEvent(UUID(0L, 54L), t0))),
                runner = runner,
                window = window(duration)
            )
            assertEquals("duration $duration must floor to 1000", 1000, runner.lastSteps)
        }
    }

    @Test
    fun `V25 84 hours produces 1009 steps`() = runBlocking {
        val runner = RecordingCurveRunner()
        estimate(
            source = reader(events = listOf(injectionEvent(UUID(0L, 55L), t0))),
            runner = runner,
            window = window(Duration.ofHours(84))
        )
        assertEquals(1009, runner.lastSteps)
    }

    @Test
    fun `V26 366 days preserves the 12 per hour density with 105409 steps`() = runBlocking {
        val runner = RecordingCurveRunner()
        estimate(
            source = reader(events = listOf(injectionEvent(UUID(0L, 56L), t0))),
            runner = runner,
            window = window(Duration.ofDays(366))
        )
        assertEquals(105409, runner.lastSteps)
    }

    // ------------------------------------------------------------------
    // S7-S9: determinism, clamp-free output, typed model context
    // ------------------------------------------------------------------

    @Test
    fun `S7 repeated estimation is deterministic`() = runBlocking {
        val events = listOf(
            injectionEvent(UUID(0L, 61L), t0.minus(Duration.ofDays(3))),
            event(UUID(0L, 62L), t0.minus(Duration.ofHours(5)), doseMG = 1.0)
        )
        val first = estimate(reader(events = events)) as RetrospectivePkResult.Available
        val second = estimate(reader(events = events)) as RetrospectivePkResult.Available

        assertEquals(first.curve.auc, second.curve.auc, 0.0)
        assertEquals(first.curve.concPGmL, second.curve.concPGmL)
        assertEquals(first.summary, second.summary)
        assertEquals(first.limitations, second.limitations)
    }

    @Test
    fun `S8 W5 curve is the raw engine output and every series value is exact`() = runBlocking {
        val injection = injectionEvent(UUID(0L, 71L), t0.minus(Duration.ofHours(2)))
        val available = estimate(reader(events = listOf(injection))) as RetrospectivePkResult.Available

        val direct = SimulationEngine(
            events = listOf(
                io.github.yingqiu0871.evolune.pk.DoseEvent(
                    id = injection.id,
                    route = Route.INJECTION,
                    timeH = injection.occurredAt.toEpochMilli() / 3_600_000.0,
                    doseMG = injection.doseMG,
                    ester = injection.ester
                )
            ),
            bodyWeightKG = 60.0,
            startTimeH = t0.toEpochMilli() / 3_600_000.0,
            endTimeH = t0.plus(Duration.ofDays(30)).toEpochMilli() / 3_600_000.0,
            numberOfSteps = 8641
        ).run()

        assertEquals(direct.auc, available.curve.auc, 0.0)
        assertEquals(direct.concPGmL, available.curve.concPGmL)
        available.series.points.forEachIndexed { index, point ->
            assertEquals(available.curve.concPGmL[index], point.concentrationPGmL, 0.0)
        }
    }

    @Test
    fun `S9 every result carries the enum-typed model provenance`() = runBlocking {
        val available = estimate(reader(events = listOf(injectionEvent(UUID(0L, 81L), t0)))) as RetrospectivePkResult.Available
        val context = available.modelContext
        assertEquals(RetrospectivePkParameterSet.EVOLUNE_E2_PK_PARAMETER_SET_V1, context.parameterSet)
        assertEquals(ParameterBasis.CURRENT_MODEL_PARAMETERS, context.parameterBasis)
        assertEquals(BodyWeightBasis.CURRENT_SETTING_AT_QUERY_TIME, context.bodyWeightBasis)
        assertEquals(IntakeBasis.RECORDED_ACTUAL_INTAKES, context.intakeBasis)
        assertEquals(HistoricalParameterSnapshotAvailability.UNAVAILABLE, context.historicalParameterSnapshotAvailability)
        assertEquals(60.0, context.bodyWeightKg, 0.0)
        assertEquals(t0, context.capturedAt)

        val unavailable = estimate(reader()) as RetrospectivePkResult.Unavailable
        assertEquals(ParameterBasis.CURRENT_MODEL_PARAMETERS, unavailable.modelContext.parameterBasis)
    }

    // ------------------------------------------------------------------
    // S10: limitations
    // ------------------------------------------------------------------

    @Test
    fun `S10 unrecorded occurrences present over the full consumed lookback`() = runBlocking {
        val plan = plan(UUID(0L, 91L), listOf(LocalTime.of(8, 0)), createdAt = t0)
        val available = estimate(
            source = reader(plans = listOf(plan), events = listOf(injectionEvent(UUID(0L, 92L), t0.plus(Duration.ofDays(1))))),
            window = window(Duration.ofDays(3))
        ) as RetrospectivePkResult.Available

        assertEquals(
            setOf(
                RetrospectivePkLimitation.EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE,
                RetrospectivePkLimitation.UNRECORDED_OCCURRENCES_PRESENT
            ),
            available.limitations
        )
    }

    @Test
    fun `S10 excluded intakes and ambiguous pairing set their limitations`() = runBlocking {
        val ambiguousApplyA = event(UUID(0L, 93L), t0.minus(Duration.ofDays(2)), doseMG = 2.0, route = Route.PATCH_APPLY)
        val ambiguousApplyB = event(UUID(0L, 94L), t0.minus(Duration.ofDays(1)), doseMG = 2.0, route = Route.PATCH_APPLY)
        val ambiguousRemove = event(UUID(0L, 95L), t0, doseMG = 0.0, route = Route.PATCH_REMOVE)
        val validInjection = injectionEvent(UUID(0L, 96L), t0.minus(Duration.ofHours(1)))

        val available = estimate(
            reader(events = listOf(ambiguousApplyA, ambiguousApplyB, ambiguousRemove, validInjection))
        ) as RetrospectivePkResult.Available

        assertTrue(RetrospectivePkLimitation.AMBIGUOUS_PATCH_PAIRING in available.limitations)
        assertTrue(RetrospectivePkLimitation.EXCLUDED_RECORDED_INTAKES in available.limitations)
        assertTrue(RetrospectivePkLimitation.EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE in available.limitations)
        assertEquals(3, available.exclusions.size)
    }

    // ------------------------------------------------------------------
    // S11: non-lossy summary
    // ------------------------------------------------------------------

    @Test
    fun `S11 summary exposes exact lookbackStart and the three id lists`() = runBlocking {
        val oldest = injectionEvent(UUID(0L, 101L), t0.minus(Duration.ofDays(10)))
        val newer = event(UUID(0L, 102L), t0.minus(Duration.ofHours(1)), doseMG = 3.0)
        val available = estimate(reader(events = listOf(newer, oldest))) as RetrospectivePkResult.Available

        assertEquals(oldest.occurredAt, available.summary.lookbackStart)
        assertEquals(t0.plus(Duration.ofDays(30)), available.summary.upperBoundInclusive)
        assertEquals(listOf(oldest.id, newer.id), available.summary.engineInputEventIds)
        assertEquals(listOf(oldest.id, newer.id), available.summary.concentrationProducingEventIds)
        assertTrue(available.summary.patchControlEventIds.isEmpty())
        assertEquals(available.summary.engineInputEventIds, available.summary.engineInputEventIds.distinct())
    }

    // ------------------------------------------------------------------
    // S12: millisecond endpoint round-trip
    // ------------------------------------------------------------------

    @Test
    fun `S12 series endpoints equal the window endpoints and round-trip exactly`() = runBlocking {
        val visible = window(Duration.ofDays(30))
        val available = estimate(reader(events = listOf(injectionEvent(UUID(0L, 111L), t0)))) as RetrospectivePkResult.Available

        assertEquals(visible.startInclusive, available.series.startInclusive)
        assertEquals(visible.endInclusive, available.series.endInclusive)
        assertEquals(visible.startInclusive, instantRoundTrip(visible.startInclusive))
        assertEquals(visible.endInclusive, instantRoundTrip(visible.endInclusive))
        assertEquals(visible, available.calculatedInterval)

        // Frozen S12 contract: point-for-point Instant representation of the curve.
        assertEquals(available.curve.timeH.size, available.series.points.size)
        assertEquals(available.curve.timeH.size, available.curve.concPGmL.size)
        assertTrue(available.series.points.isNotEmpty())
        assertEquals(visible.startInclusive, available.series.points.first().instant)
        assertEquals(visible.endInclusive, available.series.points.last().instant)
        available.curve.timeH.forEachIndexed { index, timeH ->
            val expectedInstant = Instant.ofEpochMilli(Math.round(timeH * 3_600_000.0))
            assertEquals("point $index", expectedInstant, available.series.points[index].instant)
        }
    }

    private fun instantRoundTrip(instant: Instant): Instant {
        val timeH = instant.toEpochMilli() / 3_600_000.0
        return Instant.ofEpochMilli(Math.round(timeH * 3_600_000.0))
    }

    // ------------------------------------------------------------------
    // S13 / S14: cursor contract
    // ------------------------------------------------------------------

    @Test
    fun `S13a null cursor produces no estimate`() = runBlocking {
        val available = estimate(reader(events = listOf(injectionEvent(UUID(0L, 121L), t0)))) as RetrospectivePkResult.Available
        assertNull(available.cursorEstimate)
    }

    @Test
    fun `S13b cursor at start inside and end interpolates the series`() = runBlocking {
        val visible = window(Duration.ofDays(30))
        val runner = FixedCurveRunner(curveWithValues(listOf(0.0, 100.0, 200.0)))
        val interpolationPoints = listOf(
            visible.startInclusive to 0.0,
            visible.startInclusive.plus(Duration.ofDays(7).plusHours(12)) to 50.0,
            visible.startInclusive.plus(Duration.ofDays(15)) to 100.0,
            visible.endInclusive to 200.0
        )
        interpolationPoints.forEach { (cursor, expected) ->
            val available = estimate(
                source = reader(events = listOf(injectionEvent(UUID(0L, 122L), t0))),
                runner = runner,
                window = visible,
                cursor = cursor
            ) as RetrospectivePkResult.Available
            assertNotNull(available.cursorEstimate)
            assertEquals("cursor $cursor", expected, requireNotNull(available.cursorEstimate).concentrationPGmL, 1e-9)
        }
    }

    @Test
    fun `S13c defensive interval validator reports the typed outside path`() {
        val interval = RetrospectivePkWindow(t0, t0.plus(Duration.ofDays(10)))
        assertEquals(
            RetrospectivePkUnavailableReason.QUERY_OUTSIDE_CALCULATED_INTERVAL,
            RetrospectivePkIntervalValidator.validateCursor(t0.plus(Duration.ofDays(20)), interval)
        )
        assertEquals(
            RetrospectivePkUnavailableReason.QUERY_OUTSIDE_CALCULATED_INTERVAL,
            RetrospectivePkIntervalValidator.validateCursor(t0.minusMillis(1), interval)
        )
        assertNull(RetrospectivePkIntervalValidator.validateCursor(t0.plus(Duration.ofDays(5)), interval))
        assertNull(RetrospectivePkIntervalValidator.validateCursor(null, interval))
    }

    @Test
    fun `S13d cursor outside the visible window fails request construction`() {
        val visible = RetrospectivePkWindow(t0, t0.plus(Duration.ofDays(1)))
        try {
            RetrospectivePkRequest(
                visibleWindow = visible,
                cursor = t0.plus(Duration.ofDays(2)),
                displayZone = utc,
                bodyWeightKG = 60.0,
                capturedAt = t0
            )
            throw AssertionError("cursor outside the visible window must fail")
        } catch (expected: IllegalArgumentException) {
            assertTrue(requireNotNull(expected.message).contains("cursor"))
        }
    }

    @Test
    fun `S14 cursor interpolation is a true interpolation not an endpoint clamp`() = runBlocking {
        val visible = window(Duration.ofDays(30))
        val available = estimate(
            source = reader(events = listOf(injectionEvent(UUID(0L, 123L), t0))),
            runner = FixedCurveRunner(curveWithValues(listOf(0.0, 100.0, 200.0))),
            window = visible,
            cursor = visible.startInclusive.plus(Duration.ofDays(7).plusHours(12))
        ) as RetrospectivePkResult.Available

        assertEquals(50.0, requireNotNull(available.cursorEstimate).concentrationPGmL, 1e-9)
    }

    // ------------------------------------------------------------------
    // S15-S19: exception classification and cancellation
    // ------------------------------------------------------------------

    @Test
    fun `S15 repository read failure becomes HISTORICAL_INPUT_UNAVAILABLE without partial output`() = runBlocking {
        val failing = AllAvailableHistorySource { _, _, _ ->
            throw RepositoryPersistenceException("read", null)
        }
        val result = RetrospectivePkService(failing).estimate(request())
        assertReason(result, RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE)
        val unavailable = result as RetrospectivePkResult.Unavailable
        assertTrue(unavailable.exclusions.isEmpty())
        assertTrue(unavailable.summary.engineInputEventIds.isEmpty())
        assertTrue(unavailable.limitations.isEmpty())
    }

    @Test
    fun `S16 generator limit diagnostic becomes HISTORICAL_INPUT_UNAVAILABLE`() = runBlocking {
        val failing = AllAvailableHistorySource { _, _, _ ->
            throw HistoricalOccurrenceLimitExceededException("occurrence result exceeds 100000 items")
        }
        val result = RetrospectivePkService(failing).estimate(request())
        assertReason(result, RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE)
    }

    @Test
    fun `S17 public enum cardinalities are exactly frozen`() {
        assertEquals(
            setOf(
                "NO_ELIGIBLE_RECORDED_INTAKES",
                "INVALID_QUERY_INTERVAL",
                "QUERY_OUTSIDE_CALCULATED_INTERVAL",
                "HISTORICAL_INPUT_UNAVAILABLE"
            ),
            RetrospectivePkUnavailableReason.values().map { it.name }.toSet()
        )
        assertEquals(
            setOf(
                "UNKNOWN_OR_PARTIAL_IDENTITY",
                "ANTIANDROGEN_IDENTITY_UNAVAILABLE",
                "UNSUPPORTED_CURRENT_MODEL_COMBINATION",
                "UNSUPPORTED_OR_INCOMPLETE_EVENT",
                "AMBIGUOUS_PATCH_PAIRING"
            ),
            RetrospectivePkExclusionReason.values().map { it.name }.toSet()
        )
        assertEquals(
            setOf(
                "EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE",
                "UNRECORDED_OCCURRENCES_PRESENT",
                "EXCLUDED_RECORDED_INTAKES",
                "AMBIGUOUS_PATCH_PAIRING"
            ),
            RetrospectivePkLimitation.values().map { it.name }.toSet()
        )
    }

    @Test
    fun `S18 CancellationException escapes unchanged`() = runBlocking {
        val cancelling = AllAvailableHistorySource { _, _, _ -> throw CancellationException("cancelled") }
        try {
            RetrospectivePkService(cancelling).estimate(request())
            throw AssertionError("cancellation must escape")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    @Test
    fun `S19 internal IllegalStateException is not swallowed`() = runBlocking {
        val broken = AllAvailableHistorySource { _, _, _ ->
            throw IllegalStateException("projection completeness violated")
        }
        try {
            RetrospectivePkService(broken).estimate(request())
            throw AssertionError("internal invariant failures must propagate")
        } catch (expected: IllegalStateException) {
            assertTrue(requireNotNull(expected.message).contains("completeness"))
        }
    }

    // ------------------------------------------------------------------
    // S20: fresh-read undo
    // ------------------------------------------------------------------

    @Test
    fun `S20 deleting the authoritative event removes the next-read contribution`() = runBlocking {
        val injection = injectionEvent(UUID(0L, 131L), t0.minus(Duration.ofHours(1)))
        val repository = FakeDoseEventRepository(listOf(injection))
        val source = reader(events = emptyList(), repository = repository)

        val before = estimate(source) as RetrospectivePkResult.Available
        assertTrue(before.curve.auc > 0.0)
        assertEquals(listOf(injection.id), before.summary.engineInputEventIds)

        repository.delete(injection.id)

        val after = estimate(source)
        assertReason(after, RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES)
        assertTrue((after as RetrospectivePkResult.Unavailable).summary.engineInputEventIds.isEmpty())
    }

    // ------------------------------------------------------------------
    // S21 / V20: body weight precondition
    // ------------------------------------------------------------------

    @Test
    fun `S21 V20 invalid body weight is a precondition failure and never reaches the engine`() = runBlocking {
        listOf(Double.NaN, 0.0, -1.0, 300.0001).forEach { weight ->
            val runner = RecordingCurveRunner()
            try {
                estimate(
                    source = reader(events = listOf(injectionEvent(UUID(0L, 141L), t0))),
                    runner = runner,
                    weight = weight
                )
                throw AssertionError("weight $weight must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(requireNotNull(expected.message).contains("weight"))
            }
            assertEquals(0, runner.calls)
        }
    }

    // ------------------------------------------------------------------
    // D1-D3: local time resolution and date-line handling
    // ------------------------------------------------------------------

    @Test
    fun `D1 DST gap resolution is typed as GapAdjusted`() {
        val paris = ZoneId.of("Europe/Paris")
        val resolution = LocalQueryTimeResolver.resolve(LocalDateTime.of(2026, 3, 29, 2, 30), paris)
        assertTrue(resolution is LocalQueryTimeResolution.GapAdjusted)
        val adjusted = resolution as LocalQueryTimeResolution.GapAdjusted
        assertEquals(LocalDateTime.of(2026, 3, 29, 2, 30), adjusted.requested)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), adjusted.resolvedInstant)
        assertEquals(ZoneOffset.ofHours(2), adjusted.resolvedOffset)
    }

    @Test
    fun `D2 DST overlap requires an explicit offset choice`() {
        val paris = ZoneId.of("Europe/Paris")
        val requested = LocalDateTime.of(2026, 10, 25, 2, 30)
        val resolution = LocalQueryTimeResolver.resolve(requested, paris)
        assertTrue(resolution is LocalQueryTimeResolution.OverlapChoiceRequired)
        val overlap = resolution as LocalQueryTimeResolution.OverlapChoiceRequired
        assertEquals(2, overlap.candidates.size)
        assertEquals(
            listOf(Instant.parse("2026-10-25T00:30:00Z"), Instant.parse("2026-10-25T01:30:00Z")),
            overlap.candidates.map { it.instant }
        )

        val earlier = LocalQueryTimeResolver.resolve(requested, paris, ZoneOffset.ofHours(2))
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), (earlier as LocalQueryTimeResolution.Resolved).instant)
        val later = LocalQueryTimeResolver.resolve(requested, paris, ZoneOffset.ofHours(1))
        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), (later as LocalQueryTimeResolution.Resolved).instant)
    }

    @Test
    fun `D3 date-line jump windows construct deterministically`() {
        val apia = ZoneId.of("Pacific/Apia")
        val window = RetrospectivePkWindow.fromLocalDates(
            LocalDate.of(2011, 12, 31),
            LocalDate.of(2011, 12, 31),
            apia
        )
        assertTrue(window.startInclusive.isBefore(window.endInclusive))
        assertEquals(window.startInclusive, instantRoundTrip(window.startInclusive))
        assertEquals(window.endInclusive, instantRoundTrip(window.endInclusive))

        // Samoa skipped 2011-12-30 entirely: a 29th..31st local range spans only one real day.
        val spanning = RetrospectivePkWindow.fromLocalDates(
            LocalDate.of(2011, 12, 29),
            LocalDate.of(2011, 12, 31),
            apia
        )
        assertTrue(spanning.duration.toMillis() > 0L)
        assertEquals(spanning.startInclusive, instantRoundTrip(spanning.startInclusive))
        assertEquals(spanning.endInclusive, instantRoundTrip(spanning.endInclusive))
    }

    @Test
    fun `V21 extreme local dates and date-times fail as request-boundary or typed errors`() {
        try {
            RetrospectivePkWindow.fromLocalDates(LocalDate.MAX, LocalDate.MAX, utc)
            throw AssertionError("extreme local date must not silently construct")
        } catch (expected: IllegalArgumentException) {
            assertTrue(requireNotNull(expected.message).isNotBlank())
        }

        // The resolver resolves an extreme local date-time deterministically, but the resulting
        // instant is far outside the numerical safety band: the service must reject it typed.
        val extremeInstant = (
            LocalQueryTimeResolver.resolve(LocalDateTime.of(LocalDate.MAX, LocalTime.MIDNIGHT), utc)
                as? LocalQueryTimeResolution.Resolved
            )?.instant
        assertNotNull(extremeInstant)
        val extremeWindow = RetrospectivePkWindow(
            requireNotNull(extremeInstant),
            requireNotNull(extremeInstant).plusMillis(1)
        )
        val runner = RecordingCurveRunner()
        val result = runBlocking {
            RetrospectivePkService(reader(), runner).estimate(request(extremeWindow))
        }
        assertReason(result, RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL)
        assertEquals(0, runner.calls)
    }

    // ------------------------------------------------------------------
    // T1: display zone independence
    // ------------------------------------------------------------------

    @Test
    fun `T1 identical instants across display zones produce identical curves`() = runBlocking {
        val injection = injectionEvent(UUID(0L, 151L), t0.minus(Duration.ofHours(3)))
        val kiritimati = ZoneId.of("Pacific/Kiritimati")

        val utcResult = estimate(reader(events = listOf(injection)), zone = utc) as RetrospectivePkResult.Available
        val lineResult = estimate(reader(events = listOf(injection)), zone = kiritimati) as RetrospectivePkResult.Available

        assertEquals(utcResult.curve.auc, lineResult.curve.auc, 0.0)
        assertEquals(utcResult.curve.concPGmL, lineResult.curve.concPGmL)
        assertEquals(utcResult.summary.engineInputEventIds, lineResult.summary.engineInputEventIds)
    }

    // ------------------------------------------------------------------
    // P10k: smoke
    // ------------------------------------------------------------------

    @Test
    fun `P10k ten thousand events remain linear smoke`() = runBlocking {
        val events = (0 until 10_000).map { index ->
            event(
                id = UUID(index.toLong(), 1L),
                at = t0.minusSeconds((index + 1) * 86L),
                doseMG = 2.0
            )
        }
        val available = estimate(
            source = reader(events = events),
            window = window(Duration.ofDays(1))
        ) as RetrospectivePkResult.Available

        assertEquals(10_000, available.summary.engineInputEventIds.size)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun assertReason(result: RetrospectivePkResult, reason: RetrospectivePkUnavailableReason) {
        assertTrue("expected Unavailable but was ${result::class.simpleName}", result is RetrospectivePkResult.Unavailable)
        assertEquals(reason, (result as RetrospectivePkResult.Unavailable).reason)
    }

    private fun assertContractViolation(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected RetrospectivePkContractViolationException")
        } catch (expected: RetrospectivePkContractViolationException) {
            assertTrue(requireNotNull(expected.message).isNotBlank())
        }
    }

    private fun estimate(
        source: AllAvailableHistorySource,
        runner: RetrospectivePkCurveRunner = DefaultRetrospectivePkCurveRunner,
        window: RetrospectivePkWindow = window(Duration.ofDays(30)),
        cursor: Instant? = null,
        zone: ZoneId = utc,
        weight: Double = 60.0
    ): RetrospectivePkResult = runBlocking {
        RetrospectivePkService(source, runner).estimate(request(window, cursor, zone, weight))
    }

    private fun request(
        window: RetrospectivePkWindow = RetrospectivePkWindow(t0, t0.plus(Duration.ofDays(30))),
        cursor: Instant? = null,
        zone: ZoneId = utc,
        weight: Double = 60.0
    ): RetrospectivePkRequest = RetrospectivePkRequest(
        visibleWindow = window,
        cursor = cursor,
        displayZone = zone,
        bodyWeightKG = weight,
        capturedAt = t0
    )

    private fun window(duration: Duration): RetrospectivePkWindow =
        RetrospectivePkWindow(t0, t0.plus(duration))

    private fun reader(
        plans: List<MedicationPlan> = emptyList(),
        events: List<DoseEvent> = emptyList(),
        repository: FakeDoseEventRepository? = null
    ): HistoryReadService = HistoryReadService(
        medicationPlans = FakeMedicationPlanRepository(plans),
        doseEvents = repository ?: FakeDoseEventRepository(events)
    )

    private fun service(source: AllAvailableHistorySource): RetrospectivePkService =
        RetrospectivePkService(source, DefaultRetrospectivePkCurveRunner)

    private fun curveWithValues(values: List<Double>): SimulationResult {
        val startH = t0.toEpochMilli() / 3_600_000.0
        val endH = t0.plus(Duration.ofDays(30)).toEpochMilli() / 3_600_000.0
        val timeH = List(values.size) { index -> startH + (endH - startH) * index / (values.size - 1) }
        return SimulationResult(timeH, values, 0.0)
    }

    private fun injectionEvent(id: UUID, at: Instant): DoseEvent = event(
        id = id,
        at = at,
        doseMG = 5.0,
        route = Route.INJECTION,
        ester = Ester.EV
    )

    private fun event(
        id: UUID,
        at: Instant,
        doseMG: Double,
        route: Route = Route.ORAL,
        ester: Ester = Ester.E2,
        extras: Map<ExtraKey, Double> = emptyMap()
    ): DoseEvent = DoseEvent(
        id = id,
        route = route,
        occurredAt = at,
        doseMG = doseMG,
        ester = ester,
        extras = extras,
        source = DoseEventSource.MANUAL
    )

    private fun plan(
        id: UUID,
        slots: List<LocalTime>,
        createdAt: Instant
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = "Retrospective synthetic plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = ScheduleType.DAILY,
        slots = slots.mapIndexed { position, time ->
            ScheduledDoseSlot(
                id = UUID(2L, position.toLong()),
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

    private class RecordingCurveRunner(
        private val delegate: RetrospectivePkCurveRunner = DefaultRetrospectivePkCurveRunner
    ) : RetrospectivePkCurveRunner {
        var calls = 0
            private set
        var lastSteps = 0
            private set

        override fun run(
            events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
            bodyWeightKG: Double,
            startTimeH: Double,
            endTimeH: Double,
            numberOfSteps: Int
        ): SimulationResult {
            calls += 1
            lastSteps = numberOfSteps
            return delegate.run(events, bodyWeightKG, startTimeH, endTimeH, numberOfSteps)
        }
    }

    private class FixedCurveRunner(private val result: SimulationResult) : RetrospectivePkCurveRunner {
        var calls = 0
            private set

        override fun run(
            events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
            bodyWeightKG: Double,
            startTimeH: Double,
            endTimeH: Double,
            numberOfSteps: Int
        ): SimulationResult {
            calls += 1
            return result
        }
    }

    private class CountingSource(
        private val delegate: AllAvailableHistorySource
    ) : AllAvailableHistorySource {
        var calls = 0
            private set

        override suspend fun readAllAvailable(
            upperBoundInclusive: Instant,
            displayZone: ZoneId,
            policy: MedicationOccurrencePolicy
        ): io.github.yingqiu0871.evolune.history.AllAvailableHistory {
            calls += 1
            return delegate.readAllAvailable(upperBoundInclusive, displayZone, policy)
        }
    }
}
