package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.data.repository.RepositoryStorageException
import kotlinx.coroutines.CancellationException
import java.util.UUID

internal sealed interface RecordDoseEventActionResult {
    data class Accepted(
        val plan: MedicationPlan,
        val event: DoseEvent,
        val replayed: Boolean
    ) : RecordDoseEventActionResult

    data object PlanNotFound : RecordDoseEventActionResult
    data object PlanDisabled : RecordDoseEventActionResult
    data object Conflict : RecordDoseEventActionResult
    data object Invalid : RecordDoseEventActionResult
    data object StorageFailure : RecordDoseEventActionResult
    data object UnexpectedFailure : RecordDoseEventActionResult
}

internal class RecordDoseEventAction(
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository
) {
    suspend fun execute(
        planId: UUID,
        eventId: UUID,
        source: DoseEventSource,
        requireEnabledPlan: Boolean,
        createEvent: (MedicationPlan) -> DoseEvent
    ): RecordDoseEventActionResult = try {
        val plan = medicationPlans.getById(planId)
            ?: return RecordDoseEventActionResult.PlanNotFound
        if (requireEnabledPlan && !plan.isEnabled) {
            return RecordDoseEventActionResult.PlanDisabled
        }

        val existing = doseEvents.getById(eventId)
        if (existing != null) {
            return existing.toReplayResult(plan, source)
        }

        val event = createEvent(plan)
        if (event.id != eventId || event.source != source) {
            return RecordDoseEventActionResult.Invalid
        }
        when (doseEvents.insert(event)) {
            InsertResult.Inserted -> RecordDoseEventActionResult.Accepted(
                plan = plan,
                event = event,
                replayed = false
            )
            InsertResult.Idempotent -> RecordDoseEventActionResult.Accepted(
                plan = plan,
                event = event,
                replayed = true
            )
            InsertResult.Conflict -> doseEvents.getById(eventId)
                ?.toReplayResult(plan, source)
                ?: RecordDoseEventActionResult.Conflict
            InsertResult.Invalid -> RecordDoseEventActionResult.Invalid
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RepositoryStorageException) {
        RecordDoseEventActionResult.StorageFailure
    } catch (_: Throwable) {
        RecordDoseEventActionResult.UnexpectedFailure
    }

    private fun DoseEvent.toReplayResult(
        plan: MedicationPlan,
        expectedSource: DoseEventSource
    ): RecordDoseEventActionResult = if (source == expectedSource) {
        RecordDoseEventActionResult.Accepted(
            plan = plan,
            event = this,
            replayed = true
        )
    } else {
        RecordDoseEventActionResult.Conflict
    }
}
