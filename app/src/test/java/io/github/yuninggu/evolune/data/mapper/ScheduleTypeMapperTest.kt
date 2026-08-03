package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.model.ScheduleType as DomainScheduleType
import io.github.yuninggu.evolune.data.MedicationPlan.ScheduleType as LegacyScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTypeMapperTest {
    @Test
    fun allLegacyTypesMapExplicitlyToDomainTypes() {
        assertEquals(DomainScheduleType.DAILY, LegacyScheduleType.DAILY.toDomainScheduleType())
        assertEquals(DomainScheduleType.WEEKLY, LegacyScheduleType.WEEKLY.toDomainScheduleType())
        assertEquals(DomainScheduleType.CUSTOM, LegacyScheduleType.CUSTOM.toDomainScheduleType())
    }

    @Test
    fun allDomainTypesMapExplicitlyToLegacyTypes() {
        assertEquals(LegacyScheduleType.DAILY, DomainScheduleType.DAILY.toLegacyScheduleType())
        assertEquals(LegacyScheduleType.WEEKLY, DomainScheduleType.WEEKLY.toLegacyScheduleType())
        assertEquals(LegacyScheduleType.CUSTOM, DomainScheduleType.CUSTOM.toLegacyScheduleType())
    }
}
