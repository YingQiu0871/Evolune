package io.github.yingqiu0871.evolune.ui.screens.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeState
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.math.abs

/**
 * V17-D-04 §32/§40 — deterministic Compose geometry assertions for the required centering rules
 * (UI35–UI39, UI46–UI47 and the UI36/UI37 hierarchy). These assertions are the primary real
 * geometry evidence demanded by the frozen contract; they compare measured node bounds instead of
 * asserting that "Alignment.Center was written".
 */
@RunWith(AndroidJUnit4::class)
class TimelineGeometryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selectedDate: LocalDate = TimelineTestStates.today

    private fun setContentWithState(
        initial: TimelineRangeState = TimelineTestStates.contentState(),
        onSelect: ((LocalDate) -> TimelineRangeState)? = null
    ) {
        composeRule.setContent {
            var state by remember { mutableStateOf(initial) }
            EvoluneTheme {
                TimelineScreenContent(
                    state = state,
                    onSelectDate = { date ->
                        onSelect?.let { state = it(date) }
                    }
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

    private fun leftOf(tag: String): Dp =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().left

    private fun widthOf(tag: String): Dp {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return bounds.right - bounds.left
    }

    private fun assertCenteredBothAxes(tag: String, containerTag: String, tolerance: Dp = 1.dp) {
        val inner = centerOf(tag)
        val container = centerOf(containerTag)
        assertTrue(
            "$tag must be horizontally centered in $containerTag: $inner vs $container",
            abs(inner.x - container.x) <= tolerance.value
        )
    }

    private fun assertSameAxis(tagA: String, tagB: String, tolerance: Dp = 1.dp) {
        val a = centerOf(tagA)
        val b = centerOf(tagB)
        assertTrue(
            "$tagA and $tagB must share one horizontal center axis: $a vs $b",
            abs(a.x - b.x) <= tolerance.value
        )
    }

    // ---------- UI35 / UI38 / UI39: day-cell internal centering ----------

    @Test
    fun ui35SelectedDateNumberIsCenteredBothAxesInsideItsHighlight() {
        setContentWithState()
        val date = selectedDate
        val number = centerOf("timeline-day-number-$date")
        val highlightBounds = composeRule
            .onNodeWithTag("timeline-day-highlight-$date", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val highlightCenter = Offset(
            (highlightBounds.left + highlightBounds.right).value / 2f,
            (highlightBounds.top + highlightBounds.bottom).value / 2f
        )
        assertTrue(
            "selected number must be horizontally centered in its highlight",
            abs(number.x - highlightCenter.x) <= 1f
        )
        assertTrue(
            "selected number must be vertically centered in its highlight",
            abs(number.y - highlightCenter.y) <= 1f
        )
    }

    @Test
    fun ui38WeekdayDateNumberAndHighlightShareOneHorizontalCenterAxis() {
        setContentWithState()
        val date = selectedDate
        assertSameAxis("timeline-day-weekday-$date", "timeline-day-number-$date")
        assertSameAxis("timeline-day-weekday-$date", "timeline-day-highlight-$date")
        assertSameAxis("timeline-day-number-$date", "timeline-day-highlight-$date")
    }

    @Test
    fun ui39SingleDigitAndDoubleDigitSelectedDatesStayGeometricallyCentered() {
        val singleDigit = LocalDate.of(2026, 9, 8)
        setContentWithState(
            initial = TimelineTestStates.contentState(),
            onSelect = { date ->
                TimelineTestStates.contentState().copy(selectedDate = date)
            }
        )

        scrollStripTo(singleDigit)
        composeRule.onNodeWithTag("timeline-day-cell-$singleDigit").performClick()
        composeRule.waitForIdle()

        val centredSingleNumber = centerOf("timeline-day-number-$singleDigit")
        val centredSingleHighlight = centerOf("timeline-day-highlight-$singleDigit")
        assertTrue(
            "single-digit date must center horizontally",
            abs(centredSingleNumber.x - centredSingleHighlight.x) <= 1f
        )
        assertTrue(
            "single-digit date must center vertically",
            abs(centredSingleNumber.y - centredSingleHighlight.y) <= 1f
        )

        val doubleDigit = selectedDate
        scrollStripTo(doubleDigit)
        composeRule.onNodeWithTag("timeline-day-cell-$doubleDigit").performClick()
        composeRule.waitForIdle()
        val doubleNumber = centerOf("timeline-day-number-$doubleDigit")
        val doubleHighlight = centerOf("timeline-day-highlight-$doubleDigit")
        assertTrue(
            "double-digit date must center horizontally",
            abs(doubleNumber.x - doubleHighlight.x) <= 1f
        )
        assertTrue(
            "double-digit date must center vertically",
            abs(doubleNumber.y - doubleHighlight.y) <= 1f
        )
    }

    // ---------- UI36: month title centering with structural slot reservation ----------

    @Test
    fun ui36MonthTitleStaysCenteredWhileTheNextControlIsDisabled() {
        setContentWithState()
        val navigationCenter = centerOf("timeline-month-navigation")
        val titleCenter = centerOf("timeline-month-title")
        assertTrue(
            "month title must sit at the navigation center when next is disabled",
            abs(titleCenter.x - navigationCenter.x) <= 1f
        )
    }

    @Test
    fun ui36MonthTitleRemainsCenteredWhenNextIsEnabled() {
        setContentWithState(initial = TimelineTestStates.pastMonthState())
        val navigationCenter = centerOf("timeline-month-navigation")
        val titleCenter = centerOf("timeline-month-title")
        assertTrue(
            "month title must sit at the navigation center when next is enabled",
            abs(titleCenter.x - navigationCenter.x) <= 1f
        )
    }

    // ---------- UI37: centered calendar region vs left-aligned body ----------

    @Test
    fun ui37CalendarRegionIsCenteredWhileSectionContentIsLeftAligned() {
        setContentWithState()
        val rootCenterX = centerOf("timeline-screen")
        val titleCenter = centerOf("timeline-month-title")
        assertTrue(
            "the calendar region must be visually centered",
            abs(titleCenter.x - rootCenterX.x) <= 2f
        )

        val headerLeft = leftOf("timeline-section-header-2026-09-15")
        val sectionLeft = leftOf("timeline-section-2026-09-15")
        assertTrue(
            "the section header must be left aligned with its section",
            abs(headerLeft.value - sectionLeft.value) <= 1f
        )
        assertTrue(
            "the section header must sit on the left half of the surface",
            headerLeft.value < rootCenterX.x - 100f
        )
    }

    @Test
    fun dateGroupRowCardsKeepAVisualSeparation() {
        val date = TimelineTestStates.today
        setContentWithState(
            initial = TimelineTestStates.state(
                days = TimelineTestStates.daysFrom(
                    date to listOf(
                        TimelineTestStates.unrecordedRow(date, slotId = 701L),
                        TimelineTestStates.unrecordedRow(date, slotId = 702L)
                    )
                )
            )
        )
        val first = composeRule
            .onNodeWithTag("timeline-row-2026-09-16-0", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val second = composeRule
            .onNodeWithTag("timeline-row-2026-09-16-1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val gap = if (first.top <= second.top) second.top - first.bottom else first.top - second.bottom
        assertTrue(
            "date-group row cards must stay separated by at least 12dp (was $gap)",
            gap.value >= 12f
        )
    }

    // ---------- UI46 / UI47: strip viewport centering and symmetric geometry ----------
    @Test
    fun ui46SelectingAnOffCenterDayCentersItInTheStripViewport() {
        setContentWithState(
            onSelect = { date -> TimelineTestStates.contentState().copy(selectedDate = date) }
        )
        val farDate = LocalDate.of(2026, 9, 2)
        scrollStripTo(farDate)
        composeRule.onNodeWithTag("timeline-day-cell-$farDate").performClick()
        composeRule.waitForIdle()

        val containerCenter = centerOf("timeline-day-strip-container")
        val cellCenter = centerOf("timeline-day-cell-$farDate")
        assertTrue(
            "the selected cell must be brought to the strip viewport center: $cellCenter vs $containerCenter",
            abs(cellCenter.x - containerCenter.x) <= 2f
        )
    }

    @Test
    fun ui47FirstAndLastSelectableDaysKeepSymmetricStripGeometry() {
        setContentWithState(
            onSelect = { date -> TimelineTestStates.contentState().copy(selectedDate = date) }
        )
        val firstDate = LocalDate.of(2026, 9, 1)
        val lastDate = TimelineTestStates.today

        scrollStripTo(firstDate)
        composeRule.onNodeWithTag("timeline-day-cell-$firstDate").performClick()
        composeRule.waitForIdle()

        val containerCenter = centerOf("timeline-day-strip-container")
        val firstCenter = centerOf("timeline-day-cell-$firstDate")
        assertTrue(
            "the first selectable day must reach the viewport center",
            abs(firstCenter.x - containerCenter.x) <= 2f
        )
        assertTrue(
            "the first day must remain fully visible",
            firstCenter.x > 24f
        )

        val firstWidth = widthOf("timeline-day-highlight-$firstDate")

        val weekday = centerOf("timeline-day-weekday-$firstDate")
        val number = centerOf("timeline-day-number-$firstDate")
        val highlight = centerOf("timeline-day-highlight-$firstDate")
        assertTrue("first-day weekday axis must survive scrolling", abs(weekday.x - number.x) <= 1f)
        assertTrue("first-day highlight axis must survive scrolling", abs(highlight.x - number.x) <= 1f)

        scrollStripTo(lastDate)
        composeRule.onNodeWithTag("timeline-day-cell-$lastDate").performClick()
        composeRule.waitForIdle()
        val lastCenter = centerOf("timeline-day-cell-$lastDate")
        val containerCenterAfter = centerOf("timeline-day-strip-container")
        assertTrue(
            "the last selectable day must reach the viewport center",
            abs(lastCenter.x - containerCenterAfter.x) <= 2f
        )

        val lastWidth = widthOf("timeline-day-highlight-$lastDate")
        assertTrue(
            "day cells must keep an equal symmetric width",
            abs(firstWidth.value - lastWidth.value) <= 0.5f
        )
    }
}
