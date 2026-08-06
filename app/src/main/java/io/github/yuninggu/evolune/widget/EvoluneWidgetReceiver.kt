package io.github.yuninggu.evolune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import io.github.yuninggu.evolune.MainActivity
import io.github.yuninggu.evolune.R
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.data.SettingsDataStore
import io.github.yuninggu.evolune.data.repository.ProductionRepositoryProvider
import io.github.yuninggu.evolune.reminder.ReceiverWorkLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Traditional RemoteViews widget used for broad launcher compatibility,
 * including Honor foldable launchers.
 */
class EvoluneWidgetReceiver : AppWidgetProvider {

    private var quickActionWorkFactory: ((Context) -> WidgetQuickActionWork)? = null
    private var updateWorkFactory: ((Context, AppWidgetManager) -> WidgetUpdateWork)? = null
    private var workLauncher = ReceiverWorkLauncher()

    constructor()

    internal constructor(
        quickActionWorkFactory: (Context) -> WidgetQuickActionWork,
        updateWorkFactory: (Context, AppWidgetManager) -> WidgetUpdateWork,
        workLauncher: ReceiverWorkLauncher = ReceiverWorkLauncher()
    ) : this() {
        this.quickActionWorkFactory = quickActionWorkFactory
        this.updateWorkFactory = updateWorkFactory
        this.workLauncher = workLauncher
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(
            context,
            appWidgetManager,
            appWidgetId,
            newOptions
        )
        updateAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_RECORD_PLAN) return

        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        workLauncher.launch(
            work = {
                val work = quickActionWorkFactory?.invoke(applicationContext)
                    ?: productionQuickActionWork(applicationContext)
                work.handle(
                    WidgetQuickActionCommand(intent.getStringExtra(EXTRA_PLAN_ID))
                )
            },
            finish = pendingResult::finish
        )
    }

    private fun updateAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        workLauncher.launch(
            work = {
                val work = updateWorkFactory?.invoke(applicationContext, appWidgetManager)
                    ?: productionUpdateWork(applicationContext, appWidgetManager)
                work.handle(appWidgetIds)
            },
            finish = pendingResult::finish
        )
    }

    private companion object {
        const val ACTION_RECORD_PLAN =
            "io.github.yuninggu.evolune.widget.RECORD_PLAN"
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_WIDGET_ID = "widget_id"
    }

    private fun productionQuickActionWork(context: Context): WidgetQuickActionWork {
        val repositories = ProductionRepositoryProvider.get(context)
        return ContractWidgetQuickActionWork(
            medicationPlans = repositories.medicationPlans,
            doseEvents = repositories.doseEvents,
            sideEffects = object : WidgetQuickActionSideEffects {
                override suspend fun refreshWidgets() {
                    updateAllEvoluneWidgets(context)
                }

                override suspend fun showRecorded(planName: String) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已记录：$planName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun productionUpdateWork(
        context: Context,
        appWidgetManager: AppWidgetManager
    ): WidgetUpdateWork = createProductionWidgetUpdateWork(context, appWidgetManager)
}

private fun renderWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    snapshot: WidgetSnapshot
) {
    val plans = snapshot.plans
    val minHeight = appWidgetManager
        .getAppWidgetOptions(appWidgetId)
        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100)
    val isTall = minHeight >= 180

    val views = RemoteViews(context.packageName, R.layout.widget_evolune)
    views.setTextViewText(
        R.id.widget_concentration,
        snapshot.concentration?.let { "%.1f".format(it) } ?: "--"
    )
    views.setViewVisibility(
        R.id.widget_target_range,
        if (isTall) View.VISIBLE else View.GONE
    )
    views.setViewVisibility(
        R.id.widget_record_title,
        if (isTall) View.VISIBLE else View.GONE
    )
    views.setOnClickPendingIntent(
        R.id.widget_concentration_panel,
        openAppPendingIntent(context)
    )

    bindPlanButton(
        context = context,
        views = views,
        buttonId = R.id.widget_plan_one,
        plan = plans.getOrNull(0),
        appWidgetId = appWidgetId,
        emptyText = "打开 App 添加方案"
    )
    bindPlanButton(
        context = context,
        views = views,
        buttonId = R.id.widget_plan_two,
        plan = plans.getOrNull(1),
        appWidgetId = appWidgetId,
        emptyText = if (plans.isEmpty()) "添加用药方案" else "添加第二个方案"
    )
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun bindPlanButton(
    context: Context,
    views: RemoteViews,
    buttonId: Int,
    plan: MedicationPlan?,
    appWidgetId: Int,
    emptyText: String
) {
    if (plan == null) {
        views.setTextViewText(buttonId, emptyText)
        views.setOnClickPendingIntent(buttonId, openAppPendingIntent(context))
        return
    }

    views.setTextViewText(
        buttonId,
        "${plan.name}\n${formatDose(plan.doseMG)} mg"
    )
    val intent = Intent(context, EvoluneWidgetReceiver::class.java).apply {
        action = "io.github.yuninggu.evolune.widget.RECORD_PLAN"
        putExtra("plan_id", plan.id.toString())
        putExtra("widget_id", appWidgetId)
        data = Uri.parse(
            "evolune://widget/$appWidgetId/plan/${plan.id}"
        )
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
    views.setOnClickPendingIntent(
        buttonId,
        PendingIntent.getBroadcast(
            context,
            appWidgetId xor plan.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )
}

private fun openAppPendingIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

internal suspend fun calculateWidgetConcentration(context: Context): Double? {
    val repositories = ProductionRepositoryProvider.get(context.applicationContext)
    return WidgetSnapshotLoader(
        medicationPlans = repositories.medicationPlans,
        doseEvents = repositories.doseEvents,
        bodyWeight = {
            SettingsDataStore(context.applicationContext)
                .userSettings.first().bodyWeight
        }
    ).load().concentration
}

internal suspend fun updateAllEvoluneWidgets(context: Context) {
    val applicationContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(applicationContext)
    val component = ComponentName(applicationContext, EvoluneWidgetReceiver::class.java)
    val ids = manager.getAppWidgetIds(component)
    createProductionWidgetUpdateWork(applicationContext, manager).handle(ids)
}

private fun createProductionWidgetUpdateWork(
    context: Context,
    appWidgetManager: AppWidgetManager
): WidgetUpdateWork {
    val repositories = ProductionRepositoryProvider.get(context)
    return ContractWidgetUpdateWork(
        snapshotLoader = WidgetSnapshotLoader(
            medicationPlans = repositories.medicationPlans,
            doseEvents = repositories.doseEvents,
            bodyWeight = {
                SettingsDataStore(context).userSettings.first().bodyWeight
            }
        ),
        renderer = WidgetSnapshotRenderer { appWidgetId, snapshot ->
            renderWidget(context, appWidgetManager, appWidgetId, snapshot)
        }
    )
}

private fun formatDose(doseMG: Double): String =
    if (doseMG % 1.0 == 0.0) {
        doseMG.toInt().toString()
    } else {
        "%.2f".format(doseMG)
    }
