package io.github.yingqiu0871.evolune.ui.screens.retrospective

import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkPoint
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * V17-C-04 §14.7 curve presentation (C1-C3): the chart consumes the approved series exactly —
 * no recomputation, no clamp/normalization, and the viewport is the series interval.
 */
class RetrospectiveChartDataTest {

    private val start = Instant.parse("2026-08-17T12:00:00Z")
    private val end = start.plus(Duration.ofDays(30))

    private fun seriesOf(values: List<Double>): RetrospectivePkSeries {
        val spanMillis = end.toEpochMilli() - start.toEpochMilli()
        val lastIndex = (values.size - 1).coerceAtLeast(1)
        return RetrospectivePkSeries(
            startInclusive = start,
            endInclusive = end,
            points = values.mapIndexed { index, value ->
                RetrospectivePkPoint(
                    instant = start.plusMillis(spanMillis * index / lastIndex),
                    concentrationPGmL = value
                )
            }
        )
    }

    @Test
    fun `C1 chart points mirror the approved series point-for-point with raw bit values`() {
        val values = listOf(0.0, -2.7255464005139244E-13, 23285.499354395688, 0.0)
        val series = seriesOf(values)

        val points = retrospectiveChartPoints(series)

        assertEquals("no point may be dropped or added", series.points.size, points.size)
        series.points.forEachIndexed { index, source ->
            assertEquals(
                "concentration must stay bit-identical (no clamp/normalization)",
                source.concentrationPGmL.toRawBits(),
                points[index].concentrationPGmL.toRawBits()
            )
            assertEquals(source.instant, series.points[index].instant)
        }
    }

    @Test
    fun `C2 a small negative roundoff value is preserved unmodified and never clamped to zero`() {
        val roundoff = -2.7255464005139244E-13
        val points = retrospectiveChartPoints(seriesOf(listOf(0.0, roundoff, 10.0)))

        assertTrue(points[1].concentrationPGmL < 0.0)
        assertEquals(roundoff.toRawBits(), points[1].concentrationPGmL.toRawBits())

        // The y-axis uses the raw extrema; the negative roundoff does not become the max.
        assertEquals(10.0, retrospectiveChartYMax(points), 0.0)
    }

    @Test
    fun `C3 the viewport is exactly the series interval - 720 hours`() {
        val series = seriesOf(listOf(0.0, 1.0))
        assertEquals(720.0, retrospectiveChartTotalHours(series), 0.0)

        val points = retrospectiveChartPoints(series)
        assertEquals(0.0, points.first().hourOffset, 0.0)
        assertEquals(720.0, points.last().hourOffset, 1e-9)
    }

    @Test
    fun `marker hour offsets are derived from the same frozen series start`() {
        val series = seriesOf(listOf(0.0, 1.0))
        assertEquals(0.0, markerHourOffset(start, series), 0.0)
        assertEquals(360.0, markerHourOffset(start.plus(Duration.ofDays(15)), series), 1e-9)
        assertEquals(720.0, markerHourOffset(end, series), 1e-9)
    }

    @Test
    fun `the y axis falls back to one only when nothing positive exists`() {
        val zero = retrospectiveChartPoints(seriesOf(listOf(0.0, 0.0)))
        assertEquals(1.0, retrospectiveChartYMax(zero), 0.0)
    }
}
