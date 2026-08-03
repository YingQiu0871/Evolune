package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.model.SlotIdError
import io.github.yuninggu.evolune.core.time.LegacyTimeError
import java.time.Instant

sealed interface MappingResult<out T> {
    data class Success<T>(val value: T) : MappingResult<T>
    data class Failure(val error: MappingError) : MappingResult<Nothing>
}

sealed interface MappingError {
    data class InvalidTimeH(
        val value: Double,
        val cause: LegacyTimeError
    ) : MappingError

    data class InvalidRoute(val value: String) : MappingError
    data class InvalidEster(val value: String) : MappingError
    data class InvalidExtraKey(val value: String) : MappingError
    data class InvalidScheduleType(val value: String) : MappingError
    data class InvalidTimeOfDay(val value: String) : MappingError
    data class InvalidDayOfWeek(val value: Int) : MappingError
    data class InvalidCreatedAt(val input: CreatedAtInput) : MappingError
    data class InvalidSlot(
        val position: Int,
        val cause: SlotIdError
    ) : MappingError

    data class InvalidDoseEventInvariant(val revision: Long) : MappingError
    data class InvalidPlanInvariant(val intervalDays: Int) : MappingError
}

sealed interface CreatedAtInput {
    data class EpochMillis(val value: Long) : CreatedAtInput
    data class InstantValue(val value: Instant) : CreatedAtInput
}

fun instantToEpochMillisForPersistence(instant: Instant): MappingResult<Long> = try {
    MappingResult.Success(instant.toEpochMilli())
} catch (_: ArithmeticException) {
    MappingResult.Failure(
        MappingError.InvalidCreatedAt(CreatedAtInput.InstantValue(instant))
    )
}
