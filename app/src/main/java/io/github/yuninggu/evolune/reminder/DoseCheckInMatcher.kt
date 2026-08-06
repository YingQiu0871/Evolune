package io.github.yuninggu.evolune.reminder

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.MedicationPlan
import kotlin.math.abs

internal const val DOSE_CHECK_IN_WINDOW_MILLIS = 60 * 60 * 1000L
private const val DOSE_TOLERANCE_MG = 0.000_001

/**
 * Events without a proven slot identity are matched using the existing
 * medication fields and time window.
 */
internal fun DoseEvent.matchesPlanDose(plan: MedicationPlan): Boolean =
    route == plan.route &&
        ester == plan.ester &&
        abs(doseMG - plan.doseMG) <= DOSE_TOLERANCE_MG

internal fun hasPlanDoseCheckIn(
    plan: MedicationPlan,
    events: List<DoseEvent>,
    scheduledAtMillis: Long
): Boolean {
    return events.any { event ->
        event.matchesPlanDose(plan) &&
            abs(event.occurredAt.toEpochMilli() - scheduledAtMillis) <=
            DOSE_CHECK_IN_WINDOW_MILLIS
    }
}

internal fun reminderEvaluationTimeMillis(scheduledAtMillis: Long): Long =
    scheduledAtMillis + DOSE_CHECK_IN_WINDOW_MILLIS
