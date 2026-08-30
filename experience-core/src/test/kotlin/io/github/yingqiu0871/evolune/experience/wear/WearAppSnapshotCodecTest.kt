package io.github.yingqiu0871.evolune.experience.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WearAppSnapshotCodecTest {
    private val snapshot = WearAppSnapshot(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        snapshotRevision = 7L,
        generatedAt = Instant.parse("2026-08-30T10:00:00Z"),
        zoneId = "Asia/Shanghai",
        overallStatus = WearAppOverallStatus.READY,
        recentDose = WearAppRecentDose(
            eventId = UUID(0L, 1L),
            planId = UUID(0L, 2L),
            slotId = UUID(0L, 3L),
            localDate = LocalDate.of(2026, 8, 30),
            occurredAt = Instant.parse("2026-08-30T09:00:00Z"),
            medicationName = "Estradiol",
            route = "ORAL",
            dose = 2.0,
            doseUnit = WearAppSnapshotRules.DOSE_UNIT_MILLIGRAM,
            source = "MANUAL"
        ),
        upcomingOccurrences = listOf(
            WearAppUpcomingOccurrence(
                occurrenceId = UUID(0L, 4L),
                planId = UUID(0L, 2L),
                slotId = UUID(0L, 3L),
                localDate = LocalDate.of(2026, 8, 30),
                scheduledAt = Instant.parse("2026-08-30T12:00:00Z"),
                medicationName = "Estradiol",
                route = "ORAL",
                dose = 2.0,
                doseUnit = WearAppSnapshotRules.DOSE_UNIT_MILLIGRAM,
                status = WearAppOccurrenceStatus.UPCOMING
            )
        ),
        concentrationState = WearAppConcentration(WearAppConcentrationStatus.EMPTY)
    )

    @Test
    fun `valid snapshot round trips through shared codec`() {
        assertEquals(snapshot, WearAppSnapshotCodec.decode(WearAppSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun `unknown fields are ignored`() {
        val payload = WearAppSnapshotCodec.encode(snapshot) + taggedField(99, byteArrayOf(1, 2, 3))

        assertEquals(snapshot, WearAppSnapshotCodec.decode(payload))
    }

    @Test
    fun `unsupported protocol version is rejected`() {
        val payload = WearAppSnapshotCodec.encode(snapshot).copyOf()
        ByteBuffer.wrap(payload, 12, 4).putInt(2)

        assertNull(WearAppSnapshotCodec.decode(payload))
    }

    @Test
    fun `empty concentration remains empty instead of becoming zero`() {
        val decoded = WearAppSnapshotCodec.decode(WearAppSnapshotCodec.encode(snapshot))

        assertNotNull(decoded)
        assertEquals(WearAppConcentrationStatus.EMPTY, decoded!!.concentrationState.status)
        assertNull(decoded.concentrationState.value)
    }

    private fun taggedField(tag: Int, value: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + value.size).apply {
            putInt(tag)
            putInt(value.size)
            put(value)
        }.array()
}
