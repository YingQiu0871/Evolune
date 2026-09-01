package io.github.yingqiu0871.evolune.experience.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

class WearAppRequestCodecTest {
    private val requestId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val observedProducerId = UUID.fromString("00000000-0000-0000-0000-000000000102")

    @Test
    fun `request without cache round trips with explicit empty observation`() {
        val request = request()

        assertEquals(request, WearAppRequestCodec.decode(WearAppRequestCodec.encode(request)))
        assertTrue(WearAppRequestRules.isValid(request))
    }

    @Test
    fun `request with cached producer and revision round trips`() {
        val request = request(
            observedProducerInstanceId = observedProducerId,
            observedProducerGeneration = 100L,
            observedSnapshotRevision = 42L
        )

        assertEquals(request, WearAppRequestCodec.decode(WearAppRequestCodec.encode(request)))
    }

    @Test
    fun `unknown request fields are ignored`() {
        val payload = WearAppRequestCodec.encode(request()) + taggedField(99, byteArrayOf(1, 2, 3))

        assertEquals(request(), WearAppRequestCodec.decode(payload))
    }

    @Test
    fun `unsupported request version is rejected`() {
        val payload = WearAppRequestCodec.encode(request()).copyOf()
        ByteBuffer.wrap(payload, 12, 4).putInt(2)

        assertNull(WearAppRequestCodec.decode(payload))
    }

    @Test
    fun `malformed request UUID is rejected`() {
        val payload = WearAppRequestCodec.encode(request()).copyOf()
        payload[24] = 'x'.code.toByte()

        assertNull(WearAppRequestCodec.decode(payload))
    }

    @Test
    fun `duplicate known request fields are rejected`() {
        val payload = WearAppRequestCodec.encode(request()) + taggedField(1, intBytes(1))

        assertNull(WearAppRequestCodec.decode(payload))
    }

    @Test
    fun `request payload size is bounded`() {
        assertNull(
            WearAppRequestCodec.decode(
                ByteArray(WearAppRequestRules.MAX_PAYLOAD_BYTES + 1)
            )
        )
    }

    @Test
    fun `invalid producer and revision combinations are rejected`() {
        val missingGeneration = request(observedProducerInstanceId = observedProducerId)
        val revisionWithoutProducer = request(observedSnapshotRevision = 1L)

        assertFalse(WearAppRequestRules.isValid(missingGeneration))
        assertFalse(WearAppRequestRules.isValid(revisionWithoutProducer))
        assertNull(
            WearAppRequestCodec.decode(
                WearAppRequestCodec.encode(request()) + taggedField(5, longBytes(1L))
            )
        )
    }

    @Test
    fun `maximum observed generation is decoded without arithmetic overflow`() {
        val request = request(
            observedProducerInstanceId = observedProducerId,
            observedProducerGeneration = Long.MAX_VALUE,
            observedSnapshotRevision = Long.MAX_VALUE
        )
        val decoded = WearAppRequestCodec.decode(WearAppRequestCodec.encode(request))

        assertNotNull(decoded)
        assertEquals(Long.MAX_VALUE, decoded!!.observedProducerGeneration)
        assertEquals(
            WearAppProducerNegotiationResult.GenerationExhausted,
            negotiateWearAppProducerIdentity(
                current = WearAppProducerIdentity(
                    producerInstanceId = UUID(0L, 103L),
                    producerGeneration = 1L
                ),
                observedProducerInstanceId = observedProducerId,
                observedProducerGeneration = decoded.observedProducerGeneration
            )
        )
    }

    @Test
    fun `producer negotiation uses the maximum observed generation and is idempotent`() {
        val current = WearAppProducerIdentity(UUID(0L, 104L), producerGeneration = 1L)
        val first = negotiate(current, UUID(0L, 105L), 100L)
        val second = negotiate(first, UUID(0L, 106L), 150L)
        val delayed = negotiate(second, UUID(0L, 105L), 100L)
        val duplicate = negotiate(second, UUID(0L, 106L), 150L)

        assertEquals(101L, first.producerGeneration)
        assertEquals(151L, second.producerGeneration)
        assertEquals(second, delayed)
        assertEquals(second, duplicate)
    }

    @Test
    fun `same producer request keeps generation while clock rollback is irrelevant`() {
        val current = WearAppProducerIdentity(observedProducerId, producerGeneration = 100L)

        val result = negotiate(current, observedProducerId, 1L)

        assertEquals(current, result)
    }

    private fun request(
        observedProducerInstanceId: UUID? = null,
        observedProducerGeneration: Long? = null,
        observedSnapshotRevision: Long? = null
    ) = WearAppRequest(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        requestId = requestId,
        observedProducerInstanceId = observedProducerInstanceId,
        observedProducerGeneration = observedProducerGeneration,
        observedSnapshotRevision = observedSnapshotRevision,
        requestedAt = Instant.parse("2026-08-30T10:00:00Z")
    )

    private fun negotiate(
        current: WearAppProducerIdentity,
        observedId: UUID,
        observedGeneration: Long
    ): WearAppProducerIdentity = when (
        val result = negotiateWearAppProducerIdentity(current, observedId, observedGeneration)
    ) {
        is WearAppProducerNegotiationResult.Accepted -> result.identity
        else -> error("expected accepted identity: $result")
    }

    private fun taggedField(tag: Int, value: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + value.size).apply {
            putInt(tag)
            putInt(value.size)
            put(value)
        }.array()

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(8).putLong(value).array()
}
