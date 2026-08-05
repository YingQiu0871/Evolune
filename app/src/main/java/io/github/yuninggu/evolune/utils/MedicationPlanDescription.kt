package io.github.yuninggu.evolune.utils

import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.pk.AntiAndrogen
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.pk.displayName
import java.time.DayOfWeek

fun MedicationPlan.description(): String {
    val routeText = when (route) {
        Route.INJECTION -> "注射"
        Route.ORAL -> "口服"
        Route.SUBLINGUAL -> "舌下"
        Route.GEL -> "凝胶"
        Route.PATCH_APPLY -> "贴片"
        Route.PATCH_REMOVE -> "移除贴片"
        Route.ANTIANDROGEN -> "抗雄口服"
    }
    val scheduleText = when (scheduleType) {
        ScheduleType.DAILY -> "每天"
        ScheduleType.WEEKLY -> {
            val days = daysOfWeek.sortedBy { it.value }
                .joinToString("、") { it.displayName() }
            "每周$days"
        }
        ScheduleType.CUSTOM -> "每${intervalDays}天"
    }
    val timeText = slots.joinToString("、") { it.localTime.toString() }
    val medicationText = if (route == Route.ANTIANDROGEN) {
        when (extras[ExtraKey.ANTI_ANDROGEN_TYPE]) {
            1.0 -> AntiAndrogen.MPA
            2.0 -> AntiAndrogen.BICALUTAMIDE
            3.0 -> AntiAndrogen.SPIRONOLACTONE
            else -> AntiAndrogen.CPA
        }.displayName
    } else {
        ester.displayName()
    }
    return "$scheduleText $timeText $routeText ${doseMG}mg $medicationText"
}

private fun Ester.displayName(): String = when (this) {
    Ester.E2 -> "雌二醇"
    Ester.EB -> "苯甲酸雌二醇"
    Ester.EV -> "戊酸雌二醇"
    Ester.EC -> "环戊丙酸雌二醇"
    Ester.EN -> "庚酸雌二醇"
}

private fun DayOfWeek.displayName(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
