package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.experience.wear.WearAppCommandType
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class WearAppConfirmationHandlerTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-08-30T07:00:00Z")
    private val plan = syntheticPlan()
    private val producer = WearAppProducerIdentity(UUID(0L, 777L), 3L)
    private val journal = InMemoryWearAppConfirmationJournal()
    private val occurrence: MedicationOccurrence = MedicationOccurrenceGenerator.generate(
        schedules = listOf(plan.toMedicationSchedule()),
        window = OccurrenceGenerationWindow(
            LocalDate.of(2026, 8, 30).atStartOfDay(zone).toInstant(),
            LocalDate.of(2026, 8, 31).atStartOfDay(zone).toInstant()
        ),
        zoneId = zone
    ).single()

    @Test
    fun `first operation writes exact current plan event and replay returns same result`() = runBlocking {
        val events = FakeDoseEventRepository()
        val handler = handler(events)

        val first = handler.handle(command())
        val replay = handler.handle(command())

        assertEquals(WearAppConfirmResultType.CONFIRMED, first.resultType)
        assertEquals(first, replay)
        assertEquals(1, events.events.size)
        assertEquals(1, events.insertCalls)
        val event = events.events.values.single()
        assertEquals(first.eventId, event.id)
        assertEquals(plan.route, event.route)
        assertEquals(plan.doseMG, event.doseMG, 0.0)
        assertEquals(plan.ester, event.ester)
        assertEquals(plan.extras, event.extras)
        assertEquals(occurrence.slotId, event.slotId)
        assertEquals(occurrence.scheduledLocalDateTime.toLocalDate(), event.localDate)
        assertEquals(now, event.occurredAt)
        assertEquals(DoseEventSource.WEAR, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
    }

    @Test
    fun `different operations competing for one occurrence create one event`() = runBlocking {
        val events = FakeDoseEventRepository()
        val handler = handler(events)

        coroutineScope {
            val first = async { handler.handle(command()) }
            val second = async {
                handler.handle(
                    command(UUID(0L, 202L))
                )
            }
            val results = listOf(first.await(), second.await())
            assertEquals(1, results.count { it.resultType == WearAppConfirmResultType.CONFIRMED })
            assertEquals(1, results.count { it.resultType == WearAppConfirmResultType.ALREADY_CONFIRMED })
            assertEquals(results.firstNotNullOf { it.eventId }, results.last { it.eventId != null }.eventId)
        }
        assertEquals(1, events.events.size)
        assertEquals(1, events.insertCalls)
    }

    @Test
    fun `same operation with changed payload is a conflict and cannot create another event`() = runBlocking {
        val events = FakeDoseEventRepository()
        val handler = handler(events)

        assertEquals(WearAppConfirmResultType.CONFIRMED, handler.handle(command()).resultType)
        val changed = command().copy(scheduledAt = command().scheduledAt.plusSeconds(60L))

        assertEquals(WearAppConfirmResultType.REJECTED_CONFLICT, handler.handle(changed).resultType)
        assertEquals(1, events.events.size)
        assertEquals(1, events.insertCalls)
    }

    @Test
    fun `stale producer and disabled plan reject without an event id`() = runBlocking {
        val stale = handler(FakeDoseEventRepository()).handle(
            command().copy(
                sourceSnapshot = WearAppSnapshotIdentity(UUID(0L, 778L), 3L, 1L)
            )
        )
        assertEquals(WearAppConfirmResultType.REJECTED_STALE_IDENTITY, stale.resultType)
        assertNull(stale.eventId)

        val disabledEvents = FakeDoseEventRepository()
        val disabled = WearAppConfirmationHandler(
            context = null,
            medicationPlans = FakeMedicationPlanRepository(listOf(plan.copy(isEnabled = false))),
            doseEvents = disabledEvents,
            clock = fixedClock(),
            zoneId = { zone },
            producerIdentity = { producer },
            operationJournal = InMemoryWearAppConfirmationJournal()
        ).handle(command())
        assertEquals(WearAppConfirmResultType.REJECTED_PLAN_DISABLED, disabled.resultType)
        assertNull(disabled.eventId)
        assertEquals(0, disabledEvents.insertCalls)
    }

    @Test
    fun `existing exact legacy window and safe same-day events are already confirmed`() = runBlocking {
        val existingEvents = listOf(
            event(UUID(0L, 801L), slotId = occurrence.slotId, localDate = occurrence.scheduledLocalDateTime.toLocalDate()),
            event(
                UUID(0L, 802L),
                slotId = occurrence.slotId,
                localDate = null,
                occurredAt = occurrence.scheduledAt
            ),
            event(UUID(0L, 803L), slotId = null, localDate = null, occurredAt = occurrence.scheduledAt),
            event(
                UUID(0L, 804L),
                slotId = null,
                localDate = occurrence.scheduledLocalDateTime.toLocalDate(),
                occurredAt = occurrence.scheduledAt.minusSeconds(2 * 60 * 60L)
            )
        )
        existingEvents.forEach { existing ->
            val events = FakeDoseEventRepository(listOf(existing))
            val result = handler(events, InMemoryWearAppConfirmationJournal()).handle(command())
            assertEquals(WearAppConfirmResultType.ALREADY_CONFIRMED, result.resultType)
            assertEquals(existing.id, result.eventId)
            assertEquals(0, events.insertCalls)
        }
    }

    @Test
    fun `storage failure remains retryable and the same operation eventually succeeds`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan)).apply {
            getFailure = RepositoryPersistenceException("synthetic")
        }
        val events = FakeDoseEventRepository()
        val handler = WearAppConfirmationHandler(
            context = null,
            medicationPlans = plans,
            doseEvents = events,
            clock = fixedClock(),
            zoneId = { zone },
            producerIdentity = { producer },
            operationJournal = journal
        )

        assertEquals(WearAppConfirmResultType.RETRYABLE_STORAGE_FAILURE, handler.handle(command()).resultType)
        plans.getFailure = null
        assertEquals(WearAppConfirmResultType.CONFIRMED, handler.handle(command()).resultType)
        assertEquals(1, events.insertCalls)
    }

    private fun handler(
        events: FakeDoseEventRepository,
        operationJournal: InMemoryWearAppConfirmationJournal = journal
    ) = WearAppConfirmationHandler(
        context = null,
        medicationPlans = FakeMedicationPlanRepository(listOf(plan)),
        doseEvents = events,
        clock = fixedClock(),
        zoneId = { zone },
        producerIdentity = { producer },
        operationJournal = operationJournal
    )

    private fun fixedClock(): Clock = Clock.fixed(now, zone)

    private fun command(
        operationId: UUID = UUID(0L, 201L)
    ) = WearAppConfirmCommand(
        protocolVersion = 1,
        commandType = WearAppCommandType.CONFIRM_OCCURRENCE,
        operationId = operationId,
        createdAt = now.minusSeconds(60L),
        sourceSnapshot = WearAppSnapshotIdentity(
            producerInstanceId = producer.producerInstanceId,
            producerGeneration = producer.producerGeneration,
            snapshotRevision = 11L
        ),
        occurrenceId = occurrence.id.value,
        planId = occurrence.planId,
        slotId = occurrence.slotId,
        localDate = occurrence.scheduledLocalDateTime.toLocalDate(),
        scheduledAt = occurrence.scheduledAt
    )

    private fun event(
        id: UUID,
        slotId: UUID?,
        localDate: LocalDate?,
        occurredAt: Instant = now
    ) = DoseEvent(
        id = id,
        route = plan.route,
        occurredAt = occurredAt,
        zoneId = zone,
        localDate = localDate,
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        slotId = slotId,
        source = DoseEventSource.MANUAL,
        status = DoseEventStatus.RECORDED
    )
}

private class InMemoryWearAppConfirmationJournal : WearAppConfirmationOperationJournal {
    private val records = linkedMapOf<UUID, WearAppStoredConfirmation>()

    override fun read(operationId: UUID): WearAppStoredConfirmation? = records[operationId]

    override fun begin(operationId: UUID, fingerprint: String): Boolean {
        val existing = records[operationId]
        if (existing != null) return existing.fingerprint == fingerprint
        records[operationId] = WearAppStoredConfirmation(fingerprint, null)
        return true
    }

    override fun saveResult(
        operationId: UUID,
        fingerprint: String,
        result: io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResult
    ): Boolean {
        val existing = records[operationId]
        if (existing != null && existing.fingerprint != fingerprint) return false
        records[operationId] = WearAppStoredConfirmation(fingerprint, result)
        return true
    }
}
