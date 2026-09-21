package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import java.time.ZoneId

/**
 * Identity of the schedule used to interpret a retained absolute deadline.
 * A zone change must invalidate the old deadline because the same plan can map
 * to a different instant in the new zone.
 */
internal data class ScheduleBoundaryIdentity(
    val enabledPlans: List<MedicationPlan>,
    val zoneId: ZoneId
)

internal data class ScheduleBoundaryObservation(
    val nextDeadlineTimeH: Double?,
    val crossed: Boolean
)

/**
 * Retains one strict-future schedule boundary independently of composition.
 *
 * The caller supplies the schedule-specific strict-future calculation. The
 * tracker compares against its retained value first, consumes a crossed
 * value once, and only then accepts the newly calculated future value.
 */
internal class ScheduleBoundaryTracker {
    private var identity: ScheduleBoundaryIdentity? = null
    private var pendingDeadlineTimeH: Double? = null

    fun observe(
        identity: ScheduleBoundaryIdentity,
        nowTimeH: Double,
        nextDeadlineProvider: () -> Double?
    ): ScheduleBoundaryObservation {
        val identityChanged = this.identity != identity
        if (identityChanged) {
            this.identity = identity
            pendingDeadlineTimeH = null
        }

        val retainedDeadlineTimeH = pendingDeadlineTimeH
        val crossed = !identityChanged &&
            retainedDeadlineTimeH != null &&
            nowTimeH >= retainedDeadlineTimeH

        if (crossed) {
            // Consume the old boundary before calculating its replacement.
            pendingDeadlineTimeH = null
        }

        // Keep the existing per-tick schedule calculation as the chart source;
        // it is never allowed to replace an unconsumed retained deadline.
        val calculatedNextDeadlineTimeH = nextDeadlineProvider()
        if (identityChanged || retainedDeadlineTimeH == null || crossed) {
            pendingDeadlineTimeH = calculatedNextDeadlineTimeH
        }

        return ScheduleBoundaryObservation(
            nextDeadlineTimeH = pendingDeadlineTimeH,
            crossed = crossed
        )
    }
}
