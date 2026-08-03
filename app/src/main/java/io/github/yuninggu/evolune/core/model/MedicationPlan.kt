package io.github.yuninggu.evolune.core.model

import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

data class MedicationPlan(
    val id: UUID,
    val name: String,
    val route: Route,
    val ester: Ester,
    val doseMG: Double,
    val scheduleType: ScheduleType,
    val slots: List<ScheduledDoseSlot>,
    val daysOfWeek: Set<DayOfWeek>,
    val intervalDays: Int,
    val isEnabled: Boolean,
    val extras: Map<ExtraKey, Double>,
    val createdAt: Instant
) {
    init {
        require(intervalDays >= 1) { "intervalDays must be at least 1" }
        slots.forEachIndexed { index, slot ->
            require(slot.planId == id) { "slot planId must match plan id" }
            require(slot.position == index) { "slot position must match its list index" }
        }
    }
}
