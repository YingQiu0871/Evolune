package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant

/**
 * V17 Phase E canonical export service (frozen contract §8/§9/§30/§31/§32/§33).
 *
 * Read-only: it consumes the approved `DoseEventRepository` read seams and performs **zero**
 * repository writes. `capturedAt` is captured exactly once per export action, normalized to
 * epoch-millisecond precision, and drives range boundaries / `captured_at` / file naming.
 * Serialization always runs on [ioDispatcher] (off main thread).
 */
class PortableExportService(
    private val repository: DoseEventRepository,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun export(
        range: PortableExportRange,
        format: PortableExportFormat
    ): PortableExportResult = withContext(ioDispatcher) {
        val capturedAt = Instant.ofEpochMilli(clock.instant().toEpochMilli())
        val resolved = resolvePortableRange(range, capturedAt)

        val rows = try {
            if (resolved.startInclusive == null) {
                repository.findAllOccurredUpTo(capturedAt)
            } else {
                repository.findOccurredBetween(resolved.startInclusive, capturedAt.plusMillis(1))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            return@withContext PortableExportResult.UnexpectedFailure
        }

        val bounded = if (resolved.startInclusive == null) {
            rows
        } else {
            rows.filter { !it.occurredAt.isAfter(capturedAt) }
        }

        val events = ArrayList<PortableEvent>(bounded.size)
        for (row in bounded) {
            val event = row.toPortableEvent()
            if (!event.isSerializable()) return@withContext PortableExportResult.InvalidData
            events += event
        }

        val sorted = events.sortedWith(
            compareBy({ it.occurredAt }, { it.id.toString() })
        )

        val request = PortableExportRequest(range = range, format = format, capturedAt = capturedAt)
        val bytes = when (format) {
            PortableExportFormat.JSON -> PortableJsonCodec.encode(request, sorted)
            PortableExportFormat.CSV -> PortableCsvCodec.encode(sorted)
        } ?: return@withContext PortableExportResult.InvalidData

        PortableExportResult.Success(
            bytes = bytes,
            fileName = portableExportFileName(range, format, capturedAt)
        )
    }
}
