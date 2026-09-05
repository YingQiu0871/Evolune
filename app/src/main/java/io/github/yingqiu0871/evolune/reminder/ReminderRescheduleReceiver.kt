package io.github.yingqiu0871.evolune.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider

/**
 * Restores medication alarms after restart, app update, or clock changes.
 */
class ReminderRescheduleReceiver : BroadcastReceiver {

    private var workFactory: ((Context) -> ReminderRescheduleWork)? = null
    private var workLauncher = ReceiverWorkLauncher()
    private var acceptedActions = DEFAULT_ACTIONS

    constructor()

    internal constructor(
        workFactory: (Context) -> ReminderRescheduleWork,
        workLauncher: ReceiverWorkLauncher = ReceiverWorkLauncher(),
        acceptedActions: Set<String> = DEFAULT_ACTIONS
    ) : this() {
        this.workFactory = workFactory
        this.workLauncher = workLauncher
        this.acceptedActions = acceptedActions
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in acceptedActions) return
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

    private companion object {
        val DEFAULT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
