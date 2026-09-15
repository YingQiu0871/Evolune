package io.github.yingqiu0871.evolune.ui.screens.retrospective

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSeries
import io.github.yingqiu0871.evolune.history.retrospective.RecordedIntakeMarker
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveMarker
import io.github.yingqiu0871.evolune.history.retrospective.ScheduleContextMarker
import java.time.Duration
import java.time.Instant

/**
 * V17-C-04 §5/§D-7 — thin retrospective-only chart.
 *
 * This component intentionally does NOT reuse the live `ConcentrationChart`: it renders only the
 * approved [RetrospectivePkSeries] over the frozen visible window, with no live "now" line, no
 * fork point, no baseline/current simulation, no future prediction and no 36-hour viewport.
 *
 * Value discipline (contract §5): point concentrations are passed to the affine screen mapping
 * exactly as supplied — no clamp, no normalization, no dropping of small floating-point
 * cancellation values in `[-1e-9, 0)`. Markers are vertical presentation overlays only and never
 * alter a curve value.
 */

private const val NANOS_PER_HOUR = 3_600_000_000_000.0

/** One plotted point: hour offset from the series start (the frozen visible window) + raw concentration. */
internal data class RetrospectiveChartPoint(
    val hourOffset: Double,
    val concentrationPGmL: Double
)

/** Exact series → chart mapping: values are copied unchanged (no clamp / normalization). */
internal fun retrospectiveChartPoints(series: RetrospectivePkSeries): List<RetrospectiveChartPoint> =
    series.points.map { point ->
        RetrospectiveChartPoint(
            hourOffset = Duration.between(series.startInclusive, point.instant).toNanos() / NANOS_PER_HOUR,
            concentrationPGmL = point.concentrationPGmL
        )
    }

/** Viewport width in hours: exactly the frozen visible window (`series.startInclusive..endInclusive`). */
internal fun retrospectiveChartTotalHours(series: RetrospectivePkSeries): Double =
    Duration.between(series.startInclusive, series.endInclusive).toNanos() / NANOS_PER_HOUR

/** y-axis top from the raw series extrema; a fallback of 1.0 applies only when nothing is positive. */
internal fun retrospectiveChartYMax(points: List<RetrospectiveChartPoint>): Double =
    points.asSequence()
        .map { it.concentrationPGmL }
        .filter { it.isFinite() }
        .maxOrNull()
        ?.takeIf { it > 0.0 }
        ?: 1.0

/** Hour offset of a marker instant relative to the series start (may fall outside `[0, totalHours]`). */
internal fun markerHourOffset(instant: Instant, series: RetrospectivePkSeries): Double =
    Duration.between(series.startInclusive, instant).toNanos() / NANOS_PER_HOUR

@Composable
internal fun retrospectiveCurveColor(): Color = MaterialTheme.colorScheme.primary

@Composable
internal fun retrospectiveScheduleMarkerColor(): Color = MaterialTheme.colorScheme.tertiary

@Composable
internal fun retrospectiveIntakeMarkerColor(): Color = MaterialTheme.colorScheme.secondary

@Composable
internal fun RetrospectiveConcentrationChart(
    series: RetrospectivePkSeries,
    markers: List<RetrospectiveMarker>,
    modifier: Modifier = Modifier
) {
    val points = remember(series) { retrospectiveChartPoints(series) }
    val totalHours = remember(series) { retrospectiveChartTotalHours(series) }
    val yMax = remember(points) { retrospectiveChartYMax(points) }
    val scheduleHours = remember(series, markers) {
        markers.filterIsInstance<ScheduleContextMarker>().mapNotNull { marker ->
            markerHourOffset(marker.scheduledAt, series)
                .takeIf { it.isFinite() && it >= 0.0 && it <= totalHours }
        }
    }
    val intakeHours = remember(series, markers) {
        markers.filterIsInstance<RecordedIntakeMarker>().mapNotNull { marker ->
            markerHourOffset(marker.occurredAt, series)
                .takeIf { it.isFinite() && it >= 0.0 && it <= totalHours }
        }
    }

    val curveColor = retrospectiveCurveColor()
    val scheduleColor = retrospectiveScheduleMarkerColor()
    val intakeColor = retrospectiveIntakeMarkerColor()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val chartDescription = stringResource(R.string.retrospective_disclosure)

    Canvas(
        modifier = modifier.semantics { contentDescription = chartDescription }
    ) {
        if (!totalHours.isFinite() || totalHours <= 0.0 || points.isEmpty()) return@Canvas

        val left = 8f
        val right = size.width - 8f
        val top = 8f
        val bottom = size.height - 8f
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return@Canvas

        fun xForHour(hour: Double): Float = left + width * (hour / totalHours).toFloat()
        fun yForValue(value: Double): Float = bottom - height * (value / yMax).toFloat()

        // Light grid at quarter positions (presentation only; no data meaning).
        for (index in 0..4) {
            val x = left + width * index / 4f
            drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
            val y = top + height * index / 4f
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
        }

        // The single continuous retrospective model-estimate curve: raw series values only.
        val path = Path()
        var started = false
        points.forEach { point ->
            val x = xForHour(point.hourOffset)
            val y = yForValue(point.concentrationPGmL)
            if (!x.isFinite() || !y.isFinite()) return@forEach
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        if (started) {
            drawPath(path, curveColor, style = Stroke(width = 2f))
        }

        // Marker overlays: current-schedule context and recorded intake, presentation only.
        scheduleHours.forEach { hour ->
            val x = xForHour(hour)
            if (x.isFinite()) drawLine(scheduleColor, Offset(x, top), Offset(x, bottom), strokeWidth = 2f)
        }
        intakeHours.forEach { hour ->
            val x = xForHour(hour)
            if (x.isFinite()) drawLine(intakeColor, Offset(x, top), Offset(x, bottom), strokeWidth = 2f)
        }
    }
}
