package io.github.yingqiu0871.evolune.export

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

/**
 * V17 Phase E E3.1/E3.2/E7.1/E7.2/E7.3 — golden byte fixtures bound to the frozen canonical
 * formats: the same fixed request and snapshot must reproduce the committed bytes exactly.
 */
class PortableGoldenFixtureTest {

    private fun goldenBytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/phase-e/$name")) { "missing golden $name" }
            .use { it.readBytes() }

    @Test
    fun `the canonical JSON byte fixture is reproduced exactly`() {
        runBlocking {
            val repository = RecordingDoseEventRepository(nineClassFixture())
            val service = PortableExportService(repository, Clock.fixed(PORTABLE_CAPTURED_AT, ZoneOffset.UTC))

            val result = service.export(PortableExportRange.ALL, PortableExportFormat.JSON) as PortableExportResult.Success

            assertTrue(goldenBytes("golden-canonical.json").contentEquals(result.bytes))
            assertEquals("evolune-export-all-20260916.json", result.fileName)
        }
    }

    @Test
    fun `the canonical CSV byte fixture is reproduced exactly`() {
        runBlocking {
            val repository = RecordingDoseEventRepository(nineClassFixture())
            val service = PortableExportService(repository, Clock.fixed(PORTABLE_CAPTURED_AT, ZoneOffset.UTC))

            val result = service.export(PortableExportRange.ALL, PortableExportFormat.CSV) as PortableExportResult.Success

            assertTrue(goldenBytes("golden-canonical.csv").contentEquals(result.bytes))
            assertEquals("evolune-export-all-20260916.csv", result.fileName)
        }
    }

    @Test
    fun `the golden JSON round trips into an empty repository without semantic loss`() {
        runBlocking {
            val original = nineClassFixture()
            val repository = RecordingDoseEventRepository()

            val outcome = PortableImportService(repository).import(goldenBytes("golden-canonical.json"))

            assertTrue(outcome is PortableImportOutcome.Completed)
            assertEquals(9, (outcome as PortableImportOutcome.Completed).insertedCount)
            original.forEach { event ->
                val imported = requireNotNull(repository.rows[event.id])
                assertEquals(event.toPortableEventKey(), imported.toPortableEventKey())
            }
        }
    }
}
