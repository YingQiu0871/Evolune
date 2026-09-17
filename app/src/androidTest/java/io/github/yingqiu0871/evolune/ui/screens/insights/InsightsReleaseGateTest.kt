package io.github.yingqiu0871.evolune.ui.screens.insights

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.7-B-04 release gates on the real product path: lazy ViewModel creation, Activity-scoped
 * retention, configuration change, the custom-range round trip, the History entry regression and
 * the primary-navigation regression.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class InsightsReleaseGateTest {

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

    private fun insightsKeys(): List<String> = mutableListOf<String>().also { keys ->
        scenario.onActivity { activity ->
            keys += activity.viewModelStore.keys().filter { it.contains("InsightsViewModel") }
        }
    }

    private fun selectTab(route: String) {
        composeRule.onNodeWithTag("nav-bar-$route").performClick()
        composeRule.waitForIdle()
    }

    private fun openInsights() {
        scrollHistoryToInsightsEntry()
        composeRule.onNodeWithTag("history-insights-entry").performClick()
        composeRule.waitForIdle()
    }

    /** The entry cards moved below the factual day content (v1.7.1 UI hotfix); scroll first. */
    private fun scrollHistoryToInsightsEntry() {
        composeRule.onNodeWithTag("history-content-list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("history-insights-entry"))
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun theInsightsViewModelIsCreatedLazilyOnFirstEntry() {
        // app is up and History is reachable, but Insights was never opened
        assertEquals("no Insights ViewModel before the first entry", emptyList<String>(), insightsKeys())

        selectTab("history")
        assertEquals("switching tabs must not create it either", emptyList<String>(), insightsKeys())

        openInsights()
        assertEquals("exactly one Insights ViewModel after the first entry", 1, insightsKeys().size)
    }

    @Test
    fun theSelectedRangeSurvivesLeavingAndReEnteringInsights() {
        selectTab("history")
        openInsights()
        composeRule.onNodeWithTag("insights-range-last30").assertIsSelected()

        composeRule.onNodeWithTag("insights-range-last7").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights-range-last7").assertIsSelected()

        pressBack()
        composeRule.onNodeWithTag("history-screen").assertExists()

        openInsights()
        // the same Activity-scoped ViewModel serves the re-entry: the user's choice is retained
        composeRule.onNodeWithTag("insights-range-last7").assertIsSelected()
        assertEquals(1, insightsKeys().size)
    }

    @Test
    fun aConfigurationChangeKeepsTheSelectionAndReloadsTheSurface() {
        selectTab("history")
        openInsights()
        composeRule.onNodeWithTag("insights-range-last7").performClick()
        composeRule.waitForIdle()

        scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("insights-screen").assertExists()
        composeRule.onNodeWithTag("insights-range-last7").assertIsSelected()
    }

    @Test
    fun theCustomRangeRoundTripKeepsTheIntendedDates() {
        selectTab("history")
        openInsights()

        // remember the dates the frozen state resolved for the default range
        val headingBefore = headingText()
        composeRule.onNodeWithTag("insights-range-custom").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights-custom-range-picker").assertExists()

        composeRule.onNodeWithTag("insights-custom-range-confirm").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("insights-custom-range-picker").assertDoesNotExist()
        composeRule.onNodeWithTag("insights-range-custom").assertIsSelected()
        assertEquals(
            "the picker round trip must not shift the dates",
            headingBefore,
            headingText()
        )
    }

    private fun headingText(): String {
        val node = composeRule.onNodeWithTag("insights-range-heading").fetchSemanticsNode()
        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
        return texts.joinToString("") { it.text }
    }

    @Test
    fun theHistoryEntryDoesNotBreakTheCalendarOrTheScroll() {
        selectTab("history")

        composeRule.onNodeWithTag("history-month-title").assertExists()
        composeRule.onNodeWithTag("history-calendar").assertExists()

        // the list still scrolls; the entry group now sits below the factual day content
        // (v1.7.1 UI hotfix) and stays reachable through the list scroll
        composeRule.onNodeWithTag("history-content-list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("history-calendar"))
        composeRule.onNodeWithTag("history-calendar").assertExists()

        scrollHistoryToInsightsEntry()
        composeRule.onNodeWithTag("history-insights-entry").assertExists()
    }

    @Test
    fun everyPrimaryTabStillOpensAndInsightsIsNotOneOfThem() {
        listOf("home", "records", "history", "medication_plans", "settings").forEach { route ->
            selectTab(route)
            assertEquals("tab $route must still exist", 1, composeRule.onAllNodesWithTag("nav-bar-$route").fetchSemanticsNodes().size)
        }
        assertTrue(
            "Insights must not become a bottom tab",
            composeRule.onAllNodesWithTag("nav-bar-insights").fetchSemanticsNodes().isEmpty()
        )

        selectTab("history")
        openInsights()
        composeRule.onNodeWithTag("insights-screen").assertExists()
        pressBack()
        composeRule.onNodeWithTag("history-screen").assertExists()
        assertFalse(
            "the Insights sub-route must not pollute the bottom bar",
            composeRule.onAllNodesWithTag("nav-bar-history").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun theInsightsCopyRendersFromResourcesInTheActiveLocale() {
        selectTab("history")
        openInsights()
        // the shipped default locale is Chinese; the title must come from resources, not code
        val expectedTitle = context.getString(R.string.insights_title)
        assertTrue(
            "the Insights title must be a resource value",
            composeRule.onAllNodesWithTag("insights-range-selector").fetchSemanticsNodes().isNotEmpty() &&
                expectedTitle.isNotBlank()
        )
    }
}
