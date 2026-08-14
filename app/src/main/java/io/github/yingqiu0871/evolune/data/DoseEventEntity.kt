package io.github.yingqiu0871.evolune.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.yingqiu0871.evolune.data.migration.LegacyMigrationResult
import io.github.yingqiu0871.evolune.data.migration.legacyTimeHToOccurredAtEpochMillis
import io.github.yingqiu0871.evolune.pk.DoseEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
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
) {
    /**
     * 转换为领域模型
     */
    fun toDoseEvent(): DoseEvent {
        val extraMap = extras.mapKeys { (key, _) ->
            DoseEvent.ExtraKey.valueOf(key)
        }
        
        return DoseEvent(
            id = id,
            route = Route.valueOf(route),
            timeH = timeH,
            doseMG = doseMG,
            ester = Ester.valueOf(ester),
            extras = extraMap
        )
    }

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromDoseEvent(event: DoseEvent): DoseEventEntity {
            val extraMap = event.extras.mapKeys { (key, _) ->
                key.name
            }
            val occurredAtEpochMillis = strictOccurredAtEpochMillis(event.id, event.timeH)

            return DoseEventEntity(
                id = event.id,
                route = event.route.name,
                timeH = event.timeH,
                doseMG = event.doseMG,
                ester = event.ester.name,
                extras = extraMap,
                occurredAtEpochMillis = occurredAtEpochMillis
            )
        }
    }
}

private fun strictOccurredAtEpochMillis(
    eventId: UUID,
    timeH: Double
): Long = when (val result = legacyTimeHToOccurredAtEpochMillis(eventId, timeH)) {
    is LegacyMigrationResult.Success -> result.value
    is LegacyMigrationResult.Failure -> throw IllegalArgumentException(
        "Invalid legacy timeH for dose event $eventId"
    )
}
