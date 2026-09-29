package io.github.yingqiu0871.evolune.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 用药方案数据库实体
 */
@Entity(tableName = "medication_plans")
data class MedicationPlanEntity(
    @PrimaryKey
    val id: UUID,
    val name: String,
    val route: String,
    val ester: String,
    val doseMG: Double,
    val scheduleType: String,
    val timeOfDay: List<String>, // 存储为"HH:mm"格式的字符串列表
    val daysOfWeek: Set<Int>, // 存储为整数集合（1-7）
    val intervalDays: Int,
    val isEnabled: Boolean,
    val extras: Map<String, Double>,
    val createdAt: Long
)
