package io.github.yingqiu0871.evolune.reminder

import io.github.yingqiu0871.evolune.application.LocalActionRecorder
import io.github.yingqiu0871.evolune.application.RecordAcceptance
import io.github.yingqiu0871.evolune.application.RecordDoseEventActionResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.data.repository.RepositoryStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
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
    medicationPlans: MedicationPlanRepository,
    doseEvents: DoseEventRepository,
    private val sideEffects: NotificationActionSideEffects,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault
) : NotificationActionWork {
    private val recordAction = LocalActionRecorder(medicationPlans, doseEvents)

    override suspend fun handle(command: NotificationActionCommand): NotificationActionOutcome {
        val recordedAtMillis = clock.millis()
        return when (
            val result = recordAction.recordReminder(
                planId = command.planId,
                scheduledAtMillis = command.scheduledAtMillis,
            ) { plan, _ ->
                createReminderDoseEvent(
                    plan = plan,
                    recordedAtMillis = recordedAtMillis,
                    scheduledAtMillis = command.scheduledAtMillis,
                    zoneId = zoneId()
                )
            }
        ) {
            is RecordDoseEventActionResult.Accepted -> accepted(
                command,
                result.acceptance != RecordAcceptance.Inserted
            )
            RecordDoseEventActionResult.PlanNotFound,
            RecordDoseEventActionResult.PlanDisabled -> stale(command)
            RecordDoseEventActionResult.Conflict -> NotificationActionOutcome.Conflict
            RecordDoseEventActionResult.Invalid -> NotificationActionOutcome.Invalid
            RecordDoseEventActionResult.StorageFailure -> NotificationActionOutcome.StorageFailure
            RecordDoseEventActionResult.UnexpectedFailure ->
                NotificationActionOutcome.UnexpectedFailure
        }
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
