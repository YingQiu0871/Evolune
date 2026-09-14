package io.github.yingqiu0871.evolune.history.insights

import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import java.time.LocalDate
import java.time.ZoneId

/** What the Insights surface currently shows. */
enum class InsightsPhase {
    /** A range load is in flight. */
    LOADING,

    /** The load succeeded and the range carries at least one historical entry. */
    CONTENT,

    /**
     * The load succeeded and the range carries **no historical entry at all**: no matched
     * occurrence, no unmatched intake and no unrecorded occurrence. A range that only carries
     * unrecorded occurrences still holds real history and is therefore [CONTENT]
     * (v1.7-B-02-R1 section 1).
     */
    EMPTY,

    /** The load failed (read failure or a broken history contract). */
    ERROR,

    /** The selected range is not valid, so no read was performed. */
    INVALID_RANGE
}

/**
 * Typed load failure.
 *
 * A broken history contract is deliberately **not** flattened into a generic read error: it means
 * the authoritative data violated a frozen invariant, and it must stay diagnosable instead of
 * being presented as "no data".
 */
sealed interface InsightsLoadFailure {
    /** The authoritative read itself failed. */
    data class ReadFailure(val cause: Throwable) : InsightsLoadFailure

    /** The aggregation refused malformed history (frozen contract violation). */
    data class ContractViolation(val cause: Throwable) : InsightsLoadFailure
}

/**
 * Single UI state of the Insights surface.
 *
 * It carries the resolved endpoints, the current `today`/display zone and the **authoritative**
 * [MedicationInsightsSummary] produced by the B-01 aggregator. The state never re-derives metric
 * values and never exposes a derived judgement field (v1.7-B-02 sections 9, 31).
 */
data class InsightsUiState(
    val selection: InsightsRangeSelection = InsightsRangeSelection.DEFAULT,
    /** Resolved inclusive start; null while the selection is invalid. */
    val startDate: LocalDate? = null,
    /** Resolved inclusive end; null while the selection is invalid. */
    val endDate: LocalDate? = null,
    val today: LocalDate,
    val displayZone: ZoneId,
    val phase: InsightsPhase = InsightsPhase.LOADING,
    val summary: MedicationInsightsSummary? = null,
    val failure: InsightsLoadFailure? = null,
    val validationError: InsightsRangeValidationError? = null
)
