package io.github.yingqiu0871.evolune.history.timeline

import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityClassifier

/**
 * V17-D-01 — Timeline read-model / row projection.
 *
 * Pure derived projection over an authoritative [HistoricalRange] (the same Phase-A projection
 * History consumes). It performs no reads of its own: no repository/DAO/Room, no
 * HistoryReadService/HistoryRangeSource invocation, no matcher, no occurrence generation, no
 * clock and no Android dependency. Identical input always produces an identical model.
 *
 * Row families (frozen): MATCHED / UNRECORDED_SCHEDULE / UNMATCHED_INTAKE. `futureOccurrences`
 * are not part of the projection type and can never become rows.
 *
 * Date attribution is preserved exactly from each entry's `displayDate`; the builder never
 * recomputes dates from a device/event/occurrence zone.
 */
object TimelineProjectionBuilder {

    fun build(range: HistoricalRange): TimelineReadModel {
        val rows = range.days.flatMap { day -> day.entries.map(::toRow) }
        val orderedRows = rows.sortedWith(TIMELINE_ROW_ORDER)
        val days = orderedRows
            .groupBy { it.displayDate }
            .toSortedMap()
            .map { (date, dayRows) -> TimelineDay(date = date, rows = dayRows) }
        return TimelineReadModel(days = days)
    }

    private fun toRow(entry: HistoricalEntry): TimelineRow = when (entry) {
        is MatchedHistoricalOccurrence -> TimelineRow(
            rowId = TimelineRowId.Occurrence(entry.occurrence.id),
            rowKind = TimelineRowKind.MATCHED,
            displayDate = entry.displayDate,
            sortInstant = entry.occurrence.scheduledAt,
            scheduleContext = scheduleContextOf(entry.occurrence),
            recordedIntake = recordedIntakeOf(entry.event)
        )

        is UnrecordedHistoricalOccurrence -> TimelineRow(
            rowId = TimelineRowId.Occurrence(entry.occurrence.id),
            rowKind = TimelineRowKind.UNRECORDED_SCHEDULE,
            displayDate = entry.displayDate,
            sortInstant = entry.occurrence.scheduledAt,
            scheduleContext = scheduleContextOf(entry.occurrence),
            recordedIntake = null
        )

        is UnmatchedHistoricalIntake -> TimelineRow(
            rowId = TimelineRowId.Event(entry.event.eventId),
            rowKind = TimelineRowKind.UNMATCHED_INTAKE,
            displayDate = entry.displayDate,
            sortInstant = entry.event.occurredAt,
            scheduleContext = null,
            recordedIntake = recordedIntakeOf(entry.event)
        )
    }

    private fun scheduleContextOf(occurrence: MedicationOccurrence): TimelineScheduleContext =
        TimelineScheduleContext(
            occurrenceId = occurrence.id,
            scheduledAt = occurrence.scheduledAt,
            matchKey = occurrence.presentation.matchKey,
            identity = MedicationIdentityClassifier.classify(occurrence.presentation.matchKey),
            planId = occurrence.planId,
            slotPosition = occurrence.slotPosition,
            slotId = occurrence.slotId
        )

    private fun recordedIntakeOf(event: RecordedMedicationEvent): TimelineRecordedIntake =
        TimelineRecordedIntake(
            eventId = event.eventId,
            occurredAt = event.occurredAt,
            matchKey = event.matchKey,
            identity = MedicationIdentityClassifier.classify(event.matchKey)
        )
}

/**
 * Canonical Timeline order (V17-D-01 §12): `displayDate` ascending, then the row's effective
 * sort instant ascending, then a deterministic same-instant tie-break.
 *
 * Intentional, evaluated difference from History: `HistoricalEntry.sortKey` prefixes the family
 * ("0:" matched, "1:" unrecorded), so History may place MATCHED before UNRECORDED at the same
 * instant even when `OCCURRENCE_ORDER` semantics say otherwise. Timeline deliberately uses the
 * `OCCURRENCE_ORDER` field sequence for occurrence-backed same-instant rows, as frozen by D-01.
 * This is a deterministic presentation-order difference only — not a different medication fact
 * and not a second matcher.
 *
 * Same-instant rules: occurrence-backed rows first (A), occurrence-backed pairs by the
 * `OCCURRENCE_ORDER` field sequence mirrored below (B), unmatched pairs by eventId lexical order
 * (C). `HistoricalEntry.sortKey` is NOT used as the occurrence-backed comparator.
 */
internal val TIMELINE_ROW_ORDER: Comparator<TimelineRow> = Comparator { first, second ->
    val byDate = first.displayDate.compareTo(second.displayDate)
    if (byDate != 0) return@Comparator byDate

    val byInstant = first.sortInstant.compareTo(second.sortInstant)
    if (byInstant != 0) return@Comparator byInstant

    compareAtSameInstant(first, second)
}

private fun compareAtSameInstant(first: TimelineRow, second: TimelineRow): Int {
    val firstContext = first.scheduleContext
    val secondContext = second.scheduleContext
    return when {
        firstContext != null && secondContext != null ->
            compareOccurrenceBacked(firstContext, secondContext)

        firstContext != null -> -1
        secondContext != null -> 1
        else -> first.recordedIntake!!.eventId.toString()
            .compareTo(second.recordedIntake!!.eventId.toString())
    }
}

/**
 * Mirrors the current `OCCURRENCE_ORDER` semantic field sequence
 * (`scheduledAt`, `planId.toString()`, `slotPosition`, `slotId.toString()`,
 * `id.value.toString()`). `scheduledAt` is already equal here. The original comparator is
 * internal to experience-core and must not be made public merely for D-01; this mirror is
 * pinned behaviorally by the TLM18 generator-parity test.
 */
private fun compareOccurrenceBacked(
    first: TimelineScheduleContext,
    second: TimelineScheduleContext
): Int {
    val byPlan = first.planId.toString().compareTo(second.planId.toString())
    if (byPlan != 0) return byPlan

    val bySlotPosition = first.slotPosition.compareTo(second.slotPosition)
    if (bySlotPosition != 0) return bySlotPosition

    val bySlotId = first.slotId.toString().compareTo(second.slotId.toString())
    if (bySlotId != 0) return bySlotId

    return first.occurrenceId.value.toString().compareTo(second.occurrenceId.value.toString())
}
