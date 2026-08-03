package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.model.ScheduleType as DomainScheduleType
import io.github.yuninggu.evolune.data.MedicationPlan.ScheduleType as LegacyScheduleType

fun LegacyScheduleType.toDomainScheduleType(): DomainScheduleType = when (this) {
    LegacyScheduleType.DAILY -> DomainScheduleType.DAILY
    LegacyScheduleType.WEEKLY -> DomainScheduleType.WEEKLY
    LegacyScheduleType.CUSTOM -> DomainScheduleType.CUSTOM
}

fun DomainScheduleType.toLegacyScheduleType(): LegacyScheduleType = when (this) {
    DomainScheduleType.DAILY -> LegacyScheduleType.DAILY
    DomainScheduleType.WEEKLY -> LegacyScheduleType.WEEKLY
    DomainScheduleType.CUSTOM -> LegacyScheduleType.CUSTOM
}

internal fun scheduleTypeFromLegacyStorage(
    value: String
): MappingResult<DomainScheduleType> = when (value) {
    "DAILY" -> MappingResult.Success(LegacyScheduleType.DAILY.toDomainScheduleType())
    "WEEKLY" -> MappingResult.Success(LegacyScheduleType.WEEKLY.toDomainScheduleType())
    "CUSTOM" -> MappingResult.Success(LegacyScheduleType.CUSTOM.toDomainScheduleType())
    else -> MappingResult.Failure(MappingError.InvalidScheduleType(value))
}
