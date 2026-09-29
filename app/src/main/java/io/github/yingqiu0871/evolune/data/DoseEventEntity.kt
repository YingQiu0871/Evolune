package io.github.yingqiu0871.evolune.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.yingqiu0871.evolune.data.migration.LegacyMigrationResult
import io.github.yingqiu0871.evolune.data.migration.legacyTimeHToOccurredAtEpochMillis
import java.util.UUID

/**
 * 用药事件数据库实体
 */
@Entity(tableName = "dose_events")
data class DoseEventEntity(
    @PrimaryKey
    val id: UUID,
    val route: String,
    val timeH: Double,
    val doseMG: Double,
    val ester: String,
    val extras: Map<String, Double>,
    @ColumnInfo(defaultValue = "0")
    val occurredAtEpochMillis: Long = strictOccurredAtEpochMillis(id, timeH),
    val zoneId: String? = null,
    val localDate: String? = null,
    val slotId: UUID? = null,
    @ColumnInfo(defaultValue = "'LEGACY'")
    val source: String = "LEGACY",
    @ColumnInfo(defaultValue = "'RECORDED'")
    val status: String = "RECORDED",
    @ColumnInfo(defaultValue = "1")
    val revision: Long = 1L
)

private fun strictOccurredAtEpochMillis(
    eventId: UUID,
    timeH: Double
): Long = when (val result = legacyTimeHToOccurredAtEpochMillis(eventId, timeH)) {
    is LegacyMigrationResult.Success -> result.value
    is LegacyMigrationResult.Failure -> throw IllegalArgumentException(
        "Invalid legacy timeH for dose event $eventId"
    )
}
