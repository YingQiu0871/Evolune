package io.github.yingqiu0871.evolune.ui.screens.retrospective

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSeries
import io.github.yingqiu0871.evolune.history.retrospective.RecordedIntakeMarker
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveMarker
import io.github.yingqiu0871.evolune.history.retrospective.ScheduleContextMarker
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * V17-C-04 §5/§D-7 — thin retrospective-only chart.
 *
 * This component intentionally does NOT reuse the live `ConcentrationChart`: it renders only the
 * approved [RetrospectivePkSeries] over the frozen visible window, with no live "now" line, no
 * fork point, no baseline/current simulation, no future prediction and no 36-hour viewport.
 *
 * Value discipline (contract §5): point concentrations are passed to the affine screen mapping
 * exactly as supplied — no clamp, no normalization, no dropping of small floating-point
 * cancellation values in `[-1e-9, 0)`. Markers are presentation overlays only and never alter a
 * curve value.
 *
 * v1.7.1 UI hotfix (view-only rendering changes, no calculation change):
 * - the drawn curve uses a deterministic decimation that preserves the first/last point and every
 *   bucket's extrema, so 720h/2160h series stay readable and bounded without changing any value;
 * - the y axis still derives from the RAW series extrema (a decimated peak is never clipped);
 * - markers became small baseline ticks/dots; intake dots stay slightly more prominent than the
 *   subdued schedule ticks;
 * - about five to seven whole-day x-axis labels are drawn under the plot; the grid keeps
 *   horizontal lines only;
 * - a tap/drag inspection reads the nearest drawn point (view-only, transient, never persisted)
 *   and shows its timestamp + model-estimate concentration.
 */

private const val NANOS_PER_HOUR = 3_600_000_000_000.0

/** Upper bound of drawn curve points; the raw series is never modified. */
internal const val RETROSPECTIVE_CHART_MAX_POINTS = 720

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

/**
 * Deterministic view-only decimation: the first and last points are always kept, the interior is
 * split into equal buckets and each bucket contributes its minimum and maximum (in hour order).
 * The result never exceeds [maxPoints] and never rewrites a value.
 */
internal fun retrospectiveChartDecimate(
    points: List<RetrospectiveChartPoint>,
    maxPoints: Int
): List<RetrospectiveChartPoint> {
    if (maxPoints < 3) return emptyList()
    if (points.size <= maxPoints) return points
    val bucketCount = (maxPoints - 2) / 2
    if (bucketCount <= 0) return points
    val interiorSize = points.size - 2
    val result = ArrayList<RetrospectiveChartPoint>(maxPoints)
    result += points.first()
    val baseBucketSize = interiorSize / bucketCount
    val largerBuckets = interiorSize % bucketCount
    var index = 1
    for (bucket in 0 until bucketCount) {
        val bucketSize = baseBucketSize + if (bucket < largerBuckets) 1 else 0
        if (bucketSize <= 0) continue
        val bucketEnd = index + bucketSize
        var minIndex = index
        var maxIndex = index
        var cursor = index
        while (cursor < bucketEnd) {
            if (points[cursor].concentrationPGmL < points[minIndex].concentrationPGmL) minIndex = cursor
            if (points[cursor].concentrationPGmL > points[maxIndex].concentrationPGmL) maxIndex = cursor
            cursor += 1
        }
        when {
            minIndex == maxIndex -> result += points[minIndex]
            minIndex < maxIndex -> {
                result += points[minIndex]
                result += points[maxIndex]
            }

            else -> {
                result += points[maxIndex]
                result += points[minIndex]
            }
        }
        index = bucketEnd
    }
    result += points.last()
    return result
}

/**
 * About five to seven whole-day x-axis label offsets over `0..totalHours`, snapped to day
 * boundaries; the window end is always included as the final label.
 */
internal fun retrospectiveChartLabelHourOffsets(totalHours: Double): List<Double> {
    if (!totalHours.isFinite() || totalHours <= 0.0) return emptyList()
    val targetCount = 6
    val stepDays = ceil(totalHours / 24.0 / (targetCount - 1)).toInt().coerceAtLeast(1)
    val offsets = mutableListOf<Double>()
    var day = 0
    while (offsets.size < targetCount) {
        val offset = day * 24.0
        if (offset > totalHours) break
        offsets += offset
        day += stepDays
    }
    if (offsets.isNotEmpty() && offsets.last() != totalHours && offsets.size < targetCount + 1) {
        offsets += totalHours
    }
    return offsets
}

/** Index of the drawn point nearest to [hourOffset]; -1 for an empty list. */
internal fun retrospectiveNearestPointIndex(
    points: List<RetrospectiveChartPoint>,
    hourOffset: Double
): Int {
    if (points.isEmpty()) return -1
    var bestIndex = 0
    var bestDistance = abs(points[0].hourOffset - hourOffset)
    for (index in points.indices) {
        val distance = abs(points[index].hourOffset - hourOffset)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}

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
    displayZone: ZoneId,
    is24Hour: Boolean,
    modifier: Modifier = Modifier
) {
    val points = remember(series) { retrospectiveChartPoints(series) }
    val drawnPoints = remember(points) {
        retrospectiveChartDecimate(points, RETROSPECTIVE_CHART_MAX_POINTS)
    }
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
    val inspectionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBackground = MaterialTheme.colorScheme.surfaceVariant
    val tooltipTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = tooltipTextColor)
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(color = tooltipTextColor)
    val chartDescription = stringResource(R.string.retrospective_disclosure)

    val textMeasurer = rememberTextMeasurer()
    val labelHours = remember(totalHours) { retrospectiveChartLabelHourOffsets(totalHours) }
    val labelFormatter = remember(displayZone) {
        DateTimeFormatter.ofPattern("M/d", Locale.getDefault()).withZone(displayZone)
    }
    val tooltipFormatter = remember(displayZone, is24Hour) {
        val pattern = if (is24Hour) "M/d HH:mm" else "M/d h:mm a"
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(displayZone)
    }
    var inspectedIndex by remember(drawnPoints) { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = chartDescription }
            .pointerInput(drawnPoints, totalHours) {
                detectTapGestures { position ->
                    inspectedIndex = if (!totalHours.isFinite() || totalHours <= 0.0) {
                        -1
                    } else {
                        val left = 8f
                        val right = size.width - 8f
                        val fraction = ((position.x - left) / (right - left)).coerceIn(0f, 1f)
                        retrospectiveNearestPointIndex(drawnPoints, fraction * totalHours)
                    }
                }
            }
            .pointerInput(drawnPoints, totalHours) {
                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        if (totalHours.isFinite() && totalHours > 0.0) {
                            val left = 8f
                            val right = size.width - 8f
                            val fraction = ((position.x - left) / (right - left)).coerceIn(0f, 1f)
                            inspectedIndex = retrospectiveNearestPointIndex(drawnPoints, fraction * totalHours)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (totalHours.isFinite() && totalHours > 0.0) {
                            val left = 8f
                            val right = size.width - 8f
                            val fraction = ((change.position.x - left) / (right - left)).coerceIn(0f, 1f)
                            inspectedIndex = retrospectiveNearestPointIndex(drawnPoints, fraction * totalHours)
                        }
                    }
                )
            }
    ) {
        if (!totalHours.isFinite() || totalHours <= 0.0 || drawnPoints.isEmpty()) return@Canvas

        val labelArea = 16.dp.toPx()
        val left = 8f
        val right = size.width - 8f
        val top = 8f
        val bottom = size.height - 8f - labelArea
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return@Canvas

        fun xForHour(hour: Double): Float = left + width * (hour / totalHours).toFloat()
        fun yForValue(value: Double): Float = bottom - height * (value / yMax).toFloat()

        // Horizontal grid only (presentation only; no data meaning).
        for (index in 0..4) {
            val y = top + height * index / 4f
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
        }

        // The single continuous retrospective model-estimate curve: raw (decimated) values only.
        val path = Path()
        var started = false
        drawnPoints.forEach { point ->
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

        // Marker overlays: small baseline ticks/dots (presentation only, subordinate to the curve).
        scheduleHours.forEach { hour ->
            val x = xForHour(hour)
            if (x.isFinite()) {
                drawLine(scheduleColor, Offset(x, bottom), Offset(x, bottom - 8f), strokeWidth = 2f)
            }
        }
        intakeHours.forEach { hour ->
            val x = xForHour(hour)
            if (x.isFinite()) {
                drawCircle(intakeColor, radius = 3.5f, center = Offset(x, bottom - 6f))
            }
        }

        // Adaptive whole-day x-axis labels.
        labelHours.forEach { hour ->
            val x = xForHour(hour)
            if (!x.isFinite()) return@forEach
            val instant = series.startInclusive.plusSeconds((hour * 3600.0).roundToLong())
            val layout = textMeasurer.measure(
                text = AnnotatedString(labelFormatter.format(instant)),
                style = labelStyle
            )
            val xPosition = (x - layout.size.width / 2f)
                .coerceIn(2f, (size.width - layout.size.width - 2f).coerceAtLeast(2f))
            drawText(layout, topLeft = Offset(xPosition, bottom + 4f))
        }

        // Transient tap/drag inspection overlay: nearest drawn point only, never persisted.
        val point = drawnPoints.getOrNull(inspectedIndex)
        if (point != null) {
            val x = xForHour(point.hourOffset)
            val y = yForValue(point.concentrationPGmL)
            if (x.isFinite() && y.isFinite()) {
                drawLine(inspectionColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5f)
                drawCircle(inspectionColor, radius = 4f, center = Offset(x, y))
                val instant = series.startInclusive.plusSeconds((point.hourOffset * 3600.0).roundToLong())
                val text = tooltipFormatter.format(instant) + "  ·  " +
                    String.format(Locale.US, "%.1f", point.concentrationPGmL) + " pg/mL"
                val layout: TextLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = tooltipStyle
                )
                val xPosition = (x - layout.size.width / 2f)
                    .coerceIn(2f, (size.width - layout.size.width - 2f).coerceAtLeast(2f))
                drawRoundRect(
                    color = tooltipBackground,
                    topLeft = Offset(xPosition - 6f, top - 2f),
                    size = Size(layout.size.width + 12f, layout.size.height + 8f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawText(layout, topLeft = Offset(xPosition, top + 2f))
            }
        }
    }
}
