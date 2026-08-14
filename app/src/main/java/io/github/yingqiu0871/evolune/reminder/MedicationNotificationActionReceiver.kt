package io.github.yingqiu0871.evolune.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.widget.updateAllEvoluneWidgets
import java.util.UUID

/**
 * Handles the system notification's dose actions without opening the app.
 */
class MedicationNotificationActionReceiver : BroadcastReceiver {

    private var workFactory: ((Context) -> NotificationActionWork)? = null
    private var workLauncher = ReceiverWorkLauncher()

    constructor()

    internal constructor(
        workFactory: (Context) -> NotificationActionWork,
        workLauncher: ReceiverWorkLauncher = ReceiverWorkLauncher()
    ) : this() {
        this.workFactory = workFactory
        this.workLauncher = workLauncher
    }

    companion object {
        const val ACTION_CONFIRM_DOSE =
            "io.github.yingqiu0871.evolune.action.CONFIRM_DOSE"
        const val ACTION_SKIP_DOSE =
            "io.github.yingqiu0871.evolune.action.SKIP_DOSE"
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SCHEDULED_AT_MILLIS = "scheduled_at_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (intent.action == ACTION_SKIP_DOSE) {
            NotificationHelper(context.applicationContext)
                .cancelNotification(notificationId)
            return
        }
        if (intent.action != ACTION_CONFIRM_DOSE) {
            return
        }

        val planId = intent.getStringExtra(EXTRA_PLAN_ID)
            ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            ?: return
        val scheduledAtMillis = intent.getLongExtra(
            EXTRA_SCHEDULED_AT_MILLIS,
            System.currentTimeMillis()
        )
        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        workLauncher.launch(
            work = {
                val work = workFactory?.invoke(applicationContext)
                    ?: productionWork(applicationContext)
                work.handle(
                    NotificationActionCommand(
                        planId = planId,
                        notificationId = notificationId,
                        scheduledAtMillis = scheduledAtMillis
                    )
                )
            },
            finish = pendingResult::finish
        )
    }

    private fun productionWork(context: Context): NotificationActionWork {
        val repositories = ProductionRepositoryProvider.get(context)
        return ContractNotificationActionWork(
            medicationPlans = repositories.medicationPlans,
            doseEvents = repositories.doseEvents,
            sideEffects = object : NotificationActionSideEffects {
                override suspend fun refreshWidgets() {
                    updateAllEvoluneWidgets(context)
                }

                override fun cancelNotification(notificationId: Int) {
                    NotificationHelper(context).cancelNotification(notificationId)
                }
            }
        )
    }
}
