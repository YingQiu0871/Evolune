package io.github.yingqiu0871.evolune.data.migration

import io.github.yingqiu0871.evolune.data.Converters
import io.github.yingqiu0871.evolune.data.DoseEventEntity
import io.github.yingqiu0871.evolune.data.MedicationPlanAggregateEntity
import io.github.yingqiu0871.evolune.data.MedicationPlanEntity
import io.github.yingqiu0871.evolune.data.ScheduledDoseSlotEntity
import io.github.yingqiu0871.evolune.data.mapper.MappingError
import io.github.yingqiu0871.evolune.data.mapper.MappingResult
import io.github.yingqiu0871.evolune.data.mapper.toDomainMedicationPlan
import io.github.yingqiu0871.evolune.data.mapper.toV3DomainDoseEvent
import java.util.UUID

internal data class LegacyEventValues(
    val id: UUID,
    val route: String,
    val timeH: Double,
    val doseMG: Double,
    val ester: String,
    val extrasPayload: String,
    val occurredAtEpochMillis: Long
)

internal data class LegacyPlanValues(
    val id: UUID,
    val name: String,
    val route: String,
    val ester: String,
    val doseMG: Double,
    val scheduleType: String,
    val timeOfDayPayload: String,
    val daysOfWeekPayload: String,
    val intervalDays: Int,
    val isEnabled: Boolean,
    val extrasPayload: String,
    val createdAt: Long,
    val slots: List<LegacySlotValues>
)

internal data class LegacySlotValues(
    val id: UUID,
    val planId: UUID,
    val localTime: String,
    val position: Int
)

internal object LegacyAggregatePreflight {
    private val converters = Converters()

    fun requireReadable(event: LegacyEventValues) {
        val extras = decode(event.extrasPayload, "extras", converters::toMap)
        val entity = DoseEventEntity(
            id = event.id,
            route = event.route,
            timeH = event.timeH,
            doseMG = event.doseMG,
            ester = event.ester,
            extras = extras,
            occurredAtEpochMillis = event.occurredAtEpochMillis,
            zoneId = null,
            localDate = null,
            slotId = null,
            source = "LEGACY",
            status = "RECORDED",
            revision = 1L
        )
        when (val result = entity.toV3DomainDoseEvent()) {
            is MappingResult.Success -> Unit
            is MappingResult.Failure -> reject(result.error.fieldName())
        }
    }

    fun requireReadable(plan: LegacyPlanValues) {
        val times = decode(plan.timeOfDayPayload, "timeOfDay", converters::toStringList)
        val days = decode(plan.daysOfWeekPayload, "daysOfWeek", converters::toIntSet)
        val extras = decode(plan.extrasPayload, "extras", converters::toMap)
        val entity = MedicationPlanEntity(
            id = plan.id,
            name = plan.name,
            route = plan.route,
            ester = plan.ester,
            doseMG = plan.doseMG,
            scheduleType = plan.scheduleType,
            timeOfDay = times,
            daysOfWeek = days,
            intervalDays = plan.intervalDays,
            isEnabled = plan.isEnabled,
            extras = extras,
            createdAt = plan.createdAt
        )
        val aggregate = MedicationPlanAggregateEntity(
            plan = entity,
            slots = plan.slots.map { slot ->
                ScheduledDoseSlotEntity(
                    id = slot.id,
                    planId = slot.planId,
                    localTime = slot.localTime,
                    position = slot.position
                )
            }
        )
        when (val result = aggregate.toDomainMedicationPlan()) {
            is MappingResult.Success -> Unit
            is MappingResult.Failure -> reject(result.error.fieldName())
        }
    }

    private inline fun <T> decode(
        payload: String,
        field: String,
        decoder: (String) -> T
    ): T = try {
        decoder(payload)
    } catch (_: RuntimeException) {
        throw LegacyAggregatePreflightException(
            field = field,
            reason = PersistedValueFailure.CONVERTER_REJECTED
        )
    }

    private fun reject(field: String): Nothing = throw LegacyAggregatePreflightException(
        field = field,
        reason = PersistedValueFailure.MAPPER_REJECTED
    )
}

internal class LegacyAggregatePreflightException(
    val field: String,
    val reason: PersistedValueFailure
) : RuntimeException("Legacy aggregate preflight rejected a persisted field")

private fun MappingError.fieldName(): String = when (this) {
    is MappingError.InvalidTimeH,
    is MappingError.InvalidOccurredAtPrecision,
    is MappingError.InconsistentEventTime -> "timeH"
    is MappingError.InvalidRoute -> "route"
    is MappingError.InvalidEster -> "ester"
    is MappingError.InvalidExtraKey -> "extras"
    is MappingError.InvalidScheduleType -> "scheduleType"
    is MappingError.InvalidTimeOfDay,
    is MappingError.InconsistentPlanTimes -> "timeOfDay"
    is MappingError.InvalidDayOfWeek -> "daysOfWeek"
    is MappingError.InvalidCreatedAt -> "createdAt"
    is MappingError.InvalidDoseEventInvariant -> "doseEvent"
    is MappingError.InvalidPlanInvariant -> "intervalDays"
    is MappingError.InvalidZoneId -> "zoneId"
    is MappingError.InvalidLocalDate -> "localDate"
    is MappingError.InvalidSource -> "source"
    is MappingError.InvalidStatus -> "status"
    is MappingError.InvalidSlot,
    is MappingError.InvalidSlotPlan,
    is MappingError.InvalidSlotPosition,
    is MappingError.InvalidSlotLocalTime,
    is MappingError.UnexpectedSlotId -> "slots"
}
