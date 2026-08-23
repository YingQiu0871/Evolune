package io.github.yingqiu0871.evolune.backup.cloud.google

import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationOperationErrorCode
import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationOperationResult
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationGateway
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationOutcome
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupError
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupErrorCode
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupGeneration
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupId
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupProvider
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupResult
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupUploadMetadata
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupUploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Manual/foreground Google Drive appDataFolder provider.
 *
 * This class owns the immutable-generation, read-back verification, bounded
 * ingress, and retention policy. It never decodes B1 bytes and never persists
 * an access token or backup content.
 */
class GoogleDriveBackupProvider(
    private val authorization: CloudAuthorizationGateway,
    private val remote: DriveRemoteGateway,
    private val clock: Clock = Clock.systemUTC(),
    private val idSource: () -> String = { UUID.randomUUID().toString() },
    private val maxBackupBytes: Int = DEFAULT_MAX_BACKUP_BYTES
) : CloudBackupProvider {
    private val uploadMutex = Mutex()
    private var activeAccessToken: String? = null

    init {
        require(maxBackupBytes > 0)
    }

    override suspend fun uploadBackup(
        bytes: ByteArray,
        metadata: CloudBackupUploadMetadata
    ): CloudBackupResult<CloudBackupUploadResult> = uploadMutex.withLock {
        if (bytes.size > maxBackupBytes) {
            return@withLock CloudBackupResult.Failure(
                CloudBackupError(CloudBackupErrorCode.BACKUP_TOO_LARGE)
            )
        }

        val contentSha256 = sha256Hex(bytes)
        val fileRequest = DriveFileCreateRequest(
            name = backupFileName(),
            createdAt = metadata.createdAt,
            envelopeFormatVersion = metadata.envelopeFormatVersion,
            payloadSchemaVersion = metadata.payloadSchemaVersion,
            contentSha256 = contentSha256,
            bytes = bytes.copyOf()
        )

        val created = withAuthorization(CloudBackupErrorCode.UPLOAD_FAILED) {
            remote.createFile(it, fileRequest)
        }
        if (created is CloudBackupResult.Failure) {
            return@withLock created
        }
        val createdMetadata = (created as CloudBackupResult.Success).value
        val generation = toGeneration(createdMetadata)
        if (generation == null) {
            val cleanupPending = bestEffortDelete(createdMetadata.id)
            return@withLock verificationFailure(cleanupPending)
        }

        val verificationDownload = withAuthorization(CloudBackupErrorCode.UPLOAD_VERIFICATION_FAILED) {
            remote.openDownload(it, CloudDriveFileId(createdMetadata.id))
        }
        if (verificationDownload is CloudBackupResult.Failure) {
            val cleanupPending = bestEffortDelete(createdMetadata.id)
            return@withLock verificationFailure(
                cleanupPending,
                verificationDownload.error
            )
        }

        val readback = readDownload((verificationDownload as CloudBackupResult.Success).value)
        if (readback is BoundedRead.TooLarge || readback is BoundedRead.Failed) {
            val cleanupPending = bestEffortDelete(createdMetadata.id)
            return@withLock verificationFailure(
                cleanupPending,
                if (readback is BoundedRead.TooLarge) {
                    CloudBackupError(CloudBackupErrorCode.BACKUP_TOO_LARGE)
                } else {
                    CloudBackupError(CloudBackupErrorCode.DOWNLOAD_FAILED)
                }
            )
        }
        val readbackBytes = (readback as BoundedRead.Success).bytes
        if (!MessageDigest.isEqual(bytes, readbackBytes) || sha256Hex(readbackBytes) != contentSha256) {
            val cleanupPending = bestEffortDelete(createdMetadata.id)
            return@withLock verificationFailure(cleanupPending)
        }

        val retentionCleanupPending = pruneOldGenerations()
        CloudBackupResult.Success(
            CloudBackupUploadResult(
                generation = generation,
                verified = true,
                retentionCleanupPending = retentionCleanupPending
            )
        )
    }

    override suspend fun listBackups(): CloudBackupResult<List<CloudBackupGeneration>> {
        val generations = mutableListOf<CloudBackupGeneration>()
        var pageToken: String? = null
        val seenPageTokens = mutableSetOf<String>()
        while (true) {
            val page = withAuthorization(CloudBackupErrorCode.MALFORMED_REMOTE_METADATA) {
                remote.listFiles(it, pageToken)
            }
            if (page is CloudBackupResult.Failure) {
                return page
            }
            val value = (page as CloudBackupResult.Success).value
            value.files.mapNotNullTo(generations, ::toGeneration)
            val next = value.nextPageToken
            if (next == null) break
            if (!seenPageTokens.add(next)) {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.MALFORMED_REMOTE_METADATA)
                )
            }
            pageToken = next
        }
        return CloudBackupResult.Success(sortNewestFirst(generations))
    }

    override suspend fun downloadBackup(id: CloudBackupId): CloudBackupResult<ByteArray> {
        val metadata = withAuthorization(CloudBackupErrorCode.DOWNLOAD_FAILED) {
            remote.getFileMetadata(it, CloudDriveFileId(id.value))
        }
        if (metadata is CloudBackupResult.Failure) return metadata
        val remoteMetadata = (metadata as CloudBackupResult.Success).value
        val generation = toGeneration(remoteMetadata)
            ?: return CloudBackupResult.Failure(
                CloudBackupError(CloudBackupErrorCode.MALFORMED_REMOTE_METADATA)
            )
        if (remoteMetadata.sizeBytes != null && remoteMetadata.sizeBytes > maxBackupBytes) {
            return CloudBackupResult.Failure(
                CloudBackupError(CloudBackupErrorCode.BACKUP_TOO_LARGE)
            )
        }

        val opened = withAuthorization(CloudBackupErrorCode.DOWNLOAD_FAILED) {
            remote.openDownload(it, CloudDriveFileId(id.value))
        }
        if (opened is CloudBackupResult.Failure) return opened
        val readback = readDownload((opened as CloudBackupResult.Success).value)
        return when (readback) {
            is BoundedRead.Success -> {
                if (sha256Hex(readback.bytes) != generation.contentSha256) {
                    CloudBackupResult.Failure(
                        CloudBackupError(CloudBackupErrorCode.DOWNLOAD_FAILED)
                    )
                } else {
                    CloudBackupResult.Success(readback.bytes)
                }
            }

            BoundedRead.TooLarge -> CloudBackupResult.Failure(
                CloudBackupError(CloudBackupErrorCode.BACKUP_TOO_LARGE)
            )

            is BoundedRead.Failed -> CloudBackupResult.Failure(
                CloudBackupError(CloudBackupErrorCode.DOWNLOAD_FAILED)
            )
        }
    }

    override suspend fun deleteBackup(id: CloudBackupId): CloudBackupResult<Unit> =
        withAuthorization(CloudBackupErrorCode.DELETE_FAILED) {
            remote.deleteFile(it, CloudDriveFileId(id.value))
        }

    override suspend fun disconnect(): CloudBackupResult<Unit> =
        when (val result = authorization.disconnect()) {
            AuthorizationOperationResult.Success -> {
                activeAccessToken = null
                CloudBackupResult.Success(Unit)
            }
            is AuthorizationOperationResult.Failure -> CloudBackupResult.Failure(
                CloudBackupError(
                    if (result.code == AuthorizationOperationErrorCode.UNAVAILABLE) {
                        CloudBackupErrorCode.AUTHORIZATION_UNAVAILABLE
                    } else {
                        CloudBackupErrorCode.AUTHORIZATION_FAILED
                    }
                )
            )
        }

    private suspend fun pruneOldGenerations(): Boolean {
        val listed = listBackups()
        if (listed is CloudBackupResult.Failure) return true
        val oldGenerations = (listed as CloudBackupResult.Success).value.drop(RETENTION_LIMIT)
        var pending = false
        for (generation in oldGenerations) {
            if (deleteBackup(generation.id) is CloudBackupResult.Failure) {
                pending = true
            }
        }
        return pending
    }

    private suspend fun bestEffortDelete(id: String): Boolean =
        deleteBackup(CloudBackupId(id)) is CloudBackupResult.Failure

    private suspend fun <T> withAuthorization(
        operationFailureCode: CloudBackupErrorCode,
        operation: suspend (String) -> DriveRemoteResult<T>
    ): CloudBackupResult<T> {
        val firstToken = activeAccessToken ?: when (val firstAuthorization = authorization.authorize()) {
            is CloudAuthorizationOutcome.Authorized -> firstAuthorization.accessToken.also {
                activeAccessToken = it
            }
            is CloudAuthorizationOutcome.UserResolutionRequired -> {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_REQUIRED)
                )
            }

            CloudAuthorizationOutcome.Cancelled -> {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_CANCELLED)
                )
            }

            CloudAuthorizationOutcome.Unavailable -> {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_UNAVAILABLE)
                )
            }

            is CloudAuthorizationOutcome.Error -> {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_FAILED)
                )
            }
        }
        if (firstToken.isBlank()) {
            return CloudBackupResult.Failure(
                CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_FAILED)
            )
        }

        val firstAttempt = operation(firstToken)
        if (firstAttempt is DriveRemoteResult.Success) {
            return CloudBackupResult.Success(firstAttempt.value)
        }
        val firstFailure = (firstAttempt as DriveRemoteResult.Failure).error
        if (firstFailure.category != DriveRemoteErrorCategory.UNAUTHORIZED) {
            return CloudBackupResult.Failure(mapRemoteError(firstFailure, operationFailureCode))
        }

        authorization.clearToken(firstToken)
        activeAccessToken = null
        val refreshedAuthorization = authorization.authorize()
        val refreshedToken = when (refreshedAuthorization) {
            is CloudAuthorizationOutcome.Authorized -> refreshedAuthorization.accessToken.also {
                activeAccessToken = it
            }
            is CloudAuthorizationOutcome.UserResolutionRequired -> {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_REQUIRED)
                )
            }

            CloudAuthorizationOutcome.Cancelled -> {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_CANCELLED)
                )
            }

            CloudAuthorizationOutcome.Unavailable -> {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_UNAVAILABLE)
                )
            }

            is CloudAuthorizationOutcome.Error -> {
                return CloudBackupResult.Failure(
                    CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_FAILED)
                )
            }
        }
        if (refreshedToken.isBlank()) {
            return CloudBackupResult.Failure(
                CloudBackupError(CloudBackupErrorCode.AUTHORIZATION_FAILED)
            )
        }
        val secondAttempt = operation(refreshedToken)
        return when (secondAttempt) {
            is DriveRemoteResult.Success -> CloudBackupResult.Success(secondAttempt.value)
            is DriveRemoteResult.Failure -> CloudBackupResult.Failure(
                if (secondAttempt.error.category == DriveRemoteErrorCategory.UNAUTHORIZED) {
                    activeAccessToken = null
                    CloudBackupError(CloudBackupErrorCode.TOKEN_EXPIRED)
                } else {
                    mapRemoteError(secondAttempt.error, operationFailureCode)
                }
            )
        }
    }

    private fun mapRemoteError(
        error: DriveRemoteError,
        operationFailureCode: CloudBackupErrorCode
    ): CloudBackupError = CloudBackupError(
        when (error.category) {
            DriveRemoteErrorCategory.UNAUTHORIZED -> CloudBackupErrorCode.TOKEN_EXPIRED
            DriveRemoteErrorCategory.FORBIDDEN -> CloudBackupErrorCode.FORBIDDEN
            DriveRemoteErrorCategory.NOT_FOUND -> CloudBackupErrorCode.NOT_FOUND
            DriveRemoteErrorCategory.TIMEOUT -> CloudBackupErrorCode.TIMEOUT
            DriveRemoteErrorCategory.RATE_LIMITED -> CloudBackupErrorCode.RATE_LIMITED
            DriveRemoteErrorCategory.SERVER -> CloudBackupErrorCode.SERVER_ERROR
            DriveRemoteErrorCategory.NETWORK -> CloudBackupErrorCode.NETWORK
            DriveRemoteErrorCategory.MALFORMED_RESPONSE -> operationFailureCode
        }
    )

    private fun verificationFailure(
        cleanupPending: Boolean,
        underlying: CloudBackupError? = null
    ): CloudBackupResult.Failure {
        val code = when (underlying?.code) {
            CloudBackupErrorCode.AUTHORIZATION_REQUIRED,
            CloudBackupErrorCode.AUTHORIZATION_CANCELLED,
            CloudBackupErrorCode.AUTHORIZATION_UNAVAILABLE,
            CloudBackupErrorCode.AUTHORIZATION_FAILED,
            CloudBackupErrorCode.TOKEN_EXPIRED -> underlying.code

            else -> CloudBackupErrorCode.UPLOAD_VERIFICATION_FAILED
        }
        return CloudBackupResult.Failure(
            CloudBackupError(code, orphanCleanupPending = cleanupPending)
        )
    }

    private fun toGeneration(metadata: DriveFileMetadata): CloudBackupGeneration? {
        val createdAt = metadata.createdTime ?: return null
        try {
            Instant.parse(createdAt)
        } catch (_: DateTimeParseException) {
            return null
        }
        val properties = metadata.appProperties
        if (metadata.id.isBlank() || metadata.name.isBlank() ||
            properties["evoluneKind"] != APP_PROPERTY_BACKUP ||
            properties["backupFormat"] != APP_PROPERTY_NATIVE ||
            properties["envelopeFormatVersion"]?.toIntOrNull() == null ||
            properties["payloadSchemaVersion"]?.toIntOrNull() == null
        ) {
            return null
        }
        val sha256 = properties["contentSha256"] ?: return null
        if (!SHA256_PATTERN.matches(sha256) || metadata.sizeBytes?.let { it < 0 } == true) {
            return null
        }
        return CloudBackupGeneration(
            id = CloudBackupId(metadata.id),
            name = metadata.name,
            createdAt = createdAt,
            sizeBytes = metadata.sizeBytes,
            contentSha256 = sha256
        )
    }

    private fun sortNewestFirst(
        generations: List<CloudBackupGeneration>
    ): List<CloudBackupGeneration> = generations.sortedWith(
        compareByDescending<CloudBackupGeneration> { Instant.parse(it.createdAt) }
            .thenByDescending { it.id.value }
    )

    private fun backupFileName(): String {
        val timestamp = clock.instant().toString().replace(UNSAFE_FILENAME_CHARS, "_")
        return "evolune-backup-$timestamp-${idSource()}.evbackup"
    }

    private fun readDownload(download: DriveDownload): BoundedRead = try {
        download.use { readBounded(it.input) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        BoundedRead.Failed
    }

    private fun readBounded(input: InputStream): BoundedRead {
        val output = ByteArrayOutputStream(minOf(maxBackupBytes, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > maxBackupBytes) return BoundedRead.TooLarge
            output.write(buffer, 0, count)
        }
        return BoundedRead.Success(output.toByteArray())
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private sealed interface BoundedRead {
        data class Success(val bytes: ByteArray) : BoundedRead

        data object TooLarge : BoundedRead

        data object Failed : BoundedRead
    }

    companion object {
        const val RETENTION_LIMIT = 3
        const val DEFAULT_MAX_BACKUP_BYTES = 16 * 1024 * 1024
        const val APP_PROPERTY_BACKUP = "backup"
        const val APP_PROPERTY_NATIVE = "native"
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9_.-]")
    }
}
