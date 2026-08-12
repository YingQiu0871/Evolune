package io.github.yuninggu.evolune.data.migration

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class LegacyMigrationException(
    val tableName: String,
    rowId: String?,
    val error: LegacyMigrationError? = null,
    val operation: String,
    cause: Throwable? = null
) : RuntimeException(
    buildLegacyMigrationMessage(tableName, rowId, error, operation),
    cause
) {
    val rowFingerprint: String? = rowId?.migrationFingerprint()
}

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
        append(" row fingerprint ")
        append(rowId.migrationFingerprint())
    }
    if (error != null) {
        append(": ")
        append(error.category())
    }
}

private fun LegacyMigrationError.category(): String = when (this) {
    is LegacyMigrationError.InvalidPersistedValue ->
        "invalid persisted $field (${reason.name.lowercase()})"
    is LegacyMigrationError.InvalidEventTimeH -> "invalid event timeH"
    is LegacyMigrationError.InvalidTimeOfDayJson -> "invalid timeOfDay JSON"
    is LegacyMigrationError.TimeOfDayElementNotString -> "non-string timeOfDay element"
    is LegacyMigrationError.InvalidLocalTime -> "invalid local time"
    is LegacyMigrationError.NonMinuteLocalTime -> "non-minute local time"
    is LegacyMigrationError.SlotIdGenerationFailed -> "slot ID generation failure"
    is LegacyMigrationError.InvalidTimeHStorageClass -> "invalid timeH storage class"
}

private fun String.migrationFingerprint(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .take(8)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
