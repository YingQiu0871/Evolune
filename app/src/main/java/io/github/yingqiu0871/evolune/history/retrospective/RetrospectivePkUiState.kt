package io.github.yingqiu0871.evolune.history.retrospective

import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkResult
import java.time.Instant
import java.time.ZoneId

/**
 * V17-C-04 §4.3 — the single retrospective surface state.
 *
 * `CONTENT` always carries [RetrospectivePkResult.Available] and `UNAVAILABLE` always carries
 * [RetrospectivePkResult.Unavailable]; both are published in one frame so a half state is never
 * observable. The presentation layer recomputes no numbers and rewrites no limitations/exclusions.
 */
enum class RetrospectivePhase { LOADING, CONTENT, UNAVAILABLE, ERROR }

/**
 * Typed load failure (V17-C-04 §4.3). A broken frozen contract stays diagnosable instead of
 * being flattened into a generic read error.
 */
sealed interface RetrospectiveLoadFailure {
    /** A history-layer read itself failed (Read 1/2/3 or the current-settings read). */
    data class ReadFailure(val cause: Throwable) : RetrospectiveLoadFailure

    /** The frozen retrospective contract was violated (fail-fast type preserved). */
    data class ContractViolation(val cause: Throwable) : RetrospectiveLoadFailure

    /** §6.3 defensive path: invalid current body weight, zero history reads performed. */
    data object InvalidBodyWeight : RetrospectiveLoadFailure
}

data class RetrospectivePkUiState(
    val windowStart: Instant,
    val windowEnd: Instant,
    val displayZone: ZoneId,
    val selectedRange: RetrospectivePkRange = RetrospectivePkRange.LAST_7_DAYS,
    val phase: RetrospectivePhase = RetrospectivePhase.LOADING,
    val result: RetrospectivePkResult? = null,
    /** Filled only on a fully successful load; rendered only in CONTENT. */
    val markers: List<RetrospectiveMarker> = emptyList(),
    val failure: RetrospectiveLoadFailure? = null
)
