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
    fun `concentration freshness is independent and exact at fifteen minutes`() {
        val concentrationSnapshot = snapshot.copy(
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = 120.0,
                unit = "pg/mL",
                calculatedAt = Instant.ofEpochMilli(10_000L)
            )
        )

        assertEquals(
            WearAppConcentrationDisplayState.FRESH,
            deriveWearAppConcentrationPresentation(
                concentrationSnapshot,
                10_000L + WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS - 1L
            ).state
        )
        assertEquals(
            WearAppConcentrationDisplayState.STALE,
            deriveWearAppConcentrationPresentation(
                concentrationSnapshot,
                10_000L + WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS
            ).state
        )
        assertEquals(
            WearAppConcentrationDisplayState.STALE,
            deriveWearAppConcentrationPresentation(
                concentrationSnapshot,
                10_000L + WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS + 1L
            ).state
        )
    }

    @Test
    fun `future concentration and invalid values are unavailable`() {
        val future = snapshot.copy(
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = 120.0,
                unit = "pg/mL",
                calculatedAt = Instant.ofEpochMilli(20_000L)
            )
        )
        assertEquals(
            WearAppConcentrationDisplayState.UNAVAILABLE,
            deriveWearAppConcentrationPresentation(future, 19_999L).state
        )
        assertEquals(
            WearAppConcentrationDisplayState.UNAVAILABLE,
            deriveWearAppConcentrationPresentation(snapshot, 20_000L).state
        )
    }

    @Test
    fun `concentration freshness survives rollback and extreme clock values`() {
        val concentrationSnapshot = snapshot.copy(
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = 0.0,
                unit = "pg/mL",
                calculatedAt = Instant.ofEpochMilli(Long.MAX_VALUE - 100L)
            )
        )
        assertEquals(
            WearAppConcentrationDisplayState.UNAVAILABLE,
            deriveWearAppConcentrationPresentation(concentrationSnapshot, Long.MAX_VALUE - 101L).state
        )
        assertEquals(
            WearAppConcentrationDisplayState.FRESH,
            deriveWearAppConcentrationPresentation(concentrationSnapshot, Long.MAX_VALUE).state
        )
        assertEquals(
            WearAppConcentrationDisplayState.UNAVAILABLE,
            deriveWearAppConcentrationPresentation(concentrationSnapshot, Long.MIN_VALUE).state
        )
    }

    @Test
    fun `fresh plan snapshot does not keep an old concentration fresh`() {
        val concentrationSnapshot = snapshot.copy(
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = 120.0,
                unit = "pg/mL",
                calculatedAt = Instant.ofEpochMilli(10_000L)
            )
        )
        val now = 10_000L + WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS

        assertEquals(
            WearAppDisplayState.READY,
            deriveWearAppPresentation(
                concentrationSnapshot,
                metadata(receivedAt = now),
                now
            ).state
        )
        assertEquals(
            WearAppConcentrationDisplayState.STALE,
            deriveWearAppConcentrationPresentation(concentrationSnapshot, now).state
        )
    }

    @Test
    fun `concentration deadline is scheduled only while fresh`() {
        val fresh = snapshot.copy(
            concentrationState = WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = 1.0,
                unit = "pg/mL",
                calculatedAt = Instant.ofEpochMilli(10_000L)
            )
        )
        val metadata = metadata(receivedAt = 10_001L)

        assertEquals(
            10_000L + WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS,
            nextWearAppRefreshDeadline(10_000L, metadata, fresh)
        )
        assertEquals(
            10_001L + WEAR_APP_STALE_AFTER_MILLIS,
            nextWearAppRefreshDeadline(
                10_000L + WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS,
                metadata,
                fresh
            )
        )
    }

    @Test
    fun `concentration formatting is locale independent and keeps unit adjacent`() {
        assertEquals("120.50 pg/mL", formatWearAppConcentration(120.5, "pg/mL"))
        assertEquals("0.00 pg/mL", formatWearAppConcentration(0.0, "pg/mL"))
        assertEquals("1.00e+09 pg/mL", formatWearAppConcentration(1_000_000_000.0, "pg/mL"))
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

    @Test
    fun `undo rejection is transient and consumed results do not recur after activity recreation`() {
        val rejected = WearAppUndoResult(
            protocolVersion = 1,
            operationId = UUID(0L, 61L),
            resultType = WearAppUndoResultType.REJECTED_NOT_LATEST,
            eventId = null,
            processedAt = Instant.ofEpochMilli(1_001L),
            messageCode = WearAppUndoMessageCode.NOT_LATEST,
            snapshotRefreshExpected = false
        )

        val shown = WearAppUndoTransientUiState().afterResult(rejected)
        val (consumed, afterConsume) = shown.consume()

        assertEquals(WearAppUndoMessageCode.NOT_LATEST, consumed)
        assertEquals(null, afterConsume.messageCode)
        assertEquals(null, afterConsume.afterAuthoritativeSnapshot().messageCode)
        assertEquals(null, WearAppUndoTransientUiState().afterResult(rejected.copy(
            resultType = WearAppUndoResultType.UNDONE,
            eventId = UUID(0L, 62L),
            messageCode = WearAppUndoMessageCode.UNDONE,
            snapshotRefreshExpected = true
        )).messageCode)
        assertEquals(null, WearAppUndoTransientUiState(afterConsume.messageCode).messageCode)
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
