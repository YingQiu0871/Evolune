package io.github.yingqiu0871.evolune.data.mapper

import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.data.MedicationPlanEntity
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class MedicationPlanEntityMapperTest {
    private val planId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun completeSyntheticEntityMapsEveryField() {
        val plan = success(
            entity(
                name = "Synthetic plan",
                route = "SUBLINGUAL",
                ester = "E2",
                doseMG = 1.5,
                scheduleType = "WEEKLY",
                timeOfDay = listOf("08:30", "20:30"),
                daysOfWeek = setOf(1, 4),
                intervalDays = 3,
                isEnabled = false,
                extras = mapOf("SUBLINGUAL_TIER" to 2.0),
                createdAt = 1_700_000_000_123L
            ).toDomainMedicationPlan()
        )

        assertEquals(planId, plan.id)
        assertEquals("Synthetic plan", plan.name)
        assertEquals(Route.SUBLINGUAL, plan.route)
        assertEquals(Ester.E2, plan.ester)
        assertEquals(1.5, plan.doseMG, 0.0)
        assertEquals(ScheduleType.WEEKLY, plan.scheduleType)
        assertEquals(listOf(LocalTime.of(8, 30), LocalTime.of(20, 30)), plan.slots.map { it.localTime })
        assertEquals(listOf(0, 1), plan.slots.map { it.position })
        assertEquals(planId, plan.slots[0].planId)
        assertEquals(planId, plan.slots[1].planId)
        assertEquals(
            UUID.fromString("17d1fd14-9d70-5344-beaa-0b158c9f62f4"),
            plan.slots.first().id
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), plan.daysOfWeek)
        assertEquals(3, plan.intervalDays)
        assertEquals(false, plan.isEnabled)
        assertEquals(mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0), plan.extras)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_123L), plan.createdAt)
    }

    @Test
    fun emptyTimeListMapsToEmptySlots() {
        assertTrue(success(entity(timeOfDay = emptyList()).toDomainMedicationPlan()).slots.isEmpty())
    }

    @Test
    fun oneTimeMapsToOneSlot() {
        val slots = success(entity(timeOfDay = listOf("23:59")).toDomainMedicationPlan()).slots

        assertEquals(1, slots.size)
        assertEquals(0, slots.single().position)
        assertEquals(LocalTime.of(23, 59), slots.single().localTime)
    }

    @Test
    fun multipleAndDuplicateTimesPreserveOrderAndReceiveDistinctStableIds() {
        val first = success(
            entity(timeOfDay = listOf("22:00", "06:00", "22:00")).toDomainMedicationPlan()
        )
        val second = success(
            entity(timeOfDay = listOf("22:00", "06:00", "22:00")).toDomainMedicationPlan()
        )

        assertEquals(
            listOf(LocalTime.of(22, 0), LocalTime.of(6, 0), LocalTime.of(22, 0)),
            first.slots.map { it.localTime }
        )
        assertEquals(listOf(0, 1, 2), first.slots.map { it.position })
        assertNotEquals(first.slots[0].id, first.slots[2].id)
        assertEquals(first.slots.map { it.id }, second.slots.map { it.id })
    }

    @Test
    fun dailyRetainsLegacyFieldsWithoutNormalization() {
        val plan = success(
            entity(
                scheduleType = "DAILY",
                daysOfWeek = setOf(2, 5),
                intervalDays = 14
            ).toDomainMedicationPlan()
        )

        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), plan.daysOfWeek)
        assertEquals(14, plan.intervalDays)
    }

    @Test
    fun weeklyAllowsEmptyDaysOfWeek() {
        val plan = success(
            entity(scheduleType = "WEEKLY", daysOfWeek = emptySet()).toDomainMedicationPlan()
        )

        assertTrue(plan.daysOfWeek.isEmpty())
    }

    @Test
    fun customRetainsIntervalAndDaysOfWeek() {
        val plan = success(
            entity(
                scheduleType = "CUSTOM",
                daysOfWeek = setOf(7),
                intervalDays = 30
            ).toDomainMedicationPlan()
        )

        assertEquals(30, plan.intervalDays)
        assertEquals(setOf(DayOfWeek.SUNDAY), plan.daysOfWeek)
    }

    @Test
    fun zeroIntervalReturnsPlanInvariantFailure() {
        assertEquals(
            MappingError.InvalidPlanInvariant(0),
            failure(entity(intervalDays = 0).toDomainMedicationPlan())
        )
    }

    @Test
    fun negativeIntervalReturnsPlanInvariantFailure() {
        assertEquals(
            MappingError.InvalidPlanInvariant(-1),
            failure(entity(intervalDays = -1).toDomainMedicationPlan())
        )
    }

    @Test
    fun malformedTimeReturnsExplicitFailure() {
        assertInvalidTimeOfDay("not-a-time")
    }

    @Test
    fun timeWithNonZeroSecondsReturnsExplicitFailure() {
        assertInvalidTimeOfDay("08:30:01")
    }

    @Test
    fun timeWithNonZeroNanosecondsReturnsExplicitFailure() {
        assertInvalidTimeOfDay("08:30:00.500")
    }

    @Test
    fun unknownScheduleTypeReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidScheduleType("UNKNOWN_SCHEDULE"),
            failure(entity(scheduleType = "UNKNOWN_SCHEDULE").toDomainMedicationPlan())
        )
    }

    @Test
    fun unknownRouteReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidRoute("UNKNOWN_ROUTE"),
            failure(entity(route = "UNKNOWN_ROUTE").toDomainMedicationPlan())
        )
    }

    @Test
    fun unknownEsterReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidEster("UNKNOWN_ESTER"),
            failure(entity(ester = "UNKNOWN_ESTER").toDomainMedicationPlan())
        )
    }

    @Test
    fun unknownExtraKeyReturnsExplicitFailure() {
        assertEquals(
            MappingError.InvalidExtraKey("UNKNOWN_EXTRA"),
            failure(
                entity(extras = mapOf("UNKNOWN_EXTRA" to 1.0)).toDomainMedicationPlan()
            )
        )
    }

    @Test
    fun dayOfWeekBelowRangeReturnsExplicitFailure() {
        assertInvalidDayOfWeek(0)
    }

    @Test
    fun dayOfWeekAboveRangeReturnsExplicitFailure() {
        assertInvalidDayOfWeek(8)
    }

    @Test
    fun createdAtEpochMillisMapsExactlyToInstant() {
        val epochMillis = -123_456_789L

        assertEquals(
            Instant.ofEpochMilli(epochMillis),
            success(entity(createdAt = epochMillis).toDomainMedicationPlan()).createdAt
        )
    }

    @Test
    fun defaultLocaleAndTimeZoneDoNotAffectMapping() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val first = success(entity().toDomainMedicationPlan())
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val second = success(entity().toDomainMedicationPlan())

            assertEquals(first, second)
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun entity(
        name: String = "Synthetic plan",
        route: String = "ORAL",
        ester: String = "EV",
        doseMG: Double = 2.0,
        scheduleType: String = "DAILY",
        timeOfDay: List<String> = listOf("08:30"),
        daysOfWeek: Set<Int> = emptySet(),
        intervalDays: Int = 1,
        isEnabled: Boolean = true,
        extras: Map<String, Double> = emptyMap(),
        createdAt: Long = 1_700_000_000_000L
    ) = MedicationPlanEntity(
        id = planId,
        name = name,
        route = route,
        ester = ester,
        doseMG = doseMG,
        scheduleType = scheduleType,
        timeOfDay = timeOfDay,
        daysOfWeek = daysOfWeek,
        intervalDays = intervalDays,
        isEnabled = isEnabled,
        extras = extras,
        createdAt = createdAt
    )

    private fun success(result: MappingResult<MedicationPlan>): MedicationPlan {
        assertTrue(result is MappingResult.Success)
        return (result as MappingResult.Success).value
    }

    private fun failure(result: MappingResult<*>): MappingError {
        assertTrue(result is MappingResult.Failure)
        return (result as MappingResult.Failure).error
    }

    private fun assertInvalidTimeOfDay(value: String) {
        assertEquals(
            MappingError.InvalidTimeOfDay(value),
            failure(entity(timeOfDay = listOf(value)).toDomainMedicationPlan())
        )
    }

    private fun assertInvalidDayOfWeek(value: Int) {
        assertEquals(
            MappingError.InvalidDayOfWeek(value),
            failure(entity(daysOfWeek = setOf(value)).toDomainMedicationPlan())
        )
    }
}
