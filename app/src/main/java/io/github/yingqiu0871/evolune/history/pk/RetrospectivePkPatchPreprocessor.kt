package io.github.yingqiu0871.evolune.history.pk

import io.github.yingqiu0871.evolune.pk.Route
import java.time.Instant
import java.util.UUID

/** One patch transition of the frozen-ordered authoritative intake sequence. */
internal data class RetrospectivePkPatchTransition(
    val eventId: UUID,
    val occurredAt: Instant,
    val route: Route,
    /**
     * `false` when the transition cannot be represented/validated (unusable). Unusable
     * transitions deliberately stay visible to grammar processing so they cannot make a
     * preceding APPLY look like a legitimate final-open patch.
     */
    val usable: Boolean
)

internal data class RetrospectivePkPatchPreprocessResult(
    val keptEventIds: Set<UUID>,
    val exclusions: List<RetrospectivePkExcludedEvent>
)

/**
 * Frozen patch grammar (V17-C-00 §6, V17-C-01 §8):
 *
 * ```
 * patch-history := (APPLY, REMOVE)*, APPLY?
 * ```
 *
 * G1 same-instant transitions are ambiguous, G2 APPLY while another APPLY is active is
 * ambiguous, G3 orphan REMOVE is ambiguous, G4 duplicate REMOVE is ambiguous, G5 a final
 * open APPLY is valid. Only the affected ambiguous causal chain is excluded; unrelated
 * valid chains and non-patch events are untouched. No patch-instance identity is invented.
 */
internal object RetrospectivePkPatchPreprocessor {

    fun preprocess(
        transitions: List<RetrospectivePkPatchTransition>
    ): RetrospectivePkPatchPreprocessResult {
        val exclusions = mutableListOf<RetrospectivePkExcludedEvent>()
        val group = mutableListOf<RetrospectivePkPatchTransition>()
        var groupHasFailure = false
        var openApplies = 0
        var lastInstant: Instant? = null
        var settledIds = mutableSetOf<UUID>()

        fun emitGroup() {
            group.forEach { transition ->
                exclusions += RetrospectivePkExcludedEvent(
                    eventId = transition.eventId,
                    occurredAt = transition.occurredAt,
                    reason = if (transition.usable) {
                        RetrospectivePkExclusionReason.AMBIGUOUS_PATCH_PAIRING
                    } else {
                        RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT
                    }
                )
            }
        }

        transitions.forEach { transition ->
            val sameInstant = lastInstant != null && transition.occurredAt == lastInstant

            // Settlement boundary: only a strictly increasing instant with no unattributed
            // APPLY may separate one causal chain from the next.
            if (!sameInstant && openApplies == 0 && group.isNotEmpty()) {
                if (groupHasFailure) emitGroup()
                group.clear()
                groupHasFailure = false
            }

            if (!transition.usable) {
                groupHasFailure = true
            } else {
                when (transition.route) {
                    Route.PATCH_APPLY -> {
                        if (sameInstant) groupHasFailure = true
                        if (openApplies >= 1) groupHasFailure = true
                        openApplies += 1
                    }
                    Route.PATCH_REMOVE -> {
                        if (sameInstant) groupHasFailure = true
                        if (openApplies == 0) groupHasFailure = true else openApplies -= 1
                    }
                    else -> Unit
                }
            }
            group += transition
            lastInstant = transition.occurredAt
        }

        if (group.isNotEmpty() && groupHasFailure) emitGroup()

        val excludedIds = exclusions.mapTo(mutableSetOf()) { it.eventId }
        settledIds = transitions
            .filter { it.usable && it.eventId !in excludedIds }
            .mapTo(mutableSetOf()) { it.eventId }
        return RetrospectivePkPatchPreprocessResult(
            keptEventIds = settledIds,
            exclusions = exclusions
        )
    }
}
