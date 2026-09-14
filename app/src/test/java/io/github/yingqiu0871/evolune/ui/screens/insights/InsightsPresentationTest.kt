package io.github.yingqiu0871.evolune.ui.screens.insights

import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.insights.InsightsBindingConfidence
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityKey
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import io.github.yingqiu0871.evolune.history.insights.InsightsLoadFailure
import io.github.yingqiu0871.evolune.history.insights.InsightsPhase
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeValidationError
import io.github.yingqiu0871.evolune.history.insights.InsightsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * v1.7-B-03 §40: the Insights presentation path is a pure JVM decision surface.
 *
 * Every assertion here is about *which frozen fact becomes which section* — never about a new
 * number.
 */
class InsightsPresentationTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 14)
    private val us: Locale = Locale.US

    private fun summary(
        startDate: LocalDate = today.minusDays(29),
        endDate: LocalDate = today,
        recordedIntakes: Int = 6,
        recordedDays: Int = 4,
        matched: Int = 4,
        unrecorded: Int = 3,
        unmatched: Int = 2,
        high: Int = 3,
        medium: Int = 1,
        low: Int = 0,
        sources: Map<MedicationIntakeSource, Int> = mapOf(
            MedicationIntakeSource.MANUAL to 3,
            MedicationIntakeSource.REMINDER to 2,
            MedicationIntakeSource.LEGACY to 1
        ),
        doses: Map<MedicationIdentityKey, Double> = mapOf(
            MedicationIdentityKey.EV to 6.0,
            MedicationIdentityKey.E2 to 12.5
        ),
        unknownIdentity: Int = 1,
        timezoneDerived: Boolean = false
    ): MedicationInsightsSummary = MedicationInsightsSummary(
        startDate = startDate,
        endDate = endDate,
        recordedIntakeCount = recordedIntakes,
        recordedDayCount = recordedDays,
        matchedOccurrenceCount = matched,
        unrecordedOccurrenceCount = unrecorded,
        unmatchedActualIntakeCount = unmatched,
        sourceCounts = MedicationIntakeSource.entries.associateWith { sources[it] ?: 0 },
        bindingConfidenceCounts = InsightsBindingConfidence.entries.associateWith {
            when (it) {
                InsightsBindingConfidence.HIGH -> high
                InsightsBindingConfidence.MEDIUM -> medium
                InsightsBindingConfidence.LOW -> low
                InsightsBindingConfidence.NONE -> unmatched
            }
        },
        perMedicationDoseTotalsMg = doses,
        unknownIdentityRecordedIntakeCount = unknownIdentity,
        containsCurrentTimezoneDerivedDates = timezoneDerived
    )

    private fun state(
        phase: InsightsPhase,
        summary: MedicationInsightsSummary? = null,
        selection: InsightsRangeSelection = InsightsRangeSelection.Last30Days,
        startDate: LocalDate? = summary?.startDate ?: today.minusDays(29),
        endDate: LocalDate? = summary?.endDate ?: today,
        validationError: InsightsRangeValidationError? = null,
        failure: InsightsLoadFailure? = null
    ): InsightsUiState = InsightsUiState(
        selection = selection,
        startDate = startDate,
        endDate = endDate,
        today = today,
        displayZone = ZoneOffset.UTC,
        phase = phase,
        summary = summary,
        failure = failure,
        validationError = validationError
    )

    @Test
    fun `content exposes exactly the two shipped overview counts`() {
        val model = InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us)

        assertEquals(2, model.overviewCards.size)
        assertEquals(R.string.insights_card_recorded_intakes, model.overviewCards[0].labelRes)
        assertEquals(6, model.overviewCards[0].value)
        assertEquals(R.string.insights_card_recorded_days, model.overviewCards[1].labelRes)
        assertEquals(4, model.overviewCards[1].value)
    }

    @Test
    fun `coverage ships both counts and both mandatory disclosure sentences`() {
        val coverage = requireNotNull(
            InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us).coverage
        )

        assertEquals(R.string.insights_coverage_title, coverage.titleRes)
        assertEquals(4, coverage.linkedCount)
        assertEquals(3, coverage.unlinkedCount)
        assertEquals(
            listOf(
                R.string.insights_coverage_disclosure_schedule,
                R.string.insights_coverage_disclosure_absence
            ),
            coverage.disclosureRes
        )
    }

    @Test
    fun `an unrecorded-only range is content and never the empty message`() {
        val unrecordedOnly = summary(
            recordedIntakes = 0,
            recordedDays = 0,
            matched = 0,
            unrecorded = 4,
            unmatched = 0,
            high = 0,
            medium = 0,
            low = 0,
            sources = emptyMap(),
            doses = emptyMap(),
            unknownIdentity = 0
        )

        val model = InsightsPresentation.present(state(InsightsPhase.CONTENT, unrecordedOnly), us)

        assertNull("an unrecorded-only range must not render the empty state", model.emptyMessageRes)
        assertTrue(model.hasSummary)
        assertEquals(0, model.overviewCards[0].value)
        assertEquals(4, requireNotNull(model.coverage).unlinkedCount)
        assertEquals(2, requireNotNull(model.coverage).disclosureRes.size)
    }

    @Test
    fun `a truly empty range shows the empty message and no sections`() {
        val empty = summary(
            recordedIntakes = 0,
            recordedDays = 0,
            matched = 0,
            unrecorded = 0,
            unmatched = 0,
            high = 0,
            medium = 0,
            low = 0,
            sources = emptyMap(),
            doses = emptyMap(),
            unknownIdentity = 0
        )

        val model = InsightsPresentation.present(state(InsightsPhase.EMPTY, empty), us)

        assertEquals(R.string.insights_empty, model.emptyMessageRes)
        assertNull(model.errorMessageRes)
        assertEquals(0, model.overviewCards[0].value)
    }

    @Test
    fun `every source keeps its own row in the frozen order`() {
        val rows = InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us).sources

        assertEquals(
            listOf(
                MedicationIntakeSource.MANUAL,
                MedicationIntakeSource.REMINDER,
                MedicationIntakeSource.WEAR,
                MedicationIntakeSource.WIDGET,
                MedicationIntakeSource.JSON_V1,
                MedicationIntakeSource.LEGACY
            ),
            rows.map { it.source }
        )
        assertEquals(
            R.string.history_source_manual,
            rows.first { it.source == MedicationIntakeSource.MANUAL }.labelRes
        )
        assertEquals(
            R.string.history_source_legacy,
            rows.first { it.source == MedicationIntakeSource.LEGACY }.labelRes
        )
        assertEquals(3, rows.first { it.source == MedicationIntakeSource.MANUAL }.count)
        assertEquals(0, rows.first { it.source == MedicationIntakeSource.WEAR }.count)
    }

    @Test
    fun `confidence keeps the four frozen buckets in order`() {
        val rows = InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us).confidence

        assertEquals(
            listOf(
                InsightsBindingConfidence.HIGH,
                InsightsBindingConfidence.MEDIUM,
                InsightsBindingConfidence.LOW,
                InsightsBindingConfidence.NONE
            ),
            rows.map { it.confidence }
        )
        assertEquals(listOf(3, 1, 0, 2), rows.map { it.count })
        assertEquals(R.string.insights_confidence_high, rows.first().labelRes)
    }

    @Test
    fun `medication dose rows are ordered deterministically and never summed`() {
        val rows = InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us).doseRows

        assertEquals(listOf(MedicationIdentityKey.E2, MedicationIdentityKey.EV), rows.map { it.medicationKey })
        assertEquals(12.5, rows[0].totalMg, 0.0001)
        assertEquals(6.0, rows[1].totalMg, 0.0001)
        assertEquals(R.string.ester_e2, rows[0].labelRes)
        assertNull(InsightsPresentation.present(state(InsightsPhase.CONTENT, summary()), us).doseEmptyMessageRes)
    }

    @Test
    fun `a summary without provable medication identity explains why the dose list is empty`() {
        val model = InsightsPresentation.present(
            state(InsightsPhase.CONTENT, summary(doses = emptyMap(), unknownIdentity = 3)),
            us
        )

        assertEquals(emptyList<InsightsPresentation.DoseRow>(), model.doseRows)
        assertEquals(R.string.insights_dose_empty, model.doseEmptyMessageRes)
        assertEquals(3, model.unknownIdentityCount)
    }

    @Test
    fun `timezone derived dates expose the neutral disclosure and nothing else does`() {
        assertEquals(
            R.string.insights_timezone_disclosure,
            InsightsPresentation.present(state(InsightsPhase.CONTENT, summary(timezoneDerived = true)), us)
                .timezoneDisclosureRes
        )
        assertNull(
            InsightsPresentation.present(state(InsightsPhase.CONTENT, summary(timezoneDerived = false)), us)
                .timezoneDisclosureRes
        )
    }

    @Test
    fun `loading and error states expose no section and no stale summary`() {
        val loading = InsightsPresentation.present(state(InsightsPhase.LOADING), us)
        assertNull("loading must not show sections", loading.coverage)
        assertNull(loading.emptyMessageRes)
        assertNull(loading.errorMessageRes)
        // the resolved range is already known while the read is in flight, so the heading stays
        assertTrue(loading.rangeHeading != null)

        val error = InsightsPresentation.present(
            state(InsightsPhase.ERROR, failure = InsightsLoadFailure.ReadFailure(IllegalStateException("x"))),
            us
        )
        assertNull("an error must not render a stale summary", error.coverage)
        assertEquals(R.string.insights_error, error.errorMessageRes)
        assertEquals(emptyList<InsightsPresentation.MetricCard>(), error.overviewCards)
        assertEquals(emptyList<InsightsPresentation.SourceRow>(), error.sources)
        assertEquals(emptyList<InsightsPresentation.DoseRow>(), error.doseRows)
    }

    @Test
    fun `invalid ranges expose a typed neutral message and no range heading`() {
        val startAfterEnd = InsightsPresentation.present(
            state(
                InsightsPhase.INVALID_RANGE,
                startDate = null,
                endDate = null,
                validationError = InsightsRangeValidationError.START_AFTER_END
            ),
            us
        )
        assertEquals(R.string.insights_invalid_start_after_end, startAfterEnd.validationMessageRes)
        assertNull(startAfterEnd.rangeHeading)
        assertNull(startAfterEnd.coverage)

        val endInFuture = InsightsPresentation.present(
            state(
                InsightsPhase.INVALID_RANGE,
                startDate = null,
                endDate = null,
                validationError = InsightsRangeValidationError.END_IN_FUTURE
            ),
            us
        )
        assertEquals(R.string.insights_invalid_end_in_future, endInFuture.validationMessageRes)

        val unknown = InsightsPresentation.present(
            state(InsightsPhase.INVALID_RANGE, startDate = null, endDate = null, validationError = null),
            us
        )
        assertEquals(R.string.insights_invalid_generic, unknown.validationMessageRes)
    }

    @Test
    fun `the range heading is formatted from the resolved endpoints only`() {
        val model = InsightsPresentation.present(
            state(
                InsightsPhase.CONTENT,
                summary(startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 14))
            ),
            us
        )

        val expected = InsightsPresentation.formatRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14), us)
        assertEquals(expected, model.rangeHeading)
        assertTrue("heading must contain a separator", expected.contains("–"))
    }

    @Test
    fun `selection labels cover all five frozen presets`() {
        assertEquals(
            R.string.insights_range_last_7,
            InsightsPresentation.selectionLabelRes(InsightsRangeSelection.Last7Days)
        )
        assertEquals(
            R.string.insights_range_last_30,
            InsightsPresentation.selectionLabelRes(InsightsRangeSelection.Last30Days)
        )
        assertEquals(
            R.string.insights_range_last_90,
            InsightsPresentation.selectionLabelRes(InsightsRangeSelection.Last90Days)
        )
        assertEquals(
            R.string.insights_range_current_month,
            InsightsPresentation.selectionLabelRes(InsightsRangeSelection.CurrentMonth)
        )
        assertEquals(
            R.string.insights_range_custom,
            InsightsPresentation.selectionLabelRes(
                InsightsRangeSelection.Custom(today.minusDays(3), today)
            )
        )
    }
}
