package io.github.yingqiu0871.evolune.backup.cloud.google

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun interface DriveUrlConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

/**
 * Small Drive v3 REST adapter. It only addresses files in appDataFolder and
 * does not depend on the deprecated Drive Android client.
 */
class HttpUrlConnectionDriveRemoteGateway(
    private val connectionFactory: DriveUrlConnectionFactory =
        DriveUrlConnectionFactory { it.openConnection() as HttpURLConnection },
    private val filesUrl: String = DRIVE_FILES_URL,
    private val uploadUrl: String = DRIVE_UPLOAD_URL
) : DriveRemoteGateway {
    override suspend fun createFile(
        accessToken: String,
        request: DriveFileCreateRequest
    ): DriveRemoteResult<DriveFileMetadata> {
        val boundary = "evolune-${request.contentSha256.take(16)}"
        val metadata = buildJsonObject {
            put("name", request.name)
            put("mimeType", BACKUP_MIME_TYPE)
            putJsonArray("parents") { add(APP_DATA_FOLDER) }
            putJsonObject("appProperties") {
                put("evoluneKind", "backup")
                put("backupFormat", "native")
                put("envelopeFormatVersion", request.envelopeFormatVersion)
                put("payloadSchemaVersion", request.payloadSchemaVersion)
                put("backupCreatedAt", request.createdAt)
                put("contentSha256", request.contentSha256)
            }
        }.toString().toByteArray(StandardCharsets.UTF_8)
        val body = multipartBody(boundary, metadata, request.bytes)
        val response = executeJson(
            method = "POST",
            url = "$uploadUrl?uploadType=multipart&fields=id,name,createdTime,size,appProperties",
            accessToken = accessToken,
            requestBody = body,
            contentType = "multipart/related; boundary=$boundary"
        )
        return when (response) {
            is JsonResponse.Success -> response.body?.let(::parseMetadata)?.let {
                DriveRemoteResult.Success(it)
            } ?: DriveRemoteResult.Failure(malformedResponse())

            is JsonResponse.Failure -> DriveRemoteResult.Failure(response.error)
        }
    }

    override suspend fun getFileMetadata(
        accessToken: String,
        id: CloudDriveFileId
    ): DriveRemoteResult<DriveFileMetadata> {
        val response = executeJson(
            method = "GET",
            url = "$filesUrl/${urlEncode(id.value)}?fields=id,name,createdTime,size,appProperties",
            accessToken = accessToken
        )
        return when (response) {
            is JsonResponse.Success -> response.body?.let(::parseMetadata)?.let {
                DriveRemoteResult.Success(it)
            } ?: DriveRemoteResult.Failure(malformedResponse())

            is JsonResponse.Failure -> DriveRemoteResult.Failure(response.error)
        }
    }

    override suspend fun listFiles(
        accessToken: String,
        pageToken: String?
    ): DriveRemoteResult<DriveFileListPage> {
        val query = buildString {
            append("spaces=")
            append(urlEncode(APP_DATA_FOLDER))
            append("&pageSize=100")
            append("&fields=nextPageToken,files(id,name,createdTime,size,appProperties)")
            if (pageToken != null) {
                append("&pageToken=")
                append(urlEncode(pageToken))
            }
        }
        val response = executeJson("GET", "$filesUrl?$query", accessToken)
        return when (response) {
            is JsonResponse.Success -> response.body?.let(::parseListPage)?.let {
                DriveRemoteResult.Success(it)
            } ?: DriveRemoteResult.Failure(malformedResponse())

            is JsonResponse.Failure -> DriveRemoteResult.Failure(response.error)
        }
    }

    override suspend fun openDownload(
        accessToken: String,
        id: CloudDriveFileId
    ): DriveRemoteResult<DriveDownload> {
        val connection = try {
            connectionFactory.open(URL("$filesUrl/${urlEncode(id.value)}?alt=media"))
                .also { configure(it, "GET", accessToken, null, null) }
        } catch (_: SocketTimeoutException) {
            return DriveRemoteResult.Failure(DriveRemoteError(DriveRemoteErrorCategory.TIMEOUT))
        } catch (_: IOException) {
            return DriveRemoteResult.Failure(DriveRemoteError(DriveRemoteErrorCategory.NETWORK))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return DriveRemoteResult.Failure(malformedResponse())
        }

        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                readAndDiscard(connection.errorStream)
                connection.disconnect()
                DriveRemoteResult.Failure(mapStatus(status))
            } else {
                val stream = connection.inputStream
                DriveRemoteResult.Success(
                    DriveDownload(stream) {
                        try {
                            stream.close()
                        } finally {
                            connection.disconnect()
                        }
                    }
                )
            }
        } catch (_: SocketTimeoutException) {
            closeQuietly(connection)
            DriveRemoteResult.Failure(DriveRemoteError(DriveRemoteErrorCategory.TIMEOUT))
        } catch (_: IOException) {
            closeQuietly(connection)
            DriveRemoteResult.Failure(DriveRemoteError(DriveRemoteErrorCategory.NETWORK))
        } catch (e: CancellationException) {
            closeQuietly(connection)
            throw e
        } catch (_: Exception) {
            closeQuietly(connection)
            DriveRemoteResult.Failure(malformedResponse())
        }
    }

    override suspend fun deleteFile(
        accessToken: String,
        id: CloudDriveFileId
    ): DriveRemoteResult<Unit> {
        val response = executeJson(
            method = "DELETE",
            url = "$filesUrl/${urlEncode(id.value)}",
            accessToken = accessToken
        )
        return when (response) {
            is JsonResponse.Success -> DriveRemoteResult.Success(Unit)
            is JsonResponse.Failure -> DriveRemoteResult.Failure(response.error)
        }
    }

    private fun executeJson(
        method: String,
        url: String,
        accessToken: String,
        requestBody: ByteArray? = null,
        contentType: String? = null
    ): JsonResponse {
        val connection = try {
            connectionFactory.open(URL(url)).also {
                configure(it, method, accessToken, requestBody, contentType)
            }
        } catch (_: SocketTimeoutException) {
            return JsonResponse.Failure(DriveRemoteError(DriveRemoteErrorCategory.TIMEOUT))
        } catch (_: IOException) {
            return JsonResponse.Failure(DriveRemoteError(DriveRemoteErrorCategory.NETWORK))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return JsonResponse.Failure(malformedResponse())
        }

        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                readAndDiscard(connection.errorStream)
                JsonResponse.Failure(mapStatus(status))
            } else {
                val bytes = readResponse(connection.inputStream)
                    ?: return JsonResponse.Failure(malformedResponse())
                val body = if (bytes.isEmpty()) null else Json.parseToJsonElement(
                    bytes.toString(StandardCharsets.UTF_8)
                )
                JsonResponse.Success(body)
            }
        } catch (_: SocketTimeoutException) {
            JsonResponse.Failure(DriveRemoteError(DriveRemoteErrorCategory.TIMEOUT))
        } catch (_: IOException) {
            JsonResponse.Failure(DriveRemoteError(DriveRemoteErrorCategory.NETWORK))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            JsonResponse.Failure(malformedResponse())
        } finally {
            connection.disconnect()
        }
    }

    private fun configure(
        connection: HttpURLConnection,
        method: String,
        accessToken: String,
        requestBody: ByteArray?,
        contentType: String?
    ) {
        connection.requestMethod = method
        connection.connectTimeout = HTTP_TIMEOUT_MILLIS
        connection.readTimeout = HTTP_TIMEOUT_MILLIS
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        if (requestBody != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
            connection.setFixedLengthStreamingMode(requestBody.size)
            connection.outputStream.use { it.write(requestBody) }
        }
    }

    private fun multipartBody(boundary: String, metadata: ByteArray, bytes: ByteArray): ByteArray {
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
            .toByteArray(StandardCharsets.UTF_8)
        val middle = "\r\n--$boundary\r\nContent-Type: $BACKUP_MIME_TYPE\r\n\r\n"
            .toByteArray(StandardCharsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        return ByteArrayOutputStream(prefix.size + metadata.size + middle.size + bytes.size + suffix.size)
            .apply {
                write(prefix)
                write(metadata)
                write(middle)
                write(bytes)
                write(suffix)
            }
            .toByteArray()
    }

    private fun parseMetadata(element: JsonElement): DriveFileMetadata? = try {
        val objectValue = element.jsonObject
        val id = objectValue["id"]?.jsonPrimitive?.content ?: return null
        val name = objectValue["name"]?.jsonPrimitive?.content ?: return null
        val createdTime = objectValue["createdTime"]?.jsonPrimitive?.content
        val size = objectValue["size"]?.jsonPrimitive?.content?.toLongOrNull()
        val properties = objectValue["appProperties"]?.jsonObject?.mapNotNull { (key, value) ->
            value.jsonPrimitive.contentOrNull?.let { key to it }
        }?.toMap() ?: emptyMap()
        DriveFileMetadata(id, name, createdTime, size, properties)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun parseListPage(element: JsonElement): DriveFileListPage? = try {
        val objectValue = element.jsonObject
        val files = objectValue["files"]?.let { filesElement ->
            filesElement.jsonArray.mapNotNull(::parseMetadata)
        } ?: emptyList()
        DriveFileListPage(
            files = files,
            nextPageToken = objectValue["nextPageToken"]?.jsonPrimitive?.contentOrNull
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun readResponse(input: java.io.InputStream): ByteArray? {
        input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_JSON_RESPONSE_BYTES) return null
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun readAndDiscard(input: java.io.InputStream?) {
        input ?: return
        input.use {
            val buffer = ByteArray(1024)
            var remaining = MAX_ERROR_RESPONSE_BYTES
            while (remaining > 0) {
                val count = it.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                remaining -= count
            }
        }
    }

    private fun closeQuietly(connection: HttpURLConnection) {
        runCatching { connection.errorStream?.close() }
        connection.disconnect()
    }

    private fun mapStatus(status: Int): DriveRemoteError = DriveRemoteError(
        when (status) {
            401 -> DriveRemoteErrorCategory.UNAUTHORIZED
            403 -> DriveRemoteErrorCategory.FORBIDDEN
            404 -> DriveRemoteErrorCategory.NOT_FOUND
            408 -> DriveRemoteErrorCategory.TIMEOUT
            429 -> DriveRemoteErrorCategory.RATE_LIMITED
            in 500..599 -> DriveRemoteErrorCategory.SERVER
            else -> DriveRemoteErrorCategory.MALFORMED_RESPONSE
        }
    )

    private fun malformedResponse() =
        DriveRemoteError(DriveRemoteErrorCategory.MALFORMED_RESPONSE)

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private sealed interface JsonResponse {
        data class Success(val body: JsonElement?) : JsonResponse

        data class Failure(val error: DriveRemoteError) : JsonResponse
    }

    companion object {
        const val APP_DATA_FOLDER = "appDataFolder"
        const val BACKUP_MIME_TYPE = "application/octet-stream"
        const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val HTTP_TIMEOUT_MILLIS = 15_000
        const val MAX_JSON_RESPONSE_BYTES = 1_048_576
        const val MAX_ERROR_RESPONSE_BYTES = 65_536
    }
}
