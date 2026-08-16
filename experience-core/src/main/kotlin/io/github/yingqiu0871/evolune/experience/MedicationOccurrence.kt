package io.github.yingqiu0871.evolune.experience

import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class MedicationScheduleType {
    DAILY,
    WEEKLY,
    CUSTOM
}

enum class MedicationDoseUnit {
    MILLIGRAM
}

data class MedicationMatchKey(
    val routeKey: String,
    val medicationKey: String,
    val doseAmount: Double
) {
    init {
        require(routeKey.isNotBlank())
        require(medicationKey.isNotBlank())
        require(doseAmount.isFinite() && doseAmount >= 0.0)
    }
}

data class MedicationPresentation(
    val planName: String,
    val matchKey: MedicationMatchKey,
    val doseUnit: MedicationDoseUnit = MedicationDoseUnit.MILLIGRAM
) {
    init {
        require(planName.isNotBlank())
    }
}

data class MedicationScheduleSlot(
    val id: UUID,
    val localTime: LocalTime,
    val position: Int
) {
    init {
        require(position >= 0)
        require(localTime.second == 0 && localTime.nano == 0)
    }
}

/**
 * Immutable projection of an authoritative Phone plan for pure occurrence expansion.
 * It is an input value, not a persisted plan or independent state store.
 */
data class MedicationSchedule(
    val planId: UUID,
    val presentation: MedicationPresentation,
    val scheduleType: MedicationScheduleType,
    val slots: List<MedicationScheduleSlot>,
    val daysOfWeek: Set<DayOfWeek>,
    val intervalDays: Int,
    val enabled: Boolean,
    val createdAt: Instant
) {
    init {
        require(intervalDays >= 1)
        slots.forEachIndexed { index, slot ->
            require(slot.position == index) { "slot position must match list order" }
        }
    }
}

data class MedicationOccurrenceId(val value: UUID)

data class MedicationOccurrence(
    val id: MedicationOccurrenceId,
    val planId: UUID,
    val slotId: UUID,
    val slotPosition: Int,
    val presentation: MedicationPresentation,
    val scheduledAt: Instant,
    val scheduledLocalDateTime: LocalDateTime,
    val zoneId: ZoneId
)

/**
 * Presentation identity only. It never replaces DoseEvent.id.
 */
object MedicationOccurrenceIdentity {
    fun derive(
        planId: UUID,
        slotId: UUID,
        scheduledLocalDate: LocalDate
    ): MedicationOccurrenceId {
        val canonicalName = buildString {
            append("medication-occurrence:v1:plan=")
            append(planId)
            append(";slot=")
            append(slotId)
            append(";localDate=")
            append(scheduledLocalDate)
        }
        return MedicationOccurrenceId(
            UUID.nameUUIDFromBytes(canonicalName.toByteArray(StandardCharsets.UTF_8))
        )
    }
}
