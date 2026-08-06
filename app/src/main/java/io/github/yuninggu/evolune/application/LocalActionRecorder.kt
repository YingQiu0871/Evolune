package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.MedicationPlan
import java.nio.charset.StandardCharsets
import java.util.UUID

internal class LocalActionRecorder(
    medicationPlans: MedicationPlanRepository,
    doseEvents: DoseEventRepository
) {
    private val engine = RecordDoseEventEngine(medicationPlans, doseEvents)

    suspend fun recordReminder(
        planId: UUID,
        scheduledAtMillis: Long,
        createEvent: suspend (MedicationPlan, UUID) -> DoseEvent
    ): RecordDoseEventActionResult {
        val eventId = localEventId("reminder", planId, scheduledAtMillis)
        return engine.execute(
            planId = planId,
            eventId = eventId,
            expectedSource = DoseEventSource.REMINDER,
            requireEnabledPlan = false,
            policy = ExistingEventPolicy.FirstAcceptedBySource(DoseEventSource.REMINDER),
            createEvent = { plan -> createEvent(plan, eventId) }
        )
    }

    suspend fun recordWidget(
        planId: UUID,
        recordedAtMillis: Long,
        createEvent: suspend (MedicationPlan, UUID) -> DoseEvent
    ): RecordDoseEventActionResult {
        val eventId = localEventId("widget", planId, recordedAtMillis / MILLIS_PER_MINUTE)
        return engine.execute(
            planId = planId,
            eventId = eventId,
            expectedSource = DoseEventSource.WIDGET,
            requireEnabledPlan = true,
            policy = ExistingEventPolicy.FirstAcceptedBySource(DoseEventSource.WIDGET),
            createEvent = { plan -> createEvent(plan, eventId) }
        )
    }

    private fun localEventId(kind: String, planId: UUID, occurrence: Long): UUID =
        UUID.nameUUIDFromBytes(
            "$kind:$planId:$occurrence".toByteArray(StandardCharsets.UTF_8)
        )

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
