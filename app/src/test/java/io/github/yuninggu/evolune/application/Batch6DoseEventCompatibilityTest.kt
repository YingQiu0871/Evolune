package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.dataapi.DeleteResult
import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.dataapi.UpdateResult
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.utils.MahiroJsonFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class Batch6DoseEventCompatibilityTest {
    @Test
    fun `JSON bridge preserves valid id time fields and locked metadata`() = runBlocking {
        val repository = FakeRepository()
        val bridge = Batch6MahiroJsonBridge(repository)

        val outcome = bridge.import(json(id = EVENT_ID.toString()))
        val event = repository.inserted.single()

        assertEquals(1, outcome.insertedCount)
        assertEquals(EVENT_ID, event.id)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_125L), event.occurredAt)
        assertNull(event.zoneId)
        assertNull(event.localDate)
        assertNull(event.slotId)
        assertEquals(DoseEventSource.JSON_V1, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertEquals(1L, event.revision)
        assertEquals(2.0, event.extras[ExtraKey.SUBLINGUAL_TIER])
    }

    @Test
    fun `JSON bridge retains parser random UUID behavior for missing and corrupt ids`() = runBlocking {
        val repository = FakeRepository()
        val bridge = Batch6MahiroJsonBridge(repository)

        bridge.import(json(id = null))
        bridge.import(json(id = "not-a-uuid"))

        assertEquals(2, repository.inserted.size)
        assertNotEquals(EVENT_ID, repository.inserted[0].id)
        assertNotEquals(EVENT_ID, repository.inserted[1].id)
        assertNotEquals(repository.inserted[0].id, repository.inserted[1].id)
    }

    @Test
    fun `JSON insert outcomes are counted without overwrite fallback`() = runBlocking {
        val results = ArrayDeque(
            listOf(
                InsertResult.Inserted,
                InsertResult.Idempotent,
                InsertResult.Conflict,
                InsertResult.Invalid
            )
        )
        val repository = FakeRepository().apply { resultProvider = { results.removeFirst() } }
        val bridge = Batch6MahiroJsonBridge(repository)
        val content = jsonWithIds((1L..4L).map { UUID(0L, it) })

        val outcome = bridge.import(content)

        assertEquals(1, outcome.insertedCount)
        assertEquals(1, outcome.idempotentCount)
        assertEquals(1, outcome.conflictCount)
        assertEquals(1, outcome.invalidCount)
        assertEquals(2, outcome.acceptedCount)
        assertEquals(4, repository.insertCalls)
    }

    @Test(expected = IllegalStateException::class)
    fun `JSON storage failure propagates and has no fallback`() {
        runBlocking {
            val repository = FakeRepository().apply {
                insertError = IllegalStateException("synthetic storage failure")
            }
            Batch6MahiroJsonBridge(repository).import(json(id = EVENT_ID.toString()))
        }
    }

    @Test
    fun `temporary export preserves Mahiro v1 fields and ordering`() {
        val repository = FakeRepository()
        val bridge = Batch6MahiroJsonBridge(repository)
        val events = listOf(event(UUID(0L, 2L), 2_000L), event(UUID(0L, 1L), 1_000L))

        val actual = bridge.export(55.0, events)
        val expected = MahiroJsonFormat.generateExport(
            55.0,
            Batch6HrtPkProjection.project(events)
        )
        val actualRoot = Json.parseToJsonElement(actual).jsonObject
        val expectedRoot = Json.parseToJsonElement(expected).jsonObject

        Instant.parse(actualRoot.getValue("meta").jsonObject.getValue("exportedAt").jsonPrimitive.content)
        assertEquals(expectedRoot.keys.toList(), actualRoot.keys.toList())
        assertEquals(
            events.map { it.id.toString() },
            actualRoot.getValue("events").jsonArray.map {
                it.jsonObject.getValue("id").jsonPrimitive.content
            }
        )
        assertEquals(withoutExportedAt(expectedRoot), withoutExportedAt(actualRoot))
    }

    @Test
    fun `PK projection preserves order id time route ester dose and extras`() {
        val events = listOf(
            event(UUID(0L, 2L), -1_000L),
            event(UUID(0L, 1L), 1_700_000_000_125L)
        )

        val projected = Batch6HrtPkProjection.project(events)

        assertEquals(events.map { it.id }, projected.map { it.id })
        assertEquals(-1_000L / 3_600_000.0, projected[0].timeH, 0.0)
        assertEquals(1_700_000_000_125L / 3_600_000.0, projected[1].timeH, 0.0)
        assertEquals(events[0].route, projected[0].route)
        assertEquals(events[0].ester, projected[0].ester)
        assertEquals(events[0].doseMG, projected[0].doseMG, 0.0)
        assertEquals(
            events[0].extras.values.single(),
            projected[0].extras.values.single(),
            0.0
        )
    }

    @Test
    fun `PK projection ignores Domain-only metadata without mutating source events`() {
        val event = event(EVENT_ID, 1_000L).copy(
            slotId = UUID(0L, 99L),
            source = DoseEventSource.WEAR,
            revision = 8L
        )

        val projected = Batch6HrtPkProjection.project(event)

        assertEquals(event.id, projected.id)
        assertEquals(DoseEventSource.WEAR, event.source)
        assertEquals(8L, event.revision)
        assertEquals(UUID(0L, 99L), event.slotId)
    }

    private fun event(id: UUID, epochMillis: Long): DoseEvent = DoseEvent(
        id = id,
        route = Route.SUBLINGUAL,
        occurredAt = Instant.ofEpochMilli(epochMillis),
        zoneId = null,
        localDate = null,
        doseMG = 2.0,
        ester = Ester.E2,
        extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
        slotId = null,
        source = DoseEventSource.JSON_V1,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )

    private fun json(id: String?): String {
        val idField = id?.let { "\"id\":\"$it\"," }.orEmpty()
        return """
            {
              "weight": 55,
              "events": [{
                $idField
                "route":"sublingual",
                "ester":"E2",
                "timeH":${1_700_000_000_125L / 3_600_000.0},
                "doseMG":2.0,
                "extras":{"sublingualTier":2.0}
              }]
            }
        """.trimIndent()
    }

    private fun jsonWithIds(ids: List<UUID>): String = """
        {
          "events": [
            ${ids.joinToString(",") { id ->
                """{"id":"$id","route":"oral","ester":"E2","timeH":1.0,"doseMG":2.0,"extras":{}}"""
            }}
          ]
        }
    """.trimIndent()

    private fun withoutExportedAt(root: JsonObject): JsonObject = JsonObject(
        root.toMutableMap().apply {
            put(
                "meta",
                JsonObject(getValue("meta").jsonObject.toMutableMap().apply {
                    remove("exportedAt")
                })
            )
        }
    )

    private class FakeRepository : DoseEventRepository {
        val events = MutableStateFlow<List<DoseEvent>>(emptyList())
        val inserted = mutableListOf<DoseEvent>()
        var insertCalls = 0
        var insertError: IllegalStateException? = null
        var resultProvider: () -> InsertResult = { InsertResult.Inserted }

        override fun observeAll(): Flow<List<DoseEvent>> = events
        override suspend fun getById(id: UUID): DoseEvent? = events.value.firstOrNull { it.id == id }
        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> = emptyList()
        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = events.value
        override suspend fun insert(event: DoseEvent): InsertResult {
            insertCalls += 1
            insertError?.let { throw it }
            inserted += event
            return resultProvider()
        }
        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult =
            UpdateResult.Updated
        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.Deleted
        override suspend fun deleteAll(): DeleteResult = DeleteResult.Deleted
    }

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
