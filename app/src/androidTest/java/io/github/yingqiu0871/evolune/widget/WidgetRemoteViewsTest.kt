package io.github.yingqiu0871.evolune.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class WidgetRemoteViewsTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun responsiveLayoutsAndDynamicChildrenInflateWithExpectedStructure() {
        listOf(
            R.layout.widget_evolune_compact,
            R.layout.widget_evolune,
            R.layout.widget_evolune_wide,
            R.layout.widget_evolune_expanded
        ).forEach { layout ->
            val root = inflate(layout)
            assertCommonViews(root)
            assertNotNull(root.findViewById<View>(R.id.widget_split_label))
            assertNotNull(root.findViewById<View>(R.id.widget_split_value_panel))
        }

        val chart = inflate(R.layout.widget_chart_segment)
        assertNotNull(chart.findViewById<View>(R.id.widget_chart_segment))
        layoutsForAxis().forEach { layout ->
            assertNotNull(inflate(layout).findViewById<View>(R.id.widget_chart_axis))
        }

        val row = inflate(R.layout.widget_medication_row)
        assertNotNull(row.findViewById<View>(R.id.widget_row_root))
        assertNotNull(row.findViewById<View>(R.id.widget_row_rail))
        assertNotNull(row.findViewById<View>(R.id.widget_row_title))
        assertNotNull(row.findViewById<View>(R.id.widget_row_meta))
        assertNotNull(row.findViewById<View>(R.id.widget_row_status))
        assertNotNull(row.findViewById<View>(R.id.widget_row_action_hit))
        assertNotNull(row.findViewById<View>(R.id.widget_row_action))

        val segment = inflate(R.layout.widget_progress_segment)
        assertNotNull(segment.findViewById<View>(R.id.widget_progress_segment))
        val spacer = inflate(R.layout.widget_row_spacer)
        assertNotNull(spacer.findViewById<View>(R.id.widget_row_spacer))
    }

    @Test
    fun pickerPreviewsInflateWithRemoteViewsAllowedViews() {
        listOf(
            R.layout.widget_preview_today_plan,
            R.layout.widget_preview_next_dose,
            R.layout.widget_preview_current_e2,
            R.layout.widget_preview_pk_chart
        ).forEach { layout ->
            assertNotNull(inflate(layout))
        }
    }

    @Test
    fun perWidgetAppearancePersistsIndependentlyAndDeletionIsScoped() {
        val store = WidgetAppearanceStore(context)
        val firstId = 900_101
        val secondId = 900_102
        val first = WidgetAppearanceConfig(
            WidgetThemeMode.DARK,
            WidgetColorScheme.MONET_VIOLET,
            0.4f,
            WidgetStyle.PK_CHART
        )
        val second = WidgetAppearanceConfig(
            WidgetThemeMode.LIGHT,
            WidgetColorScheme.MATERIAL_YOU_AUTO,
            1f
        )
        try {
            store.write(firstId, first)
            store.write(secondId, second)
            val reconstructedStore = WidgetAppearanceStore(context)
            assertEquals(first, reconstructedStore.read(firstId))
            assertEquals(second, reconstructedStore.read(secondId))

            store.delete(firstId)
            assertEquals(WidgetAppearanceConfig.Default, store.read(firstId))
            assertEquals(second, store.read(secondId))
        } finally {
            store.delete(firstId)
            store.delete(secondId)
        }
    }

    @Test
    fun desktopRowsUseEachWidgetsResolvedOpaqueRailRoles() {
        val store = WidgetAppearanceStore(context)
        val firstId = 900_201
        val secondId = 900_202
        val configs = listOf(
            firstId to WidgetAppearanceConfig(
                WidgetThemeMode.LIGHT,
                WidgetColorScheme.MONET_VIOLET,
                0.3f
            ),
            secondId to WidgetAppearanceConfig(
                WidgetThemeMode.DARK,
                WidgetColorScheme.MONET_AMBER,
                1f
            )
        )
        val rowLayout = WidgetRowDensityPolicy.resolve(
            WidgetSizePolicy.resolve(WidgetSize(150, 213)),
            3
        )
        val openApp = android.app.PendingIntent.getActivity(
            context,
            702,
            Intent(context, WidgetConfigurationActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            configs.forEach { (appWidgetId, config) -> store.write(appWidgetId, config) }
            val palettes = configs.map { (appWidgetId, _) ->
                val palette = WidgetPaletteResolver.resolve(context, store.read(appWidgetId))
                (0..2).forEach { railRoleIndex ->
                    val row = medicationRow(
                        context,
                        occurrence(MedicationOccurrenceStatus.DUE),
                        TimeFormat.HOUR_24,
                        palette,
                        rowLayout,
                        appWidgetId,
                        openApp,
                        railRoleIndex
                    ).apply(context, FrameLayout(context))
                    val rail = row.findViewById<View>(R.id.widget_row_rail)
                    val actual = rail.backgroundTintList?.defaultColor
                    assertEquals(palette.medicationRailColor(railRoleIndex), actual)
                    assertEquals(0xFF, requireNotNull(actual) ushr 24)
                }
                palette
            }
            assertNotEquals(palettes[0].medicationRailRoles, palettes[1].medicationRailRoles)
        } finally {
            store.delete(firstId)
            store.delete(secondId)
        }
    }

    @Test
    fun allProvidersRequireTheOfficialConfigurationActivityAndSupportReconfiguration() {
        val manager = AppWidgetManager.getInstance(context)
        WidgetProviderCatalog.providerClasses.forEach { receiver ->
            val component = ComponentName(context, receiver)
            val provider = manager.installedProviders.single { it.provider == component }

            assertEquals(
                ComponentName(context, WidgetConfigurationActivity::class.java),
                provider.configure
            )
            assertTrue(
                provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
            )
            assertEquals(
                0,
                provider.widgetFeatures and
                    AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
            )
        }
    }

    @Test
    fun configurationIntentTargetsTheExactWidgetInstance() {
        val intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 314)

        assertEquals(314, configuredAppWidgetId(intent))
        assertEquals(AppWidgetManager.INVALID_APPWIDGET_ID, configuredAppWidgetId(Intent()))
        assertEquals(AppWidgetManager.INVALID_APPWIDGET_ID, configuredAppWidgetId(null))
    }

    @Test
    fun actionableOccurrenceCarriesExactIdentityAndBuildsCheckPendingIntent() {
        val occurrence = occurrence(MedicationOccurrenceStatus.DUE)
        val intent = widgetOccurrenceActionIntent(context, occurrence, 314)

        assertEquals(ACTION_RECORD_OCCURRENCE, intent.action)
        assertEquals(occurrence.planId.toString(), intent.getStringExtra(EXTRA_PLAN_ID))
        assertEquals(occurrence.slotId.toString(), intent.getStringExtra(EXTRA_SLOT_ID))
        assertEquals(
            occurrence.scheduledLocalDate.toString(),
            intent.getStringExtra(EXTRA_SCHEDULED_LOCAL_DATE)
        )
        assertEquals(
            occurrence.occurrenceId.toString(),
            intent.getStringExtra(EXTRA_OCCURRENCE_ID)
        )
        assertEquals(314, intent.getIntExtra(EXTRA_WIDGET_ID, -1))
        assertNotNull(recordOccurrencePendingIntent(context, occurrence, 314))
    }

    private fun layoutsForAxis() = listOf(
        R.layout.widget_evolune_compact,
        R.layout.widget_evolune,
        R.layout.widget_evolune_wide,
        R.layout.widget_evolune_expanded
    )

    @Test
    fun openAppFillInCarriesReadOnlyWidgetOccurrenceIdentity() {
        val occurrence = occurrence(MedicationOccurrenceStatus.UPCOMING)
        val intent = widgetOpenAppFillInIntent(314, occurrence.occurrenceId)

        assertEquals(ACTION_OPEN_WIDGET_APP, intent.action)
        assertEquals(314, intent.getIntExtra(EXTRA_WIDGET_ID, -1))
        assertEquals(
            "evolune://widget/314/open/${occurrence.occurrenceId}",
            intent.dataString
        )
        assertEquals(null, intent.getStringExtra(EXTRA_PLAN_ID))
        assertEquals(null, intent.getStringExtra(EXTRA_SLOT_ID))
        assertEquals(null, intent.getStringExtra(EXTRA_SCHEDULED_LOCAL_DATE))
        assertEquals(null, intent.getStringExtra(EXTRA_OCCURRENCE_ID))
    }

    @Test
    fun openAppReceiverLaunchesMainActivityWithoutStartingQuickActionWork() {
        val started = RecordingStartActivityContext(context)
        var quickActionFactoryCalls = 0
        val receiver = EvoluneWidgetReceiver(
            quickActionWorkFactory = {
                quickActionFactoryCalls += 1
                WidgetQuickActionWork { WidgetQuickActionOutcome.Accepted(false) }
            },
            updateWorkFactory = { _, _ -> WidgetUpdateWork {} }
        )

        receiver.onReceive(started, Intent(ACTION_OPEN_WIDGET_APP))

        assertEquals(
            ComponentName(context, MainActivity::class.java),
            started.startedIntent?.component
        )
        assertTrue(
            started.startedIntent?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        )
        assertEquals(0, quickActionFactoryCalls)
    }

    @Test
    fun samePlanSlotsHaveDistinctAndStableOccurrencePendingIntents() {
        val base = occurrence(MedicationOccurrenceStatus.DUE)
        val occurrences = listOf(9, 17, 22).mapIndexed { index, hour ->
            base.copy(
                occurrenceId = UUID(0L, 710L + index),
                slotId = UUID(0L, 720L + index),
                scheduledLocalDateTime = LocalDateTime.of(2027, 1, 15, hour, 0)
            )
        }
        val pendingIntents = occurrences.map { occurrence ->
            recordOccurrencePendingIntent(context, occurrence, 314)
        }
        val repeated = recordOccurrencePendingIntent(context, occurrences[1], 314)

        try {
            assertEquals(3, pendingIntents.distinct().size)
            assertNotEquals(pendingIntents[0], pendingIntents[1])
            assertNotEquals(pendingIntents[1], pendingIntents[2])
            assertEquals(pendingIntents[1], repeated)
        } finally {
            (pendingIntents + repeated).distinct().forEach { it.cancel() }
        }
    }

    @Test
    fun actionableAndCompletedChecksKeepStableCircularGeometry() {
        val rowLayout = WidgetRowDensityPolicy.resolve(
            WidgetSizePolicy.resolve(WidgetSize(150, 213)),
            3
        )
        val palette = WidgetPaletteResolver.resolve(context, WidgetAppearanceConfig.Default)
        val openApp = android.app.PendingIntent.getActivity(
            context,
            701,
            Intent(context, WidgetConfigurationActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )
        fun render(status: MedicationOccurrenceStatus): ImageView = medicationRow(
            context,
            occurrence(status),
            TimeFormat.HOUR_24,
            palette,
            rowLayout,
            314,
            openApp
        ).apply(context, FrameLayout(context)).findViewById(R.id.widget_row_action)

        val actionable = render(MedicationOccurrenceStatus.DUE)
        val completed = render(MedicationOccurrenceStatus.RECORDED)

        assertEquals(actionable.layoutParams.width, completed.layoutParams.width)
        assertEquals(actionable.layoutParams.height, completed.layoutParams.height)
        assertEquals(actionable.layoutParams.width, actionable.layoutParams.height)
        val actionableCircle = (actionable.background as InsetDrawable).drawable as GradientDrawable
        val completedCircle = (completed.background as InsetDrawable).drawable as GradientDrawable
        assertTrue(actionableCircle.cornerRadius >=
            actionable.layoutParams.width / 2f)
        assertTrue(completedCircle.cornerRadius >=
            completed.layoutParams.width / 2f)
        assertEquals(palette.primaryForeground, actionable.backgroundTintList?.defaultColor)
        assertEquals(palette.primaryContainer, completed.backgroundTintList?.defaultColor)
        assertNotEquals(
            actionable.backgroundTintList?.defaultColor,
            completed.backgroundTintList?.defaultColor
        )
    }

    @Test
    fun oneTwoThreeRowDensityInflatesAtBoundedPhysicalSizes() {
        val layout = WidgetSizePolicy.resolve(WidgetSize(150, 213))
        listOf(1, 2, 3).forEach { count ->
            val rowLayout = WidgetRowDensityPolicy.resolve(layout, count)
            listOf(
                MedicationOccurrenceStatus.DUE,
                MedicationOccurrenceStatus.RECORDED
            ).forEach { status ->
                val row = medicationRow(
                    context = context,
                    occurrence = occurrence(status),
                    timeFormat = TimeFormat.HOUR_24,
                    palette = WidgetPaletteResolver.resolve(context, WidgetAppearanceConfig.Default),
                    rowLayout = rowLayout,
                    appWidgetId = 314,
                    openApp = android.app.PendingIntent.getActivity(
                        context,
                        count,
                        Intent(context, WidgetConfigurationActivity::class.java),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                            android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                ).apply(context, FrameLayout(context))
                val rowWidthPx = context.dpToPx(220)
                val rowHeightPx = context.dpToPx(rowLayout.rowHeightDp)
                row.measure(
                    View.MeasureSpec.makeMeasureSpec(rowWidthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(rowHeightPx, View.MeasureSpec.EXACTLY)
                )
                row.layout(0, 0, row.measuredWidth, row.measuredHeight)

                val hitTarget = row.findViewById<View>(R.id.widget_row_action_hit)
                val visibleButton = row.findViewById<View>(R.id.widget_row_action)
                assertNotNull(hitTarget)
                assertNotNull(visibleButton)
                assertEquals(
                    context.dpToPx(rowLayout.actionTouchTargetDp),
                    hitTarget.layoutParams.width
                )
                assertEquals(
                    context.dpToPx(rowLayout.actionTouchTargetDp),
                    hitTarget.layoutParams.height
                )
                assertEquals(
                    context.dpToPx(rowLayout.actionContainerSizeDp),
                    visibleButton.layoutParams.width
                )
                assertEquals(
                    context.dpToPx(rowLayout.actionContainerSizeDp),
                    visibleButton.layoutParams.height
                )
                assertTrue(hitTarget.top >= row.top)
                assertTrue(hitTarget.bottom <= row.bottom)
                assertTrue(visibleButton.top >= 0)
                assertTrue(visibleButton.bottom <= hitTarget.height)
                visibleButton.background.setBounds(
                    0,
                    0,
                    visibleButton.width,
                    visibleButton.height
                )
                val circle = requireNotNull(
                    (visibleButton.background as InsetDrawable).drawable
                )
                assertTrue(circle.bounds.top > 0)
                assertTrue(circle.bounds.bottom < visibleButton.height)
                val visibleCircleDp =
                    circle.bounds.width() / context.resources.displayMetrics.density
                assertTrue(
                    "visibleCircleDp=$visibleCircleDp bounds=${circle.bounds} " +
                        "button=${visibleButton.width}x${visibleButton.height}",
                    visibleCircleDp in 29.5f..32.75f
                )
            }
            assertTrue(rowLayout.rowHeightDp in 44..72)
            assertTrue(rowLayout.titleTextSp in 13..16)
        }
    }

    private fun inflate(layoutId: Int): View = RemoteViews(context.packageName, layoutId)
        .apply(context, FrameLayout(context))

    private fun assertCommonViews(root: View) {
        assertNotNull(root.findViewById<View>(R.id.widget_root))
        assertNotNull(root.findViewById<View>(R.id.widget_summary))
        assertNotNull(root.findViewById<View>(R.id.widget_concentration))
        assertNotNull(root.findViewById<View>(R.id.widget_progress_container))
        assertNotNull(root.findViewById<View>(R.id.widget_rows_container))
        assertNotNull(root.findViewById<View>(R.id.widget_empty_area))
        assertNotNull(root.findViewById<View>(R.id.widget_empty_title))
        assertNotNull(root.findViewById<View>(R.id.widget_empty_meta))
    }

    private fun occurrence(status: MedicationOccurrenceStatus): WidgetOccurrenceUi =
        WidgetOccurrenceUi(
            occurrenceId = UUID(0L, 700L),
            planId = UUID(0L, 701L),
            slotId = UUID(0L, 702L),
            scheduledLocalDate = LocalDate.parse("2027-01-15"),
            planName = "Estradiol",
            routeKey = "ORAL",
            scheduledLocalDateTime = LocalDateTime.parse("2027-01-15T09:00:00"),
            doseMg = 2.0,
            status = status,
            action = when (status) {
                MedicationOccurrenceStatus.DUE -> WidgetRowAction.RECORD
                MedicationOccurrenceStatus.RECORDED -> WidgetRowAction.COMPLETED
                MedicationOccurrenceStatus.UPCOMING,
                MedicationOccurrenceStatus.PAST_UNRECORDED -> WidgetRowAction.OPEN_APP
            },
            statusPresentation = if (status == MedicationOccurrenceStatus.RECORDED) {
                WidgetRowStatusPresentation.COMPLETED
            } else {
                WidgetRowStatusPresentation.SCHEDULED_TIME
            }
        )

    private fun android.content.Context.dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    private class RecordingStartActivityContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            startedIntent = Intent(intent)
        }
    }
}
