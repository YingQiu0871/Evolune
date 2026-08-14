package io.github.yingqiu0871.evolune.data.mapper

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.SlotIdResult
import io.github.yingqiu0871.evolune.data.DoseEventEntity
import io.github.yingqiu0871.evolune.data.MedicationPlanAggregateEntity
import io.github.yingqiu0871.evolune.data.MedicationPlanEntity
import io.github.yingqiu0871.evolune.data.ScheduledDoseSlotEntity
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class V3PersistenceMapperTest {
    @Test
    fun doseEventRoundTripsEveryV3Field() {
        val event = DoseEvent(
            id = EVENT_ID,
            route = Route.INJECTION,
            occurredAt = Instant.ofEpochMilli(1_700_000_000_123L),
            zoneId = ZoneId.of("Asia/Shanghai"),
            localDate = LocalDate.of(2023, 11, 15),
            doseMG = 3.5,
            ester = Ester.EV,
            extras = mapOf(ExtraKey.CONCENTRATION_MG_ML to 20.0),
            slotId = SLOT_ID,
            source = DoseEventSource.WEAR,
            status = DoseEventStatus.RECORDED,
            revision = 7
        )

        val entity = (event.toV3Entity() as MappingResult.Success).value
        val roundTrip = (entity.toV3DomainDoseEvent() as MappingResult.Success).value

        assertEquals(event, roundTrip)
        assertEquals(1_700_000_000_123L, entity.occurredAtEpochMillis)
        assertEquals(1_700_000_000_123L / 3_600_000.0, entity.timeH, 0.0)
    }

    @Test
    fun subMillisecondOccurredAtIsRejected() {
        val result = validEvent().copy(
            occurredAt = Instant.ofEpochSecond(0, 1)
        ).toV3Entity()

        assertTrue((result as MappingResult.Failure).error is MappingError.InvalidOccurredAtPrecision)
    }

    @Test
    fun inconsistentLegacyAndAuthoritativeEventTimeIsRejected() {
        val result = validEventEntity().copy(
            occurredAtEpochMillis = 3_600_001L
        ).toV3DomainDoseEvent()

        assertTrue((result as MappingResult.Failure).error is MappingError.InconsistentEventTime)
    }

    @Test
    fun invalidEventMetadataStringsAreRejected() {
        assertTrue(
            ((validEventEntity().copy(zoneId = "Not/AZone").toV3DomainDoseEvent()
                as MappingResult.Failure).error) is MappingError.InvalidZoneId
        )
        assertTrue(
            ((validEventEntity().copy(localDate = "2024-02-30").toV3DomainDoseEvent()
                as MappingResult.Failure).error) is MappingError.InvalidLocalDate
        )
        assertTrue(
            ((validEventEntity().copy(source = "UNKNOWN").toV3DomainDoseEvent()
                as MappingResult.Failure).error) is MappingError.InvalidSource
        )
        assertTrue(
            ((validEventEntity().copy(status = "DELETED").toV3DomainDoseEvent()
                as MappingResult.Failure).error) is MappingError.InvalidStatus
        )
    }

    @Test
    fun medicationPlanRoundTripsOrderedDuplicateSlots() {
        val plan = validPlan(listOf(LocalTime.of(20, 0), LocalTime.of(8, 30), LocalTime.of(8, 30)))

        val persistence = (plan.toPersistenceAggregate() as MappingResult.Success).value
        val roundTrip = (
            MedicationPlanAggregateEntity(persistence.plan, persistence.slots)
                .toDomainMedicationPlan() as MappingResult.Success
            ).value

        assertEquals(plan, roundTrip)
        assertEquals(listOf("20:00", "08:30", "08:30"), persistence.plan.timeOfDay)
        assertEquals(listOf(0, 1, 2), persistence.slots.map { it.position })
    }

    @Test
    fun domainPlanWithUnexpectedSlotIdIsRejected() {
        val plan = validPlan(listOf(LocalTime.of(8, 30))).copy(
            slots = listOf(
                ScheduledDoseSlot(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000099"),
                    planId = PLAN_ID,
                    localTime = LocalTime.of(8, 30),
                    position = 0
                )
            )
        )

        val result = plan.toPersistenceAggregate()

        assertTrue((result as MappingResult.Failure).error is MappingError.UnexpectedSlotId)
    }

    @Test
    fun storedSlotForDifferentPlanIsRejected() {
        val aggregate = validAggregate().copy(
            slots = listOf(validSlotEntity().copy(planId = OTHER_PLAN_ID))
        )

        val result = aggregate.toDomainMedicationPlan()

        assertTrue((result as MappingResult.Failure).error is MappingError.InvalidSlotPlan)
    }

    @Test
    fun duplicateAndNonContiguousStoredPositionsAreRejected() {
        val duplicate = validAggregate(listOf(LocalTime.of(8, 30), LocalTime.of(12, 0))).copy(
            slots = listOf(
                validSlotEntity(0, LocalTime.of(8, 30)),
                validSlotEntity(0, LocalTime.of(12, 0))
            )
        )
        val nonContiguous = validAggregate(listOf(LocalTime.of(8, 30))).copy(
            slots = listOf(validSlotEntity(1, LocalTime.of(8, 30)))
        )

        assertTrue(
            ((duplicate.toDomainMedicationPlan() as MappingResult.Failure).error)
                is MappingError.InvalidSlotPosition
        )
        assertTrue(
            ((nonContiguous.toDomainMedicationPlan() as MappingResult.Failure).error)
                is MappingError.InvalidSlotPosition
        )
    }

    @Test
    fun nonCanonicalStoredSlotTimeIsRejected() {
        val aggregate = validAggregate().copy(
            slots = listOf(validSlotEntity().copy(localTime = "08:30:00"))
        )

        val result = aggregate.toDomainMedicationPlan()

        assertTrue((result as MappingResult.Failure).error is MappingError.InvalidSlotLocalTime)
    }

    @Test
    fun legacyPlanTimeMismatchIsRejectedWithoutRepair() {
        val aggregate = validAggregate().copy(
            plan = validPlanEntity(listOf("09:30"))
        )

        val result = aggregate.toDomainMedicationPlan()

        assertTrue((result as MappingResult.Failure).error is MappingError.InconsistentPlanTimes)
    }

    private fun validEvent(): DoseEvent = DoseEvent(
        id = EVENT_ID,
        route = Route.ORAL,
        occurredAt = Instant.ofEpochMilli(3_600_000L),
        doseMG = 1.0,
        ester = Ester.E2,
        source = DoseEventSource.MANUAL
    )

    private fun validEventEntity(): DoseEventEntity = DoseEventEntity(
        id = EVENT_ID,
        route = "ORAL",
        timeH = 1.0,
        doseMG = 1.0,
        ester = "E2",
        extras = emptyMap(),
        occurredAtEpochMillis = 3_600_000L,
        source = "MANUAL",
        status = "RECORDED",
        revision = 1
    )

    private fun validPlan(times: List<LocalTime>): MedicationPlan = MedicationPlan(
        id = PLAN_ID,
        name = "Synthetic plan",
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = ScheduleType.WEEKLY,
        slots = times.mapIndexed { position, time ->
            ScheduledDoseSlot(
                id = generatedSlotId(position, time),
                planId = PLAN_ID,
                localTime = time,
                position = position
            )
        },
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        intervalDays = 2,
        isEnabled = true,
        extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.4),
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )

    private fun validAggregate(
        times: List<LocalTime> = listOf(LocalTime.of(8, 30))
    ): MedicationPlanAggregateEntity = MedicationPlanAggregateEntity(
        plan = validPlanEntity(times.map { canonicalTime(it) }),
        slots = times.mapIndexed { position, time -> validSlotEntity(position, time) }
    )

    private fun validPlanEntity(times: List<String>): MedicationPlanEntity = MedicationPlanEntity(
        id = PLAN_ID,
        name = "Synthetic plan",
        route = "SUBLINGUAL",
        ester = "E2",
        doseMG = 2.0,
        scheduleType = "WEEKLY",
        timeOfDay = times,
        daysOfWeek = setOf(1, 5),
        intervalDays = 2,
        isEnabled = true,
        extras = mapOf("SUBLINGUAL_THETA" to 0.4),
        createdAt = 1_700_000_000_000L
    )

    private fun validSlotEntity(
        position: Int = 0,
        time: LocalTime = LocalTime.of(8, 30)
    ): ScheduledDoseSlotEntity = ScheduledDoseSlotEntity(
        id = generatedSlotId(position, time),
        planId = PLAN_ID,
        localTime = canonicalTime(time),
        position = position
    )

    private fun generatedSlotId(position: Int, time: LocalTime): UUID =
        (ScheduledDoseSlotId.generate(PLAN_ID, position, time) as SlotIdResult.Success).id

    private fun canonicalTime(time: LocalTime): String =
        time.hour.toString().padStart(2, '0') + ":" + time.minute.toString().padStart(2, '0')

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val SLOT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000102")
        val PLAN_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val OTHER_PLAN_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
