package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.dataapi.DeleteResult
import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.core.dataapi.PlanSaveResult
import io.github.yuninggu.evolune.core.dataapi.PlanUpdateResult
import io.github.yuninggu.evolune.core.dataapi.UpdateResult
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlot
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

internal class FakeDoseEventRepository(
    initialEvents: List<DoseEvent> = emptyList()
) : DoseEventRepository {
    val events = initialEvents.associateByTo(linkedMapOf(), DoseEvent::id)
    var getFailure: Throwable? = null
    var rangeFailure: Throwable? = null
    var pkFailure: Throwable? = null
    var insertFailure: Throwable? = null
    var forcedInsertResult: InsertResult? = null
    var beforeForcedInsertResult: ((DoseEvent) -> Unit)? = null
    var rangeEvents: List<DoseEvent> = initialEvents
    var pkEvents: List<DoseEvent> = initialEvents
    var insertCalls = 0
    var getCalls = 0
    var lastInserted: DoseEvent? = null
    var lastRange: Pair<Instant, Instant>? = null

    override fun observeAll(): Flow<List<DoseEvent>> = flowOf(events.values.toList())

    override suspend fun getById(id: UUID): DoseEvent? {
        getFailure?.let { throw it }
        getCalls += 1
        return events[id]
    }

    override suspend fun findOccurredBetween(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<DoseEvent> {
        rangeFailure?.let { throw it }
        lastRange = startInclusive to endExclusive
        return rangeEvents
    }

    override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> {
        pkFailure?.let { throw it }
        return pkEvents
    }

    override suspend fun insert(event: DoseEvent): InsertResult {
        insertFailure?.let { throw it }
        insertCalls += 1
        lastInserted = event
        forcedInsertResult?.let {
            beforeForcedInsertResult?.invoke(event)
            return it
        }
        val existing = events[event.id]
        return when {
            existing == null -> {
                events[event.id] = event
                InsertResult.Inserted
            }
            existing == event -> InsertResult.Idempotent
            else -> InsertResult.Conflict
        }
    }

    override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult =
        UpdateResult.Invalid

    override suspend fun delete(id: UUID): DeleteResult = DeleteResult.NotFound

    override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
}

internal class FakeMedicationPlanRepository(
    plans: List<MedicationPlan> = emptyList()
) : MedicationPlanRepository {
    val plans = plans.associateByTo(linkedMapOf(), MedicationPlan::id)
    var getFailure: Throwable? = null
    var observeFailure: Throwable? = null
    var getCalls = 0

    override fun observeAll(): Flow<List<MedicationPlan>> {
        observeFailure?.let { throw it }
        return flowOf(plans.values.toList())
    }

    override fun observeEnabled(): Flow<List<MedicationPlan>> {
        observeFailure?.let { throw it }
        return flowOf(plans.values.filter(MedicationPlan::isEnabled))
    }

    override suspend fun getById(id: UUID): MedicationPlan? {
        getFailure?.let { throw it }
        getCalls += 1
        return plans[id]
    }

    override suspend fun save(plan: MedicationPlan): PlanSaveResult = PlanSaveResult.Invalid

    override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult =
        PlanUpdateResult.Invalid

    override suspend fun delete(id: UUID): DeleteResult = DeleteResult.NotFound

    override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
}

internal fun syntheticPlan(
    id: UUID = UUID(0L, 601L),
    enabled: Boolean = true,
    scheduleType: ScheduleType = ScheduleType.DAILY,
    slots: List<LocalTime> = listOf(LocalTime.of(8, 30))
): MedicationPlan = MedicationPlan(
    id = id,
    name = "Synthetic plan",
    route = Route.ORAL,
    ester = Ester.E2,
    doseMG = 2.0,
    scheduleType = scheduleType,
    slots = slots.mapIndexed { position, time ->
        ScheduledDoseSlot(
            id = UUID(1L, position.toLong()),
            planId = id,
            localTime = time,
            position = position
        )
    },
    daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
    intervalDays = 3,
    isEnabled = enabled,
    extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
    createdAt = Instant.parse("2024-01-02T03:04:05Z")
)
