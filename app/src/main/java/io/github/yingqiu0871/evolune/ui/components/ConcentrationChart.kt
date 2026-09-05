package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.diagnostics.RecordComposeRecomposition
import io.github.yingqiu0871.evolune.pk.SimulationResult
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.ceil
import kotlin.math.log10

private const val MAX_X_AXIS_LABEL_COUNT = 6
private const val GAHT_TARGET_MIN_PG_ML = 100.0
private const val GAHT_TARGET_MAX_PG_ML = 200.0

/**
 * Returns the number of X-axis labels that fit without overlapping.
 *
 * Keeping this calculation independent from Compose drawing makes the narrow-screen
 * behavior deterministic and easy to cover with unit tests.
 */
internal fun calculateXAxisLabelCount(
    chartWidthPx: Float,
    labelWidthPx: Float,
    minimumGapPx: Float
): Int {
    if (!chartWidthPx.isFinite() || !labelWidthPx.isFinite() || !minimumGapPx.isFinite() ||
        chartWidthPx <= 0f || labelWidthPx < 0f || minimumGapPx < 0f
    ) {
        return 2
    }

    val slotWidth = (labelWidthPx + minimumGapPx).coerceAtLeast(1f)
    return (chartWidthPx / slotWidth)
        .toInt()
        .coerceIn(2, MAX_X_AXIS_LABEL_COUNT)
}

internal fun formatYAxisLabel(value: Double, step: Double): String {
    val decimalPlaces = if (step.isFinite() && step in 0.0..1.0 && step > 0.0) {
        ceil(-log10(step)).toInt().coerceIn(0, 6)
    } else {
        0
    }
    return String.format(Locale.getDefault(), "%.${decimalPlaces}f", value)
}

private fun createStarPath(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float
): Path = Path().apply {
    repeat(10) { index ->
        val radius = if (index % 2 == 0) outerRadius else innerRadius
        val angle = -PI / 2.0 + index * PI / 5.0
        val point = Offset(
            x = center.x + (cos(angle) * radius).toFloat(),
            y = center.y + (sin(angle) * radius).toFloat()
        )
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

/**
 * 雌二醇浓度图表组件
 * 使用 Canvas 绘制交互式折线图
 * 
 * 功能：
 * - 支持手势缩放和拖动
 * - 显示坐标轴和网格线
 * - 标记给药时间点和当前时刻
 * - 同时显示基线曲线（无计划）和计划曲线（有计划）
 *
 * @param simulationResult 完整模拟结果（历史+未来计划）
 * @param baselineSimulationResult 基线模拟结果（仅历史，不考虑未来计划）
 * @param currentTimeHState 当前时刻状态（小时）；仅在 Canvas 绘制阶段读取
 * @param doseTimePoints 给药时间点列表（小时）
 * @param forkPointTimeH 分叉点时间（未来第一次计划用药时间），此时刻后主曲线转为计划曲线
 * @param modifier Modifier
 */
@Composable
fun ConcentrationChart(
    simulationResult: SimulationResult,
    baselineSimulationResult: SimulationResult?,
    currentTimeHState: State<Double>,
    doseTimePoints: List<Double>,
    modifier: Modifier = Modifier,
    forkPointTimeHState: State<Double?>? = null,
    is24Hour: Boolean = true
) {
    RecordComposeRecomposition(
        surface = "ConcentrationChart",
        state = "points=${simulationResult.timeH.size}",
        recompositionToken = simulationResult
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // 触摸交互状态
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    var selectedPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) } // (time, conc)

    // 数据范围
    val finiteTimes = simulationResult.timeH.asSequence().filter(Double::isFinite)
    val timeMin = finiteTimes.minOrNull() ?: 0.0
    val timeMax = simulationResult.timeH.asSequence()
        .filter(Double::isFinite)
        .maxOrNull() ?: 1.0
    val totalTimeRange = timeMax - timeMin
    
    // 计算默认显示范围：首次绘制时刻-24小时到+12小时（共36小时）。
    // 后续当前时刻只在 Canvas 绘制阶段读取，不触发图表内容重组。
    val initialCurrentTimeH = remember {
        System.currentTimeMillis() / 3600000.0
    }
    val defaultViewStart = initialCurrentTimeH - 24.0
    val defaultViewEnd = initialCurrentTimeH + 12.0
    val defaultViewRange = defaultViewEnd - defaultViewStart // 36小时
    
    // 计算默认缩放级别
    val initialScale = if (totalTimeRange > 0 && defaultViewRange > 0) {
        (totalTimeRange / defaultViewRange).toFloat().coerceIn(1f, 50f)
    } else 1f

    // 缩放和平移状态（仅针对时间轴）
    var scaleX by remember { mutableFloatStateOf(initialScale) }
    val initialViewportStart = if (totalTimeRange > 0.0) {
        ((defaultViewStart - timeMin) / totalTimeRange)
            .toFloat()
            .coerceIn(0f, (1f - 1f / initialScale).coerceAtLeast(0f))
    } else {
        0f
    }
    var viewportStartFraction by remember { mutableFloatStateOf(initialViewportStart) }
    
    // 仅当模拟结果改变时重置初始化状态（不包括 currentTimeH 变化）
    LaunchedEffect(simulationResult) {
        scaleX = initialScale
        viewportStartFraction = initialViewportStart
    }

    if (simulationResult.timeH.isEmpty() || simulationResult.concPGmL.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.chart_no_data), color = onSurfaceColor)
        }
        return
    }

    val visibleTimeStart = timeMin + viewportStartFraction * totalTimeRange
    val visibleTimeEnd = visibleTimeStart + totalTimeRange / scaleX
    val visibleScale = calculateVisibleWindowYScale(
        series = buildList {
            add(ChartSeries(simulationResult.timeH, simulationResult.concPGmL))
            baselineSimulationResult?.let { baseline ->
                add(ChartSeries(baseline.timeH, baseline.concPGmL))
            }
        },
        visibleStartH = visibleTimeStart,
        visibleEndH = visibleTimeEnd
    )
    val yAxisTextStyle = TextStyle(color = onSurfaceColor, fontSize = 11.sp)
    val yAxisLabelWidthPx = visibleScale.tickValues()
        .maxOf { value ->
            textMeasurer.measure(
                text = formatYAxisLabel(value, visibleScale.tickStep),
                style = yAxisTextStyle
            ).size.width
        }
        .toFloat()
    val labelToAxisGapPx = with(density) { 8.dp.toPx() }
    val outerMarginPx = with(density) { 8.dp.toPx() }
    val topMarginPx = with(density) { 20.dp.toPx() }
    val xAxisGutterPx = with(density) { 50.dp.toPx() }

    fun geometryFor(widthPx: Float, heightPx: Float): PlotGeometry = calculatePlotGeometry(
        contentWidthPx = widthPx,
        contentHeightPx = heightPx,
        yAxisLabelWidthPx = yAxisLabelWidthPx,
        labelToAxisGapPx = labelToAxisGapPx,
        outerMarginPx = outerMarginPx,
        topMarginPx = topMarginPx,
        xAxisGutterPx = xAxisGutterPx,
        dataXMin = visibleTimeStart,
        dataXMax = visibleTimeEnd,
        dataYMax = visibleScale.max
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visibleScale, yAxisLabelWidthPx, timeMin, timeMax) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val currentVisibleStart =
                            timeMin + viewportStartFraction * totalTimeRange
                        val currentVisibleEnd =
                            currentVisibleStart + totalTimeRange / scaleX
                        val geometry = geometryFor(size.width.toFloat(), size.height.toFloat()).copy(
                            dataXMin = currentVisibleStart,
                            dataXMax = currentVisibleEnd
                        )
                        val plotRect = geometry.rect
                        val isInXAxisArea = centroid.y >= plotRect.bottom
                        if (totalTimeRange <= 0.0 || !totalTimeRange.isFinite()) {
                            return@detectTransformGestures
                        }
                        
                        // 只允许 X 轴方向的缩放（最大50倍）
                        val oldScaleX = scaleX
                        val newScaleX = (scaleX * zoom).coerceIn(1f, 50f)
                        
                        // 缩放时保持手势焦点对应的数据时间不变。
                        if (newScaleX != oldScaleX && zoom != 1f) {
                            val focusFraction = ((centroid.x - plotRect.left) / plotRect.width)
                                .coerceIn(0f, 1f)
                            val oldVisibleRange = totalTimeRange / oldScaleX
                            val focusTime = currentVisibleStart + focusFraction * oldVisibleRange
                            val newVisibleRange = totalTimeRange / newScaleX
                            viewportStartFraction = (
                                (focusTime - focusFraction * newVisibleRange - timeMin) /
                                    totalTimeRange
                                ).toFloat()
                        }
                        
                        scaleX = newScaleX
                        
                        // 只有在X轴区域才允许平移
                        if (isInXAxisArea) {
                            viewportStartFraction -= pan.x / plotRect.width / scaleX
                        }
                        
                        // 将可见窗口限制在完整模拟范围内。
                        viewportStartFraction = viewportStartFraction.coerceIn(
                            0f,
                            (1f - 1f / scaleX).coerceAtLeast(0f)
                        )
                        
                        // 清除触摸选中状态
                        touchPosition = null
                        selectedPoint = null
                    }
                }
                .pointerInput(visibleScale, yAxisLabelWidthPx, timeMin, timeMax) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val currentVisibleStart =
                            timeMin + viewportStartFraction * totalTimeRange
                        val currentVisibleEnd =
                            currentVisibleStart + totalTimeRange / scaleX
                        val geometry = geometryFor(size.width.toFloat(), size.height.toFloat()).copy(
                            dataXMin = currentVisibleStart,
                            dataXMax = currentVisibleEnd
                        )
                        val plotRect = geometry.rect
                        
                        // 检查是否在图表区域内
                        if (plotRect.contains(down.position.x, down.position.y)) {
                            
                            // 更新选中点的函数
                            fun updateSelectedPoint(offset: Offset) {
                                touchPosition = offset
                                
                                // 找到最近的数据点
                                val touchTime = geometry.screenXToData(offset.x)
                                
                                var closestIndex = 0
                                var minDistance = Double.MAX_VALUE
                                
                                simulationResult.timeH.forEachIndexed { index, time ->
                                    val distance = abs(time - touchTime)
                                    if (distance < minDistance) {
                                        minDistance = distance
                                        closestIndex = index
                                    }
                                }
                                
                                if (closestIndex < simulationResult.timeH.size &&
                                    closestIndex < simulationResult.concPGmL.size
                                ) {
                                    selectedPoint = Pair(
                                        simulationResult.timeH[closestIndex],
                                        simulationResult.concPGmL[closestIndex]
                                    )
                                }
                            }
                            
                            // 初始选中点
                            updateSelectedPoint(down.position)
                            
                            // 跟踪拖动
                            drag(down.id) { change ->
                                val currentPos = change.position
                                // 限制在图表区域内
                                if (plotRect.contains(currentPos.x, currentPos.y)) {
                                    updateSelectedPoint(currentPos)
                                    change.consume()
                                }
                            }
                            
                            // 松开后清除选中
                            touchPosition = null
                            selectedPoint = null
                        }
                    }
                }
    ) {
        val currentTimeH = currentTimeHState.value
        val geometry = geometryFor(size.width, size.height)
        val plotRect = geometry.rect
        val chartWidth = plotRect.width
        val chartLeft = plotRect.left
        val chartTop = plotRect.top
        val chartRight = plotRect.right
        val chartBottom = plotRect.bottom

        // 限制绘制区域在图表框内
        clipRect(
            left = chartLeft,
            top = chartTop,
            right = chartRight,
            bottom = chartBottom
        ) {
            val targetBandTop = geometry.dataYToScreen(GAHT_TARGET_MAX_PG_ML)
            val targetBandBottom = geometry.dataYToScreen(GAHT_TARGET_MIN_PG_ML)

            drawRect(
                color = primaryColor.copy(alpha = 0.12f),
                topLeft = Offset(chartLeft, targetBandTop),
                size = Size(chartWidth, targetBandBottom - targetBandTop)
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.38f),
                start = Offset(chartLeft, targetBandTop),
                end = Offset(chartRight, targetBandTop),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.38f),
                start = Offset(chartLeft, targetBandBottom),
                end = Offset(chartRight, targetBandBottom),
                strokeWidth = 1.dp.toPx()
            )

            // 确定分叉点时间（优先使用传入的状态，否则使用当前时刻）
            val forkTime = forkPointTimeHState?.value ?: currentTimeH
            
            // 计算分叉点处的浓度值（线性插值）
            var forkPointConc = 0.0
            var forkPointFound = false
            for (i in 0 until simulationResult.timeH.size - 1) {
                val time1 = simulationResult.timeH[i]
                val time2 = simulationResult.timeH[i + 1]
                
                if (time1 <= forkTime && time2 >= forkTime) {
                    // 线性插值计算分叉点处的浓度
                    val conc1 = simulationResult.concPGmL[i]
                    val conc2 = simulationResult.concPGmL[i + 1]
                    val ratio = if (time2 != time1) (forkTime - time1) / (time2 - time1) else 0.0
                    forkPointConc = conc1 + (conc2 - conc1) * ratio
                    forkPointFound = true
                    break
                }
            }
            
            // 如果没找到插值点，使用最近的点
            if (!forkPointFound) {
                simulationResult.timeH.forEachIndexed { index, time ->
                    if ((index == 0 || abs(time - forkTime) < abs(simulationResult.timeH[index - 1] - forkTime)) &&
                        index < simulationResult.concPGmL.size) {
                        forkPointConc = simulationResult.concPGmL[index]
                    }
                }
            }
            
            // 计算分叉点的屏幕坐标
            val forkScreenX = geometry.dataXToScreen(forkTime)
            val forkScreenY = geometry.dataYToScreen(forkPointConc)
            
            // 绘制基线曲线（分叉点之后未用药）- primary虚线
            baselineSimulationResult?.let { baseline ->
                val baselinePath = Path()
                var isFirst = true
                baseline.timeH.forEachIndexed { index, time ->
                    // 只绘制分叉点之后的部分
                    if (time >= forkTime) {
                        val conc = baseline.concPGmL[index]
                        val x = geometry.dataXToScreen(time)
                        val y = geometry.dataYToScreen(conc)
                        
                        if (isFirst) {
                            // 从分叉点开始
                            baselinePath.moveTo(forkScreenX, forkScreenY)
                            baselinePath.lineTo(x, y)
                            isFirst = false
                        } else {
                            baselinePath.lineTo(x, y)
                        }
                    }
                }
                
                // 绘制基线未来曲线（使用 primary color 50% 不透明实线）
                drawPath(
                    path = baselinePath,
                    color = primaryColor.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 2.5.dp.toPx()
                    )
                )
            }
            
            // 绘制主曲线 - 分段绘制（分叉点前后使用不同颜色）
            // 1. 分叉点之前的部分（历史 + 第一次未来用药之前）- primary实线
            val pathBefore = Path()
            var hasMovedBefore = false
            simulationResult.timeH.forEachIndexed { index, time ->
                if (time < forkTime) {  // 严格小于分叉点时间
                    val conc = simulationResult.concPGmL[index]
                    val x = geometry.dataXToScreen(time)
                    val y = geometry.dataYToScreen(conc)
                    
                    if (!hasMovedBefore) {
                        pathBefore.moveTo(x, y)
                        hasMovedBefore = true
                    } else {
                        pathBefore.lineTo(x, y)
                    }
                }
            }
            
            // 线段延伸到分叉点
            if (forkTime > timeMin && forkTime < timeMax) {
                if (hasMovedBefore) {
                    pathBefore.lineTo(forkScreenX, forkScreenY)
                } else {
                    // 如果没有数据点在分叉点之前，从分叉点开始
                    pathBefore.moveTo(forkScreenX, forkScreenY)
                }
            }
            
            drawPath(
                path = pathBefore,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx())
            )
            
            // 2. 分叉点之后的部分（按计划用药）- tertiary虚线
            val pathAfter = Path()
            var startedAfter = false
            
            // 首先从分叉点开始
            if (forkTime > timeMin && forkTime < timeMax) {
                pathAfter.moveTo(forkScreenX, forkScreenY)
                startedAfter = true
            }
            
            simulationResult.timeH.forEachIndexed { index, time ->
                if (time > forkTime) {  // 严格大于分叉点时间
                    val conc = simulationResult.concPGmL[index]
                    val x = geometry.dataXToScreen(time)
                    val y = geometry.dataYToScreen(conc)
                    
                    if (!startedAfter) {
                        pathAfter.moveTo(x, y)
                        startedAfter = true
                    } else {
                        pathAfter.lineTo(x, y)
                    }
                }
            }
            
            drawPath(
                path = pathAfter,
                color = tertiaryColor.copy(alpha = 0.5f),
                style = Stroke(
                    width = 2.5.dp.toPx()
                )
            )

            // 在曲线上用星形标记实际给药记录。
            doseTimePoints.forEach { doseTime ->
                if (doseTime >= timeMin && doseTime <= timeMax) {
                    val x = geometry.dataXToScreen(doseTime)
                    val concentration = simulationResult.concentration(doseTime)
                    if (x >= chartLeft && x <= chartRight && concentration != null) {
                        val y = geometry.dataYToScreen(concentration)
                        val starPath = createStarPath(
                            center = Offset(x, y),
                            outerRadius = 8.dp.toPx(),
                            innerRadius = 3.6.dp.toPx()
                        )
                        drawPath(
                            path = starPath,
                            color = errorColor.copy(alpha = 0.95f)
                        )
                        drawPath(
                            path = starPath,
                            color = surfaceColor,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // 标记当前时刻（用大圆点）
            if (currentTimeH >= timeMin && currentTimeH <= timeMax) {
                val x = geometry.dataXToScreen(currentTimeH)
                
                // 找到当前时刾的浓度
                var currentConc = 0.0
                var minTimeDiff = Double.MAX_VALUE
                simulationResult.timeH.forEachIndexed { index, time ->
                    val diff = abs(time - currentTimeH)
                    if (diff < minTimeDiff) {
                        minTimeDiff = diff
                        currentConc = simulationResult.concPGmL[index]
                    }
                }
                
                val y = geometry.dataYToScreen(currentConc)
                
                if (x >= chartLeft && x <= chartRight) {
                    // 绘制外圆（白色边框）
                    drawCircle(
                        color = surfaceColor,
                        radius = 8.dp.toPx(),
                        center = Offset(x, y)
                    )
                    // 绘制内圆（tertiary 颜色）
                    drawCircle(
                        color = tertiaryColor,
                        radius = 6.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            
            // 绘制选中点的标记
            selectedPoint?.let { (time, conc) ->
                val x = geometry.dataXToScreen(time)
                val y = geometry.dataYToScreen(conc)
                
                if (x >= chartLeft && x <= chartRight) {
                    // 绘制高亮圆点
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.3f),
                        radius = 12.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
        
        // 绘制网格和坐标轴（不受clipRect限制）
        val gridColor = onSurfaceColor.copy(alpha = 0.15f)
        
        // 垂直网格线（时间）
        for (i in 0..10) {
            val gridTime = visibleTimeStart + (visibleTimeEnd - visibleTimeStart) * i / 10.0
            val x = geometry.dataXToScreen(gridTime)
            drawLine(
                color = gridColor,
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        
        for (concValue in visibleScale.tickValues()) {
            val y = geometry.dataYToScreen(concValue)
            
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.5.dp.toPx()
            )
            
            // Y轴刻度标签
            val text = formatYAxisLabel(concValue, visibleScale.tickStep)
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = yAxisTextStyle
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    chartLeft - textLayoutResult.size.width - labelToAxisGapPx,
                    y - textLayoutResult.size.height / 2
                )
            )
        }

        // 绘制坐标轴
        drawLine(
            color = onSurfaceColor,
            start = Offset(chartLeft, chartTop),
            end = Offset(chartLeft, chartBottom),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = onSurfaceColor,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 2.dp.toPx()
        )

        // X轴标签
        val dateFormat = SimpleDateFormat(
            if (is24Hour) "MM/dd HH:mm" else "MM/dd hh:mm a",
            Locale.getDefault()
        )
        fun formatXAxisLabel(timeH: Double): String {
            val timeMillis = (timeH * 3600000).toLong()
            return dateFormat.format(Date(timeMillis)).replaceFirst(" ", "\n")
        }

        val xAxisTextStyle = TextStyle(
            color = onSurfaceColor,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        val widestEdgeLabel = listOf(visibleTimeStart, visibleTimeEnd)
            .map { time ->
                textMeasurer.measure(
                    text = formatXAxisLabel(time),
                    style = xAxisTextStyle
                )
            }
            .maxBy { it.size.width }
        val xAxisLabelCount = calculateXAxisLabelCount(
            chartWidthPx = chartWidth,
            labelWidthPx = widestEdgeLabel.size.width.toFloat(),
            minimumGapPx = 12.dp.toPx()
        )

        for (i in 0 until xAxisLabelCount) {
            val fraction = i.toDouble() / (xAxisLabelCount - 1)
            val timeValue = visibleTimeStart + (visibleTimeEnd - visibleTimeStart) * fraction
            val x = geometry.dataXToScreen(timeValue)
            
            if (x >= chartLeft && x <= chartRight) {
                val textLayoutResult = textMeasurer.measure(
                    text = formatXAxisLabel(timeValue),
                    style = xAxisTextStyle
                )
                val maxLabelLeft = (size.width - textLayoutResult.size.width).coerceAtLeast(0f)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        (x - textLayoutResult.size.width / 2).coerceIn(0f, maxLabelLeft),
                        chartBottom + 8.dp.toPx()
                    )
                )
            }
        }
        }
        
        // 显示浮动信息窗口
        selectedPoint?.let { (time, conc) ->
            touchPosition?.let { pos ->
                val dateFormat = SimpleDateFormat(
                    if (is24Hour) "MM/dd HH:mm" else "MM/dd hh:mm a",
                    LocalLocale.current.platformLocale
                )
                val timeMillis = (time * 3600000).toLong()
                val timeText = dateFormat.format(Date(timeMillis))
                val concText = "%.1f pg/mL".format(conc)
                
                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (pos.x + 16.dp.toPx()).toInt(),
                                y = (pos.y - 60.dp.toPx()).toInt()
                            )
                        }
                        .wrapContentSize(),
                    color = surfaceColor,
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = timeText,
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = onSurfaceColor
                            )
                        )
                        Text(
                            text = concText,
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = primaryColor
                            )
                        )
                    }
                }
            }
        }
    }
}
