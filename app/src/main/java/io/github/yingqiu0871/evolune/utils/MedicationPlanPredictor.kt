package io.github.yingqiu0871.evolune.utils

import io.github.yingqiu0871.evolune.core.adapter.toPkExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.data.MedicationPlan as LegacyMedicationPlan
import io.github.yingqiu0871.evolune.pk.DoseEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * 用药方案预测工具
 * 根据用药方案生成未来的虚拟DoseEvent列表
 */
object MedicationPlanPredictor {

    /**
     * 实际用药记录与计划用药冲突的时间窗口（小时）
     * 实际用药后此窗口内的计划用药将被过滤，避免短时间内重复计算
     */
    const val PLAN_CONFLICT_WINDOW_H = 1.0

    fun generateFutureEvents(
        plan: LegacyMedicationPlan,
        fromDateTime: LocalDateTime = LocalDateTime.now(),
        daysAhead: Int = 15
    ): List<DoseEvent> = generateFutureEvents(
        plan = PredictionPlan(
            isEnabled = plan.isEnabled,
            route = plan.route,
            ester = plan.ester,
            doseMG = plan.doseMG,
            scheduleType = plan.scheduleType.toDomainScheduleType(),
            times = plan.timeOfDay,
            daysOfWeek = plan.daysOfWeek,
            intervalDays = plan.intervalDays,
            extras = plan.extras
        ),
        fromDateTime = fromDateTime,
        daysAhead = daysAhead
    )

    fun generateFutureEvents(
        plan: DomainMedicationPlan,
        fromDateTime: LocalDateTime = LocalDateTime.now(),
        daysAhead: Int = 15
    ): List<DoseEvent> = generateFutureEvents(
        plan = PredictionPlan(
            isEnabled = plan.isEnabled,
            route = plan.route,
            ester = plan.ester,
            doseMG = plan.doseMG,
            scheduleType = plan.scheduleType,
            times = plan.slots.map { it.localTime },
            daysOfWeek = plan.daysOfWeek,
            intervalDays = plan.intervalDays,
            extras = plan.extras.mapKeys { (key, _) -> key.toPkExtraKey() }
        ),
        fromDateTime = fromDateTime,
        daysAhead = daysAhead
    )

    private fun generateFutureEvents(
        plan: PredictionPlan,
        fromDateTime: LocalDateTime,
        daysAhead: Int
    ): List<DoseEvent> {
        if (!plan.isEnabled) {
            return emptyList()
        }
        val events = mutableListOf<DoseEvent>()
        val today = fromDateTime.toLocalDate()
        when (plan.scheduleType) {
            ScheduleType.DAILY -> {
                for (dayOffset in 0 until daysAhead) {
                    val date = today.plusDays(dayOffset.toLong())
                    plan.times.forEach { time ->
                        addIfFuture(events, plan, LocalDateTime.of(date, time), fromDateTime)
                    }
                }
            }
            ScheduleType.WEEKLY -> {
                for (dayOffset in 0 until daysAhead) {
                    val date = today.plusDays(dayOffset.toLong())
                    if (date.dayOfWeek in plan.daysOfWeek) {
                        plan.times.forEach { time ->
                            addIfFuture(events, plan, LocalDateTime.of(date, time), fromDateTime)
                        }
                    }
                }
            }
            ScheduleType.CUSTOM -> {
                var dayOffset = 0
                while (dayOffset < daysAhead) {
                    val date = today.plusDays(dayOffset.toLong())
                    plan.times.forEach { time ->
                        addIfFuture(events, plan, LocalDateTime.of(date, time), fromDateTime)
                    }
                    dayOffset += plan.intervalDays
                }
            }
        }
        return events.sortedBy { it.timeH }
    }

    private fun addIfFuture(
        events: MutableList<DoseEvent>,
        plan: PredictionPlan,
        dateTime: LocalDateTime,
        fromDateTime: LocalDateTime
    ) {
        if (dateTime.isAfter(fromDateTime)) {
            events += DoseEvent(
                id = UUID.randomUUID(),
                route = plan.route,
                timeH = dateTime.atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() / 3_600_000.0,
                doseMG = plan.doseMG,
                ester = plan.ester,
                extras = plan.extras
            )
        }
    }

    fun generateFutureEventsForPlans(
        plans: List<LegacyMedicationPlan>,
        fromDateTime: LocalDateTime = LocalDateTime.now(),
        daysAhead: Int = 15
    ): List<DoseEvent> = plans
        .filter { it.isEnabled }
        .flatMap { generateFutureEvents(it, fromDateTime, daysAhead) }
        .sortedBy { it.timeH }

    fun generateFutureEventsForDomainPlans(
        plans: List<DomainMedicationPlan>,
        fromDateTime: LocalDateTime = LocalDateTime.now(),
        daysAhead: Int = 15
    ): List<DoseEvent> = plans
        .filter { it.isEnabled }
        .flatMap { generateFutureEvents(it, fromDateTime, daysAhead) }
        .sortedBy { it.timeH }

    fun filterConflictingPredictions(
        predictedEvents: List<DoseEvent>,
        actualEvents: List<DoseEvent>,
        conflictWindowH: Double = PLAN_CONFLICT_WINDOW_H
    ): List<DoseEvent> {
        if (actualEvents.isEmpty() || predictedEvents.isEmpty()) return predictedEvents

        return predictedEvents.filter { predicted ->
            actualEvents.none { actual ->
                actual.route == predicted.route &&
                    actual.ester == predicted.ester &&
                    predicted.timeH > actual.timeH &&
                    predicted.timeH <= actual.timeH + conflictWindowH
            }
        }
    }

    private data class PredictionPlan(
        val isEnabled: Boolean,
        val route: Route,
        val ester: Ester,
        val doseMG: Double,
        val scheduleType: ScheduleType,
        val times: List<LocalTime>,
        val daysOfWeek: Set<DayOfWeek>,
        val intervalDays: Int,
        val extras: Map<DoseEvent.ExtraKey, Double>
    )
}

private fun LegacyMedicationPlan.ScheduleType.toDomainScheduleType(): ScheduleType = when (this) {
    LegacyMedicationPlan.ScheduleType.DAILY -> ScheduleType.DAILY
    LegacyMedicationPlan.ScheduleType.WEEKLY -> ScheduleType.WEEKLY
    LegacyMedicationPlan.ScheduleType.CUSTOM -> ScheduleType.CUSTOM
}
