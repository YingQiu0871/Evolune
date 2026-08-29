package io.github.yingqiu0871.evolune.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MedicationOccurrencePresentationTest {
    private val now = Instant.parse("2025-01-02T10:00:00Z")
    private val plan = schedule(times = listOf(java.time.LocalTime.of(10, 0)))

    @Test
    fun `status and action availability derive around the due window`() {
        val generated = occurrences(
            listOf(plan),
            "2024-12-31T00:00:00Z",
            "2025-01-05T00:00:00Z"
        )
        val items = MedicationOccurrencePresentation.derive(generated, emptyList(), now)

        assertEquals(
            listOf(
                MedicationOccurrenceStatus.PAST_UNRECORDED,
                MedicationOccurrenceStatus.DUE,
                MedicationOccurrenceStatus.UPCOMING,
                MedicationOccurrenceStatus.UPCOMING
            ),
            items.map { it.status }
        )
        assertEquals(
            listOf(
                MedicationActionAvailability.WINDOW_EXPIRED,
                MedicationActionAvailability.AVAILABLE,
                MedicationActionAvailability.NOT_YET_DUE,
                MedicationActionAvailability.NOT_YET_DUE
            ),
            items.map { it.actionAvailability }
        )
    }

    @Test
    fun `matching accepted event marks recorded and exposes only real event id`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val eventId = UUID(9L, 1L)
        val item = MedicationOccurrencePresentation.derive(
            occurrences = listOf(occurrence),
            recordedEvents = listOf(
                RecordedMedicationEvent(
                    eventId = eventId,
                    occurredAt = now.plusSeconds(30 * 60L),
                    slotId = null,
                    matchKey = occurrence.presentation.matchKey
                )
            ),
            now = now
        ).single()

        assertEquals(MedicationOccurrenceStatus.RECORDED, item.status)
        assertEquals(MedicationActionAvailability.ALREADY_RECORDED, item.actionAvailability)
        assertEquals(eventId, item.recordedEventId)
    }

    @Test
    fun `unrelated event does not falsely record occurrence`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val item = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(
                RecordedMedicationEvent(
                    eventId = UUID(9L, 2L),
                    occurredAt = now,
                    slotId = null,
                    matchKey = occurrence.presentation.matchKey.copy(routeKey = "INJECTION")
                )
            ),
            now
        ).single()

        assertEquals(MedicationOccurrenceStatus.DUE, item.status)
        assertNull(item.recordedEventId)
    }

    @Test
    fun `proven slot identity cannot fall back to a different slot`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val item = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(
                RecordedMedicationEvent(
                    eventId = UUID(9L, 3L),
                    occurredAt = now,
                    slotId = UUID(8L, 8L),
                    matchKey = occurrence.presentation.matchKey
                )
            ),
            now
        ).single()

        assertEquals(MedicationOccurrenceStatus.DUE, item.status)
    }

    @Test
    fun `exact slot identity takes precedence over medication fallback`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val exactId = UUID(9L, 4L)
        val fallbackId = UUID(9L, 5L)
        val item = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(
                RecordedMedicationEvent(
                    eventId = fallbackId,
                    occurredAt = now,
                    slotId = null,
                    matchKey = occurrence.presentation.matchKey
                ),
                RecordedMedicationEvent(
                    eventId = exactId,
                    occurredAt = now.plusSeconds(50 * 60L),
                    slotId = occurrence.slotId,
                    matchKey = occurrence.presentation.matchKey.copy(routeKey = "LEGACY")
                )
            ),
            now
        ).single()

        assertEquals(exactId, item.recordedEventId)
    }

    @Test
    fun `ambiguous null-slot event does not record either simultaneous occurrence`() {
        val simultaneous = occurrences(
            listOf(schedule(number = 1, times = listOf(java.time.LocalTime.of(10, 0))), schedule(number = 2, times = listOf(java.time.LocalTime.of(10, 0)))),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        )
        val items = MedicationOccurrencePresentation.derive(
            simultaneous,
            listOf(
                RecordedMedicationEvent(
                    eventId = UUID(9L, 6L),
                    occurredAt = now,
                    slotId = null,
                    matchKey = simultaneous.first().presentation.matchKey,
                    localDate = LocalDate.parse("2025-01-02")
                )
            ),
            now
        )

        assertEquals(0, items.count { it.status == MedicationOccurrenceStatus.RECORDED })
        assertEquals(2, items.count { it.status == MedicationOccurrenceStatus.DUE })
    }

    @Test
    fun `null-slot event compatible with staggered occurrences remains unmatched`() {
        val candidates = occurrences(
            listOf(
                schedule(number = 1, times = listOf(java.time.LocalTime.of(9, 0))),
                schedule(number = 2, times = listOf(java.time.LocalTime.of(9, 30)))
            ),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        )
        val items = MedicationOccurrencePresentation.derive(
            candidates,
            listOf(
                RecordedMedicationEvent(
                    eventId = UUID(9L, 12L),
                    occurredAt = Instant.parse("2025-01-02T09:20:00Z"),
                    slotId = null,
                    matchKey = candidates.first().presentation.matchKey
                )
            ),
            now
        )

        assertEquals(0, items.count { it.status == MedicationOccurrenceStatus.RECORDED })
        assertEquals(2, items.count { it.status == MedicationOccurrenceStatus.DUE })
    }

    @Test
    fun `null-slot time-window match takes precedence over same-day fallback`() {
        val candidates = occurrences(
            listOf(
                schedule(number = 1, times = listOf(java.time.LocalTime.of(9, 0))),
                schedule(number = 2, times = listOf(java.time.LocalTime.of(17, 0)))
            ),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        )
        val eventId = UUID(9L, 28L)
        val occurredAt = Instant.parse("2025-01-02T09:20:00Z")
        val items = MedicationOccurrencePresentation.derive(
            candidates,
            listOf(
                RecordedMedicationEvent(
                    eventId = eventId,
                    occurredAt = occurredAt,
                    slotId = null,
                    matchKey = candidates.first().presentation.matchKey,
                    localDate = LocalDate.parse("2025-01-02")
                )
            ),
            occurredAt
        )

        val morning = items.single {
            it.occurrence.scheduledLocalDateTime.toLocalTime() == java.time.LocalTime.of(9, 0)
        }
        val evening = items.single {
            it.occurrence.scheduledLocalDateTime.toLocalTime() == java.time.LocalTime.of(17, 0)
        }

        assertEquals(MedicationOccurrenceStatus.RECORDED, morning.status)
        assertEquals(eventId, morning.recordedEventId)
        assertEquals(MedicationOccurrenceStatus.UPCOMING, evening.status)
        assertNull(evening.recordedEventId)
    }

    @Test
    fun `unique null-slot candidate records the only compatible occurrence`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val eventId = UUID(9L, 8L)

        val item = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(
                RecordedMedicationEvent(
                    eventId = eventId,
                    occurredAt = now.plusSeconds(30 * 60L),
                    slotId = null,
                    matchKey = occurrence.presentation.matchKey
                )
            ),
            now
        ).single()

        assertEquals(MedicationOccurrenceStatus.RECORDED, item.status)
        assertEquals(eventId, item.recordedEventId)
    }

    @Test
    fun `fallback cannot reuse exact matched occurrence`() {
        val simultaneous = occurrences(
            listOf(
                schedule(number = 1, times = listOf(java.time.LocalTime.of(10, 0))),
                schedule(number = 2, times = listOf(java.time.LocalTime.of(10, 0)))
            ),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        )
        val exactId = UUID(9L, 9L)
        val fallbackId = UUID(9L, 10L)
        val items = MedicationOccurrencePresentation.derive(
            simultaneous,
            listOf(
                RecordedMedicationEvent(
                    eventId = exactId,
                    occurredAt = now,
                    slotId = simultaneous.first().slotId,
                    matchKey = simultaneous.first().presentation.matchKey
                ),
                RecordedMedicationEvent(
                    eventId = fallbackId,
                    occurredAt = now,
                    slotId = null,
                    matchKey = simultaneous.first().presentation.matchKey
                )
            ),
            now
        )

        assertEquals(exactId, items.first { it.occurrence.slotId == simultaneous.first().slotId }.recordedEventId)
        assertEquals(fallbackId, items.first { it.occurrence.slotId == simultaneous.last().slotId }.recordedEventId)
        assertEquals(2, items.count { it.status == MedicationOccurrenceStatus.RECORDED })
    }

    @Test
    fun `fallback ambiguity is independent of event input ordering`() {
        val simultaneous = occurrences(
            listOf(
                schedule(number = 1, times = listOf(java.time.LocalTime.of(10, 0))),
                schedule(number = 2, times = listOf(java.time.LocalTime.of(10, 0)))
            ),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        )
        val event = RecordedMedicationEvent(
            eventId = UUID(9L, 11L),
            occurredAt = now,
            slotId = null,
            matchKey = simultaneous.first().presentation.matchKey,
            localDate = LocalDate.parse("2025-01-02")
        )

        val forward = MedicationOccurrencePresentation.derive(simultaneous, listOf(event), now)
        val reverse = MedicationOccurrencePresentation.derive(simultaneous.reversed(), listOf(event), now)

        assertEquals(
            forward.map { it.status to it.recordedEventId },
            reverse.map { it.status to it.recordedEventId }
        )
        assertEquals(0, forward.count { it.status == MedicationOccurrenceStatus.RECORDED })
    }

    @Test
    fun `event outside accepted matching window does not record occurrence`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val item = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(
                RecordedMedicationEvent(
                    eventId = UUID(9L, 7L),
                    occurredAt = now.plusSeconds(3_601L),
                    slotId = null,
                    matchKey = occurrence.presentation.matchKey
                )
            ),
            now
        ).single()

        assertEquals(MedicationOccurrenceStatus.DUE, item.status)
    }

    @Test
    fun `same-day null-slot event after one hour still records unique occurrence`() {
        val occurrence = occurrences(
            listOf(schedule(times = listOf(java.time.LocalTime.of(9, 0)))),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        ).single()
        val occurredAt = Instant.parse("2025-01-02T10:01:00Z")
        val eventId = UUID(9L, 18L)

        val item = MedicationOccurrencePresentation.derive(
            occurrences = listOf(occurrence),
            recordedEvents = listOf(
                RecordedMedicationEvent(
                    eventId = eventId,
                    occurredAt = occurredAt,
                    slotId = null,
                    matchKey = occurrence.presentation.matchKey,
                    localDate = LocalDate.parse("2025-01-02")
                )
            ),
            now = occurredAt
        ).single()

        assertEquals(MedicationOccurrenceStatus.RECORDED, item.status)
        assertEquals(eventId, item.recordedEventId)
    }

    @Test
    fun `same-day null-slot event with a large delay preserves actual event time`() {
        val occurrence = occurrences(
            listOf(schedule(times = listOf(java.time.LocalTime.of(9, 0)))),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        ).single()
        val occurredAt = Instant.parse("2025-01-02T18:45:00Z")
        val eventId = UUID(9L, 19L)
        val event = RecordedMedicationEvent(
            eventId = eventId,
            occurredAt = occurredAt,
            slotId = null,
            matchKey = occurrence.presentation.matchKey,
            localDate = LocalDate.parse("2025-01-02")
        )

        val item = MedicationOccurrencePresentation.derive(
            occurrences = listOf(occurrence),
            recordedEvents = listOf(event),
            now = occurredAt
        ).single()

        assertEquals(MedicationOccurrenceStatus.RECORDED, item.status)
        assertEquals(eventId, item.recordedEventId)
        assertEquals(occurredAt, event.occurredAt)
    }

    @Test
    fun `same-day null-slot fallback rejects an event from another local date`() {
        val occurrence = occurrences(
            listOf(schedule(times = listOf(java.time.LocalTime.of(9, 0)))),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        ).single()
        val occurredAt = Instant.parse("2025-01-03T10:01:00Z")

        val item = MedicationOccurrencePresentation.derive(
            occurrences = listOf(occurrence),
            recordedEvents = listOf(
                RecordedMedicationEvent(
                    eventId = UUID(9L, 22L),
                    occurredAt = occurredAt,
                    slotId = null,
                    matchKey = occurrence.presentation.matchKey,
                    localDate = LocalDate.parse("2025-01-03")
                )
            ),
            now = occurredAt
        ).single()

        assertEquals(MedicationOccurrenceStatus.PAST_UNRECORDED, item.status)
        assertNull(item.recordedEventId)
    }

    @Test
    fun `same-day null-slot fallback keeps ambiguous matching unresolved`() {
        val simultaneous = occurrences(
            listOf(
                schedule(number = 1, times = listOf(java.time.LocalTime.of(9, 0))),
                schedule(number = 2, times = listOf(java.time.LocalTime.of(9, 0)))
            ),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        )
        val occurredAt = Instant.parse("2025-01-02T18:00:00Z")
        val items = MedicationOccurrencePresentation.derive(
            occurrences = simultaneous,
            recordedEvents = listOf(
                RecordedMedicationEvent(
                    eventId = UUID(9L, 23L),
                    occurredAt = occurredAt,
                    slotId = null,
                    matchKey = simultaneous.first().presentation.matchKey,
                    localDate = LocalDate.parse("2025-01-02")
                )
            ),
            now = occurredAt
        )

        assertEquals(0, items.count { it.status == MedicationOccurrenceStatus.RECORDED })
        assertEquals(2, items.count { it.status == MedicationOccurrenceStatus.PAST_UNRECORDED })
    }

    @Test
    fun `multiple same-day null-slot events choose one deterministically`() {
        val occurrence = occurrences(
            listOf(schedule(times = listOf(java.time.LocalTime.of(9, 0)))),
            "2025-01-02T00:00:00Z",
            "2025-01-03T00:00:00Z"
        ).single()
        val earlier = RecordedMedicationEvent(
            eventId = UUID(9L, 24L),
            occurredAt = Instant.parse("2025-01-02T18:00:00Z"),
            slotId = null,
            matchKey = occurrence.presentation.matchKey,
            localDate = LocalDate.parse("2025-01-02")
        )
        val later = earlier.copy(
            eventId = UUID(9L, 25L),
            occurredAt = Instant.parse("2025-01-02T19:00:00Z")
        )

        val forward = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(later, earlier),
            later.occurredAt
        ).single()
        val reverse = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(earlier, later),
            later.occurredAt
        ).single()

        assertEquals(earlier.eventId, forward.recordedEventId)
        assertEquals(forward.recordedEventId, reverse.recordedEventId)
    }

    @Test
    fun `exact slot and date match reserves occurrence from null-slot fallback`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val exactId = UUID(9L, 26L)
        val fallbackId = UUID(9L, 27L)
        val occurredAt = Instant.parse("2025-01-02T18:00:00Z")
        val items = MedicationOccurrencePresentation.derive(
            occurrences = listOf(occurrence),
            recordedEvents = listOf(
                RecordedMedicationEvent(
                    eventId = exactId,
                    occurredAt = occurredAt,
                    slotId = occurrence.slotId,
                    localDate = LocalDate.parse("2025-01-02"),
                    matchKey = occurrence.presentation.matchKey.copy(routeKey = "LEGACY")
                ),
                RecordedMedicationEvent(
                    eventId = fallbackId,
                    occurredAt = occurredAt.plusSeconds(60L),
                    slotId = null,
                    localDate = LocalDate.parse("2025-01-02"),
                    matchKey = occurrence.presentation.matchKey
                )
            ),
            now = occurredAt
        )

        assertEquals(exactId, items.single().recordedEventId)
    }

    @Test
    fun `slot and local date exactly associate a late actual dose time`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val eventId = UUID(9L, 20L)
        val item = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(
                RecordedMedicationEvent(
                    eventId = eventId,
                    occurredAt = now.plusSeconds(5 * 3_600L),
                    slotId = occurrence.slotId,
                    localDate = LocalDate.parse("2025-01-02"),
                    matchKey = occurrence.presentation.matchKey.copy(routeKey = "LEGACY")
                )
            ),
            now.plusSeconds(5 * 3_600L)
        ).single()

        assertEquals(MedicationOccurrenceStatus.RECORDED, item.status)
        assertEquals(eventId, item.recordedEventId)
    }

    @Test
    fun `slot date exact association rejects another slot or local date`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        fun derive(slotId: UUID, localDate: LocalDate) =
            MedicationOccurrencePresentation.derive(
                listOf(occurrence),
                listOf(
                    RecordedMedicationEvent(
                        eventId = UUID(9L, localDate.dayOfMonth.toLong()),
                        occurredAt = now.plusSeconds(5 * 3_600L),
                        slotId = slotId,
                        localDate = localDate,
                        matchKey = occurrence.presentation.matchKey
                    )
                ),
                now.plusSeconds(5 * 3_600L)
            ).single()

        assertEquals(
            MedicationOccurrenceStatus.PAST_UNRECORDED,
            derive(UUID(8L, 20L), LocalDate.parse("2025-01-02")).status
        )
        assertEquals(
            MedicationOccurrenceStatus.PAST_UNRECORDED,
            derive(occurrence.slotId, LocalDate.parse("2025-01-03")).status
        )
    }

    @Test
    fun `slot event without local date retains the legacy time window`() {
        val occurrence = occurrenceAt("2025-01-02T10:00:00Z")
        val item = MedicationOccurrencePresentation.derive(
            listOf(occurrence),
            listOf(
                RecordedMedicationEvent(
                    eventId = UUID(9L, 21L),
                    occurredAt = now.plusSeconds(5 * 3_600L),
                    slotId = occurrence.slotId,
                    localDate = null,
                    matchKey = occurrence.presentation.matchKey
                )
            ),
            now.plusSeconds(5 * 3_600L)
        ).single()

        assertEquals(MedicationOccurrenceStatus.PAST_UNRECORDED, item.status)
        assertNull(item.recordedEventId)
    }

    private fun occurrenceAt(instant: String): MedicationOccurrence = occurrences(
        listOf(plan),
        Instant.parse(instant).minusSeconds(1L).toString(),
        Instant.parse(instant).plusSeconds(1L).toString()
    ).single()
}
