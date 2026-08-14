package io.github.yingqiu0871.evolune.data

import androidx.room.Embedded
import androidx.room.Relation

data class MedicationPlanAggregateEntity(
    @Embedded
    val plan: MedicationPlanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "planId"
    )
    val slots: List<ScheduledDoseSlotEntity>
)
