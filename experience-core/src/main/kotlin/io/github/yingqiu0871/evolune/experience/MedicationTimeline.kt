package io.github.yingqiu0871.evolune.experience

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

data class RecordedMedicationEvent(
    val eventId: UUID,
    val occurredAt: Instant,
    val slotId: UUID?,
    val matchKey: MedicationMatchKey
)

enum class MedicationOccurrenceStatus {
    UPCOMING,
    DUE,
    RECORDED,
    PAST_UNRECORDED
}

enum class MedicationActionAvailability {
    AVAILABLE,
    NOT_YET_DUE,
    ALREADY_RECORDED,
    WINDOW_EXPIRED
}

data class MedicationTimelineItem(
    val occurrence: MedicationOccurrence,
    val status: MedicationOccurrenceStatus,
    val actionAvailability: MedicationActionAvailability,
    val recordedEventId: UUID?
)

data class MedicationOccurrencePolicy(
    val dueBefore: Duration = Duration.ofHours(1),
    val dueAfter: Duration = Duration.ofHours(1),
    val matchBefore: Duration = Duration.ofHours(1),
    val matchAfter: Duration = Duration.ofHours(1),
    val doseTolerance: Double = 0.000_001
) {
    init {
        require(!dueBefore.isNegative)
        require(!dueAfter.isNegative)
        require(!matchBefore.isNegative)
        require(!matchAfter.isNegative)
        require(doseTolerance.isFinite() && doseTolerance >= 0.0)
    }
}

object MedicationOccurrencePresentation {
    fun derive(
        occurrences: List<MedicationOccurrence>,
        recordedEvents: List<RecordedMedicationEvent>,
        now: Instant,
        policy: MedicationOccurrencePolicy = MedicationOccurrencePolicy()
    ): List<MedicationTimelineItem> {
        val orderedOccurrences = occurrences.sortedWith(OCCURRENCE_ORDER)
        val matches = assignRecordedEvents(orderedOccurrences, recordedEvents, policy)
        return orderedOccurrences.map { occurrence ->
            val event = matches[occurrence.id]
            val status = when {
                event != null -> MedicationOccurrenceStatus.RECORDED
                Duration.between(occurrence.scheduledAt, now) < policy.dueBefore.negated() ->
                    MedicationOccurrenceStatus.UPCOMING
                Duration.between(occurrence.scheduledAt, now) <= policy.dueAfter ->
                    MedicationOccurrenceStatus.DUE
                else -> MedicationOccurrenceStatus.PAST_UNRECORDED
            }
            MedicationTimelineItem(
                occurrence = occurrence,
                status = status,
                actionAvailability = when (status) {
                    MedicationOccurrenceStatus.UPCOMING ->
                        MedicationActionAvailability.NOT_YET_DUE
                    MedicationOccurrenceStatus.DUE ->
                        MedicationActionAvailability.AVAILABLE
                    MedicationOccurrenceStatus.RECORDED ->
                        MedicationActionAvailability.ALREADY_RECORDED
                    MedicationOccurrenceStatus.PAST_UNRECORDED ->
                        MedicationActionAvailability.WINDOW_EXPIRED
                },
                recordedEventId = event?.eventId
            )
        }
    }

    private fun assignRecordedEvents(
        occurrences: List<MedicationOccurrence>,
        events: List<RecordedMedicationEvent>,
        policy: MedicationOccurrencePolicy
    ): Map<MedicationOccurrenceId, RecordedMedicationEvent> {
        val result = mutableMapOf<MedicationOccurrenceId, RecordedMedicationEvent>()

        // Slot-bearing events have the strongest available identity. Resolve them
        // first in stable event order so fallback cannot reuse their occurrences.
        val exactConsumed = mutableSetOf<MedicationOccurrenceId>()
        events.asSequence()
            .filter { it.slotId != null }
            .sortedWith(EVENT_ORDER)
            .forEach { event ->
                val candidates = occurrences.filter { occurrence ->
                    occurrence.id !in exactConsumed &&
                        matchCandidate(occurrence, event, policy) != null
                }
                if (candidates.size == 1) {
                    val occurrence = candidates.single()
                    exactConsumed += occurrence.id
                    result[occurrence.id] = event
                }
            }

        // Null-slot fallback is accepted only when its candidate is unique after
        // exact matches. Candidate sets are fixed before fallback assignments, so
        // iteration order cannot turn a genuine ambiguity into a match.
        val uniqueFallback = events.asSequence()
            .filter { it.slotId == null }
            .sortedWith(EVENT_ORDER)
            .mapNotNull { event ->
                val candidates = occurrences.filter { occurrence ->
                    occurrence.id !in exactConsumed &&
                        matchCandidate(occurrence, event, policy) != null
                }
                if (candidates.size == 1) {
                    MatchCandidate(candidates.single(), event)
                } else {
                    null
                }
            }
            .groupBy { it.occurrence.id }

        uniqueFallback.forEach { (occurrenceId, candidates) ->
            result[occurrenceId] = candidates
                .sortedWith(compareBy({ it.event.occurredAt }, { it.event.eventId.toString() }))
                .first()
                .event
        }
        return result
    }

    private fun matchCandidate(
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

data class MedicationTimelinePolicy(
    val previousCount: Int = 2,
    val upcomingCount: Int = 5
) {
    init {
        require(previousCount >= 0)
        require(upcomingCount >= 0)
    }
}

data class MedicationTimelineWindow(
    val previous: List<MedicationTimelineItem>,
    /** All actionable items tied at the selected current instant. Usually one. */
    val current: List<MedicationTimelineItem>,
    val upcoming: List<MedicationTimelineItem>
)

object MedicationTimelineSelector {
    fun select(
        items: List<MedicationTimelineItem>,
        now: Instant,
        policy: MedicationTimelinePolicy = MedicationTimelinePolicy()
    ): MedicationTimelineWindow {
        val ordered = items.sortedWith(compareByTimelineItem())
        val candidate = ordered
            .asSequence()
            .filter { it.actionAvailability == MedicationActionAvailability.AVAILABLE }
            .minWithOrNull(
                compareBy<MedicationTimelineItem>(
                    { distance(it.occurrence.scheduledAt, now) },
                    { it.occurrence.scheduledAt },
                    { it.occurrence.id.value.toString() }
                )
            )

        if (candidate == null) {
            return MedicationTimelineWindow(
                previous = ordered.filter { it.occurrence.scheduledAt <= now }
                    .takeLast(policy.previousCount),
                current = emptyList(),
                upcoming = ordered.filter { it.occurrence.scheduledAt > now }
                    .take(policy.upcomingCount)
            )
        }

        val currentAt = candidate.occurrence.scheduledAt
        val current = ordered.filter {
            it.occurrence.scheduledAt == currentAt &&
                it.actionAvailability == MedicationActionAvailability.AVAILABLE
        }
        val currentIds = current.mapTo(mutableSetOf()) { it.occurrence.id }
        return MedicationTimelineWindow(
            previous = ordered.filter {
                it.occurrence.id !in currentIds && it.occurrence.scheduledAt <= currentAt
            }.takeLast(policy.previousCount),
            current = current,
            upcoming = ordered.filter { it.occurrence.scheduledAt > currentAt }
                .take(policy.upcomingCount)
        )
    }

    private fun compareByTimelineItem() = compareBy<MedicationTimelineItem>(
        { it.occurrence.scheduledAt },
        { it.occurrence.planId.toString() },
        { it.occurrence.slotPosition },
        { it.occurrence.slotId.toString() },
        { it.occurrence.id.value.toString() }
    )

    private fun distance(first: Instant, second: Instant): Duration {
        val duration = Duration.between(first, second)
        return if (duration.isNegative) duration.negated() else duration
    }
}
