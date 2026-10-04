package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Rule
import org.junit.Test

class E2CalibrationNoteTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appliedCalibrationShowsLabCountScaleAndFitError() {
        composeRule.setContent {
            EvoluneTheme { E2CalibrationNote(labCount = 2, scale = 1.234, fitErrorPct = 11.6) }
        }

        composeRule.onNodeWithText("已按化验校准").assertIsDisplayed()
        composeRule.onNodeWithTag("e2-calibration-scale")
            .assertTextEquals("根据 2 次雌二醇化验，曲线按模型估算 × 1.23 显示。")
        composeRule.onNodeWithText("化验与校准后曲线的典型偏差约 ±12%。").assertIsDisplayed()
    }

    @Test
    fun noComparableLabSaysCalibrationIsNotApplied() {
        composeRule.setContent {
            EvoluneTheme { E2CalibrationNote(labCount = 0, scale = 1.0, fitErrorPct = null) }
        }

        composeRule.onNodeWithText("化验校准未生效").assertIsDisplayed()
        composeRule.onNodeWithTag("e2-calibration-scale").assertDoesNotExist()
    }
}
