package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
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
        occurrenceId: UUID,
        validatePlan: (MedicationPlan) -> Boolean = { true },
        createEvent: suspend (MedicationPlan, UUID) -> DoseEvent
    ): RecordDoseEventActionResult {
        val eventId = widgetOccurrenceActionEventId(occurrenceId)
        return engine.execute(
            planId = planId,
            eventId = eventId,
            expectedSource = DoseEventSource.WIDGET,
            requireEnabledPlan = true,
            policy = ExistingEventPolicy.FirstAcceptedBySource(DoseEventSource.WIDGET),
            validatePlan = validatePlan,
            createEvent = { plan -> createEvent(plan, eventId) }
        )
    }

    private fun localEventId(kind: String, planId: UUID, occurrence: Long): UUID =
        UUID.nameUUIDFromBytes(
            "$kind:$planId:$occurrence".toByteArray(StandardCharsets.UTF_8)
        )

}

internal fun widgetOccurrenceActionEventId(occurrenceId: UUID): UUID =
    UUID.nameUUIDFromBytes(
        "widget-occurrence-action:v1:$occurrenceId".toByteArray(StandardCharsets.UTF_8)
    )
