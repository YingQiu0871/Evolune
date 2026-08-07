package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.pk.AntiAndrogen
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.pk.SublingualTier
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class MedicationPlanEditSession(
    val id: UUID,
    val createdAt: Instant,
    val existingPlan: MedicationPlan?
) {
    init {
        require(existingPlan == null || existingPlan.id == id)
        require(existingPlan == null || existingPlan.createdAt == createdAt)
    }
}

class MedicationPlanEditSessionFactory(
    private val idSupplier: () -> UUID = UUID::randomUUID,
    private val clock: Clock = Clock.systemUTC()
) {
    fun createNew(): MedicationPlanEditSession = MedicationPlanEditSession(
        id = idSupplier(),
        createdAt = clock.instant().truncatedTo(ChronoUnit.MILLIS),
        existingPlan = null
    )

    fun edit(plan: MedicationPlan): MedicationPlanEditSession = MedicationPlanEditSession(
        id = plan.id,
        createdAt = plan.createdAt,
        existingPlan = plan
    )
}

data class MedicationPlanEditorInput(
    val name: String,
    val route: Route,
    val ester: Ester,
    val selectedAntiAndrogen: AntiAndrogen,
    val doseMGText: String,
    val scheduleType: ScheduleType,
    val times: List<LocalTime>,
    val daysOfWeek: Set<DayOfWeek>,
    val intervalDaysText: String,
    val isEnabled: Boolean,
    val sublingualTier: SublingualTier
)

sealed interface MedicationPlanInputResult {
    data class Success(val draft: MedicationPlanDraft) : MedicationPlanInputResult
    data class InvalidInput(
        val errors: List<MedicationPlanInputError>
    ) : MedicationPlanInputResult
}

sealed interface MedicationPlanInputError {
    data object InvalidDoseMG : MedicationPlanInputError
    data object NonPositiveDoseMG : MedicationPlanInputError
    data object InvalidIntervalDays : MedicationPlanInputError
    data object NonPositiveIntervalDays : MedicationPlanInputError
    data object MissingTime : MedicationPlanInputError
    data object MissingWeeklyDay : MedicationPlanInputError
}

fun MedicationPlanEditorInput.toMedicationPlanDraft(
    session: MedicationPlanEditSession
): MedicationPlanInputResult {
    val errors = mutableListOf<MedicationPlanInputError>()
    val doseMG = doseMGText.toDoubleOrNull()
    if (doseMG == null || !doseMG.isFinite()) {
        errors += MedicationPlanInputError.InvalidDoseMG
    } else if (doseMG <= 0.0) {
        errors += MedicationPlanInputError.NonPositiveDoseMG
    }

    val intervalDays = intervalDaysText.toIntOrNull()
    if (intervalDays == null) {
        errors += MedicationPlanInputError.InvalidIntervalDays
    } else if (intervalDays <= 0) {
        errors += MedicationPlanInputError.NonPositiveIntervalDays
    }

    if (times.isEmpty()) {
        errors += MedicationPlanInputError.MissingTime
    }
    if (scheduleType == ScheduleType.WEEKLY && daysOfWeek.isEmpty()) {
        errors += MedicationPlanInputError.MissingWeeklyDay
    }
    if (errors.isNotEmpty()) {
        return MedicationPlanInputResult.InvalidInput(errors)
    }

    val extras = session.existingPlan?.extras.orEmpty().toMutableMap()
    if (route == Route.SUBLINGUAL) {
        extras[ExtraKey.SUBLINGUAL_TIER] = sublingualTier.toStableCode()
    }
    if (route == Route.ANTIANDROGEN) {
        extras[ExtraKey.ANTI_ANDROGEN_TYPE] = selectedAntiAndrogen.toStableCode()
    }

    return MedicationPlanInputResult.Success(
        MedicationPlanDraft(
            id = session.id,
            name = name,
            route = route,
            ester = if (route == Route.ANTIANDROGEN) Ester.E2 else ester,
            doseMG = requireNotNull(doseMG),
            scheduleType = scheduleType,
            times = times,
            daysOfWeek = daysOfWeek,
            intervalDays = requireNotNull(intervalDays),
            isEnabled = isEnabled,
            extras = extras,
            createdAt = session.createdAt
        )
    )
}

fun MedicationPlan.selectedAntiAndrogen(): AntiAndrogen =
    when (extras[ExtraKey.ANTI_ANDROGEN_TYPE]) {
        0.0 -> AntiAndrogen.CPA
        1.0 -> AntiAndrogen.MPA
        2.0 -> AntiAndrogen.BICALUTAMIDE
        3.0 -> AntiAndrogen.SPIRONOLACTONE
        else -> AntiAndrogen.CPA
    }

fun MedicationPlan.selectedSublingualTier(): SublingualTier =
    when (extras[ExtraKey.SUBLINGUAL_TIER]) {
        0.0 -> SublingualTier.QUICK
        1.0 -> SublingualTier.CASUAL
        2.0 -> SublingualTier.STANDARD
        3.0 -> SublingualTier.STRICT
        else -> SublingualTier.STANDARD
    }

private fun AntiAndrogen.toStableCode(): Double = when (this) {
    AntiAndrogen.CPA -> 0.0
    AntiAndrogen.MPA -> 1.0
    AntiAndrogen.BICALUTAMIDE -> 2.0
    AntiAndrogen.SPIRONOLACTONE -> 3.0
}

private fun SublingualTier.toStableCode(): Double = when (this) {
    SublingualTier.QUICK -> 0.0
    SublingualTier.CASUAL -> 1.0
    SublingualTier.STANDARD -> 2.0
    SublingualTier.STRICT -> 3.0
}
