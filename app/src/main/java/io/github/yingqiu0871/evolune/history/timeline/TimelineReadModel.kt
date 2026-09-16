package io.github.yingqiu0871.evolune.history.timeline

import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceId
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * V17-D-01 §4 — derived, NON-PERSISTENT typed Timeline row identity.
 *
 * `Occurrence` is used by both MATCHED and UNRECORDED_SCHEDULE rows for the same occurrence, so a
 * family change (unrecorded -> matched, matched -> unrecorded) keeps the occurrence-backed
 * identity. A genuine unmatched -> matched reconciliation may change `Event` into `Occurrence`
 * because the authoritative projection family changed. No persistent ID store exists.
 */
sealed interface TimelineRowId {
    data class Occurrence(val occurrenceId: MedicationOccurrenceId) : TimelineRowId
    data class Event(val eventId: UUID) : TimelineRowId
}

/** The three frozen D-01 row families. */
enum class TimelineRowKind { MATCHED, UNRECORDED_SCHEDULE, UNMATCHED_INTAKE }

/**
 * Schedule-context side of an occurrence-backed row (V17-D-01 §5/§12).
 *
 * Represents the CURRENT schedule context materialized for this historical occurrence — never a
 * historical prescription snapshot, historical prescribed dose or plan-at-that-time (Phase A has
 * no plan snapshots).
 *
 * [planId]/[slotPosition]/[slotId] are carried as ORDERING INPUTS ONLY (the verified
 * `OCCURRENCE_ORDER` field sequence for same-instant occurrence-backed rows), not as rendering
 * facts. `planName` is deliberately absent: it is never medication-identity evidence.
 */
data class TimelineScheduleContext(
    val occurrenceId: MedicationOccurrenceId,
    val scheduledAt: Instant,
    val matchKey: MedicationMatchKey,
    val identity: MedicationIdentity,
    internal val planId: UUID,
    internal val slotPosition: Int,
    internal val slotId: UUID
)

/**
 * Recorded-intake side of a recorded-backed row (V17-D-01 §5/§6). This is the authoritative
 * actual medication fact; a later UI describing "what was taken" must use this side, never the
 * schedule-context side.
 */
data class TimelineRecordedIntake(
    val eventId: UUID,
    val occurredAt: Instant,
    val matchKey: MedicationMatchKey,
    val identity: MedicationIdentity
)

/**
 * One Timeline row. Raw timestamps only: no timing delta, no early/late/on-time, no
 * adherence/compliance interpretation exists anywhere in this model (V17-D-01 §10/§20).
 */
data class TimelineRow(
    val rowId: TimelineRowId,
    val rowKind: TimelineRowKind,
    /** Preserved exactly from `HistoricalEntry.displayDate`; never re-derived here. */
    val displayDate: LocalDate,
    /** The row's effective sort instant (scheduledAt or occurredAt). */
    val sortInstant: Instant,
    /** Present for MATCHED and UNRECORDED_SCHEDULE. */
    val scheduleContext: TimelineScheduleContext?,
    /** Present for MATCHED and UNMATCHED_INTAKE. */
    val recordedIntake: TimelineRecordedIntake?
)

/** One non-empty date section; dates are ascending and deterministic. */
data class TimelineDay(
    val date: LocalDate,
    val rows: List<TimelineRow>
)

/** The read-only Timeline projection result (V17-D-01 §14). */
data class TimelineReadModel(
    val days: List<TimelineDay>
)
