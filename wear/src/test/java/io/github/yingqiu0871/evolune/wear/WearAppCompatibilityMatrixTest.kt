package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequest
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequestCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

class WearAppCompatibilityMatrixTest {
    private val producer = WearAppProducerIdentity(UUID(0L, 701L), producerGeneration = 1L)
    private val snapshot = WearAppSnapshot(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        snapshotRevision = 1L,
        generatedAt = Instant.parse("2026-09-06T00:00:00Z"),
        zoneId = "Asia/Shanghai",
        overallStatus = WearAppOverallStatus.EMPTY,
        recentDose = null,
        upcomingOccurrences = emptyList(),
        concentrationState = WearAppConcentration(WearAppConcentrationStatus.EMPTY),
        producerInstanceId = producer.producerInstanceId,
        producerGeneration = producer.producerGeneration
    )

    @Test
    fun `v1 request and snapshot codecs round trip`() {
        val request = WearAppRequest(
            protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
            requestId = UUID(0L, 702L),
            observedProducerInstanceId = null,
            observedProducerGeneration = null,
            observedSnapshotRevision = null,
            requestedAt = Instant.parse("2026-09-06T00:00:01Z")
        )

        assertEquals(request, WearAppRequestCodec.decode(WearAppRequestCodec.encode(request)))
        assertEquals(snapshot, WearAppSnapshotCodec.decode(WearAppSnapshotCodec.encode(snapshot)))
        assertTrue(WearAppSnapshotRules.isValid(snapshot))
    }

    @Test
    fun `missing snapshot remains waiting without fabricating a summary`() {
        val presentation = deriveWearAppPresentation(
            snapshot = null,
            metadata = WearAppCacheMetadata(
                receivedAt = 0L,
                lastRequestedAt = 0L,
                pendingSince = 0L,
                lastFailureAt = 0L,
                connectionState = WearAppConnectionState.CONNECTED
            ),
            nowMillis = 1_000L
        )

        assertEquals(WearAppDisplayState.WAITING_FOR_PHONE, presentation.state)
        assertNull(presentation.snapshot)
    }

    @Test
    fun `known v1 snapshot remains displayable when today summary is absent`() {
        val baselineSnapshot = snapshot.copy(
            overallStatus = WearAppOverallStatus.READY
        )

        val presentation = deriveWearAppPresentation(
            snapshot = baselineSnapshot,
            metadata = WearAppCacheMetadata(
                receivedAt = 900L,
                lastRequestedAt = 0L,
                pendingSince = 0L,
                lastFailureAt = 0L,
                connectionState = WearAppConnectionState.CONNECTED
            ),
            nowMillis = 1_000L
        )

        assertEquals(WearAppDisplayState.READY, presentation.state)
        assertEquals(baselineSnapshot, presentation.snapshot)
    }

    @Test
    fun `unknown protocol version is rejected by both v1 decoders`() {
        val snapshotPayload = WearAppSnapshotCodec.encode(snapshot).copyOf()
        ByteBuffer.wrap(snapshotPayload, 12, 4).putInt(WearAppProtocol.PROTOCOL_VERSION + 1)
        assertNull(WearAppSnapshotCodec.decode(snapshotPayload))

        val request = WearAppRequest(
            protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
            requestId = UUID(0L, 703L),
            observedProducerInstanceId = null,
            observedProducerGeneration = null,
            observedSnapshotRevision = null,
            requestedAt = Instant.parse("2026-09-06T00:00:02Z")
        )
        val requestPayload = WearAppRequestCodec.encode(request).copyOf()
        ByteBuffer.wrap(requestPayload, 12, 4).putInt(WearAppProtocol.PROTOCOL_VERSION + 1)
        assertNull(WearAppRequestCodec.decode(requestPayload))
    }
}
