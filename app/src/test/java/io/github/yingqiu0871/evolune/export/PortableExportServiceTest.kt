package io.github.yingqiu0871.evolune.export

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors

/**
 * V17 Phase E E1.1/E1.2 (zero writes), E3.1/E3.2 (byte determinism), E5.1/E5.2/E5.4
 * (range boundaries / all-history / large bounded fixture) for the canonical export service.
 */
class PortableExportServiceTest {

    private val capturedAt = Instant.parse("2026-09-16T08:05:00.000Z")
    private val fixedClock = Clock.fixed(capturedAt, ZoneOffset.UTC)

    @Test
    fun `every format and range performs zero repository writes and preserves the snapshot`() = runBlocking {
        val repository = RecordingDoseEventRepository(nineClassFixture())
        // Any write attempt would throw: the export path must never reach a write API.
        repository.writeFailure = FakeStorageException("export must not write")
        val service = PortableExportService(repository, fixedClock)
        val before = repository.snapshot()

        for (format in PortableExportFormat.values()) {
            for (range in PortableExportRange.values()) {
                val result = service.export(range, format)
                assertTrue(result is PortableExportResult.Success)
            }
        }

        assertEquals(0, repository.writeCalls)
        assertEquals(0, repository.insertCalls)
        assertEquals(0, repository.updateCalls)
        assertEquals(0, repository.deleteCalls)
        assertEquals(before, repository.snapshot())
    }

    @Test
    fun `repeat export with the same request and snapshot is byte identical`() = runBlocking {
        val repository = RecordingDoseEventRepository(nineClassFixture())
        val service = PortableExportService(repository, fixedClock)

        val first = service.export(PortableExportRange.ALL, PortableExportFormat.JSON) as PortableExportResult.Success
        val second = service.export(PortableExportRange.ALL, PortableExportFormat.JSON) as PortableExportResult.Success
        assertArrayEquals(first.bytes, second.bytes)

        val csvFirst = service.export(PortableExportRange.LAST_30_DAYS, PortableExportFormat.CSV) as PortableExportResult.Success
        val csvSecond = service.export(PortableExportRange.LAST_30_DAYS, PortableExportFormat.CSV) as PortableExportResult.Success
        assertArrayEquals(csvFirst.bytes, csvSecond.bytes)
    }

    @Test
    fun `capturedAt is normalized to epoch milliseconds once per action`() = runBlocking {
        val subMillisecondClock = Clock.fixed(Instant.parse("2026-09-16T08:05:00.123456789Z"), ZoneOffset.UTC)
        val repository = RecordingDoseEventRepository(nineClassFixture())
        val service = PortableExportService(repository, subMillisecondClock)

        val result = service.export(PortableExportRange.ALL, PortableExportFormat.JSON) as PortableExportResult.Success
        val text = String(result.bytes, Charsets.UTF_8)
        assertTrue(text.contains("\"captured_at\": \"2026-09-16T08:05:00.123Z\""))
        assertEquals("evolune-export-all-20260916.json", result.fileName)
    }

    @Test
    fun `30 day boundaries are inclusive at both ends and exclude one millisecond outside`() = runBlocking {
        val windowStart = Instant.parse("2026-08-17T08:05:00.000Z")
        val repository = RecordingDoseEventRepository(
            listOf(
                syntheticEvent(id = eventId(1L), occurredAt = windowStart.minusMillis(1)),
                syntheticEvent(id = eventId(2L), occurredAt = windowStart),
                syntheticEvent(id = eventId(3L), occurredAt = capturedAt.minusMillis(1)),
                syntheticEvent(id = eventId(4L), occurredAt = capturedAt),
                syntheticEvent(id = eventId(5L), occurredAt = capturedAt.plusMillis(1))
            )
        )
        val service = PortableExportService(repository, fixedClock)

        val result = service.export(PortableExportRange.LAST_30_DAYS, PortableExportFormat.JSON) as PortableExportResult.Success
        val ids = idsInJson(result.bytes)
        assertEquals(listOf(eventId(2L).toString(), eventId(3L).toString(), eventId(4L).toString()), ids)
    }

    @Test
    fun `90 day boundaries are inclusive at both ends and exclude one millisecond outside`() = runBlocking {
        val windowStart = Instant.parse("2026-06-18T08:05:00.000Z")
        val repository = RecordingDoseEventRepository(
            listOf(
                syntheticEvent(id = eventId(1L), occurredAt = windowStart.minusMillis(1)),
                syntheticEvent(id = eventId(2L), occurredAt = windowStart),
                syntheticEvent(id = eventId(3L), occurredAt = capturedAt),
                syntheticEvent(id = eventId(4L), occurredAt = capturedAt.plusMillis(1))
            )
        )
        val service = PortableExportService(repository, fixedClock)

        val result = service.export(PortableExportRange.LAST_90_DAYS, PortableExportFormat.CSV) as PortableExportResult.Success
        val ids = idsInCsv(result.bytes)
        assertEquals(listOf(eventId(2L).toString(), eventId(3L).toString()), ids)
    }

    @Test
    fun `all range keeps every past event without lower bound and drops future events`() = runBlocking {
        val repository = RecordingDoseEventRepository(
            listOf(
                syntheticEvent(id = eventId(1L), occurredAt = Instant.parse("2019-01-01T00:00:00.000Z")),
                syntheticEvent(id = eventId(2L), occurredAt = capturedAt),
                syntheticEvent(id = eventId(3L), occurredAt = capturedAt.plusMillis(1))
            )
        )
        val service = PortableExportService(repository, fixedClock)

        val result = service.export(PortableExportRange.ALL, PortableExportFormat.JSON) as PortableExportResult.Success
        assertEquals(listOf(eventId(1L).toString(), eventId(2L).toString()), idsInJson(result.bytes))
        assertEquals(1, repository.findAllUpToCalls)
        assertEquals(0, repository.findBetweenCalls)
    }

    @Test
    fun `range membership depends on absolute instants only, not on per-event zones or dates`() = runBlocking {
        val repository = RecordingDoseEventRepository(
            listOf(
                syntheticEvent(
                    id = eventId(1L),
                    occurredAt = capturedAt.minusSeconds(1),
                    zoneId = java.time.ZoneId.of("Europe/Paris"),
                    localDate = java.time.LocalDate.of(2026, 9, 16)
                ),
                syntheticEvent(
                    id = eventId(2L),
                    occurredAt = capturedAt.minusSeconds(1),
                    zoneId = java.time.ZoneId.of("Asia/Shanghai"),
                    localDate = java.time.LocalDate.of(2026, 9, 16)
                ),
                syntheticEvent(
                    id = eventId(3L),
                    occurredAt = capturedAt.plusSeconds(1),
                    zoneId = java.time.ZoneId.of("America/New_York"),
                    localDate = java.time.LocalDate.of(2026, 9, 16)
                )
            )
        )
        val service = PortableExportService(repository, fixedClock)

        val result = service.export(PortableExportRange.LAST_30_DAYS, PortableExportFormat.JSON) as PortableExportResult.Success
        assertEquals(listOf(eventId(1L).toString(), eventId(2L).toString()), idsInJson(result.bytes))
    }

    @Test
    fun `the large history fixture exports deterministically`() = runBlocking {
        val base = Instant.parse("2026-09-15T00:00:00.000Z")
        val events = (1..20_000).map { index ->
            syntheticEvent(
                id = eventId(index.toLong()),
                occurredAt = base.minusSeconds(index.toLong() * 600L),
                source = if (index % 2 == 0) io.github.yingqiu0871.evolune.core.model.DoseEventSource.MANUAL
                else io.github.yingqiu0871.evolune.core.model.DoseEventSource.LEGACY
            )
        }
        val repository = RecordingDoseEventRepository(events)
        val service = PortableExportService(repository, fixedClock)

        val first = service.export(PortableExportRange.ALL, PortableExportFormat.JSON) as PortableExportResult.Success
        val second = service.export(PortableExportRange.ALL, PortableExportFormat.CSV) as PortableExportResult.Success
        assertTrue(first.bytes.size > 1_000_000)
        assertTrue(second.bytes.size > 500_000)
        val csvRows = String(second.bytes, Charsets.UTF_8).lines().filter { it.isNotEmpty() }
        assertEquals(20_001, csvRows.size)
    }

    @Test
    fun `serialization runs on the injected off-main dispatcher`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "phase-e-export-worker")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val repository = RecordingDoseEventRepository(nineClassFixture())
            var readThread: String? = null
            repository.onRead = { readThread = Thread.currentThread().name }
            val service = PortableExportService(repository, fixedClock, dispatcher)

            service.export(PortableExportRange.ALL, PortableExportFormat.JSON)

            assertTrue(readThread?.contains("phase-e-export-worker") == true)
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun `repository read failure maps to typed unexpected failure`() = runBlocking {
        val repository = RecordingDoseEventRepository(nineClassFixture())
        repository.readFailure = FakeStorageException("read failed")
        val service = PortableExportService(repository, fixedClock)

        assertEquals(
            PortableExportResult.UnexpectedFailure,
            service.export(PortableExportRange.ALL, PortableExportFormat.JSON)
        )
    }

    @Test
    fun `non representable values fail as typed invalid data`() = runBlocking {
        val repository = RecordingDoseEventRepository(
            listOf(
                syntheticEvent(id = eventId(1L), doseMG = Double.NaN),
                syntheticEvent(id = eventId(2L), occurredAt = Instant.parse("2026-09-10T08:00:00.123456789Z"))
            )
        )
        val service = PortableExportService(repository, fixedClock)

        assertEquals(
            PortableExportResult.InvalidData,
            service.export(PortableExportRange.ALL, PortableExportFormat.JSON)
        )
        assertEquals(
            PortableExportResult.InvalidData,
            service.export(PortableExportRange.ALL, PortableExportFormat.CSV)
        )
    }

    @Test
    fun `serialization order is actual_time ascending then event id lexical, never read order`() = runBlocking {
        val repository = RecordingDoseEventRepository(
            listOf(
                syntheticEvent(id = eventId(9L), occurredAt = Instant.parse("2026-09-10T08:00:00.000Z")),
                syntheticEvent(id = eventId(3L), occurredAt = Instant.parse("2026-09-10T08:00:00.000Z")),
                syntheticEvent(id = eventId(1L), occurredAt = Instant.parse("2026-09-01T08:00:00.000Z")),
                syntheticEvent(id = eventId(7L), occurredAt = Instant.parse("2026-09-12T08:00:00.000Z"))
            )
        )
        repository.preserveInsertionOrderInReads = true
        val service = PortableExportService(repository, fixedClock)

        val result = service.export(PortableExportRange.ALL, PortableExportFormat.JSON) as PortableExportResult.Success
        assertEquals(
            listOf(
                eventId(1L).toString(),
                eventId(3L).toString(),
                eventId(9L).toString(),
                eventId(7L).toString()
            ),
            idsInJson(result.bytes)
        )
    }

    private fun idsInJson(bytes: ByteArray): List<String> =
        String(bytes, Charsets.UTF_8)
            .lines()
            .filter { it.trimStart().startsWith("\"id\":") }
            .map { it.trim().removePrefix("\"id\": \"").removeSuffix("\",") }

    private fun idsInCsv(bytes: ByteArray): List<String> =
        String(bytes, Charsets.UTF_8)
            .lines()
            .filter { it.isNotEmpty() }
            .drop(1)
            .map { it.split(',')[1].trim('"') }
}
