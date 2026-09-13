package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.core.presentation.toRecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.HistoricalProjectionBuilder
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.HistoricalReadModel
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Phone-side read adapter that turns authoritative facts into a historical read model.
 *
 * It owns exactly the boundary work that must not leak into UI:
 * repository queries, occurrence-generation context bounds, projection invocation and
 * final date-range filtering. Occurrence matching itself stays in
 * [HistoricalProjectionBuilder] (the single A-01 matcher implementation).
 *
 * Query bounds are **bounded context expansion**, never a full-table scan:
 *
 * - occurrences are generated for a ±[OCCURRENCE_CONTEXT_DAYS] calendar-day context,
 *   which is what the existing cross-midnight / ±1h matcher adjacency needs;
 * - events are queried by an **instant** window padded by
 *   [EVENT_QUERY_CONTEXT_DAYS] calendar days, which is a *different* problem: a
 *   persisted `localDate` may have been recorded in a zone up to 26 hours away from
 *   the display zone (IANA civil offsets span UTC-12..UTC+14), so one calendar day of
 *   padding is not enough. The wider padding is still bounded and proportional;
 * - the returned range is filtered afterwards by the projection's final **display date**
 *   (which is provenance-based: intended occurrence date, persisted recording date, or
 *   the current-timezone derived date for true legacy orphans).
 *
 * Reads are derived-only: this service never writes to any repository.
 */
class HistoryReadService(
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository
) {
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

        val occurrenceWindowStart = startDate
            .minusDays(OCCURRENCE_CONTEXT_DAYS)
            .atStartOfDay(displayZone)
            .toInstant()
        val occurrenceWindowEndExclusive = endDate
            .plusDays(OCCURRENCE_CONTEXT_DAYS + 1)
            .atStartOfDay(displayZone)
            .toInstant()
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
        val recordedEvents = doseEvents
            .findOccurredBetween(eventQueryStart, eventQueryEndExclusive)
            .mapNotNull { it.toRecordedMedicationEvent() }

        val projection = HistoricalProjectionBuilder.derive(
            occurrences = occurrences,
            events = recordedEvents,
            now = now,
            displayZone = displayZone,
            policy = policy
        )

        return HistoricalReadModel.range(projection, startDate, endDate)
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
         * It is deliberately **not** widened by the event-query bound below.
         */
        const val OCCURRENCE_CONTEXT_DAYS: Long = 1

        /**
         * Event-query context: two calendar days on each side.
         *
         * Bound proof (see `V17_A_02_HISTORY_READ_MODEL.md` §7): IANA civil offsets span
         * UTC-12..UTC+14, so a persisted local date `D` can correspond to instants from
         * `(D-1) 10:00Z` (UTC+14 midnight) to `(D+1) 11:59:59.999Z` (UTC-12 end of day)
         * — about 26 hours of displacement, more than one calendar day. Two calendar
         * days of padding (>= 46 h even in the worst DST-shortened pair) covers that
         * envelope on both ends while keeping the query proportional to the range.
         */
        const val EVENT_QUERY_CONTEXT_DAYS: Long = 2

        /** Mirrors the occurrence generator's own ten-year window cap. */
        const val MAX_RANGE_DAYS: Long = 3_660
    }
}
