package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlot
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlotId
import io.github.yuninggu.evolune.core.model.SlotIdResult
import io.github.yuninggu.evolune.data.MedicationPlanEntity
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeParseException

fun MedicationPlanEntity.toDomainMedicationPlan(): MappingResult<DomainMedicationPlan> {
    val mappedRoute = when (val result = routeFromLegacyStorage(route)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedEster = when (val result = esterFromLegacyStorage(ester)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedScheduleType = when (val result = scheduleTypeFromLegacyStorage(scheduleType)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedSlots = when (val result = slotsFromLegacyStorage()) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedDays = when (val result = daysOfWeek.toDomainDaysOfWeek()) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedExtras = when (val result = extras.toDomainExtras()) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedCreatedAt = try {
        Instant.ofEpochMilli(createdAt)
    } catch (_: DateTimeException) {
        return MappingResult.Failure(
            MappingError.InvalidCreatedAt(CreatedAtInput.EpochMillis(createdAt))
        )
    }

    return try {
        MappingResult.Success(
            DomainMedicationPlan(
                id = id,
                name = name,
                route = mappedRoute,
                ester = mappedEster,
                doseMG = doseMG,
                scheduleType = mappedScheduleType,
                slots = mappedSlots,
                daysOfWeek = mappedDays,
                intervalDays = intervalDays,
                isEnabled = isEnabled,
                extras = mappedExtras,
                createdAt = mappedCreatedAt
            )
        )
    } catch (_: IllegalArgumentException) {
        MappingResult.Failure(MappingError.InvalidPlanInvariant(intervalDays))
    }
}

private fun MedicationPlanEntity.slotsFromLegacyStorage(): MappingResult<List<ScheduledDoseSlot>> {
    val slots = mutableListOf<ScheduledDoseSlot>()
    timeOfDay.forEachIndexed { position, value ->
        val localTime = try {
            LocalTime.parse(value)
        } catch (_: DateTimeParseException) {
            return MappingResult.Failure(MappingError.InvalidTimeOfDay(value))
        }
        if (localTime.second != 0 || localTime.nano != 0) {
            return MappingResult.Failure(MappingError.InvalidTimeOfDay(value))
        }
        val slotId = when (val result = ScheduledDoseSlotId.generate(id, position, localTime)) {
            is SlotIdResult.Success -> result.id
            is SlotIdResult.Failure -> {
                return MappingResult.Failure(
                    MappingError.InvalidSlot(position, result.error)
                )
            }
        }
        val slot = try {
            ScheduledDoseSlot(
                id = slotId,
                planId = id,
                localTime = localTime,
                position = position
            )
        } catch (_: IllegalArgumentException) {
            return MappingResult.Failure(
                MappingError.InvalidPlanInvariant(intervalDays)
            )
        }
        slots += slot
    }
    return MappingResult.Success(slots)
}

private fun Set<Int>.toDomainDaysOfWeek(): MappingResult<Set<DayOfWeek>> {
    val days = linkedSetOf<DayOfWeek>()
    for (value in this) {
        val day = when (value) {
            1 -> DayOfWeek.MONDAY
            2 -> DayOfWeek.TUESDAY
            3 -> DayOfWeek.WEDNESDAY
            4 -> DayOfWeek.THURSDAY
            5 -> DayOfWeek.FRIDAY
            6 -> DayOfWeek.SATURDAY
            7 -> DayOfWeek.SUNDAY
            else -> return MappingResult.Failure(MappingError.InvalidDayOfWeek(value))
        }
        days += day
    }
    return MappingResult.Success(days)
}
