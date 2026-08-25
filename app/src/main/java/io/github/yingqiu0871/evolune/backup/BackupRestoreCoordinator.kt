package io.github.yingqiu0871.evolune.backup

import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationResolution
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationGateway
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationOutcome
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupErrorCode
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupGeneration
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupProvider
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

enum class BackupRestoreOperation {
    BACKUP,
    RESTORE
}

enum class BackupRestoreErrorCode {
    AUTHORIZATION_REQUIRED,
    AUTHORIZATION_CANCELLED,
    AUTHORIZATION_UNAVAILABLE,
    AUTHORIZATION_FAILED,
    NETWORK_UNAVAILABLE,
    NO_BACKUPS,
    BACKUP_TOO_LARGE,
    LOCAL_DATA_INVALID,
    BACKUP_UPLOAD_FAILED,
    BACKUP_VERIFICATION_FAILED,
    WRONG_SECRET_OR_TAMPERED,
    UNSUPPORTED_FUTURE_BACKUP,
    INVALID_OR_CORRUPT_BACKUP,
    RESTORE_FAILED,
    RECOVERY_REQUIRED,
    DISCONNECT_FAILED
}

data class BackupRestoreError(
    val operation: BackupRestoreOperation,
    val code: BackupRestoreErrorCode
)

sealed interface AuthorizationGateResult {
    data object Authorized : AuthorizationGateResult
    data class ResolutionRequired(val resolution: AuthorizationResolution) : AuthorizationGateResult
    data object Cancelled : AuthorizationGateResult
    data class Failure(val code: BackupRestoreErrorCode) : AuthorizationGateResult
}

sealed interface BackupCreationResult {
    data object InvalidPassphrase : BackupCreationResult
    data object InvalidLocalData : BackupCreationResult
    data class AuthorizationRequired(val resolution: AuthorizationResolution) : BackupCreationResult
    data class Success(
        val generation: CloudBackupGeneration,
        val cleanupPending: Boolean
    ) : BackupCreationResult
    data class Failure(val error: BackupRestoreError) : BackupCreationResult
}

sealed interface BackupListResult {
    data object NoBackups : BackupListResult
    data class Success(val generations: List<CloudBackupGeneration>) : BackupListResult
    data class AuthorizationRequired(val resolution: AuthorizationResolution) : BackupListResult
    data class Failure(val error: BackupRestoreError) : BackupListResult
}

internal sealed interface RestorePreparationResult {
    data class Success(
        val prepared: PreparedRestore,
        val preview: BackupRestorePreview
    ) : RestorePreparationResult
    data class AuthorizationRequired(val resolution: AuthorizationResolution) : RestorePreparationResult
    data object InvalidPassphrase : RestorePreparationResult
    data class Failure(val error: BackupRestoreError) : RestorePreparationResult
}

internal sealed interface RestoreCompletionResult {
    data object Success : RestoreCompletionResult
    data object SuccessWithRefreshWarning : RestoreCompletionResult
    data class Failure(val error: BackupRestoreError) : RestoreCompletionResult
}

data class BackupRestorePreview(
    val createdAt: String,
    val producerAppVersionName: String?,
    val medicationPlanCount: Int,
    val scheduledDoseSlotCount: Int,
    val doseEventCount: Int,
    val bodyWeightKg: Double,
    val themeMode: String,
    val colorTheme: String,
    val autoCheckUpdates: Boolean,
    val timeFormat: String
)

/**
 * Application-layer orchestration for manual foreground backup and restore.
 * B1 owns bytes, B3 owns transport, and B2 owns persistence mutation.
 */
internal class BackupRestoreCoordinator(
    private val snapshotSource: LocalBackupSnapshotSource,
    private val codec: EvoluneBackupCodec,
    private val authorization: CloudAuthorizationGateway,
    private val provider: CloudBackupProvider,
    private val restoreTransaction: RestoreTransaction,
    private val postRestoreCoordinator: PostRestoreCoordinator,
    private val producerAppVersionName: String,
    private val producerAppVersionCode: Int,
    private val clock: Clock = Clock.systemUTC()
) {
    private val operationMutex = Mutex()

    suspend fun authorizeFor(operation: BackupRestoreOperation): AuthorizationGateResult =
        when (val outcome = authorization.authorize()) {
            is CloudAuthorizationOutcome.Authorized -> AuthorizationGateResult.Authorized
            is CloudAuthorizationOutcome.UserResolutionRequired ->
                AuthorizationGateResult.ResolutionRequired(outcome.resolution)
            CloudAuthorizationOutcome.Cancelled -> AuthorizationGateResult.Cancelled
            CloudAuthorizationOutcome.Unavailable ->
                AuthorizationGateResult.Failure(BackupRestoreErrorCode.AUTHORIZATION_UNAVAILABLE)
            is CloudAuthorizationOutcome.Error ->
                AuthorizationGateResult.Failure(BackupRestoreErrorCode.AUTHORIZATION_FAILED)
        }

    suspend fun listBackups(): BackupListResult = operationMutex.withLock {
        when (val auth = authorization.authorize()) {
            is CloudAuthorizationOutcome.Authorized -> Unit
            is CloudAuthorizationOutcome.UserResolutionRequired ->
                return@withLock BackupListResult.AuthorizationRequired(auth.resolution)
            CloudAuthorizationOutcome.Cancelled -> return@withLock BackupListResult.Failure(
                error(BackupRestoreOperation.RESTORE, BackupRestoreErrorCode.AUTHORIZATION_CANCELLED)
            )
            CloudAuthorizationOutcome.Unavailable,
            is CloudAuthorizationOutcome.Error -> return@withLock BackupListResult.Failure(
                error(BackupRestoreOperation.RESTORE, BackupRestoreErrorCode.AUTHORIZATION_FAILED)
            )
        }
        when (val result = provider.listBackups()) {
            is CloudBackupResult.Success -> {
                if (result.value.isEmpty()) {
                    BackupListResult.NoBackups
                } else {
                    BackupListResult.Success(result.value)
                }
            }
            is CloudBackupResult.Failure -> when (result.error.code) {
                CloudBackupErrorCode.AUTHORIZATION_REQUIRED ->
                    BackupListResult.Failure(
                        error(BackupRestoreOperation.RESTORE, BackupRestoreErrorCode.AUTHORIZATION_REQUIRED)
                    )
                CloudBackupErrorCode.AUTHORIZATION_CANCELLED ->
                    BackupListResult.Failure(
                        error(BackupRestoreOperation.RESTORE, BackupRestoreErrorCode.AUTHORIZATION_CANCELLED)
                    )
                else -> BackupListResult.Failure(
                    error(
                        BackupRestoreOperation.RESTORE,
                        mapCloudError(BackupRestoreOperation.RESTORE, result.error.code)
                    )
                )
            }
        }
    }

    suspend fun createBackup(
        passphrase: CharArray,
        confirmation: CharArray
    ): BackupCreationResult = operationMutex.withLock {
        if (passphrase.isEmpty() || !passphrase.contentEquals(confirmation)) {
            return@withLock BackupCreationResult.InvalidPassphrase
        }
        when (val auth = authorization.authorize()) {
            is CloudAuthorizationOutcome.Authorized -> Unit
            is CloudAuthorizationOutcome.UserResolutionRequired ->
                return@withLock BackupCreationResult.AuthorizationRequired(auth.resolution)
            CloudAuthorizationOutcome.Cancelled -> return@withLock BackupCreationResult.Failure(
                error(BackupRestoreErrorCode.AUTHORIZATION_CANCELLED)
            )
            CloudAuthorizationOutcome.Unavailable,
            is CloudAuthorizationOutcome.Error -> return@withLock BackupCreationResult.Failure(
                error(BackupRestoreErrorCode.AUTHORIZATION_FAILED)
            )
        }
        val secret = passphrase.copyOf()
        try {
            when (val snapshot = snapshotSource.capture()) {
                is SnapshotCaptureResult.Failure -> {
                    return@withLock if (snapshot.code == SnapshotCaptureErrorCode.INVALID_LOCAL_DATA) {
                        BackupCreationResult.InvalidLocalData
                    } else {
                        BackupCreationResult.Failure(
                            error(BackupRestoreErrorCode.LOCAL_DATA_INVALID)
                        )
                    }
                }
                is SnapshotCaptureResult.Success -> {
                    val createdAt = java.time.Instant.ofEpochMilli(clock.millis()).toString()
                    val metadata = BackupProducerMetadataV1(
                        createdAt = createdAt,
                        producerAppVersionName = producerAppVersionName,
                        producerAppVersionCode = producerAppVersionCode
                    )
                    val encoded = when (val result = codec.encodeOnCryptoDispatcher(
                        snapshot.payload,
                        secret,
                        metadata
                    )) {
                        is BackupEncodeResult.Success -> result.bytes
                        is BackupEncodeResult.Failure -> {
                            return@withLock BackupCreationResult.Failure(
                                error(
                                    if (result.error.code == BackupCodecErrorCode.INVALID_PAYLOAD) {
                                        BackupRestoreErrorCode.LOCAL_DATA_INVALID
                                    } else {
                                        BackupRestoreErrorCode.BACKUP_UPLOAD_FAILED
                                    }
                                )
                            )
                        }
                    }
                    try {
                        when (val uploaded = provider.uploadBackup(
                            encoded,
                            io.github.yingqiu0871.evolune.backup.cloud.CloudBackupUploadMetadata(
                                createdAt = createdAt,
                                envelopeFormatVersion = EvoluneBackupFormat.ENVELOPE_FORMAT_VERSION,
                                payloadSchemaVersion = EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION
                            )
                        )) {
                            is CloudBackupResult.Success -> {
                                if (!uploaded.value.verified) {
                                    BackupCreationResult.Failure(
                                        error(BackupRestoreErrorCode.BACKUP_VERIFICATION_FAILED)
                                    )
                                } else {
                                    BackupCreationResult.Success(
                                        generation = uploaded.value.generation,
                                        cleanupPending = uploaded.value.retentionCleanupPending
                                    )
                                }
                            }
                            is CloudBackupResult.Failure -> BackupCreationResult.Failure(
                                error(mapCloudError(BackupRestoreOperation.BACKUP, uploaded.error.code))
                            )
                        }
                    } finally {
                        encoded.fill(0)
                    }
                }
            }
        } finally {
            secret.fill('\u0000')
        }
    }

    internal suspend fun prepareRestore(
        generation: CloudBackupGeneration,
        passphrase: CharArray
    ): RestorePreparationResult = operationMutex.withLock {
        if (passphrase.isEmpty()) return@withLock RestorePreparationResult.InvalidPassphrase
        val secret = passphrase.copyOf()
        try {
            val downloaded = when (val result = provider.downloadBackup(generation.id)) {
                is CloudBackupResult.Success -> result.value
                is CloudBackupResult.Failure -> return@withLock RestorePreparationResult.Failure(
                    error(mapCloudError(BackupRestoreOperation.RESTORE, result.error.code))
                )
            }
            try {
                val decoded = when (val result = codec.decodeAndValidateOnCryptoDispatcher(downloaded, secret)) {
                    is BackupDecodeResult.Success -> result
                    is BackupDecodeResult.Failure -> return@withLock RestorePreparationResult.Failure(
                        error(mapCodecError(result.error.code))
                    )
                }
                return@withLock when (val prepared = restoreTransaction.prepare(decoded.payload, decoded.metadata)) {
                    is RestorePrepareResult.Success -> RestorePreparationResult.Success(
                        prepared = prepared.prepared,
                        preview = prepared.prepared.preview.toUiPreview(
                            prepared.prepared.preview.createdAt ?: generation.createdAt
                        )
                    )
                    is RestorePrepareResult.Failure -> RestorePreparationResult.Failure(
                        error(
                            BackupRestoreOperation.RESTORE,
                            if (prepared.error.code == RestoreErrorCode.INVALID_PAYLOAD) {
                                BackupRestoreErrorCode.INVALID_OR_CORRUPT_BACKUP
                            } else {
                                BackupRestoreErrorCode.RESTORE_FAILED
                            }
                        )
                    )
                }
            } finally {
                downloaded.fill(0)
            }
        } finally {
            secret.fill('\u0000')
        }
    }

    internal suspend fun confirmRestore(prepared: PreparedRestore): RestoreCompletionResult =
        operationMutex.withLock {
            when (val restored = restoreTransaction.restore(prepared)) {
                is RestoreResult.Failure -> RestoreCompletionResult.Failure(
                    error(
                        BackupRestoreOperation.RESTORE,
                        if (restored.error.code == RestoreErrorCode.RECOVERY_REQUIRED ||
                            restored.error.code == RestoreErrorCode.ROLLBACK_FAILED
                        ) {
                            BackupRestoreErrorCode.RECOVERY_REQUIRED
                        } else {
                            BackupRestoreErrorCode.RESTORE_FAILED
                        }
                    )
                )
                is RestoreResult.Success -> when (postRestoreCoordinator.afterRestore()) {
                    PostRestoreRefreshResult.COMPLETE -> RestoreCompletionResult.Success
                    PostRestoreRefreshResult.WARNING -> RestoreCompletionResult.SuccessWithRefreshWarning
                }
            }
        }

    suspend fun disconnect(): Boolean = operationMutex.withLock {
        provider.disconnect() is CloudBackupResult.Success
    }

    private fun RestorePreview.toUiPreview(createdAt: String): BackupRestorePreview =
        BackupRestorePreview(
            createdAt = createdAt,
            producerAppVersionName = producerAppVersionName,
            medicationPlanCount = medicationPlanCount,
            scheduledDoseSlotCount = scheduledDoseSlotCount,
            doseEventCount = doseEventCount,
            bodyWeightKg = bodyWeightKg,
            themeMode = themeMode,
            colorTheme = colorTheme,
            autoCheckUpdates = autoCheckUpdates,
            timeFormat = timeFormat
        )

    private fun error(code: BackupRestoreErrorCode): BackupRestoreError =
        BackupRestoreError(BackupRestoreOperation.BACKUP, code)

    private fun error(operation: BackupRestoreOperation, code: BackupRestoreErrorCode): BackupRestoreError =
        BackupRestoreError(operation, code)

    private fun mapCloudError(
        operation: BackupRestoreOperation,
        code: CloudBackupErrorCode
    ): BackupRestoreErrorCode = when (code) {
        CloudBackupErrorCode.AUTHORIZATION_REQUIRED -> BackupRestoreErrorCode.AUTHORIZATION_REQUIRED
        CloudBackupErrorCode.AUTHORIZATION_CANCELLED -> BackupRestoreErrorCode.AUTHORIZATION_CANCELLED
        CloudBackupErrorCode.AUTHORIZATION_UNAVAILABLE -> BackupRestoreErrorCode.AUTHORIZATION_UNAVAILABLE
        CloudBackupErrorCode.AUTHORIZATION_FAILED,
        CloudBackupErrorCode.TOKEN_EXPIRED -> BackupRestoreErrorCode.AUTHORIZATION_FAILED
        CloudBackupErrorCode.NETWORK,
        CloudBackupErrorCode.TIMEOUT,
        CloudBackupErrorCode.RATE_LIMITED,
        CloudBackupErrorCode.SERVER_ERROR -> BackupRestoreErrorCode.NETWORK_UNAVAILABLE
        CloudBackupErrorCode.BACKUP_TOO_LARGE -> BackupRestoreErrorCode.BACKUP_TOO_LARGE
        CloudBackupErrorCode.UPLOAD_VERIFICATION_FAILED -> BackupRestoreErrorCode.BACKUP_VERIFICATION_FAILED
        CloudBackupErrorCode.UPLOAD_FAILED -> BackupRestoreErrorCode.BACKUP_UPLOAD_FAILED
        CloudBackupErrorCode.DOWNLOAD_FAILED -> BackupRestoreErrorCode.INVALID_OR_CORRUPT_BACKUP
        CloudBackupErrorCode.MALFORMED_REMOTE_METADATA -> BackupRestoreErrorCode.INVALID_OR_CORRUPT_BACKUP
        CloudBackupErrorCode.FORBIDDEN,
        CloudBackupErrorCode.NOT_FOUND,
        CloudBackupErrorCode.DELETE_FAILED ->
            if (operation == BackupRestoreOperation.BACKUP) {
                BackupRestoreErrorCode.BACKUP_UPLOAD_FAILED
            } else {
                BackupRestoreErrorCode.RESTORE_FAILED
            }
    }

    private fun mapCodecError(code: BackupCodecErrorCode): BackupRestoreErrorCode = when (code) {
        BackupCodecErrorCode.UNSUPPORTED_ENVELOPE_VERSION,
        BackupCodecErrorCode.UNSUPPORTED_PAYLOAD_VERSION,
        BackupCodecErrorCode.UNSUPPORTED_CRYPTO -> BackupRestoreErrorCode.UNSUPPORTED_FUTURE_BACKUP
        BackupCodecErrorCode.AUTHENTICATION_FAILED,
        BackupCodecErrorCode.INVALID_SECRET -> BackupRestoreErrorCode.WRONG_SECRET_OR_TAMPERED
        else -> BackupRestoreErrorCode.INVALID_OR_CORRUPT_BACKUP
    }
}
