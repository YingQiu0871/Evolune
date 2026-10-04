package io.github.yingqiu0871.evolune.history.retrospective

import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkPoint
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSeries
import java.time.Instant

/**
 * PK 2.0 slice 5a — optional lab calibration of the retrospective surface.
 *
 * A presentation overlay only: the approved C-01 [RetrospectivePkSeries] and its
 * `RetrospectivePkResult` are never modified. These models carry no `pk` types so the C-04
 * surface keeps its seam boundary; the fit itself happens behind
 * [RetrospectiveCalibrationSource] at the composition root.
 */

/** A measured E2 lab value, converted to pg/mL. */
data class RetrospectiveLabPoint(
    val measuredAt: Instant,
    val valuePgMl: Double
)

/**
 * The personal amplitude fitted from every comparable E2 lab. [labCount] 0 means calibration
 * is on but no lab could be compared, so [scale] is 1.
 */
data class RetrospectiveCalibrationReading(
    val scale: Double,
    val labCount: Int,
    val fitErrorPct: Double?,
    val labPoints: List<RetrospectiveLabPoint>
)

/** Reads the current calibration; only called while the user has calibration turned on. */
fun interface RetrospectiveCalibrationSource {
    suspend fun read(bodyWeightKg: Double): RetrospectiveCalibrationReading
}

/**
 * What the CONTENT surface shows when calibration is on: the reading, the approved series
 * scaled by it, and the labs that fall inside the visible window.
 */
data class RetrospectiveCalibration(
    val reading: RetrospectiveCalibrationReading,
    val calibratedSeries: RetrospectivePkSeries,
    val visibleLabPoints: List<RetrospectiveLabPoint>
) {
    companion object {
        fun of(
            reading: RetrospectiveCalibrationReading,
            series: RetrospectivePkSeries
        ): RetrospectiveCalibration {
            val calibratedSeries = if (reading.labCount == 0 || reading.scale == 1.0) {
                series
            } else {
                series.copy(
                    points = series.points.map { point ->
                        RetrospectivePkPoint(point.instant, point.concentrationPGmL * reading.scale)
                    }
                )
            }
            val visible = reading.labPoints.filter { lab ->
                !lab.measuredAt.isBefore(series.startInclusive) &&
                    !lab.measuredAt.isAfter(series.endInclusive)
            }
            return RetrospectiveCalibration(reading, calibratedSeries, visible)
        }
    }
}
