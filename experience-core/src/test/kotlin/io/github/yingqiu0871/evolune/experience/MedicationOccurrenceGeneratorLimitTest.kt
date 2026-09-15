package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * V17-C-01 RC-4 regression: the 100,000-occurrence guard keeps the exact threshold and now fails
 * through the dedicated exception type (exception typing only; count/control-flow unchanged).
 */
class MedicationOccurrenceGeneratorLimitTest {

    private val zone = ZoneOffset.UTC
    private val anchor: Instant = Instant.parse("2025-01-01T00:00:00Z")

    @Test
    fun `exactly one hundred thousand occurrences remain accepted`() {
        val window = OccurrenceGenerationWindow(anchor, anchor.plus(Duration.ofDays(1250)))
        val occurrences = MedicationOccurrenceGenerator.generate(listOf(schedule(80)), window, zone)

        assertEquals(100_000, occurrences.size)
    }

    @Test
    fun `the next occurrence beyond the guard fails with the dedicated exception`() {
        val window = OccurrenceGenerationWindow(anchor, anchor.plus(Duration.ofDays(1251)))
        try {
            MedicationOccurrenceGenerator.generate(listOf(schedule(80)), window, zone)
            throw AssertionError("expected the dedicated guard exception")
        } catch (expected: MedicationOccurrenceGenerationLimitExceededException) {
            assertTrue(requireNotNull(expected.message).contains("100000"))
        }
    }

    private fun schedule(slotCount: Int): MedicationSchedule = MedicationSchedule(
        planId = UUID(0L, 1L),
        presentation = MedicationPresentation(
            planName = "guard",
            matchKey = MedicationMatchKey("ORAL", "E2", 2.0)
        ),
        scheduleType = MedicationScheduleType.DAILY,
        slots = (0 until slotCount).map { index ->
            MedicationScheduleSlot(
                id = UUID(1L, index.toLong()),
                localTime = LocalTime.of(index / 4, (index % 4) * 15),
                position = index
            )
        },
        daysOfWeek = emptySet(),
        intervalDays = 1,
        enabled = true,
        createdAt = anchor
    )
}
