package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1Codec
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DecodeResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DocumentError
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DoseEventAdapter
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1ImportMappingResult
import kotlinx.coroutines.CancellationException

class MahiroJsonV1ImportService(
    private val repository: DoseEventRepository,
    private val codec: MahiroV1Codec = MahiroV1Codec(),
    private val adapter: MahiroV1DoseEventAdapter = MahiroV1DoseEventAdapter()
) {
    suspend fun import(jsonContent: String): MahiroJsonV1ImportResult {
        val decoded = when (val result = codec.decode(jsonContent)) {
            is MahiroV1DecodeResult.Success -> result
            is MahiroV1DecodeResult.Failure -> return MahiroJsonV1ImportResult.Failure(
                summary = MahiroJsonV1ImportSummary.empty(),
                error = MahiroJsonV1ImportError.Document(result.error)
            )
        }
        var insertedCount = 0
        var idempotentCount = 0
        var conflictCount = 0
        var invalidCount = 0
        var eventIndex = 0
        val diagnosticsByIndex = decoded.diagnostics.associateBy { it.index }
        val sourceEntryCount = decoded.document.events.size + decoded.diagnostics.size

        for (sourceIndex in 0 until sourceEntryCount) {
            if (sourceIndex in diagnosticsByIndex) {
                invalidCount += 1
                continue
            }
            val dto = decoded.document.events[eventIndex++]
            val event = when (val mapping = adapter.toDomain(dto)) {
                is MahiroV1ImportMappingResult.Success -> mapping.event
                is MahiroV1ImportMappingResult.Failure -> {
                    invalidCount += 1
                    continue
                }
            }
            val insertResult = try {
                repository.insert(event)
            } catch (error: CancellationException) {
                throw error
            } catch (_: RuntimeException) {
                return MahiroJsonV1ImportResult.Failure(
                    summary = MahiroJsonV1ImportSummary(
                        weight = decoded.document.weight,
                        insertedCount = insertedCount,
                        idempotentCount = idempotentCount,
                        conflictCount = conflictCount,
                        invalidCount = invalidCount,
                        failedCount = 1
                    ),
                    error = MahiroJsonV1ImportError.Storage(sourceIndex)
                )
            }
            when (insertResult) {
                InsertResult.Inserted -> insertedCount += 1
                InsertResult.Idempotent -> idempotentCount += 1
                InsertResult.Conflict -> conflictCount += 1
                InsertResult.Invalid -> invalidCount += 1
            }
        }

        return MahiroJsonV1ImportResult.Success(
            MahiroJsonV1ImportSummary(
                weight = decoded.document.weight,
                insertedCount = insertedCount,
                idempotentCount = idempotentCount,
                conflictCount = conflictCount,
                invalidCount = invalidCount,
                failedCount = 0
            )
        )
    }
}

data class MahiroJsonV1ImportSummary(
    val weight: Double?,
    val insertedCount: Int,
    val idempotentCount: Int,
    val conflictCount: Int,
    val invalidCount: Int,
    val failedCount: Int
) {
    val acceptedCount: Int = insertedCount + idempotentCount
    val processedCount: Int = acceptedCount + conflictCount + invalidCount + failedCount

    companion object {
        fun empty(): MahiroJsonV1ImportSummary = MahiroJsonV1ImportSummary(
            weight = null,
            insertedCount = 0,
            idempotentCount = 0,
            conflictCount = 0,
            invalidCount = 0,
            failedCount = 0
        )
    }
}

sealed interface MahiroJsonV1ImportResult {
    data class Success(val summary: MahiroJsonV1ImportSummary) : MahiroJsonV1ImportResult

    data class Failure(
        val summary: MahiroJsonV1ImportSummary,
        val error: MahiroJsonV1ImportError
    ) : MahiroJsonV1ImportResult
}

sealed interface MahiroJsonV1ImportError {
    data class Document(val error: MahiroV1DocumentError) : MahiroJsonV1ImportError
    data class Storage(val sourceIndex: Int) : MahiroJsonV1ImportError
}
