package io.github.yingqiu0871.evolune.data.lab

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Row of the separate lab database (`evolune_labs`); see [LabDatabase]. */
@Entity(tableName = "lab_results")
data class LabResultEntity(
    @PrimaryKey
    val id: String,
    val measuredAtEpochMillis: Long,
    val value: Double,
    val unit: String,
    val revision: Long
)
