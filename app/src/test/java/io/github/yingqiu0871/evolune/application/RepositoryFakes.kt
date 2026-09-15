package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanUpdateResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
    var beforeInsert: (suspend (DoseEvent) -> Unit)? = null
    var forcedInsertResult: InsertResult? = null
    var beforeForcedInsertResult: ((DoseEvent) -> Unit)? = null
    /**
     * Explicit channel overrides. When left null the channel answers from the **live** event map
     * (A-04), so a read after a production mutation — e.g. `HRTViewModel.deleteEvent` or the Wear
     * latest-delete — observes the current authoritative state instead of a startup snapshot.
     */
    var rangeEvents: List<DoseEvent>? = null
    var localDateRangeEvents: List<DoseEvent>? = null
    var pkEvents: List<DoseEvent>? = null

    /**
     * All-history channel override. When left null the channel answers from the live
     * event map; either way the fake **always** honours the inclusive upper bound so a
     * missing bound filter cannot hide defects (A-02-R1 lesson).
     */
    var allEventsUpTo: List<DoseEvent>? = null
    var allEventsUpperBoundFailure: Throwable? = null
    var allEventsUpperBoundCalls = 0
    var lastAllEventsUpperBound: Instant? = null

    var insertCalls = 0
    var getCalls = 0
    var lastInserted: DoseEvent? = null
    var lastRange: Pair<Instant, Instant>? = null
    var lastLocalDateRange: Pair<LocalDate, LocalDate>? = null
    var conditionalDeleteResult: ConditionalDeleteResult? = null
    var conditionalDeleteCalls = 0
    var deleteCalls = 0
    var deleteFailure: Throwable? = null
    var beforeConditionalDelete: ((UUID, Long) -> Unit)? = null
    var latestDoseDeleteCalls = 0
    var beforeLatestDoseDelete: (() -> Unit)? = null
    var latestDoseDeleteResult: LatestDoseDeleteResult? = null

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
        return rangeEvents ?: events.values.toList()
    }

    override suspend fun findRecordedLocalDateBetween(
        startInclusive: LocalDate,
        endInclusive: LocalDate
    ): List<DoseEvent> {
        lastLocalDateRange = startInclusive to endInclusive
        return localDateRangeEvents ?: events.values.toList()
    }

    override suspend fun findAllOccurredUpTo(endInclusive: Instant): List<DoseEvent> {
        allEventsUpperBoundFailure?.let { throw it }
        allEventsUpperBoundCalls += 1
        lastAllEventsUpperBound = endInclusive
        val source = allEventsUpTo ?: events.values.toList()
        return source
            .filter { !it.occurredAt.isAfter(endInclusive) }
            .sortedWith(compareBy({ it.occurredAt }, { it.id.toString() }))
    }

    override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> {
        pkFailure?.let { throw it }
        return pkEvents ?: events.values.toList()
    }

    override suspend fun insert(event: DoseEvent): InsertResult {
        insertFailure?.let { throw it }
        insertCalls += 1
        lastInserted = event
        beforeInsert?.invoke(event)
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

    /**
     * Faithful physical delete (A-04): mirrors `RoomDoseEventRepository.delete(id)` — the row is
     * removed when present, otherwise `NotFound`. `delete(id)` is the mutation the production
     * Phone delete use case (`HRTViewModel.deleteEvent`) performs.
     */
    override suspend fun delete(id: UUID): DeleteResult {
        deleteCalls += 1
        deleteFailure?.let { throw it }
        return if (events.remove(id) != null) DeleteResult.Deleted else DeleteResult.NotFound
    }

    override suspend fun deleteIfRevisionMatches(
        id: UUID,
        expectedRevision: Long
    ): ConditionalDeleteResult {
        conditionalDeleteCalls += 1
        beforeConditionalDelete?.invoke(id, expectedRevision)
        conditionalDeleteResult?.let { result ->
            if (result == ConditionalDeleteResult.Deleted) events.remove(id)
            return result
        }
        val existing = events[id] ?: return ConditionalDeleteResult.NotFound
        if (existing.revision != expectedRevision) return ConditionalDeleteResult.RevisionConflict
        events.remove(id)
        return ConditionalDeleteResult.Deleted
    }

    override suspend fun deleteLatestRecordedIfRevisionMatches(
        eventId: UUID,
        eventRevision: Long
    ): LatestDoseDeleteResult {
        latestDoseDeleteCalls += 1
        val recentBefore = io.github.yingqiu0871.evolune.wear.WearAppRecentDoseSelector.select(
            events.values.toList()
        )
        beforeLatestDoseDelete?.invoke()
        latestDoseDeleteResult?.let { result ->
            if (result == LatestDoseDeleteResult.Deleted) events.remove(eventId)
            return result
        }
        val target = events[eventId]
            ?: return LatestDoseDeleteResult.EventNotFound
        if (target.revision != eventRevision) return LatestDoseDeleteResult.EventChanged
        val recentAfter = io.github.yingqiu0871.evolune.wear.WearAppRecentDoseSelector.select(
            events.values.toList()
        )
        if (recentBefore?.id != recentAfter?.id ||
            recentAfter?.id != eventId ||
            recentAfter.revision != eventRevision
        ) {
            return LatestDoseDeleteResult.NotLatest
        }
        events.remove(eventId)
        return if (events[eventId] == null) {
            LatestDoseDeleteResult.Deleted
        } else {
            LatestDoseDeleteResult.EventChanged
        }
    }

    override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
}

internal class FakeMedicationPlanRepository(
    plans: List<MedicationPlan> = emptyList()
) : MedicationPlanRepository {
    val plans = plans.associateByTo(linkedMapOf(), MedicationPlan::id)
    var getFailure: Throwable? = null
    var observeFailure: Throwable? = null
    var getCalls = 0
    var beforeGet: (suspend (UUID) -> Unit)? = null

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
        beforeGet?.invoke(id)
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
