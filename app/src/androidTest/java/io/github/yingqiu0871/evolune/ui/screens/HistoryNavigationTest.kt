package io.github.yingqiu0871.evolune.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A-03 §16: History is the fifth primary Phone destination.
 *
 * The tab must open, report its selected state and leave the four existing tabs untouched —
 * a bottom-bar item, a route/destination, a title and an icon, nothing else.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HistoryNavigationTest {

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

    @Test
    fun historyTabOpensShowsTheCalendarAndIsSelected() {
        composeRule.selectPrimaryTab("history")

        composeRule.onNodeWithTag("history-screen").assertExists()
        composeRule.onNodeWithTag("history-month-title").assertExists()
        composeRule.onNodeWithTag("history-calendar").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.history_title)).assertExists()
    }

    @Test
    fun everyPrimaryTabStillOpensAndOnlyTheSelectedOneIsMarked() {
        val routes = listOf("home", "records", "history", "medication_plans", "settings")

        routes.forEach { route ->
            composeRule.selectPrimaryTab(route)
            composeRule.onNodeWithTag(composeRule.primaryTabTag(route)).assertIsSelected()
            routes.filter { it != route }.forEach { other ->
                composeRule.onNodeWithTag(composeRule.primaryTabTag(other)).assertIsNotSelected()
            }
        }
    }

    @Test
    fun leavingHistoryKeepsTheOtherTabsBehaviour() {
        composeRule.selectPrimaryTab("history")
        composeRule.onNodeWithTag("history-screen").assertExists()

        composeRule.selectPrimaryTab("records")
        composeRule.onNodeWithTag(composeRule.primaryTabTag("records")).assertIsSelected()
        composeRule.onNodeWithTag("history-screen").assertDoesNotExist()

        composeRule.selectPrimaryTab("home")
        composeRule.onNodeWithTag(composeRule.primaryTabTag("home")).assertIsSelected()
    }

    @Test
    fun navigationChromeStillHostsExactlyOneItemPerPrimaryDestination() {
        val route = "history"
        val barNodes = composeRule.onAllNodesWithTag("navigation-bar").fetchSemanticsNodes()
        val railNodes = composeRule.onAllNodesWithTag("navigation-rail").fetchSemanticsNodes()

        assertTrue("exactly one navigation chrome is expected", barNodes.size + railNodes.size == 1)
        composeRule.onAllNodesWithTag(composeRule.primaryTabTag(route)).fetchSemanticsNodes().let { nodes ->
            assertTrue("history must have exactly one navigation item", nodes.size == 1)
        }
    }
}
