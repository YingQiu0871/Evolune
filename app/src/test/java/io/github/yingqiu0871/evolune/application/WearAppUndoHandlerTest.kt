package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommandType
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoMessageCode
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultType
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class WearAppUndoHandlerTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-08-30T09:00:00Z")
    private val producer = io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity(
        UUID(0L, 777L),
        3L
    )
    private val plan = syntheticPlan(
        id = UUID(0L, 601L),
        slots = listOf(java.time.LocalTime.of(8, 30))
    )
    private val eventId = UUID(0L, 701L)

    @Test
    fun `successful undo physically deletes the exact event and replay is idempotent`() = runBlocking {
        val events = FakeDoseEventRepository(listOf(event()))
        val journal = InMemoryUndoJournal()
        val handler = handler(events, journal)

        val first = handler.handle(command())
        val replay = handler.handle(command())

        assertEquals(WearAppUndoResultType.UNDONE, first.resultType)
        assertEquals(first, replay)
        assertNull(events.events[eventId])
        assertEquals(1, events.latestDoseDeleteCalls)
        assertEquals(0, events.conditionalDeleteCalls)
        assertEquals(WearAppUndoMessageCode.UNDONE, first.messageCode)
    }

    @Test
    fun `same operation with changed identity is a conflict`() = runBlocking {
        val events = FakeDoseEventRepository(listOf(event()))
        val journal = InMemoryUndoJournal()
        val handler = handler(events, journal)

        assertEquals(WearAppUndoResultType.UNDONE, handler.handle(command()).resultType)
        val changed = command().copy(expectedSource = "MANUAL")

        assertEquals(WearAppUndoResultType.REJECTED_CONFLICT, handler.handle(changed).resultType)
        assertEquals(1, events.latestDoseDeleteCalls)
        assertEquals(0, events.conditionalDeleteCalls)
    }

    @Test
    fun `revision or event identity changes reject before delete`() = runBlocking {
        val revisionChanged = FakeDoseEventRepository(listOf(event(revision = 5L)))
        val revisionResult = handler(revisionChanged).handle(command())
        assertEquals(WearAppUndoResultType.REJECTED_EVENT_CHANGED, revisionResult.resultType)
        assertEquals(0, revisionChanged.conditionalDeleteCalls)

        val sourceChanged = FakeDoseEventRepository(listOf(event(source = DoseEventSource.MANUAL)))
        val sourceResult = handler(sourceChanged).handle(command())
        assertEquals(WearAppUndoResultType.REJECTED_EVENT_CHANGED, sourceResult.resultType)
        assertEquals(0, sourceChanged.conditionalDeleteCalls)
    }

    @Test
    fun `missing event and non-latest event are rejected without deleting another event`() = runBlocking {
        val other = event(UUID(0L, 702L), occurredAt = now.plusSeconds(1L))
        val missingEvents = FakeDoseEventRepository(listOf(other))
        val missingResult = handler(missingEvents).handle(command())
        assertEquals(WearAppUndoResultType.REJECTED_EVENT_NOT_FOUND, missingResult.resultType)
        assertEquals(other, missingEvents.events[other.id])

        val target = event()
        val newer = event(UUID(0L, 703L), occurredAt = now.plusSeconds(1L))
        val notLatestEvents = FakeDoseEventRepository(listOf(target, newer))
        val notLatestResult = handler(notLatestEvents).handle(command())
        assertEquals(WearAppUndoResultType.REJECTED_NOT_LATEST, notLatestResult.resultType)
        assertEquals(0, notLatestEvents.conditionalDeleteCalls)
        assertTrue(notLatestEvents.events.keys.containsAll(listOf(target.id, newer.id)))
    }

    @Test
    fun `stale source identity and invalid command do not mutate storage`() = runBlocking {
        val staleEvents = FakeDoseEventRepository(listOf(event()))
        val stale = handler(staleEvents).handle(
            command().copy(sourceSnapshot = WearAppSnapshotIdentity(producer.producerInstanceId, 3L, 12L))
        )
        assertEquals(WearAppUndoResultType.REJECTED_STALE_IDENTITY, stale.resultType)
        assertEquals(0, staleEvents.conditionalDeleteCalls)

        val invalidEvents = FakeDoseEventRepository(listOf(event()))
        val invalid = handler(invalidEvents).handle(command().copy(expectedEventRevision = 0L))
        assertEquals(WearAppUndoResultType.REJECTED_INVALID, invalid.resultType)
        assertEquals(0, invalidEvents.conditionalDeleteCalls)
    }

    @Test
    fun `conditional delete conflict is surfaced as event changed`() = runBlocking {
        val events = FakeDoseEventRepository(listOf(event())).apply {
            latestDoseDeleteResult = LatestDoseDeleteResult.EventChanged
        }

        val result = handler(events).handle(command())

        assertEquals(WearAppUndoResultType.REJECTED_EVENT_CHANGED, result.resultType)
        assertEquals(1, events.latestDoseDeleteCalls)
        assertEquals(0, events.conditionalDeleteCalls)
        assertTrue(events.events.containsKey(eventId))
    }

    @Test
    fun `newer event inserted during transactional recent check prevents deleting the old recent`() = runBlocking {
        val newerId = UUID(0L, 705L)
        val newer = event(id = newerId, occurredAt = now.plusSeconds(1L), revision = 1L)
        val events = FakeDoseEventRepository(listOf(event()))
        events.beforeLatestDoseDelete = { events.events[newer.id] = newer }
        val journal = InMemoryUndoJournal()
        val undoHandler = handler(events, journal)

        val result = undoHandler.handle(command())
        val replay = undoHandler.handle(command())

        assertEquals(WearAppUndoResultType.REJECTED_NOT_LATEST, result.resultType)
        assertEquals(result, replay)
        assertTrue(events.events.containsKey(eventId))
        assertTrue(events.events.containsKey(newerId))
        assertEquals(1, events.latestDoseDeleteCalls)
        assertEquals(0, events.conditionalDeleteCalls)
    }

    @Test
    fun `repository storage failure stays retryable and cannot delete`() = runBlocking {
        val events = FakeDoseEventRepository(listOf(event())).apply {
            getFailure = RepositoryPersistenceException("synthetic undo failure")
        }

        val result = handler(events).handle(command())

        assertEquals(WearAppUndoResultType.RETRYABLE_STORAGE_FAILURE, result.resultType)
        assertEquals(0, events.conditionalDeleteCalls)
        assertTrue(events.events.containsKey(eventId))
    }

    @Test
    fun `delete success followed by journal failure recovers as already undone`() = runBlocking {
        val events = FakeDoseEventRepository(listOf(event()))
        val journal = InMemoryUndoJournal().apply { failNextSave = true }
        val handler = handler(events, journal)

        val first = handler.handle(command())
        val recovered = handler.handle(command())

        assertEquals(WearAppUndoResultType.RETRYABLE_STORAGE_FAILURE, first.resultType)
        assertEquals(WearAppUndoResultType.ALREADY_UNDONE, recovered.resultType)
        assertEquals(1, events.latestDoseDeleteCalls)
        assertEquals(0, events.conditionalDeleteCalls)
        assertNull(events.events[eventId])
        assertEquals(recovered, handler.handle(command()))
    }

    @Test
    fun `already missing result is terminal and different operation cannot delete it again`() = runBlocking {
        val events = FakeDoseEventRepository(listOf(event()))
        events.beforeLatestDoseDelete = { events.events.remove(eventId) }
        val first = handler(events).handle(command())
        val second = handler(events).handle(command(UUID(0L, 704L)))

        assertEquals(WearAppUndoResultType.REJECTED_EVENT_NOT_FOUND, first.resultType)
        assertEquals(WearAppUndoResultType.REJECTED_EVENT_NOT_FOUND, second.resultType)
        assertEquals(1, events.latestDoseDeleteCalls)
        assertEquals(0, events.conditionalDeleteCalls)
    }

    @Test
    fun `same operation is serialized and performs one physical delete`() = runBlocking {
        val events = FakeDoseEventRepository(listOf(event()))
        val journal = InMemoryUndoJournal()
        val handler = handler(events, journal)

        val results = coroutineScope {
            listOf(
                async { handler.handle(command()) },
                async { handler.handle(command()) }
            ).map { it.await() }
        }

        assertEquals(2, results.count { it.resultType == WearAppUndoResultType.UNDONE })
        assertEquals(2, results.count { it == results.first() })
        assertEquals(1, events.latestDoseDeleteCalls)
        assertEquals(0, events.conditionalDeleteCalls)
    }

    private fun handler(
        events: FakeDoseEventRepository,
        journal: InMemoryUndoJournal = InMemoryUndoJournal()
    ) = WearAppUndoHandler(
        context = null,
        doseEvents = events,
        clock = Clock.fixed(now, zone),
        producerIdentity = { producer },
        latestSnapshotRevision = { 11L },
        operationJournal = journal
    )

    private fun command(operationId: UUID = UUID(0L, 703L)) = WearAppUndoCommand(
        protocolVersion = 1,
        commandType = WearAppUndoCommandType.UNDO_RECENT_DOSE,
        operationId = operationId,
        createdAt = now.minusSeconds(60L),
        sourceSnapshot = WearAppSnapshotIdentity(producer.producerInstanceId, producer.producerGeneration, 11L),
        eventId = eventId,
        expectedEventRevision = 4L,
        expectedOccurredAt = now,
        expectedSource = DoseEventSource.WEAR.name
    )

    private fun event(
        id: UUID = eventId,
        occurredAt: Instant = now,
        revision: Long = 4L,
        source: DoseEventSource = DoseEventSource.WEAR
    ) = DoseEvent(
        id = id,
        route = Route.ORAL,
        occurredAt = occurredAt,
        zoneId = zone,
        localDate = LocalDate.of(2026, 8, 30),
        doseMG = plan.doseMG,
        ester = Ester.E2,
        extras = plan.extras,
        slotId = plan.slots.single().id,
        source = source,
        status = DoseEventStatus.RECORDED,
        revision = revision
    )
}

private class InMemoryUndoJournal : WearAppUndoOperationJournal {
    private val records = linkedMapOf<UUID, WearAppStoredUndo>()
    var failNextSave = false

    override fun read(operationId: UUID): WearAppStoredUndo? = records[operationId]

    override fun begin(operationId: UUID, fingerprint: String): Boolean {
        val existing = records[operationId]
        if (existing != null) return existing.fingerprint == fingerprint
        records[operationId] = WearAppStoredUndo(
            fingerprint = fingerprint,
            status = WearAppUndoOperationStatus.PREPARED,
            result = null
        )
        return true
    }

    override fun markDeleteInProgress(operationId: UUID, fingerprint: String): Boolean {
        val existing = records[operationId] ?: return false
        if (existing.fingerprint != fingerprint) return false
        records[operationId] = existing.copy(status = WearAppUndoOperationStatus.DELETE_IN_PROGRESS)
        return true
    }

    override fun saveResult(
        operationId: UUID,
        fingerprint: String,
        result: WearAppUndoResult
    ): Boolean {
        if (failNextSave) {
            failNextSave = false
            return false
        }
        val existing = records[operationId]
        if (existing != null && existing.fingerprint != fingerprint) return false
        records[operationId] = WearAppStoredUndo(
            fingerprint = fingerprint,
            status = WearAppUndoOperationStatus.DELETE_IN_PROGRESS,
            result = result
        )
        return true
    }
}
