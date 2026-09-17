package io.github.yingqiu0871.evolune.ui.screens.timeline

import androidx.annotation.StringRes
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentity
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityKey
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityStatus
import io.github.yingqiu0871.evolune.history.HistoryFormatting
import io.github.yingqiu0871.evolune.history.timeline.TimelineDay
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangePhase
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeState
import io.github.yingqiu0871.evolune.history.timeline.TimelineRow
import io.github.yingqiu0871.evolune.history.timeline.TimelineRowKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * V17-D-04 §13/§16/§23 — the single presentation path from the published [TimelineRangeState] to
 * the Timeline surface.
 *
 * The mapper only selects, orders and labels what the closed D-01/D-03 model already states:
 * - sections are the D-01 days reversed for display (newest first); the rows inside a section stay
 *   in canonical ascending order and are never re-sorted or filtered;
 * - every timestamp belonging to the published snapshot is formatted with the state's own
 *   `displayZone`; the mapper never obtains a zone, a clock or a "today" of its own;
 * - relative section labels derive from `state.today` (the published snapshot reference date), not
 *   from any live clock read;
 * - MATCHED keeps two truthful sides with per-side identity and dose provenance; UNRECORDED and
 *   UNMATCHED stay one-sided exactly as frozen.
 */
object TimelinePresentation {

    /**
     * How many adjacent-month days the strip renders on both sides of the effective range. The
     * continuous window lets the first day of the visible month sit inside the viewport with the
     * preceding (adjacent-month) dates still visible to its left (v1.7.1 UI hotfix).
     */
    private const val DAY_STRIP_ADJACENT_DAYS = 7L

    /** The month-step a day selection outside the effective range requires (adjacent dates). */
    enum class MonthStep { NONE, PREVIOUS, NEXT }

    /**
     * Adjacent-month selection routing (v1.7.1 UI hotfix): a date before the effective start
     * belongs to the previous month, a date after the effective end to the next month. Dates
     * inside the range (or while no range is published) perform no month step.
     */
    fun monthStepFor(
        date: LocalDate,
        effectiveStartDate: LocalDate?,
        effectiveEndDate: LocalDate?
    ): MonthStep = when {
        effectiveStartDate == null || effectiveEndDate == null -> MonthStep.NONE
        date.isBefore(effectiveStartDate) -> MonthStep.PREVIOUS
        date.isAfter(effectiveEndDate) -> MonthStep.NEXT
        else -> MonthStep.NONE
    }

    /**
     * A day-strip cell. The strip renders the effective range plus the surrounding
     * [DAY_STRIP_ADJACENT_DAYS] window in ascending order; cells outside the requested month stay
     * distinguishable through [isInRequestedMonth] and future cells stay non-selectable.
     */
    data class DayCell(
        val date: LocalDate,
        val isSelected: Boolean,
        val isToday: Boolean,
        val enabled: Boolean,
        val isInRequestedMonth: Boolean
    )

    /** Section heading: a relative resource (Today/Yesterday) or an absolute date + weekday. */
    sealed interface SectionLabel {
        data class Relative(@StringRes val labelRes: Int) : SectionLabel

        data class Absolute(val dateText: String, @StringRes val weekdayRes: Int) : SectionLabel
    }

    /** Resolved medication identity label (KNOWN uses the canonical ester vocabulary). */
    data class IdentityUi(@StringRes val labelRes: Int)

    /** One truthful row: MATCHED carries both sides, the others exactly one. */
    sealed interface RowUi {
        data class Matched(
            val scheduleTimeText: String,
            val scheduleIdentity: IdentityUi,
            val scheduleDoseText: String,
            val recordedTimeText: String,
            val recordedIdentity: IdentityUi,
            val recordedDoseText: String
        ) : RowUi

        data class Unrecorded(
            val scheduleTimeText: String,
            val scheduleIdentity: IdentityUi,
            val scheduleDoseText: String
        ) : RowUi

        data class Unmatched(
            val recordedTimeText: String,
            val recordedIdentity: IdentityUi,
            val recordedDoseText: String
        ) : RowUi
    }

    data class Section(
        val date: LocalDate,
        val label: SectionLabel,
        val rows: List<RowUi>
    )

    /**
     * Everything the surface renders for one published state. Sections are the complete non-empty
     * month (never filtered by the selection); the selection is expressed by [selectedDate] and the
     * day-cell/highlight flags only.
     */
    data class Model(
        val phase: TimelineRangePhase,
        val requestedMonth: YearMonth,
        val selectedDate: LocalDate,
        val today: LocalDate,
        val effectiveStartDate: LocalDate?,
        val effectiveEndDate: LocalDate?,
        val canGoToNextMonth: Boolean,
        val dayCells: List<DayCell>,
        val sections: List<Section>
    )

    fun present(
        state: TimelineRangeState,
        is24Hour: Boolean,
        locale: Locale = Locale.getDefault()
    ): Model {
        val dayCells = if (state.effectiveStartDate != null && state.effectiveEndDate != null) {
            dayCells(state, state.effectiveStartDate, state.effectiveEndDate)
        } else {
            emptyList()
        }
        return Model(
            phase = state.phase,
            requestedMonth = state.requestedMonth,
            selectedDate = state.selectedDate,
            today = state.today,
            effectiveStartDate = state.effectiveStartDate,
            effectiveEndDate = state.effectiveEndDate,
            canGoToNextMonth = state.requestedMonth < YearMonth.from(state.today),
            dayCells = dayCells,
            sections = sections(state, is24Hour, locale)
        )
    }

    /**
     * The strip renders a continuous window: the effective range extended by
     * [DAY_STRIP_ADJACENT_DAYS] days on both sides, so adjacent-month dates stay visible (the
     * first day of the month is never left-most) and the viewport is never given empty gaps.
     */
    private fun dayCells(
        state: TimelineRangeState,
        start: LocalDate,
        end: LocalDate
    ): List<DayCell> {
        val cells = mutableListOf<DayCell>()
        var date = start.minusDays(DAY_STRIP_ADJACENT_DAYS)
        val lastDate = end.plusDays(DAY_STRIP_ADJACENT_DAYS)
        while (!date.isAfter(lastDate)) {
            cells += DayCell(
                date = date,
                isSelected = date == state.selectedDate,
                isToday = date == state.today,
                enabled = !date.isAfter(state.today),
                isInRequestedMonth = YearMonth.from(date) == state.requestedMonth
            )
            date = date.plusDays(1)
        }
        return cells
    }

    /** D-01 days, newest first (presentation only; `TimelineDay.rows` order is untouched). */
    private fun sections(
        state: TimelineRangeState,
        is24Hour: Boolean,
        locale: Locale
    ): List<Section> {
        val days: List<TimelineDay> = state.timelineReadModel?.days ?: return emptyList()
        return days.asReversed().map { day ->
            Section(
                date = day.date,
                label = sectionLabel(day.date, state.today, locale),
                rows = day.rows.map { rowUi(it, state.displayZone, is24Hour) }
            )
        }
    }

    private fun sectionLabel(date: LocalDate, today: LocalDate, locale: Locale): SectionLabel =
        when (date) {
            today -> SectionLabel.Relative(R.string.timeline_today)
            today.minusDays(1) -> SectionLabel.Relative(R.string.timeline_yesterday)
            else -> SectionLabel.Absolute(
                dateText = DATE_FORMATTER.withLocale(locale).format(date),
                weekdayRes = weekdayRes(date.dayOfWeek)
            )
        }

    private fun rowUi(row: TimelineRow, zone: ZoneId, is24Hour: Boolean): RowUi = when (row.rowKind) {
        TimelineRowKind.MATCHED -> {
            val schedule = requireNotNull(row.scheduleContext)
            val recorded = requireNotNull(row.recordedIntake)
            RowUi.Matched(
                scheduleTimeText = HistoryFormatting.timeText(schedule.scheduledAt, zone, is24Hour),
                scheduleIdentity = identityUi(schedule.identity),
                scheduleDoseText = HistoryFormatting.dose(schedule.matchKey.doseAmount),
                recordedTimeText = HistoryFormatting.timeText(recorded.occurredAt, zone, is24Hour),
                recordedIdentity = identityUi(recorded.identity),
                recordedDoseText = HistoryFormatting.dose(recorded.matchKey.doseAmount)
            )
        }

        TimelineRowKind.UNRECORDED_SCHEDULE -> {
            val schedule = requireNotNull(row.scheduleContext)
            RowUi.Unrecorded(
                scheduleTimeText = HistoryFormatting.timeText(schedule.scheduledAt, zone, is24Hour),
                scheduleIdentity = identityUi(schedule.identity),
                scheduleDoseText = HistoryFormatting.dose(schedule.matchKey.doseAmount)
            )
        }

        TimelineRowKind.UNMATCHED_INTAKE -> {
            val recorded = requireNotNull(row.recordedIntake)
            RowUi.Unmatched(
                recordedTimeText = HistoryFormatting.timeText(recorded.occurredAt, zone, is24Hour),
                recordedIdentity = identityUi(recorded.identity),
                recordedDoseText = HistoryFormatting.dose(recorded.matchKey.doseAmount)
            )
        }
    }

    private fun identityUi(identity: MedicationIdentity): IdentityUi = when (identity.status) {
        MedicationIdentityStatus.KNOWN -> IdentityUi(medicationLabelRes(requireNotNull(identity.key)))
        MedicationIdentityStatus.PARTIAL -> IdentityUi(R.string.timeline_identity_partial)
        MedicationIdentityStatus.UNAVAILABLE -> IdentityUi(R.string.timeline_identity_unavailable)
    }

    /** Reuses the existing ester string resources, exactly like the Insights presenter. */
    private fun medicationLabelRes(key: MedicationIdentityKey): Int = when (key) {
        MedicationIdentityKey.E2 -> R.string.ester_e2
        MedicationIdentityKey.EB -> R.string.ester_eb
        MedicationIdentityKey.EV -> R.string.ester_ev
        MedicationIdentityKey.EC -> R.string.ester_ec
        MedicationIdentityKey.EN -> R.string.ester_en
    }

    /** Shared weekday vocabulary for the day strip (existing History resource set). */
    fun weekdayRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
        DayOfWeek.MONDAY -> R.string.history_weekday_mon
        DayOfWeek.TUESDAY -> R.string.history_weekday_tue
        DayOfWeek.WEDNESDAY -> R.string.history_weekday_wed
        DayOfWeek.THURSDAY -> R.string.history_weekday_thu
        DayOfWeek.FRIDAY -> R.string.history_weekday_fri
        DayOfWeek.SATURDAY -> R.string.history_weekday_sat
        DayOfWeek.SUNDAY -> R.string.history_weekday_sun
    }

    /**
     * V17-D-05 §6/§9/§35 — full weekday vocabulary for accessibility speech only. The visible
     * day strip and section headers keep the short form; spoken phrases use these full names.
     */
    fun fullWeekdayRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
        DayOfWeek.MONDAY -> R.string.timeline_a11y_weekday_mon
        DayOfWeek.TUESDAY -> R.string.timeline_a11y_weekday_tue
        DayOfWeek.WEDNESDAY -> R.string.timeline_a11y_weekday_wed
        DayOfWeek.THURSDAY -> R.string.timeline_a11y_weekday_thu
        DayOfWeek.FRIDAY -> R.string.timeline_a11y_weekday_fri
        DayOfWeek.SATURDAY -> R.string.timeline_a11y_weekday_sat
        DayOfWeek.SUNDAY -> R.string.timeline_a11y_weekday_sun
    }

    private val DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
}
