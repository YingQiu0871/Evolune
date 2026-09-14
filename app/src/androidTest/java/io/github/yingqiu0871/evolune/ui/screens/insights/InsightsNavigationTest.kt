package io.github.yingqiu0871.evolune.ui.screens.insights

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.7-B-03 §34: the real product path History → Insights → back → Insights.
 *
 * No harness ViewModel and no synthetic navigation: the test drives MainActivity's own navigation,
 * so the entry point, the route, the shared top bar's back action and the destination-scoped
 * composition are all exercised as shipped.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class InsightsNavigationTest {

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
        composeRule.waitForIdle()
    }

    @After
    fun closeActivity() {
        scenario.close()
    }

    private fun selectHistory() {
        composeRule.onNodeWithTag("nav-bar-history").performClick()
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun historyOpensInsightsAndBackReturnsToHistory() {
        selectHistory()
        composeRule.onNodeWithTag("history-screen").assertExists()
        composeRule.onNodeWithTag("history-insights-entry").assertExists()

        composeRule.onNodeWithTag("history-insights-entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("insights-screen").assertExists()
        composeRule.onNodeWithTag("insights-range-selector").assertExists()
        composeRule.onNodeWithTag("insights-range-last30").assertExists()

        pressBack()

        composeRule.onNodeWithTag("history-screen").assertExists()
        composeRule.onNodeWithTag("insights-screen").assertDoesNotExist()
    }

    @Test
    fun leavingAndReEnteringInsightsKeepsTheSurfaceUsable() {
        selectHistory()

        composeRule.onNodeWithTag("history-insights-entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights-screen").assertExists()

        pressBack()
        composeRule.onNodeWithTag("history-screen").assertExists()

        // re-entry must not crash and must keep rendering the production surface
        composeRule.onNodeWithTag("history-insights-entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights-screen").assertExists()
        composeRule.onNodeWithTag("insights-range-selector").assertExists()

        // and the bottom bar stays hidden inside the Insights destination
        composeRule.onNodeWithTag("nav-bar-history").assertDoesNotExist()
    }
}
