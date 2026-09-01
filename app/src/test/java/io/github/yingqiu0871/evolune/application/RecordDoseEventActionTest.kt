package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import io.github.yingqiu0871.evolune.reminder.reminderDoseEventId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class RecordDoseEventActionTest {
    private val plan = syntheticPlan()
    private val eventId = UUID(0L, 602L)
    private val widgetOccurrenceId = UUID(0L, 603L)
    private val occurredAt = Instant.parse("2027-01-15T08:30:00Z")

    @Test
    fun `RepositoryStrict maps repository outcomes without pre-read or conflict reinterpretation`() =
        runBlocking {
            val insertedEvents = FakeDoseEventRepository()
            assertAcceptance(
                strict(FakeMedicationPlanRepository(listOf(plan)), insertedEvents),
                RecordAcceptance.Inserted
            )
            assertEquals(0, insertedEvents.getCalls)
            assertEquals(1, insertedEvents.insertCalls)

            val candidate = event(DoseEventSource.MANUAL)
            val idempotentEvents = FakeDoseEventRepository(listOf(candidate))
            assertAcceptance(
                strict(
                    FakeMedicationPlanRepository(listOf(plan)),
                    idempotentEvents,
                    candidate
                ),
                RecordAcceptance.RepositoryIdempotent
            )
            assertEquals(0, idempotentEvents.getCalls)

            val conflictEvents = FakeDoseEventRepository(
                listOf(candidate.copy(occurredAt = occurredAt.plusSeconds(1)))
            )
            assertSame(
                RecordDoseEventActionResult.Conflict,
                strict(FakeMedicationPlanRepository(listOf(plan)), conflictEvents, candidate)
            )
            assertEquals(0, conflictEvents.getCalls)
            assertEquals(1, conflictEvents.insertCalls)

            val invalidEvents = FakeDoseEventRepository().apply {
                forcedInsertResult = InsertResult.Invalid
            }
            assertSame(
                RecordDoseEventActionResult.Invalid,
                strict(FakeMedicationPlanRepository(listOf(plan)), invalidEvents, candidate)
            )
            assertEquals(0, invalidEvents.getCalls)
        }

    @Test
    fun `RepositoryStrict rejects invalid identity and policy mismatch before insert`() = runBlocking {
        val invalidIdentityEvents = FakeDoseEventRepository()
        assertSame(
            RecordDoseEventActionResult.Invalid,
            strict(
                FakeMedicationPlanRepository(listOf(plan)),
                invalidIdentityEvents,
                event(DoseEventSource.MANUAL).copy(id = UUID(0L, 999L))
            )
        )
        assertEquals(0, invalidIdentityEvents.insertCalls)
        assertEquals(0, invalidIdentityEvents.getCalls)

        val plans = FakeMedicationPlanRepository(listOf(plan))
        var materializerCalls = 0
        val mismatch = engine(plans, FakeDoseEventRepository()).execute(
            planId = plan.id,
            eventId = eventId,
            expectedSource = DoseEventSource.MANUAL,
            requireEnabledPlan = true,
            policy = ExistingEventPolicy.FirstAcceptedBySource(DoseEventSource.WIDGET)
        ) {
            materializerCalls += 1
            event(DoseEventSource.MANUAL)
        }
        assertSame(RecordDoseEventActionResult.Invalid, mismatch)
        assertEquals(0, plans.getCalls)
        assertEquals(0, materializerCalls)
    }

    @Test
    fun `RepositoryStrict classifies storage exception and cancellation without fallback`() {
        val storage = FakeDoseEventRepository().apply {
            insertFailure = RepositoryPersistenceException("synthetic insert")
        }
        assertSame(
            RecordDoseEventActionResult.StorageFailure,
            runBlocking { strict(FakeMedicationPlanRepository(listOf(plan)), storage) }
        )
        val unexpected = FakeDoseEventRepository().apply {
            insertFailure = IllegalStateException("synthetic insert")
        }
        assertSame(
            RecordDoseEventActionResult.UnexpectedFailure,
            runBlocking { strict(FakeMedicationPlanRepository(listOf(plan)), unexpected) }
        )
        val cancelled = FakeDoseEventRepository().apply {
            insertFailure = CancellationException("synthetic cancellation")
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { strict(FakeMedicationPlanRepository(listOf(plan)), cancelled) }
        }
        assertEquals(0, storage.getCalls)
        assertEquals(0, unexpected.getCalls)
        assertEquals(0, cancelled.getCalls)
    }

    @Test
    fun `Reminder facade derives trusted ID and preserves delayed first accepted event`() =
        runBlocking {
            val scheduledAtMillis = 1_800_000_000_000L
            val expectedId = reminderDoseEventId(plan.id, scheduledAtMillis)
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val events = FakeDoseEventRepository()
            val recorder = LocalActionRecorder(plans, events)

            val first = recorder.recordReminder(plan.id, scheduledAtMillis) { _, derivedId ->
                event(DoseEventSource.REMINDER, id = derivedId)
            }
            val firstAccepted = assertAcceptance(first, RecordAcceptance.Inserted)
            assertEquals(expectedId, firstAccepted.event.id)

            var replayMaterializerCalls = 0
            val delayed = recorder.recordReminder(plan.id, scheduledAtMillis) { _, derivedId ->
                replayMaterializerCalls += 1
                event(
                    DoseEventSource.REMINDER,
                    occurredAt = occurredAt.plusSeconds(30),
                    id = derivedId
                )
            }
            val replay = assertAcceptance(delayed, RecordAcceptance.FirstAcceptedReplay)
            assertEquals(firstAccepted.event, replay.event)
            assertEquals(firstAccepted.event, events.events[expectedId])
            assertEquals(0, replayMaterializerCalls)
            assertEquals(1, events.insertCalls)
        }

    @Test
    fun `Widget facade folds repeated delivery by occurrence identity across minutes`() =
        runBlocking {
            val firstMillis = occurredAt.toEpochMilli() + 123L
            val expectedId = widgetOccurrenceActionEventId(widgetOccurrenceId)
            val events = FakeDoseEventRepository()
            val recorder = LocalActionRecorder(
                FakeMedicationPlanRepository(listOf(plan)),
                events
            )

            val first = recorder.recordWidget(plan.id, widgetOccurrenceId) { _, derivedId ->
                event(
                    DoseEventSource.WIDGET,
                    occurredAt = Instant.ofEpochMilli(firstMillis),
                    id = derivedId
                )
            }
            assertAcceptance(first, RecordAcceptance.Inserted)
            var replayMaterializerCalls = 0
            val replay = recorder.recordWidget(plan.id, widgetOccurrenceId) { _, derivedId ->
                replayMaterializerCalls += 1
                event(
                    DoseEventSource.WIDGET,
                    occurredAt = Instant.ofEpochMilli(firstMillis + 1_000L),
                    id = derivedId
                )
            }

            val accepted = assertAcceptance(replay, RecordAcceptance.FirstAcceptedReplay)
            assertEquals(expectedId, accepted.event.id)
            assertEquals(Instant.ofEpochMilli(firstMillis), accepted.event.occurredAt)
            assertEquals(0, replayMaterializerCalls)
            assertEquals(1, events.insertCalls)
        }

    @Test
    fun `Local policy distinguishes repository idempotency from first accepted replay`() =
        runBlocking {
            val events = FakeDoseEventRepository().apply {
                forcedInsertResult = InsertResult.Idempotent
            }
            val result = LocalActionRecorder(
                FakeMedicationPlanRepository(listOf(plan)),
                events
            ).recordWidget(plan.id, widgetOccurrenceId) { _, derivedId ->
                event(DoseEventSource.WIDGET, id = derivedId)
            }

            assertAcceptance(result, RecordAcceptance.RepositoryIdempotent)
            assertEquals(1, events.getCalls)
            assertEquals(1, events.insertCalls)
        }

    @Test
    fun `Local policy rejects another source and never overwrites the stored event`() =
        runBlocking {
            val scheduledAtMillis = 1_800_000_000_000L
            val id = reminderDoseEventId(plan.id, scheduledAtMillis)
            val collision = event(DoseEventSource.MANUAL, id = id)
            val events = FakeDoseEventRepository(listOf(collision))
            var materializerCalls = 0

            val result = LocalActionRecorder(
                FakeMedicationPlanRepository(listOf(plan)),
                events
            ).recordReminder(plan.id, scheduledAtMillis) { _, derivedId ->
                materializerCalls += 1
                event(DoseEventSource.REMINDER, id = derivedId)
            }

            assertSame(RecordDoseEventActionResult.Conflict, result)
            assertEquals(collision, events.events[id])
            assertEquals(0, materializerCalls)
            assertEquals(0, events.insertCalls)
        }

    @Test
    fun `Local insert race rereads once and applies expected source`() = runBlocking {
        val recordedAtMillis = occurredAt.toEpochMilli()
        val expectedId = widgetOccurrenceActionEventId(widgetOccurrenceId)
        val matching = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Conflict
            beforeForcedInsertResult = { inserted -> events[expectedId] = inserted }
        }
        val matchingResult = LocalActionRecorder(
            FakeMedicationPlanRepository(listOf(plan)),
            matching
        ).recordWidget(plan.id, widgetOccurrenceId) { _, derivedId ->
            event(DoseEventSource.WIDGET, id = derivedId)
        }
        assertAcceptance(matchingResult, RecordAcceptance.FirstAcceptedReplay)
        assertEquals(2, matching.getCalls)
        assertEquals(1, matching.insertCalls)

        val mismatching = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Conflict
            beforeForcedInsertResult = { inserted ->
                events[expectedId] = inserted.copy(source = DoseEventSource.MANUAL)
            }
        }
        val mismatchResult = LocalActionRecorder(
            FakeMedicationPlanRepository(listOf(plan)),
            mismatching
        ).recordWidget(plan.id, widgetOccurrenceId) { _, derivedId ->
            event(DoseEventSource.WIDGET, id = derivedId)
        }
        assertSame(RecordDoseEventActionResult.Conflict, mismatchResult)
        assertEquals(2, mismatching.getCalls)
        assertEquals(1, mismatching.insertCalls)
    }

    @Test
    fun `Local storage and unexpected failures are never accepted as replay`() = runBlocking {
        val storage = FakeDoseEventRepository().apply {
            getFailure = RepositoryPersistenceException("synthetic local read")
        }
        val unexpected = FakeDoseEventRepository().apply {
            getFailure = IllegalStateException("synthetic local read")
        }
        assertSame(
            RecordDoseEventActionResult.StorageFailure,
            LocalActionRecorder(FakeMedicationPlanRepository(listOf(plan)), storage)
                .recordWidget(plan.id, widgetOccurrenceId) { _, derivedId ->
                    event(DoseEventSource.WIDGET, id = derivedId)
                }
        )
        assertSame(
            RecordDoseEventActionResult.UnexpectedFailure,
            LocalActionRecorder(FakeMedicationPlanRepository(listOf(plan)), unexpected)
                .recordWidget(plan.id, widgetOccurrenceId) { _, derivedId ->
                    event(DoseEventSource.WIDGET, id = derivedId)
                }
        )
        assertEquals(0, storage.insertCalls)
        assertEquals(0, unexpected.insertCalls)
    }

    @Test
    fun `Wear replay accepts exact source and occurrence without plan or materializer`() =
        runBlocking {
            val existing = event(DoseEventSource.WEAR)
            val plans = FakeMedicationPlanRepository()
            val events = FakeDoseEventRepository(listOf(existing))
            var materializerCalls = 0

            val result = WearActionRecorder(plans, events).record(
                planId = UUID(0L, 999L),
                actionId = eventId,
                recordedAt = occurredAt
            ) {
                materializerCalls += 1
                event(DoseEventSource.WEAR)
            }

            val replay = assertAcceptance(result, RecordAcceptance.FirstAcceptedReplay)
            assertEquals(existing, replay.event)
            assertNull(replay.plan)
            assertEquals(0, plans.getCalls)
            assertEquals(0, materializerCalls)
            assertEquals(0, events.insertCalls)
        }

    @Test
    fun `Wear replay rejects occurrence or source mismatch without plan lookup`() = runBlocking {
        listOf(
            event(DoseEventSource.WEAR, occurredAt = occurredAt.plusMillis(1)),
            event(DoseEventSource.WIDGET)
        ).forEach { existing ->
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val events = FakeDoseEventRepository(listOf(existing))
            var materializerCalls = 0
            val result = WearActionRecorder(plans, events).record(
                plan.id,
                eventId,
                occurredAt
            ) {
                materializerCalls += 1
                event(DoseEventSource.WEAR)
            }
            assertSame(RecordDoseEventActionResult.Conflict, result)
            assertEquals(0, plans.getCalls)
            assertEquals(0, materializerCalls)
            assertEquals(existing, events.events[eventId])
        }
    }

    @Test
    fun `Wear first materialization inserts once and maps repository idempotency directly`() =
        runBlocking {
            val insertedPlans = FakeMedicationPlanRepository(listOf(plan))
            val insertedEvents = FakeDoseEventRepository()
            var insertedMaterializerCalls = 0
            val inserted = WearActionRecorder(insertedPlans, insertedEvents).record(
                plan.id,
                eventId,
                occurredAt
            ) { materializedPlan ->
                insertedMaterializerCalls += 1
                event(DoseEventSource.WEAR, plan = materializedPlan)
            }
            assertAcceptance(inserted, RecordAcceptance.Inserted)
            assertEquals(1, insertedPlans.getCalls)
            assertEquals(1, insertedMaterializerCalls)
            assertEquals(1, insertedEvents.insertCalls)

            val idempotentEvents = FakeDoseEventRepository().apply {
                forcedInsertResult = InsertResult.Idempotent
            }
            val idempotent = WearActionRecorder(
                FakeMedicationPlanRepository(listOf(plan)),
                idempotentEvents
            ).record(plan.id, eventId, occurredAt) {
                event(DoseEventSource.WEAR)
            }
            assertAcceptance(idempotent, RecordAcceptance.RepositoryIdempotent)
            assertEquals(1, idempotentEvents.getCalls)
            assertEquals(1, idempotentEvents.insertCalls)
        }

    @Test
    fun `Wear conflict race rereads once with source and occurrence policy`() = runBlocking {
        val matching = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Conflict
            beforeForcedInsertResult = { inserted -> events[eventId] = inserted }
        }
        val matchingResult = WearActionRecorder(
            FakeMedicationPlanRepository(listOf(plan)),
            matching
        ).record(plan.id, eventId, occurredAt) { event(DoseEventSource.WEAR) }
        assertAcceptance(matchingResult, RecordAcceptance.FirstAcceptedReplay)
        assertEquals(2, matching.getCalls)

        val mismatching = FakeDoseEventRepository().apply {
            forcedInsertResult = InsertResult.Conflict
            beforeForcedInsertResult = { inserted ->
                events[eventId] = inserted.copy(occurredAt = occurredAt.plusMillis(1))
            }
        }
        val mismatchResult = WearActionRecorder(
            FakeMedicationPlanRepository(listOf(plan)),
            mismatching
        ).record(plan.id, eventId, occurredAt) { event(DoseEventSource.WEAR) }
        assertSame(RecordDoseEventActionResult.Conflict, mismatchResult)
        assertEquals(2, mismatching.getCalls)
        assertEquals(1, mismatching.insertCalls)
    }

    @Test
    fun `Wear first materialization rejects missing disabled and invalid candidates`() =
        runBlocking {
            var missingMaterializerCalls = 0
            val missingEvents = FakeDoseEventRepository()
            assertSame(
                RecordDoseEventActionResult.PlanNotFound,
                WearActionRecorder(FakeMedicationPlanRepository(), missingEvents).record(
                    plan.id,
                    eventId,
                    occurredAt
                ) {
                    missingMaterializerCalls += 1
                    event(DoseEventSource.WEAR)
                }
            )
            assertEquals(0, missingMaterializerCalls)

            var disabledMaterializerCalls = 0
            val disabledEvents = FakeDoseEventRepository()
            assertSame(
                RecordDoseEventActionResult.PlanDisabled,
                WearActionRecorder(
                    FakeMedicationPlanRepository(listOf(plan.copy(isEnabled = false))),
                    disabledEvents
                ).record(plan.id, eventId, occurredAt) {
                    disabledMaterializerCalls += 1
                    event(DoseEventSource.WEAR)
                }
            )
            assertEquals(0, disabledMaterializerCalls)

            val invalidCandidates = listOf(
                event(DoseEventSource.WEAR).copy(id = UUID(0L, 999L)),
                event(DoseEventSource.WIDGET),
                event(DoseEventSource.WEAR, occurredAt = occurredAt.plusMillis(1))
            )
            invalidCandidates.forEach { candidate ->
                val events = FakeDoseEventRepository()
                assertSame(
                    RecordDoseEventActionResult.Invalid,
                    WearActionRecorder(
                        FakeMedicationPlanRepository(listOf(plan)),
                        events
                    ).record(plan.id, eventId, occurredAt) { candidate }
                )
                assertEquals(0, events.insertCalls)
            }
        }

    @Test
    fun `Wear storage unexpected and cancellation failures never become replay`() {
        val storage = FakeDoseEventRepository().apply {
            getFailure = RepositoryPersistenceException("synthetic Wear read")
        }
        assertSame(
            RecordDoseEventActionResult.StorageFailure,
            runBlocking {
                WearActionRecorder(FakeMedicationPlanRepository(listOf(plan)), storage)
                    .record(plan.id, eventId, occurredAt) { event(DoseEventSource.WEAR) }
            }
        )
        val unexpectedPlans = FakeMedicationPlanRepository(listOf(plan)).apply {
            getFailure = IllegalStateException("synthetic Wear plan read")
        }
        assertSame(
            RecordDoseEventActionResult.UnexpectedFailure,
            runBlocking {
                WearActionRecorder(unexpectedPlans, FakeDoseEventRepository())
                    .record(plan.id, eventId, occurredAt) { event(DoseEventSource.WEAR) }
            }
        )
        val cancelled = FakeDoseEventRepository().apply {
            getFailure = CancellationException("synthetic Wear cancellation")
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                WearActionRecorder(FakeMedicationPlanRepository(listOf(plan)), cancelled)
                    .record(plan.id, eventId, occurredAt) { event(DoseEventSource.WEAR) }
            }
        }
        assertEquals(0, storage.insertCalls)
    }

    @Test
    fun `Concurrent same Wear action produces one row and two accepted outcomes`() = runBlocking {
        val events = CoordinatedDoseEventRepository()
        val recorder = WearActionRecorder(FakeMedicationPlanRepository(listOf(plan)), events)
        val outcomes = listOf(occurredAt, occurredAt).map { recordedAt ->
            async(Dispatchers.Default) {
                recorder.record(plan.id, eventId, recordedAt) {
                    event(DoseEventSource.WEAR, occurredAt = recordedAt)
                }
            }
        }.awaitAll()

        assertEquals(1, events.events.size)
        assertEquals(2, events.insertCalls.get())
        assertEquals(
            setOf(RecordAcceptance.Inserted, RecordAcceptance.RepositoryIdempotent),
            outcomes.map { (it as RecordDoseEventActionResult.Accepted).acceptance }.toSet()
        )
    }

    @Test
    fun `Concurrent same Wear ID with different occurrence preserves first row and conflicts`() =
        runBlocking {
            val events = CoordinatedDoseEventRepository()
            val recorder = WearActionRecorder(FakeMedicationPlanRepository(listOf(plan)), events)
            val occurrences = listOf(occurredAt, occurredAt.plusMillis(1))
            val outcomes = occurrences.map { recordedAt ->
                async(Dispatchers.Default) {
                    recorder.record(plan.id, eventId, recordedAt) {
                        event(DoseEventSource.WEAR, occurredAt = recordedAt)
                    }
                }
            }.awaitAll()

            assertEquals(1, events.events.size)
            assertTrue(outcomes.any { it is RecordDoseEventActionResult.Accepted })
            assertTrue(outcomes.any { it === RecordDoseEventActionResult.Conflict })
            assertTrue(events.events.single().occurredAt in occurrences)
        }

    private suspend fun strict(
        plans: FakeMedicationPlanRepository,
        events: FakeDoseEventRepository,
        candidate: DoseEvent = event(DoseEventSource.MANUAL)
    ): RecordDoseEventActionResult = engine(plans, events).execute(
        planId = plan.id,
        eventId = eventId,
        expectedSource = DoseEventSource.MANUAL,
        requireEnabledPlan = true,
        policy = ExistingEventPolicy.RepositoryStrict,
        createEvent = { candidate }
    )

    private fun engine(
        plans: FakeMedicationPlanRepository,
        events: FakeDoseEventRepository
    ) = RecordDoseEventEngine(plans, events)

    private fun event(
        source: DoseEventSource,
        occurredAt: Instant = this.occurredAt,
        id: UUID = eventId,
        plan: io.github.yingqiu0871.evolune.core.model.MedicationPlan = this.plan
    ): DoseEvent = DoseEvent(
        id = id,
        route = plan.route,
        occurredAt = occurredAt,
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        source = source
    )

    private fun assertAcceptance(
        result: RecordDoseEventActionResult,
        expected: RecordAcceptance
    ): RecordDoseEventActionResult.Accepted {
        assertTrue(result is RecordDoseEventActionResult.Accepted)
        return (result as RecordDoseEventActionResult.Accepted).also {
            assertEquals(expected, it.acceptance)
        }
    }
}

private class CoordinatedDoseEventRepository : DoseEventRepository {
    val events = mutableListOf<DoseEvent>()
    val insertCalls = AtomicInteger()
    private val initialReads = AtomicInteger()
    private val bothInitialReads = CompletableDeferred<Unit>()
    private val mutex = Mutex()

    override fun observeAll(): Flow<List<DoseEvent>> = flowOf(events.toList())

    override suspend fun getById(id: UUID): DoseEvent? {
        val read = initialReads.incrementAndGet()
        if (read <= EXPECTED_INITIAL_READS) {
            if (read == EXPECTED_INITIAL_READS) bothInitialReads.complete(Unit)
            bothInitialReads.await()
            return null
        }
        return mutex.withLock { events.singleOrNull { it.id == id } }
    }

    override suspend fun findOccurredBetween(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<DoseEvent> = emptyList()

    override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = emptyList()

    override suspend fun insert(event: DoseEvent): InsertResult = mutex.withLock {
        insertCalls.incrementAndGet()
        val existing = events.singleOrNull { it.id == event.id }
        when {
            existing == null -> {
                events += event
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
    ): LatestDoseDeleteResult = LatestDoseDeleteResult.EventNotFound

    override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound

    private companion object {
        const val EXPECTED_INITIAL_READS = 2
    }
}
