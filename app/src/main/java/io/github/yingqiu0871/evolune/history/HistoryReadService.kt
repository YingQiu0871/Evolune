package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.core.presentation.toRecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.HistoricalProjectionBuilder
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.HistoricalReadModel
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerationLimitExceededException
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Phone-side read adapter that turns authoritative facts into a historical read model.
 *
 * It owns exactly the boundary work that must not leak into UI:
 * repository queries, occurrence-generation context bounds, dual-channel event fetch,
 * projection invocation and final date-range filtering. Occurrence matching itself stays
 * in [HistoricalProjectionBuilder] (the single A-01 matcher implementation).
 *
 * **Event completeness uses two channels, never one padding value** (A-02-R2):
 *
 * - **Channel A — persisted local date.** Every event whose persisted `localDate` is
 *   non-null is fetched by that date over the occurrence context
 *   `[startDate - OCCURRENCE_CONTEXT_DAYS, endDate + OCCURRENCE_CONTEXT_DAYS]`
 *   (inclusive). The persisted date is written at record time and is **not** derivable
 *   from `occurredAt`: a reminder confirmation stores the planned day while `occurredAt`
 *   is the action instant, so the two may differ by an arbitrary amount. This channel
 *   therefore guarantees Channel-A completeness independently of time zones, DST,
 *   date-line jumps or padding size.
 * - **Channel B — instant context.** [findOccurredBetween] over a bounded instant window
 *   padded by [EVENT_QUERY_CONTEXT_DAYS] calendar days. It exists for the rows Channel A
 *   cannot see: legacy rows with `localDate IS NULL` (whose display day is derived from
 *   the instant in the display zone) and bounded inferred candidates adjacent to the
 *   requested days. **It no longer carries any persisted-date completeness guarantee.**
 *
 * The two channels are unioned by authoritative event id before projection (see
 * [unionAuthoritativeEvents]); the returned range is then filtered by the projection's
 * final **display date** (intended occurrence date, persisted recording date, or the
 * current-timezone derived date for true legacy orphans).
 *
 * Reads are derived-only: this service never writes to any repository.
 */
class HistoryReadService(
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository,
    /**
     * D-10/M02: worker dispatcher for the post-query pure-CPU History tail
     * (union/deduplication, projection/matching, range grouping). Room keeps
     * its own query executor; the ViewModel keeps Main publication ownership.
     */
    private val projectionDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AllAvailableHistorySource {
    suspend fun readRange(
        startDate: LocalDate,
        endDate: LocalDate,
        displayZone: ZoneId,
        now: Instant,
        policy: MedicationOccurrencePolicy = MedicationOccurrencePolicy()
    ): HistoricalRange {
        require(!endDate.isBefore(startDate)) {
            "history range endDate $endDate must not be before startDate $startDate"
        }
        require(ChronoUnit.DAYS.between(startDate, endDate) < MAX_RANGE_DAYS) {
            "history range must not exceed $MAX_RANGE_DAYS days"
        }

        // Occurrence context: matcher adjacency (cross-midnight / ±1h inferred matching).
        val occurrenceContextStartDate = startDate.minusDays(OCCURRENCE_CONTEXT_DAYS)
        val occurrenceContextEndDate = endDate.plusDays(OCCURRENCE_CONTEXT_DAYS)
        val occurrenceWindowStart = occurrenceContextStartDate.atStartOfDay(displayZone).toInstant()
        val occurrenceWindowEndExclusive = endDate
            .plusDays(OCCURRENCE_CONTEXT_DAYS + 1)
            .atStartOfDay(displayZone)
            .toInstant()

        // Channel B window: bounded instant context for null-localDate / adjacency rows.
        val eventQueryStart = startDate
            .minusDays(EVENT_QUERY_CONTEXT_DAYS)
            .atStartOfDay(displayZone)
            .toInstant()
        val eventQueryEndExclusive = endDate
            .plusDays(EVENT_QUERY_CONTEXT_DAYS + 1)
            .atStartOfDay(displayZone)
            .toInstant()

        val schedules = medicationPlans.observeAll().first().map { it.toMedicationSchedule() }
        val occurrences = generateContextOccurrences(
            schedules = schedules,
            windowStart = occurrenceWindowStart,
            windowEndExclusive = occurrenceWindowEndExclusive,
            displayZone = displayZone
        )

        // Channel A: persisted `localDate` over the occurrence context (inclusive).
        val persistedDateEvents = doseEvents.findRecordedLocalDateBetween(
            startInclusive = occurrenceContextStartDate,
            endInclusive = occurrenceContextEndDate
        )
        // Channel B: bounded instant context.
        val instantContextEvents = doseEvents.findOccurredBetween(
            eventQueryStart,
            eventQueryEndExclusive
        )

        // D-10/M02: the post-query pure-CPU History tail runs on the injected
        // worker dispatcher. Inputs (occurrences, both channel results, query
        // window, now, zone, policy) are stable captured values; queries keep
        // their original placement and executor ownership.
        return withContext(projectionDispatcher) {
            val recordedEvents = unionAuthoritativeEvents(persistedDateEvents, instantContextEvents)

            val projection = HistoricalProjectionBuilder.derive(
                occurrences = occurrences,
                events = recordedEvents,
                now = now,
                displayZone = displayZone,
                policy = policy
            )

            HistoricalReadModel.range(projection, startDate, endDate)
        }
    }

    /**
     * All-history read capability of the same History reader (V17-C-01 §4.2).
     *
     * Fetches every authoritative row with `occurredAt <= upperBoundInclusive` (no lower
     * bound), asserts uniqueness/mappability, derives the occurrence context so that it
     * also covers the span between the last recorded event and the upper bound, generates
     * occurrences in the existing <=3660-day chunks (no cumulative cap) and performs ONE
     * projection derive over the complete consumed event set.
     */
    override suspend fun readAllAvailable(
        upperBoundInclusive: Instant,
        displayZone: ZoneId,
        policy: MedicationOccurrencePolicy
    ): AllAvailableHistory {
        val events = doseEvents.findAllOccurredUpTo(upperBoundInclusive)
            .sortedWith(compareBy({ it.occurredAt }, { it.id.toString() }))

        check(events.map { it.id }.toSet().size == events.size) {
            "all-history read returned duplicate authoritative event ids"
        }
        val recordedEvents = events.map { event ->
            event.toRecordedMedicationEvent()
                ?: throw IllegalStateException(
                    "authoritative event ${event.id} is not a recorded intake"
                )
        }

        if (events.isEmpty()) {
            return AllAvailableHistory(
                upperBoundInclusive = upperBoundInclusive,
                lookbackStart = null,
                projection = HistoricalProjectionBuilder.derive(
                    occurrences = emptyList(),
                    events = emptyList(),
                    now = upperBoundInclusive,
                    displayZone = displayZone,
                    policy = policy
                )
            )
        }

        // Occurrence context (R4.2): each authoritative event contributes exactly ONE candidate
        // date - its persisted localDate when present, otherwise the occurredAt-derived date.
        // The context is additionally anchored to the query's upper bound so that plan occurrences
        // between the last recorded event and the bound are generated.
        val candidateDates = events.map { event ->
            event.localDate
                ?: event.occurredAt.atZone(displayZone).toLocalDate()
        }
        val upperBoundLocalDate = upperBoundInclusive.atZone(displayZone).toLocalDate()
        val contextStartDate = candidateDates.min().minusDays(OCCURRENCE_CONTEXT_DAYS)
        val contextEndDate = maxOf(candidateDates.max(), upperBoundLocalDate)
            .plusDays(OCCURRENCE_CONTEXT_DAYS)
        val occurrenceWindowStart = contextStartDate.atStartOfDay(displayZone).toInstant()
        val occurrenceWindowEndExclusive = contextEndDate
            .plusDays(1)
            .atStartOfDay(displayZone)
            .toInstant()

        val schedules = medicationPlans.observeAll().first().map { it.toMedicationSchedule() }
        val occurrences = try {
            generateContextOccurrences(
                schedules = schedules,
                windowStart = occurrenceWindowStart,
                windowEndExclusive = occurrenceWindowEndExclusive,
                displayZone = displayZone
            )
        } catch (error: MedicationOccurrenceGenerationLimitExceededException) {
            throw HistoricalOccurrenceLimitExceededException(
                error.message ?: "occurrence context generation exceeded the per-call limit"
            )
        }

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = occurrences,
            events = recordedEvents,
            now = upperBoundInclusive,
            displayZone = displayZone,
            policy = policy
        )

        val projectedEventIds = projection.entries.mapNotNull { entry ->
            when (entry) {
                is MatchedHistoricalOccurrence -> entry.event.eventId
                is UnmatchedHistoricalIntake -> entry.event.eventId
                is UnrecordedHistoricalOccurrence -> null
            }
        }
        val consumedEventIds = events.map { it.id }
        check(
            projectedEventIds.size == consumedEventIds.size &&
                projectedEventIds.toSet() == consumedEventIds.toSet()
        ) {
            "all-history projection must contain every consumed authoritative event exactly once"
        }

        return AllAvailableHistory(
            upperBoundInclusive = upperBoundInclusive,
            lookbackStart = events.first().occurredAt,
            projection = projection
        )
    }

    /**
     * Unions the two event channels into one authoritative input list.
     *
     * Rows are deduplicated by authoritative event id: a row fetched by both channels is
     * one database row and must reach the projection exactly once. The channels read the
     * same table through the same mapper, so a repeated id must carry identical content —
     * any difference means malformed input and fails fast instead of silently picking a
     * winner.
     */
    private fun unionAuthoritativeEvents(
        persistedDateEvents: List<DoseEvent>,
        instantContextEvents: List<DoseEvent>
    ): List<RecordedMedicationEvent> {
        val byId = LinkedHashMap<UUID, DoseEvent>()
        (persistedDateEvents + instantContextEvents).forEach { event ->
            val existing = byId[event.id]
            check(existing == null || existing == event) {
                "history event channels returned conflicting rows for event ${event.id}"
            }
            byId[event.id] = event
        }
        return byId.values.mapNotNull { it.toRecordedMedicationEvent() }
    }

    /**
     * Generates occurrences for the whole occurrence context even when the requested
     * range sits at [MAX_RANGE_DAYS]: the generator itself caps a single window at
     * [OccurrenceGenerationWindow.MAX_WINDOW_DAYS], so the context is split into
     * chronological half-open chunks instead of shrinking the context. Chunks do not
     * overlap, so no occurrence can be produced twice, and the generator remains the
     * only recurrence implementation.
     */
    private fun generateContextOccurrences(
        schedules: List<io.github.yingqiu0871.evolune.experience.MedicationSchedule>,
        windowStart: Instant,
        windowEndExclusive: Instant,
        displayZone: ZoneId
    ): List<io.github.yingqiu0871.evolune.experience.MedicationOccurrence> {
        val occurrences = mutableListOf<io.github.yingqiu0871.evolune.experience.MedicationOccurrence>()
        var chunkStart = windowStart
        while (chunkStart < windowEndExclusive) {
            val candidateEnd = chunkStart.plus(Duration.ofDays(OccurrenceGenerationWindow.MAX_WINDOW_DAYS))
            val chunkEnd = if (candidateEnd.isBefore(windowEndExclusive)) candidateEnd else windowEndExclusive
            occurrences += MedicationOccurrenceGenerator.generate(
                schedules = schedules,
                window = OccurrenceGenerationWindow(chunkStart, chunkEnd),
                zoneId = displayZone
            )
            chunkStart = chunkEnd
        }
        return occurrences
    }

    companion object {
        /**
         * Occurrence-generation context: one calendar day on each side. This is what
         * the matcher adjacency needs (±1h window plus the cross-midnight case).
         *
         * The same date context is used as the **inclusive** range of Channel A
         * (`findRecordedLocalDateBetween`), because the historical matcher may bind an
         * event to an occurrence on an adjacent day and that binding is decided by the
         * persisted date, not by the instant.
         */
        const val OCCURRENCE_CONTEXT_DAYS: Long = 1

        /**
         * Channel B (instant) context: two calendar days on each side.
         *
         * This window exists **only** for rows Channel A cannot see — legacy rows with
         * `localDate IS NULL`, whose display day is derived from the instant in the
         * display zone, plus bounded inferred candidates adjacent to the requested days.
         *
         * It is **not** a completeness guarantee for persisted-date events: the persisted
         * `localDate` is written independently of `occurredAt` (a reminder confirmation
         * stores the planned day, a Wear confirmation stores the day carried by the
         * command), so the two may differ by an arbitrary amount and no finite padding
         * could cover every legal row. Persisted-date completeness comes from Channel A.
         */
        const val EVENT_QUERY_CONTEXT_DAYS: Long = 2

        /** Mirrors the occurrence generator's own ten-year window cap. */
        const val MAX_RANGE_DAYS: Long = 3_660
    }
}
