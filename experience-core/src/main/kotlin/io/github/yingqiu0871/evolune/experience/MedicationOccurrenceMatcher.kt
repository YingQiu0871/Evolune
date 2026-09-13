package io.github.yingqiu0871.evolune.experience

import java.time.Duration
import java.util.UUID
import kotlin.math.abs

/**
 * Why a recorded event is considered to belong to an occurrence.
 *
 * The values mirror the four ordered phases of the single matcher implementation
 * below; they never replace it. History, Timeline, Insights and any future
 * retrospective PK adapter must consume these decisions instead of re-running
 * their own matching rules.
 */
enum class MedicationMatchProvenance {
    /** Phase 1: persisted `slotId` + persisted `localDate` matched exactly. */
    EXACT_SLOT_AND_LOCAL_DATE,

    /** Phase 2: persisted `slotId` without a trustworthy `localDate`, matched by the bounded time window. */
    SLOT_WINDOW_WITHOUT_LOCAL_DATE,

    /** Phase 3: no slot identity at all (legacy null-slot), matched by the bounded time window. */
    NULL_SLOT_TIME_WINDOW,

    /** Phase 4: no slot identity, matched by the delayed same-local-date fallback only. */
    NULL_SLOT_SAME_DAY
}

/** One occurrence-to-event decision produced by the shared matcher. */
data class MedicationOccurrenceMatch(
    val event: RecordedMedicationEvent,
    val provenance: MedicationMatchProvenance
)

internal object MedicationOccurrenceMatcher {
    fun assign(
        occurrences: List<MedicationOccurrence>,
        events: List<RecordedMedicationEvent>,
        policy: MedicationOccurrencePolicy
    ): Map<MedicationOccurrenceId, MedicationOccurrenceMatch> {
        val result = mutableMapOf<MedicationOccurrenceId, MedicationOccurrenceMatch>()
        val consumedOccurrences = mutableSetOf<MedicationOccurrenceId>()
        val consumedEvents = mutableSetOf<UUID>()

        // A persisted slot/date pair identifies one logical occurrence independently
        // from the actual dose time. Resolve it before all time-window fallbacks.
        events.asSequence()
            .filter { it.slotId != null && it.localDate != null }
            .sortedWith(EVENT_ORDER)
            .forEach { event ->
                val candidates = occurrences.filter { occurrence ->
                    occurrence.id !in consumedOccurrences &&
                        occurrence.slotId == event.slotId &&
                        occurrence.scheduledLocalDateTime.toLocalDate() == event.localDate
                }
                if (candidates.size == 1) {
                    val occurrence = candidates.single()
                    consumedOccurrences += occurrence.id
                    consumedEvents += event.eventId
                    result[occurrence.id] = MedicationOccurrenceMatch(
                        event = event,
                        provenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE
                    )
                }
            }

        // Older slot-bearing events do not have a trustworthy local date. Preserve
        // their previous slot plus inclusive time-window behavior without inferring
        // an exact cross-date association.
        events.asSequence()
            .filter { it.slotId != null && it.localDate == null }
            .sortedWith(EVENT_ORDER)
            .forEach { event ->
                val candidates = occurrences.filter { occurrence ->
                    occurrence.id !in consumedOccurrences &&
                        fallbackMatchCandidate(occurrence, event, policy) != null
                }
                if (candidates.size == 1) {
                    val occurrence = candidates.single()
                    consumedOccurrences += occurrence.id
                    consumedEvents += event.eventId
                    result[occurrence.id] = MedicationOccurrenceMatch(
                        event = event,
                        provenance = MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE
                    )
                }
            }

        // Preserve the original null-slot time-window match as its own phase. Its
        // candidate sets are fixed before any of these matches are assigned, so
        // input order cannot turn a genuine ambiguity into a match.
        val windowCandidateEventIds = events.asSequence()
            .filter { it.slotId == null && it.eventId !in consumedEvents }
            .filter { event ->
                occurrences.any { occurrence ->
                    fallbackMatchCandidate(occurrence, event, policy) != null
                }
            }
            .map { it.eventId }
            .toSet()
        val uniqueTimeWindowMatches = events.asSequence()
            .filter { it.slotId == null && it.eventId !in consumedEvents }
            .sortedWith(EVENT_ORDER)
            .mapNotNull { event ->
                val candidates = occurrences.filter { occurrence ->
                    occurrence.id !in consumedOccurrences &&
                        fallbackMatchCandidate(occurrence, event, policy) != null
                }
                if (candidates.size == 1) {
                    MatchCandidate(candidates.single(), event)
                } else {
                    null
                }
            }
            .groupBy { it.occurrence.id }

        uniqueTimeWindowMatches.forEach { (occurrenceId, candidates) ->
            val event = candidates
                .sortedWith(compareBy({ it.event.occurredAt }, { it.event.eventId.toString() }))
                .first()
                .event
            consumedOccurrences += occurrenceId
            consumedEvents += event.eventId
            result[occurrenceId] = MedicationOccurrenceMatch(
                event = event,
                provenance = MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW
            )
        }

        // Only null-slot events that remain unused after the original window
        // phase may use the delayed same-day fallback. This deliberately does not
        // widen the time-window candidate set above. An event with any original
        // window evidence remains ineligible even if it lost a competition.
        val uniqueSameDayMatches = events.asSequence()
            .filter {
                it.slotId == null &&
                    it.eventId !in consumedEvents &&
                    it.eventId !in windowCandidateEventIds
            }
            .sortedWith(EVENT_ORDER)
            .mapNotNull { event ->
                val candidates = occurrences.filter { occurrence ->
                    occurrence.id !in consumedOccurrences &&
                        sameDayNullSlotMatchCandidate(occurrence, event, policy) != null
                }
                if (candidates.size == 1) {
                    MatchCandidate(candidates.single(), event)
                } else {
                    null
                }
            }
            .groupBy { it.occurrence.id }

        uniqueSameDayMatches.forEach { (occurrenceId, candidates) ->
            val event = candidates
                .sortedWith(compareBy({ it.event.occurredAt }, { it.event.eventId.toString() }))
                .first()
                .event
            consumedOccurrences += occurrenceId
            consumedEvents += event.eventId
            result[occurrenceId] = MedicationOccurrenceMatch(
                event = event,
                provenance = MedicationMatchProvenance.NULL_SLOT_SAME_DAY
            )
        }
        return result
    }

    private fun fallbackMatchCandidate(
        occurrence: MedicationOccurrence,
        event: RecordedMedicationEvent,
        policy: MedicationOccurrencePolicy
    ): MatchCandidate? {
        val difference = Duration.between(occurrence.scheduledAt, event.occurredAt)
        if (difference < policy.matchBefore.negated() || difference > policy.matchAfter) {
            return null
        }

        if (event.slotId != null) {
            if (event.slotId != occurrence.slotId) return null
        } else {
            if (!event.matchKey.matches(occurrence.presentation.matchKey, policy.doseTolerance)) {
                return null
            }
        }
        return MatchCandidate(
            occurrence = occurrence,
            event = event
        )
    }

    private fun sameDayNullSlotMatchCandidate(
        occurrence: MedicationOccurrence,
        event: RecordedMedicationEvent,
        policy: MedicationOccurrencePolicy
    ): MatchCandidate? {
        if (event.slotId != null || event.localDate == null) return null
        if (occurrence.scheduledLocalDateTime.toLocalDate() != event.localDate) return null
        if (!event.matchKey.matches(occurrence.presentation.matchKey, policy.doseTolerance)) return null
        return MatchCandidate(
            occurrence = occurrence,
            event = event
        )
    }

    private fun MedicationMatchKey.matches(
        other: MedicationMatchKey,
        doseTolerance: Double
    ): Boolean =
        routeKey == other.routeKey &&
            medicationKey == other.medicationKey &&
            abs(doseAmount - other.doseAmount) <= doseTolerance

    private data class MatchCandidate(
        val occurrence: MedicationOccurrence,
        val event: RecordedMedicationEvent
    )

    private val EVENT_ORDER = compareBy<RecordedMedicationEvent>(
        { it.occurredAt },
        { it.eventId.toString() }
    )
}
