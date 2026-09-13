package io.github.yingqiu0871.evolune.history.insights

import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityKey
import io.github.yingqiu0871.evolune.experience.insights.ReadOnlyMedicationInsightsAggregator
import io.github.yingqiu0871.evolune.experience.insights.InsightsBindingConfidence
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * v1.7-B-02 section 35: wiring check with the **real** B-01 aggregator.
 *
 * The aggregation math itself is not mocked here: the ViewModel reads through a fake seam and then
 * hands the range to [ReadOnlyMedicationInsightsAggregator], so the summary that reaches the UI
 * state is the frozen B-01 result. This proves the orchestration maps nothing incorrectly.
 */
class InsightsViewModelIntegrationTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 13)
    private val now: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun `the state carries the frozen aggregation of the range the read returned`() {
        val endDate = today
        val day = testDay(
            date = endDate,
            entries = listOf(
                matchedEntry(displayDate = endDate),
                unrecordedEntry(displayDate = endDate),
                unmatchedEntry(displayDate = endDate, provenance = HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED)
            )
        )
        val source = RecordingRangeSource().apply {
            result = { call -> HistoricalRange(call.startDate, call.endDate, listOf(day)) }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = InsightsViewModel(
            rangeSource = source,
            aggregator = ReadOnlyMedicationInsightsAggregator,
            clock = MutableTestClock(now, utc),
            displayZone = { utc },
            operationScope = scope
        )

        try {
            val state = viewModel.uiState.value
            assertEquals(InsightsPhase.CONTENT, state.phase)
            val summary = requireNotNull(state.summary)
            assertEquals("matched + unmatched both count as recorded intakes", 2, summary.recordedIntakeCount)
            assertEquals(1, summary.recordedDayCount)
            assertEquals(1, summary.matchedOccurrenceCount)
            assertEquals(1, summary.unrecordedOccurrenceCount)
            assertEquals(1, summary.unmatchedActualIntakeCount)
            assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.HIGH))
            assertEquals(1, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.NONE))
            assertEquals(0, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.MEDIUM))
            assertEquals(0, summary.bindingConfidenceCounts.getValue(InsightsBindingConfidence.LOW))
            assertEquals(true, summary.containsCurrentTimezoneDerivedDates)
            // the state carries exactly the frozen aggregation of the range the read returned
            assertEquals(
                ReadOnlyMedicationInsightsAggregator.aggregate(source.returned.single()),
                summary
            )
            assertEquals(
                listOf(MedicationIdentityKey.E2),
                summary.perMedicationDoseTotalsMg.keys.toList()
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the same range produces the same summary twice`() {
        val endDate = today
        val day = testDay(date = endDate, entries = listOf(matchedEntry(displayDate = endDate)))
        val range = HistoricalRange(startDate = endDate.minusDays(6), endDate = endDate, days = listOf(day))

        val first = ReadOnlyMedicationInsightsAggregator.aggregate(range)
        val second = ReadOnlyMedicationInsightsAggregator.aggregate(range)

        assertEquals(first, second)
    }
}
