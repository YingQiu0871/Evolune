package io.github.yingqiu0871.evolune.external.mahiro.v1

import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeError
import io.github.yingqiu0871.evolune.core.time.LegacyTimeResult
import java.time.Instant
import java.util.UUID

/** Maps Mahiro v1 `labResults` entries to and from [LabResult] (PK 2.0 slice 2c). */
class MahiroV1LabResultAdapter(
    private val uuidSupplier: () -> UUID = UUID::randomUUID
) {
    fun toDomain(dto: MahiroV1LabResultDto): MahiroV1LabImportMappingResult {
        val unit = unitFromWire(dto.unit) ?: return MahiroV1LabImportMappingResult.Failure(
            MahiroV1LabMappingError.UnknownUnit(dto.unit)
        )
        if (!dto.concValue.isFinite() || dto.concValue <= 0.0) {
            return MahiroV1LabImportMappingResult.Failure(
                MahiroV1LabMappingError.InvalidValue(dto.concValue)
            )
        }
        val measuredAt = when (val result = LegacyTimeAdapter.timeHToInstant(dto.timeH)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> return MahiroV1LabImportMappingResult.Failure(
                MahiroV1LabMappingError.InvalidTimeH(dto.timeH, result.error)
            )
        }
        val id = dto.id?.let(::parseUuidOrNull) ?: uuidSupplier()

        return MahiroV1LabImportMappingResult.Success(
            LabResult(
                id = id,
                measuredAt = measuredAt,
                value = dto.concValue,
                unit = unit,
                revision = 1L
            )
        )
    }

    fun fromDomain(result: LabResult): MahiroV1LabExportMappingResult {
        val timeH = when (val time = LegacyTimeAdapter.instantToTimeH(result.measuredAt)) {
            is LegacyTimeResult.Success -> time.value
            is LegacyTimeResult.Failure -> return MahiroV1LabExportMappingResult.Failure(
                MahiroV1LabMappingError.UnrepresentableInstant(result.measuredAt, time.error)
            )
        }
        return MahiroV1LabExportMappingResult.Success(
            MahiroV1LabResultDto(
                id = result.id.toString(),
                timeH = timeH,
                concValue = result.value,
                unit = result.unit.mahiroCode
            )
        )
    }

    private fun parseUuidOrNull(rawId: String): UUID? = try {
        UUID.fromString(rawId)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Mahiro writes lower-case codes; "pg/mL"-style capitalisation is accepted on import. */
    private fun unitFromWire(value: String): LabUnit? {
        val normalized = value.trim().lowercase()
        return LabUnit.entries.firstOrNull { it.mahiroCode == normalized }
    }
}

sealed interface MahiroV1LabImportMappingResult {
    data class Success(val result: LabResult) : MahiroV1LabImportMappingResult
    data class Failure(val error: MahiroV1LabMappingError) : MahiroV1LabImportMappingResult
}

sealed interface MahiroV1LabExportMappingResult {
    data class Success(val result: MahiroV1LabResultDto) : MahiroV1LabExportMappingResult
    data class Failure(val error: MahiroV1LabMappingError) : MahiroV1LabExportMappingResult
}

sealed interface MahiroV1LabMappingError {
    data class UnknownUnit(val value: String) : MahiroV1LabMappingError
    data class InvalidValue(val value: Double) : MahiroV1LabMappingError
    data class InvalidTimeH(
        val value: Double,
        val error: LegacyTimeError
    ) : MahiroV1LabMappingError

    data class UnrepresentableInstant(
        val value: Instant,
        val error: LegacyTimeError
    ) : MahiroV1LabMappingError
}
