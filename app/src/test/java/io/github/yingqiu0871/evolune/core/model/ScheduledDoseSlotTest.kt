package io.github.yingqiu0871.evolune.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ScheduledDoseSlotTest {
    private val planId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun fixedVectorMatchesTheResolvedUuidV5Design() {
        val result = success(ScheduledDoseSlotId.generate(planId, 0, LocalTime.of(8, 30)))

        assertEquals("68559b97-4ddc-5be2-bcbd-9ab409f0d95b", result.projectNamespace.toString())
        assertEquals(
            "slot:v1:plan=00000000-0000-0000-0000-000000000001;position=0;time=08:30",
            result.canonicalName
        )
        assertEquals("17d1fd14-9d70-5344-beaa-0b158c9f62f4", result.id.toString())
        assertEquals(5, result.id.version())
        assertEquals(2, result.id.variant())
        assertTrue(result.id.toString().matches(UUID_PATTERN))
    }

    @Test
    fun equalInputsAreStable() {
        val first = success(ScheduledDoseSlotId.generate(planId, 0, LocalTime.of(8, 30)))
        val second = success(ScheduledDoseSlotId.generate(planId, 0, LocalTime.of(8, 30)))
        assertEquals(first.id, second.id)
    }

    @Test
    fun eachIdentityInputChangesTheId() {
        val baseline = success(ScheduledDoseSlotId.generate(planId, 0, LocalTime.of(8, 30))).id
        val otherPlan = success(
            ScheduledDoseSlotId.generate(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                0,
                LocalTime.of(8, 30)
            )
        ).id
        val otherPosition = success(
            ScheduledDoseSlotId.generate(planId, 1, LocalTime.of(8, 30))
        ).id
        val otherTime = success(
            ScheduledDoseSlotId.generate(planId, 0, LocalTime.of(8, 31))
        ).id

        assertNotEquals(baseline, otherPlan)
        assertNotEquals(baseline, otherPosition)
        assertNotEquals(baseline, otherTime)
        assertNotEquals(otherPosition, baseline)
    }

    @Test
    fun zeroAndMaximumPositionsAreAccepted() {
        assertTrue(ScheduledDoseSlotId.generate(planId, 0, LocalTime.MIDNIGHT) is SlotIdResult.Success)
        assertTrue(
            ScheduledDoseSlotId.generate(
                planId,
                Int.MAX_VALUE,
                LocalTime.of(23, 59)
            ) is SlotIdResult.Success
        )
    }

    @Test
    fun negativePositionIsRejected() {
        val result = ScheduledDoseSlotId.generate(planId, -1, LocalTime.NOON)
        assertTrue(result is SlotIdResult.Failure)
        assertTrue((result as SlotIdResult.Failure).error is SlotIdError.InvalidPosition)
    }

    @Test
    fun secondsAndNanosecondsAreRejectedWithoutTruncation() {
        val seconds = ScheduledDoseSlotId.generate(planId, 0, LocalTime.of(8, 30, 1))
        val nanos = ScheduledDoseSlotId.generate(planId, 0, LocalTime.of(8, 30, 0, 500_000_000))

        assertTrue((seconds as SlotIdResult.Failure).error is SlotIdError.InvalidLocalTimePrecision)
        assertTrue((nanos as SlotIdResult.Failure).error is SlotIdError.InvalidLocalTimePrecision)
    }

    @Test
    fun invalidAndWhitespacePlanIdsReturnExplicitErrors() {
        val invalid = ScheduledDoseSlotId.generate("not-a-uuid", 0, LocalTime.NOON)
        val whitespace = ScheduledDoseSlotId.generate(" $planId", 0, LocalTime.NOON)

        assertTrue((invalid as SlotIdResult.Failure).error is SlotIdError.InvalidPlanId)
        assertTrue(
            (whitespace as SlotIdResult.Failure).error is
                SlotIdError.PlanIdHasSurroundingWhitespace
        )
    }

    @Test
    fun localeAndDefaultTimeZoneDoNotChangeTheResult() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val first = success(
                ScheduledDoseSlotId.generate(planId, 12, LocalTime.of(8, 30))
            )
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val second = success(
                ScheduledDoseSlotId.generate(planId, 12, LocalTime.of(8, 30))
            )

            assertEquals(first, second)
            assertEquals(
                "slot:v1:plan=00000000-0000-0000-0000-000000000001;position=12;time=08:30",
                second.canonicalName
            )
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun modelEnforcesTheSamePositionAndPrecisionInvariants() {
        val id = success(ScheduledDoseSlotId.generate(planId, 0, LocalTime.NOON)).id
        val slot = ScheduledDoseSlot(id, planId, LocalTime.NOON, 0)
        assertEquals(0, slot.position)

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ScheduledDoseSlot(id, planId, LocalTime.NOON, -1)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ScheduledDoseSlot(id, planId, LocalTime.of(12, 0, 1), 0)
        }
    }

    private fun success(result: SlotIdResult): SlotIdResult.Success {
        assertTrue(result is SlotIdResult.Success)
        return result as SlotIdResult.Success
    }

    private companion object {
        val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
