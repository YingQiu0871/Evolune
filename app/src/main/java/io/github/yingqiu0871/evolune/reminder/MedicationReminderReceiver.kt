package io.github.yingqiu0871.evolune.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.utils.description
import java.util.UUID

/**
 * 用药提醒广播接收器
 * 接收定时提醒的广播并显示通知
 */
class MedicationReminderReceiver : BroadcastReceiver {

    private var workFactory: ((Context) -> ReminderDeliveryWork)? = null
    private var workLauncher = ReceiverWorkLauncher()

    constructor()

    internal constructor(
        workFactory: (Context) -> ReminderDeliveryWork,
        workLauncher: ReceiverWorkLauncher = ReceiverWorkLauncher()
    ) : this() {
        this.workFactory = workFactory
        this.workLauncher = workLauncher
    }

    companion object {
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_PLAN_NAME = "plan_name"
        const val EXTRA_PLAN_DESCRIPTION = "plan_description"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SCHEDULED_AT_MILLIS = "scheduled_at_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val scheduledAtMillis = intent.getLongExtra(
            EXTRA_SCHEDULED_AT_MILLIS,
            System.currentTimeMillis()
        )
        val planUuid = runCatching { UUID.fromString(planId) }.getOrNull() ?: return
        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        workLauncher.launch(
            work = {
                val work = workFactory?.invoke(applicationContext)
                    ?: productionWork(applicationContext)
                work.handle(
                    ReminderDeliveryCommand(
                        planId = planUuid,
                        notificationId = notificationId,
                        scheduledAtMillis = scheduledAtMillis
                    )
                )
            },
            finish = pendingResult::finish
        )
    }

    private fun productionWork(context: Context): ReminderDeliveryWork {
        val repositories = ProductionRepositoryProvider.get(context)
        return ContractReminderDeliveryWork(
            medicationPlans = repositories.medicationPlans,
            doseEvents = repositories.doseEvents,
            sideEffects = object : ReminderDeliverySideEffects {
                override fun sendReminder(
                    plan: io.github.yingqiu0871.evolune.core.model.MedicationPlan,
                    command: ReminderDeliveryCommand
                ) {
                    NotificationHelper(context).sendMedicationReminder(
                        planId = plan.id.toString(),
                        planName = plan.name,
                        description = plan.description(),
                        notificationId = command.notificationId,
                        scheduledAtMillis = command.scheduledAtMillis
                    )
                }

                override fun scheduleNextBatch(
                    plan: io.github.yingqiu0871.evolune.core.model.MedicationPlan
                ) {
                    ReminderManager(context).scheduleReminder(plan)
                }
            }
        )
    }
}
