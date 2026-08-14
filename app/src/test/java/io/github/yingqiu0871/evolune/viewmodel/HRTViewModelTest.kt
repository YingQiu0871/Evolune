package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.application.DoseEventEditSessionFactory
import io.github.yingqiu0871.evolune.application.DoseEventEditorInput
import io.github.yingqiu0871.evolune.application.DoseEventInputIssue
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanUpdateResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.UUID

class HRTViewModelTest {
    @Test
    fun `create session survives validation and storage failure without changing identity`() {
        var nextId = 1L
        val repository = FakeDoseEventRepository()
        val fixture = fixture(
            repository = repository,
            sessionFactory = DoseEventEditSessionFactory(
                idSupplier = { UUID(0L, nextId++) },
                clock = Clock.fixed(NOW, ZoneOffset.UTC),
                zoneIdSupplier = { TEST_ZONE }
            )
        )
        try {
            fixture.viewModel.startCreateSession()
            val session = requireNotNull(fixture.viewModel.editSession.value)
            fixture.viewModel.startCreateSession()
            assertSame(session, fixture.viewModel.editSession.value)

            fixture.viewModel.saveEvent(input(doseMG = 0.0))
            assertEquals(0, repository.insertCalls)
            assertSame(session, fixture.viewModel.editSession.value)
            assertEquals(
                DoseEventOperationState.Failure(
                    DoseEventOperation.CREATE,
                    DoseEventOperationError.InvalidInput(
                        listOf(DoseEventInputIssue.NonPositiveDose)
                    )
                ),
                fixture.viewModel.operationState.value
            )

            repository.insertError = IllegalStateException("synthetic storage failure")
            fixture.viewModel.saveEvent(input())
            assertEquals(1, repository.insertCalls)
            assertSame(session, fixture.viewModel.editSession.value)
            assertEquals(
                DoseEventOperationState.Failure(
                    DoseEventOperation.CREATE,
                    DoseEventOperationError.StorageFailure
                ),
                fixture.viewModel.operationState.value
            )

            fixture.viewModel.closeEditSession()
            fixture.viewModel.startCreateSession()
            assertEquals(UUID(0L, 2L), fixture.viewModel.editSession.value?.original?.id)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `inserted and idempotent create return persisted event and success UI event`() = runBlocking {
        listOf(InsertResult.Inserted, InsertResult.Idempotent).forEach { result ->
            val repository = FakeDoseEventRepository().apply {
                insertResult = result
                storedTransform = { it.copy(revision = 4L) }
            }
            val fixture = fixture(repository)
            try {
                fixture.viewModel.startCreateSession()
                fixture.viewModel.saveEvent(input())

                val state = fixture.viewModel.operationState.value as DoseEventOperationState.Success
                assertEquals(DoseEventOperation.CREATE, state.operation)
                assertEquals(4L, state.event?.revision)
                val uiEvent = withTimeout(1_000L) { fixture.viewModel.uiEvents.first() }
                    as DoseEventUiEvent.Saved
                assertTrue(uiEvent.created)
                assertEquals(4L, uiEvent.event.revision)
                assertEquals(1, repository.insertCalls)
                assertEquals(1, repository.getByIdCalls)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `create maps conflict invalid and exception without success event`() {
        listOf(
            InsertResult.Conflict to DoseEventOperationError.Conflict,
            InsertResult.Invalid to DoseEventOperationError.RepositoryInvalid
        ).forEach { (result, expectedError) ->
            val repository = FakeDoseEventRepository().apply { insertResult = result }
            val fixture = fixture(repository)
            try {
                fixture.viewModel.startCreateSession()
                fixture.viewModel.saveEvent(input())
                assertEquals(
                    DoseEventOperationState.Failure(DoseEventOperation.CREATE, expectedError),
                    fixture.viewModel.operationState.value
                )
                assertNotNull(fixture.viewModel.editSession.value)
            } finally {
                fixture.close()
            }
        }

        val repository = FakeDoseEventRepository().apply {
            insertError = IllegalStateException("synthetic storage failure")
        }
        val fixture = fixture(repository)
        try {
            fixture.viewModel.startCreateSession()
            fixture.viewModel.saveEvent(input())
            assertEquals(
                DoseEventOperationState.Failure(
                    DoseEventOperation.CREATE,
                    DoseEventOperationError.StorageFailure
                ),
                fixture.viewModel.operationState.value
            )
            assertNotNull(fixture.viewModel.editSession.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `duplicate create while first insert is suspended writes once`() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeDoseEventRepository().apply { insertGate = gate }
        val fixture = fixture(repository)
        try {
            fixture.viewModel.startCreateSession()
            fixture.viewModel.saveEvent(input())
            assertTrue(fixture.viewModel.operationState.value is DoseEventOperationState.Running)

            fixture.viewModel.saveEvent(input(doseMG = 3.0))
            assertEquals(1, repository.insertCalls)

            gate.complete(Unit)
            assertEquals(1, repository.insertCalls)
            assertTrue(fixture.viewModel.operationState.value is DoseEventOperationState.Success)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `CAS update uses original revision and publishes Repository persisted revision`() = runBlocking {
        listOf(UpdateResult.Updated, UpdateResult.NoChange).forEach { result ->
            val original = event(revision = 7L, source = DoseEventSource.WEAR, slotId = SLOT_ID)
            val persisted = original.copy(doseMG = 3.0, revision = 8L)
            val repository = FakeDoseEventRepository().apply {
                updateResult = result
                stored[EVENT_ID] = persisted
            }
            val fixture = fixture(repository)
            try {
                fixture.viewModel.startEditSession(original)
                fixture.viewModel.saveEvent(input(doseMG = 3.0))

                assertEquals(7L, repository.updated.single().second)
                assertEquals(DoseEventSource.WEAR, repository.updated.single().first.source)
                assertEquals(SLOT_ID, repository.updated.single().first.slotId)
                val state = fixture.viewModel.operationState.value as DoseEventOperationState.Success
                assertEquals(8L, state.event?.revision)
                val uiEvent = withTimeout(1_000L) { fixture.viewModel.uiEvents.first() }
                    as DoseEventUiEvent.Saved
                assertTrue(!uiEvent.created)
                assertEquals(8L, uiEvent.event.revision)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `CAS failures keep complete edit session and never insert`() {
        listOf(
            UpdateResult.RevisionConflict to DoseEventOperationError.RevisionConflict,
            UpdateResult.NotFound to DoseEventOperationError.NotFound,
            UpdateResult.Invalid to DoseEventOperationError.RepositoryInvalid
        ).forEach { (result, expectedError) ->
            val original = event(revision = 7L, source = DoseEventSource.WEAR, slotId = SLOT_ID)
            val repository = FakeDoseEventRepository().apply { updateResult = result }
            val fixture = fixture(repository)
            try {
                fixture.viewModel.startEditSession(original)
                val session = requireNotNull(fixture.viewModel.editSession.value)
                fixture.viewModel.saveEvent(input(doseMG = 3.0))

                assertEquals(
                    DoseEventOperationState.Failure(DoseEventOperation.UPDATE, expectedError),
                    fixture.viewModel.operationState.value
                )
                assertSame(session, fixture.viewModel.editSession.value)
                assertEquals(0, repository.insertCalls)
                assertEquals(original.source, session.original.source)
                assertEquals(original.slotId, session.original.slotId)
                assertEquals(original.revision, session.expectedRevision)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `cancellation releases operation gate for a later save`() {
        val repository = FakeDoseEventRepository().apply {
            insertError = CancellationException("synthetic cancellation")
        }
        val fixture = fixture(repository)
        try {
            fixture.viewModel.startCreateSession()
            fixture.viewModel.saveEvent(input())
            assertEquals(DoseEventOperationState.Idle, fixture.viewModel.operationState.value)

            repository.insertError = null
            fixture.viewModel.saveEvent(input())
            assertEquals(2, repository.insertCalls)
            assertTrue(fixture.viewModel.operationState.value is DoseEventOperationState.Success)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `delete handles deleted not found and storage failure without fallback`() = runBlocking {
        val deletedRepository = FakeDoseEventRepository()
        val deletedFixture = fixture(deletedRepository)
        try {
            deletedFixture.viewModel.deleteEvent(EVENT_ID)
            assertEquals(
                DoseEventOperationState.Success(DoseEventOperation.DELETE),
                deletedFixture.viewModel.operationState.value
            )
            assertEquals(
                DoseEventUiEvent.Deleted(EVENT_ID),
                withTimeout(1_000L) { deletedFixture.viewModel.uiEvents.first() }
            )
        } finally {
            deletedFixture.close()
        }

        val notFoundRepository = FakeDoseEventRepository().apply {
            deleteResult = DeleteResult.NotFound
        }
        val notFoundFixture = fixture(notFoundRepository)
        try {
            notFoundFixture.viewModel.deleteEvent(EVENT_ID)
            assertEquals(
                DoseEventOperationState.Failure(
                    DoseEventOperation.DELETE,
                    DoseEventOperationError.NotFound
                ),
                notFoundFixture.viewModel.operationState.value
            )
        } finally {
            notFoundFixture.close()
        }

        val failingRepository = FakeDoseEventRepository().apply {
            deleteError = IllegalStateException("synthetic storage failure")
        }
        val failingFixture = fixture(failingRepository)
        try {
            failingFixture.viewModel.deleteEvent(EVENT_ID)
            assertEquals(
                DoseEventOperationState.Failure(
                    DoseEventOperation.DELETE,
                    DoseEventOperationError.StorageFailure
                ),
                failingFixture.viewModel.operationState.value
            )
        } finally {
            failingFixture.close()
        }
    }

    @Test
    fun `quick add captures manual metadata and existing minute precision`() {
        val repository = FakeDoseEventRepository()
        val fixture = fixture(
            repository = repository,
            sessionFactory = DoseEventEditSessionFactory(
                idSupplier = { EVENT_ID },
                clock = Clock.fixed(Instant.parse("2026-01-02T03:04:59.999Z"), ZoneOffset.UTC),
                zoneIdSupplier = { TEST_ZONE }
            )
        )
        try {
            fixture.viewModel.quickAddFromPlan(plan())
            val inserted = repository.inserted.single()

            assertEquals(Instant.parse("2026-01-02T03:04:00Z"), inserted.occurredAt)
            assertEquals(TEST_ZONE, inserted.zoneId)
            assertEquals(inserted.occurredAt.atZone(TEST_ZONE).toLocalDate(), inserted.localDate)
            assertEquals(DoseEventSource.MANUAL, inserted.source)
            assertEquals(DoseEventStatus.RECORDED, inserted.status)
            assertEquals(1L, inserted.revision)
            assertEquals(null, inserted.slotId)
            assertEquals(plan().extras, inserted.extras)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `JSON import reports all contract outcomes and weight without fallback`() = runBlocking {
        val results = ArrayDeque(
            listOf(
                InsertResult.Inserted,
                InsertResult.Idempotent,
                InsertResult.Conflict,
                InsertResult.Invalid
            )
        )
        val repository = FakeDoseEventRepository().apply {
            insertResultProvider = { results.removeFirst() }
        }
        val fixture = fixture(repository)
        try {
            var importedWeight: Double? = null
            fixture.viewModel.importFromMahiroJson(jsonWithFourEvents()) { importedWeight = it }
            val result = withTimeout(5_000L) {
                fixture.viewModel.importResult.filter { it is ImportResult.Success }.first()
            } as ImportResult.Success

            assertEquals(2, result.importedCount)
            assertEquals(1, result.existingCount)
            assertEquals(1, result.conflictCount)
            assertEquals(1, result.invalidCount)
            assertEquals(55.0, importedWeight)
            assertEquals(4, repository.insertCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `JSON storage failure is explicit and operation gate remains reusable`() = runBlocking {
        val repository = FakeDoseEventRepository().apply {
            insertResult = InsertResult.Inserted
            insertError = IllegalStateException("synthetic import failure")
            insertErrorCall = 2
        }
        val fixture = fixture(repository)
        try {
            var importedWeight: Double? = null
            fixture.viewModel.importFromMahiroJson(jsonWithFourEvents()) { importedWeight = it }
            val result = withTimeout(5_000L) {
                fixture.viewModel.importResult.filter { it is ImportResult.Error }.first()
            } as ImportResult.Error
            assertEquals(1, result.importedCount)
            assertEquals(0, result.existingCount)
            assertEquals(0, result.conflictCount)
            assertEquals(0, result.invalidCount)
            assertEquals(1, result.failedIndex)
            assertNull(importedWeight)
            assertEquals(
                DoseEventOperationState.Failure(
                    DoseEventOperation.IMPORT,
                    DoseEventOperationError.StorageFailure
                ),
                fixture.viewModel.operationState.value
            )
            assertEquals(2, repository.insertCalls)

            repository.insertError = null
            fixture.viewModel.startCreateSession()
            fixture.viewModel.saveEvent(input())
            assertTrue(fixture.viewModel.operationState.value is DoseEventOperationState.Success)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `JSON numeric id follows formal v1 invalid behavior without repository fallback`() =
        runBlocking {
            val repository = FakeDoseEventRepository()
            val fixture = fixture(repository)
            try {
                fixture.viewModel.importFromMahiroJson(
                    """
                        {
                          "weight": 55,
                          "events": [
                            {"id":7,"route":"oral","ester":"E2","timeH":1.0,"doseMG":2.0,"extras":{}},
                            {"id":"00000000-0000-0000-0000-000000000008","route":"oral","ester":"E2","timeH":2.0,"doseMG":2.0,"extras":{}}
                          ]
                        }
                    """.trimIndent()
                )
                val result = withTimeout(5_000L) {
                    fixture.viewModel.importResult.filter { it is ImportResult.Success }.first()
                } as ImportResult.Success

                assertEquals(1, result.importedCount)
                assertEquals(1, result.invalidCount)
                assertEquals(1, repository.insertCalls)
                assertEquals(UUID(0L, 8L), repository.inserted.single().id)
            } finally {
                fixture.close()
            }
        }

    @Test
    fun `PK inputs preserve contract ordering and delegate selection to repository`() = runBlocking {
        val events = listOf(
            event(id = UUID(0L, 2L), occurredAt = Instant.ofEpochMilli(2_000L)),
            event(id = UUID(0L, 1L), occurredAt = Instant.ofEpochMilli(1_000L))
        )
        val repository = FakeDoseEventRepository().apply {
            observed.value = events
            pkEvents = events
        }
        val fixture = fixture(repository)
        try {
            val points = withTimeout(5_000L) {
                fixture.viewModel.doseTimePoints.filter { it.size == 2 }.first()
            }
            assertEquals(events.map { it.occurredAt.toEpochMilli() / 3_600_000.0 }, points)
            withTimeout(5_000L) {
                while (repository.getEventsForPkCalls == 0) {
                    kotlinx.coroutines.yield()
                }
            }
            assertEquals(NOW, repository.lastPkAsOf)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `JSON export uses the formal Domain protocol boundary and preserves order`() = runBlocking {
        val events = listOf(
            event(UUID(0L, 2L), Instant.ofEpochMilli(2_000L)),
            event(UUID(0L, 1L), Instant.ofEpochMilli(1_000L))
        )
        val repository = FakeDoseEventRepository().apply { observed.value = events }
        val fixture = fixture(repository)
        try {
            withTimeout(5_000L) {
                fixture.viewModel.events.filter { it.size == 2 }.first()
            }
            val root = Json.parseToJsonElement(
                fixture.viewModel.exportToMahiroJson(55.0)
            ).jsonObject

            assertEquals(
                events.map { it.id.toString() },
                root.getValue("events").jsonArray.map {
                    it.jsonObject.getValue("id").jsonPrimitive.content
                }
            )
            assertEquals(
                NOW.toString(),
                root.getValue("meta").jsonObject
                    .getValue("exportedAt").jsonPrimitive.content
            )
        } finally {
            fixture.close()
        }
    }

    private fun fixture(
        repository: FakeDoseEventRepository,
        sessionFactory: DoseEventEditSessionFactory = DoseEventEditSessionFactory(
            idSupplier = { EVENT_ID },
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            zoneIdSupplier = { TEST_ZONE }
        )
    ): ViewModelFixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return ViewModelFixture(
            viewModel = HRTViewModel(
                repository = repository,
                medicationPlanRepository = FakeMedicationPlanRepository(),
                sessionFactory = sessionFactory,
                clock = Clock.fixed(NOW, ZoneOffset.UTC),
                operationScope = scope
            ),
            scope = scope
        )
    }

    private fun input(doseMG: Double = 2.0): DoseEventEditorInput = DoseEventEditorInput(
        occurredAt = NOW,
        occurredAtEdited = false,
        route = Route.ORAL,
        doseMG = doseMG,
        ester = Ester.E2,
        extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.25)
    )

    private fun event(
        id: UUID = EVENT_ID,
        occurredAt: Instant = NOW,
        revision: Long = 1L,
        source: DoseEventSource = DoseEventSource.MANUAL,
        slotId: UUID? = null
    ): DoseEvent = DoseEvent(
        id = id,
        route = Route.ORAL,
        occurredAt = occurredAt,
        zoneId = TEST_ZONE,
        localDate = occurredAt.atZone(TEST_ZONE).toLocalDate(),
        doseMG = 2.0,
        ester = Ester.E2,
        extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.25),
        slotId = slotId,
        source = source,
        status = DoseEventStatus.RECORDED,
        revision = revision
    )

    private fun plan(): MedicationPlan = MedicationPlan(
        id = PLAN_ID,
        name = "Synthetic quick plan",
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 1.0,
        scheduleType = ScheduleType.DAILY,
        slots = emptyList(),
        daysOfWeek = emptySet(),
        intervalDays = 1,
        isEnabled = true,
        extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
        createdAt = Instant.EPOCH
    )

    private fun jsonWithFourEvents(): String = """
        {
          "weight": 55,
          "events": [
            {"id":"00000000-0000-0000-0000-000000000001","route":"oral","ester":"E2","timeH":1.0,"doseMG":2.0,"extras":{}},
            {"id":"00000000-0000-0000-0000-000000000002","route":"oral","ester":"E2","timeH":2.0,"doseMG":2.0,"extras":{}},
            {"id":"00000000-0000-0000-0000-000000000003","route":"oral","ester":"E2","timeH":3.0,"doseMG":2.0,"extras":{}},
            {"id":"00000000-0000-0000-0000-000000000004","route":"oral","ester":"E2","timeH":4.0,"doseMG":2.0,"extras":{}}
          ]
        }
    """.trimIndent()

    private data class ViewModelFixture(
        val viewModel: HRTViewModel,
        val scope: CoroutineScope
    ) {
        fun close() = scope.cancel()
    }

    private class FakeDoseEventRepository : DoseEventRepository {
        val observed = MutableStateFlow<List<DoseEvent>>(emptyList())
        val stored = mutableMapOf<UUID, DoseEvent>()
        val inserted = mutableListOf<DoseEvent>()
        val updated = mutableListOf<Pair<DoseEvent, Long>>()
        val deleted = mutableListOf<UUID>()
        var insertCalls = 0
        var getByIdCalls = 0
        var getEventsForPkCalls = 0
        var lastPkAsOf: Instant? = null
        var pkEvents: List<DoseEvent> = emptyList()
        var insertResult: InsertResult = InsertResult.Inserted
        var insertResultProvider: (() -> InsertResult)? = null
        var updateResult: UpdateResult = UpdateResult.Updated
        var deleteResult: DeleteResult = DeleteResult.Deleted
        var insertError: RuntimeException? = null
        var insertErrorCall: Int? = null
        var deleteError: RuntimeException? = null
        var insertGate: CompletableDeferred<Unit>? = null
        var storedTransform: (DoseEvent) -> DoseEvent = { it }

        override fun observeAll(): Flow<List<DoseEvent>> = observed

        override suspend fun getById(id: UUID): DoseEvent? {
            getByIdCalls += 1
            return stored[id]
        }

        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> = observed.value.filter {
            it.occurredAt >= startInclusive && it.occurredAt < endExclusive
        }

        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> {
            getEventsForPkCalls += 1
            lastPkAsOf = asOf
            return pkEvents
        }

        override suspend fun insert(event: DoseEvent): InsertResult {
            insertCalls += 1
            if (insertError != null && (insertErrorCall == null || insertCalls == insertErrorCall)) {
                throw requireNotNull(insertError)
            }
            insertGate?.await()
            inserted += event
            val result = insertResultProvider?.invoke() ?: insertResult
            if (result == InsertResult.Inserted || result == InsertResult.Idempotent) {
                stored[event.id] = storedTransform(event)
            }
            return result
        }

        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult {
            updated += event to expectedRevision
            return updateResult
        }

        override suspend fun delete(id: UUID): DeleteResult {
            deleteError?.let { throw it }
            deleted += id
            return deleteResult
        }

        override suspend fun deleteAll(): DeleteResult = deleteResult
    }

    private class FakeMedicationPlanRepository : MedicationPlanRepository {
        private val plans = MutableStateFlow<List<MedicationPlan>>(emptyList())

        override fun observeAll(): Flow<List<MedicationPlan>> = plans
        override fun observeEnabled(): Flow<List<MedicationPlan>> = plans
        override suspend fun getById(id: UUID): MedicationPlan? = null
        override suspend fun save(plan: MedicationPlan): PlanSaveResult = PlanSaveResult.Invalid
        override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult =
            PlanUpdateResult.Invalid
        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.NotFound
        override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
    }

    private companion object {
        val EVENT_ID: UUID = UUID(0L, 100L)
        val SLOT_ID: UUID = UUID(0L, 101L)
        val PLAN_ID: UUID = UUID(0L, 200L)
        val NOW: Instant = Instant.parse("2026-01-02T03:04:05.678Z")
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
