package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.data.repository.RepositoryStorageException
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.UUID

internal enum class RecordAcceptance {
    Inserted,
    RepositoryIdempotent,
    FirstAcceptedReplay
}

internal sealed interface ExistingEventPolicy {
    data object RepositoryStrict : ExistingEventPolicy

    data class FirstAcceptedBySource(
        val expectedSource: DoseEventSource
    ) : ExistingEventPolicy

    data class FirstAcceptedBySourceAndOccurredAt(
        val expectedSource: DoseEventSource,
        val expectedOccurredAt: Instant
    ) : ExistingEventPolicy
}

internal sealed interface RecordDoseEventActionResult {
    data class Accepted(
        val plan: MedicationPlan?,
        val event: DoseEvent,
        val acceptance: RecordAcceptance
    ) : RecordDoseEventActionResult

    data object PlanNotFound : RecordDoseEventActionResult
    data object PlanDisabled : RecordDoseEventActionResult
    data object Conflict : RecordDoseEventActionResult
    data object Invalid : RecordDoseEventActionResult
    data object StorageFailure : RecordDoseEventActionResult
    data object UnexpectedFailure : RecordDoseEventActionResult
}

internal class RecordDoseEventEngine(
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository
) {
    suspend fun execute(
        planId: UUID,
        eventId: UUID,
        expectedSource: DoseEventSource,
        requireEnabledPlan: Boolean,
        policy: ExistingEventPolicy,
        createEvent: suspend (MedicationPlan) -> DoseEvent
    ): RecordDoseEventActionResult = try {
        when (policy) {
            ExistingEventPolicy.RepositoryStrict -> executeStrict(
                planId,
                eventId,
                expectedSource,
                requireEnabledPlan,
                createEvent
            )
            is ExistingEventPolicy.FirstAcceptedBySource -> {
                if (policy.expectedSource != expectedSource) {
                    RecordDoseEventActionResult.Invalid
                } else {
                    executeFirstAcceptedBySource(
                        planId,
                        eventId,
                        expectedSource,
                        requireEnabledPlan,
                        createEvent
                    )
                }
            }
            is ExistingEventPolicy.FirstAcceptedBySourceAndOccurredAt -> {
                if (policy.expectedSource != expectedSource) {
                    RecordDoseEventActionResult.Invalid
                } else {
                    executeFirstAcceptedBySourceAndOccurredAt(
                        planId,
                        eventId,
                        expectedSource,
                        policy.expectedOccurredAt,
                        requireEnabledPlan,
                        createEvent
                    )
                }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RepositoryStorageException) {
        RecordDoseEventActionResult.StorageFailure
    } catch (_: Throwable) {
        RecordDoseEventActionResult.UnexpectedFailure
    }

    private suspend fun executeStrict(
        planId: UUID,
        eventId: UUID,
        expectedSource: DoseEventSource,
        requireEnabledPlan: Boolean,
        createEvent: suspend (MedicationPlan) -> DoseEvent
    ): RecordDoseEventActionResult {
        val plan = loadPlan(planId)
            ?: return RecordDoseEventActionResult.PlanNotFound
        if (requireEnabledPlan && !plan.isEnabled) {
            return RecordDoseEventActionResult.PlanDisabled
        }
        val event = createEvent(plan)
        if (!event.matchesIdentity(eventId, expectedSource)) {
            return RecordDoseEventActionResult.Invalid
        }
        return insert(event, plan)
    }

    private suspend fun executeFirstAcceptedBySource(
        planId: UUID,
        eventId: UUID,
        expectedSource: DoseEventSource,
        requireEnabledPlan: Boolean,
        createEvent: suspend (MedicationPlan) -> DoseEvent
    ): RecordDoseEventActionResult {
        val plan = loadPlan(planId)
            ?: return RecordDoseEventActionResult.PlanNotFound
        if (requireEnabledPlan && !plan.isEnabled) {
            return RecordDoseEventActionResult.PlanDisabled
        }
        val existing = doseEvents.getById(eventId)
        if (existing != null) {
            return existing.firstAcceptedResult(plan, expectedSource)
        }

        val event = createEvent(plan)
        if (!event.matchesIdentity(eventId, expectedSource)) {
            return RecordDoseEventActionResult.Invalid
        }
        return when (val result = doseEvents.insert(event)) {
            InsertResult.Inserted -> accepted(plan, event, RecordAcceptance.Inserted)
            InsertResult.Idempotent -> accepted(
                plan,
                event,
                RecordAcceptance.RepositoryIdempotent
            )
            InsertResult.Conflict -> doseEvents.getById(eventId)
                ?.firstAcceptedResult(plan, expectedSource)
                ?: RecordDoseEventActionResult.Conflict
            InsertResult.Invalid -> RecordDoseEventActionResult.Invalid
        }
    }

    private suspend fun executeFirstAcceptedBySourceAndOccurredAt(
        planId: UUID,
        eventId: UUID,
        expectedSource: DoseEventSource,
        expectedOccurredAt: Instant,
        requireEnabledPlan: Boolean,
        createEvent: suspend (MedicationPlan) -> DoseEvent
    ): RecordDoseEventActionResult {
        val existing = doseEvents.getById(eventId)
        if (existing != null) {
            return existing.firstAcceptedResult(expectedSource, expectedOccurredAt)
        }

        val plan = loadPlan(planId)
            ?: return RecordDoseEventActionResult.PlanNotFound
        if (requireEnabledPlan && !plan.isEnabled) {
            return RecordDoseEventActionResult.PlanDisabled
        }
        val event = createEvent(plan)
        if (!event.matchesIdentity(eventId, expectedSource) ||
            event.occurredAt != expectedOccurredAt
        ) {
            return RecordDoseEventActionResult.Invalid
        }
        return when (val result = doseEvents.insert(event)) {
            InsertResult.Inserted -> accepted(plan, event, RecordAcceptance.Inserted)
            InsertResult.Idempotent -> accepted(
                plan,
                event,
                RecordAcceptance.RepositoryIdempotent
            )
            InsertResult.Conflict -> doseEvents.getById(eventId)
                ?.firstAcceptedResult(expectedSource, expectedOccurredAt)
                ?: RecordDoseEventActionResult.Conflict
            InsertResult.Invalid -> RecordDoseEventActionResult.Invalid
        }
    }

    private suspend fun loadPlan(planId: UUID): MedicationPlan? =
        medicationPlans.getById(planId)

    private suspend fun insert(
        event: DoseEvent,
        plan: MedicationPlan
    ): RecordDoseEventActionResult = when (doseEvents.insert(event)) {
        InsertResult.Inserted -> accepted(plan, event, RecordAcceptance.Inserted)
        InsertResult.Idempotent -> accepted(
            plan,
            event,
            RecordAcceptance.RepositoryIdempotent
        )
        InsertResult.Conflict -> RecordDoseEventActionResult.Conflict
        InsertResult.Invalid -> RecordDoseEventActionResult.Invalid
    }

    private fun DoseEvent.firstAcceptedResult(
        plan: MedicationPlan,
        expectedSource: DoseEventSource
    ): RecordDoseEventActionResult = if (source == expectedSource) {
        accepted(plan, this, RecordAcceptance.FirstAcceptedReplay)
    } else {
        RecordDoseEventActionResult.Conflict
    }

    private fun DoseEvent.firstAcceptedResult(
        expectedSource: DoseEventSource,
        expectedOccurredAt: Instant
    ): RecordDoseEventActionResult = if (
        source == expectedSource && occurredAt == expectedOccurredAt
    ) {
        accepted(null, this, RecordAcceptance.FirstAcceptedReplay)
    } else {
        RecordDoseEventActionResult.Conflict
    }

    private fun DoseEvent.matchesIdentity(
        expectedId: UUID,
        expectedSource: DoseEventSource
    ): Boolean = id == expectedId && source == expectedSource

    private fun accepted(
        plan: MedicationPlan?,
        event: DoseEvent,
        acceptance: RecordAcceptance
    ): RecordDoseEventActionResult.Accepted = RecordDoseEventActionResult.Accepted(
        plan = plan,
        event = event,
        acceptance = acceptance
    )
}
