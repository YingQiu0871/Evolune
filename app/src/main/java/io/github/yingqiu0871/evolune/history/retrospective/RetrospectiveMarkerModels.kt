package io.github.yingqiu0871.evolune.history.retrospective

import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceId
import java.time.Instant
import java.util.UUID

/**
 * V17-C-04 §3.1 — internal marker presentation model.
 *
 * Exactly two user-visible marker families exist. `provenance` serves the internal model,
 * tests and diagnostics only: no user-visible layer may derive copy, icon differences or
 * state classification from it. No delta / confidence / punctuality / early-late /
 * on-time / adherence field may be added here.
 */
sealed interface RetrospectiveMarker {
    /** The family's own inclusion instant (schedule marker: scheduledAt; intake marker: occurredAt). */
    val instant: Instant
}

/** A current-schedule occurrence supplied by the Read 3 window-history projection. */
data class ScheduleContextMarker(
    val occurrenceId: MedicationOccurrenceId,
    val scheduledAt: Instant,
    /** INTERNAL ONLY — never rendered. */
    val provenance: ScheduleMarkerProvenance
) : RetrospectiveMarker {
    override val instant: Instant get() = scheduledAt
}

/** An authoritative accepted recorded intake fact supplied by the Read 2 all-history projection. */
data class RecordedIntakeMarker(
    val eventId: UUID,
    val occurredAt: Instant,
    /** INTERNAL ONLY — never rendered. */
    val provenance: IntakeMarkerProvenance
) : RetrospectiveMarker {
    override val instant: Instant get() = occurredAt
}

enum class ScheduleMarkerProvenance { MATCHED_OCCURRENCE, UNRECORDED_OCCURRENCE }

enum class IntakeMarkerProvenance { MATCHED_INTAKE, UNMATCHED_INTAKE }
