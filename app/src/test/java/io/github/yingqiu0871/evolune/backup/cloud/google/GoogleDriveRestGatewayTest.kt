package io.github.yingqiu0871.evolune.backup.cloud.google

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.Executors

class GoogleDriveRestGatewayTest {
    @Test
    fun `all blocking gateway operations run on injected io dispatcher`() {
        val mainExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "drive-main-test")
        }
        val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "drive-io-test")
        }
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        try {
            runBlocking(mainDispatcher) {
                val metadataResponse =
                    """{"id":"g1","name":"backup","createdTime":"2026-08-23T00:00:00Z","size":"3","appProperties":{"evoluneKind":"backup","backupFormat":"native","envelopeFormatVersion":"1","payloadSchemaVersion":"1","contentSha256":"${sha256(BYTES)}"}}"""
                val allConnections = listOf(
                    FakeConnection(URL("https://example.test/upload"), 200, metadataResponse),
                    FakeConnection(URL("https://example.test/files/g1"), 200, metadataResponse),
                    FakeConnection(URL("https://example.test/files"), 200, """{"files":[]}"""),
                    FakeConnection(URL("https://example.test/files/g1?alt=media"), 200, "bytes"),
                    FakeConnection(URL("https://example.test/files/g1"), 204, "")
                )
                val connections = ArrayDeque(allConnections)
                val gateway = HttpUrlConnectionDriveRemoteGateway(
                    connectionFactory = DriveUrlConnectionFactory { url ->
                        connections.removeFirst().also { it.requestedUrl = url }
                    },
                    ioDispatcher = ioDispatcher
                )

                gateway.createFile(
                    "token",
                    DriveFileCreateRequest(
                        "backup",
                        "2026-08-23T00:00:00Z",
                        1,
                        1,
                        sha256(BYTES),
                        BYTES
                    )
                )
                gateway.getFileMetadata("token", CloudDriveFileId("g1"))
                gateway.listFiles("token", null)
                val download = gateway.openDownload("token", CloudDriveFileId("g1"))
                assertTrue(download is DriveRemoteResult.Success)
                withContext(ioDispatcher) {
                    (download as DriveRemoteResult.Success).value.close()
                }
                gateway.deleteFile("token", CloudDriveFileId("g1"))

                val observedThreads = allConnections.flatMap { it.observedThreads }
                assertTrue(observedThreads.isNotEmpty())
                assertTrue(observedThreads.all { it.startsWith("drive-io-test") })
            }
        } finally {
            mainDispatcher.close()
            ioDispatcher.close()
            mainExecutor.shutdownNow()
            ioExecutor.shutdownNow()
        }
    }

    @Test
    fun `create uses appDataFolder and safe marker properties`() = runBlocking {
        val connection = FakeConnection(
            URL("https://example.test/upload"),
            200,
            """{"id":"g1","name":"evolune-backup-a.evbackup","createdTime":"2026-08-23T00:00:00Z","size":"3","appProperties":{"evoluneKind":"backup","backupFormat":"native","envelopeFormatVersion":"1","payloadSchemaVersion":"1","contentSha256":"${sha256(BYTES)}"}}"""
        )
        val gateway = HttpUrlConnectionDriveRemoteGateway(
            connectionFactory = DriveUrlConnectionFactory { url ->
                connection.requestedUrl = url
                connection
            }
        )

        val result = gateway.createFile(
            "token",
            DriveFileCreateRequest("evolune-backup-a.evbackup", "2026-08-23T00:00:00Z", 1, 1, sha256(BYTES), BYTES)
        )

        assertTrue(result is DriveRemoteResult.Success)
        assertTrue(connection.requestedUrl.toString().contains("uploadType=multipart"))
        assertEquals("POST", connection.requestMethod)
        val body = connection.output.toString(StandardCharsets.UTF_8.name())
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("\"evoluneKind\":\"backup\""))
        assertTrue(body.contains("\"backupFormat\":\"native\""))
    }

    @Test
    fun `list is restricted to appDataFolder and delete is direct`() = runBlocking {
        val listConnection = FakeConnection(URL("https://example.test/files"), 200, """{"files":[]}""")
        val gateway = HttpUrlConnectionDriveRemoteGateway(
            connectionFactory = DriveUrlConnectionFactory { url ->
                listConnection.requestedUrl = url
                listConnection
            }
        )

        val listResult = gateway.listFiles("token", null)
        assertTrue(listResult is DriveRemoteResult.Success)
        assertTrue(listConnection.requestedUrl.toString().contains("spaces=appDataFolder"))
        assertFalse(listConnection.requestedUrl.toString().contains("drive.file"))

        val deleteConnection = FakeConnection(URL("https://example.test/files/g1"), 204, "")
        val deleteGateway = HttpUrlConnectionDriveRemoteGateway(
            connectionFactory = DriveUrlConnectionFactory { url ->
                deleteConnection.requestedUrl = url
                deleteConnection
            }
        )
        val deleteResult = deleteGateway.deleteFile("token", CloudDriveFileId("g1"))

        assertTrue(deleteResult is DriveRemoteResult.Success)
        assertEquals("DELETE", deleteConnection.requestMethod)
        assertTrue(deleteConnection.disconnected)
    }

    @Test
    fun `http status mapping does not expose response text`() = runBlocking {
        val connection = FakeConnection(URL("https://example.test/files/g1"), 401, "secret error body")
        val gateway = HttpUrlConnectionDriveRemoteGateway(
            connectionFactory = DriveUrlConnectionFactory { url ->
                connection.requestedUrl = url
                connection
            }
        )

        val result = gateway.getFileMetadata("token", CloudDriveFileId("g1"))

        assertEquals(
            DriveRemoteErrorCategory.UNAUTHORIZED,
            (result as DriveRemoteResult.Failure).error.category
        )
    }

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val responseText: String
    ) : HttpURLConnection(url) {
        var requestedUrl: URL = url
        val observedThreads = mutableListOf<String>()
        val output = object : ByteArrayOutputStream() {
            override fun write(b: Int) {
                observeThread()
                super.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                observeThread()
                super.write(b, off, len)
            }
        }
        var disconnected = false

        override fun disconnect() {
            observeThread()
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun connect() {
            observeThread()
        }

        override fun getResponseCode(): Int {
            observeThread()
            return status
        }

        override fun getInputStream(): InputStream {
            observeThread()
            return recordingInputStream()
        }

        override fun getErrorStream(): InputStream {
            observeThread()
            return recordingInputStream()
        }

        override fun getOutputStream(): OutputStream {
            observeThread()
            return output
        }

        private fun recordingInputStream(): InputStream = object : ByteArrayInputStream(
            responseText.toByteArray(StandardCharsets.UTF_8)
        ) {
            override fun read(): Int {
                observeThread()
                return super.read()
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                observeThread()
                return super.read(b, off, len)
            }
        }

        private fun observeThread() {
            synchronized(observedThreads) {
                observedThreads += Thread.currentThread().name
            }
        }
    }

    companion object {
        private val BYTES = byteArrayOf(1, 2, 3)

        private fun sha256(bytes: ByteArray): String = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
