package io.github.yuninggu.hrttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.yuninggu.hrttracker.pk.DoseEvent
import io.github.yuninggu.hrttracker.pk.Ester
import io.github.yuninggu.hrttracker.pk.Route
import java.time.DayOfWeek
import java.time.LocalTime
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
) {
    /**
     * 转换为领域模型
     */
    fun toMedicationPlan(): MedicationPlan {
        val extraMap = extras.mapKeys { (key, _) ->
            DoseEvent.ExtraKey.valueOf(key)
        }

        val times = timeOfDay.map { LocalTime.parse(it) }
        val days = daysOfWeek.map { DayOfWeek.of(it) }.toSet()

        return MedicationPlan(
            id = id,
            name = name,
            route = Route.valueOf(route),
            ester = Ester.valueOf(ester),
            doseMG = doseMG,
            scheduleType = MedicationPlan.ScheduleType.valueOf(scheduleType),
            timeOfDay = times,
            daysOfWeek = days,
            intervalDays = intervalDays,
            isEnabled = isEnabled,
            extras = extraMap,
            createdAt = createdAt
        )
    }

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromMedicationPlan(plan: MedicationPlan): MedicationPlanEntity {
            val extraMap = plan.extras.mapKeys { (key, _) ->
                key.name
            }

            val times = plan.timeOfDay.map { it.toString() }
            val days = plan.daysOfWeek.map { it.value }.toSet()

            return MedicationPlanEntity(
                id = plan.id,
                name = plan.name,
                route = plan.route.name,
                ester = plan.ester.name,
                doseMG = plan.doseMG,
                scheduleType = plan.scheduleType.name,
                timeOfDay = times,
                daysOfWeek = days,
                intervalDays = plan.intervalDays,
                isEnabled = plan.isEnabled,
                extras = extraMap,
                createdAt = plan.createdAt
            )
        }
    }
}
