package io.github.yingqiu0871.evolune.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/**
 * Crash-safe local restore coordinator. It deliberately knows nothing about
 * backup encryption, Health Connect, widgets, reminders, Wear, or Drive.
 */
internal class RestoreTransaction(
    private val persistence: RestorePersistence,
    private val journalStore: RestoreJournalStore,
    private val now: () -> Instant = { Instant.now() },
    private val nextOperationId: () -> String = { UUID.randomUUID().toString() }
) {
    private val mutex = Mutex()

    fun prepare(
        validated: ValidatedEvoluneBackupPayloadV1,
        metadata: BackupProducerMetadataV1? = null
    ): RestorePrepareResult {
        val payload = validated.payload
        return when (val validation = EvoluneBackupCodec().validate(payload)) {
            is BackupValidationResult.Invalid -> RestorePrepareResult.Failure(
                RestoreError(
                    RestoreErrorCode.INVALID_PAYLOAD,
                    IllegalArgumentException(validation.error.field ?: "payload")
                )
            )
            is BackupValidationResult.Valid -> {
                val acceptedPayload = validation.payload.payload
                RestorePrepareResult.Success(
                    PreparedRestore(
                        preview = restorePreview(acceptedPayload, metadata),
                        room = RestoreRoomState.fromPayload(acceptedPayload),
                        settings = acceptedPayload.settings
                    )
                )
            }
        }
    }

    suspend fun restore(prepared: PreparedRestore): RestoreResult = mutex.withLock {
        when (val recovery = recoverLocked()) {
            RestoreRecoveryResult.NothingToRecover,
            RestoreRecoveryResult.Recovered -> Unit
            is RestoreRecoveryResult.Failure ->
                return@withLock RestoreResult.Failure(recovery.error)
        }

        val beforeRoom = try {
            persistence.readRoomState().canonical()
        } catch (error: Throwable) {
            rethrowCancellation(error)
            return@withLock RestoreResult.Failure(
                RestoreError(RestoreErrorCode.LOCAL_SNAPSHOT_FAILED, error)
            )
        }
        val beforeSettings = try {
            persistence.readSettings()
        } catch (error: Throwable) {
            rethrowCancellation(error)
            return@withLock RestoreResult.Failure(
                RestoreError(RestoreErrorCode.LOCAL_SNAPSHOT_FAILED, error)
            )
        }
        when (val validation = EvoluneBackupCodec().validate(beforeRoom.toPayload(beforeSettings))) {
            is BackupValidationResult.Valid -> Unit
            is BackupValidationResult.Invalid -> {
                return@withLock RestoreResult.Failure(
                    RestoreError(
                        RestoreErrorCode.LOCAL_SNAPSHOT_FAILED,
                        IllegalStateException(validation.error.field ?: "local snapshot")
                    )
                )
            }
        }

        val preparedJournal = RestoreJournal(
            formatVersion = 1,
            operationId = nextOperationId(),
            createdAt = now().toString(),
            phase = RestoreJournalPhase.PREPARED,
            beforeRoom = beforeRoom,
            beforeSettings = beforeSettings
        )
        try {
            journalStore.write(preparedJournal)
        } catch (error: Throwable) {
            rethrowCancellation(error)
            return@withLock RestoreResult.Failure(
                RestoreError(RestoreErrorCode.JOURNAL_WRITE_FAILED, error)
            )
        }

        try {
            persistence.replaceRoom(prepared.room)
        } catch (error: Throwable) {
            rethrowCancellation(error)
            return@withLock rollbackAndReturn(
                RestoreError(RestoreErrorCode.DATABASE_RESTORE_FAILED, error),
                beforeRoom,
                beforeSettings
            )
        }

        try {
            if (!persistence.replaceSettings(prepared.settings)) {
                return@withLock rollbackAndReturn(
                    RestoreError(RestoreErrorCode.SETTINGS_RESTORE_FAILED),
                    beforeRoom,
                    beforeSettings
                )
            }
        } catch (error: Throwable) {
            rethrowCancellation(error)
            return@withLock rollbackAndReturn(
                RestoreError(RestoreErrorCode.SETTINGS_RESTORE_FAILED, error),
                beforeRoom,
                beforeSettings
            )
        }

        val postcondition = try {
            persistence.readRoomState().canonical() == prepared.room.canonical() &&
                persistence.readSettings() == prepared.settings
        } catch (error: Throwable) {
            rethrowCancellation(error)
            false
        }
        if (!postcondition) {
            return@withLock rollbackAndReturn(
                RestoreError(RestoreErrorCode.POSTCONDITION_FAILED),
                beforeRoom,
                beforeSettings
            )
        }

        try {
            journalStore.write(preparedJournal.copy(phase = RestoreJournalPhase.COMMITTED))
        } catch (error: Throwable) {
            rethrowCancellation(error)
            return@withLock rollbackAndReturn(
                RestoreError(RestoreErrorCode.JOURNAL_COMMIT_FAILED, error),
                beforeRoom,
                beforeSettings
            )
        }

        return@withLock try {
            journalStore.delete()
            RestoreResult.Success()
        } catch (error: Throwable) {
            rethrowCancellation(error)
            // COMMITTED is the durable success point. Startup recovery will
            // retain the target and retry only this cleanup.
            RestoreResult.Success(cleanupPending = true)
        }
    }

    suspend fun recoverInterruptedRestoreIfNeeded(): RestoreRecoveryResult = mutex.withLock {
        recoverLocked()
    }

    private suspend fun recoverLocked(): RestoreRecoveryResult {
        val read = try {
            journalStore.read()
        } catch (error: Throwable) {
            rethrowCancellation(error)
            return RestoreRecoveryResult.Failure(
                RestoreError(RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT, error)
            )
        }
        return when (read) {
            RestoreJournalReadResult.Missing -> RestoreRecoveryResult.NothingToRecover
            is RestoreJournalReadResult.Failure -> read.asRecoveryFailure()
            is RestoreJournalReadResult.Found -> when (read.journal.phase) {
                RestoreJournalPhase.COMMITTED -> try {
                    journalStore.delete()
                    RestoreRecoveryResult.Recovered
                } catch (error: Throwable) {
                    rethrowCancellation(error)
                    RestoreRecoveryResult.Failure(
                        RestoreError(RestoreErrorCode.JOURNAL_CLEANUP_FAILED, error)
                    )
                }
                RestoreJournalPhase.PREPARED -> recoverPrepared(read.journal)
            }
        }
    }

    private suspend fun recoverPrepared(journal: RestoreJournal): RestoreRecoveryResult {
        return try {
            persistence.replaceRoom(journal.beforeRoom)
            if (!persistence.replaceSettings(journal.beforeSettings)) {
                throw IllegalStateException("settings rollback rejected")
            }
            check(persistence.readRoomState().canonical() == journal.beforeRoom.canonical())
            check(persistence.readSettings() == journal.beforeSettings)
            journalStore.delete()
            RestoreRecoveryResult.Recovered
        } catch (error: Throwable) {
            rethrowCancellation(error)
            RestoreRecoveryResult.Failure(
                RestoreError(RestoreErrorCode.RECOVERY_REQUIRED, error)
            )
        }
    }

    private suspend fun rollbackAndReturn(
        original: RestoreError,
        beforeRoom: RestoreRoomState,
        beforeSettings: BackupSettingsV1
    ): RestoreResult {
        return try {
            persistence.replaceRoom(beforeRoom)
            if (!persistence.replaceSettings(beforeSettings)) {
                throw IllegalStateException("settings rollback rejected")
            }
            check(persistence.readRoomState().canonical() == beforeRoom.canonical())
            check(persistence.readSettings() == beforeSettings)
            journalStore.delete()
            RestoreResult.Failure(original)
        } catch (error: Throwable) {
            rethrowCancellation(error)
            RestoreResult.Failure(
                RestoreError(RestoreErrorCode.ROLLBACK_FAILED, error)
            )
        }
    }

    private fun RestoreJournalReadResult.Failure.asRecoveryFailure(): RestoreRecoveryResult.Failure {
        val mapped = when (error.code) {
            RestoreErrorCode.UNSUPPORTED_JOURNAL_VERSION ->
                RestoreErrorCode.UNSUPPORTED_JOURNAL_VERSION
            RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT ->
                RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT
            else -> RestoreErrorCode.RECOVERY_REQUIRED
        }
        return RestoreRecoveryResult.Failure(error.copy(code = mapped))
    }

    private fun rethrowCancellation(error: Throwable) {
        if (error is CancellationException) throw error
    }
}
