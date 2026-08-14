package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import java.time.Instant
import java.util.UUID

internal class WearActionRecorder(
    medicationPlans: MedicationPlanRepository,
    doseEvents: DoseEventRepository
) {
    private val engine = RecordDoseEventEngine(medicationPlans, doseEvents)

    suspend fun record(
        planId: UUID,
        actionId: UUID,
        recordedAt: Instant,
        createEvent: suspend (MedicationPlan) -> DoseEvent
    ): RecordDoseEventActionResult = engine.execute(
        planId = planId,
        eventId = actionId,
        expectedSource = DoseEventSource.WEAR,
        requireEnabledPlan = true,
        policy = ExistingEventPolicy.FirstAcceptedBySourceAndOccurredAt(
            expectedSource = DoseEventSource.WEAR,
            expectedOccurredAt = recordedAt
        ),
        createEvent = createEvent
    )
}
