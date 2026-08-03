package io.github.yuninggu.evolune.core.model

import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class MedicationPlanTest {
    private val planId = UUID.fromString("20000000-0000-0000-0000-000000000001")
    private val createdAt = Instant.parse("2024-02-03T04:05:06Z")

    @Test
    fun completeValidPlanCanBeCreated() {
        val slots = listOf(slot(0, LocalTime.of(8, 30)), slot(1, LocalTime.of(20, 30)))
        val plan = plan(
            name = "Synthetic plan",
            route = Route.SUBLINGUAL,
            ester = Ester.E2,
            doseMG = 1.5,
            scheduleType = ScheduleType.WEEKLY,
            slots = slots,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            intervalDays = 3,
            isEnabled = false,
            extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0)
        )

        assertEquals(planId, plan.id)
        assertEquals("Synthetic plan", plan.name)
        assertEquals(Route.SUBLINGUAL, plan.route)
        assertEquals(Ester.E2, plan.ester)
        assertEquals(1.5, plan.doseMG, 0.0)
        assertEquals(ScheduleType.WEEKLY, plan.scheduleType)
        assertEquals(slots, plan.slots)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), plan.daysOfWeek)
        assertEquals(3, plan.intervalDays)
        assertEquals(false, plan.isEnabled)
        assertEquals(mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0), plan.extras)
        assertEquals(createdAt, plan.createdAt)
    }

    @Test
    fun emptySlotsAreValid() {
        assertTrue(plan(slots = emptyList()).slots.isEmpty())
    }

    @Test
    fun duplicateLocalTimesAreValid() {
        val time = LocalTime.of(8, 30)
        val plan = plan(slots = listOf(slot(0, time), slot(1, time)))

        assertEquals(listOf(time, time), plan.slots.map { it.localTime })
    }

    @Test
    fun mismatchedSlotPlanIdIsRejected() {
        val otherPlanId = UUID.fromString("20000000-0000-0000-0000-000000000002")

        assertThrows(IllegalArgumentException::class.java) {
            plan(slots = listOf(slot(0, LocalTime.NOON, otherPlanId)))
        }
    }

    @Test
    fun firstPositionMustBeZero() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(slots = listOf(slot(1, LocalTime.NOON)))
        }
    }

    @Test
    fun positionsMustBeContinuous() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(slots = listOf(slot(0, LocalTime.NOON), slot(2, LocalTime.of(13, 0))))
        }
    }

    @Test
    fun eachPositionMustMatchItsListIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(slots = listOf(slot(0, LocalTime.NOON), slot(0, LocalTime.of(13, 0))))
        }
    }

    @Test
    fun intervalDaysOneIsValid() {
        assertEquals(1, plan(intervalDays = 1).intervalDays)
    }

    @Test
    fun intervalDaysMaximumIsValid() {
        assertEquals(Int.MAX_VALUE, plan(intervalDays = Int.MAX_VALUE).intervalDays)
    }

    @Test
    fun intervalDaysZeroIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(intervalDays = 0)
        }
    }

    @Test
    fun negativeIntervalDaysAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(intervalDays = -1)
        }
    }

    @Test
    fun dailyRetainsIrrelevantScheduleFields() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
        val plan = plan(
            scheduleType = ScheduleType.DAILY,
            daysOfWeek = days,
            intervalDays = 14
        )

        assertEquals(days, plan.daysOfWeek)
        assertEquals(14, plan.intervalDays)
    }

    @Test
    fun weeklyAllowsEmptyDaysOfWeek() {
        val plan = plan(scheduleType = ScheduleType.WEEKLY, daysOfWeek = emptySet())

        assertTrue(plan.daysOfWeek.isEmpty())
    }

    @Test
    fun customRetainsDaysOfWeek() {
        val days = setOf(DayOfWeek.SUNDAY)
        val plan = plan(scheduleType = ScheduleType.CUSTOM, daysOfWeek = days)

        assertEquals(days, plan.daysOfWeek)
    }

    @Test
    fun slotsAreNotAutomaticallySorted() {
        val late = slot(0, LocalTime.of(22, 0))
        val early = slot(1, LocalTime.of(6, 0))
        val plan = plan(slots = listOf(late, early))

        assertEquals(listOf(late, early), plan.slots)
    }

    @Test
    fun createdAtRetainsOrdinaryInstant() {
        val instant = Instant.parse("2030-12-31T23:59:59Z")

        assertEquals(instant, plan(createdAt = instant).createdAt)
    }

    @Test
    fun nameDoseAndExtrasReceiveNoNewCompatibilityValidation() {
        val extras = mapOf(ExtraKey.AREA_CM2 to Double.NaN)
        val plan = plan(name = "", doseMG = Double.NaN, extras = extras)

        assertEquals("", plan.name)
        assertTrue(plan.doseMG.isNaN())
        assertTrue(plan.extras.getValue(ExtraKey.AREA_CM2).isNaN())
    }

    @Test
    fun scheduleTypeContainsOnlyResolvedValues() {
        assertEquals(
            listOf("DAILY", "WEEKLY", "CUSTOM"),
            ScheduleType.entries.map { it.name }
        )
    }

    private fun plan(
        name: String = "Synthetic plan",
        route: Route = Route.ORAL,
        ester: Ester = Ester.E2,
        doseMG: Double = 2.0,
        scheduleType: ScheduleType = ScheduleType.DAILY,
        slots: List<ScheduledDoseSlot> = listOf(slot(0, LocalTime.of(8, 0))),
        daysOfWeek: Set<DayOfWeek> = emptySet(),
        intervalDays: Int = 1,
        isEnabled: Boolean = true,
        extras: Map<ExtraKey, Double> = emptyMap(),
        createdAt: Instant = this.createdAt
    ): MedicationPlan = MedicationPlan(
        id = planId,
        name = name,
        route = route,
        ester = ester,
        doseMG = doseMG,
        scheduleType = scheduleType,
        slots = slots,
        daysOfWeek = daysOfWeek,
        intervalDays = intervalDays,
        isEnabled = isEnabled,
        extras = extras,
        createdAt = createdAt
    )

    private fun slot(
        position: Int,
        localTime: LocalTime,
        slotPlanId: UUID = planId
    ): ScheduledDoseSlot = ScheduledDoseSlot(
        id = UUID.fromString("30000000-0000-0000-0000-${(position + 1).toString().padStart(12, '0')}"),
        planId = slotPlanId,
        localTime = localTime,
        position = position
    )
}
