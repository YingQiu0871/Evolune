package io.github.yuninggu.evolune.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.yuninggu.evolune.data.repository.ProductionRepositoryProvider

/**
 * Restores medication alarms after restart, app update, or clock changes.
 */
class ReminderRescheduleReceiver : BroadcastReceiver {

    private var workFactory: ((Context) -> ReminderRescheduleWork)? = null
    private var workLauncher = ReceiverWorkLauncher()

    constructor()

    internal constructor(
        workFactory: (Context) -> ReminderRescheduleWork,
        workLauncher: ReceiverWorkLauncher = ReceiverWorkLauncher()
    ) : this() {
        this.workFactory = workFactory
        this.workLauncher = workLauncher
    }

    override fun onReceive(context: Context, intent: Intent) {
        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        workLauncher.launch(
            work = {
                val work = workFactory?.invoke(applicationContext)
                    ?: productionWork(applicationContext)
                work.handle()
            },
            finish = pendingResult::finish
        )
    }

    private fun productionWork(context: Context): ReminderRescheduleWork {
        val repositories = ProductionRepositoryProvider.get(context)
        return ContractReminderRescheduleWork(
            medicationPlans = repositories.medicationPlans,
            scheduler = ReminderManager(context)
        )
    }
}
