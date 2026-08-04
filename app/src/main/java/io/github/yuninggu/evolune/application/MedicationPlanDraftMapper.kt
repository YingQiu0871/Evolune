package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlot
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlotId
import io.github.yuninggu.evolune.core.model.SlotIdResult
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

data class MedicationPlanDraft(
    val id: UUID,
    val name: String,
    val route: Route,
    val ester: Ester,
    val doseMG: Double,
    val scheduleType: ScheduleType,
    val times: List<LocalTime>,
    val daysOfWeek: Set<DayOfWeek>,
    val intervalDays: Int,
    val isEnabled: Boolean,
    val extras: Map<ExtraKey, Double>,
    val createdAt: Instant
)

sealed interface DraftMappingResult<out T> {
    data class Success<T>(val value: T) : DraftMappingResult<T>

    data class InvalidDraft(
        val issues: List<DraftIssue>
    ) : DraftMappingResult<Nothing>
}

sealed interface DraftIssue {
    data class MissingRequiredField(val field: DraftField) : DraftIssue
    data class NonMinuteTime(val position: Int) : DraftIssue
    data class SlotIdMismatch(val position: Int) : DraftIssue
    data class SlotIdGenerationFailure(val position: Int) : DraftIssue
    data object DomainValidationFailure : DraftIssue
}

enum class DraftField {
    NAME
}

fun MedicationPlanDraft.toDomainMedicationPlan(): DraftMappingResult<MedicationPlan> {
    val issues = mutableListOf<DraftIssue>()
    if (name.isBlank()) {
        issues += DraftIssue.MissingRequiredField(DraftField.NAME)
    }

    val slots = buildList {
        times.forEachIndexed { position, localTime ->
            if (!localTime.hasMinutePrecision()) {
                issues += DraftIssue.NonMinuteTime(position)
                return@forEachIndexed
            }
            when (val result = ScheduledDoseSlotId.generate(id, position, localTime)) {
                is SlotIdResult.Success -> add(
                    ScheduledDoseSlot(
                        id = result.id,
                        planId = id,
                        localTime = localTime,
                        position = position
                    )
                )
                is SlotIdResult.Failure -> {
                    issues += DraftIssue.SlotIdGenerationFailure(position)
                }
            }
        }
    }

    if (issues.isNotEmpty()) {
        return DraftMappingResult.InvalidDraft(issues)
    }

    return try {
        DraftMappingResult.Success(
            MedicationPlan(
                id = id,
                name = name,
                route = route,
                ester = ester,
                doseMG = doseMG,
                scheduleType = scheduleType,
                slots = slots,
                daysOfWeek = daysOfWeek,
                intervalDays = intervalDays,
                isEnabled = isEnabled,
                extras = extras,
                createdAt = createdAt
            )
        )
    } catch (_: IllegalArgumentException) {
        DraftMappingResult.InvalidDraft(
            listOf(DraftIssue.DomainValidationFailure)
        )
    }
}

fun MedicationPlan.toMedicationPlanDraft(): DraftMappingResult<MedicationPlanDraft> {
    val issues = mutableListOf<DraftIssue>()
    if (name.isBlank()) {
        issues += DraftIssue.MissingRequiredField(DraftField.NAME)
    }

    slots.forEachIndexed { index, slot ->
        if (slot.planId != id || slot.position != index) {
            if (DraftIssue.DomainValidationFailure !in issues) {
                issues += DraftIssue.DomainValidationFailure
            }
            return@forEachIndexed
        }
        if (!slot.localTime.hasMinutePrecision()) {
            issues += DraftIssue.NonMinuteTime(index)
            return@forEachIndexed
        }
        when (val result = ScheduledDoseSlotId.generate(id, index, slot.localTime)) {
            is SlotIdResult.Success -> {
                if (slot.id != result.id) {
                    issues += DraftIssue.SlotIdMismatch(index)
                }
            }
            is SlotIdResult.Failure -> {
                issues += DraftIssue.SlotIdGenerationFailure(index)
            }
        }
    }

    if (issues.isNotEmpty()) {
        return DraftMappingResult.InvalidDraft(issues)
    }

    return DraftMappingResult.Success(
        MedicationPlanDraft(
            id = id,
            name = name,
            route = route,
            ester = ester,
            doseMG = doseMG,
            scheduleType = scheduleType,
            times = slots.map { it.localTime },
            daysOfWeek = daysOfWeek,
            intervalDays = intervalDays,
            isEnabled = isEnabled,
            extras = extras,
            createdAt = createdAt
        )
    )
}

private fun LocalTime.hasMinutePrecision(): Boolean = second == 0 && nano == 0
