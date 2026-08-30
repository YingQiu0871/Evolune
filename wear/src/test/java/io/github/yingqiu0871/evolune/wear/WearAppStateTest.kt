package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WearAppStateTest {
    private val producerIdentity = WearAppProducerIdentity(
        producerInstanceId = java.util.UUID(0L, 9L),
        producerGeneration = 1L
    )

    private val snapshot = WearAppSnapshot(
        protocolVersion = 1,
        snapshotRevision = 1L,
        generatedAt = Instant.ofEpochMilli(1_000L),
        zoneId = "UTC",
        overallStatus = WearAppOverallStatus.READY,
        recentDose = null,
        upcomingOccurrences = emptyList(),
        concentrationState = WearAppConcentration(WearAppConcentrationStatus.EMPTY),
        producerInstanceId = producerIdentity.producerInstanceId,
        producerGeneration = producerIdentity.producerGeneration
    )

    @Test
    fun `no snapshot waits for phone`() {
        assertEquals(
            WearAppDisplayState.WAITING_FOR_PHONE,
            deriveWearAppPresentation(snapshot = null, metadata = metadata(), nowMillis = 20_000L).state
        )
    }

    @Test
    fun `recent connected snapshot is ready`() {
        assertEquals(
            WearAppDisplayState.READY,
            deriveWearAppPresentation(snapshot, metadata(receivedAt = 10_000L), nowMillis = 20_000L).state
        )
    }

    @Test
    fun `empty snapshot is empty`() {
        val emptySnapshot = snapshot.copy(overallStatus = WearAppOverallStatus.EMPTY)

        assertEquals(
            WearAppDisplayState.EMPTY,
            deriveWearAppPresentation(emptySnapshot, metadata(receivedAt = 10_000L), nowMillis = 20_000L).state
        )
    }

    @Test
    fun `pending request is syncing until timeout then error`() {
        val pending = metadata(pendingSince = 10_000L)

        assertEquals(
            WearAppDisplayState.SYNCING,
            deriveWearAppPresentation(snapshot, pending, nowMillis = 39_999L).state
        )
        assertEquals(
            WearAppDisplayState.ERROR,
            deriveWearAppPresentation(snapshot, pending, nowMillis = 40_000L).state
        )
    }

    @Test
    fun `old snapshot is stale`() {
        assertEquals(
            WearAppDisplayState.STALE,
            deriveWearAppPresentation(snapshot, metadata(receivedAt = 10_000L), nowMillis = 910_001L).state
        )
    }

    @Test
    fun `disconnected state is offline and keeps cached snapshot`() {
        val presentation = deriveWearAppPresentation(
            snapshot,
            metadata(receivedAt = 10_000L, connectionState = WearAppConnectionState.DISCONNECTED),
            nowMillis = 20_000L
        )

        assertEquals(WearAppDisplayState.OFFLINE, presentation.state)
        assertEquals(snapshot, presentation.snapshot)
    }

    @Test
    fun `cache ordering rejects older and conflicting revisions`() {
        assertEquals(
            WearAppSnapshotApplyResult.Older,
            classifyWearAppSnapshot(snapshot.copy(snapshotRevision = 2L), snapshot)
        )
        assertEquals(
            WearAppSnapshotApplyResult.Duplicate,
            classifyWearAppSnapshot(snapshot, snapshot.copy())
        )
        assertEquals(
            WearAppSnapshotApplyResult.Rejected,
            classifyWearAppSnapshot(
                snapshot,
                snapshot.copy(overallStatus = WearAppOverallStatus.EMPTY)
            )
        )
        assertEquals(
            WearAppSnapshotApplyResult.Applied,
            classifyWearAppSnapshot(snapshot, snapshot.copy(snapshotRevision = 2L))
        )
    }

    @Test
    fun `unsupported payload cannot become a cache candidate`() {
        val payload = WearAppSnapshotCodec.encode(snapshot).copyOf()
        payload[15] = 2

        assertEquals(null, WearAppSnapshotCodec.decode(payload))
    }

    private fun metadata(
        receivedAt: Long = 0L,
        pendingSince: Long = 0L,
        connectionState: WearAppConnectionState = WearAppConnectionState.CONNECTED
    ) = WearAppCacheMetadata(
        receivedAt = receivedAt,
        lastRequestedAt = 0L,
        pendingSince = pendingSince,
        lastFailureAt = 0L,
        connectionState = connectionState
    )
}
