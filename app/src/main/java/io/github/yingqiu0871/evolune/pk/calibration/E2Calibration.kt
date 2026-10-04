package io.github.yingqiu0871.evolune.pk.calibration

import io.github.yingqiu0871.evolune.pk.SimulationResult
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * PK 2.0 slice 1: personal E2 amplitude calibration against measured lab results.
 *
 * Pure computation. Since slice 4 the Home chart applies it when the user turns it on
 * (`E2CurveCalibrationCalculator`). Design, parameter sources and the provenance boundary are
 * in docs/evolune/pk2/PK2_CALIBRATION_PLAN.md.
 *
 * The production E2 model ([io.github.yingqiu0871.evolune.pk.SimulationEngine]) is never
 * modified: calibration is a separate multiplicative layer applied to its output, so the
 * uncalibrated curve, Widget, Wear and retrospective PK stay numerically identical.
 * A calibrated curve is still a model estimate, not a measured concentration.
 */
object E2CalibrationParameters {
    /** pmol/L per pg/mL for estradiol (molar mass 272.38 g/mol → 1000 / 272.38). */
    const val PMOL_PER_L_PER_PG_PER_ML: Double = 3.671

    /**
     * Log-space SD of a single lab measurement (assay + draw-timing scatter), ≈ ±13 %.
     * Reference value, see plan §3.2.
     */
    const val MEASUREMENT_LOG_SD: Double = 0.13

    /** Log-space SD of the population prior on the personal amplitude, ≈ ±65 %. Plan §3.2. */
    const val PRIOR_LOG_SD: Double = 0.5

    /**
     * Labs where the model predicts less than this (pg/mL) are not comparable — e.g. a
     * baseline draw before any recorded dose — and would produce an absurd ratio.
     */
    const val MIN_PREDICTED_PG_ML: Double = 1.0

    /** Hard safety bounds on the fitted scale; the prior already shrinks toward 1. */
    const val MIN_SCALE: Double = 0.25
    const val MAX_SCALE: Double = 4.0
}

/** Units an E2 lab result can be entered in. Testosterone units are out of scope. */
enum class E2LabUnit {
    PG_PER_ML,
    PMOL_PER_L;

    fun toPgMl(value: Double): Double = when (this) {
        PG_PER_ML -> value
        PMOL_PER_L -> value / E2CalibrationParameters.PMOL_PER_L_PER_PG_PER_ML
    }
}

/**
 * A measured serum estradiol value.
 *
 * @param timeH draw time in absolute hours since 1970, same clock as PK `DoseEvent.timeH`
 * @param value value in [unit]
 */
data class E2LabResult(
    val id: String,
    val timeH: Double,
    val value: Double,
    val unit: E2LabUnit
) {
    val valuePgMl: Double get() = unit.toPgMl(value)
}

/** One measured-vs-model comparison at a lab draw time. */
data class CalibrationPoint(
    val labId: String,
    val timeH: Double,
    val measuredPgMl: Double,
    val predictedPgMl: Double
) {
    /** How far the body runs above (> 1) or below (< 1) the population model. */
    val ratio: Double get() = measuredPgMl / predictedPgMl
}

/**
 * Fitted personal amplitude.
 *
 * @param scale calibrated E2 = model E2 × scale
 * @param labCount number of comparable labs used
 * @param fitErrorPct typical residual as ±% (log-space RMSE); null with fewer than 2 labs
 * @param points the comparable labs, ordered by time
 */
data class E2Calibration(
    val scale: Double,
    val labCount: Int,
    val fitErrorPct: Double?,
    val points: List<CalibrationPoint>
) {
    val isIdentity: Boolean get() = labCount == 0

    companion object {
        val NONE = E2Calibration(scale = 1.0, labCount = 0, fitErrorPct = null, points = emptyList())
    }
}

object E2Calibrator {

    /**
     * Pairs each lab with the uncalibrated model prediction at its draw time. Labs outside the
     * simulated range, with non-finite or non-positive values, or where the model predicts
     * less than [E2CalibrationParameters.MIN_PREDICTED_PG_ML] are dropped.
     */
    fun points(simulation: SimulationResult, labs: List<E2LabResult>): List<CalibrationPoint> {
        val times = simulation.timeH
        if (times.isEmpty() || times.size != simulation.concPGmL.size) return emptyList()
        val start = times.first()
        val end = times.last()
        return points(labs) { timeH ->
            if (timeH < start || timeH > end) null else simulation.concentration(timeH)
        }
    }

    /**
     * Same pairing rules as the [SimulationResult] overload, with the uncalibrated model
     * prediction supplied per draw time. [predictedPgMlAt] returns null when the model has no
     * value at that time.
     */
    fun points(
        labs: List<E2LabResult>,
        predictedPgMlAt: (timeH: Double) -> Double?
    ): List<CalibrationPoint> = labs.mapNotNull { lab ->
        val measured = lab.valuePgMl
        if (!lab.timeH.isFinite() || !measured.isFinite() || measured <= 0.0) return@mapNotNull null
        val predicted = predictedPgMlAt(lab.timeH) ?: return@mapNotNull null
        if (!predicted.isFinite() || predicted < E2CalibrationParameters.MIN_PREDICTED_PG_ML) {
            return@mapNotNull null
        }
        CalibrationPoint(lab.id, lab.timeH, measured, predicted)
    }.sortedBy { it.timeH }

    /**
     * Maximum a-posteriori log-amplitude under a Gaussian population prior centred on the
     * model (scale 1) and Gaussian log-space measurement noise. Closed form:
     * `a = Σ mᵢ/σ² / (n/σ² + 1/τ²)` with `mᵢ = ln(measuredᵢ / predictedᵢ)`.
     */
    fun fit(points: List<CalibrationPoint>): E2Calibration {
        if (points.isEmpty()) return E2Calibration.NONE
        val measurementPrecision = 1.0 / (E2CalibrationParameters.MEASUREMENT_LOG_SD *
            E2CalibrationParameters.MEASUREMENT_LOG_SD)
        val priorPrecision = 1.0 / (E2CalibrationParameters.PRIOR_LOG_SD *
            E2CalibrationParameters.PRIOR_LOG_SD)
        val logRatios = points.map { ln(it.ratio) }
        val logScale = logRatios.sum() * measurementPrecision /
            (points.size * measurementPrecision + priorPrecision)
        val scale = exp(logScale).coerceIn(E2CalibrationParameters.MIN_SCALE, E2CalibrationParameters.MAX_SCALE)
        val fitErrorPct = if (points.size < 2) {
            null
        } else {
            val fitted = ln(scale)
            val rmse = sqrt(logRatios.sumOf { (it - fitted) * (it - fitted) } / points.size)
            (exp(rmse) - 1.0) * 100.0
        }
        return E2Calibration(scale, points.size, fitErrorPct, points)
    }

    fun calibrate(simulation: SimulationResult, labs: List<E2LabResult>): E2Calibration =
        fit(points(simulation, labs))

    /** Scales concentrations and AUC; the time grid is unchanged. */
    fun apply(simulation: SimulationResult, calibration: E2Calibration): SimulationResult {
        if (calibration.isIdentity || calibration.scale == 1.0) return simulation
        return SimulationResult(
            timeH = simulation.timeH,
            concPGmL = simulation.concPGmL.map { it * calibration.scale },
            auc = simulation.auc * calibration.scale
        )
    }
}
