package io.github.yingqiu0871.evolune.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FeatureTutorialNavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchFromDeterministicOnboardingState() {
        runBlocking {
            val store = OnboardingStateStore(context, isExistingInstallation = true)
            store.initializeIfNeeded()
            store.acceptTerms()
            store.acknowledgeMedicalPkDisclosure()
            store.completeOnboarding()
            store.markFeatureTutorialHandled()
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    @After
    fun closeActivity() {
        scenario.close()
    }

    @Test
    fun tutorialCtasUseExistingEditorAndNavigationSurfaces() {
        composeRule.waitForIdle()
        openSettings()
        composeRule.onNodeWithTag("settings-feature-tutorial-entry")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("feature-tutorial-create-plan")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("plan-editor-surface").assertIsDisplayed()
        pressSystemBack()
        composeRule.onNodeWithTag("feature-tutorial-step-title").assertIsDisplayed()

        next()
        composeRule.onNodeWithTag("feature-tutorial-record-dose")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("record-editor-surface").assertIsDisplayed()
        pressSystemBack()

        next()
        composeRule.onNodeWithTag("feature-tutorial-open-pk")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("app-top-title").assertTextEquals(
            context.getString(R.string.nav_home)
        )
        pressSystemBack()
        composeRule.onNodeWithTag("feature-tutorial-step-title").assertIsDisplayed()

        repeat(3) { next() }
        composeRule.onNodeWithTag("feature-tutorial-open-backup")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("google-drive-backup-now").assertIsDisplayed()
        pressSystemBack()
        composeRule.onNodeWithTag("feature-tutorial-step-title").assertIsDisplayed()
    }

    @Test
    fun activityRecreationRestoresSettingsNavigationRoute() {
        composeRule.waitForIdle()
        openSettings()

        scenario.recreate()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("settings-basic-data-entry")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("settings-basic-data-entry").assertIsDisplayed()
    }

    private fun next() {
        composeRule.onNodeWithTag("feature-tutorial-next").performScrollTo().performClick()
    }

    private fun openSettings() {
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("nav-rail-settings")
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("nav-bar-settings")
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val railSettings = composeRule.onAllNodesWithTag("nav-rail-settings").fetchSemanticsNodes()
        if (railSettings.isNotEmpty()) {
            composeRule.onNodeWithTag("nav-rail-settings").performClick()
        } else {
            composeRule.onNodeWithTag("nav-bar-settings").performClick()
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("settings-feature-tutorial-entry")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressSystemBack() {
        scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }
}
