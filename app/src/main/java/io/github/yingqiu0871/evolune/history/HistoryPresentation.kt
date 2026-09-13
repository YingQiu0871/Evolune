package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Pure mapping from the loaded domain read model to History presentation models (A-03 §3).
 *
 * This type translates and labels; it never classifies. In particular it does not compare
 * `scheduledAt` with `now`, never inspects the projection's non-history (upcoming) occurrence
 * list, never re-runs the matcher, never touches a DAO/repository and never re-derives a
 * display date or its provenance. The three entry kinds it maps are exactly the three domain
 * `HistoricalEntry` subtypes.
 */
object HistoryPresentation {

    fun present(state: HistoryUiState): HistoryMonthUiModel {
        val month = state.visibleMonth
        val loaded = state.loadedMonth == month
        val day = if (loaded) state.loadedDays[state.selectedDate] else null
        val phase = when {
            state.failed -> HistoryDayPhase.ERROR
            state.loading || !loaded -> HistoryDayPhase.LOADING
            day == null || day.entries.isEmpty() -> HistoryDayPhase.EMPTY
            else -> HistoryDayPhase.CONTENT
        }
        return HistoryMonthUiModel(
            month = month,
            today = state.today,
            selectedDate = state.selectedDate,
            canGoToNextMonth = month < YearMonth.from(state.today),
            cells = cells(state, loaded),
            phase = phase,
            day = day?.let { day(it, state.displayZone) }
        )
    }

    fun day(day: HistoricalDay, displayZone: ZoneId): HistoryDayUiModel = HistoryDayUiModel(
        date = day.date,
        recordedCount = day.recordedCount,
        unrecordedCount = day.unrecordedCount,
        unmatchedActualCount = day.unmatchedActualCount,
        entries = day.entries.map { entry(it, displayZone) }
    )

    fun entry(entry: HistoricalEntry, displayZone: ZoneId): HistoryEntryUiModel = when (entry) {
        is MatchedHistoricalOccurrence -> matched(entry, displayZone)
        is UnrecordedHistoricalOccurrence -> unrecorded(entry, displayZone)
        is UnmatchedHistoricalIntake -> unmatched(entry, displayZone)
    }

    // ---------- cells ----------

    private fun cells(state: HistoryUiState, loaded: Boolean): List<HistoryCalendarCellUiModel> {
        val month = state.visibleMonth
        // Week starts on Monday, matching the weekday header; leading blanks keep the grid aligned.
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val cells = ArrayList<HistoryCalendarCellUiModel>(leadingBlanks + month.lengthOfMonth())
        repeat(leadingBlanks) { cells += HistoryCalendarCellUiModel(date = null) }
        for (dayOfMonth in 1..month.lengthOfMonth()) {
            val date = month.atDay(dayOfMonth)
            val loadedDay = if (loaded) state.loadedDays[date] else null
            cells += HistoryCalendarCellUiModel(
                date = date,
                isToday = date == state.today,
                isSelected = date == state.selectedDate,
                // Interaction rule only: a calendar day after today cannot be opened. This is
                // not history filtering — the domain already excludes future occurrences.
                isEnabled = !date.isAfter(state.today),
                hasRecorded = (loadedDay?.recordedCount ?: 0) > 0,
                hasUnrecorded = (loadedDay?.unrecordedCount ?: 0) > 0,
                hasUnmatchedActual = (loadedDay?.unmatchedActualCount ?: 0) > 0
            )
        }
        return cells
    }

    // ---------- entries ----------

    private fun matched(
        entry: MatchedHistoricalOccurrence,
        displayZone: ZoneId
    ): HistoryEntryUiModel {
        val matchKey = entry.occurrence.presentation.matchKey
        return HistoryEntryUiModel(
            key = entry.sortKey,
            kind = HistoryEntryKind.MATCHED,
            statusLabelRes = R.string.history_status_recorded,
            planName = entry.occurrence.presentation.planName,
            routeLabelRes = routeLabelRes(matchKey.routeKey),
            routeFallback = matchKey.routeKey,
            medicationLabelRes = medicationLabelRes(matchKey.routeKey, matchKey.medicationKey),
            medicationFallback = matchKey.medicationKey,
            doseAmount = matchKey.doseAmount,
            // The actual intake is the authoritative instant; it is never replaced by the
            // scheduled time, and it is rendered in the zone the event itself persisted.
            actualTime = HistoryActualTimeUiModel(
                instant = entry.event.occurredAt,
                zone = entry.event.zoneId ?: displayZone,
                needsFullDate = entry.crossesLocalDateBoundary
            ),
            scheduleTime = HistoryScheduleTimeUiModel(
                instant = entry.occurrence.scheduledAt,
                zone = displayZone,
                needsFullDate = entry.occurrence.scheduledLocalDateTime.toLocalDate() != entry.displayDate
            ),
            noteRes = if (entry.matchProvenance == MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE) {
                null
            } else {
                R.string.history_note_legacy_context
            },
            secondaryNoteRes = dateNote(entry.displayDateProvenance),
            sourceLabelRes = null,
            isManualSource = false,
            isInferredMatch = entry.matchProvenance != MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE
        )
    }

    private fun unrecorded(
        entry: UnrecordedHistoricalOccurrence,
        displayZone: ZoneId
    ): HistoryEntryUiModel {
        val matchKey = entry.occurrence.presentation.matchKey
        return HistoryEntryUiModel(
            key = entry.sortKey,
            kind = HistoryEntryKind.UNRECORDED,
            statusLabelRes = R.string.history_status_no_recorded_intake,
            planName = entry.occurrence.presentation.planName,
            routeLabelRes = routeLabelRes(matchKey.routeKey),
            routeFallback = matchKey.routeKey,
            medicationLabelRes = medicationLabelRes(matchKey.routeKey, matchKey.medicationKey),
            medicationFallback = matchKey.medicationKey,
            doseAmount = matchKey.doseAmount,
            actualTime = null,
            scheduleTime = HistoryScheduleTimeUiModel(
                instant = entry.occurrence.scheduledAt,
                zone = displayZone,
                needsFullDate = entry.occurrence.scheduledLocalDateTime.toLocalDate() != entry.displayDate
            ),
            noteRes = R.string.history_note_no_recorded_intake,
            secondaryNoteRes = R.string.history_note_not_necessarily_missed,
            sourceLabelRes = null,
            isManualSource = false
        )
    }

    private fun unmatched(
        entry: UnmatchedHistoricalIntake,
        displayZone: ZoneId
    ): HistoryEntryUiModel {
        val matchKey = entry.event.matchKey
        return HistoryEntryUiModel(
            key = entry.sortKey,
            kind = HistoryEntryKind.UNMATCHED,
            statusLabelRes = R.string.history_status_recorded_intake,
            planName = null,
            routeLabelRes = routeLabelRes(matchKey.routeKey),
            routeFallback = matchKey.routeKey,
            medicationLabelRes = medicationLabelRes(matchKey.routeKey, matchKey.medicationKey),
            medicationFallback = matchKey.medicationKey,
            doseAmount = matchKey.doseAmount,
            actualTime = HistoryActualTimeUiModel(
                instant = entry.event.occurredAt,
                zone = entry.event.zoneId ?: displayZone,
                needsFullDate = false
            ),
            scheduleTime = null,
            noteRes = R.string.history_note_plan_unavailable,
            secondaryNoteRes = dateNote(entry.displayDateProvenance),
            sourceLabelRes = sourceLabelRes(entry.source),
            isManualSource = entry.isManualIntake
        )
    }

    private fun dateNote(provenance: HistoricalDisplayDateProvenance): Int? =
        if (provenance == HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED) {
            R.string.history_note_current_zone_date
        } else {
            null
        }

    private fun routeLabelRes(routeKey: String): Int? = when (routeKey) {
        Route.INJECTION.name -> R.string.route_injection
        Route.ORAL.name -> R.string.route_oral
        Route.SUBLINGUAL.name -> R.string.route_sublingual
        Route.GEL.name -> R.string.route_gel
        Route.PATCH_APPLY.name -> R.string.route_patch_apply
        Route.PATCH_REMOVE.name -> R.string.route_patch_remove
        Route.ANTIANDROGEN.name -> R.string.route_antiandrogen
        else -> null
    }

    /**
     * The match key carries the ester slot of the current plan. For an anti-androgen route the
     * authoritative drug is not part of the model, so no ester name may be claimed there.
     */
    private fun medicationLabelRes(routeKey: String, medicationKey: String): Int? {
        if (routeKey == Route.ANTIANDROGEN.name) return null
        return when (medicationKey) {
            Ester.E2.name -> R.string.ester_e2
            Ester.EB.name -> R.string.ester_eb
            Ester.EV.name -> R.string.ester_ev
            Ester.EC.name -> R.string.ester_ec
            Ester.EN.name -> R.string.ester_en
            else -> null
        }
    }

    /** Only `MANUAL` may be presented as manual; every other origin keeps its own label. */
    private fun sourceLabelRes(source: MedicationIntakeSource): Int = when (source) {
        MedicationIntakeSource.MANUAL -> R.string.history_source_manual
        MedicationIntakeSource.REMINDER -> R.string.history_source_reminder
        MedicationIntakeSource.WEAR -> R.string.history_source_wear
        MedicationIntakeSource.WIDGET -> R.string.history_source_widget
        MedicationIntakeSource.JSON_V1 -> R.string.history_source_json
        MedicationIntakeSource.LEGACY -> R.string.history_source_legacy
    }
}

/** Convenience for the screen: the selected day of an already presented month. */
fun HistoryMonthUiModel.selectedDayOrNull(): HistoryDayUiModel? = day

/** Convenience for tests and previews: presentation of one day of a loaded state. */
internal fun HistoryUiState.presentedDay(date: LocalDate = selectedDate): HistoryDayUiModel? =
    loadedDays[date]?.let { HistoryPresentation.day(it, displayZone) }
