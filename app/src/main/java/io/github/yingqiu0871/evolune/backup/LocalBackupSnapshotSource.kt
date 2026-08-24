package io.github.yingqiu0871.evolune.backup

import kotlinx.coroutines.CancellationException

/**
 * Reads the authoritative local state used by B1. UI state and provider caches
 * never participate in backup creation.
 */
interface LocalBackupSnapshotSource {
    suspend fun capture(): SnapshotCaptureResult
}

sealed interface SnapshotCaptureResult {
    data class Success(val payload: EvoluneBackupPayloadV1) : SnapshotCaptureResult
    data class Failure(val code: SnapshotCaptureErrorCode, val cause: Throwable? = null) :
        SnapshotCaptureResult
}

enum class SnapshotCaptureErrorCode {
    STORAGE_FAILURE,
    INVALID_LOCAL_DATA
}

/** Production snapshot source backed by the B2 persistence boundary. */
internal class RestorePersistenceSnapshotSource(
    private val persistence: RestorePersistence,
    private val codec: EvoluneBackupCodec = EvoluneBackupCodec()
) : LocalBackupSnapshotSource {
    override suspend fun capture(): SnapshotCaptureResult {
        return try {
            val room = persistence.readRoomState().canonical()
            val settings = persistence.readSettings()
            val payload = room.toPayload(settings)
            when (val validation = codec.validate(payload)) {
                is BackupValidationResult.Valid -> SnapshotCaptureResult.Success(
                    validation.payload.payload
                )
                is BackupValidationResult.Invalid -> SnapshotCaptureResult.Failure(
                    SnapshotCaptureErrorCode.INVALID_LOCAL_DATA,
                    IllegalStateException(validation.error.field ?: "local snapshot")
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SnapshotCaptureResult.Failure(SnapshotCaptureErrorCode.STORAGE_FAILURE, error)
        }
    }
}
