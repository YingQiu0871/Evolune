package io.github.yingqiu0871.evolune.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.data.ThemeColorSource
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.ThemePresetSelection
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * v1.7.2 Slice C AVD acceptance — seeds the exact canonical LEGACY_BUILTIN state through the
 * real settings DataStore, exercises the flattened screen and palette selection through the
 * real app shell, and captures device screenshots for the evidence bundle.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SliceCSettingsSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = SettingsDataStore(context)
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun prepare() {
        runBlocking {
            val onboarding = OnboardingStateStore(context, isExistingInstallation = true)
            onboarding.initializeIfNeeded()
            onboarding.acceptTerms()
            onboarding.acknowledgeMedicalPkDisclosure()
            onboarding.completeOnboarding()
            onboarding.markFeatureTutorialHandled()
            store.replaceSettings(
                UserSettings(
                    themeMode = ThemeMode.SYSTEM,
                    themeColorSource = ThemeColorSource.PRESET,
                    themePreset = ThemePresetSelection.LegacyBuiltin
                )
            )
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    @After
    fun close() {
        scenario.close()
        runBlocking { store.replaceSettings(UserSettings()) }
    }

    @Test
    fun flattenedSettingsAndPaletteFlowOnAvd() {
        composeRule.waitForIdle()
        openSettings()
        dismissUpdateDialogIfVisible()

        composeRule.onNodeWithTag("settings-basic-data-section").assertIsDisplayed()
        screenshot("01-settings-top")

        composeRule.onNodeWithTag("settings-color-scheme-section").performScrollTo()
        composeRule.onNodeWithTag("color-legacy-builtin-current").assertIsDisplayed()
        screenshot("02-legacy-builtin-state")

        composeRule.onNodeWithTag("palette-tile-monet_blue").performScrollTo().performClick()
        composeRule.waitForIdle()
        Thread.sleep(400)
        screenshot("03-monet-blue-selected")

        composeRule.onNodeWithTag("theme-mode-dark").performScrollTo().performClick()
        composeRule.waitForIdle()
        Thread.sleep(400)
        screenshot("04-dark-theme-mode")

        composeRule.onNodeWithTag("settings-about-entry").performScrollTo().assertIsDisplayed()
        screenshot("05-lower-sections")

        composeRule.onNodeWithTag("settings-color-scheme-section").performScrollTo()
        composeRule.onNodeWithTag("palette-tile-monet_amber").performClick()
        composeRule.waitForIdle()
        Thread.sleep(400)
        screenshot("06-monet-amber-selected")
    }

    private fun dismissUpdateDialogIfVisible() {
        val cancellations = composeRule.onAllNodesWithText("取消").fetchSemanticsNodes()
        if (cancellations.isNotEmpty()) {
            composeRule.onAllNodesWithText("取消")[0].performClick()
            composeRule.waitForIdle()
            Thread.sleep(300)
        }
    }

    private fun openSettings() {
        composeRule.waitUntil(15_000L) {
            composeRule.onAllNodesWithTag("nav-bar-settings").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("nav-rail-settings").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("nav-rail-settings").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("nav-rail-settings").performClick()
        } else {
            composeRule.onNodeWithTag("nav-bar-settings").performClick()
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("settings-basic-data-section")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/slice-c")
        }
        val uri = targetContext.contentResolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("failed to create media store entry for $name")
        targetContext.contentResolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
