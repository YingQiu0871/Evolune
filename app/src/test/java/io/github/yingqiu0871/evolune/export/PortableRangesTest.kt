package io.github.yingqiu0871.evolune.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * V17 Phase E E5.1/E5.3 — absolute-Instant range resolution (frozen contract §8):
 * exactly 30/90 * 24h, both ends inclusive, no DST/calendar/device-zone semantics.
 */
class PortableRangesTest {

    private val capturedAt = Instant.parse("2026-09-16T08:05:00.000Z")

    @Test
    fun `30 day range starts exactly 720 hours before capturedAt`() {
        val resolved = resolvePortableRange(PortableExportRange.LAST_30_DAYS, capturedAt)
        assertEquals(Instant.parse("2026-08-17T08:05:00.000Z"), resolved.startInclusive)
        assertEquals(capturedAt, resolved.endInclusive)
    }

    @Test
    fun `90 day range starts exactly 2160 hours before capturedAt`() {
        val resolved = resolvePortableRange(PortableExportRange.LAST_90_DAYS, capturedAt)
        assertEquals(Instant.parse("2026-06-18T08:05:00.000Z"), resolved.startInclusive)
        assertEquals(capturedAt, resolved.endInclusive)
    }

    @Test
    fun `all range has no lower bound and capturedAt inclusive upper bound`() {
        val resolved = resolvePortableRange(PortableExportRange.ALL, capturedAt)
        assertNull(resolved.startInclusive)
        assertEquals(capturedAt, resolved.endInclusive)
    }

    @Test
    fun `range resolution ignores calendar month lengths and DST transitions`() {
        // 30 * 24h across a European DST transition is still exactly 720h of absolute time.
        val dstCapturedAt = Instant.parse("2026-11-02T00:30:00.000Z")
        val resolved = resolvePortableRange(PortableExportRange.LAST_30_DAYS, dstCapturedAt)
        assertEquals(720L * 3_600_000L, resolved.endInclusive.toEpochMilli() - resolved.startInclusive!!.toEpochMilli())
    }

    @Test
    fun `file names use frozen range tokens and UTC date`() {
        assertEquals(
            "evolune-export-30d-20260916.json",
            portableExportFileName(PortableExportRange.LAST_30_DAYS, PortableExportFormat.JSON, capturedAt)
        )
        assertEquals(
            "evolune-export-90d-20260916.csv",
            portableExportFileName(PortableExportRange.LAST_90_DAYS, PortableExportFormat.CSV, capturedAt)
        )
        assertEquals(
            "evolune-export-all-20260916.json",
            portableExportFileName(PortableExportRange.ALL, PortableExportFormat.JSON, capturedAt)
        )
    }
}
