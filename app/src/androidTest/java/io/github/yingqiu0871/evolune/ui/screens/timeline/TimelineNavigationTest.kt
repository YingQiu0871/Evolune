package io.github.yingqiu0871.evolune.ui.screens.timeline

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangePhase
import io.github.yingqiu0871.evolune.history.timeline.TimelineViewModel
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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

    // ---------- UI34: activity-scoped retention across the real route cycle ----------

    /**
     * UI34 executed proof on the REAL product path: History -> Timeline -> change month + select a
     * day -> Back -> re-enter Timeline. The activity-scoped ViewModel must keep the previously
     * chosen month/date (no default-current-month reset), the exit must not produce any Timeline
     * read, the re-entry must accept exactly ONE activation refresh, and no duplicate refresh may
     * follow.
     *
     * The probe reads the already-public state of the REAL activity-scoped ViewModel through the
     * Activity ViewModelStore (no production seam is added): `TimelineRangeState.generation`
     * counts accepted generation-producing commands (frozen D-03 semantics), so an exact delta
     * proves "exactly one load" / "exactly one re-entry refresh" / "no duplicate refresh".
     */
    @Test
    fun timelineContextIsRetainedAcrossHistoryRoundTripWithExactlyOneReentryRefresh() {
        selectHistory()
        composeRule.onNodeWithTag("history-timeline-entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-screen").assertExists()

        val initial = awaitSettledTimeline()
        assertEquals("the initial entry owns exactly one load generation", 1, initial.generation)
        assertEquals(initial.today, initial.selectedDate)
        assertEquals(initial.month, YearMonth.from(initial.today))
        assertMonthTitle(initial.month)

        val previousMonth = initial.month.minusMonths(1)
        composeRule.onNodeWithTag("timeline-previous-month").performClick()
        val afterMonthChange = awaitSettledTimeline { it.month == previousMonth }
        assertEquals(
            "one accepted month change = exactly one load generation",
            2,
            afterMonthChange.generation
        )
        assertEquals(previousMonth.atDay(1), afterMonthChange.selectedDate)
        assertMonthTitle(previousMonth)

        val selectedDay = previousMonth.atDay(10)
        composeRule.onNodeWithTag("timeline-day-strip")
            .performScrollToNode(hasTestTag("timeline-day-cell-$selectedDay"))
        composeRule.onNodeWithTag("timeline-day-cell-$selectedDay").performClick()
        composeRule.waitForIdle()
        val afterSelection = probeTimeline()
        assertEquals(selectedDay, afterSelection.selectedDate)
        assertEquals(
            "a day selection performs zero generation-producing commands",
            2,
            afterSelection.generation
        )

        pressBack()
        composeRule.onNodeWithTag("history-screen").assertExists()
        val afterBack = probeTimeline()
        assertEquals("leaving Timeline must not issue any Timeline command", 2, afterBack.generation)
        assertEquals(previousMonth, afterBack.month)
        assertEquals(selectedDay, afterBack.selectedDate)

        composeRule.onNodeWithTag("history-timeline-entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline-screen").assertExists()

        val afterReentry = awaitSettledTimeline { it.generation == 3 }
        assertEquals(
            "re-entry must perform exactly one activation refresh",
            3,
            afterReentry.generation
        )
        assertEquals("the previous month must be retained", previousMonth, afterReentry.month)
        assertEquals("the selected day must be retained", selectedDay, afterReentry.selectedDate)
        assertEquals(
            "the display zone context must be retained",
            initial.displayZone,
            afterReentry.displayZone
        )

        // No duplicate refresh may follow the single re-entry activation.
        composeRule.waitForIdle()
        Thread.sleep(200)
        composeRule.waitForIdle()
        assertEquals(
            "a duplicate re-entry refresh would have claimed another generation",
            3,
            probeTimeline().generation
        )

        assertMonthTitle(previousMonth)
        composeRule.onNodeWithTag("timeline-day-strip")
            .performScrollToNode(hasTestTag("timeline-day-cell-$selectedDay"))
        composeRule
            .onNodeWithTag("timeline-day-cell-$selectedDay")
            .assertContentDescriptionContains(
                context.getString(R.string.history_cell_selected),
                substring = true
            )
    }

    // ---------- UI34 probe helpers ----------

    private data class TimelineProbe(
        val generation: Int,
        val phase: TimelineRangePhase,
        val month: YearMonth,
        val selectedDate: LocalDate,
        val today: LocalDate,
        val displayZone: ZoneId
    )

    /** Reads the REAL activity-scoped ViewModel's published state and logical intent. */
    private fun probeTimeline(): TimelineProbe {
        lateinit var probe: TimelineProbe
        scenario.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[TimelineViewModel::class.java]
            val state = viewModel.state.value
            val intent = viewModel.latestLogicalIntent
            probe = TimelineProbe(
                generation = state.generation,
                phase = state.phase,
                month = intent.requestedMonth,
                selectedDate = intent.selectedDate,
                today = intent.requestToday,
                displayZone = intent.displayZone
            )
        }
        return probe
    }

    private fun awaitSettledTimeline(
        timeoutMillis: Long = 15_000,
        predicate: (TimelineProbe) -> Boolean = { true }
    ): TimelineProbe {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var last: TimelineProbe? = null
        while (SystemClock.uptimeMillis() < deadline) {
            composeRule.waitForIdle()
            val probe = probeTimeline()
            last = probe
            val settled = probe.phase == TimelineRangePhase.CONTENT ||
                probe.phase == TimelineRangePhase.EMPTY_RANGE ||
                probe.phase == TimelineRangePhase.EMPTY_DAY
            if (settled && predicate(probe)) return probe
            Thread.sleep(50)
        }
        throw AssertionError("Timeline did not settle; last probe = $last")
    }

    private fun assertMonthTitle(month: YearMonth) {
        composeRule
            .onNodeWithText(
                context.getString(R.string.timeline_month_title, month.year, month.monthValue)
            )
            .assertExists()
    }
}
