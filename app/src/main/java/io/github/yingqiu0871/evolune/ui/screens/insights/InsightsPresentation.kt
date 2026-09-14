package io.github.yingqiu0871.evolune.ui.screens.insights

import androidx.annotation.StringRes
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.insights.InsightsBindingConfidence
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityKey
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import io.github.yingqiu0871.evolune.history.insights.InsightsPhase
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeValidationError
import io.github.yingqiu0871.evolune.history.insights.InsightsUiState
import io.github.yingqiu0871.evolune.pk.Ester
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * v1.7-B-03: the single presentation path from the frozen B-02 [InsightsUiState] to the Insights
 * surface.
 *
 * The mapper only *selects, orders and labels* what the B-01 aggregate already states. It never
 * derives a new fact: no ratio, no percentage, no per-day series, no cross-medication total, no
 * re-classification of identity. Every number it exposes is copied from
 * [MedicationInsightsSummary] or from the range the ViewModel resolved.
 */
object InsightsPresentation {

    /** One metric card: a frozen count plus its label resource. */
    data class MetricCard(@StringRes val labelRes: Int, val value: Int)

    /** Coverage is always shipped as two counts plus the mandatory two-sentence disclosure. */
    data class CoverageSection(
        val titleRes: Int,
        val linkedLabelRes: Int,
        val linkedCount: Int,
        val unlinkedLabelRes: Int,
        val unlinkedCount: Int,
        val disclosureRes: List<Int>
    )

    data class SourceRow(val source: MedicationIntakeSource, @StringRes val labelRes: Int, val count: Int)

    data class ConfidenceRow(val confidence: InsightsBindingConfidence, @StringRes val labelRes: Int, val count: Int)

    data class DoseRow(val medicationKey: MedicationIdentityKey, @StringRes val labelRes: Int, val totalMg: Double)

    /**
     * Everything the surface renders. Sections are absent (`null` / empty list) when the frozen
     * aggregate has nothing to say about them, so the screen cannot invent placeholder facts.
     */
    data class InsightsUiModel(
        val phase: InsightsPhase,
        val selection: InsightsRangeSelection,
        val rangeHeading: String?,
        val validationMessageRes: Int?,
        val overviewCards: List<MetricCard>,
        val coverage: CoverageSection?,
        val sources: List<SourceRow>,
        val confidence: List<ConfidenceRow>,
        val doseRows: List<DoseRow>,
        val doseEmptyMessageRes: Int?,
        val unknownIdentityCount: Int,
        val timezoneDisclosureRes: Int?,
        val emptyMessageRes: Int?,
        val errorMessageRes: Int?
    ) {
        val hasSummary: Boolean get() = coverage != null
    }

    private val sourceOrder = listOf(
        MedicationIntakeSource.MANUAL,
        MedicationIntakeSource.REMINDER,
        MedicationIntakeSource.WEAR,
        MedicationIntakeSource.WIDGET,
        MedicationIntakeSource.JSON_V1,
        MedicationIntakeSource.LEGACY
    )

    private val confidenceOrder = listOf(
        InsightsBindingConfidence.HIGH,
        InsightsBindingConfidence.MEDIUM,
        InsightsBindingConfidence.LOW,
        InsightsBindingConfidence.NONE
    )

    fun present(state: InsightsUiState, locale: Locale = Locale.getDefault()): InsightsUiModel {
        val heading = rangeHeading(state, locale)
        val summary = state.summary
        return when {
            state.phase == InsightsPhase.INVALID_RANGE -> InsightsUiModel(
                phase = state.phase,
                selection = state.selection,
                rangeHeading = null,
                validationMessageRes = validationMessageRes(state.validationError),
                overviewCards = emptyList(),
                coverage = null,
                sources = emptyList(),
                confidence = emptyList(),
                doseRows = emptyList(),
                doseEmptyMessageRes = null,
                unknownIdentityCount = 0,
                timezoneDisclosureRes = null,
                emptyMessageRes = null,
                errorMessageRes = null
            )

            state.phase == InsightsPhase.ERROR || summary == null -> InsightsUiModel(
                phase = state.phase,
                selection = state.selection,
                rangeHeading = heading,
                validationMessageRes = null,
                overviewCards = emptyList(),
                coverage = null,
                sources = emptyList(),
                confidence = emptyList(),
                doseRows = emptyList(),
                doseEmptyMessageRes = null,
                unknownIdentityCount = 0,
                timezoneDisclosureRes = null,
                emptyMessageRes = null,
                errorMessageRes = if (state.phase == InsightsPhase.ERROR) R.string.insights_error else null
            )

            else -> InsightsUiModel(
                phase = state.phase,
                selection = state.selection,
                rangeHeading = heading,
                validationMessageRes = null,
                overviewCards = overviewCards(summary),
                coverage = coverage(summary),
                sources = sourceRows(summary),
                confidence = confidenceRows(summary),
                doseRows = doseRows(summary),
                doseEmptyMessageRes = if (summary.perMedicationDoseTotalsMg.isEmpty()) {
                    R.string.insights_dose_empty
                } else {
                    null
                },
                unknownIdentityCount = summary.unknownIdentityRecordedIntakeCount,
                timezoneDisclosureRes = if (summary.containsCurrentTimezoneDerivedDates) {
                    R.string.insights_timezone_disclosure
                } else {
                    null
                },
                emptyMessageRes = if (state.phase == InsightsPhase.EMPTY) R.string.insights_empty else null,
                errorMessageRes = null
            )
        }
    }

    /**
     * The range heading is formatted from the endpoints B-02 already resolved; the mapper never
     * derives a range of its own.
     */
    fun rangeHeading(state: InsightsUiState, locale: Locale = Locale.getDefault()): String? {
        val start = state.startDate ?: return null
        val end = state.endDate ?: return null
        return formatRange(start, end, locale)
    }

    fun formatRange(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return "${formatter.format(start)} – ${formatter.format(end)}"
    }

    fun selectionLabelRes(selection: InsightsRangeSelection): Int = when (selection) {
        InsightsRangeSelection.Last7Days -> R.string.insights_range_last_7
        InsightsRangeSelection.Last30Days -> R.string.insights_range_last_30
        InsightsRangeSelection.Last90Days -> R.string.insights_range_last_90
        InsightsRangeSelection.CurrentMonth -> R.string.insights_range_current_month
        is InsightsRangeSelection.Custom -> R.string.insights_range_custom
    }

    fun validationMessageRes(error: InsightsRangeValidationError?): Int = when (error) {
        InsightsRangeValidationError.START_AFTER_END -> R.string.insights_invalid_start_after_end
        InsightsRangeValidationError.END_IN_FUTURE -> R.string.insights_invalid_end_in_future
        null -> R.string.insights_invalid_generic
    }

    private fun overviewCards(summary: MedicationInsightsSummary): List<MetricCard> = listOf(
        MetricCard(R.string.insights_card_recorded_intakes, summary.recordedIntakeCount),
        MetricCard(R.string.insights_card_recorded_days, summary.recordedDayCount)
    )

    private fun coverage(summary: MedicationInsightsSummary): CoverageSection = CoverageSection(
        titleRes = R.string.insights_coverage_title,
        linkedLabelRes = R.string.insights_coverage_linked,
        linkedCount = summary.matchedOccurrenceCount,
        unlinkedLabelRes = R.string.insights_coverage_unlinked,
        unlinkedCount = summary.unrecordedOccurrenceCount,
        disclosureRes = listOf(
            R.string.insights_coverage_disclosure_schedule,
            R.string.insights_coverage_disclosure_absence
        )
    )

    /** The six authoritative sources keep their own row and their own label, in frozen order. */
    private fun sourceRows(summary: MedicationInsightsSummary): List<SourceRow> =
        sourceOrder.map { source ->
            SourceRow(source, sourceLabelRes(source), summary.sourceCounts[source] ?: 0)
        }

    private fun confidenceRows(summary: MedicationInsightsSummary): List<ConfidenceRow> =
        confidenceOrder.map { confidence ->
            ConfidenceRow(confidence, confidenceLabelRes(confidence), summary.bindingConfidenceCounts[confidence] ?: 0)
        }

    /**
     * One row per provable medication, ordered by the aggregate's own total (descending) and then
     * by the frozen key order, so the list is deterministic. No row is ever summed with another.
     */
    private fun doseRows(summary: MedicationInsightsSummary): List<DoseRow> =
        summary.perMedicationDoseTotalsMg.entries
            .sortedWith(compareByDescending<Map.Entry<MedicationIdentityKey, Double>> { it.value }
                .thenBy { MedicationIdentityKey.entries.indexOf(it.key) })
            .map { (key, total) -> DoseRow(key, medicationLabelRes(key), total) }

    private fun sourceLabelRes(source: MedicationIntakeSource): Int = when (source) {
        MedicationIntakeSource.MANUAL -> R.string.history_source_manual
        MedicationIntakeSource.REMINDER -> R.string.history_source_reminder
        MedicationIntakeSource.WEAR -> R.string.history_source_wear
        MedicationIntakeSource.WIDGET -> R.string.history_source_widget
        MedicationIntakeSource.JSON_V1 -> R.string.history_source_json
        MedicationIntakeSource.LEGACY -> R.string.history_source_legacy
    }

    private fun confidenceLabelRes(confidence: InsightsBindingConfidence): Int = when (confidence) {
        InsightsBindingConfidence.HIGH -> R.string.insights_confidence_high
        InsightsBindingConfidence.MEDIUM -> R.string.insights_confidence_medium
        InsightsBindingConfidence.LOW -> R.string.insights_confidence_low
        InsightsBindingConfidence.NONE -> R.string.insights_confidence_none
    }

    /**
     * The medication label reuses the existing ester string resources, so Insights cannot drift
     * from the vocabulary the plan/record surfaces already ship.
     */
    private fun medicationLabelRes(key: MedicationIdentityKey): Int = when (key.name) {
        Ester.E2.name -> R.string.ester_e2
        Ester.EB.name -> R.string.ester_eb
        Ester.EV.name -> R.string.ester_ev
        Ester.EC.name -> R.string.ester_ec
        Ester.EN.name -> R.string.ester_en
        else -> R.string.insights_unknown_identity
    }
}
