package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ContextualAuthorizationDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogExplainsPurposeAndExposesContinueAndNotNow() {
        var action = ""
        composeRule.setContent {
            EvoluneTheme {
                ContextualAuthorizationDialog(
                    visible = true,
                    title = "授权说明",
                    message = "用于测试说明内容",
                    onContinue = { action = "continue" },
                    onNotNow = { action = "not-now" }
                )
            }
        }

        composeRule.onNodeWithText("授权说明").assertIsDisplayed()
        composeRule.onNodeWithText("用于测试说明内容").assertIsDisplayed()
        composeRule.onNodeWithText("继续").performClick()
        composeRule.runOnIdle { assertEquals("continue", action) }
        composeRule.onNodeWithText("暂不").performClick()
        composeRule.runOnIdle { assertEquals("not-now", action) }
    }
}
