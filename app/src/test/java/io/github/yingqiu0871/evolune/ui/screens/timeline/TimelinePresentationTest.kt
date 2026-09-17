package io.github.yingqiu0871.evolune.ui.screens.timeline

import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.history.HistoryFormatting
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.history.timeline.TimelineDay
import io.github.yingqiu0871.evolune.history.timeline.TimelineProjectionBuilder
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangePhase
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeState
import io.github.yingqiu0871.evolune.history.timeline.TimelineReadModel
import io.github.yingqiu0871.evolune.history.unmatchedEntry
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * V17-D-04 §31 — deterministic presentation coverage: UI9–UI19, UI22–UI26, UI29–UI30, UI44–UI45,
 * UI51–UI53 and the day-cell/section mapping of the published state.
 *
 * The presenter is a pure function of the published [TimelineRangeState]; these tests also act as
 * the "renderer never observes a clock or a system zone" proof at the JVM boundary.
 */
class TimelinePresentationTest {

    private val utc: ZoneOffset = ZoneOffset.UTC
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")
    private val today: LocalDate = LocalDate.of(2026, 9, 16)
    private val locale: Locale = Locale.CHINA

    private fun daysFrom(vararg days: Pair<LocalDate, List<HistoricalEntry>>): List<TimelineDay> =
        TimelineProjectionBuilder.build(
            HistoricalRange(
                startDate = days.first().first.withDayOfMonth(1),
                endDate = days.last().first,
                days = days.map { (date, entries) -> HistoricalDay(date = date, entries = entries) }
            )
        ).days

    private fun unrecordedOn(date: LocalDate, slotId: Long): HistoricalEntry =
        unrecordedEntry(occurrence = testOccurrence(slotId = slotId, date = date, time = LocalTime.of(8, 0)))

    private fun state(
        phase: TimelineRangePhase = TimelineRangePhase.CONTENT,
        days: List<TimelineDay> = emptyList(),
        selectedDate: LocalDate = today,
        snapshotToday: LocalDate = today,
        displayZone: ZoneId = utc,
        requestedMonth: YearMonth = YearMonth.of(2026, 9),
        effectiveStart: LocalDate? = requestedMonth.atDay(1),
        effectiveEnd: LocalDate? = snapshotToday
    ): TimelineRangeState {
        val model = if (days.isEmpty()) null else TimelineReadModel(days)
        return TimelineRangeState(
            requestedMonth = requestedMonth,
            effectiveStartDate = effectiveStart,
            effectiveEndDate = effectiveEnd,
            selectedDate = selectedDate,
            today = snapshotToday,
            displayZone = displayZone,
            phase = phase,
            timelineReadModel = model,
            selectedDay = days.firstOrNull { it.date == selectedDate },
            failure = null,
            generation = 1
        )
    }

    // ---------- ordering (UI9/UI10) ----------

    @Test
    fun `UI9 sections are rendered newest date first`() {
        val days = daysFrom(
            LocalDate.of(2026, 9, 14) to listOf(unrecordedOn(LocalDate.of(2026, 9, 14), 91L)),
            LocalDate.of(2026, 9, 15) to listOf(unrecordedOn(LocalDate.of(2026, 9, 15), 92L)),
            LocalDate.of(2026, 9, 16) to listOf(unrecordedOn(LocalDate.of(2026, 9, 16), 93L))
        )
        val model = TimelinePresentation.present(state(days = days), is24Hour = true, locale = locale)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 14)),
            model.sections.map { it.date }
        )
    }

    @Test
    fun `UI10 rows inside a section keep canonical ascending order`() {
        val date = LocalDate.of(2026, 9, 10)
        val morning = unrecordedEntry(
            occurrence = testOccurrence(slotId = 11L, date = date, time = LocalTime.of(8, 0))
        )
        val noon = matchedEntry(
            occurrence = testOccurrence(slotId = 12L, date = date, time = LocalTime.of(12, 0)),
            event = testEvent(id = 12L, occurredAt = date.atTime(12, 5).toInstant(utc))
        )
        val afternoon = unrecordedEntry(
            occurrence = testOccurrence(slotId = 13L, date = date, time = LocalTime.of(16, 0))
        )
        val days = daysFrom(date to listOf(afternoon, morning, noon))

        val model = TimelinePresentation.present(state(days = days), is24Hour = true, locale = locale)
        val section = model.sections.single()

        val expected = listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0)).map { time ->
            HistoryFormatting.timeText(date.atTime(time).toInstant(utc), utc, true)
        }
        assertEquals(expected, section.rows.map { row ->
            when (row) {
                is TimelinePresentation.RowUi.Matched -> row.scheduleTimeText
                is TimelinePresentation.RowUi.Unrecorded -> row.scheduleTimeText
                is TimelinePresentation.RowUi.Unmatched -> row.recordedTimeText
            }
        })
        assertTrue(section.rows[1] is TimelinePresentation.RowUi.Matched)
    }

    // ---------- selection focus vs filtering (UI12) ----------

    @Test
    fun `UI12 EMPTY_DAY keeps every non-empty month section visible`() {
        val days = daysFrom(
            LocalDate.of(2026, 9, 15) to listOf(unrecordedOn(LocalDate.of(2026, 9, 15), 94L)),
            LocalDate.of(2026, 9, 16) to listOf(unrecordedOn(LocalDate.of(2026, 9, 16), 95L))
        )
        val model = TimelinePresentation.present(
            state(phase = TimelineRangePhase.EMPTY_DAY, days = days, selectedDate = LocalDate.of(2026, 9, 10)),
            is24Hour = true,
            locale = locale
        )
        assertEquals(TimelineRangePhase.EMPTY_DAY, model.phase)
        assertEquals(2, model.sections.size)
        assertNull("no section may be synthesized for the empty selection", model.sections.firstOrNull { it.date == LocalDate.of(2026, 9, 10) })
    }

    @Test
    fun `the strip renders the continuous window around the effective range in ascending order`() {
        val model = TimelinePresentation.present(
            state(selectedDate = LocalDate.of(2026, 9, 10)),
            is24Hour = true,
            locale = locale
        )
        assertEquals(LocalDate.of(2026, 8, 25), model.dayCells.first().date)
        assertEquals(LocalDate.of(2026, 9, 23), model.dayCells.last().date)
        assertEquals(30, model.dayCells.size)
        assertEquals(listOf(LocalDate.of(2026, 9, 10)), model.dayCells.filter { it.isSelected }.map { it.date })
        assertEquals(1, model.dayCells.count { it.isToday })
        assertTrue(
            "in-month, non-future cells stay selectable",
            model.dayCells.filter { it.isInRequestedMonth && !it.date.isAfter(today) }
                .all { it.enabled }
        )
        assertTrue(
            "future cells stay non-selectable",
            model.dayCells.filter { it.date.isAfter(today) }.none { it.enabled }
        )
        assertTrue(
            "adjacent-month cells are flagged for subdued styling",
            model.dayCells.filter { YearMonth.from(it.date) != YearMonth.of(2026, 9) }
                .all { !it.isInRequestedMonth }
        )
    }

    @Test
    fun `a month switch keeps the first day inside the strip with preceding dates to its left`() {
        val model = TimelinePresentation.present(
            state(
                selectedDate = LocalDate.of(2026, 8, 1),
                requestedMonth = YearMonth.of(2026, 8),
                effectiveStart = LocalDate.of(2026, 8, 1),
                effectiveEnd = LocalDate.of(2026, 8, 31)
            ),
            is24Hour = true,
            locale = locale
        )
        assertEquals(LocalDate.of(2026, 7, 25), model.dayCells.first().date)
        assertEquals(LocalDate.of(2026, 9, 7), model.dayCells.last().date)
        val firstOfMonthIndex = model.dayCells.indexOfFirst { it.date == LocalDate.of(2026, 8, 1) }
        assertTrue("the first day of the month must not be the left-most item", firstOfMonthIndex > 0)
        assertTrue(
            "adjacent-month selectable dates stay enabled",
            model.dayCells.filter { it.date.isBefore(LocalDate.of(2026, 8, 1)) }.all { it.enabled }
        )
    }

    @Test
    fun `adjacent-month selection resolves to a month step`() {
        val start = LocalDate.of(2026, 8, 1)
        val end = LocalDate.of(2026, 8, 31)
        assertEquals(
            TimelinePresentation.MonthStep.PREVIOUS,
            TimelinePresentation.monthStepFor(LocalDate.of(2026, 7, 30), start, end)
        )
        assertEquals(
            TimelinePresentation.MonthStep.NEXT,
            TimelinePresentation.monthStepFor(LocalDate.of(2026, 9, 3), start, end)
        )
        assertEquals(
            TimelinePresentation.MonthStep.NONE,
            TimelinePresentation.monthStepFor(LocalDate.of(2026, 8, 15), start, end)
        )
        assertEquals(
            TimelinePresentation.MonthStep.NONE,
            TimelinePresentation.monthStepFor(LocalDate.of(2026, 8, 15), null, null)
        )
    }

    // ---------- row truthfulness (UI13–UI15, UI19) ----------

    @Test
    fun `UI13 and UI19 MATCHED exposes two sides with per-side dose provenance`() {
        val occurrence = testOccurrence(slotId = 21L, doseAmount = 2.0)
        val event = testEvent(
            id = 21L,
            slotId = occurrence.slotId,
            occurredAt = occurrence.scheduledAt.plusSeconds(300),
            doseAmount = 3.0
        )
        val days = daysFrom(today to listOf(matchedEntry(occurrence = occurrence, event = event)))

        val model = TimelinePresentation.present(state(days = days), is24Hour = true, locale = locale)
        val row = model.sections.single().rows.single() as TimelinePresentation.RowUi.Matched

        assertEquals(HistoryFormatting.dose(2.0), row.scheduleDoseText)
        assertEquals(HistoryFormatting.dose(3.0), row.recordedDoseText)
        assertNotEquals("the schedule dose must never substitute the actual dose", row.scheduleDoseText, row.recordedDoseText)
        assertEquals(
            HistoryFormatting.timeText(occurrence.scheduledAt, utc, true),
            row.scheduleTimeText
        )
        assertEquals(HistoryFormatting.timeText(event.occurredAt, utc, true), row.recordedTimeText)
    }

    @Test
    fun `UI14 UNRECORDED renders the schedule side only`() {
        val days = daysFrom(today to listOf(unrecordedEntry()))
        val model = TimelinePresentation.present(state(days = days), is24Hour = true, locale = locale)
        assertTrue(model.sections.single().rows.single() is TimelinePresentation.RowUi.Unrecorded)
    }

    @Test
    fun `UI15 UNMATCHED renders the recorded side only`() {
        val days = daysFrom(today to listOf(unmatchedEntry()))
        val model = TimelinePresentation.present(state(days = days), is24Hour = true, locale = locale)
        assertTrue(model.sections.single().rows.single() is TimelinePresentation.RowUi.Unmatched)
    }

    // ---------- identity (UI16–UI18) ----------

    @Test
    fun `UI16 KNOWN identity uses the canonical ester resource on both sides`() {
        val occurrence = testOccurrence(slotId = 31L, medicationKey = "EV")
        val event = testEvent(id = 31L, slotId = occurrence.slotId, medicationKey = "EV")
        val days = daysFrom(today to listOf(matchedEntry(occurrence = occurrence, event = event)))

        val row = TimelinePresentation.present(state(days = days), true, locale)
            .sections.single().rows.single() as TimelinePresentation.RowUi.Matched
        assertEquals(R.string.ester_ev, row.scheduleIdentity.labelRes)
        assertEquals(R.string.ester_ev, row.recordedIdentity.labelRes)
    }

    @Test
    fun `UI17 PARTIAL identity is neutral and never guessed`() {
        val occurrence = testOccurrence(slotId = 32L, medicationKey = "MYSTERY")
        val days = daysFrom(today to listOf(unrecordedEntry(occurrence = occurrence)))

        val row = TimelinePresentation.present(state(days = days), true, locale)
            .sections.single().rows.single() as TimelinePresentation.RowUi.Unrecorded
        assertEquals(R.string.timeline_identity_partial, row.scheduleIdentity.labelRes)
    }

    @Test
    fun `UI18 UNAVAILABLE identity is neutral and never mapped to an ester`() {
        val occurrence = testOccurrence(slotId = 33L, routeKey = "ANTIANDROGEN")
        val days = daysFrom(today to listOf(unrecordedEntry(occurrence = occurrence)))

        val row = TimelinePresentation.present(state(days = days), true, locale)
            .sections.single().rows.single() as TimelinePresentation.RowUi.Unrecorded
        assertEquals(R.string.timeline_identity_unavailable, row.scheduleIdentity.labelRes)
    }

    // ---------- phase mapping (UI22–UI26) ----------

    @Test
    fun `UI22 to UI26 every phase passes through without inventing content`() {
        val phases = listOf(
            TimelineRangePhase.LOADING,
            TimelineRangePhase.EMPTY_RANGE,
            TimelineRangePhase.EMPTY_DAY,
            TimelineRangePhase.INVALID_REQUEST,
            TimelineRangePhase.NOT_LOADABLE,
            TimelineRangePhase.ERROR
        )
        phases.forEach { phase ->
            val model = TimelinePresentation.present(
                state(phase = phase, effectiveStart = null, effectiveEnd = null),
                is24Hour = true,
                locale = locale
            )
            assertEquals(phase, model.phase)
            assertTrue("no synthetic sections for $phase", model.sections.isEmpty())
        }
        val content = TimelinePresentation.present(
            state(days = daysFrom(today to listOf(unrecordedOn(today, 96L)))),
            true,
            locale
        )
        assertEquals(TimelineRangePhase.CONTENT, content.phase)
        assertEquals(1, content.sections.size)
    }

    // ---------- 12/24h and explicit display zone (UI29/UI30/UI44/UI45) ----------

    @Test
    fun `UI29 and UI30 the 12-24h preference changes notation only`() {
        val occurrence = testOccurrence(slotId = 97L, date = today)
        val days = daysFrom(today to listOf(unrecordedEntry(occurrence = occurrence)))
        val instant = occurrence.scheduledAt

        val hour12 = TimelinePresentation.present(state(days = days), is24Hour = false, locale = locale)
            .sections.single().rows.single() as TimelinePresentation.RowUi.Unrecorded
        val hour24 = TimelinePresentation.present(state(days = days), is24Hour = true, locale = locale)
            .sections.single().rows.single() as TimelinePresentation.RowUi.Unrecorded

        assertEquals(HistoryFormatting.timeText(instant, utc, false), hour12.scheduleTimeText)
        assertEquals(HistoryFormatting.timeText(instant, utc, true), hour24.scheduleTimeText)
        assertNotEquals(hour12.scheduleTimeText, hour24.scheduleTimeText)
    }

    @Test
    fun `UI44 and UI45 visible timestamps use the published display zone independent of the 12-24h mode`() {
        val occurrence = testOccurrence(slotId = 98L, date = today)
        val days = daysFrom(today to listOf(unrecordedEntry(occurrence = occurrence)))
        val instant = occurrence.scheduledAt

        val utcModel = TimelinePresentation.present(state(days = days, displayZone = utc), true, locale)
            .sections.single().rows.single() as TimelinePresentation.RowUi.Unrecorded
        val tokyoModel = TimelinePresentation.present(state(days = days, displayZone = tokyo), true, locale)
            .sections.single().rows.single() as TimelinePresentation.RowUi.Unrecorded
        val tokyo12 = TimelinePresentation.present(state(days = days, displayZone = tokyo), false, locale)
            .sections.single().rows.single() as TimelinePresentation.RowUi.Unrecorded

        assertEquals(HistoryFormatting.timeText(instant, utc, true), utcModel.scheduleTimeText)
        assertEquals(HistoryFormatting.timeText(instant, tokyo, true), tokyoModel.scheduleTimeText)
        assertEquals(
            "the 12/24h switch must not change the zone used to interpret the instant",
            HistoryFormatting.timeText(instant, tokyo, false),
            tokyo12.scheduleTimeText
        )
    }

    // ---------- snapshot-relative labels (UI51–UI53) ----------

    @Test
    fun `UI51 an unchanged snapshot keeps Today and Yesterday stable`() {
        val days = daysFrom(
            LocalDate.of(2026, 9, 15) to listOf(unrecordedOn(LocalDate.of(2026, 9, 15), 99L)),
            LocalDate.of(2026, 9, 16) to listOf(unrecordedOn(LocalDate.of(2026, 9, 16), 100L))
        )
        val model = TimelinePresentation.present(state(days = days), true, locale)
        val labels = model.sections.associate { it.date to it.label }
        assertEquals(
            TimelinePresentation.SectionLabel.Relative(R.string.timeline_today),
            labels.getValue(LocalDate.of(2026, 9, 16))
        )
        assertEquals(
            TimelinePresentation.SectionLabel.Relative(R.string.timeline_yesterday),
            labels.getValue(LocalDate.of(2026, 9, 15))
        )
    }

    @Test
    fun `UI52 a new published state today moves the relative labels`() {
        val days = daysFrom(
            LocalDate.of(2026, 9, 15) to listOf(unrecordedOn(LocalDate.of(2026, 9, 15), 101L)),
            LocalDate.of(2026, 9, 16) to listOf(unrecordedOn(LocalDate.of(2026, 9, 16), 102L))
        )
        val model = TimelinePresentation.present(
            state(days = days, snapshotToday = LocalDate.of(2026, 9, 17)),
            true,
            locale
        )
        val labels = model.sections.associate { it.date to it.label }
        assertEquals(
            TimelinePresentation.SectionLabel.Relative(R.string.timeline_yesterday),
            labels.getValue(LocalDate.of(2026, 9, 16))
        )
        assertTrue(
            "Sep 15 is neither today nor yesterday in the new snapshot",
            labels.getValue(LocalDate.of(2026, 9, 15)) is TimelinePresentation.SectionLabel.Absolute
        )
    }

    @Test
    fun `UI53 month-control enablement derives from the published state today only`() {
        val atMonthEnd = TimelinePresentation.present(
            state(snapshotToday = LocalDate.of(2026, 9, 30)),
            true,
            locale
        )
        assertTrue("the requested month is the published current month", !atMonthEnd.canGoToNextMonth)

        val crossingIntoOctober = TimelinePresentation.present(
            state(snapshotToday = LocalDate.of(2026, 10, 1)),
            true,
            locale
        )
        assertTrue("the same requested month is now historical", crossingIntoOctober.canGoToNextMonth)

        val pastMonth = TimelinePresentation.present(
            state(requestedMonth = YearMonth.of(2026, 8), snapshotToday = today),
            true,
            locale
        )
        assertTrue(pastMonth.canGoToNextMonth)
    }

    @Test
    fun `absolute section labels carry the localized date and the existing weekday vocabulary`() {
        val date = LocalDate.of(2026, 9, 10)
        val model = TimelinePresentation.present(
            state(days = daysFrom(date to listOf(unrecordedOn(date, 103L)))),
            true,
            locale
        )
        val label = model.sections.single().label as TimelinePresentation.SectionLabel.Absolute
        assertEquals(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date),
            label.dateText
        )
        assertEquals(R.string.history_weekday_thu, label.weekdayRes)
    }

    @Test
    fun `D05 full weekday accessibility vocabulary maps every day deterministically`() {
        val expected = mapOf(
            DayOfWeek.MONDAY to R.string.timeline_a11y_weekday_mon,
            DayOfWeek.TUESDAY to R.string.timeline_a11y_weekday_tue,
            DayOfWeek.WEDNESDAY to R.string.timeline_a11y_weekday_wed,
            DayOfWeek.THURSDAY to R.string.timeline_a11y_weekday_thu,
            DayOfWeek.FRIDAY to R.string.timeline_a11y_weekday_fri,
            DayOfWeek.SATURDAY to R.string.timeline_a11y_weekday_sat,
            DayOfWeek.SUNDAY to R.string.timeline_a11y_weekday_sun
        )
        expected.forEach { (day, res) ->
            assertEquals("full weekday mapping for $day", res, TimelinePresentation.fullWeekdayRes(day))
        }
        assertEquals(7, expected.values.toSet().size)
    }
}
