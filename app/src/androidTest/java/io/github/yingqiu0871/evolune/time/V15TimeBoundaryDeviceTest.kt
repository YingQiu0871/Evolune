package io.github.yingqiu0871.evolune.time

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.MedicationPresentation
import io.github.yingqiu0871.evolune.experience.MedicationSchedule
import io.github.yingqiu0871.evolune.experience.MedicationScheduleSlot
import io.github.yingqiu0871.evolune.experience.MedicationScheduleType
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Runs the deterministic date/time contracts on a real Android runtime. */
@RunWith(AndroidJUnit4::class)
class V15TimeBoundaryDeviceTest {
    @Test
    fun dateZoneAndDstBoundariesRemainStableOnDevice() {
        val crossingMidnight = occurrences(
            schedule(times = listOf(LocalTime.of(23, 30))),
            "2025-01-01T23:00:00Z",
            "2025-01-04T00:00:00Z",
            ZoneId.of("UTC")
        )
        assertEquals(3, crossingMidnight.size)
        assertEquals(
            listOf(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2025, 1, 3)
            ),
            crossingMidnight.map { it.scheduledLocalDateTime.toLocalDate() }
        )

        val paris = occurrences(
            schedule(),
            "2025-01-01T23:00:00Z",
            "2025-01-02T23:00:00Z",
            ZoneId.of("Europe/Paris")
        ).single()
        val shanghai = occurrences(
            schedule(),
            "2025-01-01T23:00:00Z",
            "2025-01-02T23:00:00Z",
            ZoneId.of("Asia/Shanghai")
        ).single()
        assertEquals(LocalTime.of(8, 0), paris.scheduledLocalDateTime.toLocalTime())
        assertEquals(LocalTime.of(8, 0), shanghai.scheduledLocalDateTime.toLocalTime())
        assertNotEquals(paris.scheduledAt, shanghai.scheduledAt)
        assertEquals(paris.id, shanghai.id)

        val springGap = occurrences(
            schedule(times = listOf(LocalTime.of(2, 30))),
            "2025-03-29T22:00:00Z",
            "2025-03-30T23:00:00Z",
            ZoneId.of("Europe/Paris")
        ).single()
        assertEquals(Instant.parse("2025-03-30T01:30:00Z"), springGap.scheduledAt)
        assertEquals(LocalDate.of(2025, 3, 30), springGap.scheduledLocalDateTime.toLocalDate())

        val fallOverlap = occurrences(
            schedule(times = listOf(LocalTime.of(2, 30))),
            "2025-10-25T22:00:00Z",
            "2025-10-26T23:00:00Z",
            ZoneId.of("Europe/Paris")
        )
        assertEquals(1, fallOverlap.size)
        assertEquals(Instant.parse("2025-10-26T00:30:00Z"), fallOverlap.single().scheduledAt)
        assertTrue(fallOverlap.single().scheduledLocalDateTime.toLocalDate().isEqual(LocalDate.of(2025, 10, 26)))
    }

    private fun occurrences(
        schedule: MedicationSchedule,
        start: String,
        end: String,
        zoneId: ZoneId
    ) = MedicationOccurrenceGenerator.generate(
        schedules = listOf(schedule),
        window = OccurrenceGenerationWindow(Instant.parse(start), Instant.parse(end)),
        zoneId = zoneId
    )

    private fun schedule(
        times: List<LocalTime> = listOf(LocalTime.of(8, 0))
    ): MedicationSchedule {
        val planId = UUID.fromString("94000000-0000-0000-0000-000000000001")
        return MedicationSchedule(
            planId = planId,
            presentation = MedicationPresentation(
                planName = "v1.5 time fixture",
                matchKey = MedicationMatchKey("ORAL", "E2", 1.0)
            ),
            scheduleType = MedicationScheduleType.DAILY,
            slots = times.mapIndexed { position, time ->
                MedicationScheduleSlot(
                    id = UUID(0L, (position + 1).toLong()),
                    localTime = time,
                    position = position
                )
            },
            daysOfWeek = DayOfWeek.entries.toSet(),
            intervalDays = 1,
            enabled = true,
            createdAt = Instant.parse("2025-01-01T00:00:00Z")
        )
    }
}
