package io.github.yingqiu0871.evolune.reminder

import io.github.yingqiu0871.evolune.application.LocalActionRecorder
import io.github.yingqiu0871.evolune.application.OccurrenceConfirmationCoordinator
import io.github.yingqiu0871.evolune.application.RecordAcceptance
import io.github.yingqiu0871.evolune.application.RecordDoseEventActionResult
import io.github.yingqiu0871.evolune.application.findPresentedEventForOccurrence
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.data.repository.RepositoryStorageException
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal data class ReminderDeliveryCommand(
    val planId: UUID,
    val notificationId: Int,
    val scheduledAtMillis: Long
)

internal sealed interface ReminderDeliveryOutcome {
    data object Notified : ReminderDeliveryOutcome
    data object CheckInFound : ReminderDeliveryOutcome
    data object PlanNotFound : ReminderDeliveryOutcome
    data object PlanDisabled : ReminderDeliveryOutcome
    data object StorageFailure : ReminderDeliveryOutcome
    data object UnexpectedFailure : ReminderDeliveryOutcome
}

internal interface ReminderDeliverySideEffects {
    fun sendReminder(plan: MedicationPlan, command: ReminderDeliveryCommand)
    fun scheduleNextBatch(plan: MedicationPlan)
}

internal fun interface ReminderDeliveryWork {
    suspend fun handle(command: ReminderDeliveryCommand): ReminderDeliveryOutcome
}

internal class ContractReminderDeliveryWork(
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository,
    private val sideEffects: ReminderDeliverySideEffects
) : ReminderDeliveryWork {
    override suspend fun handle(command: ReminderDeliveryCommand): ReminderDeliveryOutcome = try {
        val plan = medicationPlans.getById(command.planId)
            ?: return ReminderDeliveryOutcome.PlanNotFound
        if (!plan.isEnabled) {
            return ReminderDeliveryOutcome.PlanDisabled
        }

        val scheduledAt = Instant.ofEpochMilli(command.scheduledAtMillis)
        val events = doseEvents.findOccurredBetween(
            startInclusive = scheduledAt.minusMillis(DOSE_CHECK_IN_WINDOW_MILLIS),
            endExclusive = scheduledAt.plusMillis(DOSE_CHECK_IN_WINDOW_MILLIS + 1L)
        )
        val checkedIn = hasPlanDoseCheckIn(
            plan = plan,
            events = events,
            scheduledAtMillis = command.scheduledAtMillis
        )
        if (!checkedIn) {
            sideEffects.sendReminder(plan, command)
        }
        sideEffects.scheduleNextBatch(plan)
        if (checkedIn) {
            ReminderDeliveryOutcome.CheckInFound
        } else {
            ReminderDeliveryOutcome.Notified
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RepositoryStorageException) {
        ReminderDeliveryOutcome.StorageFailure
    } catch (_: Throwable) {
        ReminderDeliveryOutcome.UnexpectedFailure
    }
}

internal data class NotificationActionCommand(
    val planId: UUID,
    val notificationId: Int,
    val scheduledAtMillis: Long
)

internal sealed interface NotificationActionOutcome {
    data class Accepted(val replayed: Boolean) : NotificationActionOutcome
    data object AcceptedWithSideEffectFailure : NotificationActionOutcome
    data object StalePlan : NotificationActionOutcome
    data object StalePlanCleanupFailure : NotificationActionOutcome
    data object Conflict : NotificationActionOutcome
    data object Invalid : NotificationActionOutcome
    data object StorageFailure : NotificationActionOutcome
    data object UnexpectedFailure : NotificationActionOutcome
}

internal interface NotificationActionSideEffects {
    suspend fun refreshWidgets()
    fun cancelNotification(notificationId: Int)
}

internal fun interface NotificationActionWork {
    suspend fun handle(command: NotificationActionCommand): NotificationActionOutcome
}

internal class ContractNotificationActionWork(
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository,
    private val sideEffects: NotificationActionSideEffects,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault
) : NotificationActionWork {
    private val recordAction = LocalActionRecorder(medicationPlans, doseEvents)

    override suspend fun handle(command: NotificationActionCommand): NotificationActionOutcome = try {
        val recordedAtMillis = clock.millis()
        OccurrenceConfirmationCoordinator.withLock {
            handleLocked(command, recordedAtMillis)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RepositoryStorageException) {
        NotificationActionOutcome.StorageFailure
    } catch (_: Throwable) {
        NotificationActionOutcome.UnexpectedFailure
    }

    private suspend fun handleLocked(
        command: NotificationActionCommand,
        recordedAtMillis: Long
    ): NotificationActionOutcome {
        val actionZoneId = zoneId()
        val scheduledAt = runCatching {
            Instant.ofEpochMilli(command.scheduledAtMillis)
        }.getOrNull() ?: return NotificationActionOutcome.Invalid
        val scheduledLocalDate = runCatching {
            scheduledAt.atZone(actionZoneId).toLocalDate()
        }.getOrNull() ?: return NotificationActionOutcome.Invalid
        val recordedAt = Instant.ofEpochMilli(recordedAtMillis)
        val currentPlan = medicationPlans.getById(command.planId)
            ?: return stale(command)
        if (!currentPlan.isEnabled) {
            return stale(command)
        }
        val targetOccurrence = regenerateExactOccurrence(
            plan = currentPlan,
            scheduledAt = scheduledAt,
            localDate = scheduledLocalDate,
            zoneId = actionZoneId
        ) ?: return NotificationActionOutcome.Invalid
        val presentationOccurrences = regeneratePresentationOccurrences(
            medicationPlans.observeEnabled().first(),
            scheduledLocalDate,
            actionZoneId
        )
        val existingPresentationEvent = findPresentedEventForOccurrence(
            targetOccurrence,
            presentationOccurrences,
            doseEvents.observeAll().first(),
            recordedAt
        )
        if (existingPresentationEvent != null) {
            return accepted(command, replayed = true)
        }

        return when (
            val result = recordAction.recordReminder(
                planId = command.planId,
                scheduledAtMillis = command.scheduledAtMillis,
                requireEnabledPlan = true,
                validatePlan = { plan ->
                    regenerateExactOccurrence(
                        plan = plan,
                        scheduledAt = scheduledAt,
                        localDate = scheduledLocalDate,
                        zoneId = actionZoneId
                    ) != null
                },
            ) { plan, _ ->
                val authoritativeOccurrence = regenerateExactOccurrence(
                    plan = plan,
                    scheduledAt = scheduledAt,
                    localDate = scheduledLocalDate,
                    zoneId = actionZoneId
                ) ?: error("notification occurrence changed during recording")
                createReminderDoseEvent(
                    plan = plan,
                    targetOccurrence = authoritativeOccurrence,
                    recordedAtMillis = recordedAtMillis,
                    zoneId = actionZoneId
                )
            }
        ) {
            is RecordDoseEventActionResult.Accepted -> {
                val verifiedPlan = result.plan ?: currentPlan
                val verifiedOccurrence = regenerateExactOccurrence(
                    plan = verifiedPlan,
                    scheduledAt = scheduledAt,
                    localDate = scheduledLocalDate,
                    zoneId = actionZoneId
                )
                val verifiedWinner = verifiedOccurrence?.let { occurrence ->
                    findPresentedEventForOccurrence(
                        occurrence,
                        regeneratePresentationOccurrences(
                            medicationPlans.observeEnabled().first(),
                            scheduledLocalDate,
                            actionZoneId
                        ),
                        doseEvents.observeAll().first(),
                        Instant.ofEpochMilli(clock.millis())
                    )
                }
                if (verifiedWinner == null) {
                    NotificationActionOutcome.Conflict
                } else {
                    accepted(
                        command,
                        replayed = result.acceptance != RecordAcceptance.Inserted ||
                            verifiedWinner.id != result.event.id
                    )
                }
            }
            RecordDoseEventActionResult.PlanNotFound,
            RecordDoseEventActionResult.PlanDisabled -> stale(command)
            RecordDoseEventActionResult.Conflict -> {
                val verified = findPresentedEventForOccurrence(
                    targetOccurrence,
                    presentationOccurrences,
                    doseEvents.observeAll().first(),
                    Instant.ofEpochMilli(clock.millis())
                )
                if (verified != null) {
                    accepted(command, replayed = true)
                } else {
                    NotificationActionOutcome.Conflict
                }
            }
            RecordDoseEventActionResult.Invalid -> NotificationActionOutcome.Invalid
            RecordDoseEventActionResult.StorageFailure -> NotificationActionOutcome.StorageFailure
            RecordDoseEventActionResult.UnexpectedFailure ->
                NotificationActionOutcome.UnexpectedFailure
        }
    }

    private fun regenerateExactOccurrence(
        plan: MedicationPlan,
        scheduledAt: Instant,
        localDate: LocalDate,
        zoneId: ZoneId
    ): MedicationOccurrence? {
        val window = runCatching {
            OccurrenceGenerationWindow(
                startInclusive = localDate.atStartOfDay(zoneId).toInstant(),
                endExclusive = localDate.plusDays(1L).atStartOfDay(zoneId).toInstant()
            )
        }.getOrNull() ?: return null
        return MedicationOccurrenceGenerator.generate(
            schedules = listOf(plan.toMedicationSchedule()),
            window = window,
            zoneId = zoneId
        ).singleOrNull { it.scheduledAt == scheduledAt }
    }

    private fun regeneratePresentationOccurrences(
        plans: List<MedicationPlan>,
        localDate: LocalDate,
        zoneId: ZoneId
    ): List<MedicationOccurrence> {
        val window = runCatching {
            OccurrenceGenerationWindow(
                startInclusive = localDate.minusDays(1L).atStartOfDay(zoneId).toInstant(),
                endExclusive = localDate.plusDays(2L).atStartOfDay(zoneId).toInstant()
            )
        }.getOrNull() ?: return emptyList()
        return MedicationOccurrenceGenerator.generate(
            schedules = plans.map(MedicationPlan::toMedicationSchedule),
            window = window,
            zoneId = zoneId
        )
    }

    private suspend fun accepted(
        command: NotificationActionCommand,
        replayed: Boolean
    ): NotificationActionOutcome = try {
        sideEffects.refreshWidgets()
        sideEffects.cancelNotification(command.notificationId)
        NotificationActionOutcome.Accepted(replayed)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        NotificationActionOutcome.AcceptedWithSideEffectFailure
    }

    private fun stale(command: NotificationActionCommand): NotificationActionOutcome = try {
        sideEffects.cancelNotification(command.notificationId)
        NotificationActionOutcome.StalePlan
    } catch (_: Throwable) {
        NotificationActionOutcome.StalePlanCleanupFailure
    }
}

internal sealed interface ReminderRescheduleOutcome {
    data object Rescheduled : ReminderRescheduleOutcome
    data object StorageFailure : ReminderRescheduleOutcome
    data object UnexpectedFailure : ReminderRescheduleOutcome
}

internal fun interface ReminderRescheduleWork {
    suspend fun handle(): ReminderRescheduleOutcome
}

internal class ContractReminderRescheduleWork(
    private val medicationPlans: MedicationPlanRepository,
    private val scheduler: MedicationPlanReminderScheduler
) : ReminderRescheduleWork {
    override suspend fun handle(): ReminderRescheduleOutcome = try {
        scheduler.reschedule(medicationPlans.observeAll().first())
        ReminderRescheduleOutcome.Rescheduled
    } catch (error: CancellationException) {
        throw error
    } catch (_: RepositoryStorageException) {
        ReminderRescheduleOutcome.StorageFailure
    } catch (_: Throwable) {
        ReminderRescheduleOutcome.UnexpectedFailure
    }
}
