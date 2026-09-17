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

    // ---------- v1.7.1 UI hotfix: view-only decimation, labels, inspection ----------

    @Test
    fun `decimation keeps the first and last point and stays inside the bound`() {
        val values = (0 until 7201).map { index -> index % 97 / 97.0 }
        val points = retrospectiveChartPoints(seriesOf(values))

        val drawn = retrospectiveChartDecimate(points, 720)

        assertTrue("the drawn series must stay bounded", drawn.size <= 720)
        assertEquals(points.first(), drawn.first())
        assertEquals(points.last(), drawn.last())
        assertTrue(
            "hour offsets must stay strictly ascending",
            drawn.zipWithNext().all { (first, second) -> second.hourOffset > first.hourOffset }
        )
    }

    @Test
    fun `decimation preserves the raw global extrema bit for bit and is deterministic`() {
        val values = MutableList(2001) { 1.0 }
        values[700] = 0.0
        values[1300] = 512.25
        val points = retrospectiveChartPoints(seriesOf(values))

        val drawn = retrospectiveChartDecimate(points, 400)
        val again = retrospectiveChartDecimate(points, 400)

        assertEquals("decimation must be deterministic", drawn, again)
        val min = drawn.minByOrNull { it.concentrationPGmL }
        val max = drawn.maxByOrNull { it.concentrationPGmL }
        assertEquals(points[700].hourOffset, min!!.hourOffset, 1e-9)
        assertEquals(
            "the raw minimum must stay bit-identical",
            points[700].concentrationPGmL.toRawBits(),
            min.concentrationPGmL.toRawBits()
        )
        assertEquals(points[1300].hourOffset, max!!.hourOffset, 1e-9)
        assertEquals(
            "the raw maximum must stay bit-identical",
            points[1300].concentrationPGmL.toRawBits(),
            max.concentrationPGmL.toRawBits()
        )
    }

    @Test
    fun `a series below the bound is returned unchanged`() {
        val points = retrospectiveChartPoints(seriesOf(listOf(0.0, 1.0, 2.0)))

        assertEquals(points, retrospectiveChartDecimate(points, 720))
    }

    @Test
    fun `x labels stay whole-day, between five and seven, and end at the window end`() {
        val sevenDay = retrospectiveChartLabelHourOffsets(168.0)
        assertEquals(listOf(0.0, 48.0, 96.0, 144.0, 168.0), sevenDay)

        val thirtyDay = retrospectiveChartLabelHourOffsets(720.0)
        assertEquals(6, thirtyDay.size)
        assertEquals(720.0, thirtyDay.last(), 0.0)
        assertTrue(thirtyDay.all { it % 24.0 == 0.0 })

        val ninetyDay = retrospectiveChartLabelHourOffsets(2160.0)
        assertEquals(6, ninetyDay.size)
        assertEquals(2160.0, ninetyDay.last(), 0.0)
        assertTrue(ninetyDay.all { it % 24.0 == 0.0 })

        assertTrue(retrospectiveChartLabelHourOffsets(0.0).isEmpty())
        assertTrue(retrospectiveChartLabelHourOffsets(Double.NaN).isEmpty())
    }

    @Test
    fun `nearest point inspection finds the closest drawn sample`() {
        val points = listOf(
            RetrospectiveChartPoint(0.0, 1.0),
            RetrospectiveChartPoint(10.0, 2.0),
            RetrospectiveChartPoint(20.0, 3.0)
        )

        assertEquals(0, retrospectiveNearestPointIndex(points, 0.0))
        assertEquals(0, retrospectiveNearestPointIndex(points, 4.0))
        assertEquals(1, retrospectiveNearestPointIndex(points, 6.0))
        assertEquals(2, retrospectiveNearestPointIndex(points, 19.5))
        assertEquals(2, retrospectiveNearestPointIndex(points, 500.0))
        assertEquals(-1, retrospectiveNearestPointIndex(emptyList(), 5.0))
    }
}
