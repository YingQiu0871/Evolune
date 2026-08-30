package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerNegotiationResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequest
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import io.github.yingqiu0871.evolune.experience.wear.negotiateWearAppProducerIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class WearAppFollowUpRegressionTest {
    private val producerA = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val producerB = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `same producer uses revision and payload rules`() {
        val base = snapshot(producerA, generation = 1, revision = 100)

        assertEquals(
            WearAppSnapshotApplyResult.Applied,
            classifyWearAppSnapshot(base, base.copy(snapshotRevision = 101)),
        )
        assertEquals(
            WearAppSnapshotApplyResult.Older,
            classifyWearAppSnapshot(base, base.copy(snapshotRevision = 99)),
        )
        assertEquals(
            WearAppSnapshotApplyResult.Duplicate,
            classifyWearAppSnapshot(base, base.copy(snapshotRevision = 100)),
        )
        assertEquals(
            WearAppSnapshotApplyResult.Rejected,
            classifyWearAppSnapshot(
                base,
                base.copy(
                    snapshotRevision = 100,
                    overallStatus = WearAppOverallStatus.EMPTY,
                ),
            ),
        )
    }

    @Test
    fun `new producer revision one wins and delayed old producer cannot overwrite it`() {
        val old = snapshot(producerA, generation = 1, revision = 100)
        val rebuiltPhone = snapshot(producerB, generation = 2, revision = 1)

        assertEquals(WearAppSnapshotApplyResult.Applied, classifyWearAppSnapshot(old, rebuiltPhone))
        assertEquals(WearAppSnapshotApplyResult.Older, classifyWearAppSnapshot(rebuiltPhone, old))
    }

    @Test
    fun `lower generation producer is rejected until phone negotiates a higher generation`() {
        val old = snapshot(producerA, generation = 100, revision = 100)
        val rebuiltPhone = snapshot(producerB, generation = 1, revision = 1)

        assertEquals(
            WearAppSnapshotApplyResult.Older,
            reduceWearAppSnapshot(WearAppSnapshotReducerState(old), rebuiltPhone).result
        )
    }

    @Test
    fun `real reducer keeps the highest producer for every three producer permutation`() {
        val first = snapshot(producerA, generation = 1, revision = 1)
        val newest = snapshot(producerB, generation = 3, revision = 1)
        val delayed = snapshot(UUID(0L, 3L), generation = 2, revision = 1)

        val finalStates = listOf(
            listOf(first, delayed, newest),
            listOf(first, newest, delayed),
            listOf(delayed, first, newest),
            listOf(delayed, newest, first),
            listOf(newest, first, delayed),
            listOf(newest, delayed, first),
        ).map(::reduce)

        assertEquals(6, finalStates.size)
        assertTrue(finalStates.all { it.current?.producerInstanceId == newest.producerInstanceId })
        assertEquals(
            newest.producerInstanceId,
            reduce(listOf(newest, delayed)).current?.producerInstanceId
        )
    }

    @Test
    fun `same generation producer tie break is stable for every permutation`() {
        val first = snapshot(producerA, generation = 7, revision = 999)
        val second = snapshot(producerB, generation = 7, revision = 1)
        val expected = listOf(first, second).maxWithOrNull(compareBy<WearAppSnapshot> {
            it.producerGeneration
        }.thenBy {
            it.producerInstanceId.toString()
        })!!

        assertEquals(
            expected.producerInstanceId,
            reduce(listOf(first, second)).current?.producerInstanceId
        )
        assertEquals(
            expected.producerInstanceId,
            reduce(listOf(second, first)).current?.producerInstanceId
        )
    }

    @Test
    fun `phone reset handshake raises generation and recovers without clearing Wear`() {
        val oldPhone = snapshot(producerA, generation = 100, revision = 100)
        val rebuiltPhone = snapshot(producerB, generation = 1, revision = 1)
        val resetRequest = WearAppRequest(
            protocolVersion = 1,
            requestId = UUID(0L, 20L),
            observedProducerInstanceId = producerA,
            observedProducerGeneration = 100,
            observedSnapshotRevision = 100,
            requestedAt = Instant.parse("2026-08-30T00:00:00Z")
        )

        assertEquals(
            WearAppSnapshotApplyResult.Older,
            reduceWearAppSnapshot(WearAppSnapshotReducerState(oldPhone), rebuiltPhone).result
        )
        val raised = negotiateWearAppProducerIdentity(
            current = WearAppProducerIdentity(producerB, producerGeneration = 1),
            observedProducerInstanceId = resetRequest.observedProducerInstanceId,
            observedProducerGeneration = resetRequest.observedProducerGeneration
        )
        val recoveredIdentity = (raised as WearAppProducerNegotiationResult.Accepted).identity
        assertEquals(101L, recoveredIdentity.producerGeneration)
        val recovered = rebuiltPhone.copy(
            producerGeneration = recoveredIdentity.producerGeneration
        )
        assertEquals(
            WearAppSnapshotApplyResult.Applied,
            reduceWearAppSnapshot(WearAppSnapshotReducerState(oldPhone), recovered).result
        )
        assertEquals(
            WearAppSnapshotApplyResult.Older,
            reduceWearAppSnapshot(WearAppSnapshotReducerState(recovered), oldPhone).result
        )

        val secondWearRaised = negotiateWearAppProducerIdentity(
            current = recoveredIdentity,
            observedProducerInstanceId = UUID(0L, 21L),
            observedProducerGeneration = 150L
        )
        val highestIdentity = (secondWearRaised as WearAppProducerNegotiationResult.Accepted).identity
        assertEquals(151L, highestIdentity.producerGeneration)
        assertEquals(
            highestIdentity,
            (negotiateWearAppProducerIdentity(
                current = highestIdentity,
                observedProducerInstanceId = producerA,
                observedProducerGeneration = 100L
            ) as WearAppProducerNegotiationResult.Accepted).identity
        )
        assertEquals(
            highestIdentity,
            (negotiateWearAppProducerIdentity(
                current = highestIdentity,
                observedProducerInstanceId = producerB,
                observedProducerGeneration = 151L
            ) as WearAppProducerNegotiationResult.Accepted).identity
        )
    }

    @Test
    fun `producer ordering is deterministic when inputs are reversed`() {
        val older = snapshot(producerA, generation = 7, revision = 100)
        val newer = snapshot(producerB, generation = 8, revision = 1)

        fun select(order: List<WearAppSnapshot>): WearAppSnapshot? = order.fold(null) { current, incoming ->
            when {
                current == null -> incoming
                classifyWearAppSnapshot(current, incoming) == WearAppSnapshotApplyResult.Applied -> incoming
                else -> current
            }
        }

        assertEquals(select(listOf(older, newer)), select(listOf(newer, older)))
        assertEquals(newer, select(listOf(older, newer)))
    }

    @Test
    fun `same generation uses producer id as a deterministic tie breaker`() {
        val lowerId = snapshot(producerA, generation = 7, revision = 999)
        val higherId = snapshot(producerB, generation = 7, revision = 1)

        assertEquals(WearAppSnapshotApplyResult.Applied, classifyWearAppSnapshot(lowerId, higherId))
        assertEquals(WearAppSnapshotApplyResult.Older, classifyWearAppSnapshot(higherId, lowerId))
    }

    @Test
    fun `codec preserves producer identity`() {
        val input = snapshot(producerB, generation = 42, revision = 1)

        val output = requireNotNull(WearAppSnapshotCodec.decode(WearAppSnapshotCodec.encode(input)))

        assertEquals(input.producerInstanceId, output.producerInstanceId)
        assertEquals(input.producerGeneration, output.producerGeneration)
    }

    @Test
    fun `request throttle allows rollback and exact boundary`() {
        assertFalse(shouldThrottleWearAppRequest(nowMillis = 10_000, lastRequestedAt = 0))
        assertTrue(shouldThrottleWearAppRequest(nowMillis = 24_999, lastRequestedAt = 10_000))
        assertFalse(shouldThrottleWearAppRequest(nowMillis = 25_000, lastRequestedAt = 10_000))
        assertFalse(shouldThrottleWearAppRequest(nowMillis = 9_000, lastRequestedAt = 10_000))
        assertFalse(shouldThrottleWearAppRequest(nowMillis = Long.MIN_VALUE, lastRequestedAt = Long.MAX_VALUE))
    }

    @Test
    fun `refresh deadline is the next pending or freshness boundary`() {
        val snapshot = snapshot(producerA, generation = 1, revision = 1)
        val metadata = WearAppCacheMetadata(
            receivedAt = 1_000,
            lastRequestedAt = 1_000,
            pendingSince = 1_000,
            lastFailureAt = 0,
            connectionState = WearAppConnectionState.CONNECTED,
        )

        assertEquals(31_000L, nextWearAppRefreshDeadline(1_000, metadata, snapshot))
        assertEquals(901_000L, nextWearAppRefreshDeadline(31_000, metadata, snapshot))
        assertNotNull(nextWearAppRefreshDeadline(900_999, metadata, snapshot))
    }

    @Test
    fun `async state transitions redraw at transport and freshness boundaries`() {
        assertEquals(
            WearAppDisplayState.OFFLINE,
            deriveWearAppPresentation(
                snapshot = null,
                metadata = metadata(connectionState = WearAppConnectionState.DISCONNECTED),
                nowMillis = 30_000L,
            ).state,
        )
        assertEquals(
            WearAppDisplayState.ERROR,
            deriveWearAppPresentation(
                snapshot = null,
                metadata = metadata(lastFailureAt = 20_000L),
                nowMillis = 20_000L,
            ).state,
        )
        assertEquals(
            WearAppDisplayState.ERROR,
            deriveWearAppPresentation(
                snapshot = null,
                metadata = metadata(pendingSince = 10_000L),
                nowMillis = 40_000L,
            ).state,
        )
        assertEquals(
            WearAppDisplayState.STALE,
            deriveWearAppPresentation(
                snapshot = snapshot(producerA, generation = 1, revision = 1),
                metadata = metadata(receivedAt = 1_000L),
                nowMillis = 901_000L,
            ).state,
        )
    }

    @Test
    fun `snapshot application removes timeout boundary and restart rebuilds from cache state`() {
        val appliedMetadata = metadata(receivedAt = 1_000L)
        val appliedSnapshot = snapshot(producerA, generation = 1, revision = 1)

        assertEquals(
            WearAppDisplayState.READY,
            deriveWearAppPresentation(appliedSnapshot, appliedMetadata, nowMillis = 2_000L).state,
        )
        assertEquals(
            901_000L,
            nextWearAppRefreshDeadline(2_000L, appliedMetadata, appliedSnapshot),
        )
        assertTrue(shouldRunWearAppRefreshCallback(true))
        assertFalse(shouldRunWearAppRefreshCallback(false))
    }

    @Test
    fun `clock rollback keeps pending state bounded and schedules a future boundary`() {
        val pending = metadata(pendingSince = 1_000L)

        assertEquals(
            WearAppDisplayState.SYNCING,
            deriveWearAppPresentation(null, pending, nowMillis = 900L).state,
        )
        assertEquals(31_000L, nextWearAppRefreshDeadline(900L, pending, null))
    }

    @Test
    fun `rotary handling requires a visible rotary scroll event`() {
        assertTrue(shouldHandleWearAppRotaryScroll(true, true, true, 1f))
        assertFalse(shouldHandleWearAppRotaryScroll(false, true, true, 1f))
        assertFalse(shouldHandleWearAppRotaryScroll(true, false, true, 1f))
        assertFalse(shouldHandleWearAppRotaryScroll(true, true, false, 1f))
        assertFalse(shouldHandleWearAppRotaryScroll(true, true, true, 0f))
    }

    private fun snapshot(
        producerId: UUID,
        generation: Long,
        revision: Long,
    ): WearAppSnapshot = WearAppSnapshot(
        protocolVersion = 1,
        producerInstanceId = producerId,
        producerGeneration = generation,
        snapshotRevision = revision,
        generatedAt = Instant.parse("2026-08-30T00:00:00Z"),
        zoneId = "Asia/Shanghai",
        overallStatus = WearAppOverallStatus.READY,
        recentDose = null,
        upcomingOccurrences = emptyList(),
        concentrationState = io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration(
            status = WearAppConcentrationStatus.EMPTY,
        ),
        )

    private fun reduce(order: List<WearAppSnapshot>): WearAppSnapshotReducerState =
        order.fold(WearAppSnapshotReducerState(null)) { state, incoming ->
            reduceWearAppSnapshot(state, incoming).state
        }

    private fun metadata(
        receivedAt: Long = 0L,
        pendingSince: Long = 0L,
        lastFailureAt: Long = 0L,
        connectionState: WearAppConnectionState = WearAppConnectionState.CONNECTED,
    ) = WearAppCacheMetadata(
        receivedAt = receivedAt,
        lastRequestedAt = 0L,
        pendingSince = pendingSince,
        lastFailureAt = lastFailureAt,
        connectionState = connectionState,
    )
}
