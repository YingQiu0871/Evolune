package io.github.yingqiu0871.evolune.history.timeline

import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.MedicationPresentation
import io.github.yingqiu0871.evolune.experience.MedicationSchedule
import io.github.yingqiu0871.evolune.experience.MedicationScheduleSlot
import io.github.yingqiu0871.evolune.experience.MedicationScheduleType
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.history.testDay
import io.github.yingqiu0871.evolune.history.testRange
import io.github.yingqiu0871.evolune.history.unrecordedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * TLM18 (binding prerequisite from final contract review) — LIVE behavioral parity against the
 * public [MedicationOccurrenceGenerator] ordering.
 *
 * The test builds real schedules that generate several occurrences at the SAME scheduled instant
 * but differing in the fields used by `OCCURRENCE_ORDER` (`planId`, `slotPosition`, `slotId`),
 * obtains the occurrence sequence from the public generator, projects the same occurrences as
 * occurrence-backed Timeline rows, and asserts that the two sequences are identical.
 *
 * This is a drift detector: if `OCCURRENCE_ORDER` changes, this test fails unless the Timeline
 * comparator is updated consistently. A hard-coded expected list is intentionally NOT used.
 */
class TimelineOrderingGeneratorParityTest {

    private val zone = ZoneOffset.UTC
    private val date: LocalDate = LocalDate.of(2026, 9, 16)
    private val targetTime: LocalTime = LocalTime.of(8, 0)

    private fun schedule(planLsb: Long, slotCount: Int): MedicationSchedule = MedicationSchedule(
        planId = UUID(0L, planLsb),
        presentation = MedicationPresentation(
            planName = "plan-$planLsb",
            matchKey = MedicationMatchKey(routeKey = "ORAL", medicationKey = "E2", doseAmount = 2.0)
        ),
        scheduleType = MedicationScheduleType.DAILY,
        slots = (0 until slotCount).map { position ->
            MedicationScheduleSlot(
                id = UUID(planLsb, position.toLong() + 1L),
                localTime = targetTime,
                position = position
            )
        },
        daysOfWeek = emptySet(),
        intervalDays = 1,
        enabled = true,
        createdAt = date.atStartOfDay(zone).toInstant()
    )

    @Test
    fun `TLM18 Timeline same-instant occurrence order matches live generator order`() {
        // Three plans at the same wall-clock time; one plan carries two slots at the same instant.
        // Supplied in shuffled order so any accidental input-order dependence is exposed.
        val schedules = listOf(
            schedule(planLsb = 10L, slotCount = 2),
            schedule(planLsb = 1L, slotCount = 1),
            schedule(planLsb = 2L, slotCount = 1)
        )

        val window = OccurrenceGenerationWindow(
            startInclusive = date.atStartOfDay(zone).toInstant(),
            endExclusive = date.plusDays(1).atStartOfDay(zone).toInstant()
        )
        val generated = MedicationOccurrenceGenerator.generate(schedules, window, zone)
        val targetInstant: Instant = date.atTime(targetTime).toInstant(zone)
        val generatorSameInstant: List<MedicationOccurrence> =
            generated.filter { it.scheduledAt == targetInstant }

        assertTrue("the same-instant set must be non-trivial", generatorSameInstant.size >= 4)
        assertEquals(
            "generator output must be grouped by plan id for this parity scenario",
            3,
            generatorSameInstant.map { it.planId }.toSet().size
        )
        assertTrue(
            "generator parity set must include a slot-position tie",
            generatorSameInstant.groupBy { it.planId }.values.any { it.size >= 2 }
        )

        // Feed the SAME occurrences (reversed) as occurrence-backed entries into the timeline.
        val entries = generatorSameInstant.reversed().map { occurrence ->
            unrecordedEntry(
                occurrence = occurrence,
                displayDate = occurrence.scheduledLocalDateTime.toLocalDate()
            )
        }
        val range = testRange(
            startDate = date.minusDays(1),
            endDate = date.plusDays(1),
            days = listOf(testDay(date = date, entries = entries))
        )

        val timelineRows = TimelineProjectionBuilder.build(range).days
            .flatMap { it.rows }
            .filter { it.sortInstant == targetInstant }

        val expectedOrder: List<UUID> = generatorSameInstant.map { it.id.value }
        val actualOrder: List<UUID> = timelineRows.map { row ->
            (row.rowId as TimelineRowId.Occurrence).occurrenceId.value
        }

        assertNotEquals("reversed input must not already equal the canonical order", expectedOrder, expectedOrder.reversed())
        assertEquals(
            "Timeline same-instant order must behaviorally match MedicationOccurrenceGenerator output",
            expectedOrder,
            actualOrder
        )
        assertTrue("parity must cover every same-instant occurrence", timelineRows.size == generatorSameInstant.size)
    }

    @Test
    fun `TLM18 parity window covers a multi-day generation bound`() {
        val schedules = listOf(schedule(planLsb = 3L, slotCount = 1))
        val window = OccurrenceGenerationWindow(
            startInclusive = date.atStartOfDay(zone).toInstant(),
            endExclusive = date.plusDays(2).atStartOfDay(zone).toInstant()
        )
        val generated = MedicationOccurrenceGenerator.generate(schedules, window, zone)
        assertEquals("two daily occurrences over a two-day window", 2, generated.size)
        assertEquals(
            listOf(date, date.plusDays(1)),
            generated.map { it.scheduledLocalDateTime.toLocalDate() }
        )
        assertEquals(Duration.ofDays(1), Duration.between(generated[0].scheduledAt, generated[1].scheduledAt))
    }
}
