package io.github.yingqiu0871.evolune.ui.screens.timeline

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
 * V17-D-04 §32 (UI31–UI33): the real product path History → Timeline → back → History.
 *
 * No harness ViewModel and no synthetic navigation: the test drives MainActivity's own navigation,
 * so the entry card, the sub-route, the shared top bar back action and the activity-scoped
 * destination composition are exercised as shipped (mirrors the Insights / Retrospective
 * precedents). The Timeline is a sub-route, never a sixth bottom tab.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimelineNavigationTest {

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
    fun historyOpensTimelineAndBackReturnsToHistory() {
        selectHistory()
        composeRule.onNodeWithTag("history-screen").assertExists()
        composeRule.onNodeWithTag("history-timeline-entry").assertExists()

        composeRule.onNodeWithTag("history-timeline-entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-screen").assertExists()
        composeRule.onNodeWithTag("timeline-content-list").assertExists()

        pressBack()
        composeRule.onNodeWithTag("history-screen").assertExists()
    }

    @Test
    fun theTimelineSubRouteAddsNoBottomEntryAndHidesTheBottomBar() {
        selectHistory()
        composeRule.onNodeWithTag("nav-bar-timeline").assertDoesNotExist()

        composeRule.onNodeWithTag("history-timeline-entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("timeline-screen").assertExists()
        // The bottom bar stays hidden on the sub-route (isSettingsSubroute precedent).
        composeRule.onNodeWithTag("nav-bar-history").assertDoesNotExist()
    }
}
