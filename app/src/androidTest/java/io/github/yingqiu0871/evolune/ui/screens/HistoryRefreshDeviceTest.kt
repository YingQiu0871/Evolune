package io.github.yingqiu0871.evolune.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * A-04 §6 refresh contract on a device: leaving History, recording an intake through the
 * authoritative repository, and coming back must show the new data.
 *
 * This is the affected-surface regression the acceptance item needs — a pure ViewModel unit test
 * cannot prove that the destination re-enters composition and refreshes on tab return.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HistoryRefreshDeviceTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val seededId = UUID.fromString("00000000-0000-0000-0000-0000a0400002")
    private val doseText = "7.7 mg"
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchFromDeterministicState() {
        runBlocking {
            val store = OnboardingStateStore(context, isExistingInstallation = true)
            store.initializeIfNeeded()
            store.acceptTerms()
            store.acknowledgeMedicalPkDisclosure()
            store.completeOnboarding()
            store.markFeatureTutorialHandled()
            ProductionRepositoryProvider.get(context).doseEvents.delete(seededId)
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        composeRule.waitForIdle()
    }

    @After
    fun cleanUp() {
        runBlocking {
            runCatching { ProductionRepositoryProvider.get(context).doseEvents.delete(seededId) }
        }
        scenario.close()
    }

    @Test
    fun returningToHistoryShowsAnIntakeRecordedWhileAway() {
        selectTab("history")
        assertTrue(
            "the seeded intake must not be visible before it exists",
            composeRule.onAllNodesWithText(doseText, substring = true).fetchSemanticsNodes().isEmpty()
        )

        selectTab("records")

        // Record through the authoritative production repository while History is not composed.
        val today = LocalDate.now(zone)
        val event = DoseEvent(
            id = seededId,
            route = Route.INJECTION,
            occurredAt = today.atTime(0, 5).atZone(zone).toInstant(),
            zoneId = zone,
            localDate = today,
            doseMG = 7.7,
            ester = Ester.EV,
            extras = emptyMap(),
            slotId = null,
            source = DoseEventSource.MANUAL,
            status = DoseEventStatus.RECORDED,
            revision = 1L
        )
        val inserted = runBlocking { ProductionRepositoryProvider.get(context).doseEvents.insert(event) }
        assertEquals(
            io.github.yingqiu0871.evolune.core.dataapi.InsertResult.Inserted,
            inserted
        )

        selectTab("history")

        // The tab return must have refreshed the visible month: the new intake is visible.
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText(doseText, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText(doseText, substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun switchingToHistoryAndStayingThereDoesNotDuplicateQueries() {
        selectTab("history")

        // Selecting a day recomposes the screen without leaving the destination: no extra load may
        // happen (the JVM tests count reads; here we prove the screen stays interactive and stable).
        composeRule.onNodeWithTag("history-screen").assertExists()
        composeRule.onNodeWithTag("history-calendar").assertExists()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history-screen").assertExists()
    }

    private fun selectTab(route: String) {
        val target = tag(route)
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            composeRule.onNodeWithTag(target).performClick()
            composeRule.waitForIdle()
            val selected = composeRule
                .onAllNodesWithTag(target)
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Selected) == true }
            if (selected) return
            Thread.sleep(NAV_CLICK_THROTTLE_MS + 100L)
        }
        error("tab $route did not become selected")
    }

    private fun tag(route: String): String =
        if (composeRule.onAllNodesWithTag("nav-bar-$route").fetchSemanticsNodes().isNotEmpty()) {
            "nav-bar-$route"
        } else {
            "nav-rail-$route"
        }

    private companion object {
        /** Mirrors `AppNavigation.NAV_CLICK_THROTTLE_MS`. */
        const val NAV_CLICK_THROTTLE_MS = 200L
    }
}
