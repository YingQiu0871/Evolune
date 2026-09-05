package io.github.yingqiu0871.evolune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.net.toUri
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.reminder.ReceiverWorkLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal const val ACTION_RECORD_OCCURRENCE =
    "io.github.yingqiu0871.evolune.widget.RECORD_OCCURRENCE"
internal const val ACTION_OPEN_WIDGET_APP =
    "io.github.yingqiu0871.evolune.widget.OPEN_APP"
internal const val EXTRA_PLAN_ID = "plan_id"
internal const val EXTRA_SLOT_ID = "slot_id"
internal const val EXTRA_SCHEDULED_LOCAL_DATE = "scheduled_local_date"
internal const val EXTRA_OCCURRENCE_ID = "occurrence_id"
internal const val EXTRA_WIDGET_ID = "widget_id"

/** Traditional RemoteViews remains the broad-launcher compatibility boundary. */
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
        updateAsync(context, appWidgetManager, appWidgetIds, WidgetUpdateReason.APP_WIDGET_UPDATE)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAsync(
            context,
            appWidgetManager,
            intArrayOf(appWidgetId),
            WidgetUpdateReason.WIDGET_RESIZED
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetAppearanceStore(context)
        appWidgetIds.forEach(store::delete)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_RECORD_OCCURRENCE -> handleRecordOccurrence(context, intent)
            ACTION_OPEN_WIDGET_APP -> context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> refreshAllAsync(
                context,
                WidgetUpdateReason.DATE_OR_TIMEZONE_CHANGED
            )
            else -> Unit
        }
    }

    private fun handleRecordOccurrence(context: Context, intent: Intent) {
        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        workLauncher.launch(
            work = {
                val work = quickActionWorkFactory?.invoke(applicationContext)
                    ?: productionQuickActionWork(applicationContext)
                work.handle(
                    WidgetQuickActionCommand(
                        planId = intent.getStringExtra(EXTRA_PLAN_ID),
                        slotId = intent.getStringExtra(EXTRA_SLOT_ID),
                        scheduledLocalDate = intent.getStringExtra(EXTRA_SCHEDULED_LOCAL_DATE),
                        occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)
                    )
                )
            },
            finish = pendingResult::finish
        )
    }

    private fun refreshAllAsync(context: Context, reason: WidgetUpdateReason) {
        val applicationContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, EvoluneWidgetReceiver::class.java)
        updateAsync(
            applicationContext,
            manager,
            manager.getAppWidgetIds(component),
            reason
        )
    }

    private fun updateAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        reason: WidgetUpdateReason
    ) {
        val applicationContext = context.applicationContext
        val pendingResult = goAsync()
        workLauncher.launch(
            work = {
                val work = updateWorkFactory?.invoke(applicationContext, appWidgetManager)
                    ?: createProductionWidgetUpdateWork(applicationContext, appWidgetManager)
                ContractWidgetUpdateCoordinator { work.handle(appWidgetIds) }.request(reason)
            },
            finish = pendingResult::finish
        )
    }

    private fun productionQuickActionWork(context: Context): WidgetQuickActionWork {
        val repositories = ProductionRepositoryProvider.get(context)
        return ContractWidgetQuickActionWork(
            medicationPlans = repositories.medicationPlans,
            doseEvents = repositories.doseEvents,
            sideEffects = object : WidgetQuickActionSideEffects {
                override suspend fun refreshWidgets() {
                    requestEvoluneWidgetUpdate(
                        context,
                        WidgetUpdateReason.ACCEPTED_WIDGET_DOSE_EVENT
                    )
                }

                override suspend fun showRecorded(planName: String) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已记录：$planName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

private fun renderWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    state: WidgetRenderState
) {
    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 97)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
    val size = WidgetSize(
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150).coerceAtLeast(1),
        maxOf(minHeight, maxHeight).coerceAtLeast(1)
    )
    val layout = WidgetSizePolicy.resolve(size)
    val appearance = WidgetAppearanceStore(context).read(appWidgetId)
    val model = WidgetUiMapper.map(state, layout, appearance)
    val palette = WidgetPaletteResolver.resolve(context, appearance)
    val views = RemoteViews(context.packageName, layout.tier.layoutRes())
    val openApp = openAppPendingIntent(context)

    applySurface(views, model, palette)
    views.setOnClickPendingIntent(R.id.widget_root, openApp)
    if (model.contentState == WidgetContentState.TIMELINE && model.rows.isNotEmpty()) {
        bindTimeline(context, views, model, palette, appWidgetId, openApp)
    } else {
        bindEmpty(context, views, model, palette, openApp)
    }
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun WidgetSizeTier.layoutRes(): Int = when (this) {
    WidgetSizeTier.NARROW_SHORT -> R.layout.widget_evolune_compact
    WidgetSizeTier.NARROW_STANDARD -> R.layout.widget_evolune
    WidgetSizeTier.WIDE_STANDARD -> R.layout.widget_evolune_wide
    WidgetSizeTier.EXPANDED -> R.layout.widget_evolune_expanded
}

private fun applySurface(
    views: RemoteViews,
    model: WidgetUiModel,
    palette: WidgetPalette
) {
    val alpha = (model.appearance.backgroundOpacity * 255f).roundToInt()
    val surface = Color.argb(
        alpha,
        Color.red(palette.surface),
        Color.green(palette.surface),
        Color.blue(palette.surface)
    )
    views.setColorStateList(
        R.id.widget_root,
        "setBackgroundTintList",
        ColorStateList.valueOf(surface)
    )
    views.setTextColor(R.id.widget_summary, palette.onSurface)
    views.setTextColor(R.id.widget_concentration, palette.primaryForeground)
    views.setTextColor(R.id.widget_empty_title, palette.onSurface)
    views.setTextColor(R.id.widget_empty_meta, palette.onSurfaceVariant)
}

private fun bindTimeline(
    context: Context,
    views: RemoteViews,
    model: WidgetUiModel,
    palette: WidgetPalette,
    appWidgetId: Int,
    openApp: PendingIntent
) {
    views.setViewVisibility(R.id.widget_empty_area, View.GONE)
    views.setViewVisibility(R.id.widget_rows_container, View.VISIBLE)
    views.setTextViewText(
        R.id.widget_summary,
        context.getString(
            R.string.widget_daily_progress,
            model.dailyProgress.completed,
            model.dailyProgress.total
        )
    )
    bindConcentration(context, views, model.concentration)
    bindProgress(context, views, model.progressSegments, palette)
    val collection = RemoteViews.RemoteCollectionItems.Builder()
        .setHasStableIds(true)
        .setViewTypeCount(1)
    model.rows.forEachIndexed { index, occurrence ->
        collection.addItem(
            occurrence.occurrenceId.mostSignificantBits xor
                occurrence.occurrenceId.leastSignificantBits,
            medicationRow(
                context,
                occurrence,
                model.timeFormat,
                palette,
                model.rowLayout,
                appWidgetId,
                openApp,
                railRoleIndex = index,
                collectionItem = true
            )
        )
    }
    views.setRemoteAdapter(R.id.widget_rows_container, collection.build())
    views.setPendingIntentTemplate(
        R.id.widget_rows_container,
        widgetCollectionPendingIntentTemplate(context, appWidgetId)
    )
}

private fun bindEmpty(
    context: Context,
    views: RemoteViews,
    model: WidgetUiModel,
    palette: WidgetPalette,
    openApp: PendingIntent
) {
    views.setViewVisibility(R.id.widget_rows_container, View.GONE)
    views.setViewVisibility(R.id.widget_progress_container, View.GONE)
    views.setViewVisibility(R.id.widget_empty_area, View.VISIBLE)
    views.setTextViewText(R.id.widget_summary, context.getString(R.string.widget_daily_progress_empty))
    bindConcentration(context, views, model.concentration)
    val (title, description) = when (model.contentState) {
        WidgetContentState.LOADING -> R.string.widget_loading to R.string.widget_loading_desc
        WidgetContentState.READ_FAILURE -> R.string.widget_read_failure to R.string.widget_read_failure_desc
        WidgetContentState.NO_ENABLED_PLANS ->
            R.string.widget_no_enabled_plans to R.string.widget_no_enabled_plans_desc
        WidgetContentState.NO_UPCOMING_OCCURRENCE ->
            R.string.widget_no_upcoming to R.string.widget_no_upcoming_desc
        WidgetContentState.TIMELINE -> R.string.widget_no_upcoming to R.string.widget_no_upcoming_desc
    }
    views.setTextViewText(R.id.widget_empty_title, context.getString(title))
    views.setTextViewText(R.id.widget_empty_meta, context.getString(description))
    if (model.contentState == WidgetContentState.READ_FAILURE) {
        views.setTextColor(R.id.widget_empty_title, palette.error)
    }
    views.setOnClickPendingIntent(R.id.widget_empty_area, openApp)
}

private fun bindConcentration(
    context: Context,
    views: RemoteViews,
    concentration: Double?
) {
    views.setViewVisibility(
        R.id.widget_concentration,
        if (concentration == null) View.GONE else View.VISIBLE
    )
    if (concentration != null) {
        views.setTextViewText(
            R.id.widget_concentration,
            context.getString(
                R.string.widget_concentration_compact,
                "%.0f".format(concentration)
            )
        )
    }
}

private fun bindProgress(
    context: Context,
    views: RemoteViews,
    segments: List<WidgetProgressSegment>,
    palette: WidgetPalette
) {
    views.removeAllViews(R.id.widget_progress_container)
    views.setViewVisibility(
        R.id.widget_progress_container,
        if (segments.isEmpty()) View.GONE else View.VISIBLE
    )
    segments.forEach { segmentState ->
        val color = when (segmentState) {
            WidgetProgressSegment.FILLED -> palette.primary
            WidgetProgressSegment.EMPTY -> palette.progressTrack
        }
        val segment = RemoteViews(context.packageName, R.layout.widget_progress_segment)
        segment.setColorStateList(
            R.id.widget_progress_segment,
            "setBackgroundTintList",
            ColorStateList.valueOf(color)
        )
        views.addView(R.id.widget_progress_container, segment)
    }
}

internal fun medicationRow(
    context: Context,
    occurrence: WidgetOccurrenceUi,
    timeFormat: TimeFormat,
    palette: WidgetPalette,
    rowLayout: WidgetRowLayoutSpec,
    appWidgetId: Int,
    openApp: PendingIntent,
    railRoleIndex: Int = 0,
    collectionItem: Boolean = false
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_medication_row)
    val railColor = palette.medicationRailColor(railRoleIndex)
    views.setColorStateList(
        R.id.widget_row_rail,
        "setBackgroundTintList",
        ColorStateList.valueOf(railColor)
    )
    views.setTextViewText(R.id.widget_row_title, occurrence.planName)
    views.setTextViewText(
        R.id.widget_row_meta,
        context.getString(
            R.string.widget_route_dose,
            routeLabel(context, occurrence.routeKey),
            formatDose(occurrence.doseMg)
        )
    )
    views.setTextColor(R.id.widget_row_title, palette.onSurface)
    views.setTextColor(R.id.widget_row_meta, palette.onSurfaceVariant)
    views.setTextColor(R.id.widget_row_status, palette.onSurfaceVariant)
    val actionStyle = occurrence.action.buttonStyle(palette)
    views.setInt(
        R.id.widget_row_action,
        "setBackgroundResource",
        when (actionStyle.treatment) {
            WidgetActionButtonTreatment.OUTLINED -> R.drawable.widget_action_button_outline
            WidgetActionButtonTreatment.TONAL -> R.drawable.widget_action_button_tonal
        }
    )
    views.setColorStateList(
        R.id.widget_row_action,
        "setBackgroundTintList",
        ColorStateList.valueOf(actionStyle.containerColor)
    )
    views.setInt(R.id.widget_row_action, "setColorFilter", actionStyle.iconColor)
    views.setImageViewResource(R.id.widget_row_action, occurrence.action.iconRes())
    views.setTextViewTextSize(
        R.id.widget_row_title,
        TypedValue.COMPLEX_UNIT_SP,
        rowLayout.titleTextSp.toFloat()
    )
    views.setTextViewTextSize(
        R.id.widget_row_meta,
        TypedValue.COMPLEX_UNIT_SP,
        rowLayout.metadataTextSp.toFloat()
    )
    views.setTextViewTextSize(
        R.id.widget_row_status,
        TypedValue.COMPLEX_UNIT_SP,
        rowLayout.statusTextSp.toFloat()
    )
    views.setViewLayoutHeight(
        R.id.widget_row_root,
        rowLayout.rowHeightDp.toFloat(),
        TypedValue.COMPLEX_UNIT_DIP
    )
    views.setViewLayoutHeight(
        R.id.widget_row_rail,
        rowLayout.railHeightDp.toFloat(),
        TypedValue.COMPLEX_UNIT_DIP
    )
    views.setViewLayoutWidth(
        R.id.widget_row_action_hit,
        rowLayout.actionTouchTargetDp.toFloat(),
        TypedValue.COMPLEX_UNIT_DIP
    )
    views.setViewLayoutHeight(
        R.id.widget_row_action_hit,
        rowLayout.actionTouchTargetDp.toFloat(),
        TypedValue.COMPLEX_UNIT_DIP
    )
    views.setViewLayoutWidth(
        R.id.widget_row_action,
        rowLayout.actionContainerSizeDp.toFloat(),
        TypedValue.COMPLEX_UNIT_DIP
    )
    views.setViewLayoutHeight(
        R.id.widget_row_action,
        rowLayout.actionContainerSizeDp.toFloat(),
        TypedValue.COMPLEX_UNIT_DIP
    )
    val actionPaddingPx = context.dpToPx(
        (rowLayout.actionContainerSizeDp - rowLayout.actionSizeDp) / 2
    )
    views.setViewPadding(
        R.id.widget_row_action,
        actionPaddingPx,
        actionPaddingPx,
        actionPaddingPx,
        actionPaddingPx
    )
    val verticalPaddingPx = context.dpToPx(rowLayout.verticalPaddingDp)
    views.setViewPadding(R.id.widget_row_root, 0, verticalPaddingPx, 0, verticalPaddingPx)
    if (collectionItem) {
        views.setOnClickFillInIntent(
            R.id.widget_row_root,
            widgetOpenAppFillInIntent(appWidgetId, occurrence.occurrenceId)
        )
    } else {
        views.setOnClickPendingIntent(R.id.widget_row_root, openApp)
    }

    when (occurrence.action) {
        WidgetRowAction.COMPLETED -> {
            views.setTextViewText(R.id.widget_row_status, context.getString(R.string.widget_completed))
        }
        WidgetRowAction.RECORD -> {
            views.setTextViewText(
                R.id.widget_row_status,
                formatTime(context, occurrence.scheduledLocalDateTime, timeFormat)
            )
            if (collectionItem) {
                views.setOnClickFillInIntent(
                    R.id.widget_row_action_hit,
                    widgetOccurrenceActionIntent(context, occurrence, appWidgetId)
                )
            } else {
                views.setOnClickPendingIntent(
                    R.id.widget_row_action_hit,
                    recordOccurrencePendingIntent(context, occurrence, appWidgetId)
                )
            }
        }
    }
    return views
}

internal fun WidgetRowAction.iconRes(): Int = when (this) {
    WidgetRowAction.RECORD -> R.drawable.ic_widget_check
    WidgetRowAction.COMPLETED -> R.drawable.ic_widget_check_circle
}

private fun routeLabel(context: Context, routeKey: String): String = when (routeKey) {
    "INJECTION" -> context.getString(R.string.route_injection)
    "ORAL" -> context.getString(R.string.route_oral)
    "SUBLINGUAL" -> context.getString(R.string.route_sublingual)
    "GEL" -> context.getString(R.string.route_gel)
    "PATCH_APPLY" -> context.getString(R.string.route_patch_apply)
    "PATCH_REMOVE" -> context.getString(R.string.route_patch_remove)
    "ANTIANDROGEN" -> context.getString(R.string.route_antiandrogen)
    else -> routeKey
}

private fun formatTime(context: Context, value: LocalDateTime, format: TimeFormat): String {
    val use24 = when (format) {
        TimeFormat.HOUR_24 -> true
        TimeFormat.HOUR_12 -> false
        TimeFormat.SYSTEM -> android.text.format.DateFormat.is24HourFormat(context)
    }
    return DateTimeFormatter.ofPattern(
        if (use24) "HH:mm" else "h:mm a",
        Locale.getDefault()
    ).format(value)
}

internal fun recordOccurrencePendingIntent(
    context: Context,
    occurrence: WidgetOccurrenceUi,
    appWidgetId: Int
): PendingIntent = PendingIntent.getBroadcast(
    context,
    appWidgetId xor occurrence.occurrenceId.hashCode(),
    widgetOccurrenceActionIntent(context, occurrence, appWidgetId),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

internal fun widgetOccurrenceActionIntent(
    context: Context,
    occurrence: WidgetOccurrenceUi,
    appWidgetId: Int
): Intent = Intent(context, EvoluneWidgetReceiver::class.java).apply {
        action = ACTION_RECORD_OCCURRENCE
        putExtra(EXTRA_PLAN_ID, occurrence.planId.toString())
        putExtra(EXTRA_SLOT_ID, occurrence.slotId.toString())
        putExtra(EXTRA_SCHEDULED_LOCAL_DATE, occurrence.scheduledLocalDate.toString())
        putExtra(EXTRA_OCCURRENCE_ID, occurrence.occurrenceId.toString())
        putExtra(EXTRA_WIDGET_ID, appWidgetId)
        data = "evolune://widget/$appWidgetId/occurrence/${occurrence.occurrenceId}".toUri()
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }

internal fun widgetCollectionPendingIntentTemplate(
    context: Context,
    appWidgetId: Int
): PendingIntent = PendingIntent.getBroadcast(
    context,
    appWidgetId,
    Intent(context, EvoluneWidgetReceiver::class.java).apply {
        data = "evolune://widget/$appWidgetId/collection".toUri()
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
)

internal fun widgetOpenAppFillInIntent(appWidgetId: Int, occurrenceId: java.util.UUID): Intent =
    Intent().apply {
        action = ACTION_OPEN_WIDGET_APP
        putExtra(EXTRA_WIDGET_ID, appWidgetId)
        data = "evolune://widget/$appWidgetId/open/$occurrenceId".toUri()
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }

private fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).roundToInt()

private fun openAppPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
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
            SettingsDataStore(context.applicationContext).userSettings.first().bodyWeight
        }
    ).load().concentration
}

internal suspend fun requestEvoluneWidgetUpdate(
    context: Context,
    reason: WidgetUpdateReason,
    appWidgetIds: IntArray? = null
) {
    val applicationContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(applicationContext)
    val component = ComponentName(applicationContext, EvoluneWidgetReceiver::class.java)
    val ids = appWidgetIds ?: manager.getAppWidgetIds(component)
    val work = createProductionWidgetUpdateWork(applicationContext, manager)
    ContractWidgetUpdateCoordinator { work.handle(ids) }.request(reason)
}

private fun createProductionWidgetUpdateWork(
    context: Context,
    appWidgetManager: AppWidgetManager
): WidgetUpdateWork {
    val repositories = ProductionRepositoryProvider.get(context)
    val settingsStore = SettingsDataStore(context)
    var cachedSettings: io.github.yingqiu0871.evolune.data.UserSettings? = null
    val readSettings: suspend () -> io.github.yingqiu0871.evolune.data.UserSettings = {
        cachedSettings ?: settingsStore.userSettings.first().also { cachedSettings = it }
    }
    return ContractWidgetUpdateWork(
        snapshotLoader = WidgetSnapshotLoader(
            medicationPlans = repositories.medicationPlans,
            doseEvents = repositories.doseEvents,
            bodyWeight = { readSettings().bodyWeight },
            timeFormat = { readSettings().timeFormat }
        ),
        renderer = WidgetSnapshotRenderer { appWidgetId, state ->
            renderWidget(context, appWidgetManager, appWidgetId, state)
        }
    )
}

private fun formatDose(doseMg: Double): String = if (doseMg % 1.0 == 0.0) {
    doseMg.toInt().toString()
} else {
    "%.2f".format(doseMg)
}
