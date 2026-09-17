package io.github.yingqiu0871.evolune.ui.screens.retrospective

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceId
import io.github.yingqiu0871.evolune.history.HistoryFormatting
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkInputSummary
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkLimitation
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkModelContext
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkPoint
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkResult
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSeries
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkUnavailableReason
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkWindow
import io.github.yingqiu0871.evolune.history.retrospective.IntakeMarkerProvenance
import io.github.yingqiu0871.evolune.history.retrospective.RecordedIntakeMarker
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveLoadFailure
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveMarker
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePhase
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePkRange
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePkUiState
import io.github.yingqiu0871.evolune.history.retrospective.ScheduleContextMarker
import io.github.yingqiu0871.evolune.history.retrospective.ScheduleMarkerProvenance
import io.github.yingqiu0871.evolune.pk.SimulationResult
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * V17-C-04 §14.10 N1-N6 — the retrospective surface on a device.
 *
 * The stateless content is driven with synthetic frozen state, exactly like the Insights surface.
 */
@RunWith(AndroidJUnit4::class)
class RetrospectiveScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneOffset.UTC
    private val end: Instant = Instant.parse("2026-09-16T12:00:00Z")
    private val window = RetrospectivePkWindow(end.minus(Duration.ofDays(30)), end)

    private fun available(
        markers: List<RetrospectiveMarker> = emptyList(),
        limitations: Set<RetrospectivePkLimitation> = emptySet()
    ): RetrospectivePkResult.Available {
        val series = RetrospectivePkSeries(
            startInclusive = window.startInclusive,
            endInclusive = window.endInclusive,
            points = listOf(
                RetrospectivePkPoint(window.startInclusive, 0.0),
                RetrospectivePkPoint(window.startInclusive.plus(Duration.ofDays(15)), 12.0),
                RetrospectivePkPoint(window.endInclusive, 4.0)
            )
        )
        return RetrospectivePkResult.Available(
            calculatedInterval = window,
            series = series,
            cursorEstimate = null,
            curve = SimulationResult(
                timeH = listOf(0.0, 360.0, 720.0),
                concPGmL = listOf(0.0, 12.0, 4.0),
                auc = 0.0
            ),
            modelContext = RetrospectivePkModelContext.current(55.0, window.endInclusive),
            summary = RetrospectivePkInputSummary(
                lookbackStart = window.startInclusive,
                upperBoundInclusive = window.endInclusive,
                engineInputEventIds = emptyList(),
                concentrationProducingEventIds = emptyList(),
                patchControlEventIds = emptyList()
            ),
            exclusions = emptyList(),
            limitations = limitations
        )
    }

    private fun state(
        phase: RetrospectivePhase,
        result: RetrospectivePkResult? = null,
        markers: List<RetrospectiveMarker> = emptyList(),
        failure: RetrospectiveLoadFailure? = null,
        selectedRange: RetrospectivePkRange = RetrospectivePkRange.LAST_30_DAYS
    ) = RetrospectivePkUiState(
        windowStart = window.startInclusive,
        windowEnd = window.endInclusive,
        displayZone = zone,
        selectedRange = selectedRange,
        phase = phase,
        result = result,
        markers = markers,
        failure = failure
    )

    private fun setContent(
        state: RetrospectivePkUiState,
        onRetry: () -> Unit = {},
        onSelectRange: (RetrospectivePkRange) -> Unit = {}
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                EvoluneTheme {
                    RetrospectivePkScreenContent(
                        state = state,
                        onRetry = onRetry,
                        onSelectRange = onSelectRange
                    )
                }
            }
        }
    }

    private fun markersOf(): List<RetrospectiveMarker> = listOf(
        ScheduleContextMarker(
            occurrenceId = MedicationOccurrenceId(UUID(1L, 1L)),
            scheduledAt = window.startInclusive.plus(Duration.ofDays(3)),
            provenance = ScheduleMarkerProvenance.UNRECORDED_OCCURRENCE
        ),
        RecordedIntakeMarker(
            eventId = UUID(2L, 2L),
            occurredAt = window.startInclusive.plus(Duration.ofDays(4)),
            provenance = IntakeMarkerProvenance.MATCHED_INTAKE
        )
    )

    @Test
    fun loadingStateShowsTheMandatoryDisclosureWindowAndSpinner() {
        setContent(state(RetrospectivePhase.LOADING))
        composeRule.onNodeWithTag("retrospective-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-disclosure").assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-window-caption").assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-loading").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retrospective_disclosure)).assertIsDisplayed()
    }

    @Test
    fun contentStateShowsChartMandatoryLabelsAndTruthfulLegendDisclosure() {
        setContent(state(RetrospectivePhase.CONTENT, result = available(), markers = markersOf()))

        composeRule.onNodeWithTag("retrospective-chart").assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-marker-legend").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-marker-disclosure").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-legend-schedule-label")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-legend-schedule-label")
            .assertTextContains(context.getString(R.string.retrospective_marker_schedule_context), substring = true)
        composeRule.onNodeWithTag("retrospective-legend-intake-label")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-legend-intake-label")
            .assertTextContains(context.getString(R.string.retrospective_marker_recorded_intake), substring = true)
        composeRule.onNodeWithText(context.getString(R.string.retrospective_marker_legend_disclosure))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun chartExposesTheModelEstimateDisclosureAsItsAccessibilityDescription() {
        setContent(state(RetrospectivePhase.CONTENT, result = available()))
        composeRule.onNodeWithTag("retrospective-chart")
            .assertContentDescriptionEquals(context.getString(R.string.retrospective_disclosure))
    }

    // ---------- v1.7.1 UI hotfix: range selector and range-aware caption ----------

    @Test
    fun rangeSelectorOffersAllThreeWindowsWithTheSelectedOneMarked() {
        setContent(
            state(
                RetrospectivePhase.CONTENT,
                result = available(),
                selectedRange = RetrospectivePkRange.LAST_7_DAYS
            )
        )

        composeRule.onNodeWithTag("retrospective-range-selector").assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-range-7").assertIsSelected()
        composeRule.onNodeWithTag("retrospective-range-30").assertExists()
        composeRule.onNodeWithTag("retrospective-range-90").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.retrospective_range_7_days)).assertIsDisplayed()
    }

    @Test
    fun rangeSelectorInvokesTheSelectionCallback() {
        val selected = mutableListOf<RetrospectivePkRange>()
        setContent(
            state(
                RetrospectivePhase.CONTENT,
                result = available(),
                selectedRange = RetrospectivePkRange.LAST_7_DAYS
            ),
            onSelectRange = { selected += it }
        )

        composeRule.onNodeWithTag("retrospective-range-90").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue("the selected range must reach the callback", selected == listOf(RetrospectivePkRange.LAST_90_DAYS))
        }
    }

    @Test
    fun windowCaptionNamesTheSelectedRangeLength() {
        setContent(
            state(RetrospectivePhase.LOADING, selectedRange = RetrospectivePkRange.LAST_90_DAYS)
        )

        val expected = context.getString(
            R.string.retrospective_window_caption,
            90,
            HistoryFormatting.fullDateTimeText(window.endInclusive, zone, true)
        )
        composeRule.onNodeWithTag("retrospective-window-caption")
            .assertTextContains(expected, substring = true)
    }

    @Test
    fun limitationCopyIsRenderedWhenTheResultCarriesLimitations() {
        setContent(
            state(
                RetrospectivePhase.CONTENT,
                result = available(
                    limitations = setOf(
                        RetrospectivePkLimitation.EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE,
                        RetrospectivePkLimitation.AMBIGUOUS_PATCH_PAIRING
                    )
                )
            )
        )
        composeRule.onNodeWithTag("retrospective-limitations").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.retrospective_limitation_zero_baseline)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.retrospective_limitation_ambiguous_patch)
        ).assertIsDisplayed()
    }

    private fun unavailableState(reason: RetrospectivePkUnavailableReason) = state(
        RetrospectivePhase.UNAVAILABLE,
        result = RetrospectivePkResult.Unavailable(
            reason = reason,
            modelContext = RetrospectivePkModelContext.current(55.0, window.endInclusive),
            summary = RetrospectivePkInputSummary(
                lookbackStart = null,
                upperBoundInclusive = window.endInclusive,
                engineInputEventIds = emptyList(),
                concentrationProducingEventIds = emptyList(),
                patchControlEventIds = emptyList()
            ),
            exclusions = emptyList(),
            limitations = emptySet()
        )
    )

    private fun assertUnavailableCopy(reason: RetrospectivePkUnavailableReason, res: Int) {
        setContent(unavailableState(reason))
        composeRule.onNodeWithTag("retrospective-unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(res)).assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-retry").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unavailableNoEligibleIntakesRendersItsFrozenCopyWithRetry() {
        assertUnavailableCopy(
            RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES,
            R.string.retrospective_unavailable_no_eligible_intakes
        )
    }

    @Test
    fun unavailableInvalidIntervalRendersItsFrozenCopyWithRetry() {
        assertUnavailableCopy(
            RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL,
            R.string.retrospective_unavailable_invalid_interval
        )
    }

    @Test
    fun unavailableDefensiveReasonRendersTheGenericCopyWithRetry() {
        assertUnavailableCopy(
            RetrospectivePkUnavailableReason.QUERY_OUTSIDE_CALCULATED_INTERVAL,
            R.string.retrospective_unavailable_generic
        )
    }

    @Test
    fun unavailableHistoryInputRendersItsFrozenCopyWithRetry() {
        assertUnavailableCopy(
            RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE,
            R.string.retrospective_unavailable_history_unavailable
        )
    }

    @Test
    fun errorStateRendersGenericCopyAndRetryInvokesTheCallback() {
        var retried = false
        setContent(
            state(RetrospectivePhase.ERROR, failure = RetrospectiveLoadFailure.InvalidBodyWeight),
            onRetry = { retried = true }
        )
        composeRule.onNodeWithTag("retrospective-error").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retrospective_unavailable_generic)).assertIsDisplayed()
        composeRule.onNodeWithTag("retrospective-retry").performClick()
        assertTrue(retried)
    }

    // ---------- T4: mandatory model-estimate disclosure in all three required states ----------

    /**
     * The disclosure is production-rendered outside the phase-specific body, so it must be
     * visibly present in CONTENT, UNAVAILABLE and ERROR (not only in resources, semantics or
     * another state). These assertions check on-screen visibility of the actual node.
     */
    private fun assertMandatoryDisclosureVisible() {
        composeRule.onNodeWithTag("retrospective-disclosure").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retrospective_disclosure)).assertIsDisplayed()
    }

    @Test
    fun t4ContentStateShowsTheMandatoryModelEstimateDisclosure() {
        setContent(state(RetrospectivePhase.CONTENT, result = available(), markers = markersOf()))
        composeRule.onNodeWithTag("retrospective-chart").assertIsDisplayed()
        assertMandatoryDisclosureVisible()
    }

    @Test
    fun t4UnavailableStateShowsTheMandatoryModelEstimateDisclosure() {
        setContent(unavailableState(RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES))
        composeRule.onNodeWithTag("retrospective-unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.retrospective_unavailable_no_eligible_intakes)
        ).assertIsDisplayed()
        assertMandatoryDisclosureVisible()
    }

    @Test
    fun t4ErrorStateShowsTheMandatoryModelEstimateDisclosure() {
        setContent(state(RetrospectivePhase.ERROR, failure = RetrospectiveLoadFailure.InvalidBodyWeight))
        composeRule.onNodeWithTag("retrospective-error").assertIsDisplayed()
        assertMandatoryDisclosureVisible()
    }
}
