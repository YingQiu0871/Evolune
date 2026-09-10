package io.github.yingqiu0871.evolune.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.createBitmap
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.expression.dynamicDataMapOf
import androidx.wear.protolayout.expression.mapTo
import androidx.wear.protolayout.expression.stringAppDataKey
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.TITLE_SMALL
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import java.nio.ByteBuffer
import java.util.UUID

private const val RESOURCES_VERSION_PREFIX = "9"
private const val DOSE_ACTIONS_PATH_PREFIX = "/hrt/dose-actions"
private const val CHART_RESOURCE_ID = "concentration_chart"
private const val CHART_WIDTH_PX = 240
private const val CHART_HEIGHT_PX = 72
private const val SENT_FEEDBACK_MILLIS = 1_000L
private val SELECTED_PLAN_KEY = stringAppDataKey("selected_plan_id")

class DoseTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ) = Futures.immediateFuture(
        buildTile(requestParams)
    )

    override fun onTileResourcesRequest(
        requestParams: ResourcesRequest
    ) = Futures.immediateFuture(buildResources())

    private fun buildTile(requestParams: RequestBuilders.TileRequest): Tile {
        WearSyncManager.requestPlansFromPhone(this)
        val dashboard = WearPlanStore.getDashboard(this)
        val plans = dashboard.plans
        val nowMillis = System.currentTimeMillis()
        val dashboardState = WearPlanStore.getPresentationState(
            this,
            dashboard,
            nowMillis
        )
        val selectedPlanId = requestParams.currentState.stateMap[SELECTED_PLAN_KEY]
        if (canSendDoseAction(dashboardState, selectedPlanId, plans)) {
            enqueueDoseAction(requireNotNull(selectedPlanId))
            WearPlanStore.markSent(
                this,
                selectedPlanId,
                nowMillis
            )
            scheduleSentFeedbackClear()
        }

        val lastSentPlanId = WearPlanStore.recentSentPlanId(
            this,
            nowMillis,
            SENT_FEEDBACK_MILLIS
        )
        val scheme = WearAppearanceStore.read(this)
        val palette = WearAppearanceStore.resolve(this, scheme)
        val layout = materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                titleSlot = {
                    text(
                        dashboard.concentrationAt(nowMillis)?.let {
                            "E2 · %.1f pg/mL".format(it)
                        }?.layoutString ?: "E2 浓度".layoutString,
                        typography = TITLE_SMALL,
                        color = LayoutColor(palette.onBackground)
                    )
                },
                mainSlot = {
                    val column = LayoutElementBuilders.Column.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHorizontalAlignment(
                            LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
                        )
                    if (dashboard.curveValues.isNotEmpty()) {
                        column.addContent(
                            LayoutElementBuilders.Image.Builder()
                                .setResourceId(CHART_RESOURCE_ID)
                                .setWidth(DimensionBuilders.dp(150f))
                                .setHeight(DimensionBuilders.dp(43f))
                                .setContentScaleMode(
                                    LayoutElementBuilders.CONTENT_SCALE_MODE_FIT
                                )
                                .build()
                        )
                        column.addContent(
                            LayoutElementBuilders.Spacer.Builder()
                                .setHeight(DimensionBuilders.dp(12f))
                                .build()
                        )
                    }
                    if (dashboardState != WearDashboardState.READY) {
                        column.addContent(
                            text(
                                requireNotNull(dashboardState.displayMessage()).layoutString,
                                typography = BODY_MEDIUM,
                                color = LayoutColor(palette.onSurfaceVariant),
                                maxLines = 2
                            )
                        )
                    } else {
                        val buttonWidth = 76f
                        val buttonSpacing = 8f
                        val rowWidth =
                            plans.size * buttonWidth +
                                (plans.size - 1) * buttonSpacing
                        val buttonRow = LayoutElementBuilders.Row.Builder()
                            .setWidth(DimensionBuilders.dp(rowWidth))
                            .setHeight(DimensionBuilders.dp(46f))
                            .setVerticalAlignment(
                                LayoutElementBuilders.VERTICAL_ALIGN_CENTER
                            )
                        plans.forEachIndexed { index, plan ->
                            buttonRow.addContent(
                                legacyActionButton(
                                    label = run {
                                        val prefix = if (lastSentPlanId == plan.id) "✓ " else ""
                                        "$prefix${plan.name}\n${formatDose(plan.doseMG)}mg"
                                    },
                                    palette = palette,
                                    onClick = clickable(
                                        action = loadAction(
                                            dynamicDataMapOf(
                                                SELECTED_PLAN_KEY mapTo plan.id
                                            )
                                        )
                                    ),
                                    widthDp = buttonWidth,
                                    heightDp = 46f
                                )
                            )
                            if (index < plans.lastIndex) {
                                buttonRow.addContent(
                                    LayoutElementBuilders.Spacer.Builder()
                                        .setWidth(
                                            DimensionBuilders.dp(buttonSpacing)
                                        )
                                        .build()
                                )
                            }
                        }
                        column.addContent(buttonRow.build())
                    }
                    column.build()
                }
            )
        }

        return Tile.Builder()
            .setResourcesVersion(resourcesVersion(dashboard, nowMillis, scheme))
            .setFreshnessIntervalMillis(WEAR_TILE_FRESHNESS_MILLIS)
            .setTileTimeline(Timeline.fromLayoutElement(layout))
            .setState(StateBuilders.State.Builder().build())
            .build()
    }

    private fun buildResources(): Resources {
        val dashboard = WearPlanStore.getDashboard(this)
        val nowMillis = System.currentTimeMillis()
        val scheme = WearAppearanceStore.read(this)
        val palette = WearAppearanceStore.resolve(this, scheme)
        val builder = Resources.Builder()
            .setVersion(resourcesVersion(dashboard, nowMillis, scheme))
        if (dashboard.curveValues.isNotEmpty()) {
            builder.addIdToImageMapping(
                CHART_RESOURCE_ID,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(
                                renderChart(
                                    dashboard.curveValues,
                                    dashboard.currentCurvePosition(nowMillis),
                                    palette
                                )
                            )
                            .setWidthPx(CHART_WIDTH_PX)
                            .setHeightPx(CHART_HEIGHT_PX)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_ARGB_8888)
                            .build()
                    )
                    .build()
            )
        }
        return builder.build()
    }

    private fun enqueueDoseAction(planId: String) {
        val actionId = UUID.randomUUID().toString()
        val request = PutDataMapRequest.create(
            "$DOSE_ACTIONS_PATH_PREFIX/$actionId"
        ).apply {
            dataMap.putString("plan_id", planId)
            dataMap.putString("action_id", actionId)
            dataMap.putLong("recorded_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(request)
    }

    private fun scheduleSentFeedbackClear() {
        Handler(Looper.getMainLooper()).postDelayed(
            {
                WearPlanStore.clearSentFeedback(applicationContext)
                TileService.getUpdater(applicationContext)
                    .requestUpdate(DoseTileService::class.java)
            },
            SENT_FEEDBACK_MILLIS
        )
    }

    private fun formatDose(doseMG: Double): String =
        if (doseMG % 1.0 == 0.0) {
            doseMG.toInt().toString()
        } else {
            "%.2f".format(doseMG)
        }
}

private fun resourcesVersion(
    dashboard: WearDashboard,
    nowMillis: Long,
    scheme: WearColorScheme
): String =
    "$RESOURCES_VERSION_PREFIX-${scheme.name}-${dashboard.updatedAt}-${nowMillis / 300_000L}"

private fun renderChart(
    values: List<Float>,
    currentPosition: Float,
    palette: WearPalette
): ByteArray {
    val bitmap = createBitmap(
        CHART_WIDTH_PX,
        CHART_HEIGHT_PX,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.TRANSPARENT)
    val maxValue = maxOf(250f, (values.maxOrNull() ?: 0f) * 1.1f)
    val plotLeft = 30f
    val plotRight = CHART_WIDTH_PX - plotLeft
    val plotTop = 3f
    val plotBottom = CHART_HEIGHT_PX - 16f
    val plotWidth = plotRight - plotLeft
    val plotHeight = plotBottom - plotTop
    fun y(value: Float): Float =
        plotBottom -
            value.coerceIn(0f, maxValue) / maxValue * plotHeight

    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(palette.outline, 90)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(palette.onSurfaceVariant, 190)
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurfaceVariant
        textSize = 9f
    }
    listOf(0f, 100f, 200f).forEach { tick ->
        val tickY = y(tick)
        canvas.drawLine(plotLeft, tickY, plotRight, tickY, gridPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            tick.toInt().toString(),
            plotLeft - 4f,
            tickY + 3f,
            labelPaint
        )
    }
    canvas.drawLine(
        plotLeft,
        plotTop,
        plotLeft,
        plotBottom,
        axisPaint
    )
    canvas.drawLine(
        plotLeft,
        plotBottom,
        plotRight,
        plotBottom,
        axisPaint
    )

    val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(palette.secondary, 48)
        style = Paint.Style.FILL
    }
    canvas.drawRect(
        plotLeft,
        y(200f),
        plotRight,
        y(100f),
        targetPaint
    )

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.primary
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = if (values.size <= 1) {
            plotLeft + plotWidth / 2f
        } else {
            plotLeft + index.toFloat() / (values.size - 1) * plotWidth
        }
        if (index == 0) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
    }
    canvas.drawPath(path, linePaint)

    val pointPosition = currentPosition.coerceIn(0f, 1f)
    val valuePosition = pointPosition * values.lastIndex
    val lowerIndex = valuePosition.toInt()
    val upperIndex = minOf(lowerIndex + 1, values.lastIndex)
    val ratio = valuePosition - lowerIndex
    val currentValue = values[lowerIndex] +
        (values[upperIndex] - values[lowerIndex]) * ratio
    val currentX = if (values.size <= 1) {
        plotLeft + plotWidth / 2f
    } else {
        plotLeft + pointPosition * plotWidth
    }
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.tertiary
        style = Paint.Style.FILL
        canvas.drawCircle(currentX, y(currentValue), 4.5f, this)
    }

    labelPaint.textSize = 8f
    labelPaint.textAlign = Paint.Align.LEFT
    canvas.drawText("-12h", plotLeft, CHART_HEIGHT_PX - 3f, labelPaint)
    labelPaint.textAlign = Paint.Align.CENTER
    canvas.drawText(
        "现在",
        plotLeft + plotWidth / 2f,
        CHART_HEIGHT_PX - 3f,
        labelPaint
    )
    labelPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText("+12h", plotRight, CHART_HEIGHT_PX - 3f, labelPaint)

    val buffer = ByteBuffer.allocate(bitmap.byteCount)
    bitmap.copyPixelsToBuffer(buffer)
    bitmap.recycle()
    return buffer.array()
}

private fun androidx.wear.protolayout.material3.MaterialScope.legacyActionButton(
    label: String,
    palette: WearPalette,
    onClick: ModifiersBuilders.Clickable,
    widthDp: Float,
    heightDp: Float
): LayoutElementBuilders.LayoutElement {
    val background = ModifiersBuilders.Background.Builder()
        .setColor(ColorBuilders.ColorProp.Builder(palette.primaryContainer).build())
        .setCorner(
            ModifiersBuilders.Corner.Builder()
                .setRadius(DimensionBuilders.dp(WEAR_CARD_CORNER_RADIUS_DP))
                .build()
        )
        .build()
    val modifiers = ModifiersBuilders.Modifiers.Builder()
        .setBackground(background)
        .setClickable(onClick)
        .setPadding(
            ModifiersBuilders.Padding.Builder()
                .setStart(DimensionBuilders.dp(7f))
                .setEnd(DimensionBuilders.dp(7f))
                .setTop(DimensionBuilders.dp(5f))
                .setBottom(DimensionBuilders.dp(5f))
                .build()
        )
        .build()
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.dp(widthDp))
        .setHeight(DimensionBuilders.dp(heightDp))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(modifiers)
        .addContent(
            text(
                label.layoutString,
                typography = BODY_MEDIUM,
                color = LayoutColor(palette.onPrimaryContainer),
                maxLines = 2
            )
        )
        .build()
}

private fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00ffffff) or (alpha.coerceIn(0, 255) shl 24)
