package io.github.yingqiu0871.evolune.backup.cloud.google

import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationErrorCode
import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationOperationResult
import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationResolution
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationGateway
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationOutcome
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupErrorCode
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupId
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupResult
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupUploadMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque

class GoogleDriveBackupProviderTest {
    @Test
    fun `successful upload verifies readback before retention`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        val result = provider(remote).uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))

        val success = result as CloudBackupResult.Success
        assertTrue(success.value.verified)
        assertFalse(success.value.retentionCleanupPending)
        assertEquals(1, remote.createCalls)
        assertEquals(1, remote.openDownloadCalls)
        assertTrue(remote.listCalls > 0)
        assertTrue(remote.deleteCalls.isEmpty())
    }

    @Test
    fun `readback mismatch fails and never prunes old generations`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        remote.downloadOverrides["new-1"] = byteArrayOf(9, 9, 9)
        remote.addValidFile("old-1", "2026-08-20T00:00:00Z", byteArrayOf(1))

        val result = provider(remote).uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))

        assertEquals(CloudBackupErrorCode.UPLOAD_VERIFICATION_FAILED, failureCode(result))
        assertEquals(listOf("new-1"), remote.deleteCalls)
        assertFalse(remote.deleteCalls.contains("old-1"))
        assertEquals(0, remote.listCalls)
    }

    @Test
    fun `verification download failure fails and only attempts new orphan cleanup`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        remote.openDownloadFailure = DriveRemoteErrorCategory.NETWORK
        remote.addValidFile("old-1", "2026-08-20T00:00:00Z", byteArrayOf(1))

        val result = provider(remote).uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))

        assertEquals(CloudBackupErrorCode.UPLOAD_VERIFICATION_FAILED, failureCode(result))
        assertEquals(listOf("new-1"), remote.deleteCalls)
        assertFalse(remote.deleteCalls.contains("old-1"))
        assertEquals(0, remote.listCalls)
    }

    @Test
    fun `failed orphan cleanup is reported without touching old generations`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        remote.downloadOverrides["new-1"] = byteArrayOf(8)
        remote.failDeleteIds += "new-1"
        remote.addValidFile("old-1", "2026-08-20T00:00:00Z", byteArrayOf(1))

        val result = provider(remote).uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))

        assertEquals(CloudBackupErrorCode.UPLOAD_VERIFICATION_FAILED, failureCode(result))
        assertTrue((result as CloudBackupResult.Failure).error.orphanCleanupPending)
        assertEquals(listOf("new-1"), remote.deleteCalls)
        assertFalse(remote.deleteCalls.contains("old-1"))
    }

    @Test
    fun `retention keeps latest three after zero two three and ten existing files`() = runBlocking {
        for (existingCount in listOf(0, 2, 3, 10)) {
            val remote = FakeDriveRemoteGateway()
            repeat(existingCount) { index ->
                remote.addValidFile(
                    id = "old-$index",
                    createdAt = "2026-08-${(10 + index).toString().padStart(2, '0')}T00:00:00Z",
                    bytes = byteArrayOf(index.toByte())
                )
            }

            val result = provider(remote).uploadBackup(
                BYTES,
                metadata("2026-08-23T00:00:00Z")
            )

            assertTrue(result is CloudBackupResult.Success)
            val remaining = remote.files.keys.filterNot { it in remote.deleteCalls }
            assertTrue("existingCount=$existingCount remaining=$remaining", remaining.size <= 3)
            assertTrue("existingCount=$existingCount remaining=$remaining", "new-1" in remaining)
        }
    }

    @Test
    fun `retention deletion failure returns verified success with pending warning`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        remote.addValidFile("old-1", "2026-08-20T00:00:00Z", byteArrayOf(1))
        remote.addValidFile("old-2", "2026-08-19T00:00:00Z", byteArrayOf(2))
        remote.addValidFile("old-3", "2026-08-18T00:00:00Z", byteArrayOf(3))
        remote.failDeleteIds += "old-3"

        val result = provider(remote).uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))

        val success = result as CloudBackupResult.Success
        assertTrue(success.value.verified)
        assertTrue(success.value.retentionCleanupPending)
        assertTrue("old-3" in remote.deleteCalls)
        assertFalse("old-3" !in remote.files)
    }

    @Test
    fun `list reads every page filters markers and sorts equal timestamps by file id`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        remote.pages = listOf(
            listOf(
                remote.validMetadata("b", "2026-08-23T00:00:00Z", byteArrayOf(1)),
                remote.unrelatedMetadata("root-file")
            ),
            listOf(
                remote.validMetadata("a", "2026-08-23T00:00:00Z", byteArrayOf(2)),
                remote.invalidMarkerMetadata("bad-marker"),
                remote.malformedTimeMetadata("bad-time")
            )
        )

        val result = provider(remote).listBackups()

        val generations = (result as CloudBackupResult.Success).value
        assertEquals(listOf("b", "a"), generations.map { it.id.value })
        assertEquals(2, remote.listCalls)
    }

    @Test
    fun `download rejects metadata above cap before opening body`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        remote.files["too-large"] = remote.validMetadata(
            "too-large",
            "2026-08-23T00:00:00Z",
            BYTE_CAP_PLUS_ONE,
            sizeOverride = BYTE_CAP_PLUS_ONE.size.toLong()
        )

        val result = provider(remote, maxBytes = BYTE_CAP).downloadBackup(CloudBackupId("too-large"))

        assertEquals(CloudBackupErrorCode.BACKUP_TOO_LARGE, failureCode(result))
        assertEquals(0, remote.openDownloadCalls)
    }

    @Test
    fun `download enforces body cap when size is missing and when stream overflows`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        remote.files["missing-size"] = remote.validMetadata(
            "missing-size",
            "2026-08-23T00:00:00Z",
            BYTE_CAP_PLUS_ONE,
            sizeOverride = null
        )
        remote.bytesById["missing-size"] = BYTE_CAP_PLUS_ONE.copyOf()

        val missingSize = provider(remote, maxBytes = BYTE_CAP)
            .downloadBackup(CloudBackupId("missing-size"))
        assertEquals(CloudBackupErrorCode.BACKUP_TOO_LARGE, failureCode(missingSize))

        remote.files["wrong-size"] = remote.validMetadata(
            "wrong-size",
            "2026-08-23T00:00:01Z",
            BYTE_CAP_PLUS_ONE,
            sizeOverride = BYTE_CAP.toLong()
        )
        remote.bytesById["wrong-size"] = BYTE_CAP_PLUS_ONE.copyOf()
        val overflow = provider(remote, maxBytes = BYTE_CAP)
            .downloadBackup(CloudBackupId("wrong-size"))
        assertEquals(CloudBackupErrorCode.BACKUP_TOO_LARGE, failureCode(overflow))
    }

    @Test
    fun `download maps not found and interrupted stream to stable failures`() = runBlocking {
        val remote = FakeDriveRemoteGateway()
        remote.metadataFailures["missing"] = DriveRemoteErrorCategory.NOT_FOUND
        assertEquals(
            CloudBackupErrorCode.NOT_FOUND,
            failureCode(provider(remote).downloadBackup(CloudBackupId("missing")))
        )

        remote.files["interrupted"] = remote.validMetadata(
            "interrupted",
            "2026-08-23T00:00:00Z",
            BYTES
        )
        remote.interruptedDownloadIds += "interrupted"
        assertEquals(
            CloudBackupErrorCode.DOWNLOAD_FAILED,
            failureCode(provider(remote).downloadBackup(CloudBackupId("interrupted")))
        )
    }

    @Test
    fun `authorized access is reused for successful foreground operation`() = runBlocking {
        val auth = FakeAuthorizationGateway(
            ArrayDeque(listOf(CloudAuthorizationOutcome.Authorized("token")))
        )
        val result = GoogleDriveBackupProvider(
            authorization = auth,
            remote = FakeDriveRemoteGateway(),
            clock = FIXED_CLOCK,
            idSource = { "id" }
        ).uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))

        assertTrue(result is CloudBackupResult.Success)
        assertEquals(1, auth.authorizeCalls)
    }

    @Test
    fun `resolution cancellation unavailable and token errors do not mutate Drive`() = runBlocking {
        val outcomes = listOf(
            CloudAuthorizationOutcome.UserResolutionRequired(TestResolution),
            CloudAuthorizationOutcome.Cancelled,
            CloudAuthorizationOutcome.Unavailable,
            CloudAuthorizationOutcome.Error(AuthorizationErrorCode.FAILED)
        )
        outcomes.forEach { outcome ->
            val remote = FakeDriveRemoteGateway()
            val result = GoogleDriveBackupProvider(
                FakeAuthorizationGateway(ArrayDeque(listOf(outcome))),
                remote,
                FIXED_CLOCK,
                { "id" }
            ).uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))
            assertEquals(
                when (outcome) {
                    is CloudAuthorizationOutcome.UserResolutionRequired -> CloudBackupErrorCode.AUTHORIZATION_REQUIRED
                    CloudAuthorizationOutcome.Cancelled -> CloudBackupErrorCode.AUTHORIZATION_CANCELLED
                    CloudAuthorizationOutcome.Unavailable -> CloudBackupErrorCode.AUTHORIZATION_UNAVAILABLE
                    is CloudAuthorizationOutcome.Error -> CloudBackupErrorCode.AUTHORIZATION_FAILED
                    is CloudAuthorizationOutcome.Authorized -> error("not used")
                },
                failureCode(result)
            )
            assertEquals(0, remote.createCalls)
        }
    }

    @Test
    fun `401 clears token authorizes once and retries the same remote operation once`() = runBlocking {
        val auth = FakeAuthorizationGateway(
            ArrayDeque(
                listOf(
                    CloudAuthorizationOutcome.Authorized("expired"),
                    CloudAuthorizationOutcome.Authorized("fresh")
                )
            )
        )
        val remote = FakeDriveRemoteGateway()
        remote.createFailures.add(DriveRemoteErrorCategory.UNAUTHORIZED)

        val result = GoogleDriveBackupProvider(auth, remote, FIXED_CLOCK, { "id" })
            .uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))

        assertTrue(result is CloudBackupResult.Success)
        assertEquals(2, remote.createCalls)
        assertEquals(2, auth.authorizeCalls)
        assertEquals(listOf("expired"), auth.clearedTokens)
    }

    @Test
    fun `second 401 becomes token expired after exactly one retry`() = runBlocking {
        val auth = FakeAuthorizationGateway(
            ArrayDeque(
                listOf(
                    CloudAuthorizationOutcome.Authorized("expired"),
                    CloudAuthorizationOutcome.Authorized("fresh")
                )
            )
        )
        val remote = FakeDriveRemoteGateway()
        remote.createFailures.add(DriveRemoteErrorCategory.UNAUTHORIZED)
        remote.createFailures.add(DriveRemoteErrorCategory.UNAUTHORIZED)

        val result = GoogleDriveBackupProvider(auth, remote, FIXED_CLOCK, { "id" })
            .uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))

        assertEquals(CloudBackupErrorCode.TOKEN_EXPIRED, failureCode(result))
        assertEquals(2, remote.createCalls)
        assertEquals(2, auth.authorizeCalls)
    }

    @Test
    fun `cancellation from remote is propagated`() {
        val remote = FakeDriveRemoteGateway().apply {
            createCancellation = true
        }
        try {
            runBlocking {
                provider(remote).uploadBackup(BYTES, metadata("2026-08-23T00:00:00Z"))
            }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(true)
        }
    }

    @Test
    fun `authorization contract requests only drive appdata without offline access`() {
        val spec = GoogleAuthorizationRequestFactory.requestSpec()

        assertEquals(listOf(GOOGLE_DRIVE_APPDATA_SCOPE), spec.requestedScopes)
        assertFalse(spec.offlineAccess)
    }

    private fun provider(remote: FakeDriveRemoteGateway, maxBytes: Int = 1024): GoogleDriveBackupProvider =
        GoogleDriveBackupProvider(
            authorization = FakeAuthorizationGateway(),
            remote = remote,
            clock = FIXED_CLOCK,
            idSource = { "id" },
            maxBackupBytes = maxBytes
        )

    private fun metadata(createdAt: String) = CloudBackupUploadMetadata(
        createdAt = createdAt,
        envelopeFormatVersion = 1,
        payloadSchemaVersion = 1
    )

    private fun failureCode(result: CloudBackupResult<*>): CloudBackupErrorCode =
        (result as CloudBackupResult.Failure).error.code

    private object TestResolution : AuthorizationResolution

    private class FakeAuthorizationGateway(
        private val outcomes: ArrayDeque<CloudAuthorizationOutcome> = ArrayDeque(
            listOf(CloudAuthorizationOutcome.Authorized("token"))
        )
    ) : CloudAuthorizationGateway {
        var authorizeCalls = 0
        val clearedTokens = mutableListOf<String>()

        override suspend fun authorize(): CloudAuthorizationOutcome {
            authorizeCalls++
            return if (outcomes.isEmpty()) {
                CloudAuthorizationOutcome.Authorized("token")
            } else {
                outcomes.removeFirst()
            }
        }

        override suspend fun clearToken(accessToken: String): AuthorizationOperationResult {
            clearedTokens += accessToken
            return AuthorizationOperationResult.Success
        }

        override suspend fun disconnect(): AuthorizationOperationResult =
            AuthorizationOperationResult.Success
    }

    private class FakeDriveRemoteGateway : DriveRemoteGateway {
        val files = linkedMapOf<String, DriveFileMetadata>()
        val bytesById = linkedMapOf<String, ByteArray>()
        val downloadOverrides = mutableMapOf<String, ByteArray>()
        val failDeleteIds = mutableSetOf<String>()
        val interruptedDownloadIds = mutableSetOf<String>()
        val metadataFailures = mutableMapOf<String, DriveRemoteErrorCategory>()
        val createFailures = ArrayDeque<DriveRemoteErrorCategory>()
        var openDownloadFailure: DriveRemoteErrorCategory? = null
        var createCancellation = false
        var createCalls = 0
        var openDownloadCalls = 0
        var listCalls = 0
        val deleteCalls = mutableListOf<String>()
        var pages: List<List<DriveFileMetadata>>? = null

        override suspend fun createFile(
            accessToken: String,
            request: DriveFileCreateRequest
        ): DriveRemoteResult<DriveFileMetadata> {
            createCalls++
            if (createCancellation) throw CancellationException("test")
            if (createFailures.isNotEmpty()) {
                val it = createFailures.removeFirst()
                return DriveRemoteResult.Failure(DriveRemoteError(it))
            }
            val id = "new-$createCalls"
            val metadata = validMetadata(
                id = id,
                createdAt = request.createdAt,
                bytes = request.bytes
            )
            files[id] = metadata
            bytesById[id] = request.bytes.copyOf()
            return DriveRemoteResult.Success(metadata)
        }

        override suspend fun getFileMetadata(
            accessToken: String,
            id: CloudDriveFileId
        ): DriveRemoteResult<DriveFileMetadata> {
            metadataFailures[id.value]?.let {
                return DriveRemoteResult.Failure(DriveRemoteError(it))
            }
            return files[id.value]?.let { DriveRemoteResult.Success(it) }
                ?: DriveRemoteResult.Failure(
                    DriveRemoteError(DriveRemoteErrorCategory.NOT_FOUND)
                )
        }

        override suspend fun listFiles(
            accessToken: String,
            pageToken: String?
        ): DriveRemoteResult<DriveFileListPage> {
            listCalls++
            val configuredPages = pages
            if (configuredPages != null) {
                val pageIndex = pageToken?.removePrefix("page-")?.toIntOrNull() ?: 0
                val next = if (pageIndex + 1 < configuredPages.size) "page-${pageIndex + 1}" else null
                return DriveRemoteResult.Success(
                    DriveFileListPage(configuredPages[pageIndex], next)
                )
            }
            return DriveRemoteResult.Success(DriveFileListPage(files.values.toList(), null))
        }

        override suspend fun openDownload(
            accessToken: String,
            id: CloudDriveFileId
        ): DriveRemoteResult<DriveDownload> {
            openDownloadCalls++
            openDownloadFailure?.let {
                return DriveRemoteResult.Failure(DriveRemoteError(it))
            }
            if (id.value in interruptedDownloadIds) {
                return DriveRemoteResult.Success(
                    DriveDownload(ThrowingInputStream())
                )
            }
            val bytes = downloadOverrides[id.value] ?: bytesById[id.value]
                ?: return DriveRemoteResult.Failure(
                    DriveRemoteError(DriveRemoteErrorCategory.NOT_FOUND)
                )
            return DriveRemoteResult.Success(DriveDownload(ByteArrayInputStream(bytes)))
        }

        override suspend fun deleteFile(
            accessToken: String,
            id: CloudDriveFileId
        ): DriveRemoteResult<Unit> {
            deleteCalls += id.value
            if (id.value in failDeleteIds) {
                return DriveRemoteResult.Failure(DriveRemoteError(DriveRemoteErrorCategory.NETWORK))
            }
            files.remove(id.value)
            bytesById.remove(id.value)
            return DriveRemoteResult.Success(Unit)
        }

        fun addValidFile(id: String, createdAt: String, bytes: ByteArray) {
            files[id] = validMetadata(id, createdAt, bytes)
            bytesById[id] = bytes.copyOf()
        }

        fun validMetadata(
            id: String,
            createdAt: String,
            bytes: ByteArray,
            sizeOverride: Long? = bytes.size.toLong()
        ): DriveFileMetadata = DriveFileMetadata(
            id = id,
            name = "evolune-backup-$id.evbackup",
            createdTime = createdAt,
            sizeBytes = sizeOverride,
            appProperties = validProperties(bytes)
        )

        fun unrelatedMetadata(id: String) = DriveFileMetadata(
            id = id,
            name = "unrelated",
            createdTime = "2026-08-23T00:00:00Z",
            sizeBytes = 1,
            appProperties = emptyMap()
        )

        fun invalidMarkerMetadata(id: String) = validMetadata(
            id,
            "2026-08-23T00:00:00Z",
            byteArrayOf(1)
        ).copy(appProperties = mapOf("evoluneKind" to "other"))

        fun malformedTimeMetadata(id: String) = validMetadata(
            id,
            "not-an-instant",
            byteArrayOf(1)
        )

        private fun validProperties(bytes: ByteArray) = mapOf(
            "evoluneKind" to "backup",
            "backupFormat" to "native",
            "envelopeFormatVersion" to "1",
            "payloadSchemaVersion" to "1",
            "contentSha256" to sha256Hex(bytes)
        )
    }

    private class ThrowingInputStream : InputStream() {
        override fun read(): Int = throw IOException("interrupted")
    }

    companion object {
        private val FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T00:00:00Z"),
            ZoneOffset.UTC
        )
        private val BYTES = byteArrayOf(1, 2, 3, 4)
        private const val BYTE_CAP = 8
        private val BYTE_CAP_PLUS_ONE = ByteArray(BYTE_CAP + 1) { it.toByte() }

        private fun sha256Hex(bytes: ByteArray): String = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
