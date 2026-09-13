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
 * - context dates: `[startDate - 1 day, endDate + 1 day]` in [displayZone];
 * - events are queried by **instant** window `[contextStart, contextEndExclusive)`, so
 *   legacy rows without a persisted `localDate` are not missed;
 * - occurrences are generated for the same bounded context so the existing
 *   cross-midnight / ±1h compatibility matching still works for the requested days;
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

        val contextStart = startDate.minusDays(CONTEXT_DAYS).atStartOfDay(displayZone).toInstant()
        val contextEndExclusive = endDate.plusDays(CONTEXT_DAYS + 1).atStartOfDay(displayZone).toInstant()

        val schedules = medicationPlans.observeAll().first().map { it.toMedicationSchedule() }
        val occurrences = MedicationOccurrenceGenerator.generate(
            schedules = schedules,
            window = OccurrenceGenerationWindow(contextStart, contextEndExclusive),
            zoneId = displayZone
        )
        val recordedEvents = doseEvents
            .findOccurredBetween(contextStart, contextEndExclusive)
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

    companion object {
        /**
         * One day of margin on each side. It covers the ±1h bounded window, the
         * cross-midnight compatibility case and zone-shifted legacy attribution while
         * keeping every query proportional to the requested range.
         */
        const val CONTEXT_DAYS: Long = 1

        /** Mirrors the occurrence generator's own ten-year window cap. */
        const val MAX_RANGE_DAYS: Long = 3_660
    }
}
