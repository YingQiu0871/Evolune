package io.github.yingqiu0871.evolune.data.mapper

import io.github.yingqiu0871.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.SlotIdResult
import io.github.yingqiu0871.evolune.core.model.ScheduleType as DomainScheduleType
import io.github.yingqiu0871.evolune.data.MedicationPlanAggregateEntity
import io.github.yingqiu0871.evolune.data.MedicationPlanEntity
import io.github.yingqiu0871.evolune.data.ScheduledDoseSlotEntity
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

data class MedicationPlanPersistenceAggregate(
    val plan: MedicationPlanEntity,
    val slots: List<ScheduledDoseSlotEntity>
)

fun MedicationPlanAggregateEntity.toDomainMedicationPlan(): MappingResult<DomainMedicationPlan> {
    val mappedRoute = when (val result = routeFromLegacyStorage(plan.route)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedEster = when (val result = esterFromLegacyStorage(plan.ester)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedScheduleType = when (
        val result = scheduleTypeFromLegacyStorage(plan.scheduleType)
    ) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedDays = when (val result = plan.daysOfWeek.toDomainDaysOfWeek()) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val mappedExtras = when (val result = plan.extras.toDomainExtras()) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val storedSlots = when (val result = slots.toDomainSlots(plan.id)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    val legacyTimes = when (val result = plan.timeOfDay.toCanonicalLegacyTimes()) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    if (legacyTimes != storedSlots.map { canonicalLocalTime(it.localTime) }) {
        return MappingResult.Failure(MappingError.InconsistentPlanTimes(plan.id))
    }
    val mappedSlots = storedSlots.chronologicallyOrdered()

    return try {
        MappingResult.Success(
            DomainMedicationPlan(
                id = plan.id,
                name = plan.name,
                route = mappedRoute,
                ester = mappedEster,
                doseMG = plan.doseMG,
                scheduleType = mappedScheduleType,
                slots = mappedSlots,
                daysOfWeek = mappedDays,
                intervalDays = plan.intervalDays,
                isEnabled = plan.isEnabled,
                extras = mappedExtras,
                createdAt = Instant.ofEpochMilli(plan.createdAt)
            )
        )
    } catch (_: IllegalArgumentException) {
        MappingResult.Failure(MappingError.InvalidPlanInvariant(plan.intervalDays))
    }
}

fun DomainMedicationPlan.toPersistenceAggregate(): MappingResult<MedicationPlanPersistenceAggregate> {
    val createdAtEpochMillis = when (val result = instantToEpochMillisForPersistence(createdAt)) {
        is MappingResult.Success -> result.value
        is MappingResult.Failure -> return result
    }
    if (Instant.ofEpochMilli(createdAtEpochMillis) != createdAt) {
        return MappingResult.Failure(
            MappingError.InvalidCreatedAt(CreatedAtInput.InstantValue(createdAt))
        )
    }

    val orderedSlots = slots.chronologicallyOrdered()
    val slotEntities = mutableListOf<ScheduledDoseSlotEntity>()
    orderedSlots.forEachIndexed { index, slot ->
        if (slot.planId != id) {
            return MappingResult.Failure(MappingError.InvalidSlotPlan(index))
        }
        if (slot.position != index) {
            return MappingResult.Failure(MappingError.InvalidSlotPosition(slot.position))
        }
        slotEntities += ScheduledDoseSlotEntity(
            id = slot.id,
            planId = slot.planId,
            localTime = canonicalLocalTime(slot.localTime),
            position = slot.position
        )
    }

    val planEntity = MedicationPlanEntity(
        id = id,
        name = name,
        route = route.toLegacyStorageRoute(),
        ester = ester.toLegacyStorageEster(),
        doseMG = doseMG,
        scheduleType = scheduleType.toLegacyStorageScheduleType(),
        timeOfDay = orderedSlots.map { canonicalLocalTime(it.localTime) },
        daysOfWeek = daysOfWeek.mapTo(linkedSetOf()) { it.value },
        intervalDays = intervalDays,
        isEnabled = isEnabled,
        extras = extras.mapKeys { (key, _) -> key.toLegacyStorageKey() },
        createdAt = createdAtEpochMillis
    )
    return MappingResult.Success(
        MedicationPlanPersistenceAggregate(planEntity, slotEntities)
    )
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
    return MappingResult.Success(slots.chronologicallyOrdered())
}

private fun List<ScheduledDoseSlotEntity>.toDomainSlots(
    planId: java.util.UUID
): MappingResult<List<ScheduledDoseSlot>> {
    val mapped = mutableListOf<ScheduledDoseSlot>()
    sortedBy { it.position }.forEachIndexed { expectedPosition, slot ->
        if (slot.planId != planId) {
            return MappingResult.Failure(MappingError.InvalidSlotPlan(slot.position))
        }
        if (slot.position != expectedPosition) {
            return MappingResult.Failure(MappingError.InvalidSlotPosition(slot.position))
        }
        val parsedTime = try {
            LocalTime.parse(slot.localTime)
        } catch (_: DateTimeParseException) {
            return MappingResult.Failure(
                MappingError.InvalidSlotLocalTime(slot.position, slot.localTime)
            )
        }
        if (
            parsedTime.second != 0 ||
            parsedTime.nano != 0 ||
            slot.localTime != canonicalLocalTime(parsedTime)
        ) {
            return MappingResult.Failure(
                MappingError.InvalidSlotLocalTime(slot.position, slot.localTime)
            )
        }
        mapped += ScheduledDoseSlot(
            id = slot.id,
            planId = slot.planId,
            localTime = parsedTime,
            position = slot.position
        )
    }
    return MappingResult.Success(mapped)
}

private fun List<ScheduledDoseSlot>.chronologicallyOrdered(): List<ScheduledDoseSlot> =
    sortedWith(compareBy<ScheduledDoseSlot> { it.localTime }.thenBy { it.position })
        .mapIndexed { position, slot -> slot.copy(position = position) }

private fun List<String>.toCanonicalLegacyTimes(): MappingResult<List<String>> {
    val canonical = mutableListOf<String>()
    for (value in this) {
        val parsed = try {
            LocalTime.parse(value)
        } catch (_: DateTimeParseException) {
            return MappingResult.Failure(MappingError.InvalidTimeOfDay(value))
        }
        if (parsed.second != 0 || parsed.nano != 0) {
            return MappingResult.Failure(MappingError.InvalidTimeOfDay(value))
        }
        canonical += canonicalLocalTime(parsed)
    }
    return MappingResult.Success(canonical)
}

private fun canonicalLocalTime(localTime: LocalTime): String =
    localTime.hour.toString().padStart(2, '0') +
        ":" +
        localTime.minute.toString().padStart(2, '0')

private fun DomainScheduleType.toLegacyStorageScheduleType(): String = when (this) {
    DomainScheduleType.DAILY -> "DAILY"
    DomainScheduleType.WEEKLY -> "WEEKLY"
    DomainScheduleType.CUSTOM -> "CUSTOM"
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
