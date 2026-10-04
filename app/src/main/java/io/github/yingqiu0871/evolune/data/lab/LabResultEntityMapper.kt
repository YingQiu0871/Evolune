package io.github.yingqiu0871.evolune.data.lab

import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import java.time.Instant
import java.util.UUID

/** Why a lab result cannot cross the persistence boundary. */
sealed interface LabMappingError {
    data class InvalidId(val value: String) : LabMappingError
    data class InvalidMeasuredAt(val value: Instant) : LabMappingError
    data class InvalidValue(val value: Double) : LabMappingError
    data class InvalidUnit(val value: String) : LabMappingError
    data class InvalidRevision(val value: Long) : LabMappingError
}

sealed interface LabMappingResult<out T> {
    data class Success<T>(val value: T) : LabMappingResult<T>
    data class Failure(val error: LabMappingError) : LabMappingResult<Nothing>
}

/**
 * Domain → row. Rejects non-finite or non-positive values and instants that are not exactly
 * representable in epoch milliseconds, so a round trip is lossless.
 */
fun LabResult.toEntity(): LabMappingResult<LabResultEntity> {
    if (!value.isFinite() || value <= 0.0) {
        return LabMappingResult.Failure(LabMappingError.InvalidValue(value))
    }
    val millis = try {
        measuredAt.toEpochMilli()
    } catch (_: ArithmeticException) {
        return LabMappingResult.Failure(LabMappingError.InvalidMeasuredAt(measuredAt))
    }
    if (Instant.ofEpochMilli(millis) != measuredAt) {
        return LabMappingResult.Failure(LabMappingError.InvalidMeasuredAt(measuredAt))
    }
    return LabMappingResult.Success(
        LabResultEntity(
            id = id.toString(),
            measuredAtEpochMillis = millis,
            value = value,
            unit = unit.code,
            revision = revision
        )
    )
}

/** Row → domain. Any invalid persisted field is reported rather than repaired. */
fun LabResultEntity.toDomain(): LabMappingResult<LabResult> {
    val uuid = try {
        UUID.fromString(id).takeIf { it.toString() == id }
    } catch (_: IllegalArgumentException) {
        null
    } ?: return LabMappingResult.Failure(LabMappingError.InvalidId(id))
    val labUnit = LabUnit.fromCode(unit)
        ?: return LabMappingResult.Failure(LabMappingError.InvalidUnit(unit))
    if (!value.isFinite() || value <= 0.0) {
        return LabMappingResult.Failure(LabMappingError.InvalidValue(value))
    }
    if (revision < 1) {
        return LabMappingResult.Failure(LabMappingError.InvalidRevision(revision))
    }
    return LabMappingResult.Success(
        LabResult(
            id = uuid,
            measuredAt = Instant.ofEpochMilli(measuredAtEpochMillis),
            value = value,
            unit = labUnit,
            revision = revision
        )
    )
}
