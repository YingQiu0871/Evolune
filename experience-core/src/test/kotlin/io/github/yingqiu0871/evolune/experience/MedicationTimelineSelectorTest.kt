package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class MedicationTimelineSelectorTest {
    private val now = Instant.parse("2025-01-04T10:00:00Z")

    @Test
    fun `default policy selects previous two current one and next five`() {
        val items = dailyItems(listOf(schedule(times = listOf(LocalTime.of(10, 0)))))
        val window = MedicationTimelineSelector.select(items, now)

        assertEquals(
            listOf("2025-01-02T10:00:00Z", "2025-01-03T10:00:00Z"),
            window.previous.map { it.occurrence.scheduledAt.toString() }
        )
        assertEquals(listOf("2025-01-04T10:00:00Z"), window.current.map { it.occurrence.scheduledAt.toString() })
        assertEquals(
            listOf(
                "2025-01-05T10:00:00Z",
                "2025-01-06T10:00:00Z",
                "2025-01-07T10:00:00Z",
                "2025-01-08T10:00:00Z",
                "2025-01-09T10:00:00Z"
            ),
            window.upcoming.map { it.occurrence.scheduledAt.toString() }
        )
    }

    @Test
    fun `counts are policy parameters and tolerate fewer items`() {
        val generated = occurrences(
            listOf(schedule(times = listOf(LocalTime.of(10, 0)))),
            "2025-01-03T00:00:00Z",
            "2025-01-07T00:00:00Z"
        )
        val items = MedicationOccurrencePresentation.derive(generated, emptyList(), now)
        val window = MedicationTimelineSelector.select(
            items,
            now,
            MedicationTimelinePolicy(previousCount = 5, upcomingCount = 10)
        )

        assertEquals(1, window.previous.size)
        assertEquals(1, window.current.size)
        assertEquals(2, window.upcoming.size)
    }

    @Test
    fun `no plans yields an empty timeline`() {
        val window = MedicationTimelineSelector.select(emptyList(), now)

        assertTrue(window.previous.isEmpty())
        assertTrue(window.current.isEmpty())
        assertTrue(window.upcoming.isEmpty())
    }

    @Test
    fun `simultaneous actionable occurrences remain in deterministic current group`() {
        val items = dailyItems(
            listOf(
                schedule(number = 2, times = listOf(LocalTime.of(10, 0))),
                schedule(number = 1, times = listOf(LocalTime.of(10, 0)))
            )
        )
        val window = MedicationTimelineSelector.select(items, now)

        assertEquals(2, window.current.size)
        assertEquals(listOf(1L, 2L), window.current.map { it.occurrence.planId.leastSignificantBits })
    }

    @Test
    fun `without actionable item selector centers previous and upcoming on now`() {
        val generated = occurrences(
            listOf(schedule(times = listOf(LocalTime.of(10, 0)))),
            "2025-01-01T00:00:00Z",
            "2025-01-08T00:00:00Z"
        )
        val items = MedicationOccurrencePresentation.derive(
            generated,
            emptyList(),
            now,
            MedicationOccurrencePolicy(dueBefore = java.time.Duration.ZERO, dueAfter = java.time.Duration.ZERO)
        )
        val shiftedNow = now.plusSeconds(1L)
        val shiftedItems = MedicationOccurrencePresentation.derive(
            generated,
            emptyList(),
            shiftedNow,
            MedicationOccurrencePolicy(dueBefore = java.time.Duration.ZERO, dueAfter = java.time.Duration.ZERO)
        )
        val window = MedicationTimelineSelector.select(shiftedItems, shiftedNow)

        assertTrue(window.current.isEmpty())
        assertEquals(2, window.previous.size)
        assertEquals(3, window.upcoming.size)
        assertEquals(MedicationOccurrenceStatus.DUE, items.single { it.occurrence.scheduledAt == now }.status)
    }

    @Test
    fun `nearest due instant is current and other due items remain visible`() {
        val generated = occurrences(
            listOf(schedule(times = listOf(LocalTime.of(9, 30), LocalTime.of(10, 10), LocalTime.of(10, 30)))),
            "2025-01-04T00:00:00Z",
            "2025-01-05T00:00:00Z"
        )
        val items = MedicationOccurrencePresentation.derive(generated, emptyList(), now)
        val window = MedicationTimelineSelector.select(items, now)

        assertEquals(Instant.parse("2025-01-04T10:10:00Z"), window.current.single().occurrence.scheduledAt)
        assertEquals(Instant.parse("2025-01-04T09:30:00Z"), window.previous.single().occurrence.scheduledAt)
        assertEquals(Instant.parse("2025-01-04T10:30:00Z"), window.upcoming.single().occurrence.scheduledAt)
    }

    private fun dailyItems(schedules: List<MedicationSchedule>): List<MedicationTimelineItem> {
        val generated = occurrences(
            schedules,
            "2025-01-01T00:00:00Z",
            "2025-01-11T00:00:00Z"
        )
        return MedicationOccurrencePresentation.derive(generated, emptyList(), now)
    }
}
