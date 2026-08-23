package io.github.yingqiu0871.evolune.backup.cloud.google

import kotlinx.coroutines.runBlocking
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

class GoogleDriveRestGatewayTest {
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
        val output = ByteArrayOutputStream()
        var disconnected = false

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getResponseCode(): Int = status

        override fun getInputStream(): InputStream = ByteArrayInputStream(
            responseText.toByteArray(StandardCharsets.UTF_8)
        )

        override fun getErrorStream(): InputStream = ByteArrayInputStream(
            responseText.toByteArray(StandardCharsets.UTF_8)
        )

        override fun getOutputStream(): OutputStream = output
    }

    companion object {
        private val BYTES = byteArrayOf(1, 2, 3)

        private fun sha256(bytes: ByteArray): String = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
