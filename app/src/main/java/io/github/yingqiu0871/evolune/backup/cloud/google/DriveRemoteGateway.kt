package io.github.yingqiu0871.evolune.backup.cloud.google

import java.io.Closeable
import java.io.InputStream

data class DriveFileCreateRequest(
    val name: String,
    val createdAt: String,
    val envelopeFormatVersion: Int,
    val payloadSchemaVersion: Int,
    val contentSha256: String,
    val bytes: ByteArray
)

data class DriveFileMetadata(
    val id: String,
    val name: String,
    val createdTime: String?,
    val sizeBytes: Long?,
    val appProperties: Map<String, String>
)

data class DriveFileListPage(
    val files: List<DriveFileMetadata>,
    val nextPageToken: String?
)

class DriveDownload(
    val input: InputStream,
    private val closeAction: () -> Unit = { input.close() }
) : Closeable {
    override fun close() = closeAction()
}

enum class DriveRemoteErrorCategory {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    TIMEOUT,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    MALFORMED_RESPONSE
}

data class DriveRemoteError(val category: DriveRemoteErrorCategory)

sealed interface DriveRemoteResult<out T> {
    data class Success<T>(val value: T) : DriveRemoteResult<T>

    data class Failure(val error: DriveRemoteError) : DriveRemoteResult<Nothing>
}

interface DriveRemoteGateway {
    suspend fun createFile(
        accessToken: String,
        request: DriveFileCreateRequest
    ): DriveRemoteResult<DriveFileMetadata>

    suspend fun getFileMetadata(
        accessToken: String,
        id: CloudDriveFileId
    ): DriveRemoteResult<DriveFileMetadata>

    suspend fun listFiles(
        accessToken: String,
        pageToken: String?
    ): DriveRemoteResult<DriveFileListPage>

    suspend fun openDownload(
        accessToken: String,
        id: CloudDriveFileId
    ): DriveRemoteResult<DriveDownload>

    suspend fun deleteFile(
        accessToken: String,
        id: CloudDriveFileId
    ): DriveRemoteResult<Unit>
}

@JvmInline
value class CloudDriveFileId(val value: String)
