package io.github.yingqiu0871.evolune.backup.cloud

/**
 * Provider-neutral cloud backup seam. The caller supplies already encrypted bytes;
 * this layer never decodes, decrypts, or persists backup content.
 */
interface CloudBackupProvider {
    suspend fun uploadBackup(
        bytes: ByteArray,
        metadata: CloudBackupUploadMetadata
    ): CloudBackupResult<CloudBackupUploadResult>

    suspend fun listBackups(): CloudBackupResult<List<CloudBackupGeneration>>

    suspend fun downloadBackup(id: CloudBackupId): CloudBackupResult<ByteArray>

    suspend fun deleteBackup(id: CloudBackupId): CloudBackupResult<Unit>

    suspend fun disconnect(): CloudBackupResult<Unit>
}

@JvmInline
value class CloudBackupId(val value: String)

data class CloudBackupUploadMetadata(
    val createdAt: String,
    val envelopeFormatVersion: Int,
    val payloadSchemaVersion: Int
)

data class CloudBackupGeneration(
    val id: CloudBackupId,
    val name: String,
    val createdAt: String,
    val sizeBytes: Long?,
    val contentSha256: String
)

data class CloudBackupUploadResult(
    val generation: CloudBackupGeneration,
    val verified: Boolean,
    val retentionCleanupPending: Boolean,
    val orphanCleanupPending: Boolean = false
)

enum class CloudBackupErrorCode {
    AUTHORIZATION_REQUIRED,
    AUTHORIZATION_CANCELLED,
    AUTHORIZATION_UNAVAILABLE,
    AUTHORIZATION_FAILED,
    TOKEN_EXPIRED,
    NETWORK,
    TIMEOUT,
    FORBIDDEN,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER_ERROR,
    MALFORMED_REMOTE_METADATA,
    BACKUP_TOO_LARGE,
    UPLOAD_FAILED,
    UPLOAD_VERIFICATION_FAILED,
    DOWNLOAD_FAILED,
    DELETE_FAILED
}

data class CloudBackupError(
    val code: CloudBackupErrorCode,
    val orphanCleanupPending: Boolean = false
)

sealed interface CloudBackupResult<out T> {
    data class Success<T>(val value: T) : CloudBackupResult<T>

    data class Failure(val error: CloudBackupError) : CloudBackupResult<Nothing>
}
