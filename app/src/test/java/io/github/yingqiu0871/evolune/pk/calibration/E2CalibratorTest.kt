package io.github.yingqiu0871.evolune.pk.calibration

import io.github.yingqiu0871.evolune.pk.DoseEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.pk.SimulationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln

class E2CalibratorTest {

    private val epsilon = 1e-9

    /** Flat 100 pg/mL from t=0 to t=100. */
    private val flat = SimulationResult(
        timeH = listOf(0.0, 50.0, 100.0),
        concPGmL = listOf(100.0, 100.0, 100.0),
        auc = 10_000.0
    )

    private fun lab(id: String, timeH: Double, pgMl: Double) =
        E2LabResult(id, timeH, pgMl, E2LabUnit.PG_PER_ML)

    /** Expected MAP log-scale for identical log-ratios, from the documented closed form. */
    private fun shrunkScale(ratio: Double, n: Int): Double {
        val measurementPrecision = 1.0 / (E2CalibrationParameters.MEASUREMENT_LOG_SD *
            E2CalibrationParameters.MEASUREMENT_LOG_SD)
        val priorPrecision = 1.0 / (E2CalibrationParameters.PRIOR_LOG_SD *
            E2CalibrationParameters.PRIOR_LOG_SD)
        return exp(n * ln(ratio) * measurementPrecision / (n * measurementPrecision + priorPrecision))
    }

    @Test
    fun `pmol per L converts to pg per mL with the estradiol molar factor`() {
        assertEquals(100.0, E2LabUnit.PMOL_PER_L.toPgMl(367.1), 1e-9)
        assertEquals(42.0, E2LabUnit.PG_PER_ML.toPgMl(42.0), 0.0)
        assertEquals(100.0, E2LabResult("a", 10.0, 367.1, E2LabUnit.PMOL_PER_L).valuePgMl, 1e-9)
    }

    @Test
    fun `no labs gives the identity calibration and apply returns the same simulation`() {
        val calibration = E2Calibrator.calibrate(flat, emptyList())
        assertSame(E2Calibration.NONE, calibration)
        assertTrue(calibration.isIdentity)
        assertSame(flat, E2Calibrator.apply(flat, calibration))
    }

    @Test
    fun `a single lab is shrunk slightly toward the model and has no fit error`() {
        val calibration = E2Calibrator.calibrate(flat, listOf(lab("a", 50.0, 200.0)))
        assertEquals(1, calibration.labCount)
        assertEquals(shrunkScale(2.0, 1), calibration.scale, epsilon)
        assertTrue(calibration.scale < 2.0 && calibration.scale > 1.8)
        assertNull(calibration.fitErrorPct)
    }

    @Test
    fun `more consistent labs pull the scale closer to the observed ratio`() {
        val one = E2Calibrator.calibrate(flat, listOf(lab("a", 10.0, 150.0)))
        val four = E2Calibrator.calibrate(
            flat,
            listOf(lab("a", 10.0, 150.0), lab("b", 30.0, 150.0), lab("c", 60.0, 150.0), lab("d", 90.0, 150.0))
        )
        assertEquals(shrunkScale(1.5, 4), four.scale, epsilon)
        assertTrue(four.scale > one.scale)
        assertTrue(four.scale < 1.5)
        assertTrue(four.fitErrorPct!! < 2.0)
    }

    @Test
    fun `ratios are averaged in log space so reciprocal labs cancel out`() {
        val calibration = E2Calibrator.calibrate(flat, listOf(lab("a", 20.0, 200.0), lab("b", 80.0, 50.0)))
        assertEquals(1.0, calibration.scale, epsilon)
        assertEquals(100.0, calibration.fitErrorPct!!, 1e-6)
    }

    @Test
    fun `scale is bounded by the hard safety limits`() {
        val high = E2Calibrator.calibrate(flat, listOf(lab("a", 50.0, 100_000.0)))
        val low = E2Calibrator.calibrate(flat, listOf(lab("a", 50.0, 0.1)))
        assertEquals(E2CalibrationParameters.MAX_SCALE, high.scale, 0.0)
        assertEquals(E2CalibrationParameters.MIN_SCALE, low.scale, 0.0)
    }

    @Test
    fun `labs outside the range, invalid values and near-zero predictions are excluded`() {
        val withBaseline = SimulationResult(
            timeH = listOf(0.0, 10.0, 20.0),
            concPGmL = listOf(0.0, 0.5, 80.0),
            auc = 0.0
        )
        val points = E2Calibrator.points(
            withBaseline,
            listOf(
                lab("before", -1.0, 80.0),
                lab("after", 21.0, 80.0),
                lab("baseline", 5.0, 30.0),
                lab("zero", 20.0, 0.0),
                lab("negative", 20.0, -5.0),
                lab("nan", 20.0, Double.NaN),
                lab("nanTime", Double.NaN, 80.0),
                lab("ok", 20.0, 120.0)
            )
        )
        assertEquals(listOf("ok"), points.map { it.labId })
        assertEquals(1.5, points.single().ratio, epsilon)
    }

    @Test
    fun `points are ordered by draw time and compare against the interpolated model`() {
        val ramp = SimulationResult(
            timeH = listOf(0.0, 10.0),
            concPGmL = listOf(10.0, 110.0),
            auc = 0.0
        )
        val points = E2Calibrator.points(ramp, listOf(lab("late", 7.5, 85.0), lab("early", 2.5, 70.0)))
        assertEquals(listOf("early", "late"), points.map { it.labId })
        assertEquals(35.0, points[0].predictedPgMl, epsilon)
        assertEquals(2.0, points[0].ratio, epsilon)
        assertEquals(85.0, points[1].predictedPgMl, epsilon)
    }

    @Test
    fun `apply scales concentrations and AUC without touching the time grid or the input`() {
        val calibration = E2Calibrator.calibrate(flat, listOf(lab("a", 50.0, 200.0)))
        val calibrated = E2Calibrator.apply(flat, calibration)
        assertSame(flat.timeH, calibrated.timeH)
        calibrated.concPGmL.forEach { assertEquals(100.0 * calibration.scale, it, epsilon) }
        assertEquals(10_000.0 * calibration.scale, calibrated.auc, 1e-6)
        assertEquals(listOf(100.0, 100.0, 100.0), flat.concPGmL)
    }

    @Test
    fun `calibrating against the production engine recovers a doubled amplitude`() {
        val raw = SimulationEngine(
            events = listOf(DoseEvent(route = Route.ORAL, timeH = 100.0, doseMG = 2.0, ester = Ester.E2)),
            bodyWeightKG = 60.0,
            startTimeH = 76.0,
            endTimeH = 196.0,
            numberOfSteps = 1_441
        ).run()
        val labs = listOf(102.0, 104.0, 108.0).mapIndexed { i, t ->
            lab("lab$i", t, raw.concentration(t)!! * 2.0)
        }

        val calibration = E2Calibrator.calibrate(raw, labs)

        assertEquals(3, calibration.labCount)
        assertEquals(shrunkScale(2.0, 3), calibration.scale, 1e-9)
        // Consistent labs: the only residual is the prior shrinkage of the fitted scale.
        assertEquals((2.0 / calibration.scale - 1.0) * 100.0, calibration.fitErrorPct!!, 1e-6)
        val calibrated = E2Calibrator.apply(raw, calibration)
        assertEquals(raw.concentration(106.0)!! * calibration.scale, calibrated.concentration(106.0)!!, 1e-9)
    }
}
