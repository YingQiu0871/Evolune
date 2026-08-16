package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.core.adapter.DomainDoseEventToPkAdapter
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class PkSimulationCalculatorTest {
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
    }
}
