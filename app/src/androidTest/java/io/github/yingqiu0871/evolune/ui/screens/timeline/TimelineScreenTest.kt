package io.github.yingqiu0871.evolune.ui.screens.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.HistoryFormatting
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeState
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * V17-D-04 §32 — Timeline screen behaviour on a device: seven-state rendering, month controls, day
 * selection, truthful row families, identity and the 12/24h presentation.
 */
@RunWith(AndroidJUnit4::class)
class TimelineScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private var previousClicks = 0
    private var nextClicks = 0
    private var retryClicks = 0
    private var returnClicks = 0
    private var selectedDates = mutableListOf<LocalDate>()

    private fun setContent(
        state: TimelineRangeState,
        is24Hour: Boolean = true
    ) {
        composeRule.setContent {
            EvoluneTheme {
                TimelineScreenContent(
                    state = state,
                    is24Hour = is24Hour,
                    onPreviousMonth = { previousClicks++ },
                    onNextMonth = { nextClicks++ },
                    onSelectDate = { selectedDates += it },
                    onRetry = { retryClicks++ },
                    onReturnToCurrentMonth = { returnClicks++ }
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertTextExists(text: String, substring: Boolean = false) {
        assertTrue(
            "expected text '$text' to be rendered",
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun scrollStripTo(date: java.time.LocalDate) {
        composeRule.onNodeWithTag("timeline-day-strip")
            .performScrollToNode(hasTestTag("timeline-day-cell-$date"))
        composeRule.waitForIdle()
    }

    /** V17-D-05: a grouped side speaks ONE phrase (label, time, identity, dose). */
    private fun assertSidePhraseContains(sideTag: String, vararg expectedParts: String) {
        val phrases = composeRule.onAllNodesWithTag(sideTag)
            .fetchSemanticsNodes()
            .map { node ->
                node.config[SemanticsProperties.ContentDescription].joinToString(separator = " ")
            }
        assertTrue("expected at least one '$sideTag' node", phrases.isNotEmpty())
        assertTrue(
            "expected one '$sideTag' phrase to contain ${expectedParts.toList()}; got $phrases",
            phrases.any { phrase -> expectedParts.all { phrase.contains(it) } }
        )
    }

    // ---------- seven-state mapping ----------

    @Test
    fun loadingStateRendersTheLoadingRegion() {
        setContent(TimelineTestStates.loadingState())
        composeRule.onNodeWithTag("timeline-loading").assertExists()
        composeRule.onNodeWithTag("timeline-day-strip").assertDoesNotExist()
    }

    @Test
    fun loadingStateWithRangeKeepsTheCalendarUsable() {
        setContent(TimelineTestStates.loadingState(withRange = true))
        composeRule.onNodeWithTag("timeline-loading").assertExists()
        composeRule.onNodeWithTag("timeline-day-strip").assertExists()
        composeRule.onNodeWithTag("timeline-month-title").assertExists()
    }

    @Test
    fun emptyRangeRendersTheNeutralMonthNotice() {
        setContent(TimelineTestStates.emptyRangeState())
        composeRule.onNodeWithTag("timeline-empty-range").assertExists()
        assertTextExists(context.getString(R.string.timeline_empty_range))
        composeRule.onNodeWithTag("timeline-day-strip").assertExists()
    }

    @Test
    fun emptyDayKeepsOtherMonthSectionsVisible() {
        setContent(TimelineTestStates.emptyDayState())
        composeRule.onNodeWithTag("timeline-empty-day").assertExists()
        composeRule.onNodeWithTag("timeline-section-2026-09-16").assertExists()
    }

    @Test
    fun invalidRequestOffersARecoverableReturnToTheCurrentMonth() {
        setContent(TimelineTestStates.invalidRequestState())
        composeRule.onNodeWithTag("timeline-invalid-request").assertExists()
        composeRule.onNodeWithTag("timeline-return-current-month").performClick()
        assertEquals(1, returnClicks)
    }

    @Test
    fun notLoadableOffersARecoverableReturnToTheCurrentMonth() {
        setContent(TimelineTestStates.notLoadableState())
        composeRule.onNodeWithTag("timeline-not-loadable").assertExists()
        composeRule.onNodeWithTag("timeline-return-current-month").performClick()
        assertEquals(1, returnClicks)
    }

    @Test
    fun errorOffersRetry() {
        setContent(TimelineTestStates.errorState())
        composeRule.onNodeWithTag("timeline-error").assertExists()
        composeRule.onNodeWithTag("timeline-retry").performClick()
        assertEquals(1, retryClicks)
    }

    @Test
    fun contentRendersAllMonthSectionsAndTheStrip() {
        setContent(TimelineTestStates.contentState())
        composeRule.onNodeWithTag("timeline-section-2026-09-16").assertExists()
        composeRule.onNodeWithTag("timeline-section-2026-09-15").assertExists()
        composeRule.onNodeWithTag("timeline-day-strip").assertExists()
        composeRule.onNodeWithTag("timeline-day-cell-2026-09-16").assertExists()
        scrollStripTo(LocalDate.of(2026, 9, 1))
        composeRule.onNodeWithTag("timeline-day-cell-2026-09-01").assertExists()
        assertTextExists(context.getString(R.string.timeline_month_title, 2026, 9))
    }

    // ---------- month controls ----------

    @Test
    fun nextMonthIsDisabledAtTheCurrentMonthAndPreviousStillWorks() {
        setContent(TimelineTestStates.contentState())
        composeRule.onNodeWithTag("timeline-next-month").assertIsNotEnabled()
        composeRule.onNodeWithTag("timeline-previous-month").assertIsEnabled()
        composeRule.onNodeWithTag("timeline-previous-month").performClick()
        assertEquals(1, previousClicks)
    }

    @Test
    fun nextMonthIsEnabledOnAPastMonthAndInvokesTheCommand() {
        setContent(TimelineTestStates.pastMonthState())
        composeRule.onNodeWithTag("timeline-next-month").assertIsEnabled()
        composeRule.onNodeWithTag("timeline-next-month").performClick()
        assertEquals(1, nextClicks)
    }

    // ---------- day selection ----------

    @Test
    fun daySelectionInvokesTheCallbackWithTheSelectedDate() {
        setContent(TimelineTestStates.contentState())
        scrollStripTo(LocalDate.of(2026, 9, 10))
        composeRule.onNodeWithTag("timeline-day-cell-2026-09-10").performClick()
        assertEquals(listOf(LocalDate.of(2026, 9, 10)), selectedDates)
    }

    @Test
    fun dayCellsCarrySelectionAndTodaySemantics() {
        setContent(TimelineTestStates.contentState())
        composeRule
            .onNodeWithTag("timeline-day-cell-2026-09-16")
            .assertContentDescriptionContains("9月16日", substring = true)
        composeRule
            .onNodeWithTag("timeline-day-cell-2026-09-16")
            .assertContentDescriptionContains(
                context.getString(R.string.timeline_a11y_weekday_wed),
                substring = true
            )
        composeRule
            .onNodeWithTag("timeline-day-cell-2026-09-16")
            .assertIsSelected()
        composeRule
            .onNodeWithTag("timeline-day-cell-2026-09-16")
            .assertContentDescriptionContains(context.getString(R.string.history_cell_today), substring = true)
        scrollStripTo(LocalDate.of(2026, 9, 1))
        composeRule
            .onNodeWithTag("timeline-day-cell-2026-09-01")
            .assertContentDescriptionContains("9月1日", substring = true)
    }

    @Test
    fun stripShowsAdjacentMonthDatesAroundTheVisibleMonth() {
        setContent(TimelineTestStates.contentState())

        // the continuous window reaches into the previous month before the first of the month
        scrollStripTo(LocalDate.of(2026, 9, 1))
        composeRule.onNodeWithTag("timeline-day-cell-2026-08-31").assertIsDisplayed()

        // and into the next month after the trailing edge; future cells stay non-selectable
        scrollStripTo(LocalDate.of(2026, 9, 23))
        composeRule.onNodeWithTag("timeline-day-cell-2026-09-23")
            .assertExists()
            .assertIsNotEnabled()
    }

    @Test
    fun theFirstDayOfTheMonthIsNotTheLeftMostStripItem() {
        setContent(
            TimelineTestStates.contentState().copy(selectedDate = LocalDate.of(2026, 9, 1))
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-day-cell-2026-09-01").assertIsDisplayed()
        val leading = composeRule
            .onNodeWithTag("timeline-day-cell-2026-08-31", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val first = composeRule
            .onNodeWithTag("timeline-day-cell-2026-09-01", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "the preceding adjacent-month date must stay visible to the left of day one",
            leading.left < first.left
        )
    }

    @Test
    fun selectingADayFocusesItsSectionWhileKeepingTheWholeMonthRendered() {
        val target = LocalDate.of(2026, 9, 11)
        var state by mutableStateOf(TimelineTestStates.manyDaysState())
        composeRule.setContent {
            EvoluneTheme {
                TimelineScreenContent(
                    state = state,
                    onSelectDate = { state = state.copy(selectedDate = it) }
                )
            }
        }
        composeRule.waitForIdle()

        assertTrue(
            "the oldest section starts off-screen",
            composeRule.onAllNodesWithTag("timeline-section-$target").fetchSemanticsNodes().isEmpty()
        )

        scrollStripTo(target)
        composeRule.onNodeWithTag("timeline-day-cell-$target").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-section-$target").assertIsDisplayed()
        assertEquals(target, state.selectedDate)
        composeRule.onNodeWithTag("timeline-section-2026-09-16").assertExists()
    }

    // ---------- truthful rows ----------

    @Test
    fun aMatchedRowRendersTwoVisiblyDistinctTruthfulSides() {
        val date = TimelineTestStates.today
        setContent(TimelineTestStates.singleSectionState(date))
        val row = "timeline-row-$date-0"
        composeRule.onNodeWithTag(row).assertExists()
        composeRule.onNodeWithTag("timeline-schedule-side", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("timeline-recorded-side", useUnmergedTree = true).assertExists()
        // V17-D-05: each side is ONE grouped accessibility node speaking label → time →
        // identity → dose with natural separators (no reliance on the visible “·”).
        assertSidePhraseContains(
            "timeline-schedule-side",
            context.getString(R.string.history_label_current_schedule_context),
            "08:00",
            "雌二醇",
            "2.0 mg"
        )
        assertSidePhraseContains(
            "timeline-recorded-side",
            context.getString(R.string.history_label_actual_time),
            "08:05",
            "雌二醇",
            "3.0 mg"
        )
    }

    @Test
    fun anUnrecordedRowRendersTheScheduleSideAndTheNeutralNoticeOnly() {
        val date = LocalDate.of(2026, 9, 14)
        val state = TimelineTestStates.state(
            days = TimelineTestStates.daysFrom(date to listOf(TimelineTestStates.unrecordedRow(date, slotId = 401L)))
        )
        setContent(state)
        composeRule.onNodeWithTag("timeline-schedule-side", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("timeline-recorded-side", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("timeline-no-recorded-intake", useUnmergedTree = true).assertExists()
        assertTextExists(context.getString(R.string.timeline_no_recorded_intake))
        assertSidePhraseContains(
            "timeline-schedule-side",
            context.getString(R.string.history_label_current_schedule_context)
        )
    }

    @Test
    fun anUnmatchedRowRendersTheRecordedSideOnly() {
        val date = TimelineTestStates.today
        val state = TimelineTestStates.state(
            days = TimelineTestStates.daysFrom(date to listOf(TimelineTestStates.unmatchedRow(date, id = 402L)))
        )
        setContent(state)
        composeRule.onNodeWithTag("timeline-recorded-side", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("timeline-schedule-side", useUnmergedTree = true).assertDoesNotExist()
        assertSidePhraseContains("timeline-recorded-side", "戊酸雌二醇", "5.0 mg")
    }

    @Test
    fun identityStatusesRenderCanonicalPartialAndUnavailableWording() {
        val knownDate = LocalDate.of(2026, 9, 12)
        setContent(
            TimelineTestStates.state(
                days = TimelineTestStates.daysFrom(
                    knownDate to listOf(
                        TimelineTestStates.unrecordedRow(
                            knownDate,
                            slotId = 411L,
                            medicationKey = "E2"
                        )
                    )
                )
            )
        )
        assertSidePhraseContains("timeline-schedule-side", "雌二醇", "2.0 mg")
    }

    @Test
    fun aPartialIdentityRendersTheNeutralIncompleteWording() {
        val date = LocalDate.of(2026, 9, 12)
        setContent(
            TimelineTestStates.state(
                days = TimelineTestStates.daysFrom(
                    date to listOf(
                        TimelineTestStates.unrecordedRow(date, slotId = 412L, medicationKey = "MYSTERY")
                    )
                )
            )
        )
        assertSidePhraseContains(
            "timeline-schedule-side",
            context.getString(R.string.timeline_identity_partial)
        )
        assertTrue(
            "a partial identity must never guess an ester name",
            composeRule.onAllNodesWithText("雌二醇", substring = true).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithContentDescription("雌二醇", substring = true).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun anUnavailableIdentityRendersTheNeutralUnavailableWording() {
        val date = LocalDate.of(2026, 9, 12)
        setContent(
            TimelineTestStates.state(
                days = TimelineTestStates.daysFrom(
                    date to listOf(
                        TimelineTestStates.unrecordedRow(date, slotId = 413L, routeKey = "ANTIANDROGEN")
                    )
                )
            )
        )
        assertSidePhraseContains(
            "timeline-schedule-side",
            context.getString(R.string.timeline_identity_unavailable)
        )
        assertTrue(
            "an unavailable identity must never be mapped to an ester",
            composeRule.onAllNodesWithText("雌二醇", substring = true).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithContentDescription("雌二醇", substring = true).fetchSemanticsNodes().isEmpty()
        )
    }

    // ---------- 12/24h presentation ----------

    @Test
    fun theTwentyFourHourModeFormatsTheTimelineTimestamps() {
        setContent(TimelineTestStates.contentState(), is24Hour = true)
        val expected = HistoryFormatting.timeText(TimelineTestStates.matchedInstant, TimelineTestStates.utc, true)
        assertSidePhraseContains("timeline-schedule-side", expected)
    }

    @Test
    fun theTwelveHourModeChangesNotationOnly() {
        setContent(TimelineTestStates.contentState(), is24Hour = false)
        val expected = HistoryFormatting.timeText(TimelineTestStates.matchedInstant, TimelineTestStates.utc, false)
        assertSidePhraseContains("timeline-schedule-side", expected)
        val twentyFour = HistoryFormatting.timeText(TimelineTestStates.matchedInstant, TimelineTestStates.utc, true)
        assertTrue("12h and 24h renderings must differ", expected != twentyFour)
    }

    // ---------- published-state-only rendering ----------

    @Test
    fun aStateChangeReRendersPublishedContentWithoutAnyLocalPendingOverlay() {
        var state by mutableStateOf(TimelineTestStates.loadingState(withRange = true))
        composeRule.setContent {
            EvoluneTheme {
                TimelineScreenContent(state = state)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-loading").assertExists()

        state = TimelineTestStates.contentState()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-loading").assertDoesNotExist()
        composeRule.onNodeWithTag("timeline-section-2026-09-16").assertExists()
    }

    @Test
    fun allTimelineRowCardsStayWithinTheirSection() {
        // One compact section keeps every card on screen so the counts are composition-stable.
        val date = TimelineTestStates.today
        setContent(
            TimelineTestStates.state(
                days = TimelineTestStates.daysFrom(
                    date to listOf(
                        TimelineTestStates.matchedRow(date, slotId = 421L),
                        TimelineTestStates.unrecordedRow(date, slotId = 422L)
                    )
                )
            )
        )
        val scheduleSides = composeRule.onAllNodesWithTag("timeline-schedule-side", useUnmergedTree = true)
            .fetchSemanticsNodes()
        val recordedSides = composeRule.onAllNodesWithTag("timeline-recorded-side", useUnmergedTree = true)
            .fetchSemanticsNodes()
        val noIntake = composeRule.onAllNodesWithTag("timeline-no-recorded-intake", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals("matched + unrecorded schedule sides", 2, scheduleSides.size)
        assertEquals("matched recorded side only", 1, recordedSides.size)
        assertEquals("one neutral notice", 1, noIntake.size)
    }
}
