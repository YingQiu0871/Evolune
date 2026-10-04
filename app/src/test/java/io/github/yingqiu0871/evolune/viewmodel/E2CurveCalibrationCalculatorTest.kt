package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.core.adapter.DomainDoseEventToPkAdapter
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.pk.calibration.E2Calibration
import io.github.yingqiu0871.evolune.pk.calibration.E2CalibrationParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class E2CurveCalibrationCalculatorTest {
    @Test
    fun `model value at the draw equals a fine grid run of the same history`() {
        val events = injections()
        val drawHour = timeH(events[1].occurredAt) + 48.0
        val draw = Instant.ofEpochMilli((drawHour * MILLIS_PER_HOUR).toLong())
        val grid = SimulationEngine(
            events = DomainDoseEventToPkAdapter.adapt(events),
            bodyWeightKG = WEIGHT,
            startTimeH = drawHour - 10.0,
            endTimeH = drawHour + 10.0,
            numberOfSteps = 21
        ).run()

        val calibration = E2CurveCalibrationCalculator.calculate(
            historicalDoseEvents = events.reversed(),
            labResults = listOf(LabResult(LAB_ID, draw, 300.0, LabUnit.PG_PER_ML)),
            bodyWeightKG = WEIGHT
        )

        val point = calibration.points.single()
        assertEquals(grid.concPGmL[10], point.predictedPgMl, 1e-9)
        assertEquals(300.0, point.measuredPgMl, 0.0)
    }

    @Test
    fun `pmol per litre labs are converted and testosterone labs are ignored`() {
        val events = injections()
        val draw = events[1].occurredAt.plusSeconds(48 * 3600L)
        val pg = E2CurveCalibrationCalculator.calculate(
            events,
            listOf(LabResult(LAB_ID, draw, 200.0, LabUnit.PG_PER_ML)),
            WEIGHT
        )
        val pmol = E2CurveCalibrationCalculator.calculate(
            events,
            listOf(
                LabResult(
                    LAB_ID,
                    draw,
                    200.0 * E2CalibrationParameters.PMOL_PER_L_PER_PG_PER_ML,
                    LabUnit.PMOL_PER_L
                ),
                LabResult(UUID.randomUUID(), draw, 30.0, LabUnit.NG_PER_DL)
            ),
            WEIGHT
        )

        assertEquals(1, pmol.labCount)
        assertEquals(pg.scale, pmol.scale, 1e-9)
    }

    @Test
    fun `no labs, no doses, or a draw before the first dose leave the curve uncalibrated`() {
        val events = injections()
        val beforeFirstDose = events.first().occurredAt.minusSeconds(86_400L)

        assertEquals(E2Calibration.NONE, E2CurveCalibrationCalculator.calculate(events, emptyList(), WEIGHT))
        assertEquals(
            E2Calibration.NONE,
            E2CurveCalibrationCalculator.calculate(
                emptyList(),
                listOf(LabResult(LAB_ID, beforeFirstDose, 100.0, LabUnit.PG_PER_ML)),
                WEIGHT
            )
        )
        assertTrue(
            E2CurveCalibrationCalculator.calculate(
                events,
                listOf(LabResult(LAB_ID, beforeFirstDose, 100.0, LabUnit.PG_PER_ML)),
                WEIGHT
            ).isIdentity
        )
    }

    private fun injections(): List<DoseEvent> = (0 until 4).map { week ->
        DoseEvent(
            id = UUID(0L, week + 1L),
            route = Route.INJECTION,
            occurredAt = FIRST_DOSE.plusSeconds(week * 7 * 86_400L),
            doseMG = 5.0,
            ester = Ester.EV,
            source = DoseEventSource.MANUAL
        )
    }

    private fun timeH(instant: Instant): Double = instant.toEpochMilli() / MILLIS_PER_HOUR

    private companion object {
        const val WEIGHT = 60.0
        const val MILLIS_PER_HOUR = 3_600_000.0
        val FIRST_DOSE: Instant = Instant.parse("2025-03-01T08:00:00Z")
        val LAB_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
    }
}
