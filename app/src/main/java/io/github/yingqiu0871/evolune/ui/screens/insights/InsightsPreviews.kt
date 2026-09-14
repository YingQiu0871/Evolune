package io.github.yingqiu0871.evolune.ui.screens.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.insights.InsightsBindingConfidence
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityKey
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import io.github.yingqiu0871.evolune.history.insights.InsightsLoadFailure
import io.github.yingqiu0871.evolune.history.insights.InsightsPhase
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeValidationError
import io.github.yingqiu0871.evolune.history.insights.InsightsUiState
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * v1.7-B-03 §31: previews render pure sample state — no repository, no database, no ViewModel.
 *
 * The sample summaries are built through the frozen [MedicationInsightsSummary] contract, so a
 * preview that violates the B-01 invariants cannot compile-time hide: it throws while rendering.
 */
private val previewToday: LocalDate = LocalDate.of(2026, 9, 14)

private fun sampleSummary(
    startDate: LocalDate = previewToday.minusDays(29),
    endDate: LocalDate = previewToday,
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
        MedicationIdentityKey.E2 to 12.5,
        MedicationIdentityKey.EV to 6.0
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
    sourceCounts = MedicationIntakeSource.entries.associateWith { source -> sources[source] ?: 0 },
    bindingConfidenceCounts = InsightsBindingConfidence.entries.associateWith { confidence ->
        when (confidence) {
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

private fun contentState(summary: MedicationInsightsSummary): InsightsUiState = InsightsUiState(
    selection = InsightsRangeSelection.Last30Days,
    startDate = summary.startDate,
    endDate = summary.endDate,
    today = previewToday,
    displayZone = ZoneOffset.UTC,
    phase = InsightsPhase.CONTENT,
    summary = summary
)

@Preview(name = "Insights content", showBackground = true)
@Composable
private fun InsightsContentPreview() {
    EvoluneTheme { InsightsScreenContent(state = contentState(sampleSummary())) }
}

@Preview(name = "Insights unrecorded only", showBackground = true)
@Composable
private fun InsightsUnrecordedOnlyPreview() {
    EvoluneTheme {
        InsightsScreenContent(
            state = contentState(
                sampleSummary(
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
            )
        )
    }
}

@Preview(name = "Insights timezone disclosure", showBackground = true)
@Composable
private fun InsightsTimezonePreview() {
    EvoluneTheme {
        InsightsScreenContent(state = contentState(sampleSummary(timezoneDerived = true)))
    }
}

@Preview(name = "Insights unknown medication", showBackground = true)
@Composable
private fun InsightsUnknownMedicationPreview() {
    EvoluneTheme {
        InsightsScreenContent(
            state = contentState(sampleSummary(doses = emptyMap(), unknownIdentity = 3))
        )
    }
}

@Preview(name = "Insights loading", showBackground = true)
@Composable
private fun InsightsLoadingPreview() {
    EvoluneTheme {
        InsightsScreenContent(
            state = InsightsUiState(
                selection = InsightsRangeSelection.Last7Days,
                today = previewToday,
                displayZone = ZoneOffset.UTC,
                phase = InsightsPhase.LOADING
            )
        )
    }
}

@Preview(name = "Insights empty", showBackground = true)
@Composable
private fun InsightsEmptyPreview() {
    EvoluneTheme {
        InsightsScreenContent(
            state = contentState(
                sampleSummary(
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
            )
        )
    }
}

@Preview(name = "Insights error", showBackground = true)
@Composable
private fun InsightsErrorPreview() {
    EvoluneTheme {
        InsightsScreenContent(
            state = InsightsUiState(
                selection = InsightsRangeSelection.Last90Days,
                startDate = previewToday.minusDays(89),
                endDate = previewToday,
                today = previewToday,
                displayZone = ZoneOffset.UTC,
                phase = InsightsPhase.ERROR,
                failure = InsightsLoadFailure.ReadFailure(IllegalStateException("preview"))
            )
        )
    }
}

@Preview(name = "Insights invalid range", showBackground = true)
@Composable
private fun InsightsInvalidPreview() {
    EvoluneTheme {
        InsightsScreenContent(
            state = InsightsUiState(
                selection = InsightsRangeSelection.Custom(previewToday, previewToday.minusDays(3)),
                today = previewToday,
                displayZone = ZoneOffset.UTC,
                phase = InsightsPhase.INVALID_RANGE,
                validationError = InsightsRangeValidationError.START_AFTER_END
            )
        )
    }
}

@Preview(name = "Insights large font", showBackground = true, fontScale = 1.5f)
@Composable
private fun InsightsLargeFontPreview() {
    EvoluneTheme { InsightsScreenContent(state = contentState(sampleSummary())) }
}
