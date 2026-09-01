package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.experience.wear.WearAppCommandType
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity
import io.github.yingqiu0871.evolune.widget.ContractWidgetQuickActionWork
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionCommand
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionOutcome
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionSideEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class CrossEntryOccurrenceConcurrencyTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-08-30T07:00:00Z")
    private val plan = syntheticPlan()
    private val producer = WearAppProducerIdentity(UUID(0L, 777L), 3L)
    private val occurrence: MedicationOccurrence = MedicationOccurrenceGenerator.generate(
        schedules = listOf(plan.toMedicationSchedule()),
        window = OccurrenceGenerationWindow(
            LocalDate.of(2026, 8, 30).atStartOfDay(zone).toInstant(),
            LocalDate.of(2026, 8, 31).atStartOfDay(zone).toInstant()
        ),
        zoneId = zone
    ).single()

    @Test
    fun `Wear read followed by Widget insert still leaves one event`() = runBlocking {
        val wearWriteGateReached = CompletableDeferred<Unit>()
        val allowWearToWrite = CompletableDeferred<Unit>()
        val events = GatedDoseEventRepository(
            gatedEventId = wearAppConfirmationEventId(wearCommand().operationId),
            gateReached = wearWriteGateReached,
            allowGatedRead = allowWearToWrite
        )
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val handler = WearAppConfirmationHandler(
            context = null,
            medicationPlans = plans,
            doseEvents = events,
            clock = Clock.fixed(now, zone),
            zoneId = { zone },
            producerIdentity = { producer },
            operationJournal = TestConfirmationJournal()
        )

        val wear = async { handler.handle(wearCommand()) }
        wearWriteGateReached.await()

        val widget = async {
            ContractWidgetQuickActionWork(
                medicationPlans = plans,
                doseEvents = events,
                sideEffects = NoOpWidgetSideEffects,
                clock = Clock.fixed(now, zone),
                zoneId = { zone }
            ).handle(widgetCommand())
        }
        allowWearToWrite.complete(Unit)
        val widgetResult = widget.await()
        assertEquals(WidgetQuickActionOutcome.Accepted(true), widgetResult)
        val wearResult = wear.await()

        assertEquals(1, events.events.size)
        assertEquals(1, events.insertCalls)
        assertEquals(WearAppConfirmResultType.CONFIRMED, wearResult.resultType)
        assertEquals(wearAppConfirmationEventId(wearCommand().operationId), wearResult.eventId)
    }

    @Test
    fun `Widget read followed by Wear command still leaves Widget as the winner`() = runBlocking {
        val widgetReadGateReached = CompletableDeferred<Unit>()
        val allowWidgetToContinue = CompletableDeferred<Unit>()
        val events = GatedDoseEventRepository(
            gatedEventId = widgetEventId(),
            gateReached = widgetReadGateReached,
            allowGatedRead = allowWidgetToContinue
        )
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val widget = async {
            ContractWidgetQuickActionWork(
                medicationPlans = plans,
                doseEvents = events,
                sideEffects = NoOpWidgetSideEffects,
                clock = Clock.fixed(now, zone),
                zoneId = { zone }
            ).handle(widgetCommand())
        }
        widgetReadGateReached.await()

        val wear = async {
            WearAppConfirmationHandler(
                context = null,
                medicationPlans = plans,
                doseEvents = events,
                clock = Clock.fixed(now, zone),
                zoneId = { zone },
                producerIdentity = { producer },
                operationJournal = TestConfirmationJournal()
            ).handle(wearCommand())
        }
        allowWidgetToContinue.complete(Unit)

        assertEquals(WidgetQuickActionOutcome.Accepted(false), widget.await())
        val wearResult = wear.await()
        assertEquals(1, events.events.size)
        assertEquals(1, events.insertCalls)
        assertEquals(WearAppConfirmResultType.ALREADY_CONFIRMED, wearResult.resultType)
        assertEquals(widgetEventId(), wearResult.eventId)
    }

    @Test
    fun `different occurrences can confirm concurrently without conflicting`() = runBlocking {
        val twoSlotPlan = syntheticPlan(
            slots = listOf(java.time.LocalTime.of(8, 30), java.time.LocalTime.of(17, 0))
        )
        val occurrences = MedicationOccurrenceGenerator.generate(
            schedules = listOf(twoSlotPlan.toMedicationSchedule()),
            window = OccurrenceGenerationWindow(
                LocalDate.of(2026, 8, 30).atStartOfDay(zone).toInstant(),
                LocalDate.of(2026, 8, 31).atStartOfDay(zone).toInstant()
            ),
            zoneId = zone
        ).filter { it.scheduledLocalDateTime.toLocalDate() == LocalDate.of(2026, 8, 30) }
        val events = FakeDoseEventRepository()
        val plans = FakeMedicationPlanRepository(listOf(twoSlotPlan))
        val handler = WearAppConfirmationHandler(
            context = null,
            medicationPlans = plans,
            doseEvents = events,
            clock = Clock.fixed(now, zone),
            zoneId = { zone },
            producerIdentity = { producer },
            operationJournal = TestConfirmationJournal()
        )

        val results = coroutineScope {
            occurrences.mapIndexed { index, target ->
                async {
                    handler.handle(
                        wearCommand(
                            target,
                            UUID(0L, 910L + index)
                        )
                    )
                }
            }.awaitAll()
        }

        assertEquals(2, results.count { it.resultType == WearAppConfirmResultType.CONFIRMED })
        assertEquals(2, events.events.size)
        assertEquals(2, events.insertCalls)
        assertEquals(occurrences.map { it.slotId }.toSet(), events.events.values.map { it.slotId }.toSet())
    }

    private fun wearCommand(
        target: MedicationOccurrence = occurrence,
        operationId: UUID = UUID(0L, 901L)
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
        occurrenceId = target.id.value,
        planId = target.planId,
        slotId = target.slotId,
        localDate = target.scheduledLocalDateTime.toLocalDate(),
        scheduledAt = target.scheduledAt
    )

    private fun widgetCommand(target: MedicationOccurrence = occurrence) = WidgetQuickActionCommand(
        planId = target.planId.toString(),
        slotId = target.slotId.toString(),
        scheduledLocalDate = target.scheduledLocalDateTime.toLocalDate().toString(),
        occurrenceId = target.id.value.toString()
    )

    private fun widgetEventId(target: MedicationOccurrence = occurrence): UUID =
        widgetOccurrenceActionEventId(target.id.value)
}

private object NoOpWidgetSideEffects : WidgetQuickActionSideEffects {
    override suspend fun refreshWidgets() = Unit
    override suspend fun showRecorded(planName: String) = Unit
}

private class GatedDoseEventRepository(
    private val gatedEventId: UUID,
    private val gateReached: CompletableDeferred<Unit>,
    private val allowGatedRead: CompletableDeferred<Unit>
) : DoseEventRepository {
    val events = linkedMapOf<UUID, DoseEvent>()
    val insertCalls get() = insertCount.get()
    private val insertCount = AtomicInteger()

    override fun observeAll(): Flow<List<DoseEvent>> = flowOf(events.values.toList())

    override suspend fun getById(id: UUID): DoseEvent? {
        if (id == gatedEventId) {
            gateReached.complete(Unit)
            allowGatedRead.await()
        }
        return events[id]
    }

    override suspend fun findOccurredBetween(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<DoseEvent> = emptyList()

    override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = emptyList()

    override suspend fun insert(event: DoseEvent): InsertResult {
        insertCount.incrementAndGet()
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

    override suspend fun deleteIfRevisionMatches(
        id: UUID,
        expectedRevision: Long
    ): ConditionalDeleteResult = ConditionalDeleteResult.NotFound

    override suspend fun deleteLatestRecordedIfRevisionMatches(
        eventId: UUID,
        eventRevision: Long
    ): LatestDoseDeleteResult = LatestDoseDeleteResult.NotLatest

    override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
}

private class TestConfirmationJournal : WearAppConfirmationOperationJournal {
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
        records[operationId] = WearAppStoredConfirmation(fingerprint, result)
        return true
    }
}
