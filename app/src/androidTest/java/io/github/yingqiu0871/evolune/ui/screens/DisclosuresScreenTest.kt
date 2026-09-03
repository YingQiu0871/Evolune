package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DisclosuresScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disclosureSectionsAndVisibleBackActionAreAvailable() {
        var navigatedBack = false
        composeRule.setContent {
            EvoluneTheme {
                DisclosuresScreen(onNavigateBack = { navigatedBack = true })
            }
        }

        composeRule.onNodeWithText("使用条款").assertIsDisplayed()
        composeRule.onNodeWithText("隐私与数据边界").assertIsDisplayed()
        composeRule.onNodeWithText("医疗与 PK 估算免责声明").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.runOnIdle { assertTrue(navigatedBack) }
    }

    @Test
    fun disclosuresStayReadableAtLargeFontScaleInWideWindow() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1.5f)
            ) {
                EvoluneTheme {
                    DisclosuresScreen(
                        modifier = Modifier
                            .width(840.dp)
                            .height(640.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithTag("disclosures-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("disclosure-medical-pk")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
