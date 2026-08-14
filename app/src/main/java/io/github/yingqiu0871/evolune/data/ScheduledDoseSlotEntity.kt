package io.github.yingqiu0871.evolune.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "scheduled_dose_slots",
    foreignKeys = [
        ForeignKey(
            entity = MedicationPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            name = "index_scheduled_dose_slots_planId",
            value = ["planId"]
        ),
        Index(
            name = "index_scheduled_dose_slots_planId_position",
            value = ["planId", "position"],
            unique = true
        )
    ]
)
data class ScheduledDoseSlotEntity(
    @PrimaryKey val id: UUID,
    val planId: UUID,
    val localTime: String,
    val position: Int
)
