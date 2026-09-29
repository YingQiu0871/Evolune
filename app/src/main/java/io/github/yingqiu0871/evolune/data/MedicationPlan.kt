package io.github.yingqiu0871.evolune.data

import io.github.yingqiu0871.evolune.pk.DoseEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * 用药方案
 * @param id 唯一标识符
 * @param name 方案名称
 * @param route 给药途径
 * @param ester 药物类型
 * @param doseMG 剂量（mg）
 * @param scheduleType 给药周期类型
 * @param timeOfDay 给药时间（一天中的时刻）
 * @param daysOfWeek 一周中的哪几天（仅用于WEEKLY类型）
 * @param intervalDays 间隔天数（仅用于CUSTOM类型）
 * @param isEnabled 是否启用
 * @param extras 额外参数（如舌下θ、贴片释放速率等）
 * @param createdAt 创建时间戳
 */
data class MedicationPlan(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val route: Route,
    val ester: Ester,
    val doseMG: Double,
    val scheduleType: ScheduleType,
    val timeOfDay: List<LocalTime>, // 支持多个时间点（如每天8:00和23:30）
    val daysOfWeek: Set<DayOfWeek> = emptySet(), // 一周中的哪几天
    val intervalDays: Int = 1, // 间隔天数（用于CUSTOM类型）
    val isEnabled: Boolean = true,
    val extras: Map<DoseEvent.ExtraKey, Double> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 给药周期类型
     */
    enum class ScheduleType {
        DAILY,      // 每天
        WEEKLY,     // 每周特定几天
        CUSTOM      // 自定义间隔天数
    }
}

/**
 * Ester扩展属性：显示名称
 */
val Ester.displayName: String
    get() = when (this) {
        Ester.E2 -> "雌二醇"
        Ester.EB -> "苯甲酸雌二醇"
        Ester.EV -> "戊酸雌二醇"
        Ester.EC -> "环戊丙酸雌二醇"
        Ester.EN -> "庚酸雌二醇"
    }
