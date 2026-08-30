package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppRecentDose
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommandType
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoMessageCode
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

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

    @Test
    fun `undo is enabled only for a fresh ready snapshot with a positive event revision`() {
        val recentSnapshot = snapshot.copy(
            recentDose = WearAppRecentDose(
                eventId = UUID(0L, 31L),
                planId = UUID(0L, 32L),
                slotId = UUID(0L, 33L),
                localDate = java.time.LocalDate.of(2026, 8, 30),
                occurredAt = Instant.ofEpochMilli(900L),
                medicationName = "Estradiol",
                route = "ORAL",
                dose = 2.0,
                doseUnit = "mg",
                source = "WEAR",
                eventRevision = 2L
            )
        )
        val recent = requireNotNull(recentSnapshot.recentDose)
        val eventId = recent.eventId

        assertTrue(canUndoRecentDose(WearAppDisplayState.READY, recentSnapshot, recent, eventId))
        assertFalse(canUndoRecentDose(WearAppDisplayState.STALE, recentSnapshot, recent, eventId))
        assertFalse(canUndoRecentDose(WearAppDisplayState.OFFLINE, recentSnapshot, recent, eventId))
        assertFalse(canUndoRecentDose(WearAppDisplayState.SYNCING, recentSnapshot, recent, eventId))
        assertFalse(canUndoRecentDose(WearAppDisplayState.ERROR, recentSnapshot, recent, eventId))
        assertFalse(
            canUndoRecentDose(
                WearAppDisplayState.READY,
                recentSnapshot.copy(recentDose = recent.copy(eventRevision = null)),
                recent.copy(eventRevision = null),
                eventId
            )
        )
        assertFalse(
            canUndoRecentDose(
                WearAppDisplayState.READY,
                recentSnapshot,
                recent,
                UUID(0L, 99L)
            )
        )
    }

    @Test
    fun `pending operation blocks a second undo`() {
        val recentSnapshot = snapshot.copy(
            recentDose = WearAppRecentDose(
                eventId = UUID(0L, 41L),
                planId = UUID(0L, 42L),
                slotId = UUID(0L, 43L),
                localDate = java.time.LocalDate.of(2026, 8, 30),
                occurredAt = Instant.ofEpochMilli(900L),
                medicationName = "Estradiol",
                route = "ORAL",
                dose = 2.0,
                doseUnit = "mg",
                source = "WEAR",
                eventRevision = 1L
            )
        )
        val recent = requireNotNull(recentSnapshot.recentDose)
        val command = WearAppUndoCommand(
            protocolVersion = 1,
            commandType = WearAppUndoCommandType.UNDO_RECENT_DOSE,
            operationId = UUID(0L, 44L),
            createdAt = Instant.ofEpochMilli(100L),
            sourceSnapshot = WearAppSnapshotIdentity(producerIdentity.producerInstanceId, 1L, 1L),
            eventId = recent.eventId,
            expectedEventRevision = 1L,
            expectedOccurredAt = Instant.ofEpochMilli(900L),
            expectedSource = "WEAR"
        )
        val pending = WearAppPendingUndo(
            command = command,
            commandDataItemUri = "/hrt/v1/wear-app/commands/${command.operationId}",
            terminalResult = null,
            sendAttempt = 0L
        )

        assertFalse(
            canUndoRecentDose(
                WearAppDisplayState.READY,
                recentSnapshot,
                recent,
                command.eventId,
                pending
            )
        )
    }

    @Test
    fun `successful undo keeps the cached recent dose until a newer snapshot proves it absent`() {
        val recentSnapshot = snapshot.copy(
            recentDose = WearAppRecentDose(
                eventId = UUID(0L, 51L),
                planId = UUID(0L, 52L),
                slotId = UUID(0L, 53L),
                localDate = java.time.LocalDate.of(2026, 8, 30),
                occurredAt = Instant.ofEpochMilli(900L),
                medicationName = "Estradiol",
                route = "ORAL",
                dose = 2.0,
                doseUnit = "mg",
                source = "WEAR",
                eventRevision = 1L
            )
        )
        val recent = requireNotNull(recentSnapshot.recentDose)
        val command = WearAppUndoCommand(
            protocolVersion = 1,
            commandType = WearAppUndoCommandType.UNDO_RECENT_DOSE,
            operationId = UUID(0L, 54L),
            createdAt = Instant.ofEpochMilli(100L),
            sourceSnapshot = WearAppSnapshotIdentity(producerIdentity.producerInstanceId, 1L, 1L),
            eventId = recent.eventId,
            expectedEventRevision = 1L,
            expectedOccurredAt = recent.occurredAt,
            expectedSource = recent.source
        )
        val pending = WearAppPendingUndo(
            command = command,
            commandDataItemUri = "/hrt/v1/wear-app/commands/${command.operationId}",
            terminalResult = WearAppUndoResult(
                protocolVersion = 1,
                operationId = command.operationId,
                resultType = WearAppUndoResultType.UNDONE,
                eventId = recent.eventId,
                processedAt = Instant.ofEpochMilli(1_001L),
                messageCode = WearAppUndoMessageCode.UNDONE,
                snapshotRefreshExpected = true
            ),
            sendAttempt = 1L
        )

        assertFalse(shouldClearUndoAfterAuthoritativeSnapshot(recentSnapshot, pending))
        assertFalse(
            shouldClearUndoAfterAuthoritativeSnapshot(
                recentSnapshot.copy(snapshotRevision = 2L),
                pending
            )
        )
        assertTrue(
            shouldClearUndoAfterAuthoritativeSnapshot(
                recentSnapshot.copy(snapshotRevision = 2L, recentDose = null),
                pending
            )
        )
        assertTrue(
            shouldClearUndoAfterAuthoritativeSnapshot(
                recentSnapshot.copy(
                    snapshotRevision = 2L,
                    recentDose = recent.copy(eventId = UUID(0L, 55L))
                ),
                pending
            )
        )
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
