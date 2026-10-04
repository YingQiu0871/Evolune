package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import io.github.yingqiu0871.evolune.pk.calibration.CalibrationPoint
import io.github.yingqiu0871.evolune.pk.calibration.E2Calibration
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Rule
import org.junit.Test

class E2CalibrationNoteTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appliedCalibrationShowsLabCountScaleAndFitError() {
        val calibration = E2Calibration(
            scale = 1.234,
            labCount = 2,
            fitErrorPct = 11.6,
            points = listOf(
                CalibrationPoint("a", 1.0, 150.0, 120.0),
                CalibrationPoint("b", 2.0, 160.0, 130.0)
            )
        )
        composeRule.setContent { EvoluneTheme { E2CalibrationNote(calibration) } }

        composeRule.onNodeWithText("已按化验校准").assertIsDisplayed()
        composeRule.onNodeWithTag("home-e2-calibration-scale")
            .assertTextEquals("根据 2 次雌二醇化验，曲线按模型估算 × 1.23 显示。")
        composeRule.onNodeWithText("化验与校准后曲线的典型偏差约 ±12%。").assertIsDisplayed()
    }

    @Test
    fun identityCalibrationSaysItIsNotApplied() {
        composeRule.setContent { EvoluneTheme { E2CalibrationNote(E2Calibration.NONE) } }

        composeRule.onNodeWithText("化验校准未生效").assertIsDisplayed()
        composeRule.onNodeWithTag("home-e2-calibration-scale").assertDoesNotExist()
    }
}
