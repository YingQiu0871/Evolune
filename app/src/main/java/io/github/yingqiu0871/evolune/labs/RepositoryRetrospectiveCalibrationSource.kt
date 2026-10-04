package io.github.yingqiu0871.evolune.labs

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.LabResultRepository
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveCalibrationReading
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveCalibrationSource
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveLabPoint
import io.github.yingqiu0871.evolune.viewmodel.E2CurveCalibrationCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * PK 2.0 slice 5a — composition-root implementation of [RetrospectiveCalibrationSource].
 *
 * Fits with the same inputs and calculator as the Home chart (all dose events, all labs), so
 * Home and the retrospective surface always show the same factor.
 */
internal class RepositoryRetrospectiveCalibrationSource(
    private val doseEvents: DoseEventRepository,
    private val labResults: LabResultRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : RetrospectiveCalibrationSource {
    override suspend fun read(bodyWeightKg: Double): RetrospectiveCalibrationReading {
        val events = doseEvents.observeAll().first()
        val labs = labResults.observeAll().first()
        val ownerContext = currentCoroutineContext()
        val calibration = withContext(dispatcher) {
            E2CurveCalibrationCalculator.calculate(
                historicalDoseEvents = events,
                labResults = labs,
                bodyWeightKG = bodyWeightKg,
                cancellationCheck = { ownerContext.ensureActive() }
            )
        }
        val measuredAtById = labs.associate { it.id.toString() to it.measuredAt }
        return RetrospectiveCalibrationReading(
            scale = calibration.scale,
            labCount = calibration.labCount,
            fitErrorPct = calibration.fitErrorPct,
            labPoints = calibration.points.mapNotNull { point ->
                measuredAtById[point.labId]?.let { RetrospectiveLabPoint(it, point.measuredPgMl) }
            }
        )
    }
}
