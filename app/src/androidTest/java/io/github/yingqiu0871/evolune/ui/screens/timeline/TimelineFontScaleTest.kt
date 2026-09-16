package io.github.yingqiu0871.evolune.ui.screens.timeline

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.history.HistoryFormatting
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeState
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.math.abs

/**
 * V17-D-05 §40/§41 — executed font-scale evidence for the responsive day-strip rules.
 *
 * Uses [DeviceConfigurationOverride.FontScale] to render the real Timeline surface at
 * 1.0 / 1.3 / 1.5 / 2.0 and MEASURES the relational geometry (number inside the highlight,
 * shared axis, uniform resolved cell width, month-title centering, nav-slot symmetry, body
 * alignment, viewport centering and edge symmetry). Contract: FONT1–FONT11.
 */
@RunWith(AndroidJUnit4::class)
class TimelineFontScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = TimelineTestStates.today

    private lateinit var scaleState: MutableFloatState

    /**
     * Renders the surface under a live font-scale state so one method can measure several scales
     * in sequence (a composition rule allows exactly one setContent per test).
     */
    private fun renderWithScale(
        initialScale: Float,
        state: TimelineRangeState = TimelineTestStates.contentState(),
        liveSelection: Boolean = false
    ) {
        scaleState = mutableFloatStateOf(initialScale)
        val stateHolder = mutableStateOf(state)
        composeRule.setContent {
            val scale = scaleState.floatValue
            EvoluneTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                    TimelineScreenContent(
                        state = stateHolder.value,
                        onSelectDate = if (liveSelection) {
                            { date -> stateHolder.value = stateHolder.value.copy(selectedDate = date) }
                        } else {
                            {}
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun updateScale(value: Float) {
        scaleState.floatValue = value
        composeRule.waitForIdle()
    }

    private fun boundsOf(tag: String): DpRect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun centerOf(tag: String): Offset {
        val b = boundsOf(tag)
        return Offset((b.left + b.right).value / 2f, (b.top + b.bottom).value / 2f)
    }

    private fun sizeOf(tag: String): Pair<Float, Float> {
        val b = boundsOf(tag)
        return Pair((b.right - b.left).value, (b.bottom - b.top).value)
    }

    private fun scrollStripTo(date: LocalDate) {
        composeRule.onNodeWithTag("timeline-day-strip")
            .performScrollToNode(hasTestTag("timeline-day-cell-$date"))
        composeRule.waitForIdle()
    }

    private fun assertNumberInsideHighlight(date: LocalDate) {
        val number = boundsOf("timeline-day-number-$date")
        val highlight = boundsOf("timeline-day-highlight-$date")
        val numberLeft = number.left.value
        val numberRight = number.right.value
        val numberTop = number.top.value
        val numberBottom = number.bottom.value
        val highlightLeft = highlight.left.value
        val highlightRight = highlight.right.value
        val highlightTop = highlight.top.value
        val highlightBottom = highlight.bottom.value
        assertTrue(
            "number $number must fit inside highlight $highlight",
            numberLeft >= highlightLeft - 0.5f &&
                numberRight <= highlightRight + 0.5f &&
                numberTop >= highlightTop - 0.5f &&
                numberBottom <= highlightBottom + 0.5f
        )
    }

    private fun assertCenteredAxis(date: LocalDate) {
        val weekday = centerOf("timeline-day-weekday-$date")
        val number = centerOf("timeline-day-number-$date")
        val highlight = centerOf("timeline-day-highlight-$date")
        assertTrue("weekday/number axis", abs(weekday.x - number.x) <= 1.5f)
        assertTrue("number/highlight x axis", abs(number.x - highlight.x) <= 1.5f)
        assertTrue("number/highlight y axis", abs(number.y - highlight.y) <= 1.5f)
    }

    private fun assertUniformCellWidth(dateA: LocalDate, dateB: LocalDate) {
        scrollStripTo(dateA)
        val a = sizeOf("timeline-day-cell-$dateA").first
        scrollStripTo(dateB)
        val b = sizeOf("timeline-day-cell-$dateB").first
        assertTrue("uniform cell width ($a vs $b)", abs(a - b) <= 0.5f)
    }

    // FONT1
    @Test
    fun fontScale10KeepsD04RelationalGeometry() {
        renderWithScale(1.0f)
        assertCenteredAxis(today)
        assertNumberInsideHighlight(today)
        assertUniformCellWidth(LocalDate.of(2026, 9, 8), today)
        val container = centerOf("timeline-day-strip-container")
        val selected = centerOf("timeline-day-cell-$today")
        assertTrue("selected cell rests at the viewport center", abs(container.x - selected.x) <= 2f)
    }

    // FONT2 / FONT3
    @Test
    fun fontScale13And15KeepDayCellContentFitted() {
        renderWithScale(1.3f)
        assertNumberInsideHighlight(today)
        assertCenteredAxis(today)
        assertUniformCellWidth(LocalDate.of(2026, 9, 8), today)

        updateScale(1.5f)
        assertNumberInsideHighlight(today)
        assertCenteredAxis(today)
        assertUniformCellWidth(LocalDate.of(2026, 9, 8), today)
    }

    // FONT4 / FONT5 / FONT6 / FONT7
    @Test
    fun fontScale20GrowsHighlightKeepsNumberInsideAndKeepsUniformWidth() {
        renderWithScale(2.0f)
        assertNumberInsideHighlight(today)
        assertCenteredAxis(today)
        assertUniformCellWidth(LocalDate.of(2026, 9, 8), today)

        val (highlightWidth, highlightHeight) = sizeOf("timeline-day-highlight-$today")
        assertTrue("highlight must grow above the 36dp baseline at 2.0x", highlightWidth > 36f)
        assertTrue("highlight keeps equal width/height", abs(highlightWidth - highlightHeight) <= 0.5f)

        val (cellWidth, cellHeight) = sizeOf("timeline-day-cell-$today")
        assertTrue("cell keeps >= 48dp hit target", cellWidth >= 48f && cellHeight >= 48f)
    }

    // FONT8 / FONT9
    @Test
    fun fontScale20KeepsMonthTitleCenteredNavSymmetricAndBodyLeftAligned() {
        renderWithScale(2.0f)
        val title = centerOf("timeline-month-title")
        val navigation = centerOf("timeline-month-navigation")
        assertTrue("month title centered at 2.0x", abs(title.x - navigation.x) <= 1.5f)

        val leftSlot = sizeOf("timeline-previous-month-slot").first
        val rightSlot = sizeOf("timeline-next-month-slot").first
        assertTrue("nav slots symmetric", abs(leftSlot - rightSlot) <= 0.5f)

        val screenCenter = centerOf("timeline-screen")
        val headerLeft = boundsOf("timeline-section-header-2026-09-15").left.value
        val sectionLeft = boundsOf("timeline-section-2026-09-15").left.value
        assertTrue("section header left aligned with its section", abs(headerLeft - sectionLeft) <= 1f)
        assertTrue("body stays left aligned", headerLeft < screenCenter.x - 100f)

        val (sideWidth, sideHeight) = composeRule.onAllNodesWithTag("timeline-schedule-side")
            .onFirst()
            .getUnclippedBoundsInRoot()
            .let { it.right.value - it.left.value to it.bottom.value - it.top.value }
        assertTrue("grouped side remains laid out (no degenerate clip)", sideWidth > 0f && sideHeight > 0f)
    }

    // FONT10 / FONT11
    @Test
    fun fontScale20ViewportCenteringAndEdgeSymmetry() {
        renderWithScale(2.0f, liveSelection = true)

        // The initial state already has today selected and centered — measure it before any
        // click, because selecting a date that HAS a section scrolls the body (calendar out of
        // view) as frozen by D-04.
        val container = centerOf("timeline-day-strip-container")
        val lastCenter = centerOf("timeline-day-cell-$today")
        assertTrue("last selectable cell reaches viewport center", abs(container.x - lastCenter.x) <= 3f)

        val farDate = LocalDate.of(2026, 9, 2)
        scrollStripTo(farDate)
        composeRule.onNodeWithTag("timeline-day-cell-$farDate").performClick()
        composeRule.waitForIdle()
        val containerAfterFar = centerOf("timeline-day-strip-container")
        val farCenter = centerOf("timeline-day-cell-$farDate")
        assertTrue(
            "selected cell reaches viewport center at 2.0x",
            abs(containerAfterFar.x - farCenter.x) <= 3f
        )

        val firstDate = LocalDate.of(2026, 9, 1)
        scrollStripTo(firstDate)
        composeRule.onNodeWithTag("timeline-day-cell-$firstDate").performClick()
        composeRule.waitForIdle()
        val containerAfterFirst = centerOf("timeline-day-strip-container")
        val firstCenter = centerOf("timeline-day-cell-$firstDate")
        assertTrue("first cell reaches viewport center", abs(containerAfterFirst.x - firstCenter.x) <= 3f)
    }

    // §41 bounded 12/24h large-font sanity (no cross product)
    @Test
    fun fontScale20TwelveAndTwentyFourHourSanity() {
        val is24 = mutableStateOf(true)
        scaleState = mutableFloatStateOf(2.0f)
        composeRule.setContent {
            val scale = scaleState.floatValue
            EvoluneTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                    TimelineScreenContent(
                        state = TimelineTestStates.contentState(),
                        is24Hour = is24.value
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val instant = TimelineTestStates.matchedInstant
        val zone = TimelineTestStates.utc
        val expected24 = HistoryFormatting.timeText(instant, zone, true)
        assertTrue(
            "24h phrase must contain the published-zone time",
            schedulePhrase().contains(expected24)
        )

        is24.value = false
        composeRule.waitForIdle()
        val expected12 = HistoryFormatting.timeText(instant, zone, false)
        assertTrue(
            "12h phrase must contain the published-zone time",
            schedulePhrase().contains(expected12)
        )
        assertTrue("12h and 24h must differ", expected12 != expected24)

        val (_, sideHeight) = composeRule.onAllNodesWithTag("timeline-schedule-side")
            .onFirst()
            .getUnclippedBoundsInRoot()
            .let { it.right.value - it.left.value to it.bottom.value - it.top.value }
        assertTrue("row text remains laid out at 2.0x", sideHeight > 0f)
    }

    private fun schedulePhrase(): String =
        composeRule.onAllNodesWithTag("timeline-schedule-side")
            .onFirst()
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .joinToString(separator = " ")
}
