package io.github.yingqiu0871.evolune.history.insights

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * v1.7-B-02 sections 19/20/39: the lifecycle wiring of [InsightsSurfaceLifecycle] on a device.
 *
 * B-02 deliberately ships no user-visible Insights destination (B-03 decides the entry point), so
 * this test hosts the production bridge in a minimal harness instead of inventing a screen. It
 * exercises the **real** Activity lifecycle and real composition entry/exit:
 *
 * - a cold start performs exactly one read (the initial load owns it);
 * - leaving and re-entering the composition (the tab-return shape) refreshes once;
 * - a real `ON_STOP` → `ON_START` transition while the surface is composed refreshes once, and the
 *   cold-start `ON_START` does not add a read.
 */
@RunWith(AndroidJUnit4::class)
class InsightsSurfaceLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val utc: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 13)

    private class RecordingSource : HistoryRangeSource {
        val calls = mutableListOf<Pair<LocalDate, LocalDate>>()

        override suspend fun read(
            startDate: LocalDate,
            endDate: LocalDate,
            displayZone: ZoneId,
            now: Instant
        ): HistoricalRange {
            calls += startDate to endDate
            return HistoricalRange(startDate = startDate, endDate = endDate, days = emptyList())
        }
    }

    private fun viewModel(source: RecordingSource): InsightsViewModel = InsightsViewModel(
        rangeSource = source,
        clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), utc),
        displayZone = { utc }
    )

    @Test
    fun `composition entry refreshes once and a foreground return refreshes once`() {
        val source = RecordingSource()
        val viewModel = viewModel(source)
        var visible by mutableStateOf(true)

        composeRule.setContent {
            if (visible) {
                InsightsSurfaceLifecycle(viewModel)
            }
        }
        composeRule.waitForIdle()

        // cold start: the initial load owns the first read and the first entry adds none
        assertEquals(1, source.calls.size)

        // leave the composition (the tab-return shape) and come back
        visible = false
        composeRule.waitForIdle()
        visible = true
        composeRule.waitForIdle()

        assertEquals("re-entry refreshes exactly once", 2, source.calls.size)

        // real background -> foreground while the surface stays composed
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertEquals("foreground return refreshes exactly once", 3, source.calls.size)
    }

    @Test
    fun `a disposed bridge does not refresh on lifecycle transitions`() {
        val source = RecordingSource()
        val viewModel = viewModel(source)
        var visible by mutableStateOf(true)

        composeRule.setContent {
            if (visible) {
                InsightsSurfaceLifecycle(viewModel)
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, source.calls.size)

        // the destination leaves composition: the observer must be disposed with it
        visible = false
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertEquals("no refresh may happen while Insights is not composed", 1, source.calls.size)

        // re-entry refreshes exactly once through the surface-return contract
        visible = true
        composeRule.waitForIdle()
        assertEquals("re-entry refreshes once", 2, source.calls.size)
    }

    @Test
    fun `the cold start does not refresh on the initial start transition`() {
        val source = RecordingSource()
        val viewModel = viewModel(source)

        composeRule.setContent { InsightsSurfaceLifecycle(viewModel) }
        composeRule.waitForIdle()

        assertEquals("exactly one read after cold start", 1, source.calls.size)
    }
}
