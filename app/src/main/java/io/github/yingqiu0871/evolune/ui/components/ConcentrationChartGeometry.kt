package io.github.yingqiu0871.evolune.ui.components

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class ChartSeries(
    val timeH: List<Double>,
    val values: List<Double>
)

internal data class YAxisScale(
    val min: Double,
    val max: Double,
    val tickStep: Double
) {
    val tickCount: Int
        get() = (max / tickStep).roundToInt()

    fun tickValues(): List<Double> = (0..tickCount).map { index -> index * tickStep }
}

internal data class PlotRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)

    fun contains(x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() && x in left..right && y in top..bottom
}

internal data class PlotGeometry(
    val rect: PlotRect,
    val dataXMin: Double,
    val dataXMax: Double,
    val dataYMax: Double
) {
    fun dataXToScreen(value: Double): Float {
        val range = dataXMax - dataXMin
        if (!value.isFinite() || !range.isFinite() || range <= 0.0) return rect.left
        val fraction = ((value - dataXMin) / range)
            .takeIf(Double::isFinite)
            ?.coerceIn(-1_000_000.0, 1_000_000.0)
            ?: return rect.left
        return rect.left + rect.width * fraction.toFloat()
    }

    fun screenXToData(value: Float): Double {
        val range = dataXMax - dataXMin
        if (!value.isFinite() || !range.isFinite() || range <= 0.0) return dataXMin
        return dataXMin + ((value - rect.left) / rect.width) * range
    }

    fun dataYToScreen(value: Double): Float {
        if (!value.isFinite() || !dataYMax.isFinite() || dataYMax <= 0.0) return rect.bottom
        val fraction = (value / dataYMax)
            .takeIf(Double::isFinite)
            ?.coerceIn(-1_000_000.0, 1_000_000.0)
            ?: return rect.bottom
        return rect.bottom - rect.height * fraction.toFloat()
    }
}

internal fun calculatePlotGeometry(
    contentWidthPx: Float,
    contentHeightPx: Float,
    yAxisLabelWidthPx: Float,
    labelToAxisGapPx: Float,
    outerMarginPx: Float,
    topMarginPx: Float,
    xAxisGutterPx: Float,
    dataXMin: Double,
    dataXMax: Double,
    dataYMax: Double
): PlotGeometry {
    val width = contentWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = contentHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
    val requestedHorizontalInset = listOf(
        yAxisLabelWidthPx,
        labelToAxisGapPx,
        outerMarginPx
    ).sumOf { value -> value.takeIf { it.isFinite() && it > 0f }?.toDouble() ?: 0.0 }
        .toFloat()
    val horizontalInset = requestedHorizontalInset.coerceAtMost(width * 0.4f)
    val topInset = topMarginPx.takeIf { it.isFinite() && it > 0f }
        ?.coerceAtMost(height * 0.4f) ?: 0f
    val bottomInset = max(
        outerMarginPx.takeIf { it.isFinite() && it > 0f } ?: 0f,
        xAxisGutterPx.takeIf { it.isFinite() && it > 0f } ?: 0f
    ).coerceAtMost(height * 0.45f)

    return PlotGeometry(
        rect = PlotRect(
            left = horizontalInset,
            top = topInset,
            right = width - horizontalInset,
            bottom = height - bottomInset
        ),
        dataXMin = dataXMin.takeIf(Double::isFinite) ?: 0.0,
        dataXMax = dataXMax.takeIf(Double::isFinite) ?: 1.0,
        dataYMax = dataYMax.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    )
}

internal fun calculateVisibleWindowYScale(
    series: List<ChartSeries>,
    visibleStartH: Double,
    visibleEndH: Double
): YAxisScale {
    if (!visibleStartH.isFinite() || !visibleEndH.isFinite() || visibleEndH < visibleStartH) {
        return YAxisScale(min = 0.0, max = 1.0, tickStep = 0.2)
    }
    val visibleMax = series.asSequence()
        .flatMap { chartSeries ->
            visibleValues(chartSeries, visibleStartH, visibleEndH).asSequence()
        }
        .filter { it.isFinite() && it >= 0.0 }
        .maxOrNull()
        ?: 0.0

    if (visibleMax <= 0.0) {
        return YAxisScale(min = 0.0, max = 1.0, tickStep = 0.2)
    }

    val targetMax = if (visibleMax <= Double.MAX_VALUE / 1.1) visibleMax * 1.1 else visibleMax
    val exponent = floor(log10(visibleMax)).toInt().coerceIn(-300, 300)
    var bestMax = Double.POSITIVE_INFINITY
    var bestStep = Double.NaN
    var bestIntervals = 0
    val factors = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)

    for (candidateExponent in (exponent - 3)..(exponent + 2)) {
        val magnitude = 10.0.pow(candidateExponent)
        for (factor in factors) {
            val step = factor * magnitude
            if (!step.isFinite() || step <= 0.0) continue
            for (intervals in 4..6) {
                val axisMax = step * intervals
                if (axisMax.isFinite() && axisMax >= targetMax && axisMax < bestMax) {
                    bestMax = axisMax
                    bestStep = step
                    bestIntervals = intervals
                }
            }
        }
    }

    if (!bestMax.isFinite() || !bestStep.isFinite() || bestIntervals == 0) {
        val fallbackMax = targetMax.takeIf { it.isFinite() && it > 0.0 } ?: Double.MAX_VALUE
        return YAxisScale(min = 0.0, max = fallbackMax, tickStep = fallbackMax / 5.0)
    }
    return YAxisScale(min = 0.0, max = bestMax, tickStep = bestStep)
}

private fun visibleValues(
    series: ChartSeries,
    visibleStartH: Double,
    visibleEndH: Double
): List<Double> {
    val size = min(series.timeH.size, series.values.size)
    if (size == 0) return emptyList()
    val values = mutableListOf<Double>()
    for (index in 0 until size) {
        val time = series.timeH[index]
        val value = series.values[index]
        if (time.isFinite() && value.isFinite() && time in visibleStartH..visibleEndH) {
            values += value
        }
    }
    listOf(visibleStartH, visibleEndH).forEach { boundary ->
        interpolatedValueAt(series, size, boundary)?.let(values::add)
    }
    return values
}

private fun interpolatedValueAt(series: ChartSeries, size: Int, timeH: Double): Double? {
    for (index in 0 until size - 1) {
        val t0 = series.timeH[index]
        val t1 = series.timeH[index + 1]
        val y0 = series.values[index]
        val y1 = series.values[index + 1]
        if (!t0.isFinite() || !t1.isFinite() || !y0.isFinite() || !y1.isFinite()) continue
        if (t1 <= t0 || timeH !in t0..t1) continue
        val fraction = (timeH - t0) / (t1 - t0)
        return y0 + (y1 - y0) * fraction
    }
    return null
}
