package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * V17 Phase E canonical import service (frozen contract §18/§19/§32/§34/§38).
 *
 * Additive, never a restore/replace. The whole document is fully validated before the first
 * repository write. Existing rows are classified through the public `getById` seam against the
 * 11 portable fields only (repository revision is repository-local concurrency metadata and is
 * never rebuilt or overwritten). Storage failures abort remaining writes and are reported as
 * typed partial/storage failures; committed rows stay committed.
 */
class PortableImportService(
    private val repository: DoseEventRepository,
    private val maxInputBytes: Int = PortableImportBounds.MAX_INPUT_BYTES,
    private val maxEventCount: Int = PortableImportBounds.MAX_EVENT_COUNT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun import(input: ByteArray): PortableImportOutcome = withContext(ioDispatcher) {
        if (input.size > maxInputBytes) return@withContext PortableImportOutcome.TooLarge

        val document = when (val decoded = PortableJsonCodec.decode(input, maxEventCount)) {
            is PortableJsonDecodeResult.Success -> decoded.document
            is PortableJsonDecodeResult.Failure -> return@withContext when (decoded.error) {
                PortableJsonError.UNSUPPORTED_VERSION -> PortableImportOutcome.UnsupportedVersion
                PortableJsonError.TOO_MANY_EVENTS -> PortableImportOutcome.TooLarge
                else -> PortableImportOutcome.InvalidDocument
            }
        }

        var insertedCount = 0
        var idempotentCount = 0
        var conflictCount = 0
        var processedCount = 0
        val totalCount = document.events.size

        for (event in document.events) {
            try {
                val existing = repository.getById(event.id)
                if (existing == null) {
                    when (repository.insert(event.toDomainEvent())) {
                        InsertResult.Inserted -> insertedCount += 1
                        InsertResult.Idempotent -> idempotentCount += 1
                        InsertResult.Conflict, InsertResult.Invalid -> conflictCount += 1
                    }
                } else if (existing.toPortableEventKey() == event.toPortableEventKey()) {
                    idempotentCount += 1
                } else {
                    conflictCount += 1
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: RuntimeException) {
                return@withContext if (processedCount == 0) {
                    PortableImportOutcome.StorageFailure(totalCount)
                } else {
                    PortableImportOutcome.PartialFailure(
                        insertedCount = insertedCount,
                        idempotentCount = idempotentCount,
                        conflictCount = conflictCount,
                        processedCount = processedCount,
                        totalCount = totalCount
                    )
                }
            }
            processedCount += 1
        }

        PortableImportOutcome.Completed(
            insertedCount = insertedCount,
            idempotentCount = idempotentCount,
            conflictCount = conflictCount
        )
    }
}
