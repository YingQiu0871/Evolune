package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.HistoryViewModel
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A-03 §20: History screen behaviour on a device.
 *
 * The screen is driven through the real [HistoryViewModel] with a counting/failing/gated read
 * seam, so these tests cover the actual state machine and rendering rather than a stub.
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2025, 1, 5)
    private val now: Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    @Test
    fun currentMonthRendersWithTitleWeekdaysAndTodaySelected() {
        launch()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history-screen").assertExists()
        composeRule.onNodeWithText(text(R.string.history_month_title, 2025, 1)).assertExists()
        composeRule.onNodeWithTag("history-weekday-header").assertExists()
        composeRule.onNodeWithTag("history-calendar").assertExists()
        composeRule.onNodeWithTag("history-cell-$today").assertExists()
        assertTrue(
            composeRule
                .onAllNodesWithContentDescription(text(R.string.history_cell_today), substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }

    @Test
    fun previousMonthLoadsOnceAndNextMonthIsDisabledAtTheCurrentMonth() {
        val source = launch()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history-next-month").assertIsNotEnabled()

        composeRule.onNodeWithTag("history-previous-month").performClick()
        composeRule.waitForIdle()

        assertEquals(2, source.calls.size)
        composeRule.onNodeWithText(text(R.string.history_month_title, 2024, 12)).assertExists()
        composeRule.onNodeWithTag("history-next-month").assertIsEnabled()

        composeRule.onNodeWithTag("history-next-month").performClick()
        composeRule.waitForIdle()

        assertEquals(3, source.calls.size)
        composeRule.onNodeWithText(text(R.string.history_month_title, 2025, 1)).assertExists()
    }

    @Test
    fun selectingAnotherDayOfTheSameMonthDoesNotQueryAgain() {
        val source = launch { _, end -> listOf(testDay(date = end)) }
        composeRule.waitForIdle()

        val other = LocalDate.of(2025, 1, 3)
        composeRule.onNodeWithTag("history-cell-$other").performClick()
        composeRule.waitForIdle()

        assertEquals(1, source.calls.size)
        composeRule.onNodeWithText(text(R.string.history_day_title, 1, 3)).assertExists()
    }

    @Test
    fun futureDatesAreDisabledAndNotSelectable() {
        val source = launch { _, end -> listOf(testDay(date = end)) }
        composeRule.waitForIdle()

        val future = today.plusDays(1)
        composeRule.onNodeWithTag("history-cell-$future").assertIsNotEnabled()
        assertTrue(
            composeRule
                .onAllNodesWithContentDescription(text(R.string.history_cell_not_arrived), substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        // A disabled cell exposes no click action, so the selection cannot move and the
        // already loaded month is never re-queried.
        composeRule.onNodeWithTag("history-cell-$future").assertHasNoClickAction()
        assertEquals(1, source.calls.size)
        composeRule.onNodeWithText(text(R.string.history_day_title, 1, 5)).assertExists()
    }

    @Test
    fun calendarShowsAllThreeIndicatorClasses() {
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(
                        matchedEntry(),
                        unrecordedEntry(),
                        unmatchedEntry(event = testEvent(id = 21L, source = MedicationIntakeSource.MANUAL))
                    )
                )
            )
        }
        composeRule.waitForIdle()

        // The calendar cell merges its descendants for accessibility, so the indicator dots
        // are only visible as individual nodes in the unmerged tree.
        listOf(
            "history-indicator-recorded" to "recorded",
            "history-indicator-unrecorded" to "unrecorded",
            "history-indicator-unmatched" to "unmatched"
        ).forEach { (tag, label) ->
            assertTrue(
                "$label indicator expected",
                composeRule
                    .onAllNodesWithTag(tag, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            )
        }
    }

    @Test
    fun daySummaryShowsTheThreeCounts() {
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(
                        matchedEntry(),
                        unrecordedEntry(),
                        unmatchedEntry(event = testEvent(id = 22L, source = MedicationIntakeSource.MANUAL))
                    )
                )
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history-day-summary").assertExists()
        composeRule.onNodeWithText(text(R.string.history_summary_recorded, 1)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_summary_no_recorded_intake, 1)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_summary_other_intake, 1)).assertExists()
    }

    @Test
    fun exactMatchedCardShowsStatusActualTimeAndCurrentScheduleContext() {
        launch { _, end -> listOf(testDay(date = end, entries = listOf(matchedEntry()))) }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_status_recorded)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_label_actual_time) + " 08:05").assertExists()
        composeRule.onNodeWithText(text(R.string.history_label_current_schedule_context) + " 08:00").assertExists()
        assertTrue(
            composeRule
                .onAllNodesWithText(text(R.string.history_note_inferred_match))
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun inferredMatchedCardCarriesAProvenanceNeutralAnnotation() {
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(
                        matchedEntry(provenance = MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE)
                    )
                )
            )
        }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_status_recorded)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_note_inferred_match)).assertExists()
        // The wording must not claim a legacy origin.
        assertTrue(
            composeRule.onAllNodesWithText("旧版", substring = true).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun aModernQuickRecordMatchedByTheTimeWindowAlsoGetsTheNeutralAnnotation() {
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(
                        matchedEntry(
                            event = testEvent(id = 51L, source = MedicationIntakeSource.MANUAL),
                            provenance = MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW
                        )
                    )
                )
            )
        }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_note_inferred_match)).assertExists()
        assertTrue(
            composeRule.onAllNodesWithText("旧版", substring = true).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun crossDateExactMatchStillShowsTheFullActualDate() {
        // Production shape: the reminder persists the planned day D while the confirmation
        // happened at D+1 00:30, so the match is exact and crossesLocalDateBoundary is false.
        val occurrence = testOccurrence(slotId = 7L, time = LocalTime.of(23, 0))
        val event = testEvent(
            id = 52L,
            occurredAt = today.plusDays(1).atTime(0, 30).toInstant(ZoneOffset.UTC),
            slotId = occurrence.slotId,
            localDate = today,
            zoneId = zone,
            source = MedicationIntakeSource.REMINDER
        )
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(
                        matchedEntry(
                            occurrence = occurrence,
                            event = event,
                            provenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE,
                            crossesLocalDateBoundary = false
                        )
                    )
                )
            )
        }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_label_actual_time) + " 2025-01-06 00:30")
            .assertExists()
        composeRule.onNodeWithText(text(R.string.history_label_current_schedule_context) + " 23:00")
            .assertExists()
        assertTrue(
            composeRule.onAllNodesWithText(text(R.string.history_note_inferred_match)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun delayedActualIntakeShowsAFullDateAndTime() {
        val event = testEvent(
            id = 23L,
            occurredAt = today.plusDays(1).atTime(1, 30).toInstant(ZoneOffset.UTC),
            localDate = null,
            zoneId = null
        )
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(matchedEntry(event = event, crossesLocalDateBoundary = true))
                )
            )
        }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_label_actual_time) + " 2025-01-06 01:30")
            .assertExists()
    }

    @Test
    fun unrecordedCardUsesNonBlamingWording() {
        launch { _, end -> listOf(testDay(date = end, entries = listOf(unrecordedEntry()))) }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_status_no_recorded_intake)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_note_no_recorded_intake)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_note_not_necessarily_missed)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_label_current_schedule_context) + " 16:00")
            .assertExists()
    }

    @Test
    fun unmatchedManualIntakeIsLabelledManual() {
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(
                        unmatchedEntry(event = testEvent(id = 24L, source = MedicationIntakeSource.MANUAL))
                    )
                )
            )
        }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_status_recorded_intake)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_source_manual)).assertExists()
        composeRule.onNodeWithText(text(R.string.history_note_plan_unavailable)).assertExists()
    }

    @Test
    fun unmatchedNonManualIntakeIsNotLabelledManual() {
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(
                        unmatchedEntry(event = testEvent(id = 25L, source = MedicationIntakeSource.WEAR))
                    )
                )
            )
        }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_source_wear)).assertExists()
        assertTrue(
            composeRule
                .onAllNodesWithText(text(R.string.history_source_manual))
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun unmatchedWithDerivedDateExplainsTheCurrentTimeZone() {
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = listOf(
                        unmatchedEntry(
                            event = testEvent(id = 26L, localDate = null, zoneId = null),
                            provenance = HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED
                        )
                    )
                )
            )
        }
        composeRule.waitForIdle()
        scrollToEntryStatus()

        composeRule.onNodeWithText(text(R.string.history_note_current_zone_date)).assertExists()
    }

    @Test
    fun emptyDayShowsTheEmptyState() {
        launch { _, end -> listOf(testDay(date = end)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history-empty-day").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.history_empty_day)).assertExists()
    }

    @Test
    fun loadingStateIsRenderedWhileTheReadIsPending() {
        val gate = CompletableDeferred<Unit>()
        launch(gate = gate)

        composeRule.onNodeWithTag("history-loading").assertIsDisplayed()
        gate.complete(Unit)
        composeRule.waitForIdle()
    }

    @Test
    fun errorStateOffersRetryAndRecovers() {
        val source = launch(failure = IllegalStateException("synthetic failure"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history-error").assertIsDisplayed()
        assertEquals(1, source.calls.size)

        source.currentFailure = null
        composeRule.onNodeWithTag("history-retry").performClick()
        composeRule.waitForIdle()

        assertEquals(2, source.calls.size)
        composeRule.onNodeWithTag("history-error").assertDoesNotExist()
        composeRule.onNodeWithTag("history-empty-day").assertIsDisplayed()
    }

    @Test
    fun calendarCellContentDescriptionCarriesTheRealDayCounts() {
        launch { _, end ->
            listOf(
                testDay(
                    date = end,
                    entries = List(3) { index -> matchedEntry(event = testEvent(id = 300L + index)) } +
                        List(2) { index ->
                            unrecordedEntry(occurrence = testOccurrence(slotId = 30L + index))
                        } +
                        List(4) { index ->
                            unmatchedEntry(
                                event = testEvent(id = 400L + index, source = MedicationIntakeSource.MANUAL)
                            )
                        }
                )
            )
        }
        composeRule.waitForIdle()

        val expectedParts = listOf(
            today.toString(),
            text(R.string.history_cell_today),
            text(R.string.history_cell_selected),
            text(R.string.history_cell_recorded, 3),
            text(R.string.history_cell_no_recorded_intake, 2),
            text(R.string.history_cell_other_intake, 4)
        )
        expectedParts.forEach { part ->
            assertTrue(
                "calendar cell description must contain '$part'",
                composeRule
                    .onAllNodesWithContentDescription(part, substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            )
        }
        // The fabricated "1" counts of the previous implementation must be gone.
        listOf(
            text(R.string.history_cell_recorded, 1),
            text(R.string.history_cell_no_recorded_intake, 1),
            text(R.string.history_cell_other_intake, 1)
        ).forEach { stale ->
            assertTrue(
                "calendar cell description must not report '$stale'",
                composeRule
                    .onAllNodesWithContentDescription(stale, substring = true)
                    .fetchSemanticsNodes()
                    .isEmpty()
            )
        }
        composeRule.onNodeWithTag("history-cell-$today").assertExists()
    }

    @Test
    fun aDayWithoutHistoryReportsNoIndicators() {
        launch { _, end -> listOf(testDay(date = end)) }
        composeRule.waitForIdle()

        listOf(
            "history-indicator-recorded",
            "history-indicator-unrecorded",
            "history-indicator-unmatched"
        ).forEach { tag ->
            assertTrue(
                "$tag must not exist for a day without history",
                composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            )
        }
        assertTrue(
            composeRule
                .onAllNodesWithContentDescription(text(R.string.history_cell_no_history), substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }

    // ---------- helpers ----------

    private fun scrollToEntryStatus() {
        composeRule.onNodeWithTag("history-content-list")
            .performScrollToNode(hasTestTag("history-entry-status"))
    }

    /** `days` is last so tests can pass the month content as a trailing lambda. */
    private fun launch(
        failure: Throwable? = null,
        gate: CompletableDeferred<Unit>? = null,
        days: (LocalDate, LocalDate) -> List<HistoricalDay> = { _, _ -> emptyList() }
    ): FakeHistoryRangeSource {
        val source = FakeHistoryRangeSource(days = days, gate = gate).apply {
            currentFailure = failure
        }
        val viewModel = HistoryViewModel(
            rangeSource = source,
            clock = Clock.fixed(now, zone),
            displayZone = { zone }
        )
        composeRule.setContent {
            EvoluneTheme {
                HistoryScreen(viewModel = viewModel)
            }
        }
        return source
    }

    private class FakeHistoryRangeSource(
        private val days: (LocalDate, LocalDate) -> List<HistoricalDay>,
        private val gate: CompletableDeferred<Unit>?
    ) : HistoryRangeSource {

        val calls = mutableListOf<Pair<LocalDate, LocalDate>>()
        var currentFailure: Throwable? = null

        override suspend fun read(
            startDate: LocalDate,
            endDate: LocalDate,
            displayZone: ZoneId,
            now: Instant
        ): HistoricalRange {
            calls += startDate to endDate
            gate?.await()
            currentFailure?.let { throw it }
            return HistoricalRange(startDate = startDate, endDate = endDate, days = days(startDate, endDate))
        }
    }
}
