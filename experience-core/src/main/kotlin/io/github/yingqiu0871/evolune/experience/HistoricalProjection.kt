package io.github.yingqiu0871.evolune.experience

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * How the display date of a historical entry was obtained.
 *
 * Decision G (v1.7-A gate): a display date is never allowed to pretend to be the
 * original local calendar date of an event when that information does not exist.
 */
enum class HistoricalDisplayDateProvenance {
    /** Bound occurrence: the intended local date of the occurrence the event belongs to. */
    INTENDED_LOCAL_DATE,

    /** The event itself persisted `localDate` (and usually `zoneId`) when it was recorded. */
    PERSISTED_RECORDING_DATE,

    /**
     * True legacy orphan (no `localDate`, no `zoneId`, no reliable occurrence binding):
     * the date is derived from the absolute event instant in the *current* display
     * timezone. It is a presentation artifact, not the original local date, and must
     * not be used as high-confidence historical adherence input.
     */
    CURRENT_DISPLAY_TIMEZONE_DERIVED
}

/**
 * What the scheduled time of a matched occurrence actually represents.
 *
 * Decision A: the repository has no plan/slot version history, so a regenerated
 * scheduled time is always the *current* schedule context. It must never be
 * presented as a historical planned snapshot.
 */
enum class HistoricalScheduleTimeContext {
    CURRENT_SCHEDULE_CONTEXT
}

/** A single entry of the shared historical projection. */
sealed interface HistoricalEntry {
    val displayDate: LocalDate
    val displayDateProvenance: HistoricalDisplayDateProvenance

    /** Deterministic ordering key inside one display date. */
    val sortInstant: Instant

    /** Stable tie-breaker so equal instants still order deterministically. */
    val sortKey: String

    /**
     * True only when [displayDate] is either the occurrence's intended local date or
     * the event's own persisted date. False for legacy-orphan dates derived from the
     * current display timezone.
     */
    val isOriginalLocalDate: Boolean
        get() = displayDateProvenance != HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED
}

/** An authoritative occurrence matched to an authoritative recorded event. */
data class MatchedHistoricalOccurrence(
    val occurrence: MedicationOccurrence,
    val event: RecordedMedicationEvent,
    /** Why this event is considered part of this occurrence (never re-derived by consumers). */
    val matchProvenance: MedicationMatchProvenance,
    val status: MedicationOccurrenceStatus,
    val actionAvailability: MedicationActionAvailability,
    /**
     * Always [HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT]: the occurrence's
     * `scheduledAt` is regenerated from the *current* plan.
     */
    val scheduleTimeContext: HistoricalScheduleTimeContext =
        HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
    /**
     * True when the event's own local date (persisted, or derived from its instant in
     * the display zone for legacy rows) differs from the occurrence's intended local
     * date. Such a match is an inferred cross-date match, never an exact one.
     */
    val crossesLocalDateBoundary: Boolean,
    override val displayDate: LocalDate,
    override val displayDateProvenance: HistoricalDisplayDateProvenance
) : HistoricalEntry {
    override val sortInstant: Instant get() = occurrence.scheduledAt
    override val sortKey: String get() = "0:" + occurrence.id.value + ":" + event.eventId
}

/**
 * An occurrence for which no authoritative recorded intake was matched.
 *
 * Wording is deliberate: this means only "no recorded intake was found in the
 * currently available authoritative event data". It must never be rendered or
 * modelled as `Missed`, `Skipped`, non-adherent or forgotten medication, and it
 * must never be used to compute strict historical adherence.
 *
 * The scheduled time comes from the *current* plan, so it carries
 * [HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT] just like a matched
 * occurrence: the plan/slot may have been edited after the fact.
 */
data class UnrecordedHistoricalOccurrence(
    val occurrence: MedicationOccurrence,
    val status: MedicationOccurrenceStatus,
    val actionAvailability: MedicationActionAvailability,
    val scheduleTimeContext: HistoricalScheduleTimeContext =
        HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
    override val displayDate: LocalDate,
    override val displayDateProvenance: HistoricalDisplayDateProvenance =
        HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE
) : HistoricalEntry {
    override val sortInstant: Instant get() = occurrence.scheduledAt
    override val sortKey: String get() = "1:" + occurrence.id.value
}

/**
 * An authoritative actual intake that no occurrence claimed.
 *
 * It exists so that a recorded fact can never disappear from history merely for
 * lacking a matching occurrence (deleted plan, edited slot identity, legacy row,
 * manual entry outside any schedule, ...).
 */
data class UnmatchedHistoricalIntake(
    val event: RecordedMedicationEvent,
    val source: MedicationIntakeSource,
    override val displayDate: LocalDate,
    override val displayDateProvenance: HistoricalDisplayDateProvenance
) : HistoricalEntry {
    /** Only `MANUAL` may be presented as a manual intake. */
    val isManualIntake: Boolean get() = source == MedicationIntakeSource.MANUAL

    override val sortInstant: Instant get() = event.occurredAt
    override val sortKey: String get() = "2:" + event.eventId
}

/** Shared historical projection consumed by History / Timeline / Insights / retrospective PK adapters. */
data class HistoricalProjection(
    val entries: List<HistoricalEntry>
) {
    val matchedOccurrences: List<MatchedHistoricalOccurrence>
        get() = entries.filterIsInstance<MatchedHistoricalOccurrence>()

    val unrecordedOccurrences: List<UnrecordedHistoricalOccurrence>
        get() = entries.filterIsInstance<UnrecordedHistoricalOccurrence>()

    val unmatchedIntakes: List<UnmatchedHistoricalIntake>
        get() = entries.filterIsInstance<UnmatchedHistoricalIntake>()

    /** All entries whose display date equals [date], in deterministic order. */
    fun entriesOn(date: LocalDate): List<HistoricalEntry> = entries.filter { it.displayDate == date }
}

/**
 * Builds the shared historical projection.
 *
 * Implemented strictly on top of [MedicationOccurrencePresentation.deriveWithMatches],
 * i.e. on top of the single existing four-phase matcher. This type contributes
 * provenance, unmatched-intake retention, schedule-context labelling and display-date
 * attribution only; it never re-implements occurrence matching.
 */
object HistoricalProjectionBuilder {
    fun derive(
        occurrences: List<MedicationOccurrence>,
        events: List<RecordedMedicationEvent>,
        now: Instant,
        displayZone: ZoneId,
        policy: MedicationOccurrencePolicy = MedicationOccurrencePolicy()
    ): HistoricalProjection {
        val presentation = MedicationOccurrencePresentation.deriveWithMatches(
            occurrences = occurrences,
            recordedEvents = events,
            now = now,
            policy = policy
        )

        val entries = mutableListOf<HistoricalEntry>()

        presentation.items.forEach { item ->
            val occurrenceDate = item.occurrence.scheduledLocalDateTime.toLocalDate()
            val match = presentation.matches[item.occurrence.id]
            if (match == null) {
                entries += UnrecordedHistoricalOccurrence(
                    occurrence = item.occurrence,
                    status = item.status,
                    actionAvailability = item.actionAvailability,
                    displayDate = occurrenceDate
                )
                return@forEach
            }
            val eventLocalDate = match.event.localDate
                ?: match.event.occurredAt.atZone(displayZone).toLocalDate()
            entries += MatchedHistoricalOccurrence(
                occurrence = item.occurrence,
                event = match.event,
                matchProvenance = match.provenance,
                status = item.status,
                actionAvailability = item.actionAvailability,
                crossesLocalDateBoundary = eventLocalDate != occurrenceDate,
                displayDate = occurrenceDate,
                displayDateProvenance = HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE
            )
        }

        val matchedEventIds = presentation.matches.values
            .mapTo(mutableSetOf<UUID>()) { it.event.eventId }

        events
            .filterNot { it.eventId in matchedEventIds }
            .forEach { event ->
                val persistedLocalDate = event.localDate
                entries += UnmatchedHistoricalIntake(
                    event = event,
                    source = event.source,
                    displayDate = persistedLocalDate
                        ?: event.occurredAt.atZone(displayZone).toLocalDate(),
                    displayDateProvenance = if (persistedLocalDate != null) {
                        HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE
                    } else {
                        HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED
                    }
                )
            }

        val ordered = entries.sortedWith(HISTORICAL_ENTRY_ORDER)
        verifyCompleteness(
            occurrenceIds = occurrences.map { it.id },
            eventIds = events.map { it.eventId },
            entries = ordered
        )
        return HistoricalProjection(ordered)
    }

    /**
     * Projection completeness invariant (A-02):
     * every generated occurrence appears exactly once as a matched or unrecorded
     * entry, and every authoritative event appears exactly once either as a matched
     * occurrence's actual event or as an unmatched intake. Nothing may vanish and
     * nothing may be consumed twice. Violations fail fast instead of degrading the
     * historical view silently.
     */
    private fun verifyCompleteness(
        occurrenceIds: List<MedicationOccurrenceId>,
        eventIds: List<UUID>,
        entries: List<HistoricalEntry>
    ) {
        val occurrenceRefs = entries.mapNotNull { entry ->
            when (entry) {
                is MatchedHistoricalOccurrence -> entry.occurrence.id
                is UnrecordedHistoricalOccurrence -> entry.occurrence.id
                is UnmatchedHistoricalIntake -> null
            }
        }
        val eventRefs = entries.mapNotNull { entry ->
            when (entry) {
                is MatchedHistoricalOccurrence -> entry.event.eventId
                is UnmatchedHistoricalIntake -> entry.event.eventId
                is UnrecordedHistoricalOccurrence -> null
            }
        }

        check(occurrenceRefs.size == occurrenceIds.size) {
            "projection must contain every occurrence exactly once"
        }
        check(occurrenceRefs.toSet() == occurrenceIds.toSet() && occurrenceRefs.size == occurrenceRefs.toSet().size) {
            "projection must not drop, duplicate or invent occurrences"
        }
        check(eventRefs.size == eventIds.size) {
            "projection must contain every event exactly once"
        }
        check(eventRefs.toSet() == eventIds.toSet() && eventRefs.size == eventRefs.toSet().size) {
            "projection must not drop, duplicate or double-consume events"
        }
    }
}

internal val HISTORICAL_ENTRY_ORDER: Comparator<HistoricalEntry> =
    compareBy<HistoricalEntry>({ it.displayDate }, { it.sortInstant }, { it.sortKey })
