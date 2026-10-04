package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.pk.calibration.E2Calibration
import io.github.yingqiu0871.evolune.pk.calibration.E2Calibrator
import io.github.yingqiu0871.evolune.pk.calibration.toE2LabResultOrNull

/**
 * PK 2.0 slice 4: fits the personal E2 amplitude used by the Home chart when the user turns
 * calibration on.
 *
 * Each lab is compared with the uncalibrated model at its own draw time, evaluated from the
 * full recorded dose history, so labs older than the Home chart window still count. The
 * model is the unchanged [SimulationEngine]; only recorded doses are used (planned future
 * doses cannot affect a past draw). The result never feeds [PKState], Widget or Wear.
 */
internal object E2CurveCalibrationCalculator {
    /** Span of the two-step probe run; only its first sample (at the draw time) is read. */
    private const val PROBE_SPAN_H = 1.0

    fun calculate(
        historicalDoseEvents: List<DoseEvent>,
        labResults: List<LabResult>,
        bodyWeightKG: Double,
        cancellationCheck: () -> Unit = {}
    ): E2Calibration {
        val labs = labResults.mapNotNull { it.toE2LabResultOrNull() }
        if (labs.isEmpty()) return E2Calibration.NONE
        // Patch removal lookup takes the first later removal in list order, so keep time order.
        val events = recordedE2PkEvents(historicalDoseEvents).sortedBy { it.timeH }
        if (events.isEmpty()) return E2Calibration.NONE
        val points = E2Calibrator.points(labs) { timeH ->
            cancellationCheck()
            SimulationEngine(
                events = events,
                bodyWeightKG = bodyWeightKG,
                startTimeH = timeH,
                endTimeH = timeH + PROBE_SPAN_H,
                numberOfSteps = 2,
                cancellationCheck = cancellationCheck
            ).run().concPGmL.firstOrNull()
        }
        return E2Calibrator.fit(points)
    }
}
