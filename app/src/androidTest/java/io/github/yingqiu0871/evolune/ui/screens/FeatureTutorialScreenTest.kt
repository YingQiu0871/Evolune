package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeatureTutorialScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun presentsSixOptionalStepsAndRoutesEachCallToAction() {
        var planClicks = 0
        var doseClicks = 0
        var pkClicks = 0
        var backupClicks = 0
        var finished = false

        composeRule.setContent {
            EvoluneTheme {
                FeatureTutorialScreen(
                    onCreatePlan = { planClicks += 1 },
                    onRecordDose = { doseClicks += 1 },
                    onOpenPkChart = { pkClicks += 1 },
                    onOpenBackup = { backupClicks += 1 },
                    onSkip = {},
                    onFinish = { finished = true }
                )
            }
        }

        composeRule.onNodeWithTag("feature-tutorial").assertIsDisplayed()
        composeRule.onNodeWithTag("feature-tutorial-create-plan").performScrollTo().performClick()
        next()

        composeRule.onNodeWithTag("feature-tutorial-record-dose").performScrollTo().performClick()
        next()

        composeRule.onNodeWithTag("feature-tutorial-open-pk").performScrollTo().performClick()
        next()

        // Widget and Wear are instructional pages only: advancing never needs a CTA.
        assertTrue(
            composeRule.onAllNodesWithTag("feature-tutorial-create-plan")
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("feature-tutorial-next").performScrollTo().performClick()
        assertTrue(
            composeRule.onAllNodesWithTag("feature-tutorial-record-dose")
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("feature-tutorial-next").performScrollTo().performClick()

        composeRule.onNodeWithTag("feature-tutorial-open-backup").performScrollTo().performClick()
        composeRule.onNodeWithTag("feature-tutorial-finish").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(1, planClicks)
            assertEquals(1, doseClicks)
            assertEquals(1, pkClicks)
            assertEquals(1, backupClicks)
            assertTrue(finished)
        }
    }

    @Test
    fun skipIsAvailableBeforeAnyAction() {
        var skipped = false
        composeRule.setContent {
            EvoluneTheme {
                FeatureTutorialScreen(
                    onSkip = { skipped = true },
                    onFinish = {}
                )
            }
        }

        composeRule.onNodeWithTag("feature-tutorial-skip").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(skipped) }
    }

    @Test
    fun finalControlsRemainReachableAtLargeFontScaleInWideWindow() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1.5f)
            ) {
                EvoluneTheme {
                    FeatureTutorialScreen(
                        onSkip = {},
                        onFinish = {},
                        modifier = Modifier
                            .width(840.dp)
                            .height(640.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithTag("feature-tutorial").assertIsDisplayed()
        repeat(5) {
            composeRule.onNodeWithTag("feature-tutorial-next")
                .performScrollTo()
                .performClick()
        }
        composeRule.onNodeWithTag("feature-tutorial-finish")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun next() {
        composeRule.onNodeWithTag("feature-tutorial-next").performScrollTo().performClick()
    }
}
