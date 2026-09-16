package io.github.yingqiu0871.evolune.ui.screens.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.HistoryUiState
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeState
import io.github.yingqiu0871.evolune.ui.screens.HistoryScreenContent
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * V17-D-05 §38 — executed accessibility-tree behaviour for the shipped Timeline surface.
 *
 * These tests assert the REAL Compose semantics tree (roles, headings, states, merged node
 * counts, grouped phrases) — never source text alone. Contract: A11Y1–A11Y21.
 */
@RunWith(AndroidJUnit4::class)
class TimelineAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var selectedDates = mutableListOf<LocalDate>()

    private fun setContent(state: TimelineRangeState, is24Hour: Boolean = true) {
        composeRule.setContent {
            EvoluneTheme {
                TimelineScreenContent(
                    state = state,
                    is24Hour = is24Hour,
                    onSelectDate = { selectedDates += it }
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollStripTo(date: LocalDate) {
        composeRule.onNodeWithTag("timeline-day-strip")
            .performScrollToNode(hasTestTag("timeline-day-cell-$date"))
        composeRule.waitForIdle()
    }

    private fun centerOf(tag: String): Offset {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return Offset((bounds.left + bounds.right).value / 2f, (bounds.top + bounds.bottom).value / 2f)
    }

    private fun sizeOf(tag: String): Pair<Float, Float> {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return Pair((bounds.right - bounds.left).value, (bounds.bottom - bounds.top).value)
    }

    // ---------- entry card ----------

    @Test
    fun entryCardIsOneButtonNodeWithSilentDecorativeIcon() {
        val historyState = HistoryUiState(
            visibleMonth = YearMonth.of(2026, 9),
            selectedDate = LocalDate.of(2026, 9, 16),
            today = LocalDate.of(2026, 9, 16),
            displayZone = ZoneOffset.UTC,
            loadedMonth = YearMonth.of(2026, 9),
            loadedDays = emptyMap(),
            loading = false,
            failed = false
        )
        composeRule.setContent {
            EvoluneTheme {
                HistoryScreenContent(state = historyState)
            }
        }
        composeRule.waitForIdle()

        val entry = composeRule.onNode(
            hasTestTag("history-timeline-entry") and hasClickAction()
        )
        entry.assertExists()
        entry.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        entry.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        composeRule.onAllNodesWithText(context.getString(R.string.timeline_entry_title)).fetchSemanticsNodes()
            .let { assertTrue("entry title must be announced", it.isNotEmpty()) }
        composeRule.onAllNodesWithText(context.getString(R.string.timeline_entry_subtitle)).fetchSemanticsNodes()
            .let { assertTrue("entry subtitle must be announced", it.isNotEmpty()) }
        val onClickLabel = entry.fetchSemanticsNode().config[SemanticsActions.OnClick].label
        assertEquals(context.getString(R.string.timeline_entry_action), onClickLabel)
        val (width, height) = sizeOf("history-timeline-entry")
        assertTrue("entry card hit target >= 48dp", width >= 48f && height >= 48f)
    }

    // ---------- headings ----------

    @Test
    fun monthTitleIsAHeading() {
        setContent(TimelineTestStates.contentState())
        composeRule.onNodeWithTag("timeline-month-title")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun sectionHeadersAreHeadingsAndAbsoluteHeaderSpeaksOneFullWeekdayPhrase() {
        val absoluteDate = LocalDate.of(2026, 9, 12)
        setContent(
            TimelineTestStates.state(
                days = TimelineTestStates.daysFrom(
                    absoluteDate to listOf(
                        TimelineTestStates.unrecordedRow(absoluteDate, slotId = 631L)
                    ),
                    TimelineTestStates.today to listOf(
                        TimelineTestStates.unrecordedRow(TimelineTestStates.today, slotId = 632L)
                    )
                )
            )
        )

        // Relative header (today): visible text is the spoken content.
        composeRule.onNodeWithTag("timeline-section-header-2026-09-16")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithTag("timeline-section-header-2026-09-16")
            .assertTextEquals(context.getString(R.string.timeline_today))

        // Absolute header: heading + exactly ONE full-weekday phrase (no visible short form).
        composeRule.onNodeWithTag("timeline-section-header-2026-09-12")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithTag("timeline-section-header-2026-09-12")
            .assertContentDescriptionEquals("2026年9月12日，星期六")
        composeRule.onNodeWithTag("timeline-section-header-2026-09-12")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))

        // §39 single-phrase proof: the visible short-weekday form is not exposed as a second
        // spoken node; exactly one node carries the header's tag and phrase.
        val shortForm = context.getString(
            R.string.timeline_section_date_weekday,
            "2026年9月12日",
            context.getString(R.string.history_weekday_sat)
        )
        assertTrue(
            "visible short-weekday form must not be announced",
            composeRule.onAllNodesWithText(shortForm).fetchSemanticsNodes().isEmpty()
        )
        assertEquals(
            "exactly one section-header node",
            1,
            composeRule.onAllNodesWithTag("timeline-section-header-2026-09-12").fetchSemanticsNodes().size
        )
    }

    // ---------- day cell ----------

    @Test
    fun dayCellIsExactlyOneButtonNodeWithChildrenExcludedFromTheAccessibilityTree() {
        setContent(TimelineTestStates.contentState())

        composeRule.onNode(hasTestTag("timeline-day-cell-2026-09-16") and hasClickAction())
            .assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithTag("timeline-day-cell-2026-09-16").assertIsSelected()

        assertEquals(
            "exactly one day-cell accessibility node",
            1,
            composeRule.onAllNodesWithTag("timeline-day-cell-2026-09-16").fetchSemanticsNodes().size
        )
        assertTrue(
            "weekday visual child must not be a separate spoken node",
            composeRule.onAllNodesWithText("三").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "date-number visual child must not be a separate spoken node",
            composeRule.onAllNodesWithText("16").fetchSemanticsNodes().isEmpty()
        )

        // Geometry/test tags remain queryable in the unmerged tree (D-04 proof preserved).
        composeRule.onNodeWithTag("timeline-day-weekday-2026-09-16", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("timeline-day-highlight-2026-09-16", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("timeline-day-number-2026-09-16", useUnmergedTree = true).assertExists()
    }

    @Test
    fun dayCellSpeaksLocalizedNonIsoGrammarWithFullWeekday() {
        setContent(TimelineTestStates.contentState())

        composeRule.onNodeWithTag("timeline-day-cell-2026-09-16")
            .assertContentDescriptionEquals("9月16日，星期三，今天")
        scrollStripTo(LocalDate.of(2026, 9, 10))
        composeRule.onNodeWithTag("timeline-day-cell-2026-09-10")
            .assertContentDescriptionEquals("9月10日，星期四")
    }

    @Test
    fun defensiveFutureCellsExposeDisabledSemanticsAndNeverInvokeSelection() {
        val future = TimelineTestStates.today.plusDays(1)
        setContent(TimelineTestStates.defensiveFutureState())
        scrollStripTo(future)

        composeRule.onNodeWithTag("timeline-day-cell-$future")
            .assertExists()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("timeline-day-cell-$future")
            .assertContentDescriptionContains("9月17日", substring = true)
        composeRule.onNodeWithTag("timeline-day-cell-$future")
            .assertContentDescriptionContains(
                context.getString(R.string.history_cell_not_arrived),
                substring = true
            )

        composeRule.onNodeWithTag("timeline-day-cell-$future").performClick()
        composeRule.waitForIdle()
        assertTrue("a non-selectable cell must not invoke selection", selectedDates.isEmpty())
    }

    // ---------- rows ----------

    @Test
    fun matchedRowExposesExactlyTwoSideGroupsWithNaturalSeparators() {
        setContent(TimelineTestStates.singleSectionState(TimelineTestStates.today))

        assertEquals(
            "exactly one schedule group",
            1,
            composeRule.onAllNodesWithTag("timeline-schedule-side").fetchSemanticsNodes().size
        )
        assertEquals(
            "exactly one recorded group",
            1,
            composeRule.onAllNodesWithTag("timeline-recorded-side").fetchSemanticsNodes().size
        )
        composeRule.onNodeWithTag("timeline-schedule-side")
            .assertContentDescriptionEquals("当前方案时间，08:00，雌二醇，2.0 mg")
        composeRule.onNodeWithTag("timeline-recorded-side")
            .assertContentDescriptionEquals("实际时间，08:05，雌二醇，3.0 mg")
        assertTrue(
            "grouped side children must not remain separate spoken stops",
            composeRule.onAllNodesWithText("08:00").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun unrecordedRowExposesScheduleGroupAndNeutralNoticeOnly() {
        val date = LocalDate.of(2026, 9, 14)
        setContent(
            TimelineTestStates.state(
                days = TimelineTestStates.daysFrom(
                    date to listOf(TimelineTestStates.unrecordedRow(date, slotId = 621L))
                )
            )
        )
        composeRule.onNodeWithTag("timeline-schedule-side").assertExists()
        composeRule.onNodeWithTag("timeline-no-recorded-intake")
            .assertExists()
            .assertTextEquals(context.getString(R.string.timeline_no_recorded_intake))
        assertEquals(
            "no recorded side may be synthesized",
            0,
            composeRule.onAllNodesWithTag("timeline-recorded-side").fetchSemanticsNodes().size
        )
    }

    @Test
    fun unmatchedRowExposesRecordedGroupOnly() {
        val date = TimelineTestStates.today
        setContent(
            TimelineTestStates.state(
                days = TimelineTestStates.daysFrom(
                    date to listOf(TimelineTestStates.unmatchedRow(date, id = 622L))
                )
            )
        )
        composeRule.onNodeWithTag("timeline-recorded-side").assertExists()
        assertEquals(
            "no schedule group may be synthesized",
            0,
            composeRule.onAllNodesWithTag("timeline-schedule-side").fetchSemanticsNodes().size
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("当前方案时间", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    // ---------- seven states / actions / live regions ----------

    @Test
    fun sevenStatesExposeMeaningfulTargetsAndDistinctActionLabels() {
        var state by mutableStateOf(TimelineTestStates.loadingState(withRange = true))
        composeRule.setContent {
            EvoluneTheme {
                TimelineScreenContent(state = state)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-loading").assertExists()

        state = TimelineTestStates.emptyRangeState()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-empty-range").assertExists()

        state = TimelineTestStates.emptyDayState()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-empty-day").assertExists()

        state = TimelineTestStates.invalidRequestState()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-invalid-request").assertExists()
        composeRule.onNodeWithTag("timeline-return-current-month").assertExists()

        state = TimelineTestStates.notLoadableState()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-not-loadable").assertExists()

        state = TimelineTestStates.errorState()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-error").assertExists()
        composeRule.onNodeWithTag("timeline-retry").assertExists()

        assertTrue(
            "retry and return-to-month labels must be distinct",
            context.getString(R.string.timeline_retry) !=
                context.getString(R.string.timeline_return_current_month)
        )
        assertTrue(
            "no automatic live-region announcements may exist",
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun selectionAndMonthChangeDoNotMoveAccessibilityFocus() {
        var state by mutableStateOf(TimelineTestStates.contentState())
        composeRule.setContent {
            EvoluneTheme {
                TimelineScreenContent(
                    state = state,
                    onSelectDate = { state = state.copy(selectedDate = it) },
                    onPreviousMonth = { }
                )
            }
        }
        composeRule.waitForIdle()

        val focusedBefore = composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
            .fetchSemanticsNodes()
            .size

        scrollStripTo(LocalDate.of(2026, 9, 10))
        composeRule.onNodeWithTag("timeline-day-cell-2026-09-10").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-previous-month").performClick()
        composeRule.waitForIdle()

        val focusedAfter = composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
            .fetchSemanticsNodes()
            .size
        assertEquals(
            "selection/month change must not programmatically move a11y focus",
            focusedBefore,
            focusedAfter
        )
        assertEquals(0, focusedAfter)
    }

    // ---------- hit targets ----------

    /**
     * Measures the platform TOUCH target (touchBoundsInRoot) — the effective interactive area,
     * including Material minimum-interactive-component expansion — converted to dp.
     */
    private fun touchSizeOf(tag: String): Pair<Float, Float> {
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
        val density = composeRule.density
        return Pair(
            with(density) { (bounds.right - bounds.left).toDp().value },
            with(density) { (bounds.bottom - bounds.top).toDp().value }
        )
    }

    @Test
    fun interactiveTargetsAreAtLeast48dp() {
        var state by mutableStateOf(TimelineTestStates.contentState())
        composeRule.setContent {
            EvoluneTheme {
                TimelineScreenContent(state = state)
            }
        }
        composeRule.waitForIdle()

        listOf(
            "timeline-previous-month",
            "timeline-next-month",
            "timeline-day-cell-2026-09-16"
        ).forEach { tag ->
            val (width, height) = touchSizeOf(tag)
            assertTrue("$tag touch width >= 48dp (was $width)", width >= 48f)
            assertTrue("$tag touch height >= 48dp (was $height)", height >= 48f)
        }

        state = TimelineTestStates.errorState()
        composeRule.waitForIdle()
        val (retryWidth, retryHeight) = touchSizeOf("timeline-retry")
        assertTrue("retry touch target >= 48dp (was ${retryWidth}x$retryHeight)", retryWidth >= 48f && retryHeight >= 48f)

        state = TimelineTestStates.invalidRequestState()
        composeRule.waitForIdle()
        val (returnWidth, returnHeight) = touchSizeOf("timeline-return-current-month")
        assertTrue(
            "return-to-month touch target >= 48dp (was ${returnWidth}x$returnHeight)",
            returnWidth >= 48f && returnHeight >= 48f
        )
    }

    @Test
    fun dayCellAxisIsIntactForAccessibilityGrouping() {
        setContent(TimelineTestStates.contentState())
        val number = centerOf("timeline-day-number-2026-09-16")
        val highlight = centerOf("timeline-day-highlight-2026-09-16")
        assertTrue(abs(number.x - highlight.x) <= 1f)
        assertTrue(abs(number.y - highlight.y) <= 1f)
    }
}
