package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MedicationOccurrenceGeneratorTest {
    @Test
    fun `daily generation crosses previous day midnight and next day`() {
        val result = occurrences(
            schedules = listOf(schedule(times = listOf(LocalTime.of(23, 30)))),
            start = "2025-01-01T23:00:00Z",
            end = "2025-01-04T00:00:00Z"
        )

        assertEquals(
            listOf(
                Instant.parse("2025-01-01T23:30:00Z"),
                Instant.parse("2025-01-02T23:30:00Z"),
                Instant.parse("2025-01-03T23:30:00Z")
            ),
            result.map { it.scheduledAt }
        )
        assertEquals(
            listOf(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 3)),
            result.map { it.scheduledLocalDateTime.toLocalDate() }
        )
    }

    @Test
    fun `multiple identical-time plans are retained and deterministically ordered`() {
        val firstCall = occurrences(
            schedules = listOf(schedule(number = 2), schedule(number = 1)),
            start = "2025-01-02T00:00:00Z",
            end = "2025-01-03T00:00:00Z"
        )
        val secondCall = occurrences(
            schedules = listOf(schedule(number = 1), schedule(number = 2)),
            start = "2025-01-02T00:00:00Z",
            end = "2025-01-03T00:00:00Z"
        )

        assertEquals(2, firstCall.size)
        assertEquals(firstCall, secondCall)
        assertEquals(listOf(1L, 2L), firstCall.map { it.planId.leastSignificantBits })
        assertEquals(1, firstCall.map { it.scheduledAt }.distinct().size)
    }

    @Test
    fun `disabled plans are excluded`() {
        val result = occurrences(
            schedules = listOf(schedule(number = 1), schedule(number = 2, enabled = false)),
            start = "2025-01-02T00:00:00Z",
            end = "2025-01-03T00:00:00Z"
        )

        assertEquals(listOf(1L), result.map { it.planId.leastSignificantBits })
    }

    @Test
    fun `window is start inclusive and end exclusive`() {
        val result = occurrences(
            schedules = listOf(schedule(times = listOf(LocalTime.MIDNIGHT, LocalTime.NOON))),
            start = "2025-01-02T00:00:00Z",
            end = "2025-01-02T12:00:00Z"
        )

        assertEquals(listOf(Instant.parse("2025-01-02T00:00:00Z")), result.map { it.scheduledAt })
    }

    @Test
    fun `weekly plan respects authoritative weekdays`() {
        val result = occurrences(
            schedules = listOf(
                schedule(
                    scheduleType = MedicationScheduleType.WEEKLY,
                    daysOfWeek = setOf(DayOfWeek.MONDAY),
                    times = listOf(LocalTime.NOON)
                )
            ),
            start = "2025-01-05T00:00:00Z",
            end = "2025-01-14T00:00:00Z"
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2025, 1, 6, 12, 0),
                LocalDateTime.of(2025, 1, 13, 12, 0)
            ),
            result.map { it.scheduledLocalDateTime }
        )
    }

    @Test
    fun `custom interval is anchored to plan creation date`() {
        val result = occurrences(
            schedules = listOf(
                schedule(
                    scheduleType = MedicationScheduleType.CUSTOM,
                    intervalDays = 3,
                    times = listOf(LocalTime.of(8, 0)),
                    createdAt = Instant.parse("2025-01-02T03:00:00Z")
                )
            ),
            start = "2025-01-01T00:00:00Z",
            end = "2025-01-12T00:00:00Z"
        )

        assertEquals(
            listOf(
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2025, 1, 5),
                LocalDate.of(2025, 1, 8),
                LocalDate.of(2025, 1, 11)
            ),
            result.map { it.scheduledLocalDateTime.toLocalDate() }
        )
    }

    @Test
    fun `occurrences before plan creation instant are excluded`() {
        val result = occurrences(
            schedules = listOf(
                schedule(
                    times = listOf(LocalTime.of(8, 0), LocalTime.of(16, 0)),
                    createdAt = Instant.parse("2025-01-02T12:00:00Z")
                )
            ),
            start = "2025-01-02T00:00:00Z",
            end = "2025-01-03T00:00:00Z"
        )

        assertEquals(listOf(Instant.parse("2025-01-02T16:00:00Z")), result.map { it.scheduledAt })
    }

    @Test
    fun `same Paris and Shanghai local occurrence keeps identity while instant changes`() {
        val plan = schedule(times = listOf(LocalTime.of(8, 0)))
        val paris = occurrences(
            listOf(plan),
            "2025-01-01T23:00:00Z",
            "2025-01-02T23:00:00Z",
            ZoneId.of("Europe/Paris")
        ).single()
        val shanghai = occurrences(
            listOf(plan),
            "2025-01-01T23:00:00Z",
            "2025-01-02T23:00:00Z",
            ZoneId.of("Asia/Shanghai")
        ).single()

        assertEquals(LocalTime.of(8, 0), paris.scheduledLocalDateTime.toLocalTime())
        assertEquals(LocalTime.of(8, 0), shanghai.scheduledLocalDateTime.toLocalTime())
        assertNotEquals(paris.scheduledAt, shanghai.scheduledAt)
        assertEquals(paris.id, shanghai.id)
    }

    @Test
    fun `DST spring gap resolves forward using java time semantics`() {
        val plan = schedule(
            times = listOf(LocalTime.of(2, 30)),
            createdAt = Instant.parse("2025-03-01T00:00:00Z")
        )
        val paris = occurrences(
            schedules = listOf(plan),
            start = "2025-03-29T22:00:00Z",
            end = "2025-03-30T23:00:00Z",
            zoneId = ZoneId.of("Europe/Paris")
        ).single()
        val utc = occurrences(
            schedules = listOf(plan),
            start = "2025-03-29T22:00:00Z",
            end = "2025-03-30T23:00:00Z",
            zoneId = ZoneId.of("UTC")
        ).single()

        assertEquals(LocalDateTime.of(2025, 3, 30, 2, 30), paris.scheduledLocalDateTime)
        assertEquals(Instant.parse("2025-03-30T01:30:00Z"), paris.scheduledAt)
        assertEquals(
            LocalDateTime.of(2025, 3, 30, 3, 30),
            paris.scheduledAt.atZone(ZoneId.of("Europe/Paris")).toLocalDateTime()
        )
        assertNotEquals(paris.scheduledAt, utc.scheduledAt)
        assertEquals(paris.id, utc.id)
    }

    @Test
    fun `DST fall overlap creates one occurrence at the earlier offset`() {
        val plan = schedule(
            times = listOf(LocalTime.of(2, 30)),
            createdAt = Instant.parse("2025-10-01T00:00:00Z")
        )
        val paris = occurrences(
            schedules = listOf(plan),
            start = "2025-10-25T22:00:00Z",
            end = "2025-10-26T23:00:00Z",
            zoneId = ZoneId.of("Europe/Paris")
        )
        val utc = occurrences(
            schedules = listOf(plan),
            start = "2025-10-25T22:00:00Z",
            end = "2025-10-26T23:00:00Z",
            zoneId = ZoneId.of("UTC")
        ).single()

        assertEquals(1, paris.size)
        assertEquals(Instant.parse("2025-10-26T00:30:00Z"), paris.single().scheduledAt)
        assertNotEquals(paris.single().scheduledAt, utc.scheduledAt)
        assertEquals(paris.single().id, utc.id)
    }

    @Test
    fun `oversized or empty windows fail instead of expanding without bound`() {
        assertThrows(IllegalArgumentException::class.java) {
            OccurrenceGenerationWindow(
                Instant.EPOCH,
                Instant.EPOCH.plus(Duration.ofDays(OccurrenceGenerationWindow.MAX_WINDOW_DAYS + 1L))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OccurrenceGenerationWindow(Instant.EPOCH, Instant.EPOCH)
        }
    }

    @Test
    fun `maximum supported bounded window remains finite`() {
        val window = OccurrenceGenerationWindow(
            Instant.parse("2020-01-01T00:00:00Z"),
            Instant.parse("2030-01-01T00:00:00Z")
        )
        val result = MedicationOccurrenceGenerator.generate(
            listOf(schedule(createdAt = Instant.parse("2020-01-01T00:00:00Z"))),
            window,
            TEST_ZONE
        )

        assertTrue(result.size in 3_652..3_653)
    }
}
