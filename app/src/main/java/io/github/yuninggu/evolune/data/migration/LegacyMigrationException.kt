package io.github.yuninggu.evolune.data.migration

internal class LegacyMigrationException(
    val tableName: String,
    val rowId: String?,
    val error: LegacyMigrationError? = null,
    val operation: String,
    cause: Throwable? = null
) : RuntimeException(
    buildLegacyMigrationMessage(tableName, rowId, error, operation),
    cause
)

private fun buildLegacyMigrationMessage(
    tableName: String,
    rowId: String?,
    error: LegacyMigrationError?,
    operation: String
): String = buildString {
    append("Room migration ")
    append(operation)
    append(" failed for ")
    append(tableName)
    if (rowId != null) {
        append(" row ")
        append(rowId)
    }
    if (error != null) {
        append(": ")
        append(error.category())
    }
}

private fun LegacyMigrationError.category(): String = when (this) {
    is LegacyMigrationError.InvalidEventTimeH -> "invalid event timeH"
    is LegacyMigrationError.InvalidTimeOfDayJson -> "invalid timeOfDay JSON"
    is LegacyMigrationError.TimeOfDayElementNotString -> "non-string timeOfDay element"
    is LegacyMigrationError.InvalidLocalTime -> "invalid local time"
    is LegacyMigrationError.NonMinuteLocalTime -> "non-minute local time"
    is LegacyMigrationError.SlotIdGenerationFailed -> "slot ID generation failure"
    is LegacyMigrationError.InvalidTimeHStorageClass -> "invalid timeH storage class"
}
