package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.experience.HistoricalProjection
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import java.time.Instant
import java.time.ZoneId

/**
 * All-history read seam of the existing History architecture (V17-C-01 §4.2).
 *
 * The only production implementation is [HistoryReadService]; it is the same single
 * historical reader that serves [HistoryRangeSource]. Retrospective PK consumes this
 * seam only and never touches repositories or DAOs.
 */
fun interface AllAvailableHistorySource {
    suspend fun readAllAvailable(
        upperBoundInclusive: Instant,
        displayZone: ZoneId,
        policy: MedicationOccurrencePolicy
    ): AllAvailableHistory
}

/**
 * Approved all-history projection result.
 *
 * @property upperBoundInclusive inclusive upper bound of the consumed authoritative set.
 * @property lookbackStart `min(occurredAt)` over the consumed authoritative event set
 *   (after the inclusive upper-bound read, before eligibility/exclusion); `null` iff the
 *   consumed set is empty.
 * @property projection the single full-lookback projection derived over the complete
 *   consumed event set and the complete generated occurrence context.
 */
data class AllAvailableHistory(
    val upperBoundInclusive: Instant,
    val lookbackStart: Instant?,
    val projection: HistoricalProjection
)

/**
 * History-layer internal diagnostic (V17-C-01 RC-4). Wraps the dedicated generator
 * guard exception; it is not a public retrospective semantic reason.
 */
class HistoricalOccurrenceLimitExceededException(message: String) : RuntimeException(message)
