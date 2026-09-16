package io.github.yingqiu0871.evolune.ui.screens.timeline

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.timeline.TimelineViewModel
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
 * V17-D-04 §32 — the lifecycle wiring of [TimelineSurfaceLifecycle] on a device, exercising the
 * real Activity lifecycle and real composition entry/exit through the production bridge:
 *
 * - a cold start performs exactly one read (the initial D-03 load owns it) and the first entry
 *   adds none;
 * - a leave/return of the composition refreshes once;
 * - a real `ON_STOP` → `ON_START` transition refreshes once;
 * - ordinary recomposition and a day selection perform zero reads.
 */
@RunWith(AndroidJUnit4::class)
class TimelineLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val utc: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 16)

    private class RecordingSource : HistoryRangeSource {
        val calls = mutableListOf<Pair<ZoneId, Instant>>()

        override suspend fun read(
            startDate: LocalDate,
            endDate: LocalDate,
            displayZone: ZoneId,
            now: Instant
        ): HistoricalRange {
            calls += displayZone to now
            return HistoricalRange(startDate = startDate, endDate = endDate, days = emptyList())
        }
    }

    private fun viewModel(source: RecordingSource): TimelineViewModel = TimelineViewModel(
        rangeSource = source,
        clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), utc),
        displayZone = { utc }
    )

    @Test
    fun coldStartReEntryAndForegroundReturnEachDriveExactlyOneRead() {
        val source = RecordingSource()
        val viewModel = viewModel(source)
        var visible by mutableStateOf(true)

        composeRule.setContent {
            if (visible) {
                TimelineSurfaceLifecycle(viewModel)
            }
        }
        composeRule.waitForIdle()

        assertEquals("the initial load owns the cold start", 1, source.calls.size)

        visible = false
        composeRule.waitForIdle()
        visible = true
        composeRule.waitForIdle()
        assertEquals("re-entry refreshes exactly once", 2, source.calls.size)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertEquals("the foreground return refreshes exactly once", 3, source.calls.size)
    }

    @Test
    fun aDisposedBridgeDoesNotRefreshOnLifecycleTransitions() {
        val source = RecordingSource()
        val viewModel = viewModel(source)
        var visible by mutableStateOf(true)

        composeRule.setContent {
            if (visible) {
                TimelineSurfaceLifecycle(viewModel)
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, source.calls.size)

        visible = false
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertEquals("no refresh may happen while Timeline is not composed", 1, source.calls.size)
    }

    @Test
    fun ordinaryRecompositionPerformsZeroReads() {
        val source = RecordingSource()
        val viewModel = viewModel(source)
        var tick by mutableStateOf(0)

        composeRule.setContent {
            Text("tick $tick")
            TimelineSurfaceLifecycle(viewModel)
        }
        composeRule.waitForIdle()
        assertEquals(1, source.calls.size)

        tick = 1
        composeRule.waitForIdle()
        tick = 2
        composeRule.waitForIdle()
        assertEquals("recomposition must never read the source", 1, source.calls.size)
    }

    @Test
    fun daySelectionPerformsZeroReads() {
        val source = RecordingSource()
        val viewModel = viewModel(source)

        composeRule.setContent { TimelineSurfaceLifecycle(viewModel) }
        composeRule.waitForIdle()
        assertEquals(1, source.calls.size)

        viewModel.selectDate(LocalDate.of(2026, 9, 10))
        composeRule.waitForIdle()
        assertEquals("selection is a zero-read local intent", 1, source.calls.size)
        assertEquals(LocalDate.of(2026, 9, 10), viewModel.latestLogicalIntent.selectedDate)
    }
}
