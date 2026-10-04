package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LabResultRepository
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1Codec
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DecodeResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1EntryError
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1LabImportMappingResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1LabMappingError
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1LabResultAdapter
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1LabResultDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class MahiroJsonV1LabResultsTest {
    @Test
    fun `codec decodes lab entries and reports bad ones by source index`() {
        val json = """
            {"events":[],"labResults":[
              {"id":"$LAB_A","timeH":492244.5,"concValue":180.5,"unit":"pg/ml","updatedAt":1},
              {"timeH":"soon","concValue":1,"unit":"pg/ml"},
              {"id":"not-a-uuid","timeH":492250,"concValue":600,"unit":"pmol/l"},
              7,
              {"timeH":492260,"concValue":300}
            ]}
        """.trimIndent()

        val decoded = MahiroV1Codec().decode(json) as MahiroV1DecodeResult.Success

        assertEquals(
            listOf(
                MahiroV1LabResultDto(LAB_A.toString(), 492244.5, 180.5, "pg/ml"),
                MahiroV1LabResultDto("not-a-uuid", 492250.0, 600.0, "pmol/l")
            ),
            decoded.document.labResults
        )
        assertEquals(
            listOf(
                1 to MahiroV1EntryError.InvalidFieldType("timeH"),
                3 to MahiroV1EntryError.ExpectedObject,
                4 to MahiroV1EntryError.MissingField("unit")
            ),
            decoded.labDiagnostics.map { it.index to it.error }
        )
        assertTrue(decoded.diagnostics.isEmpty())
    }

    @Test
    fun `codec ignores a labResults value that is not an array`() {
        val decoded = MahiroV1Codec().decode("""{"events":[],"labResults":{"x":1}}""")
            as MahiroV1DecodeResult.Success

        assertTrue(decoded.document.labResults.isEmpty())
        assertTrue(decoded.labDiagnostics.isEmpty())
    }

    @Test
    fun `adapter maps units values and time and rejects what calibration cannot use`() {
        val adapter = MahiroV1LabResultAdapter(uuidSupplier = { GENERATED })

        val mapped = adapter.toDomain(MahiroV1LabResultDto("not-a-uuid", 2.5, 367.1, "pmol/L"))
            as MahiroV1LabImportMappingResult.Success
        assertEquals(
            LabResult(GENERATED, Instant.ofEpochMilli(9_000_000L), 367.1, LabUnit.PMOL_PER_L),
            mapped.result
        )
        assertEquals(
            LabUnit.entries.toList(),
            LabUnit.entries.map {
                (adapter.toDomain(MahiroV1LabResultDto(null, 1.0, 1.0, it.mahiroCode))
                    as MahiroV1LabImportMappingResult.Success).result.unit
            }
        )
        assertEquals(
            MahiroV1LabMappingError.UnknownUnit("mg/dl"),
            (adapter.toDomain(MahiroV1LabResultDto(null, 1.0, 1.0, "mg/dl"))
                as MahiroV1LabImportMappingResult.Failure).error
        )
        listOf(0.0, -1.0, Double.NaN).forEach { value ->
            assertTrue(
                adapter.toDomain(MahiroV1LabResultDto(null, 1.0, value, "pg/ml"))
                    is MahiroV1LabImportMappingResult.Failure
            )
        }
        assertTrue(
            adapter.toDomain(MahiroV1LabResultDto(null, Double.POSITIVE_INFINITY, 1.0, "pg/ml"))
                is MahiroV1LabImportMappingResult.Failure
        )
    }

    @Test
    fun `import stores lab results and counts them apart from dose events`() = runBlocking {
        val labs = FakeLabRepository(InsertResult.Inserted, InsertResult.Idempotent, InsertResult.Conflict)
        val json = """
            {"events":[],"labResults":[
              {"id":"$LAB_A","timeH":1,"concValue":100,"unit":"pg/ml"},
              {"id":"$LAB_B","timeH":2,"concValue":200,"unit":"pg/ml"},
              {"timeH":3,"concValue":300,"unit":"lightyears"},
              {"id":"$LAB_C","timeH":4,"concValue":2,"unit":"nmol/l"},
              {"timeH":5}
            ]}
        """.trimIndent()

        val summary = (service(labs).import(json) as MahiroJsonV1ImportResult.Success).summary

        assertEquals(listOf(LAB_A, LAB_B, LAB_C), labs.attempted.map { it.id })
        assertEquals(MahiroJsonV1LabImportCounts(1, 1, 1, 2, 0), summary.labs)
        assertEquals(0, summary.processedCount)
    }

    @Test
    fun `import without a lab repository ignores labResults as before`() = runBlocking {
        val json = """{"events":[],"labResults":[{"timeH":1,"concValue":100,"unit":"pg/ml"}]}"""

        val summary = (MahiroJsonV1ImportService(NoDoseEvents()).import(json)
            as MahiroJsonV1ImportResult.Success).summary

        assertEquals(MahiroJsonV1LabImportCounts(), summary.labs)
    }

    @Test
    fun `lab storage failure stops the import and names the source index`() = runBlocking {
        val labs = FakeLabRepository().apply { failureCall = 2 }
        val json = """
            {"labResults":[
              {"timeH":1,"concValue":100,"unit":"pg/ml"},
              {"timeH":2},
              {"timeH":3,"concValue":300,"unit":"pg/ml"}
            ]}
        """.trimIndent()

        val result = service(labs).import(json) as MahiroJsonV1ImportResult.Failure

        assertEquals(MahiroJsonV1ImportError.LabStorage(2), result.error)
        assertEquals(MahiroJsonV1LabImportCounts(1, 0, 0, 1, 1), result.summary.labs)
    }

    @Test
    fun `lab entries count toward the import entry bound`() = runBlocking {
        val json = """{"events":[],"labResults":[
            {"timeH":1,"concValue":1,"unit":"pg/ml"},{"timeH":2,"concValue":1,"unit":"pg/ml"}]}"""
        val service = MahiroJsonV1ImportService(
            NoDoseEvents(),
            labRepository = FakeLabRepository(),
            maxEventCount = 1
        )

        val result = service.import(json) as MahiroJsonV1ImportResult.Failure

        assertEquals(MahiroJsonV1ImportError.TooLarge, result.error)
    }

    @Test
    fun `export writes lab results oldest first in Mahiro shape and round trips`() = runBlocking {
        val newer = LabResult(LAB_A, Instant.parse("2026-09-02T08:00:00Z"), 367.1, LabUnit.PMOL_PER_L)
        val older = LabResult(LAB_B, Instant.parse("2026-09-01T08:00:00.125Z"), 120.0, LabUnit.PG_PER_ML)
        val exported = MahiroJsonV1ExportService(clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
            .export(55.0, emptyList(), listOf(newer, older))

        val labsJson = Json.parseToJsonElement(exported).jsonObject.getValue("labResults").jsonArray
        assertEquals(
            listOf(LAB_B.toString(), LAB_A.toString()),
            labsJson.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        )
        assertEquals(
            setOf("id", "timeH", "concValue", "unit"),
            labsJson.first().jsonObject.keys
        )
        assertEquals("pmol/l", labsJson.last().jsonObject.getValue("unit").jsonPrimitive.content)

        val labs = FakeLabRepository()
        service(labs).import(exported)
        assertEquals(listOf(older, newer), labs.attempted)
    }

    @Test
    fun `export with no lab results still writes an empty labResults array`() {
        val exported = MahiroJsonV1ExportService(clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
            .export(55.0, emptyList())

        assertTrue(
            Json.parseToJsonElement(exported).jsonObject.getValue("labResults").jsonArray.isEmpty()
        )
    }

    private fun service(labs: LabResultRepository) =
        MahiroJsonV1ImportService(NoDoseEvents(), labRepository = labs)

    private class FakeLabRepository(vararg results: InsertResult) : LabResultRepository {
        private val insertResults = ArrayDeque(results.toList())
        val attempted = mutableListOf<LabResult>()
        var failureCall: Int? = null

        override fun observeAll(): Flow<List<LabResult>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: UUID): LabResult? = null
        override suspend fun insert(result: LabResult): InsertResult {
            attempted += result
            if (attempted.size == failureCall) throw IllegalStateException("synthetic lab failure")
            return insertResults.removeFirstOrNull() ?: InsertResult.Inserted
        }

        override suspend fun update(result: LabResult, expectedRevision: Long): UpdateResult =
            UpdateResult.Updated

        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult = ConditionalDeleteResult.NotFound
    }

    private class NoDoseEvents : DoseEventRepository {
        override fun observeAll(): Flow<List<DoseEvent>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: UUID): DoseEvent? = null
        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> = emptyList()

        override suspend fun findRecordedLocalDateBetween(
            startInclusive: LocalDate,
            endInclusive: LocalDate
        ): List<DoseEvent> = emptyList()

        override suspend fun findAllOccurredUpTo(endInclusive: Instant): List<DoseEvent> =
            emptyList()

        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = emptyList()
        override suspend fun insert(event: DoseEvent): InsertResult = InsertResult.Inserted
        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult =
            UpdateResult.Updated

        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.Deleted
        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult = ConditionalDeleteResult.NotFound

        override suspend fun deleteLatestRecordedIfRevisionMatches(
            eventId: UUID,
            eventRevision: Long
        ): LatestDoseDeleteResult = LatestDoseDeleteResult.EventNotFound

        override suspend fun deleteAll(): DeleteResult = DeleteResult.Deleted
    }

    private companion object {
        val LAB_A: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
        val LAB_B: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000b2")
        val LAB_C: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000c3")
        val GENERATED: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000ff")
    }
}
