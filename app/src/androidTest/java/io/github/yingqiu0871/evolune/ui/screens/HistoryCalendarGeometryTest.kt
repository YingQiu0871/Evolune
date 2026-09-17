package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.HistoryUiState
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
 * v1.7.1 calendar alignment follow-up — relational geometry for the History month calendar.
 *
 * The selected-day visual background and the day-number label must share the same geometric
 * center on both axes; neighboring cells must stay row-aligned; the number must stay inside
 * the background (no clipping) at 1.0x and at a larger supported font scale.
 */
@RunWith(AndroidJUnit4::class)
class HistoryCalendarGeometryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val selectedDate = LocalDate.of(2026, 9, 18)

    private fun setContent(fontScale: Float, selected: LocalDate = selectedDate) {
        composeRule.setContent {
            EvoluneTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    HistoryScreenContent(
                        state = HistoryUiState(
                            visibleMonth = YearMonth.of(2026, 9),
                            selectedDate = selected,
                            today = selectedDate,
                            displayZone = ZoneOffset.UTC,
                            loadedMonth = YearMonth.of(2026, 9),
                            loadedDays = emptyMap(),
                            loading = false,
                            failed = false
                        )
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun boundsOf(tag: String): DpRect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun centerOf(tag: String): Offset {
        val b = boundsOf(tag)
        return Offset((b.left + b.right).value / 2f, (b.top + b.bottom).value / 2f)
    }

    private fun assertNumberCenteredInItsBackground() {
        val background = centerOf("history-cell-$selectedDate")
        val number = centerOf("history-day-number-$selectedDate")
        assertTrue(
            "day number must be horizontally centered in its selection background: " +
                "number=$number background=$background",
            abs(number.x - background.x) <= 1.5f
        )
        assertTrue(
            "day number must be vertically centered in its selection background: " +
                "number=$number background=$background",
            abs(number.y - background.y) <= 1.5f
        )
    }

    private fun assertNumberInsideItsBackground() {
        val number = boundsOf("history-day-number-$selectedDate")
        val background = boundsOf("history-cell-$selectedDate")
        assertTrue(
            "day number must stay inside the selection background: number=$number background=$background",
            number.left.value >= background.left.value - 0.5f &&
                number.right.value <= background.right.value + 0.5f &&
                number.top.value >= background.top.value - 0.5f &&
                number.bottom.value <= background.bottom.value + 0.5f
        )
    }

    @Test
    fun selectedDayNumberIsCenteredInItsSelectionBackgroundAtNormalFontScale() {
        setContent(fontScale = 1.0f)
        assertNumberCenteredInItsBackground()
        assertNumberInsideItsBackground()
    }

    @Test
    fun selectedDayNumberIsCenteredInItsSelectionBackgroundAtLargeFontScale() {
        setContent(fontScale = 2.0f)
        assertNumberCenteredInItsBackground()
        assertNumberInsideItsBackground()
    }

    @Test
    fun neighboringCellsStayRowAlignedAndTheSelectionStaysUsable() {
        setContent(fontScale = 1.0f)

        val before = boundsOf("history-cell-2026-09-17")
        val selected = boundsOf("history-cell-$selectedDate")
        val after = boundsOf("history-cell-2026-09-19")
        listOf("17" to before, "19" to after).forEach { (label, neighbor) ->
            assertEquals(
                "cell $label top must stay aligned with the selected row (selected=$selected neighbor=$neighbor)",
                selected.top.value,
                neighbor.top.value,
                0.5f
            )
            assertEquals(
                "cell $label bottom must stay aligned with the selected row (selected=$selected neighbor=$neighbor)",
                selected.bottom.value,
                neighbor.bottom.value,
                0.5f
            )
        }

        composeRule.onNodeWithTag("history-cell-$selectedDate")
            .assert(hasClickAction())
            .assertContentDescriptionContains(
                context.getString(R.string.history_cell_selected),
                substring = true
            )
        composeRule.onNodeWithTag("history-cell-2026-09-10").assert(hasClickAction())
    }

    @Test
    fun selectedCellTouchTargetIsNotReduced() {
        setContent(fontScale = 1.0f)
        val bounds = boundsOf("history-cell-$selectedDate")
        val width = bounds.right.value - bounds.left.value
        val height = bounds.bottom.value - bounds.top.value
        assertTrue(
            "the cell touch target must not shrink below the pre-fix size " +
                "(52.6x28.2dp measured before the fix; now ${width}x$height)",
            width >= 44f && height >= 28f
        )
    }
}
