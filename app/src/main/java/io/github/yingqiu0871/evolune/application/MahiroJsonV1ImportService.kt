package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LabResultRepository
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1Codec
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DecodeResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DocumentError
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DoseEventAdapter
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1ImportMappingResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1LabImportMappingResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1LabResultAdapter
import io.github.yingqiu0871.evolune.export.PortableImportBounds
import kotlinx.coroutines.CancellationException

class MahiroJsonV1ImportService(
    private val repository: DoseEventRepository,
    private val codec: MahiroV1Codec = MahiroV1Codec(),
    private val adapter: MahiroV1DoseEventAdapter = MahiroV1DoseEventAdapter(),
    /** When null, `labResults` entries are ignored as before PK 2.0. */
    private val labRepository: LabResultRepository? = null,
    private val labAdapter: MahiroV1LabResultAdapter = MahiroV1LabResultAdapter(),
    private val maxInputBytes: Int = PortableImportBounds.MAX_INPUT_BYTES,
    private val maxEventCount: Int = PortableImportBounds.MAX_EVENT_COUNT
) {
    suspend fun import(jsonContent: String): MahiroJsonV1ImportResult {
        // Phase E §32 bounds: enforced before unbounded parse/write; legacy wire semantics untouched.
        if (jsonContent.toByteArray(Charsets.UTF_8).size > maxInputBytes) {
            return MahiroJsonV1ImportResult.Failure(
                summary = MahiroJsonV1ImportSummary.empty(),
                error = MahiroJsonV1ImportError.TooLarge
            )
        }
        val decoded = when (val result = codec.decode(jsonContent)) {
            is MahiroV1DecodeResult.Success -> result
            is MahiroV1DecodeResult.Failure -> return MahiroJsonV1ImportResult.Failure(
                summary = MahiroJsonV1ImportSummary.empty(),
                error = MahiroJsonV1ImportError.Document(result.error)
            )
        }
        val labEntryCount = if (labRepository == null) 0 else {
            decoded.document.labResults.size + decoded.labDiagnostics.size
        }
        if (decoded.document.events.size + decoded.diagnostics.size + labEntryCount > maxEventCount) {
            return MahiroJsonV1ImportResult.Failure(
                summary = MahiroJsonV1ImportSummary.empty(),
                error = MahiroJsonV1ImportError.TooLarge
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

        val eventSummary = MahiroJsonV1ImportSummary(
            weight = decoded.document.weight,
            insertedCount = insertedCount,
            idempotentCount = idempotentCount,
            conflictCount = conflictCount,
            invalidCount = invalidCount,
            failedCount = 0
        )
        val labs = labRepository ?: return MahiroJsonV1ImportResult.Success(eventSummary)
        return importLabs(labs, decoded, eventSummary)
    }

    private suspend fun importLabs(
        labs: LabResultRepository,
        decoded: MahiroV1DecodeResult.Success,
        eventSummary: MahiroJsonV1ImportSummary
    ): MahiroJsonV1ImportResult {
        var counts = MahiroJsonV1LabImportCounts()
        var labIndex = 0
        val diagnosticsByIndex = decoded.labDiagnostics.associateBy { it.index }
        val sourceEntryCount = decoded.document.labResults.size + decoded.labDiagnostics.size

        for (sourceIndex in 0 until sourceEntryCount) {
            if (sourceIndex in diagnosticsByIndex) {
                counts = counts.copy(invalidCount = counts.invalidCount + 1)
                continue
            }
            val dto = decoded.document.labResults[labIndex++]
            val lab = when (val mapping = labAdapter.toDomain(dto)) {
                is MahiroV1LabImportMappingResult.Success -> mapping.result
                is MahiroV1LabImportMappingResult.Failure -> {
                    counts = counts.copy(invalidCount = counts.invalidCount + 1)
                    continue
                }
            }
            val insertResult = try {
                labs.insert(lab)
            } catch (error: CancellationException) {
                throw error
            } catch (_: RuntimeException) {
                return MahiroJsonV1ImportResult.Failure(
                    summary = eventSummary.copy(labs = counts.copy(failedCount = 1)),
                    error = MahiroJsonV1ImportError.LabStorage(sourceIndex)
                )
            }
            counts = when (insertResult) {
                InsertResult.Inserted -> counts.copy(insertedCount = counts.insertedCount + 1)
                InsertResult.Idempotent -> counts.copy(idempotentCount = counts.idempotentCount + 1)
                InsertResult.Conflict -> counts.copy(conflictCount = counts.conflictCount + 1)
                InsertResult.Invalid -> counts.copy(invalidCount = counts.invalidCount + 1)
            }
        }
        return MahiroJsonV1ImportResult.Success(eventSummary.copy(labs = counts))
    }
}

/** Outcome counts for the `labResults` section, kept apart from dose-event counts. */
data class MahiroJsonV1LabImportCounts(
    val insertedCount: Int = 0,
    val idempotentCount: Int = 0,
    val conflictCount: Int = 0,
    val invalidCount: Int = 0,
    val failedCount: Int = 0
) {
    val acceptedCount: Int = insertedCount + idempotentCount
}

data class MahiroJsonV1ImportSummary(
    val weight: Double?,
    val insertedCount: Int,
    val idempotentCount: Int,
    val conflictCount: Int,
    val invalidCount: Int,
    val failedCount: Int,
    val labs: MahiroJsonV1LabImportCounts = MahiroJsonV1LabImportCounts()
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
    /** A lab result write failed; dose events before it are already stored. */
    data class LabStorage(val sourceIndex: Int) : MahiroJsonV1ImportError
    data object TooLarge : MahiroJsonV1ImportError
}
