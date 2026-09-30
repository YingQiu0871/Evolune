package io.github.yingqiu0871.evolune.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

class ConcentrationChartGeometryTest {
    @Test
    fun `representative visible maxima select readable stable scales`() {
        val expected = mapOf(
            0.0 to 1.0,
            7.0 to 8.0,
            42.0 to 50.0,
            97.0 to 120.0,
            225.0 to 250.0,
            301.0 to 400.0,
            999.0 to 1_200.0,
            1_000_000.0 to 1_200_000.0
        )

        expected.forEach { (visibleMax, expectedAxisMax) ->
            val scale = scaleFor(visibleMax)
            assertEquals(0.0, scale.min, 0.0)
            assertEquals(expectedAxisMax, scale.max, expectedAxisMax * 1e-12)
            assertTrue(scale.max >= visibleMax)
            assertTrue(scale.max <= maxOf(1.0, visibleMax * 1.35))
            assertTrue(isNiceStep(scale.tickStep))
            assertTrue(scale.tickCount in 4..6 || visibleMax == 0.0)
        }
    }

    @Test
    fun `off screen peaks do not inflate visible scale`() {
        val scale = calculateVisibleWindowYScale(
            series = listOf(
                ChartSeries(
                    timeH = listOf(0.0, 1.0, 2.0, 10.0),
                    values = listOf(90.0, 180.0, 225.0, 10_000.0)
                )
            ),
            visibleStartH = 0.0,
            visibleEndH = 2.0
        )

        assertEquals(250.0, scale.max, 0.0)
    }

    @Test
    fun `history forecast and invalid values are combined safely`() {
        val scale = calculateVisibleWindowYScale(
            series = listOf(
                ChartSeries(listOf(0.0, 1.0), listOf(30.0, Double.NaN)),
                ChartSeries(listOf(0.0, 1.0, 2.0), listOf(40.0, 97.0, Double.POSITIVE_INFINITY))
            ),
            visibleStartH = 0.0,
            visibleEndH = 1.0
        )

        assertEquals(120.0, scale.max, 0.0)
        assertTrue(scale.tickValues().all(Double::isFinite))
    }

    @Test
    fun `history only forecast only and mixed windows include every visible series`() {
        val history = ChartSeries(listOf(0.0, 1.0), listOf(30.0, 42.0))
        val forecast = ChartSeries(listOf(0.0, 1.0), listOf(80.0, 97.0))

        assertEquals(50.0, calculateVisibleWindowYScale(listOf(history), 0.0, 1.0).max, 0.0)
        assertEquals(120.0, calculateVisibleWindowYScale(listOf(forecast), 0.0, 1.0).max, 0.0)
        assertEquals(
            120.0,
            calculateVisibleWindowYScale(listOf(history, forecast), 0.0, 1.0).max,
            0.0
        )
    }

    @Test
    fun `nearby visible ranges stay on the same deterministic boundary`() {
        val maxima = listOf(224.0, 225.0, 226.0).map { scaleFor(it).max }
        assertEquals(listOf(250.0, 250.0, 250.0), maxima)
    }

    @Test
    fun `empty zero tiny and malformed windows remain finite`() {
        val empty = calculateVisibleWindowYScale(emptyList(), 0.0, 1.0)
        val zero = scaleFor(0.0)
        val tiny = scaleFor(0.004)
        val malformed = calculateVisibleWindowYScale(emptyList(), Double.NaN, 1.0)

        listOf(empty, zero, tiny, malformed).forEach { scale ->
            assertTrue(scale.max.isFinite() && scale.max > 0.0)
            assertTrue(scale.tickStep.isFinite() && scale.tickStep > 0.0)
        }
        assertTrue(tiny.max >= 0.004)
    }

    @Test
    fun `plot geometry is centered and coordinate transforms invert`() {
        val geometry = calculatePlotGeometry(
            contentWidthPx = 400f,
            contentHeightPx = 240f,
            yAxisLabelWidthPx = 32f,
            labelToAxisGapPx = 8f,
            outerMarginPx = 8f,
            topMarginPx = 16f,
            xAxisGutterPx = 48f,
            dataXMin = 10.0,
            dataXMax = 20.0,
            dataYMax = 250.0
        )

        assertEquals(geometry.rect.left, 400f - geometry.rect.right, 0f)
        assertEquals(geometry.rect.left, geometry.dataXToScreen(10.0), 0f)
        assertEquals(geometry.rect.right, geometry.dataXToScreen(20.0), 0f)
        assertEquals(geometry.rect.bottom, geometry.dataYToScreen(0.0), 0f)
        assertEquals(geometry.rect.top, geometry.dataYToScreen(250.0), 0.001f)
        assertEquals(15.0, geometry.screenXToData(geometry.dataXToScreen(15.0)), 1e-6)
    }

    @Test
    fun `invalid coordinate inputs never produce non finite screen positions`() {
        val geometry = calculatePlotGeometry(
            contentWidthPx = Float.NaN,
            contentHeightPx = Float.POSITIVE_INFINITY,
            yAxisLabelWidthPx = Float.NaN,
            labelToAxisGapPx = 8f,
            outerMarginPx = 8f,
            topMarginPx = 16f,
            xAxisGutterPx = 48f,
            dataXMin = Double.NaN,
            dataXMax = Double.POSITIVE_INFINITY,
            dataYMax = Double.NaN
        )

        assertTrue(geometry.dataXToScreen(Double.NaN).isFinite())
        assertTrue(geometry.screenXToData(Float.NaN).isFinite())
        assertTrue(geometry.dataYToScreen(Double.POSITIVE_INFINITY).isFinite())
    }

    private fun scaleFor(max: Double): YAxisScale = calculateVisibleWindowYScale(
        series = listOf(ChartSeries(listOf(0.0), listOf(max))),
        visibleStartH = 0.0,
        visibleEndH = 1.0
    )

    private fun isNiceStep(step: Double): Boolean {
        val exponent = floor(log10(step))
        val normalized = step / 10.0.pow(exponent)
        return listOf(1.0, 2.0, 2.5, 5.0, 10.0).any { abs(normalized - it) < 1e-9 }
    }
}
