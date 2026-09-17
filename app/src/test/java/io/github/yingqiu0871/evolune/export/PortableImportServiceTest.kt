package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * V17 Phase E E7.1/E7.5/E7.6 + E2.3 (bounds / zero writes) for the canonical import service
 * (frozen contract §18/§19/§32/§38).
 */
class PortableImportServiceTest {

    private fun canonicalBytes(events: List<io.github.yingqiu0871.evolune.core.model.DoseEvent>): ByteArray =
        requireNotNull(
            PortableJsonCodec.encode(
                PortableExportRequest(
                    range = PortableExportRange.ALL,
                    format = PortableExportFormat.JSON,
                    capturedAt = PORTABLE_CAPTURED_AT
                ),
                events.map { it.toPortableEvent() }
            )
        )

    @Test
    fun `round trip into an empty repository preserves the 11 portable fields with revision one`() = runBlocking {
        val original = nineClassFixture()
        val repository = RecordingDoseEventRepository()

        val outcome = PortableImportService(repository).import(canonicalBytes(original))

        assertTrue(outcome is PortableImportOutcome.Completed)
        assertEquals(9, (outcome as PortableImportOutcome.Completed).insertedCount)
        assertEquals(9, repository.rows.size)
        original.forEach { event ->
            val imported = requireNotNull(repository.rows[event.id])
            assertEquals(event.toPortableEventKey(), imported.toPortableEventKey())
            assertEquals(1L, imported.revision)
        }
        assertEquals(0, repository.updateCalls)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun `replaying the same file is idempotent and never duplicates rows`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        val service = PortableImportService(repository)
        val bytes = canonicalBytes(nineClassFixture())

        service.import(bytes)
        val insertsAfterFirst = repository.insertCalls
        val outcome = service.import(bytes)

        assertTrue(outcome is PortableImportOutcome.Completed)
        assertEquals(0, (outcome as PortableImportOutcome.Completed).insertedCount)
        assertEquals(9, outcome.idempotentCount)
        assertEquals(0, outcome.conflictCount)
        assertEquals(insertsAfterFirst, repository.insertCalls)
        assertEquals(9, repository.rows.size)
    }

    @Test
    fun `same id with a changed portable payload is a conflict and is never overwritten`() = runBlocking {
        val event = syntheticEvent(id = eventId(501L), doseMG = 5.0)
        val stored = event.copy(doseMG = 9.0)
        val repository = RecordingDoseEventRepository(listOf(stored))

        val outcome = PortableImportService(repository).import(canonicalBytes(listOf(event)))

        assertTrue(outcome is PortableImportOutcome.Completed)
        assertEquals(0, (outcome as PortableImportOutcome.Completed).insertedCount)
        assertEquals(1, outcome.conflictCount)
        assertEquals(0, outcome.idempotentCount)
        assertEquals(stored, repository.rows[eventId(501L)])
        assertEquals(0, repository.insertCalls)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun `stored revision above one with equal portable fields is idempotent with zero writes`() = runBlocking {
        val event = syntheticEvent(id = eventId(502L), doseMG = 5.0)
        val stored = event.copy(revision = 5L)
        val repository = RecordingDoseEventRepository(listOf(stored))

        val outcome = PortableImportService(repository).import(canonicalBytes(listOf(event)))

        assertTrue(outcome is PortableImportOutcome.Completed)
        assertEquals(0, (outcome as PortableImportOutcome.Completed).insertedCount)
        assertEquals(1, outcome.idempotentCount)
        assertEquals(0, outcome.conflictCount)
        assertEquals(0, repository.insertCalls)
        assertEquals(0, repository.updateCalls)
        assertEquals(0, repository.writeCalls)
        assertEquals(stored, repository.rows[eventId(502L)])
    }

    @Test
    fun `stored revision above one with a differing portable field is a conflict with zero writes`() = runBlocking {
        val event = syntheticEvent(id = eventId(503L), doseMG = 5.0)
        val stored = event.copy(doseMG = 6.0, revision = 7L)
        val repository = RecordingDoseEventRepository(listOf(stored))

        val outcome = PortableImportService(repository).import(canonicalBytes(listOf(event)))

        assertTrue(outcome is PortableImportOutcome.Completed)
        assertEquals(0, (outcome as PortableImportOutcome.Completed).insertedCount)
        assertEquals(1, outcome.conflictCount)
        assertEquals(0, repository.writeCalls)
        assertEquals(stored, repository.rows[eventId(503L)])
    }

    @Test
    fun `storage failure after N writes reports a typed partial failure and keeps committed rows`() = runBlocking {
        val events = listOf(
            syntheticEvent(id = eventId(601L)),
            syntheticEvent(id = eventId(602L)),
            syntheticEvent(id = eventId(603L))
        )
        val repository = RecordingDoseEventRepository()
        var insertsSeen = 0
        repository.beforeInsert = {
            insertsSeen += 1
            if (insertsSeen == 3) throw FakeStorageException("disk full")
        }
        val service = PortableImportService(repository)

        val outcome = service.import(canonicalBytes(events))

        assertTrue(outcome is PortableImportOutcome.PartialFailure)
        val partial = outcome as PortableImportOutcome.PartialFailure
        assertEquals(2, partial.processedCount)
        assertEquals(3, partial.totalCount)
        assertEquals(2, partial.insertedCount)
        assertEquals(2, repository.rows.size)

        repository.beforeInsert = null
        val retry = service.import(canonicalBytes(events))
        assertTrue(retry is PortableImportOutcome.Completed)
        assertEquals(1, (retry as PortableImportOutcome.Completed).insertedCount)
        assertEquals(2, retry.idempotentCount)
        assertEquals(3, repository.rows.size)
    }

    @Test
    fun `storage failure on the first write reports a typed storage failure`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        repository.insertFailure = FakeStorageException("io error")

        val outcome = PortableImportService(repository).import(canonicalBytes(nineClassFixture()))

        assertEquals(PortableImportOutcome.StorageFailure(9), outcome)
        assertEquals(0, repository.rows.size)
    }

    @Test
    fun `read failure during classification aborts as a storage failure`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        repository.getByIdFailure = FakeStorageException("io error")

        val outcome = PortableImportService(repository).import(canonicalBytes(nineClassFixture()))

        assertEquals(PortableImportOutcome.StorageFailure(9), outcome)
        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun `repository invalid insert results are counted as conflicts without overwriting`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        repository.insertResultOverride = { InsertResult.Invalid }

        val outcome = PortableImportService(repository).import(canonicalBytes(listOf(syntheticEvent())))

        assertTrue(outcome is PortableImportOutcome.Completed)
        assertEquals(1, (outcome as PortableImportOutcome.Completed).conflictCount)
        assertEquals(0, repository.rows.size)
    }

    @Test
    fun `byte bound rejects oversized input before any repository call`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        val service = PortableImportService(repository, maxInputBytes = 16)

        val outcome = service.import(canonicalBytes(nineClassFixture()))

        assertEquals(PortableImportOutcome.TooLarge, outcome)
        assertEquals(0, repository.getByIdCalls)
        assertEquals(0, repository.writeCalls)
    }

    @Test
    fun `event count bound rejects oversized documents before any repository call`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        val service = PortableImportService(repository, maxEventCount = 2)

        val outcome = service.import(canonicalBytes(nineClassFixture()))

        assertEquals(PortableImportOutcome.TooLarge, outcome)
        assertEquals(0, repository.getByIdCalls)
        assertEquals(0, repository.writeCalls)
    }

    @Test
    fun `invalid documents and unsupported versions are typed failures with zero writes`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        val service = PortableImportService(repository)

        val invalid = service.import("{ not json".toByteArray(Charsets.UTF_8))
        assertEquals(PortableImportOutcome.InvalidDocument, invalid)
        val unsupported = service.import(
            canonicalBytes(listOf(syntheticEvent()))
                .toString(Charsets.UTF_8)
                .replace("\"version\": 1", "\"version\": 2")
                .toByteArray(Charsets.UTF_8)
        )
        assertEquals(PortableImportOutcome.UnsupportedVersion, unsupported)
        assertEquals(0, repository.getByIdCalls)
        assertEquals(0, repository.writeCalls)
    }

    @Test
    fun `failed classification never fabricates identities or writes`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        val service = PortableImportService(repository)
        val tampered = canonicalBytes(listOf(syntheticEvent()))
            .toString(Charsets.UTF_8)
            .replace("\"id\": \"00000000-0000-0000-0000-000000000001\"", "\"id\": \"not-a-uuid\"")

        val outcome = service.import(tampered.toByteArray(Charsets.UTF_8))

        assertEquals(PortableImportOutcome.InvalidDocument, outcome)
        assertEquals(0, repository.writeCalls)
        assertNull(repository.rows[eventId(1L)])
    }
}
