package io.github.yingqiu0871.evolune.ui.screens

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.history.HistoryUiState
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A-03 §21 visual evidence: renders the real History screen on the Pixel 7 AVD and writes PNGs.
 *
 * The captured states are synthetic UI states — no read service, no repository — and the
 * screenshots are visual evidence only; behaviour is asserted by [HistoryScreenTest].
 */
@RunWith(AndroidJUnit4::class)
class HistoryVisualEvidenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2025, 1, 5)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun captureCalendarMixedDayEmptyAndErrorStates() {
        var state by mutableStateOf(mixedState())
        composeRule.setContent {
            EvoluneTheme {
                HistoryScreenContent(state = state)
            }
        }
        composeRule.waitForIdle()

        val outputs = mutableListOf<String>()
        outputs += capture("history-01-current-month-mixed-day")

        state = emptyState()
        composeRule.waitForIdle()
        outputs += capture("history-02-empty-day")

        state = errorState()
        composeRule.waitForIdle()
        outputs += capture("history-03-error-state")

        outputs.forEach { path ->
            val file = File(path)
            assertTrue("screenshot must exist: $path", file.exists() && file.length() > 0L)
        }
    }

    private fun capture(name: String): String {
        val directory = File(context.getExternalFilesDir(null), "a-03-ui").apply { mkdirs() }
        val file = File(directory, "$name.png")
        file.outputStream().use { stream ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return file.absolutePath
    }

    private fun mixedState(): HistoryUiState {
        val day = testDay(
            date = today,
            entries = listOf(
                matchedEntry(),
                matchedEntry(
                    occurrence = io.github.yingqiu0871.evolune.history.testOccurrence(
                        slotId = 9L,
                        time = java.time.LocalTime.of(23, 0)
                    ),
                    event = testEvent(
                        id = 31L,
                        occurredAt = today.plusDays(1).atTime(1, 30).toInstant(ZoneOffset.UTC),
                        localDate = null,
                        zoneId = null,
                        source = MedicationIntakeSource.LEGACY
                    ),
                    provenance = MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE,
                    crossesLocalDateBoundary = true
                ),
                unrecordedEntry(),
                unmatchedEntry(
                    event = testEvent(id = 32L, source = MedicationIntakeSource.MANUAL)
                ),
                unmatchedEntry(
                    event = testEvent(id = 33L, localDate = null, zoneId = null),
                    provenance = HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED
                )
            )
        )
        return baseState(day)
    }

    private fun emptyState(): HistoryUiState = baseState(testDay(date = today))

    private fun errorState(): HistoryUiState = baseState(testDay(date = today)).copy(failed = true)

    private fun baseState(day: HistoricalDay): HistoryUiState = HistoryUiState(
        visibleMonth = YearMonth.of(2025, 1),
        selectedDate = today,
        today = today,
        displayZone = zone,
        loadedMonth = YearMonth.of(2025, 1),
        loadedDays = mapOf(today to day),
        loading = false,
        failed = false
    )
}
