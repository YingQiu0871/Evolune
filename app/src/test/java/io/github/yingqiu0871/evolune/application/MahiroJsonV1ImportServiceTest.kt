package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DoseEventAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID

class MahiroJsonV1ImportServiceTest {
    @Test
    fun `valid event is inserted with protocol fields and locked metadata`() = runBlocking {
        val repository = FakeRepository(InsertResult.Inserted)
        val summary = service(repository).import(
            document(
                event(
                    idField = stringId(EVENT_ID),
                    route = "sublingual",
                    timeH = 1.5,
                    doseMG = 2.25,
                    extras = "\"sublingualTier\":2.0,\"unknown\":9.0"
                ),
                weight = 55.0
            )
        ).requireSuccess()
        val inserted = repository.attempted.single()

        assertEquals(55.0, summary.weight)
        assertEquals(1, summary.insertedCount)
        assertEquals(1, summary.processedCount)
        assertEquals(EVENT_ID, inserted.id)
        assertEquals(Instant.ofEpochMilli(5_400_000L), inserted.occurredAt)
        assertEquals(2.25, inserted.doseMG, 0.0)
        assertEquals(2.0, inserted.extras[ExtraKey.SUBLINGUAL_TIER])
        assertEquals(1, inserted.extras.size)
        assertNull(inserted.zoneId)
        assertNull(inserted.localDate)
        assertNull(inserted.slotId)
        assertEquals(DoseEventSource.JSON_V1, inserted.source)
        assertEquals(DoseEventStatus.RECORDED, inserted.status)
        assertEquals(1L, inserted.revision)
    }

    @Test
    fun `mixed repository outcomes remain separately counted`() = runBlocking {
        val repository = FakeRepository(
            InsertResult.Inserted,
            InsertResult.Idempotent,
            InsertResult.Conflict,
            InsertResult.Invalid
        )
        val events = (1L..4L).map { event(stringId(UUID(0L, it))) }.toTypedArray()

        val summary = service(repository).import(document(*events)).requireSuccess()

        assertEquals(1, summary.insertedCount)
        assertEquals(1, summary.idempotentCount)
        assertEquals(1, summary.conflictCount)
        assertEquals(1, summary.invalidCount)
        assertEquals(0, summary.failedCount)
        assertEquals(4, summary.processedCount)
        assertEquals(4, repository.insertCalls)
    }

    @Test
    fun `conflict does not prevent a later insert`() = runBlocking {
        val repository = FakeRepository(InsertResult.Conflict, InsertResult.Inserted)

        val summary = service(repository).import(twoValidEvents()).requireSuccess()

        assertEquals(1, summary.conflictCount)
        assertEquals(1, summary.insertedCount)
        assertEquals(2, repository.insertCalls)
    }

    @Test
    fun `idempotent result does not prevent a later insert`() = runBlocking {
        val repository = FakeRepository(InsertResult.Idempotent, InsertResult.Inserted)

        val summary = service(repository).import(twoValidEvents()).requireSuccess()

        assertEquals(1, summary.idempotentCount)
        assertEquals(1, summary.insertedCount)
        assertEquals(2, repository.insertCalls)
    }

    @Test
    fun `repository invalid result does not prevent a later insert`() = runBlocking {
        val repository = FakeRepository(InsertResult.Invalid, InsertResult.Inserted)

        val summary = service(repository).import(twoValidEvents()).requireSuccess()

        assertEquals(1, summary.invalidCount)
        assertEquals(1, summary.insertedCount)
        assertEquals(2, repository.insertCalls)
    }

    @Test
    fun `codec and adapter invalid entries are skipped before a later valid event`() = runBlocking {
        val repository = FakeRepository(InsertResult.Inserted)

        val summary = service(repository).import(
            document(
                event(idField = "\"id\":42"),
                event(idField = stringId(UUID(0L, 2L)), route = "unknown"),
                event(idField = stringId(UUID(0L, 3L)))
            )
        ).requireSuccess()

        assertEquals(2, summary.invalidCount)
        assertEquals(1, summary.insertedCount)
        assertEquals(3, summary.processedCount)
        assertEquals(1, repository.insertCalls)
        assertEquals(UUID(0L, 3L), repository.attempted.single().id)
    }

    @Test
    fun `numeric id is invalid while missing blank and malformed strings use independent ids`() =
        runBlocking {
            val generatedIds = ArrayDeque(
                listOf(UUID(0L, 101L), UUID(0L, 102L), UUID(0L, 103L))
            )
            val repository = FakeRepository(
                InsertResult.Inserted,
                InsertResult.Inserted,
                InsertResult.Inserted
            )
            val importService = service(
                repository,
                MahiroV1DoseEventAdapter { generatedIds.removeFirst() }
            )

            val summary = importService.import(
                document(
                    event(idField = null),
                    event(idField = "\"id\":\"\""),
                    event(idField = "\"id\":\"not-a-uuid\""),
                    event(idField = "\"id\":42")
                )
            ).requireSuccess()

            assertEquals(3, summary.insertedCount)
            assertEquals(1, summary.invalidCount)
            assertEquals(
                listOf(UUID(0L, 101L), UUID(0L, 102L), UUID(0L, 103L)),
                repository.attempted.map { it.id }
            )
            assertEquals(3, repository.insertCalls)
            assertTrue(generatedIds.isEmpty())
        }

    @Test
    fun `storage failure stops later entries with exact partial summary and source index`() =
        runBlocking {
            val repository = FakeRepository(InsertResult.Inserted, InsertResult.Idempotent).apply {
                failureCall = 3
            }

            val result = service(repository).import(
                document(
                    event(stringId(UUID(0L, 1L))),
                    event(idField = "\"id\":42"),
                    event(stringId(UUID(0L, 2L))),
                    event(stringId(UUID(0L, 3L))),
                    event(stringId(UUID(0L, 4L)))
                )
            )

            assertTrue(result is MahiroJsonV1ImportResult.Failure)
            result as MahiroJsonV1ImportResult.Failure
            assertEquals(1, result.summary.insertedCount)
            assertEquals(1, result.summary.idempotentCount)
            assertEquals(1, result.summary.invalidCount)
            assertEquals(1, result.summary.failedCount)
            assertEquals(4, result.summary.processedCount)
            assertEquals(MahiroJsonV1ImportError.Storage(3), result.error)
            assertEquals(3, repository.insertCalls)
            assertEquals(
                listOf(UUID(0L, 1L), UUID(0L, 2L), UUID(0L, 3L)),
                repository.attempted.map { it.id }
            )
        }

    @Test
    fun `empty payload succeeds without repository calls`() = runBlocking {
        val repository = FakeRepository()

        val summary = service(repository).import("{\"weight\":55,\"events\":[]}").requireSuccess()

        assertEquals(55.0, summary.weight)
        assertEquals(0, summary.processedCount)
        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun `malformed document returns typed failure without repository calls`() = runBlocking {
        val repository = FakeRepository()

        val result = service(repository).import("{")

        assertTrue(result is MahiroJsonV1ImportResult.Failure)
        result as MahiroJsonV1ImportResult.Failure
        assertTrue(result.error is MahiroJsonV1ImportError.Document)
        assertEquals(MahiroJsonV1ImportSummary.empty(), result.summary)
        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun `repeated stable id payload consumes repository idempotency result`() = runBlocking {
        val repository = FakeRepository(InsertResult.Inserted, InsertResult.Idempotent)
        val content = document(event(stringId(EVENT_ID)))
        val importService = service(repository)

        val first = importService.import(content).requireSuccess()
        val second = importService.import(content).requireSuccess()

        assertEquals(1, first.insertedCount)
        assertEquals(1, second.idempotentCount)
        assertEquals(listOf(EVENT_ID, EVENT_ID), repository.attempted.map { it.id })
        assertEquals(2, repository.insertCalls)
    }

    @Test
    fun `repeated missing id payload creates independent identities`() = runBlocking {
        val generatedIds = ArrayDeque(listOf(UUID(0L, 201L), UUID(0L, 202L)))
        val repository = FakeRepository(InsertResult.Inserted, InsertResult.Inserted)
        val importService = service(
            repository,
            MahiroV1DoseEventAdapter { generatedIds.removeFirst() }
        )
        val content = document(event(idField = null))

        val first = importService.import(content).requireSuccess()
        val second = importService.import(content).requireSuccess()

        assertEquals(1, first.insertedCount)
        assertEquals(1, second.insertedCount)
        assertEquals(
            listOf(UUID(0L, 201L), UUID(0L, 202L)),
            repository.attempted.map { it.id }
        )
    }

    private fun service(
        repository: DoseEventRepository,
        adapter: MahiroV1DoseEventAdapter = MahiroV1DoseEventAdapter()
    ): MahiroJsonV1ImportService = MahiroJsonV1ImportService(
        repository = repository,
        adapter = adapter
    )

    private fun MahiroJsonV1ImportResult.requireSuccess(): MahiroJsonV1ImportSummary {
        assertTrue(this is MahiroJsonV1ImportResult.Success)
        return (this as MahiroJsonV1ImportResult.Success).summary
    }

    private fun twoValidEvents(): String = document(
        event(stringId(UUID(0L, 1L))),
        event(stringId(UUID(0L, 2L)))
    )

    private fun document(vararg events: String, weight: Double? = null): String {
        val weightField = weight?.let { "\"weight\":$it," }.orEmpty()
        return "{$weightField\"events\":[${events.joinToString(",")}]}"
    }

    private fun event(
        idField: String? = stringId(EVENT_ID),
        route: String = "oral",
        timeH: Double = 1.0,
        doseMG: Double = 2.0,
        extras: String = ""
    ): String {
        val idProperty = idField?.let { "$it," }.orEmpty()
        return "{$idProperty\"route\":\"$route\",\"ester\":\"E2\",\"timeH\":$timeH," +
            "\"doseMG\":$doseMG,\"extras\":{$extras}}"
    }

    private fun stringId(id: UUID): String = "\"id\":\"$id\""

    private class FakeRepository(vararg results: InsertResult) : DoseEventRepository {
        private val insertResults = ArrayDeque(results.toList())
        val attempted = mutableListOf<DoseEvent>()
        var insertCalls = 0
        var failureCall: Int? = null

        override fun observeAll(): Flow<List<DoseEvent>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: UUID): DoseEvent? = null
        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> = emptyList()

        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = emptyList()

        override suspend fun insert(event: DoseEvent): InsertResult {
            insertCalls += 1
            attempted += event
            if (insertCalls == failureCall) {
                throw IllegalStateException("synthetic storage failure")
            }
            return if (insertResults.isEmpty()) InsertResult.Inserted else insertResults.removeFirst()
        }

        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult =
            UpdateResult.Updated

        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.Deleted
        override suspend fun deleteAll(): DeleteResult = DeleteResult.Deleted
    }

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000701")
    }
}
