package io.github.yuninggu.evolune.data.migration

import io.github.yuninggu.evolune.core.time.LegacyTimeAdapter
import io.github.yuninggu.evolune.core.time.LegacyTimeResult
import java.util.UUID

fun legacyTimeHToOccurredAtEpochMillis(
    eventId: UUID,
    rawTimeH: Double
): LegacyMigrationResult<Long> =
    when (val result = LegacyTimeAdapter.timeHToEpochMillis(rawTimeH)) {
        is LegacyTimeResult.Success -> LegacyMigrationResult.Success(result.value)
        is LegacyTimeResult.Failure -> LegacyMigrationResult.Failure(
            LegacyMigrationError.InvalidEventTimeH(
                eventId = eventId,
                rawTimeH = rawTimeH,
                cause = result.error
            )
        )
    }

fun validateLegacyTimeHStorageClass(
    storageClass: LegacySqliteStorageClass
): LegacyMigrationResult<LegacySqliteStorageClass> = when (storageClass) {
    LegacySqliteStorageClass.INTEGER,
    LegacySqliteStorageClass.FLOAT -> LegacyMigrationResult.Success(storageClass)
    LegacySqliteStorageClass.NULL,
    LegacySqliteStorageClass.STRING,
    LegacySqliteStorageClass.BLOB -> LegacyMigrationResult.Failure(
        LegacyMigrationError.InvalidTimeHStorageClass(storageClass)
    )
}
