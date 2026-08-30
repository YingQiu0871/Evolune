package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class WearAppSnapshotBuilderTest {
    private val now = Instant.parse("2026-08-30T10:00:00Z")
    private val zone = ZoneId.of("UTC")
    private val producerIdentity = WearAppProducerIdentity(
        producerInstanceId = UUID(0L, 9L),
        producerGeneration = 1L
    )

    @Test
    fun `recent dose uses real event id and is independent of input order`() {
        val older = doseEvent(UUID(0L, 1L), now.minusSeconds(60L))
        val latest = doseEvent(UUID(0L, 2L), now)

        val first = build(events = listOf(older, latest))
        val reversed = build(events = listOf(latest, older))

        assertEquals(latest.id, first.recentDose?.eventId)
        assertEquals(first.recentDose, reversed.recentDose)
    }

    @Test
    fun `legacy null slot recent dose remains displayable`() {
        val event = doseEvent(UUID(0L, 21L), now).copy(slotId = null, localDate = null)

        val recent = build(events = listOf(event)).recentDose

        assertNotNull(recent)
        assertEquals(event.id, recent!!.eventId)
        assertEquals(null, recent.slotId)
    }

    @Test
    fun `same timestamp uses stable event id tie break`() {
        val lower = doseEvent(UUID(0L, 10L), now)
        val higher = doseEvent(UUID(0L, 11L), now)

        val snapshot = build(events = listOf(higher, lower))

        assertEquals(higher.id, snapshot.recentDose?.eventId)
    }

    @Test
    fun `plan and event input order does not alter the snapshot`() {
        val firstPlan = plan(UUID(6L, 1L))
        val secondPlan = plan(UUID(6L, 2L))
        val firstEvent = doseEvent(UUID(0L, 31L), now.minusSeconds(60L))
        val secondEvent = doseEvent(UUID(0L, 32L), now)

        val first = WearAppSnapshotBuilder.build(
            plans = listOf(firstPlan, secondPlan),
            events = listOf(firstEvent, secondEvent),
            generatedAt = now,
            zoneId = zone,
            snapshotRevision = 1L,
            currentConcentration = null,
            producerIdentity = producerIdentity
        )
        val reversed = WearAppSnapshotBuilder.build(
            plans = listOf(secondPlan, firstPlan),
            events = listOf(secondEvent, firstEvent),
            generatedAt = now,
            zoneId = zone,
            snapshotRevision = 1L,
            currentConcentration = null,
            producerIdentity = producerIdentity
        )

        assertEquals(first, reversed)
    }

    @Test
    fun `upcoming is sorted capped at five and excludes recorded occurrence`() {
        val plan = plan(
            id = UUID(1L, 1L),
            slots = (0..5).map { position ->
                ScheduledDoseSlot(
                    id = UUID(10L, position.toLong() + 1L),
                    planId = UUID(1L, 1L),
                    localTime = LocalTime.of(10 + position, 0),
                    position = position
                )
            }
        )
        val recordedSlot = plan.slots.first()
        val recorded = DoseEvent(
            id = UUID(0L, 99L),
            route = plan.route,
            occurredAt = now,
            zoneId = zone,
            localDate = LocalDate.of(2026, 8, 30),
            doseMG = plan.doseMG,
            ester = plan.ester,
            slotId = recordedSlot.id,
            source = DoseEventSource.MANUAL
        )

        val snapshot = WearAppSnapshotBuilder.build(
            plans = listOf(plan),
            events = listOf(recorded),
            generatedAt = now,
            zoneId = zone,
            snapshotRevision = 1L,
            currentConcentration = null,
            producerIdentity = producerIdentity
        )

        assertEquals(WearAppSnapshotRules.MAX_UPCOMING_OCCURRENCES, snapshot.upcomingOccurrences.size)
        assertTrue(snapshot.upcomingOccurrences.zipWithNext().all { (left, right) ->
            left.scheduledAt < right.scheduledAt
        })
        assertFalse(snapshot.upcomingOccurrences.any { it.slotId == recordedSlot.id })
        assertEquals(WearAppConcentrationStatus.EMPTY, snapshot.concentrationState.status)
        assertEquals(null, snapshot.concentrationState.value)
    }

    @Test
    fun `fewer upcoming occurrences are returned without padding`() {
        val snapshot = build(
            plans = listOf(
                plan(
                    id = UUID(2L, 1L),
                    scheduleType = ScheduleType.CUSTOM,
                    intervalDays = 200,
                    slots = listOf(
                        ScheduledDoseSlot(
                            id = UUID(20L, 1L),
                            planId = UUID(2L, 1L),
                            localTime = LocalTime.of(11, 0),
                            position = 0
                        )
                    )
                )
            )
        )

        assertTrue(snapshot.upcomingOccurrences.isNotEmpty())
        assertTrue(snapshot.upcomingOccurrences.size < WearAppSnapshotRules.MAX_UPCOMING_OCCURRENCES)
        assertNotNull(snapshot.upcomingOccurrences.first().occurrenceId)
    }

    @Test
    fun `disabled plans produce an empty snapshot`() {
        val snapshot = build(
            plans = listOf(plan(UUID(3L, 1L), enabled = false))
        )

        assertEquals(0, snapshot.upcomingOccurrences.size)
        assertEquals(io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus.EMPTY, snapshot.overallStatus)
    }

    @Test
    fun `phone builder payload is consumable by the shared Wear codec`() {
        val snapshot = build()

        assertEquals(snapshot, WearAppSnapshotCodec.decode(WearAppSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun `invalid concentration values stay empty`() {
        val snapshot = WearAppSnapshotBuilder.build(
            plans = listOf(plan(UUID(5L, 1L))),
            events = emptyList(),
            generatedAt = now,
            zoneId = zone,
            snapshotRevision = 1L,
            currentConcentration = Double.NaN,
            producerIdentity = producerIdentity
        )

        assertEquals(WearAppConcentrationStatus.EMPTY, snapshot.concentrationState.status)
        assertEquals(null, snapshot.concentrationState.value)
    }

    @Test
    fun `cached concentration does not invent a calculated timestamp`() {
        val snapshot = WearAppSnapshotBuilder.build(
            plans = listOf(plan(UUID(8L, 1L))),
            events = emptyList(),
            generatedAt = now,
            zoneId = zone,
            snapshotRevision = 1L,
            currentConcentration = 12.5,
            producerIdentity = producerIdentity
        )

        assertEquals(WearAppConcentrationStatus.AVAILABLE, snapshot.concentrationState.status)
        assertEquals(12.5, requireNotNull(snapshot.concentrationState.value), 0.0)
        assertEquals(null, snapshot.concentrationState.calculatedAt)
    }

    @Test
    fun `DST gap keeps phone local date while resolving scheduled instant`() {
        val dstPlan = plan(
            id = UUID(7L, 1L),
            slots = listOf(
                ScheduledDoseSlot(
                    id = UUID(70L, 1L),
                    planId = UUID(7L, 1L),
                    localTime = LocalTime.of(2, 30),
                    position = 0
                )
            ),
            createdAt = Instant.parse("2026-03-01T00:00:00Z")
        )

        val snapshot = WearAppSnapshotBuilder.build(
            plans = listOf(dstPlan),
            events = emptyList(),
            generatedAt = Instant.parse("2026-03-08T06:00:00Z"),
            zoneId = ZoneId.of("America/New_York"),
            snapshotRevision = 1L,
            currentConcentration = null,
            producerIdentity = producerIdentity
        )

        assertEquals(LocalDate.of(2026, 3, 8), snapshot.upcomingOccurrences.first().localDate)
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z"),
            snapshot.upcomingOccurrences.first().scheduledAt
        )
    }

    private fun build(
        plans: List<MedicationPlan> = listOf(plan(UUID(4L, 1L))),
        events: List<DoseEvent> = emptyList()
    ) = WearAppSnapshotBuilder.build(
        plans = plans,
        events = events,
        generatedAt = now,
        zoneId = zone,
        snapshotRevision = 1L,
        currentConcentration = null,
        producerIdentity = producerIdentity
    )

    private fun doseEvent(id: UUID, occurredAt: Instant) = DoseEvent(
        id = id,
        route = io.github.yingqiu0871.evolune.pk.Route.ORAL,
        occurredAt = occurredAt,
        doseMG = 2.0,
        ester = io.github.yingqiu0871.evolune.pk.Ester.E2,
        source = DoseEventSource.MANUAL
    )

    private fun plan(
        id: UUID,
        slots: List<ScheduledDoseSlot> = listOf(
            ScheduledDoseSlot(
                id = UUID(id.mostSignificantBits + 100L, id.leastSignificantBits),
                planId = id,
                localTime = LocalTime.of(12, 0),
                position = 0
            )
        ),
        enabled: Boolean = true,
        scheduleType: ScheduleType = ScheduleType.DAILY,
        intervalDays: Int = 1,
        createdAt: Instant = Instant.parse("2026-08-01T00:00:00Z")
    ) = MedicationPlan(
        id = id,
        name = "E2 plan ${id.leastSignificantBits}",
        route = io.github.yingqiu0871.evolune.pk.Route.ORAL,
        ester = io.github.yingqiu0871.evolune.pk.Ester.E2,
        doseMG = 2.0,
        scheduleType = scheduleType,
        slots = slots,
        daysOfWeek = emptySet(),
        intervalDays = intervalDays,
        isEnabled = enabled,
        extras = emptyMap(),
        createdAt = createdAt
    )
}
