package io.github.yingqiu0871.evolune.ui.screens

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import io.github.yingqiu0871.evolune.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalProjectionBuilder
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.history.HistoryUiState
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * A-03 §21 visual evidence: renders the real History screen on the Pixel 7 AVD and writes PNGs.
 *
 * A-03-UI-R1: the mixed day is produced by the **real** [HistoricalProjectionBuilder] from a
 * production-shaped delayed reminder (planned day D 23:00, confirmed D+1 00:30, planned day
 * persisted, slot id present), so the screenshot itself proves that a cross-date *exact* match
 * renders `实际时间 2025-01-06 00:30` without any inferred annotation.
 *
 * Screenshots are visual evidence only; behaviour is asserted by [HistoryScreenTest] and the JVM
 * presentation tests.
 */
@RunWith(AndroidJUnit4::class)
class HistoryVisualEvidenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneOffset.UTC
    private val plannedDay: LocalDate = LocalDate.of(2025, 1, 5)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun captureCalendarMixedDayEmptyAndErrorStates() {
        val mixed = mixedState()

        // Guard the production shape that the screenshot is meant to prove.
        val matched = mixed.loadedDays.getValue(plannedDay).entries
            .filterIsInstance<io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence>()
            .single()
        assertEquals(plannedDay, matched.displayDate)
        assertFalse("the domain boundary flag is false for this production shape", matched.crossesLocalDateBoundary)
        assertEquals(plannedDay.plusDays(1), matched.event.occurredAt.atZone(zone).toLocalDate())

        var state by mutableStateOf(mixed)
        composeRule.setContent {
            EvoluneTheme {
                HistoryScreenContent(state = state)
            }
        }
        composeRule.waitForIdle()

        val outputs = mutableListOf<String>()
        outputs += capture("history-r1-01-cross-date-exact-and-mixed-day")

        // The cross-date exact card sorts last (23:00), so scroll to it and capture it explicitly:
        // this is the visual proof that the P1 case renders a full actual date without any
        // inferred annotation.
        composeRule.onNodeWithTag("history-content-list")
            .performScrollToNode(hasTestTag("history-entry-${matched.sortKey}"))
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText(
                context.getString(R.string.history_label_actual_time) + " 2025-01-06 00:30"
            )
            .assertExists()
        composeRule
            .onNodeWithText(
                context.getString(R.string.history_label_current_schedule_context) + " 23:00"
            )
            .assertExists()
        outputs += capture("history-r1-04-cross-date-exact-card")

        state = emptyState()
        composeRule.waitForIdle()
        outputs += capture("history-r1-02-empty-day")

        state = errorState()
        composeRule.waitForIdle()
        outputs += capture("history-r1-03-error-state")

        outputs.forEach { path ->
            val file = File(path)
            assertTrue("screenshot must exist: $path", file.exists() && file.length() > 0L)
        }
    }

    private fun capture(name: String): String {
        val directory = File(context.getExternalFilesDir(null), "a-03-ui-r1").apply { mkdirs() }
        val file = File(directory, "$name.png")
        file.outputStream().use { stream ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return file.absolutePath
    }

    /**
     * Mixed day built by the real projection:
     * 23:00 planned occurrence matched by a reminder confirmed at D+1 00:30 (exact, cross-date),
     * 16:00 planned occurrence with no record, plus a manual and a legacy orphan intake.
     */
    private fun mixedState(): HistoryUiState {
        val evening = testOccurrence(slotId = 7L, time = LocalTime.of(23, 0))
        val afternoon = testOccurrence(slotId = 8L, time = LocalTime.of(16, 0))
        val planKey = evening.presentation.matchKey

        val reminderEvent = RecordedMedicationEvent(
            eventId = UUID(7L, 71L),
            occurredAt = plannedDay.plusDays(1).atTime(0, 30).toInstant(ZoneOffset.UTC),
            slotId = evening.slotId,
            matchKey = planKey,
            source = MedicationIntakeSource.REMINDER,
            // production shape: the planned day is persisted together with the actual instant
            localDate = plannedDay,
            zoneId = zone
        )
        val manualOrphan = RecordedMedicationEvent(
            eventId = UUID(7L, 72L),
            occurredAt = plannedDay.atTime(21, 0).toInstant(ZoneOffset.UTC),
            slotId = null,
            matchKey = MedicationMatchKey(routeKey = "INJECTION", medicationKey = "EV", doseAmount = 5.0),
            source = MedicationIntakeSource.MANUAL,
            localDate = plannedDay,
            zoneId = zone
        )
        val legacyOrphan = RecordedMedicationEvent(
            eventId = UUID(7L, 73L),
            occurredAt = plannedDay.atTime(9, 45).toInstant(ZoneOffset.UTC),
            slotId = null,
            matchKey = MedicationMatchKey(routeKey = "SUBLINGUAL", medicationKey = "E2", doseAmount = 1.0),
            source = MedicationIntakeSource.LEGACY,
            localDate = null,
            zoneId = null
        )

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = listOf(evening, afternoon),
            events = listOf(reminderEvent, manualOrphan, legacyOrphan),
            now = plannedDay.plusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC),
            displayZone = zone
        )
        return baseState(HistoricalDay(date = plannedDay, entries = projection.entries))
    }

    private fun emptyState(): HistoryUiState = baseState(HistoricalDay(date = plannedDay, entries = emptyList()))

    private fun errorState(): HistoryUiState = emptyState().copy(failed = true)

    private fun baseState(day: HistoricalDay): HistoryUiState = HistoryUiState(
        visibleMonth = YearMonth.of(2025, 1),
        selectedDate = plannedDay,
        today = plannedDay,
        displayZone = zone,
        loadedMonth = YearMonth.of(2025, 1),
        loadedDays = mapOf(plannedDay to day),
        loading = false,
        failed = false
    )
}
