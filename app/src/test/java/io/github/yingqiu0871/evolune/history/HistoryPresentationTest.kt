package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A-03 §19: presentation mapping. The mapping translates and labels only — these tests pin
 * which label a card gets, where its timestamp comes from and that no presentation invents
 * medication metadata or blaming wording.
 */
class HistoryPresentationTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    @Test
    fun `exact matched entry carries recorded status and both times`() {
        val entry = matchedEntry()

        val model = HistoryPresentation.entry(entry, utc)

        assertEquals(HistoryEntryKind.MATCHED, model.kind)
        assertEquals(R.string.history_status_recorded, model.statusLabelRes)
        assertFalse(model.isInferredMatch)
        assertNull(model.noteRes)
        assertEquals("Synthetic plan", model.planName)
        assertEquals(R.string.route_oral, model.routeLabelRes)
        assertEquals(R.string.ester_e2, model.medicationLabelRes)
        assertEquals(2.0, model.doseAmount!!, 0.0)
        assertNotNull(model.actualTime)
        assertNotNull(model.scheduleTime)
    }

    @Test
    fun `inferred matched entry is visibly annotated`() {
        val entry = matchedEntry(provenance = MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE)

        val model = HistoryPresentation.entry(entry, utc)

        assertTrue(model.isInferredMatch)
        assertEquals(R.string.history_note_inferred_match, model.noteRes)
        // exact and inferred must not be presented identically
        val exact = HistoryPresentation.entry(matchedEntry(), utc)
        assertTrue(exact.noteRes != model.noteRes)
    }

    @Test
    fun `unrecorded entry keeps the non-blaming wording`() {
        val model = HistoryPresentation.entry(unrecordedEntry(), utc)

        assertEquals(HistoryEntryKind.UNRECORDED, model.kind)
        assertEquals(R.string.history_status_no_recorded_intake, model.statusLabelRes)
        assertEquals(R.string.history_note_no_recorded_intake, model.noteRes)
        assertEquals(R.string.history_note_not_necessarily_missed, model.secondaryNoteRes)
        assertNull(model.actualTime)
        assertNotNull(model.scheduleTime)
    }

    @Test
    fun `unmatched manual entry is marked manual`() {
        val entry = unmatchedEntry(
            event = testEvent(id = 5L, source = MedicationIntakeSource.MANUAL)
        )

        val model = HistoryPresentation.entry(entry, utc)

        assertEquals(HistoryEntryKind.UNMATCHED, model.kind)
        assertEquals(R.string.history_status_recorded_intake, model.statusLabelRes)
        assertTrue(model.isManualSource)
        assertEquals(R.string.history_source_manual, model.sourceLabelRes)
    }

    @Test
    fun `unmatched non manual entry is never marked manual`() {
        MedicationIntakeSource.entries
            .filter { it != MedicationIntakeSource.MANUAL }
            .forEach { source ->
                val model = HistoryPresentation.entry(
                    unmatchedEntry(event = testEvent(id = 6L, source = source)),
                    utc
                )

                assertFalse("$source must not be presented as manual", model.isManualSource)
                assertTrue(
                    "$source must keep its own source label",
                    model.sourceLabelRes != null && model.sourceLabelRes != R.string.history_source_manual
                )
            }
    }

    @Test
    fun `unmatched entry does not claim plan ownership`() {
        val model = HistoryPresentation.entry(unmatchedEntry(), utc)

        assertNull(model.planName)
        assertEquals(R.string.history_note_plan_unavailable, model.noteRes)
    }

    @Test
    fun `unmatched entry with a derived date explains the current time zone`() {
        val entry = unmatchedEntry(
            event = testEvent(id = 7L, localDate = null, zoneId = null),
            provenance = HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED
        )

        val model = HistoryPresentation.entry(entry, utc)

        assertEquals(R.string.history_note_current_zone_date, model.secondaryNoteRes)
    }

    @Test
    fun `matched actual time uses the persisted event zone`() {
        val event = testEvent(
            id = 8L,
            occurredAt = Instant.parse("2025-01-04T23:30:00Z"),
            localDate = TEST_DAY,
            zoneId = tokyo
        )

        val model = HistoryPresentation.entry(matchedEntry(event = event), utc)

        assertEquals(tokyo, model.actualTime!!.zone)
        assertEquals(utc, model.scheduleTime!!.zone)
    }

    @Test
    fun `matched actual time falls back to the display zone`() {
        val event = testEvent(id = 9L, localDate = null, zoneId = null)

        val model = HistoryPresentation.entry(matchedEntry(event = event), utc)

        assertEquals(utc, model.actualTime!!.zone)
    }

    @Test
    fun `delayed actual intake shows a full date and time`() {
        val event = testEvent(
            id = 10L,
            occurredAt = TEST_DAY.plusDays(1).atTime(1, 30).toInstant(ZoneOffset.UTC),
            localDate = null,
            zoneId = null
        )
        // The domain flag is deliberately false here: the rendered actual date decides.
        val model = HistoryPresentation.entry(
            matchedEntry(event = event, crossesLocalDateBoundary = false),
            utc
        )

        assertTrue(model.actualTime!!.needsFullDate)
        val text = HistoryFormatting.actualIntakeText(
            instant = model.actualTime!!.instant,
            zone = model.actualTime!!.zone,
            needsFullDate = model.actualTime!!.needsFullDate,
            is24Hour = true
        )
        assertEquals("2025-01-06 01:30", text)
    }

    @Test
    fun `same day actual intake shows time only`() {
        val model = HistoryPresentation.entry(matchedEntry(), utc)

        assertFalse(model.actualTime!!.needsFullDate)
        assertEquals(
            "08:05",
            HistoryFormatting.actualIntakeText(
                instant = model.actualTime!!.instant,
                zone = model.actualTime!!.zone,
                needsFullDate = false,
                is24Hour = true
            )
        )
    }

    @Test
    fun `current schedule context is exposed for matched and unrecorded entries`() {
        val matched = HistoryPresentation.entry(matchedEntry(), utc)
        val unrecorded = HistoryPresentation.entry(unrecordedEntry(), utc)

        listOf(matched, unrecorded).forEach { model ->
            assertNotNull(model.scheduleTime)
            assertEquals(utc, model.scheduleTime!!.zone)
        }
        assertEquals(
            TEST_DAY.atTime(8, 0).toInstant(ZoneOffset.UTC),
            matched.scheduleTime!!.instant
        )
        assertEquals(
            TEST_DAY.atTime(16, 0).toInstant(ZoneOffset.UTC),
            unrecorded.scheduleTime!!.instant
        )
    }

    @Test
    fun `anti androgen route does not claim an ester name`() {
        val occurrence = testOccurrence(
            slotId = 4L,
            routeKey = "ANTIANDROGEN",
            medicationKey = "CPA",
            doseAmount = 50.0
        )

        val model = HistoryPresentation.entry(matchedEntry(occurrence = occurrence), utc)

        assertNull(model.medicationLabelRes)
        assertEquals("CPA", model.medicationFallback)
        assertEquals(R.string.route_antiandrogen, model.routeLabelRes)
    }

    @Test
    fun `month grid has leading blanks today selection and disabled future days`() {
        val state = state(
            today = TEST_DAY,
            selectedDate = TEST_DAY,
            loadedDays = mapOf(
                TEST_DAY to testDay(
                    date = TEST_DAY,
                    entries = listOf(
                        matchedEntry(),
                        unrecordedEntry(),
                        unmatchedEntry(event = testEvent(id = 11L, source = MedicationIntakeSource.MANUAL))
                    )
                )
            )
        )

        val model = HistoryPresentation.present(state)

        // 2025-01-01 is a Wednesday: two leading blanks, then 31 day cells.
        assertEquals(33, model.cells.size)
        assertNull(model.cells[0].date)
        assertNull(model.cells[1].date)
        assertEquals(LocalDate.of(2025, 1, 1), model.cells[2].date)

        val todayCell = model.cells.single { it.date == TEST_DAY }
        assertTrue(todayCell.isToday)
        assertTrue(todayCell.isSelected)
        assertTrue(todayCell.isEnabled)
        // counts are the source of truth; the indicator flags derive from them
        assertEquals(1, todayCell.recordedCount)
        assertEquals(1, todayCell.unrecordedCount)
        assertEquals(1, todayCell.unmatchedActualCount)
        assertTrue(todayCell.hasRecorded)
        assertTrue(todayCell.hasUnrecorded)
        assertTrue(todayCell.hasUnmatchedActual)

        val futureCell = model.cells.single { it.date == TEST_DAY.plusDays(1) }
        assertFalse(futureCell.isEnabled)
        assertFalse(futureCell.hasRecorded)
        assertEquals(0, futureCell.recordedCount)

        assertFalse(model.canGoToNextMonth)
        assertEquals(HistoryDayPhase.CONTENT, model.phase)
        assertEquals(1, model.day!!.recordedCount)
        assertEquals(1, model.day!!.unrecordedCount)
        assertEquals(1, model.day!!.unmatchedActualCount)
    }

    @Test
    fun `calendar cells carry the real day counts`() {
        val entries = List(3) { index -> matchedEntry(event = testEvent(id = 100L + index)) } +
            List(2) { index -> unrecordedEntry(occurrence = testOccurrence(slotId = 20L + index)) } +
            List(4) { index ->
                unmatchedEntry(event = testEvent(id = 200L + index, source = MedicationIntakeSource.MANUAL))
            }
        val state = state(
            loadedDays = mapOf(TEST_DAY to testDay(date = TEST_DAY, entries = entries))
        )

        val cell = HistoryPresentation.present(state).cells.single { it.date == TEST_DAY }

        assertEquals(3, cell.recordedCount)
        assertEquals(2, cell.unrecordedCount)
        assertEquals(4, cell.unmatchedActualCount)
        assertTrue(cell.hasRecorded)
        assertTrue(cell.hasUnrecorded)
        assertTrue(cell.hasUnmatchedActual)
    }

    @Test
    fun `a day without history reports zero counts and no indicators`() {
        val state = state(loadedDays = mapOf(TEST_DAY to testDay(date = TEST_DAY)))

        val cell = HistoryPresentation.present(state).cells.single { it.date == TEST_DAY }

        assertEquals(0, cell.recordedCount)
        assertEquals(0, cell.unrecordedCount)
        assertEquals(0, cell.unmatchedActualCount)
        assertFalse(cell.hasRecorded)
        assertFalse(cell.hasUnrecorded)
        assertFalse(cell.hasUnmatchedActual)
    }

    @Test
    fun `past month can move forward and past day cells stay enabled`() {
        val pastMonth = YearMonth.of(2024, 12)
        val state = state(
            visibleMonth = pastMonth,
            loadedMonth = pastMonth,
            selectedDate = LocalDate.of(2024, 12, 20),
            loadedDays = mapOf(
                LocalDate.of(2024, 12, 20) to testDay(date = LocalDate.of(2024, 12, 20))
            )
        )

        val model = HistoryPresentation.present(state)

        assertTrue(model.canGoToNextMonth)
        assertTrue(model.cells.filter { it.date != null }.all { it.isEnabled })
        assertEquals(HistoryDayPhase.EMPTY, model.phase)
    }

    @Test
    fun `loading and error phases keep the month grid`() {
        val loading = HistoryPresentation.present(state(loading = true, loadedMonth = null))
        assertEquals(HistoryDayPhase.LOADING, loading.phase)
        assertEquals(33, loading.cells.size)

        val failed = HistoryPresentation.present(state(failed = true))
        assertEquals(HistoryDayPhase.ERROR, failed.phase)
        assertEquals(33, failed.cells.size)
        assertEquals(TEST_DAY, failed.selectedDate)
    }

    @Test
    fun `dose and timestamp formatting follow the existing conventions`() {
        assertEquals("2.0 mg", HistoryFormatting.dose(2.0))
        assertEquals("5.0 mg", HistoryFormatting.dose(5.0))
        assertEquals("0.25 mg", HistoryFormatting.dose(0.25))
        assertEquals(
            "16:30",
            HistoryFormatting.timeText(TEST_DAY.atTime(16, 30).toInstant(ZoneOffset.UTC), utc, true)
        )
        assertEquals(
            "2025-01-05 16:30",
            HistoryFormatting.fullDateTimeText(
                TEST_DAY.atTime(16, 30).toInstant(ZoneOffset.UTC),
                utc,
                true
            )
        )
    }

    @Test
    fun `local time in another zone is rendered in that zone`() {
        val instant = Instant.parse("2025-01-05T00:30:00Z")

        assertEquals("09:30", HistoryFormatting.timeText(instant, tokyo, true))
        assertEquals("00:30", HistoryFormatting.timeText(instant, utc, true))
        assertEquals(LocalDate.of(2025, 1, 5), HistoryFormatting.localDate(instant, tokyo))
    }

    private fun state(
        visibleMonth: YearMonth = YearMonth.of(2025, 1),
        selectedDate: LocalDate = TEST_DAY,
        today: LocalDate = TEST_DAY,
        loadedMonth: YearMonth? = YearMonth.of(2025, 1),
        loadedDays: Map<LocalDate, HistoricalDay> = emptyMap(),
        loading: Boolean = false,
        failed: Boolean = false,
        displayZone: ZoneId = utc
    ): HistoryUiState = HistoryUiState(
        visibleMonth = visibleMonth,
        selectedDate = selectedDate,
        today = today,
        displayZone = displayZone,
        loadedMonth = loadedMonth,
        loadedDays = loadedDays,
        loading = loading,
        failed = failed
    )
}
