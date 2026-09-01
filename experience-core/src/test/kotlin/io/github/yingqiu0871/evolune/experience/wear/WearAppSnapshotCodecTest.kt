package io.github.yingqiu0871.evolune.experience.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WearAppSnapshotCodecTest {
    private val producerIdentity = WearAppProducerIdentity(
        producerInstanceId = UUID(0L, 9L),
        producerGeneration = 1L
    )

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
            source = "MANUAL",
            eventRevision = 4L
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
        concentrationState = WearAppConcentration(WearAppConcentrationStatus.EMPTY),
        producerInstanceId = producerIdentity.producerInstanceId,
        producerGeneration = producerIdentity.producerGeneration
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

    @Test
    fun `legacy snapshot without concentration field remains readable`() {
        val legacyPayload = withoutTopLevelField(
            WearAppSnapshotCodec.encode(snapshot),
            tag = 8
        )

        val decoded = WearAppSnapshotCodec.decode(legacyPayload)

        assertEquals(snapshot.copy(concentrationState = WearAppConcentration.unavailable()), decoded)
    }

    @Test
    fun `duplicate concentration field is rejected`() {
        val payload = WearAppSnapshotCodec.encode(snapshot)
        val concentrationField = topLevelField(payload, tag = 8)

        assertNull(WearAppSnapshotCodec.decode(payload + concentrationField))
    }

    @Test
    fun `oversized snapshot payload is rejected`() {
        val payload = WearAppSnapshotCodec.encode(snapshot) +
            taggedField(99, ByteArray(256 * 1024))

        assertNull(WearAppSnapshotCodec.decode(payload))
    }

    @Test
    fun `zero concentration is a valid model result only when timestamped`() {
        val zero = snapshot.copy(
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = 0.0,
                unit = WearAppSnapshotRules.CONCENTRATION_UNIT_PG_ML,
                calculatedAt = Instant.parse("2026-08-30T10:00:00Z")
            )
        )

        assertEquals(zero, WearAppSnapshotCodec.decode(WearAppSnapshotCodec.encode(zero)))
    }

    @Test
    fun `available concentration with invalid timestamp is rejected`() {
        val invalid = snapshot.copy(
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = 1.0,
                unit = WearAppSnapshotRules.CONCENTRATION_UNIT_PG_ML,
                calculatedAt = Instant.ofEpochMilli(0L)
            )
        )

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WearAppSnapshotCodec.encode(invalid)
        }
    }

    @Test
    fun `invalid concentration values and units are rejected`() {
        val calculatedAt = Instant.parse("2026-08-30T10:00:00Z")
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            -0.1
        ).forEach { value ->
            val invalid = snapshot.copy(
                concentrationState = WearAppConcentration(
                    status = WearAppConcentrationStatus.AVAILABLE,
                    value = value,
                    unit = WearAppSnapshotRules.CONCENTRATION_UNIT_PG_ML,
                    calculatedAt = calculatedAt
                )
            )
            org.junit.Assert.assertFalse(WearAppSnapshotRules.isValid(invalid))
        }
        assertFalse(
            WearAppSnapshotRules.isValid(
                snapshot.copy(
                    concentrationState = WearAppConcentration(
                        status = WearAppConcentrationStatus.AVAILABLE,
                        value = 1.0,
                        unit = null,
                        calculatedAt = calculatedAt
                    )
                )
            )
        )
        assertFalse(
            WearAppSnapshotRules.isValid(
                snapshot.copy(
                    concentrationState = WearAppConcentration(
                        status = WearAppConcentrationStatus.AVAILABLE,
                        value = 1.0,
                        unit = "ng/mL",
                        calculatedAt = calculatedAt
                    )
                )
            )
        )
    }

    @Test
    fun `legacy recent dose without revision remains displayable but has no undo authority`() {
        val legacy = snapshot.copy(recentDose = requireNotNull(snapshot.recentDose).copy(eventRevision = null))

        val decoded = WearAppSnapshotCodec.decode(WearAppSnapshotCodec.encode(legacy))

        assertEquals(null, decoded?.recentDose?.eventRevision)
        assertEquals(legacy, decoded)
    }

    private fun taggedField(tag: Int, value: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + value.size).apply {
            putInt(tag)
            putInt(value.size)
            put(value)
        }.array()

    private fun withoutTopLevelField(payload: ByteArray, tag: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(payload, 0, 4)
        var offset = 4
        while (offset < payload.size) {
            val size = ByteBuffer.wrap(payload, offset + 4, 4).int
            val fieldSize = 8 + size
            val fieldTag = ByteBuffer.wrap(payload, offset, 4).int
            if (fieldTag != tag) output.write(payload, offset, fieldSize)
            offset += fieldSize
        }
        return output.toByteArray()
    }

    private fun topLevelField(payload: ByteArray, tag: Int): ByteArray {
        var offset = 4
        while (offset < payload.size) {
            val fieldTag = ByteBuffer.wrap(payload, offset, 4).int
            val size = ByteBuffer.wrap(payload, offset + 4, 4).int
            if (fieldTag == tag) return payload.copyOfRange(offset, offset + 8 + size)
            offset += 8 + size
        }
        error("missing field $tag")
    }
}
