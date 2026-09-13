package io.github.yingqiu0871.evolune.history.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * v1.7-B-02 section 4/5/6: the resolver is the only place that turns a selection into endpoints.
 *
 * The preset expectations are the frozen v1.7-B-00 section 19 definitions, verified against the
 * B-00-R1 evidence example (`today = 2026-09-13`).
 */
class InsightsRangeResolverTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 13)

    private fun resolved(selection: InsightsRangeSelection): InsightsRangeResolution.Resolved =
        InsightsRangeResolver.resolve(selection, today) as InsightsRangeResolution.Resolved

    @Test
    fun `last 7 days is today - 6 through today inclusive`() {
        val resolution = resolved(InsightsRangeSelection.Last7Days)

        assertEquals(LocalDate.of(2026, 9, 7), resolution.startDate)
        assertEquals(today, resolution.endDate)
        assertEquals(7, daysInclusive(resolution))
    }

    @Test
    fun `last 30 days is today - 29 through today inclusive`() {
        val resolution = resolved(InsightsRangeSelection.Last30Days)

        assertEquals(LocalDate.of(2026, 8, 15), resolution.startDate)
        assertEquals(today, resolution.endDate)
        assertEquals(30, daysInclusive(resolution))
    }

    @Test
    fun `last 90 days is today - 89 through today inclusive`() {
        val resolution = resolved(InsightsRangeSelection.Last90Days)

        assertEquals(LocalDate.of(2026, 6, 16), resolution.startDate)
        assertEquals(today, resolution.endDate)
        assertEquals(90, daysInclusive(resolution))
    }

    @Test
    fun `current month is the first of the month through today`() {
        val resolution = resolved(InsightsRangeSelection.CurrentMonth)

        assertEquals(LocalDate.of(2026, 9, 1), resolution.startDate)
        assertEquals(today, resolution.endDate)
    }

    @Test
    fun `current month on the first day is a single date`() {
        val firstOfMonth = LocalDate.of(2026, 9, 1)

        val resolution = InsightsRangeResolver.resolve(
            InsightsRangeSelection.CurrentMonth,
            firstOfMonth
        ) as InsightsRangeResolution.Resolved

        assertEquals(firstOfMonth, resolution.startDate)
        assertEquals(firstOfMonth, resolution.endDate)
    }

    @Test
    fun `a valid custom range resolves unchanged`() {
        val selection = InsightsRangeSelection.Custom(
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 10)
        )

        val resolution = resolved(selection)

        assertEquals(LocalDate.of(2026, 9, 1), resolution.startDate)
        assertEquals(LocalDate.of(2026, 9, 10), resolution.endDate)
    }

    @Test
    fun `a custom range ending today is valid`() {
        val selection = InsightsRangeSelection.Custom(LocalDate.of(2026, 9, 1), today)

        val resolution = resolved(selection)

        assertEquals(today, resolution.endDate)
    }

    @Test
    fun `a custom range with start after end is invalid`() {
        val selection = InsightsRangeSelection.Custom(
            startDate = LocalDate.of(2026, 9, 10),
            endDate = LocalDate.of(2026, 9, 1)
        )

        val resolution = InsightsRangeResolver.resolve(selection, today)

        assertEquals(
            InsightsRangeResolution.Invalid(InsightsRangeValidationError.START_AFTER_END),
            resolution
        )
    }

    @Test
    fun `a custom range ending after today is invalid`() {
        val selection = InsightsRangeSelection.Custom(
            startDate = LocalDate.of(2026, 9, 1),
            endDate = today.plusDays(1)
        )

        val resolution = InsightsRangeResolver.resolve(selection, today)

        assertEquals(
            InsightsRangeResolution.Invalid(InsightsRangeValidationError.END_IN_FUTURE),
            resolution
        )
    }

    @Test
    fun `relative presets follow today`() {
        val nextDay = today.plusDays(1)

        val last7 = InsightsRangeResolver.resolve(InsightsRangeSelection.Last7Days, nextDay)
            as InsightsRangeResolution.Resolved
        val month = InsightsRangeResolver.resolve(InsightsRangeSelection.CurrentMonth, nextDay)
            as InsightsRangeResolution.Resolved

        assertEquals(LocalDate.of(2026, 9, 8), last7.startDate)
        assertEquals(nextDay, last7.endDate)
        assertEquals(LocalDate.of(2026, 9, 1), month.startDate)
        assertEquals(nextDay, month.endDate)
    }

    @Test
    fun `relative presets cross a month boundary correctly`() {
        val march1 = LocalDate.of(2026, 3, 1)

        val last7 = InsightsRangeResolver.resolve(InsightsRangeSelection.Last7Days, march1)
            as InsightsRangeResolution.Resolved
        val last30 = InsightsRangeResolver.resolve(InsightsRangeSelection.Last30Days, march1)
            as InsightsRangeResolution.Resolved
        val last90 = InsightsRangeResolver.resolve(InsightsRangeSelection.Last90Days, march1)
            as InsightsRangeResolution.Resolved

        assertEquals(LocalDate.of(2026, 2, 23), last7.startDate)
        assertEquals(LocalDate.of(2026, 1, 31), last30.startDate)
        assertEquals(LocalDate.of(2025, 12, 2), last90.startDate)
    }

    @Test
    fun `the resolver is deterministic and side-effect free`() {
        val first = InsightsRangeResolver.resolve(InsightsRangeSelection.Last30Days, today)
        val second = InsightsRangeResolver.resolve(InsightsRangeSelection.Last30Days, today)

        assertEquals(first, second)
        assertTrue(first is InsightsRangeResolution.Resolved)
    }

    private fun daysInclusive(resolution: InsightsRangeResolution.Resolved): Int =
        (resolution.endDate.toEpochDay() - resolution.startDate.toEpochDay() + 1).toInt()
}
