package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.core.adapter.DomainDoseEventToPkAdapter
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.pk.SimulationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class PkSimulationCalculatorTest {
    @Test
    fun `historical events with empty future reuse the baseline simulation result`() = runBlocking {
        val runner = RecordingPkSimulationRunner()
        val state = DefaultPkSimulationCalculator.calculate(
            input = input(historicalEvents = listOf(historicalEvent())),
            simulationRunner = runner
        )

        assertEquals(1, runner.calls.size)
        assertEquals(1, runner.calls[0].events.size)
        assertSame(state.baselineSimulationResult, state.simulationResult)
        assertEquals(runner.results[0], state.simulationResult)
    }

    @Test
    fun `nonempty future keeps distinct baseline and full simulations`() = runBlocking {
        val runner = RecordingPkSimulationRunner()
        val state = DefaultPkSimulationCalculator.calculate(
            input = input(
                historicalEvents = listOf(historicalEvent()),
                enabledPlans = listOf(futurePlan())
            ),
            simulationRunner = runner
        )

        assertEquals(2, runner.calls.size)
        assertEquals(1, runner.calls[0].events.size)
        assertTrue(runner.calls[1].events.size > runner.calls[0].events.size)
        assertTrue(runner.calls[1].events.first().timeH <= runner.calls[1].events.last().timeH)
        assertNotSame(state.baselineSimulationResult, state.simulationResult)
        assertEquals(runner.results[0], state.baselineSimulationResult)
        assertEquals(runner.results[1], state.simulationResult)
    }

    @Test
    fun `view model calculation preserves range points and concentrations`() = runBlocking {
        val now = Instant.parse("2026-01-02T03:04:05.678Z")
        val currentTimeH = now.toEpochMilli() / 3_600_000.0
        val event = DoseEvent(
            id = UUID(0L, 1L),
            route = Route.ORAL,
            occurredAt = now.minusSeconds(6 * 3_600L),
            zoneId = ZoneId.of("Asia/Shanghai"),
            localDate = now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate(),
            doseMG = 2.0,
            ester = Ester.E2,
            source = DoseEventSource.MANUAL,
            status = DoseEventStatus.RECORDED
        )

        val actual = DefaultPkSimulationCalculator.calculate(
            PkSimulationInput(
                now = now,
                currentTimeH = currentTimeH,
                historicalDoseEvents = listOf(event),
                enabledPlans = emptyList(),
                bodyWeightKG = 55.0,
                zoneId = ZoneId.of("Asia/Shanghai")
            )
        )
        val expected = SimulationEngine(
            events = DomainDoseEventToPkAdapter.adapt(listOf(event)),
            bodyWeightKG = 55.0,
            startTimeH = currentTimeH - 24.0 * 15,
            endTimeH = currentTimeH + 24.0 * 15,
            numberOfSteps = 8_641
        ).run()

        val actualResult = requireNotNull(actual.simulationResult)
        assertEquals(expected.timeH, actualResult.timeH)
        assertEquals(expected.concPGmL.size, actualResult.concPGmL.size)
        expected.concPGmL.indices.forEach { index ->
            assertEquals(
                expected.concPGmL[index],
                actualResult.concPGmL[index],
                1e-9
            )
        }
        assertEquals(expected, actual.baselineSimulationResult)
        assertEquals(expected.concentration(currentTimeH), actual.currentConcentration)
        assertEquals(currentTimeH, actual.currentTimeH, 0.0)
        assertEquals(now, actual.concentrationCalculatedAt)
    }

    private fun input(
        historicalEvents: List<DoseEvent> = emptyList(),
        enabledPlans: List<MedicationPlan> = emptyList()
    ): PkSimulationInput {
        val now = Instant.parse("2026-01-02T03:04:05.678Z")
        return PkSimulationInput(
            now = now,
            currentTimeH = now.toEpochMilli() / 3_600_000.0,
            historicalDoseEvents = historicalEvents,
            enabledPlans = enabledPlans,
            bodyWeightKG = 55.0,
            zoneId = ZoneId.of("Asia/Shanghai")
        )
    }

    private fun historicalEvent(): DoseEvent = DoseEvent(
        id = UUID(0L, 11L),
        route = Route.ORAL,
        occurredAt = Instant.parse("2026-01-02T02:00:00Z"),
        zoneId = ZoneId.of("Asia/Shanghai"),
        localDate = Instant.parse("2026-01-02T02:00:00Z")
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toLocalDate(),
        doseMG = 2.0,
        ester = Ester.E2,
        source = DoseEventSource.MANUAL,
        status = DoseEventStatus.RECORDED
    )

    private fun futurePlan(): MedicationPlan {
        val planId = UUID(0L, 21L)
        return MedicationPlan(
            id = planId,
            name = "A-02 future plan",
            route = Route.ORAL,
            ester = Ester.E2,
            doseMG = 2.0,
            scheduleType = ScheduleType.DAILY,
            slots = listOf(
                ScheduledDoseSlot(
                    id = UUID(0L, 22L),
                    planId = planId,
                    localTime = LocalTime.of(12, 0),
                    position = 0
                )
            ),
            daysOfWeek = emptySet(),
            intervalDays = 1,
            isEnabled = true,
            extras = emptyMap(),
            createdAt = Instant.EPOCH
        )
    }

    private class RecordingPkSimulationRunner : PkSimulationRunner {
        data class Call(
            val events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
            val bodyWeightKG: Double,
            val startTimeH: Double,
            val endTimeH: Double,
            val numberOfSteps: Int
        )

        val calls = mutableListOf<Call>()
        val results = mutableListOf<SimulationResult>()

        override fun run(
            events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
            bodyWeightKG: Double,
            startTimeH: Double,
            endTimeH: Double,
            numberOfSteps: Int,
            cancellationCheck: () -> Unit
        ): SimulationResult {
            calls += Call(events, bodyWeightKG, startTimeH, endTimeH, numberOfSteps)
            return SimulationResult(
                timeH = listOf(startTimeH, endTimeH),
                concPGmL = listOf(calls.size.toDouble(), calls.size.toDouble() + 1.0),
                auc = calls.size.toDouble() + 2.0
            ).also { results += it }
        }
    }
}
