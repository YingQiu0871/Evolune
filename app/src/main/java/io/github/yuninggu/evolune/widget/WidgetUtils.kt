package io.github.yuninggu.evolune.widget

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.pk.AntiAndrogen
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.pk.displayName as antiAndrogenDisplayName
import io.github.yuninggu.evolune.reminder.matchesPlanDose
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * 已计划用药事件的状态信息
 */
data class ScheduledDoseInfo(
    val plan: MedicationPlan,
    val scheduledTime: LocalDateTime,
    val isTaken: Boolean,
    val isOverdue: Boolean
)

/**
 * Widget 共用工具函数
 */
object WidgetUtils {

    /** 计划用药的"已用药"判断时间窗口（±1小时） */
    const val TAKEN_WINDOW_HOURS = 1.0

    /**
     * 非 Composable 版本的给药途径显示名称
     */
    fun routeDisplayName(route: Route): String = when (route) {
        Route.INJECTION -> "注射"
        Route.ORAL -> "口服"
        Route.SUBLINGUAL -> "舌下"
        Route.GEL -> "凝胶"
        Route.PATCH_APPLY -> "贴片"
        Route.PATCH_REMOVE -> "移除贴"
        Route.ANTIANDROGEN -> "抗雄"
    }

    /**
     * 获取用药方案的药物显示名称。
     * 抗雄药物返回抗雄类型名称，其他药物返回雌激素酯类名称。
     */
    fun MedicationPlan.drugDisplayName(): String {
        return if (route == Route.ANTIANDROGEN) {
            val aaType = extras[ExtraKey.ANTI_ANDROGEN_TYPE]?.toInt()?.let {
                AntiAndrogen.values().getOrElse(it) { AntiAndrogen.CPA }
            } ?: AntiAndrogen.CPA
            aaType.antiAndrogenDisplayName
        } else {
            ester.widgetDisplayName()
        }
    }

    /**
     * 将 LocalDateTime 转换为自 epoch 起的小时数
     */
    fun localDateTimeToHours(dateTime: LocalDateTime): Double =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 3600000.0

    /**
     * 为提醒微件查找最相关的计划用药信息。
     *
     * 逻辑：
     * - 若计划用药时间前后 ±[TAKEN_WINDOW_HOURS] 小时内存在相同途径和酯类的实际记录，视为已用药
     * - 已过期未用药时：继续显示该次用药，直到距下一次计划用药时间更近才切换
     * - 优先展示最近的待用药事件
     */
    fun findRelevantScheduledDose(
        enabledPlans: List<MedicationPlan>,
        recentActualEvents: List<DoseEvent>
    ): ScheduledDoseInfo? {
        val now = LocalDateTime.now()
        val nowMillis = System.currentTimeMillis()

        val relevantPerPlan = enabledPlans.mapNotNull { plan ->
            findRelevantForPlan(plan, now, nowMillis, recentActualEvents)
        }

        if (relevantPerPlan.isEmpty()) return null

        val overdueUntaken = relevantPerPlan.filter { it.isOverdue && !it.isTaken }
        val upcomingUntaken = relevantPerPlan.filter { !it.isOverdue && !it.isTaken }

        return when {
            overdueUntaken.isEmpty() && upcomingUntaken.isEmpty() -> {
                // 全部已用药，展示最近的下一次计划
                relevantPerPlan
                    .filter { !it.isOverdue }
                    .minByOrNull { scheduledMillis(it.scheduledTime) - nowMillis }
                    ?: relevantPerPlan.maxByOrNull { localDateTimeToHours(it.scheduledTime) }
            }
            overdueUntaken.isEmpty() -> {
                upcomingUntaken.minByOrNull { scheduledMillis(it.scheduledTime) - nowMillis }
            }
            upcomingUntaken.isEmpty() -> {
                overdueUntaken.maxByOrNull { localDateTimeToHours(it.scheduledTime) }
            }
            else -> {
                val closestOverdue =
                    overdueUntaken.maxByOrNull { localDateTimeToHours(it.scheduledTime) }!!
                val closestUpcoming =
                    upcomingUntaken.minByOrNull { localDateTimeToHours(it.scheduledTime) }!!
                val timeSince = nowMillis - scheduledMillis(closestOverdue.scheduledTime)
                val timeToNext = scheduledMillis(closestUpcoming.scheduledTime) - nowMillis
                // 过期事件仍更近时继续显示它
                if (timeSince <= timeToNext) closestOverdue else closestUpcoming
            }
        }
    }

    private fun findRelevantForPlan(
        plan: MedicationPlan,
        now: LocalDateTime,
        nowMillis: Long,
        recentActualEvents: List<DoseEvent>
    ): ScheduledDoseInfo? {
        val scheduledTimes = generateScheduledTimes(plan, now.minusHours(48), now.plusDays(7))
        if (scheduledTimes.isEmpty()) return null

        val lastPast = scheduledTimes.filter { it.isBefore(now) }.maxOrNull()
        val nextFuture = scheduledTimes.filter { !it.isBefore(now) }.minOrNull()

        /** Standard ±[TAKEN_WINDOW_HOURS] check for a given scheduled time. */
        fun isTakenAt(time: LocalDateTime): Boolean {
            val scheduledMillis = scheduledMillis(time)
            return recentActualEvents.any { actual ->
                actual.matchesPlanDose(plan) &&
                    abs(actual.occurredAt.toEpochMilli() - scheduledMillis) <=
                    DOSE_WINDOW_MILLIS
            }
        }

        /**
         * Extended check for whether the past scheduled dose has been fulfilled.
         * A catch-up dose taken at any point during the overdue display window
         * (from [fromH] − [TAKEN_WINDOW_HOURS] up to, but not overlapping, the next dose's own
         * window) is counted as satisfying the missed scheduled dose.
         *
         * The [TAKEN_WINDOW_HOURS] subtraction on [fromH] mirrors the ±window logic of
         * [isTakenAt], so a dose recorded slightly before the scheduled time is still counted.
         */
        fun isTakenBetween(fromMillis: Long, toExclusiveMillis: Long): Boolean {
            return recentActualEvents.any { actual ->
                actual.matchesPlanDose(plan) &&
                    actual.occurredAt.toEpochMilli() >= fromMillis - DOSE_WINDOW_MILLIS &&
                    actual.occurredAt.toEpochMilli() < toExclusiveMillis
            }
        }

        if (lastPast == null) {
            return nextFuture?.let { ScheduledDoseInfo(plan, it, isTakenAt(it), false) }
        }

        val lastPastMillis = scheduledMillis(lastPast)
        val nextFutureMillis = nextFuture?.let(::scheduledMillis)

        // Consider the past dose taken if any matching dose was recorded from the scheduled
        // time all the way up to (but not within the window of) the next scheduled dose.
        // This allows catch-up doses to clear the "漏服" state immediately.
        val lastPastTaken = isTakenBetween(
            fromMillis = lastPastMillis,
            toExclusiveMillis = nextFutureMillis?.minus(DOSE_WINDOW_MILLIS)
                ?: (nowMillis + DOSE_WINDOW_MILLIS)
        )

        return if (!lastPastTaken && nextFuture != null) {
            val timeSince = nowMillis - lastPastMillis
            val timeToNext = nextFutureMillis!! - nowMillis
            if (timeSince <= timeToNext) {
                ScheduledDoseInfo(plan, lastPast, false, isOverdue = true)
            } else {
                ScheduledDoseInfo(plan, nextFuture, isTakenAt(nextFuture), false)
            }
        } else if (!lastPastTaken) {
            ScheduledDoseInfo(plan, lastPast, false, isOverdue = true)
        } else {
            nextFuture?.let { ScheduledDoseInfo(plan, it, isTakenAt(it), false) }
        }
    }

    /**
     * 在给定时间窗口内生成某方案的全部计划时间点
     */
    private fun generateScheduledTimes(
        plan: MedicationPlan,
        fromDateTime: LocalDateTime,
        toDateTime: LocalDateTime
    ): List<LocalDateTime> {
        val result = mutableListOf<LocalDateTime>()
        val today = LocalDate.now()
        val fromDate = fromDateTime.toLocalDate()
        val toDate = toDateTime.toLocalDate()

        fun addIfInRange(dt: LocalDateTime) {
            if (!dt.isBefore(fromDateTime) && !dt.isAfter(toDateTime)) result.add(dt)
        }

        when (plan.scheduleType) {
            ScheduleType.DAILY -> {
                var date = fromDate
                while (!date.isAfter(toDate)) {
                    plan.slots.forEach { addIfInRange(LocalDateTime.of(date, it.localTime)) }
                    date = date.plusDays(1)
                }
            }
            ScheduleType.WEEKLY -> {
                var date = fromDate
                while (!date.isAfter(toDate)) {
                    if (plan.daysOfWeek.contains(date.dayOfWeek)) {
                        plan.slots.forEach { addIfInRange(LocalDateTime.of(date, it.localTime)) }
                    }
                    date = date.plusDays(1)
                }
            }
            ScheduleType.CUSTOM -> {
                // 从今天向前和向后按间隔枚举
                var offset = 0L
                var date = today
                while (!date.isAfter(toDate)) {
                    plan.slots.forEach { addIfInRange(LocalDateTime.of(date, it.localTime)) }
                    offset += plan.intervalDays
                    date = today.plusDays(offset)
                }
                offset = plan.intervalDays.toLong()
                date = today.minusDays(offset)
                while (!date.isBefore(fromDate)) {
                    plan.slots.forEach { addIfInRange(LocalDateTime.of(date, it.localTime)) }
                    offset += plan.intervalDays
                    date = today.minusDays(offset)
                }
            }
        }

        return result.sorted()
    }

    /**
     * 将计划时间格式化为友好字符串：今天/明天/昨天 HH:mm，其他显示 M/d HH:mm
     */
    fun formatScheduledTime(dateTime: LocalDateTime): String {
        val today = LocalDate.now()
        val date = dateTime.toLocalDate()
        val timeStr = "%02d:%02d".format(dateTime.hour, dateTime.minute)
        return when {
            date == today -> "今天 $timeStr"
            date == today.plusDays(1) -> "明天 $timeStr"
            date == today.minusDays(1) -> "昨天 $timeStr"
            else -> "${date.monthValue}/${date.dayOfMonth} $timeStr"
        }
    }

    private fun scheduledMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun Ester.widgetDisplayName(): String = when (this) {
        Ester.E2 -> "雌二醇"
        Ester.EB -> "苯甲酸雌二醇"
        Ester.EV -> "戊酸雌二醇"
        Ester.EC -> "环戊丙酸雌二醇"
        Ester.EN -> "庚酸雌二醇"
    }

    private const val DOSE_WINDOW_MILLIS = 3_600_000L
}
