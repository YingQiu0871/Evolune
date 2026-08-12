package io.github.yuninggu.evolune.data.migration

import io.github.yuninggu.evolune.core.model.SlotIdError
import io.github.yuninggu.evolune.core.time.LegacyTimeError
import java.time.LocalTime
import java.util.UUID

sealed interface LegacyMigrationResult<out T> {
    data class Success<T>(val value: T) : LegacyMigrationResult<T>
    data class Failure(val error: LegacyMigrationError) : LegacyMigrationResult<Nothing>
}

sealed interface LegacyMigrationError {
    data class InvalidPersistedValue(
        val field: String,
        val reason: PersistedValueFailure
    ) : LegacyMigrationError

    data class InvalidEventTimeH(
        val eventId: UUID,
        val rawTimeH: Double,
        val cause: LegacyTimeError
    ) : LegacyMigrationError

    data class InvalidTimeOfDayJson(
        val planId: UUID,
        val rawTimeOfDay: String,
        val reason: TimeOfDayJsonFailure
    ) : LegacyMigrationError

    data class TimeOfDayElementNotString(
        val planId: UUID,
        val position: Int,
        val rawValue: String,
        val elementKind: JsonElementKind
    ) : LegacyMigrationError

    data class InvalidLocalTime(
        val planId: UUID,
        val position: Int,
        val originalValue: String
    ) : LegacyMigrationError

    data class NonMinuteLocalTime(
        val planId: UUID,
        val position: Int,
        val originalValue: String,
        val parsedLocalTime: LocalTime
    ) : LegacyMigrationError

    data class SlotIdGenerationFailed(
        val planId: UUID,
        val position: Int,
        val originalValue: String,
        val cause: SlotIdError
    ) : LegacyMigrationError

    data class InvalidTimeHStorageClass(
        val storageClass: LegacySqliteStorageClass
    ) : LegacyMigrationError
}

enum class PersistedValueFailure {
    INVALID_STORAGE_CLASS,
    NONCANONICAL_ID,
    CONVERTER_REJECTED,
    MAPPER_REJECTED,
    NONCANONICAL_BOOLEAN
}

enum class TimeOfDayJsonFailure {
    MALFORMED,
    ROOT_NOT_ARRAY
}

enum class JsonElementKind {
    NULL,
    STRING,
    NUMBER,
    BOOLEAN,
    ARRAY,
    OBJECT
}

enum class LegacySqliteStorageClass {
    INTEGER,
    FLOAT,
    NULL,
    STRING,
    BLOB
}
