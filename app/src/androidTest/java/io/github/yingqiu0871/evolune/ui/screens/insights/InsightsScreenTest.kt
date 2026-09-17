package io.github.yingqiu0871.evolune.ui.screens.insights

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.insights.InsightsBindingConfidence
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityKey
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import io.github.yingqiu0871.evolune.experience.insights.ReadOnlyMedicationInsightsAggregator
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.insights.InsightsLoadFailure
import io.github.yingqiu0871.evolune.history.insights.InsightsPhase
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeValidationError
import io.github.yingqiu0871.evolune.history.insights.InsightsUiState
import io.github.yingqiu0871.evolune.history.insights.InsightsViewModel
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * v1.7-B-03 §35/§36/§41: the Insights surface on a device.
 *
 * Section tests drive the stateless content with synthetic frozen state; the range-selection test
 * drives the real [InsightsViewModel] through a counting seam, so "which range was read" is counted
 * rather than assumed.
 */
@RunWith(AndroidJUnit4::class)
class InsightsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val utc: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 14)
    private val now: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC)

    private fun summary(
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
        startDate = today.minusDays(29),
        endDate = today,
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
        phase: InsightsPhase = InsightsPhase.CONTENT,
        summary: MedicationInsightsSummary? = summary(),
        selection: InsightsRangeSelection = InsightsRangeSelection.Last30Days,
        startDate: LocalDate? = today.minusDays(29),
        endDate: LocalDate? = today,
        validationError: InsightsRangeValidationError? = null,
        failure: InsightsLoadFailure? = null
    ): InsightsUiState = InsightsUiState(
        selection = selection,
        startDate = startDate,
        endDate = endDate,
        today = today,
        displayZone = utc,
        phase = phase,
        summary = summary,
        failure = failure,
        validationError = validationError
    )

    private fun setContent(state: InsightsUiState, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            EvoluneTheme {
                InsightsScreenContent(state = state, onRetry = onRetry)
            }
        }
    }

    @Test
    fun contentShowsEveryShippedSection() {
        setContent(state())

        composeRule.onNodeWithTag("insights-screen").assertExists()
        composeRule.onNodeWithTag("insights-card-recorded-intakes").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-card-recorded-days").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-section").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-sources-section").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-content-list")
            .performScrollToNode(hasTestTag("insights-confidence-section"))
        composeRule.onNodeWithTag("insights-confidence-section").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-content-list")
            .performScrollToNode(hasTestTag("insights-dose-section"))
        composeRule.onNodeWithTag("insights-dose-section").assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.insights_coverage_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-disclosure-schedule").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-disclosure-absence").assertIsDisplayed()
    }

    @Test
    fun overviewMetricCardsAreVisuallySeparated() {
        setContent(state())

        val first = composeRule.onNodeWithTag("insights-card-recorded-intakes")
            .getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("insights-card-recorded-days")
            .getUnclippedBoundsInRoot()
        val gap = if (first.top <= second.top) second.top - first.bottom else first.top - second.bottom
        assertTrue(
            "the two overview metric cards must be separated by at least 12dp (was $gap)",
            gap.value >= 12f
        )
    }

    @Test
    fun everySourceAndConfidenceRowIsShippedWithItsOwnCount() {
        setContent(state())

        MedicationIntakeSource.entries.forEach { source ->
            composeRule.onNodeWithTag("insights-source-row-${source.name}").assertExists()
        }
        composeRule.onNodeWithTag("insights-source-row-LEGACY").assertIsDisplayed()
        InsightsBindingConfidence.entries.forEach { confidence ->
            composeRule.onNodeWithTag("insights-confidence-row-${confidence.name}").assertExists()
        }
        // the legacy source keeps its own label instead of being merged into manual
        val manual = context.getString(R.string.history_source_manual)
        val legacy = context.getString(R.string.history_source_legacy)
        assertEquals(false, manual == legacy)
    }

    @Test
    fun countsAreTextAndContentDescriptionForScreenReaders() {
        setContent(state())

        val label = context.getString(R.string.history_source_manual)
        val expected = context.getString(R.string.insights_count_row_description, label, 3)
        composeRule.onNodeWithTag("insights-source-row-MANUAL").assertContentDescriptionEquals(expected)
    }

    @Test
    fun unrecordedOnlyRangeIsContentAndNeverTheEmptyState() {
        setContent(
            state(
                summary = summary(
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

        composeRule.onNodeWithTag("insights-card-recorded-intakes").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-section").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-unlinked").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-disclosure-absence").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-empty").assertDoesNotExist()
    }

    @Test
    fun trulyEmptyRangeShowsTheEmptyMessageAndNoSections() {
        setContent(
            state(
                phase = InsightsPhase.EMPTY,
                summary = summary(
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

        composeRule.onNodeWithTag("insights-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-section").assertDoesNotExist()
        composeRule.onNodeWithTag("insights-dose-section").assertDoesNotExist()
    }

    @Test
    fun loadingKeepsTheRangeSelectorAndShowsNoSections() {
        setContent(
            state(
                phase = InsightsPhase.LOADING,
                summary = null,
                selection = InsightsRangeSelection.Last7Days,
                startDate = today.minusDays(6)
            )
        )

        composeRule.onNodeWithTag("insights-range-selector").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-loading").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-section").assertDoesNotExist()
        composeRule.onNodeWithTag("insights-empty").assertDoesNotExist()
    }

    @Test
    fun errorOffersRetryAndShowsNoStaleSummary() {
        var retried = 0
        setContent(
            state(
                phase = InsightsPhase.ERROR,
                summary = null,
                failure = InsightsLoadFailure.ReadFailure(IllegalStateException("boom"))
            ),
            onRetry = { retried += 1 }
        )

        composeRule.onNodeWithTag("insights-error").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-section").assertDoesNotExist()
        composeRule.onNodeWithTag("insights-retry").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun invalidRangeShowsTheTypedNeutralMessage() {
        setContent(
            state(
                phase = InsightsPhase.INVALID_RANGE,
                summary = null,
                startDate = null,
                endDate = null,
                validationError = InsightsRangeValidationError.END_IN_FUTURE
            )
        )

        composeRule.onNodeWithTag("insights-invalid-range").assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.insights_invalid_end_in_future))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-section").assertDoesNotExist()
    }

    @Test
    fun timezoneDerivedDatesShowTheNeutralDisclosure() {
        setContent(state(summary = summary(timezoneDerived = true)))
        composeRule.onNodeWithTag("insights-timezone-disclosure").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-coverage-section").assertIsDisplayed()
    }

    @Test
    fun unknownIdentityIsACountAndNeverADoseBucket() {
        setContent(state(summary = summary(doses = emptyMap(), unknownIdentity = 3)))

        composeRule.onNodeWithTag("insights-content-list")
            .performScrollToNode(hasTestTag("insights-dose-section"))
        composeRule.onNodeWithTag("insights-unknown-identity").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-dose-empty").assertIsDisplayed()
        // the count sentence is exposed once, as the row's merged fact
        composeRule.onNodeWithTag("insights-unknown-identity").assertContentDescriptionEquals(
            context.getString(R.string.insights_unknown_identity, 3)
        )
    }

    @Test
    fun rangeSelectorExposesAllFiveOptionsAndDefaultsToLast30() {
        setContent(state())

        composeRule.onNodeWithTag("insights-range-last7").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-range-last30").assertIsSelected()
        composeRule.onNodeWithTag("insights-range-last7").assertIsNotSelected()
        composeRule.onNodeWithTag("insights-range-last90").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-range-current-month").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-range-custom").assertIsDisplayed()
    }

    @Test
    fun selectingAnotherRangeAsksTheViewModelForItAndUpdatesTheSurface() {
        val source = CountingRangeSource()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = InsightsViewModel(
            rangeSource = source,
            aggregator = ReadOnlyMedicationInsightsAggregator,
            clock = Clock.fixed(now, utc),
            displayZone = { utc },
            operationScope = scope
        )
        try {
            composeRule.setContent {
                val state by viewModel.uiState.collectAsState()
                EvoluneTheme {
                    InsightsScreenContent(state = state, onSelectRange = viewModel::selectRange)
                }
            }

            assertEquals(1, source.calls.size)
            composeRule.onNodeWithTag("insights-range-last30").assertIsSelected()

            composeRule.onNodeWithTag("insights-range-last7").performClick()
            composeRule.waitForIdle()

            assertEquals("selecting another range reads exactly once", 2, source.calls.size)
            assertEquals(LocalDate.of(2026, 9, 8), source.calls.last().startDate)
            assertEquals(LocalDate.of(2026, 9, 14), source.calls.last().endDate)
            val state = viewModel.uiState.value
            assertEquals(InsightsRangeSelection.Last7Days, state.selection)
            assertEquals(InsightsPhase.CONTENT, state.phase)
            assertEquals(2, state.summary!!.recordedIntakeCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun everyMetricRowExposesExactlyOneSemanticFact() {
        setContent(state())
        composeRule.onNodeWithTag("insights-content-list")
            .performScrollToNode(hasTestTag("insights-dose-section"))

        // The merged tree is what TalkBack walks: each row must be exactly one node carrying the
        // localized "label: value" fact, and the child label/value Texts must not appear as their
        // own nodes (that was the B-03 P2 duplication risk).
        fun assertSingleFact(tag: String, label: String, value: String, expected: String) {
            assertEquals("$tag must be one merged node", 1, composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size)
            composeRule.onNodeWithTag(tag).assertContentDescriptionEquals(expected)
            assertEquals(
                "'$label' must not be exposed separately for $tag",
                0,
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().size
            )
            assertEquals(
                "'$value' must not be exposed separately for $tag",
                0,
                composeRule.onAllNodesWithText(value).fetchSemanticsNodes().size
            )
        }

        val intakes = context.getString(R.string.insights_card_recorded_intakes)
        assertSingleFact(
            "insights-card-recorded-intakes",
            intakes,
            "6",
            context.getString(R.string.insights_count_row_description, intakes, 6)
        )

        val manual = context.getString(R.string.history_source_manual)
        assertSingleFact(
            "insights-source-row-MANUAL",
            manual,
            "3",
            context.getString(R.string.insights_count_row_description, manual, 3)
        )

        val high = context.getString(R.string.insights_confidence_high)
        assertSingleFact(
            "insights-confidence-row-HIGH",
            high,
            "3",
            context.getString(R.string.insights_count_row_description, high, 3)
        )

        val e2 = context.getString(R.string.ester_e2)
        val doseText = io.github.yingqiu0871.evolune.history.HistoryFormatting.dose(12.5)
        assertSingleFact(
            "insights-dose-row-E2",
            e2,
            doseText,
            context.getString(R.string.insights_value_row_description, e2, doseText)
        )
    }

    @Test
    fun coverageGoldenShowsOnlyTheTwoCountsAndNeverARatio() {
        setContent(state())

        composeRule.onNodeWithTag("insights-coverage-linked").assertContentDescriptionEquals(
            context.getString(
                R.string.insights_count_row_description,
                context.getString(R.string.insights_coverage_linked),
                4
            )
        )
        composeRule.onNodeWithTag("insights-coverage-unlinked").assertIsDisplayed()
        listOf("%", "4/7", "7/4", "0.57", "57").forEach { token ->
            val matches = composeRule.onAllNodesWithText(token, substring = true).fetchSemanticsNodes()
            assertEquals("coverage must not render '$token'", 0, matches.size)
        }
    }

    @Test
    fun unknownIdentityGoldenIsCountOnlyAndNeverADrugGuess() {
        setContent(state(summary = summary(doses = emptyMap(), unknownIdentity = 3)))

        composeRule.onNodeWithTag("insights-content-list")
            .performScrollToNode(hasTestTag("insights-unknown-identity"))
        composeRule.onNodeWithTag("insights-unknown-identity").assertIsDisplayed()
        listOf("mg", "E2", "EV", "CPA", "spironolactone", "cyproterone", "antiandrogen").forEach { token ->
            val matches = composeRule.onAllNodesWithText(token, substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
            assertEquals("unknown identity must not render '$token'", 0, matches.size)
        }
    }

    @Test
    fun theStateMatrixKeepsOnlyTheAllowedNodesPerPhase() {
        var current by mutableStateOf(phaseState(InsightsPhase.LOADING))
        composeRule.setContent {
            EvoluneTheme { InsightsScreenContent(state = current) }
        }

        // LOADING: selector + loading only
        composeRule.onNodeWithTag("insights-range-selector").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-loading").assertIsDisplayed()
        listOf("insights-coverage-section", "insights-empty", "insights-error", "insights-invalid-range").forEach {
            composeRule.onNodeWithTag(it).assertDoesNotExist()
        }

        // CONTENT: sections, no loading/empty/error/validation
        current = phaseState(InsightsPhase.CONTENT, summary())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights-coverage-section").assertIsDisplayed()
        listOf("insights-loading", "insights-empty", "insights-error", "insights-invalid-range").forEach {
            composeRule.onNodeWithTag(it).assertDoesNotExist()
        }

        // EMPTY: empty copy only
        current = phaseState(
            InsightsPhase.EMPTY,
            summary(
                recordedIntakes = 0, recordedDays = 0, matched = 0, unrecorded = 0, unmatched = 0,
                high = 0, medium = 0, low = 0, sources = emptyMap(), doses = emptyMap(), unknownIdentity = 0
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights-empty").assertIsDisplayed()
        listOf("insights-coverage-section", "insights-error", "insights-invalid-range", "insights-loading").forEach {
            composeRule.onNodeWithTag(it).assertDoesNotExist()
        }

        // ERROR: retry only
        current = phaseState(InsightsPhase.ERROR, null)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights-error").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-retry").assertIsDisplayed()
        listOf("insights-coverage-section", "insights-empty", "insights-invalid-range", "insights-loading").forEach {
            composeRule.onNodeWithTag(it).assertDoesNotExist()
        }

        // INVALID_RANGE: typed copy only, controls stay
        current = phaseState(
            InsightsPhase.INVALID_RANGE,
            null,
            validationError = InsightsRangeValidationError.START_AFTER_END
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights-range-selector").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-invalid-range").assertIsDisplayed()
        listOf("insights-coverage-section", "insights-empty", "insights-error", "insights-loading").forEach {
            composeRule.onNodeWithTag(it).assertDoesNotExist()
        }
    }

    private fun phaseState(
        phase: InsightsPhase,
        summary: MedicationInsightsSummary? = null,
        validationError: InsightsRangeValidationError? = null
    ): InsightsUiState = state(
        phase = phase,
        summary = summary,
        startDate = summary?.startDate ?: today.minusDays(29),
        endDate = summary?.endDate ?: today,
        validationError = validationError,
        failure = if (phase == InsightsPhase.ERROR) {
            InsightsLoadFailure.ReadFailure(IllegalStateException("matrix"))
        } else {
            null
        }
    )

    @Test
    fun largeFontScaleKeepsTheSurfaceReadable() {
        composeRule.setContent {
            EvoluneTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 1.5f)
                ) {
                    InsightsScreenContent(state = state())
                }
            }
        }

        // core nodes must all still exist at 1.5x, with their full metric value
        composeRule.onNodeWithTag("insights-range-selector").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-range-last30").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-card-recorded-intakes").assertIsDisplayed()
        composeRule.onNodeWithTag("insights-card-recorded-intakes")
            .assertContentDescriptionEquals(
                context.getString(
                    R.string.insights_count_row_description,
                    context.getString(R.string.insights_card_recorded_intakes),
                    6
                )
            )
        composeRule.onNodeWithTag("insights-coverage-section").assertIsDisplayed()

    }

    @Test
    fun theRangeSelectorStaysOperableAtLargeFontScale() {
        var selected: InsightsRangeSelection? = null
        composeRule.setContent {
            EvoluneTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                    InsightsScreenContent(state = state(), onSelectRange = { selected = it })
                }
            }
        }

        composeRule.onNodeWithTag("insights-range-last7").performClick()
        composeRule.waitForIdle()
        assertEquals(InsightsRangeSelection.Last7Days, selected)
    }

    @Test
    fun noForbiddenMetricCopyIsRendered() {
        setContent(state(summary = summary(timezoneDerived = true)))

        listOf("adherence", "compliance", "completion", "missed", "skipped", "on time", "late", "delay", "%")
            .forEach { token ->
                val matches = composeRule
                    .onAllNodesWithText(token, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes()
                assertEquals("the surface must not render '$token'", 0, matches.size)
            }
    }

    /** Counting seam: records the exact range arguments and returns one day with two facts. */
    private class CountingRangeSource : HistoryRangeSource {
        data class Call(val startDate: LocalDate, val endDate: LocalDate)

        val calls = mutableListOf<Call>()

        override suspend fun read(
            startDate: LocalDate,
            endDate: LocalDate,
            displayZone: ZoneId,
            now: Instant
        ): HistoricalRange {
            calls += Call(startDate, endDate)
            val date = endDate
            return HistoricalRange(
                startDate = startDate,
                endDate = endDate,
                days = listOf(
                    testDay(
                        date = date,
                        entries = listOf(
                            matchedEntry(
                                occurrence = testOccurrence(slotId = 1L, date = date, time = LocalTime.of(8, 0)),
                                event = testEvent(
                                    id = 1L,
                                    slotId = UUID(1L, 1L),
                                    localDate = date,
                                    occurredAt = date.atTime(8, 5).toInstant(ZoneOffset.UTC)
                                ),
                                displayDate = date
                            ),
                            unmatchedEntry(
                                event = testEvent(
                                    id = 2L,
                                    occurredAt = date.atTime(9, 5).toInstant(ZoneOffset.UTC),
                                    localDate = null,
                                    zoneId = null
                                ),
                                displayDate = date
                            ),
                            unrecordedEntry(
                                occurrence = testOccurrence(slotId = 3L, date = date, time = LocalTime.of(16, 0)),
                                displayDate = date
                            )
                        )
                    )
                )
            )
        }
    }
}
