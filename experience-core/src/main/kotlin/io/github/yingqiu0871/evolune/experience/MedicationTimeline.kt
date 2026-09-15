package io.github.yingqiu0871.evolune.experience

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/**
 * Event-side projection consumed by the shared matcher.
 *
 * [source] is required on purpose: only `MANUAL` may be presented as a manual
 * intake, so callers must state the real origin instead of relying on a default.
 * [zoneId] and [localDate] carry the persisted recording context when the
 * authoritative event has it (legacy migrated rows have neither).
 */
data class RecordedMedicationEvent(
    val eventId: UUID,
    val occurredAt: Instant,
    val slotId: UUID?,
    val matchKey: MedicationMatchKey,
    val source: MedicationIntakeSource,
    val localDate: LocalDate? = null,
    val zoneId: ZoneId? = null,
    /**
     * Derived-layer propagation of the authoritative event extras (V17-C-00 §4).
     *
     * The default is source/test compatibility only: the production mapper always
     * populates every key the authoritative row carries and must never rely on the
     * default. Adding this field changes equality/hashCode of the data class.
     */
    val extras: Map<HistoricalMedicationExtraKey, Double> = emptyMap()
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

/** Presentation items plus the exact match decisions they were derived from. */
data class MedicationOccurrencePresentationResult(
    val items: List<MedicationTimelineItem>,
    val matches: Map<MedicationOccurrenceId, MedicationOccurrenceMatch>
)

object MedicationOccurrencePresentation {
    fun derive(
        occurrences: List<MedicationOccurrence>,
        recordedEvents: List<RecordedMedicationEvent>,
        now: Instant,
        policy: MedicationOccurrencePolicy = MedicationOccurrencePolicy()
    ): List<MedicationTimelineItem> =
        deriveWithMatches(occurrences, recordedEvents, now, policy).items

    /**
     * Runs the shared four-phase matcher exactly once and returns both the
     * presentation items and the underlying match decisions. Every derived
     * surface must consume one of these two outputs instead of re-running
     * occurrence matching.
     */
    fun deriveWithMatches(
        occurrences: List<MedicationOccurrence>,
        recordedEvents: List<RecordedMedicationEvent>,
        now: Instant,
        policy: MedicationOccurrencePolicy = MedicationOccurrencePolicy()
    ): MedicationOccurrencePresentationResult {
        val orderedOccurrences = occurrences.sortedWith(OCCURRENCE_ORDER)
        val matches = MedicationOccurrenceMatcher.assign(orderedOccurrences, recordedEvents, policy)
        val items = orderedOccurrences.map { occurrence ->
            val event = matches[occurrence.id]?.event
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
        return MedicationOccurrencePresentationResult(items = items, matches = matches)
    }
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
