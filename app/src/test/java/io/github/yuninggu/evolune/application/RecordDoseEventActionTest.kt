package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.data.repository.RepositoryPersistenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class RecordDoseEventActionTest {
    private val plan = syntheticPlan()
    private val eventId = UUID(0L, 602L)

    @Test
    fun `new action inserts exactly once`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val events = FakeDoseEventRepository()

        val result = action(plans, events).executeCommand()

        assertTrue(result is RecordDoseEventActionResult.Accepted)
        assertFalse((result as RecordDoseEventActionResult.Accepted).replayed)
        assertEquals(1, events.insertCalls)
        assertEquals(eventId, events.lastInserted?.id)
    }

    @Test
    fun `same source existing event is accepted without another insert`() = runBlocking {
        val existing = event(DoseEventSource.WIDGET)
        val events = FakeDoseEventRepository(listOf(existing))

        val result = action(
            FakeMedicationPlanRepository(listOf(plan)),
            events
        ).executeCommand()

        assertTrue((result as RecordDoseEventActionResult.Accepted).replayed)
        assertEquals(existing, result.event)
        assertEquals(0, events.insertCalls)
    }

    @Test
    fun `different source collision is explicit and never overwritten`() = runBlocking {
        val existing = event(DoseEventSource.REMINDER)
        val events = FakeDoseEventRepository(listOf(existing))

        val result = action(
            FakeMedicationPlanRepository(listOf(plan)),
            events
        ).executeCommand()

        assertSame(RecordDoseEventActionResult.Conflict, result)
        assertEquals(existing, events.events[eventId])
        assertEquals(0, events.insertCalls)
    }

    @Test
    fun `insert race rereads matching source as idempotent replay`() = runBlocking {
        val events = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Conflict
            beforeForcedInsertResult = { inserted -> this.events[eventId] = inserted }
        }

        val result = action(
            FakeMedicationPlanRepository(listOf(plan)),
            events
        ).executeCommand()

        assertTrue((result as RecordDoseEventActionResult.Accepted).replayed)
        assertEquals(1, events.insertCalls)
        assertEquals(2, events.getCalls)
    }

    @Test
    fun `missing and disabled plans are distinct rejections`() = runBlocking {
        val events = FakeDoseEventRepository()
        assertSame(
            RecordDoseEventActionResult.PlanNotFound,
            action(FakeMedicationPlanRepository(), events).executeCommand()
        )
        val disabled = plan.copy(isEnabled = false)
        assertSame(
            RecordDoseEventActionResult.PlanDisabled,
            action(FakeMedicationPlanRepository(listOf(disabled)), events)
                .executeCommand(requireEnabled = true)
        )
        assertEquals(0, events.insertCalls)
    }

    @Test
    fun `invalid event identity and repository results remain explicit`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val invalidIdentity = action(plans, FakeDoseEventRepository()).execute(
            planId = plan.id,
            eventId = eventId,
            source = DoseEventSource.WIDGET,
            requireEnabledPlan = true
        ) { event(DoseEventSource.WIDGET).copy(id = UUID(0L, 999L)) }
        assertSame(RecordDoseEventActionResult.Invalid, invalidIdentity)

        val invalidInsert = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Invalid
        }
        assertSame(
            RecordDoseEventActionResult.Invalid,
            action(plans, invalidInsert).executeCommand()
        )
    }

    @Test
    fun `storage and unexpected failures never fall back`() = runBlocking {
        val storage = FakeDoseEventRepository().apply {
            getFailure = RepositoryPersistenceException("synthetic read")
        }
        assertSame(
            RecordDoseEventActionResult.StorageFailure,
            action(FakeMedicationPlanRepository(listOf(plan)), storage).executeCommand()
        )
        val unexpected = FakeDoseEventRepository().apply {
            getFailure = IllegalStateException("synthetic failure")
        }
        assertSame(
            RecordDoseEventActionResult.UnexpectedFailure,
            action(FakeMedicationPlanRepository(listOf(plan)), unexpected).executeCommand()
        )
        assertEquals(0, storage.insertCalls)
        assertEquals(0, unexpected.insertCalls)
    }

    @Test
    fun `cancellation keeps cancellation semantics`() {
        val events = FakeDoseEventRepository().apply {
            getFailure = CancellationException("synthetic cancellation")
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                action(FakeMedicationPlanRepository(listOf(plan)), events).executeCommand()
            }
        }
    }

    private fun action(
        plans: FakeMedicationPlanRepository,
        events: FakeDoseEventRepository
    ) = RecordDoseEventAction(plans, events)

    private suspend fun RecordDoseEventAction.executeCommand(
        requireEnabled: Boolean = true
    ): RecordDoseEventActionResult = execute(
        planId = plan.id,
        eventId = eventId,
        source = DoseEventSource.WIDGET,
        requireEnabledPlan = requireEnabled,
        createEvent = { event(DoseEventSource.WIDGET) }
    )

    private fun event(source: DoseEventSource): DoseEvent = DoseEvent(
        id = eventId,
        route = plan.route,
        occurredAt = Instant.parse("2027-01-15T08:30:00Z"),
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        source = source
    )
}
