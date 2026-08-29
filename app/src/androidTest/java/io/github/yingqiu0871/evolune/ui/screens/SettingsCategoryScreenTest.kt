package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsCategoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun basicDataKeepsBodyWeightInputAndValidationCallback() {
        val weights = mutableListOf<Double>()
        composeRule.setContent {
            EvoluneTheme {
                BasicDataScreen(
                    bodyWeight = 55.0,
                    onBodyWeightChange = { weights += it }
                )
            }
        }

        composeRule.onNodeWithTag("settings-basic-data-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-weight-input").performTextClearance()
        composeRule.onNodeWithTag("settings-weight-input").performTextInput("72.5")
        composeRule.runOnIdle {
            assertEquals(72.5, weights.last(), 0.0)
        }
    }

    @Test
    fun appearanceAndFormatKeepsThemeColorAndTimeSelectors() {
        val selectedModes = mutableListOf<ThemeMode>()
        val selectedColors = mutableListOf<ColorTheme>()
        val selectedFormats = mutableListOf<TimeFormat>()
        composeRule.setContent {
            EvoluneTheme {
                AppearanceAndFormatScreen(
                    settings = UserSettings(),
                    onThemeModeChange = { selectedModes += it },
                    onColorThemeChange = { selectedColors += it },
                    onTimeFormatChange = { selectedFormats += it }
                )
            }
        }

        composeRule.onNodeWithTag("settings-appearance-format-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("theme-mode-dark").performClick()
        composeRule.onNodeWithTag("color-theme-builtin").performClick()
        composeRule.onNodeWithTag("time-format-hour_24").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(ThemeMode.DARK), selectedModes)
            assertEquals(listOf(ColorTheme.BUILTIN), selectedColors)
            assertEquals(listOf(TimeFormat.HOUR_24), selectedFormats)
        }
    }

    @Test
    fun updatePageKeepsToggleCheckAndVersionActions() {
        val autoCheckValues = mutableListOf<Boolean>()
        var checkCount = 0
        composeRule.setContent {
            EvoluneTheme {
                UpdateScreen(
                    autoCheckUpdates = false,
                    onAutoCheckUpdatesChange = { autoCheckValues += it },
                    onCheckForUpdates = { checkCount += 1 },
                    updateCheckResult = UpdateCheckResult.Idle
                )
            }
        }

        composeRule.onNodeWithTag("settings-update-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-auto-check-updates").performClick()
        composeRule.onNodeWithTag("settings-check-updates-now").performClick()
        composeRule.onNodeWithTag("settings-current-version").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(true), autoCheckValues)
            assertEquals(1, checkCount)
        }
    }

    @Test
    fun aboutPageKeepsContactLinksCopyrightAndDisclaimerDialogs() {
        var websiteOpenCount = 0
        var developerContactCount = 0
        composeRule.setContent {
            EvoluneTheme {
                AboutScreen(
                    onOpenWebsite = { websiteOpenCount += 1 },
                    onContactDeveloper = { developerContactCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("settings-about-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-about-website").performClick()
        composeRule.onNodeWithTag("settings-about-developer-contact").performClick()
        composeRule.runOnIdle {
            assertEquals(1, websiteOpenCount)
            assertEquals(1, developerContactCount)
        }
        composeRule.onNodeWithTag("settings-about-copyright").performScrollTo().performClick()
        composeRule.onAllNodesWithText("版权信息").get(1).assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithTag("settings-about-disclaimer").performScrollTo().performClick()
        composeRule.onAllNodesWithText("免责声明").get(1).assertIsDisplayed()
    }
}
