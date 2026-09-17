package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.application.MahiroJsonV1ExportService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * V17 Phase E E2.6/E2.4 — cross-format no-fallthrough and legacy permissive compatibility
 * (frozen contract §20/§40).
 */
class PortableCrossFormatTest {

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
    fun `a valid canonical document via the legacy Mahiro surface writes nothing`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        val service = io.github.yingqiu0871.evolune.application.MahiroJsonV1ImportService(repository)

        val result = service.import(String(canonicalBytes(nineClassFixture()), Charsets.UTF_8))

        assertTrue(
            result is io.github.yingqiu0871.evolune.application.MahiroJsonV1ImportResult.Success
        )
        val summary = (result as io.github.yingqiu0871.evolune.application.MahiroJsonV1ImportResult.Success).summary
        assertEquals(0, summary.acceptedCount)
        assertEquals(0, summary.insertedCount)
        assertEquals(0, summary.idempotentCount)
        assertNull(summary.weight)
        assertEquals(0, repository.insertCalls)
        assertEquals(0, repository.writeCalls)
        assertEquals(0, repository.rows.size)
    }

    @Test
    fun `legacy documents without meta remain accepted`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        val service = io.github.yingqiu0871.evolune.application.MahiroJsonV1ImportService(repository)

        val result = service.import(
            """{"events":[{"route":"injection","ester":"EV","timeH":496000.0,"doseMG":5.0}]}"""
        )

        assertTrue(
            result is io.github.yingqiu0871.evolune.application.MahiroJsonV1ImportResult.Success
        )
        assertEquals(1, repository.insertCalls)
        assertEquals(1, repository.rows.size)
    }

    @Test
    fun `unrelated meta versions and unknown fields stay tolerated and are never a canonical gate`() = runBlocking {
        val repository = RecordingDoseEventRepository()
        val service = io.github.yingqiu0871.evolune.application.MahiroJsonV1ImportService(repository)

        val result = service.import(
            """{"meta":{"version":999},"futureField":{"x":1},"events":[
                {"id":"00000000-0000-0000-0000-0000000000aa","route":"oral","ester":"E2","timeH":496000.0,"doseMG":2.0,"extras":{"sublingualTier":2}}
            ]}"""
        )

        assertTrue(
            result is io.github.yingqiu0871.evolune.application.MahiroJsonV1ImportResult.Success
        )
        assertEquals(
            java.util.UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
            repository.rows.keys.single()
        )
    }

    @Test
    fun `legacy export failures are typed and never escape as IllegalArgumentException`() = runBlocking {
        val service = MahiroJsonV1ExportService(clock = Clock.fixed(Instant.parse("2026-09-16T08:05:00Z"), ZoneOffset.UTC))
        val runner = LegacyMahiroExportRunner(service)

        val unrepresentable = syntheticEvent(occurredAt = Instant.MAX)
        assertEquals(
            LegacyMahiroExportOutcome.InvalidData,
            runner.export(55.0, listOf(unrepresentable))
        )

        val representable = syntheticEvent()
        val success = runner.export(55.0, listOf(representable))
        assertTrue(success is LegacyMahiroExportOutcome.Success)
        assertTrue((success as LegacyMahiroExportOutcome.Success).json.contains("\"weight\": 55.0"))
    }

    @Test
    fun `unexpected runtime failures map to a typed unexpected outcome`() {
        val invalidRunner = LegacyMahiroExportRunner { _, _ -> throw IllegalArgumentException("mapped") }
        assertEquals(
            LegacyMahiroExportOutcome.InvalidData,
            invalidRunner.export(55.0, emptyList())
        )
        val failingRunner = LegacyMahiroExportRunner { _, _ -> throw IllegalStateException("boom") }
        assertEquals(
            LegacyMahiroExportOutcome.UnexpectedFailure,
            failingRunner.export(55.0, emptyList())
        )
    }
}
