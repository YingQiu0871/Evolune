package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class WearDoseActionHandlerTest {
    private val plan = syntheticPlan()
    private val actionId = UUID.fromString("dc206fed-7a62-4a9f-8911-22840a1152ef")
    private val recordedAt = Instant.ofEpochMilli(1_800_000_000_123L)
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `first action inserts complete event then deletes exact URI`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val events = FakeDoseEventRepository()
        val deleted = mutableListOf<String>()
        var sideEffects = 0

        val result = WearDoseActionHandler(
            medicationPlans = plans,
            doseEvents = events,
            zoneId = { zoneId },
            acceptedSideEffect = { sideEffects += 1 },
            deleteDataItem = { uri -> deleted += uri; true }
        ).handle(payload()) as WearDoseActionOutcome.Accepted

        assertEquals(RecordAcceptance.Inserted, result.acceptance)
        assertEquals(actionId, result.event.id)
        assertEquals(recordedAt, result.event.occurredAt)
        assertEquals(DoseEventSource.WEAR, result.event.source)
        assertTrue(result.dataItemDeleted)
        assertEquals(listOf(payload().dataItemUri), deleted)
        assertEquals(1, plans.getCalls)
        assertEquals(1, events.insertCalls)
        assertEquals(1, sideEffects)
    }

    @Test
    fun `accepted replay uses stored event without plan lookup`() = runBlocking {
        val existing = event()
        val plans = FakeMedicationPlanRepository()
        val events = FakeDoseEventRepository(listOf(existing))
        var deletes = 0
        val replayPayload = payload(planId = UUID(0L, 999L))

        val result = WearDoseActionHandler(
            plans,
            events,
            zoneId = { zoneId },
            deleteDataItem = { deletes += 1; true }
        ).handle(replayPayload) as WearDoseActionOutcome.Accepted

        assertEquals(RecordAcceptance.FirstAcceptedReplay, result.acceptance)
        assertEquals(existing, result.event)
        assertEquals(0, plans.getCalls)
        assertEquals(0, events.insertCalls)
        assertEquals(1, deletes)
    }

    @Test
    fun `source or occurrence conflict performs no plan lookup or deletion`() = runBlocking {
        listOf(
            event().copy(source = DoseEventSource.WIDGET),
            event().copy(occurredAt = recordedAt.plusMillis(1L))
        ).forEach { existing ->
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val events = FakeDoseEventRepository(listOf(existing))
            var deletes = 0

            val result = WearDoseActionHandler(
                plans,
                events,
                deleteDataItem = { deletes += 1; true }
            ).handle(payload())

            assertSame(WearDoseActionOutcome.Conflict, result)
            assertEquals(0, plans.getCalls)
            assertEquals(0, events.insertCalls)
            assertEquals(0, deletes)
            assertEquals(existing, events.events[actionId])
        }
    }

    @Test
    fun `invalid missing and disabled actions do not write or delete`() = runBlocking {
        val invalidEvents = FakeDoseEventRepository()
        var invalidDeletes = 0
        val invalid = WearDoseActionHandler(
            FakeMedicationPlanRepository(listOf(plan)),
            invalidEvents,
            deleteDataItem = { invalidDeletes += 1; true }
        ).handle(payload(actionId = null))
        assertSame(WearDoseActionOutcome.Invalid, invalid)
        assertEquals(0, invalidEvents.insertCalls)
        assertEquals(0, invalidDeletes)

        val missingEvents = FakeDoseEventRepository()
        var missingDeletes = 0
        val missing = WearDoseActionHandler(
            FakeMedicationPlanRepository(),
            missingEvents,
            deleteDataItem = { missingDeletes += 1; true }
        ).handle(payload())
        assertSame(WearDoseActionOutcome.PlanNotFound, missing)
        assertEquals(0, missingEvents.insertCalls)
        assertEquals(0, missingDeletes)

        val disabledEvents = FakeDoseEventRepository()
        var disabledDeletes = 0
        val disabled = WearDoseActionHandler(
            FakeMedicationPlanRepository(listOf(plan.copy(isEnabled = false))),
            disabledEvents,
            deleteDataItem = { disabledDeletes += 1; true }
        ).handle(payload())
        assertSame(WearDoseActionOutcome.PlanDisabled, disabled)
        assertEquals(0, disabledEvents.insertCalls)
        assertEquals(0, disabledDeletes)
    }

    @Test
    fun `repository idempotency remains distinct and permits deletion`() = runBlocking {
        val events = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Idempotent
        }
        var deletes = 0

        val result = WearDoseActionHandler(
            FakeMedicationPlanRepository(listOf(plan)),
            events,
            zoneId = { zoneId },
            deleteDataItem = { deletes += 1; true }
        ).handle(payload()) as WearDoseActionOutcome.Accepted

        assertEquals(RecordAcceptance.RepositoryIdempotent, result.acceptance)
        assertEquals(1, deletes)
    }

    @Test
    fun `insert conflict reread accepts only matching Wear occurrence`() = runBlocking {
        val matching = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Conflict
            beforeForcedInsertResult = { candidate -> events[actionId] = candidate }
        }
        var matchingDeletes = 0
        val accepted = WearDoseActionHandler(
            FakeMedicationPlanRepository(listOf(plan)),
            matching,
            zoneId = { zoneId },
            deleteDataItem = { matchingDeletes += 1; true }
        ).handle(payload()) as WearDoseActionOutcome.Accepted
        assertEquals(RecordAcceptance.FirstAcceptedReplay, accepted.acceptance)
        assertEquals(1, matchingDeletes)

        val conflict = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Conflict
            beforeForcedInsertResult = { candidate ->
                events[actionId] = candidate.copy(occurredAt = recordedAt.plusMillis(1L))
            }
        }
        var conflictDeletes = 0
        assertSame(
            WearDoseActionOutcome.Conflict,
            WearDoseActionHandler(
                FakeMedicationPlanRepository(listOf(plan)),
                conflict,
                zoneId = { zoneId },
                deleteDataItem = { conflictDeletes += 1; true }
            ).handle(payload())
        )
        assertEquals(0, conflictDeletes)
    }

    @Test
    fun `storage and infrastructure failure retain DataItem`() = runBlocking {
        listOf(
            RepositoryPersistenceException("synthetic Wear read"),
            IllegalStateException("synthetic Wear read")
        ).forEach { failure ->
            val events = FakeDoseEventRepository().apply { getFailure = failure }
            var deletes = 0
            val result = WearDoseActionHandler(
                FakeMedicationPlanRepository(listOf(plan)),
                events,
                deleteDataItem = { deletes += 1; true }
            ).handle(payload())
            if (failure is RepositoryPersistenceException) {
                assertSame(WearDoseActionOutcome.StorageFailure, result)
            } else {
                assertSame(WearDoseActionOutcome.UnexpectedFailure, result)
            }
            assertEquals(0, deletes)
        }
    }

    @Test
    fun `cancellation is rethrown and never deletes`() {
        val events = FakeDoseEventRepository().apply {
            getFailure = CancellationException("synthetic cancellation")
        }
        var deletes = 0

        assertThrows(CancellationException::class.java) {
            runBlocking {
                WearDoseActionHandler(
                    FakeMedicationPlanRepository(listOf(plan)),
                    events,
                    deleteDataItem = { deletes += 1; true }
                ).handle(payload())
            }
        }
        assertEquals(0, deletes)
    }

    @Test
    fun `deletion failure keeps event and restart replay retries acknowledgement`() = runBlocking {
        val events = FakeDoseEventRepository()
        val first = WearDoseActionHandler(
            FakeMedicationPlanRepository(listOf(plan)),
            events,
            zoneId = { zoneId },
            deleteDataItem = { false }
        ).handle(payload()) as WearDoseActionOutcome.Accepted
        assertEquals(RecordAcceptance.Inserted, first.acceptance)
        assertFalse(first.dataItemDeleted)

        val replayPlans = FakeMedicationPlanRepository()
        var retryDeletes = 0
        val replay = WearDoseActionHandler(
            replayPlans,
            events,
            deleteDataItem = { retryDeletes += 1; true }
        ).handle(payload(planId = UUID(0L, 999L))) as WearDoseActionOutcome.Accepted

        assertEquals(RecordAcceptance.FirstAcceptedReplay, replay.acceptance)
        assertTrue(replay.dataItemDeleted)
        assertEquals(0, replayPlans.getCalls)
        assertEquals(1, events.insertCalls)
        assertEquals(1, retryDeletes)
    }

    @Test
    fun `accepted side effect failure defers deletion without reinserting`() = runBlocking {
        val events = FakeDoseEventRepository()
        var deletes = 0
        val result = WearDoseActionHandler(
            FakeMedicationPlanRepository(listOf(plan)),
            events,
            zoneId = { zoneId },
            acceptedSideEffect = { throw IllegalStateException("synthetic Widget refresh") },
            deleteDataItem = { deletes += 1; true }
        ).handle(payload()) as WearDoseActionOutcome.Accepted

        assertEquals(RecordAcceptance.Inserted, result.acceptance)
        assertFalse(result.dataItemDeleted)
        assertEquals(1, events.insertCalls)
        assertEquals(0, deletes)
    }

    private fun payload(
        planId: UUID? = plan.id,
        actionId: UUID? = this.actionId,
        recordedAtMillis: Long? = recordedAt.toEpochMilli()
    ) = WearDoseActionPayload(
        dataItemUri = "wear://synthetic-node/hrt/dose-actions/${this.actionId}",
        planId = planId,
        actionId = actionId,
        recordedAtMillis = recordedAtMillis
    )

    private fun event(): DoseEvent = createWearDoseEvent(plan, actionId, recordedAt, zoneId)
}
