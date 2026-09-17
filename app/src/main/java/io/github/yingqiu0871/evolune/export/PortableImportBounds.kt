package io.github.yingqiu0871.evolune.export

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Frozen Phase E import bounds (contract §32). */
object PortableImportBounds {
    const val MAX_INPUT_BYTES: Int = 32 * 1024 * 1024
    const val MAX_EVENT_COUNT: Int = 100_000
}

/** Result of a bounded UTF-8 read that refuses to buffer past [PortableImportBounds.MAX_INPUT_BYTES]. */
sealed interface BoundedReadResult {
    data class Success(val bytes: ByteArray) : BoundedReadResult

    data object TooLarge : BoundedReadResult

    data object Failure : BoundedReadResult
}

/**
 * Reads at most [maxBytes] bytes from [stream]; returns [BoundedReadResult.TooLarge] as soon as the
 * input exceeds the bound (never fully buffering an oversized document). The caller owns [stream].
 */
fun readBoundedBytes(
    stream: InputStream,
    maxBytes: Int = PortableImportBounds.MAX_INPUT_BYTES
): BoundedReadResult {
    val buffer = ByteArray(64 * 1024)
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    var total = 0
    try {
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return BoundedReadResult.TooLarge
            output.write(buffer, 0, read)
        }
    } catch (_: IOException) {
        return BoundedReadResult.Failure
    }
    return BoundedReadResult.Success(output.toByteArray())
}
