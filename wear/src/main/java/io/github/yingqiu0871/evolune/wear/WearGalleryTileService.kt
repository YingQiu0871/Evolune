package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.expression.dynamicDataMapOf
import androidx.wear.protolayout.expression.mapTo
import androidx.wear.protolayout.expression.stringAppDataKey
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.BODY_SMALL
import androidx.wear.protolayout.material3.Typography.TITLE_LARGE
import androidx.wear.protolayout.material3.Typography.TITLE_MEDIUM
import androidx.wear.protolayout.material3.Typography.TITLE_SMALL
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppUpcomingOccurrence
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Bump whenever the gallery Tile layout/resource contract changes so an upgraded host
// cannot keep rendering the previous cached layout for an existing Tile instance.
private const val RESOURCES_VERSION_PREFIX = "7"
// Keep the card inside the narrow safe chord of a round watch face. 168dp left only
// about 19dp per side on the 206dp Galaxy Watch viewport and made the curved ends
// look clipped even though the content remained visible.
internal const val WEAR_GALLERY_PANEL_WIDTH_DP = 156f
internal const val WEAR_GALLERY_PANEL_CORNER_RADIUS_DP = WEAR_CARD_CORNER_RADIUS_DP
private const val SELECTED_OCCURRENCE_KEY = "selected_occurrence_id"
private val selectedOccurrenceKey = stringAppDataKey(SELECTED_OCCURRENCE_KEY)
private val tileTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val tileClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

enum class WearGalleryTileKind {
    NEXT_DOSE,
    TODAY_PLAN,
    CURRENT_E2
}

abstract class WearGalleryTileService : TileService() {
    protected abstract val kind: WearGalleryTileKind

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        Futures.immediateFuture(
            buildWearGalleryTile(this, requestParams, kind).also {
                Log.i("EvoluneWearTile", "rendered kind=$kind")
            }
        )

    override fun onTileResourcesRequest(requestParams: ResourcesRequest) =
        Futures.immediateFuture(buildWearGalleryResources(this, kind))
}

class NextDoseTileService : WearGalleryTileService() {
    override val kind = WearGalleryTileKind.NEXT_DOSE
}

class TodayPlanTileService : WearGalleryTileService() {
    override val kind = WearGalleryTileKind.TODAY_PLAN
}

class CurrentE2TileService : WearGalleryTileService() {
    override val kind = WearGalleryTileKind.CURRENT_E2
}

private fun buildWearGalleryTile(
    context: Context,
    request: RequestBuilders.TileRequest,
    kind: WearGalleryTileKind
): Tile {
    val now = System.currentTimeMillis()
    var presentation = WearAppStore.getPresentation(context, now)
    Log.i("EvoluneWearTile", "request kind=$kind state=${presentation.state}")
    if (presentation.state != WearAppDisplayState.READY &&
        presentation.state != WearAppDisplayState.EMPTY
    ) {
        WearAppDataLayer.requestSnapshot(context)
        presentation = WearAppStore.getPresentation(context, now)
        Log.i("EvoluneWearTile", "after request kind=$kind state=${presentation.state}")
    }
    val snapshot = presentation.snapshot
    val zoneId = snapshot?.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        ?: ZoneId.systemDefault()
    val selectedId = request.currentState.stateMap[selectedOccurrenceKey]
    val pendingConfirmation = WearAppConfirmationStore.getPending(context)
    if (kind == WearGalleryTileKind.NEXT_DOSE && snapshot != null && selectedId != null) {
        val occurrence = snapshot.upcomingOccurrences.firstOrNull {
            it.occurrenceId.toString() == selectedId
        }
        if (occurrence != null) {
            val retryable = isRetryableConfirmation(
                pendingConfirmation?.takeIf { it.occurrenceId == occurrence.occurrenceId }
            )
            if (retryable) {
                WearAppDataLayer.retryPending(context)
            } else if (WearAppStore.canConfirm(context, snapshot, occurrence.occurrenceId)) {
                WearAppDataLayer.confirmOccurrence(context, snapshot, occurrence)
            }
        }
    }

    val title = when (kind) {
        WearGalleryTileKind.NEXT_DOSE -> "下一次服药"
        WearGalleryTileKind.TODAY_PLAN -> "今日计划"
        WearGalleryTileKind.CURRENT_E2 -> "当前 E2"
    }
    val scheme = WearAppearanceStore.read(context)
    val palette = WearAppearanceStore.resolve(context, scheme)
    val layout = materialScope(context, request.deviceConfiguration) {
        primaryLayout(
            titleSlot = {
                text(
                    title.layoutString,
                    typography = TITLE_SMALL,
                    color = LayoutColor(palette.onBackground)
                )
            },
            mainSlot = {
                val column = LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                when {
                    presentation.state != WearAppDisplayState.READY &&
                        presentation.state != WearAppDisplayState.EMPTY -> {
                        column.addContent(
                            centeredTonalPanel(
                                primary = wearGalleryStateMessage(presentation.state),
                                secondary = "打开手机端后将自动刷新",
                                palette = palette
                            )
                        )
                    }
                    kind == WearGalleryTileKind.CURRENT_E2 -> {
                        column.addContent(
                            centeredTonalPanel(
                                primary = concentrationLabel(snapshot),
                                secondary = snapshot?.concentrationState?.calculatedAt?.let {
                                    "计算于 ${tileTimeFormatter.format(it.atZone(zoneId))}"
                                } ?: "等待手机端浓度数据",
                                emphasized = true,
                                palette = palette
                            )
                        )
                    }
                    else -> {
                        val occurrences = when (kind) {
                            WearGalleryTileKind.TODAY_PLAN -> snapshot?.let(::todayPendingOccurrences).orEmpty()
                            WearGalleryTileKind.NEXT_DOSE -> snapshot?.upcomingOccurrences.orEmpty()
                            WearGalleryTileKind.CURRENT_E2 -> emptyList()
                        }
                        val visible = if (kind == WearGalleryTileKind.NEXT_DOSE) {
                            occurrences.take(1)
                        } else {
                            occurrences.take(3)
                        }
                        if (visible.isEmpty()) {
                            column.addContent(
                                centeredTonalPanel(
                                    primary = if (kind == WearGalleryTileKind.TODAY_PLAN) {
                                        "今日暂无待服"
                                    } else {
                                        "暂无下一次服药"
                                    },
                                    secondary = "计划变化后将自动刷新",
                                    palette = palette
                                )
                            )
                        } else if (kind == WearGalleryTileKind.NEXT_DOSE) {
                            val occurrence = visible.first()
                            val pending = WearAppConfirmationStore.getPending(context)
                                ?.takeIf { it.occurrenceId == occurrence.occurrenceId }
                            val retryable = isRetryableConfirmation(pending)
                            val (primary, secondary) = nextDosePanelLabels(
                                occurrence = occurrence,
                                zoneId = zoneId,
                                pending = pending
                            )
                            if (pending == null || retryable) {
                                column.addContent(
                                    tonalPanelContainer(
                                        content = centeredTextBlock(
                                            primary = primary,
                                            secondary = secondary,
                                            emphasized = true,
                                            palette = palette
                                        ),
                                        heightDp = 88f,
                                        palette = palette,
                                        onClick = clickable(
                                            action = loadAction(
                                                dynamicDataMapOf(
                                                    selectedOccurrenceKey mapTo occurrence.occurrenceId.toString()
                                                )
                                            )
                                        )
                                    )
                                )
                            } else {
                                column.addContent(
                                    centeredTonalPanel(
                                        primary = primary,
                                        secondary = secondary,
                                        emphasized = true,
                                        palette = palette
                                    )
                                )
                            }
                        } else {
                            val planColumn = LayoutElementBuilders.Column.Builder()
                                .setWidth(DimensionBuilders.expand())
                                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                            visible.forEach { occurrence ->
                                planColumn.addContent(
                                    text(
                                        todayPlanRowLabel(occurrence, zoneId).layoutString,
                                        typography = BODY_MEDIUM,
                                        color = LayoutColor(palette.onPrimaryContainer),
                                        maxLines = 1
                                    )
                                )
                            }
                            val pendingCount = snapshot?.let { todayPendingCount(it, occurrences) }
                            if (kind == WearGalleryTileKind.TODAY_PLAN &&
                                pendingCount != null && pendingCount > visible.size
                            ) {
                                planColumn.addContent(
                                    text(
                                        todayPendingOverflowLabel(pendingCount, visible.size).layoutString,
                                        typography = BODY_SMALL,
                                        color = LayoutColor(palette.onPrimaryContainer),
                                        maxLines = 1
                                    )
                                )
                            }
                            column.addContent(
                                tonalPanelContainer(
                                    content = planColumn.build(),
                                    heightDp = 112f,
                                    palette = palette
                                )
                            )
                        }
                    }
                }
                column.build()
            }
        )
    }
    return Tile.Builder()
        .setResourcesVersion(wearGalleryResourcesVersion(kind, scheme))
        .setFreshnessIntervalMillis(WEAR_TILE_FRESHNESS_MILLIS)
        .setTileTimeline(Timeline.fromLayoutElement(layout))
        .setState(StateBuilders.State.Builder().build())
        .build()
}

private fun MaterialScope.centeredTonalPanel(
    primary: String,
    secondary: String,
    emphasized: Boolean = false,
    palette: WearPalette
): LayoutElementBuilders.LayoutElement = tonalPanelContainer(
    content = centeredTextBlock(primary, secondary, emphasized, palette),
    heightDp = 88f,
    palette = palette
)

private fun MaterialScope.centeredTextBlock(
    primary: String,
    secondary: String,
    emphasized: Boolean,
    palette: WearPalette
): LayoutElementBuilders.LayoutElement {
    val column = LayoutElementBuilders.Column.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
    column.addContent(
        text(
            primary.layoutString,
            typography = if (emphasized) TITLE_LARGE else TITLE_MEDIUM,
            color = LayoutColor(palette.onPrimaryContainer),
            maxLines = 2
        )
    )
    column.addContent(
        LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.dp(5f))
            .build()
    )
    column.addContent(
        text(
            secondary.layoutString,
            typography = BODY_SMALL,
            color = LayoutColor(palette.onPrimaryContainer),
            maxLines = 2
        )
    )
    return column.build()
}

private fun MaterialScope.tonalPanelContainer(
    content: LayoutElementBuilders.LayoutElement,
    heightDp: Float,
    palette: WearPalette,
    onClick: ModifiersBuilders.Clickable? = null
): LayoutElementBuilders.LayoutElement {
    val corner = ModifiersBuilders.Corner.Builder()
        .setRadius(DimensionBuilders.dp(WEAR_GALLERY_PANEL_CORNER_RADIUS_DP))
        .build()
    val background = ModifiersBuilders.Background.Builder()
        .setColor(ColorBuilders.ColorProp.Builder(palette.primaryContainer).build())
        .setCorner(corner)
        .build()
    val padding = ModifiersBuilders.Padding.Builder()
        .setStart(DimensionBuilders.dp(14f))
        .setEnd(DimensionBuilders.dp(14f))
        .setTop(DimensionBuilders.dp(10f))
        .setBottom(DimensionBuilders.dp(10f))
        .build()
    val modifiers = ModifiersBuilders.Modifiers.Builder()
        .setBackground(background)
        .setPadding(padding)
    onClick?.let(modifiers::setClickable)
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.dp(WEAR_GALLERY_PANEL_WIDTH_DP))
        .setHeight(DimensionBuilders.dp(heightDp))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(modifiers.build())
        .addContent(content)
        .build()
}

private fun nextDosePanelLabels(
    occurrence: WearAppUpcomingOccurrence,
    zoneId: ZoneId,
    pending: WearAppPendingConfirmation?
): Pair<String, String> {
    val fallbackPrimary = occurrence.medicationName
    val scheduled = occurrence.scheduledAt.atZone(zoneId)
    val time = if (scheduled.toLocalDate() == java.time.Instant.now().atZone(zoneId).toLocalDate()) {
        tileClockFormatter.format(scheduled)
    } else {
        tileTimeFormatter.format(scheduled)
    }
    val fallbackSecondary = "${formatDose(occurrence.dose)} ${occurrence.doseUnit} · $time"
    val lifecycle = confirmationLabel(pending, fallbackPrimary)
    return if (lifecycle == fallbackPrimary) {
        fallbackPrimary to fallbackSecondary
    } else {
        lifecycle to fallbackSecondary
    }
}

private fun todayPlanRowLabel(occurrence: WearAppUpcomingOccurrence, zoneId: ZoneId): String =
    "${tileClockFormatter.format(occurrence.scheduledAt.atZone(zoneId))}  ${occurrence.medicationName}"

internal fun todayPendingOccurrences(
    snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
): List<WearAppUpcomingOccurrence> {
    val date = snapshot.todaySummary?.todayLocalDate
        ?: snapshot.generatedAt.atZone(
            snapshot.zoneId.let { runCatching { ZoneId.of(it) }.getOrDefault(ZoneId.systemDefault()) }
        ).toLocalDate()
    return snapshot.upcomingOccurrences.filter { it.localDate == date }
}

internal fun confirmationLabel(
    pending: WearAppPendingConfirmation?,
    fallback: String
): String {
    val result = pending?.terminalResult
    return when {
        pending == null -> fallback
        result == null -> "已发送，等待手机回执"
        result.resultType == io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType.CONFIRMED ||
            result.resultType == io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType.ALREADY_CONFIRMED ->
            "已确认，等待快照更新"
        else -> "确认失败，请重试"
    }
}

private fun buildWearGalleryResources(context: Context, kind: WearGalleryTileKind): Resources {
    val scheme = WearAppearanceStore.read(context)
    return Resources.Builder().setVersion(wearGalleryResourcesVersion(kind, scheme)).build()
}

internal fun wearGalleryResourcesVersion(
    kind: WearGalleryTileKind,
    scheme: WearColorScheme = WearColorScheme.SYSTEM
): String = "$RESOURCES_VERSION_PREFIX-${kind.name}-${scheme.name}"

internal fun concentrationLabel(snapshot: WearAppSnapshot?, nowMillis: Long = System.currentTimeMillis()): String {
    if (snapshot?.concentrationState == null) return "E2 不可用"
    val presentation = deriveWearAppConcentrationPresentation(snapshot, nowMillis)
    if (presentation.state == WearAppConcentrationDisplayState.STALE) return "E2 已过期"
    if (presentation.state != WearAppConcentrationDisplayState.FRESH) return "E2 不可用"
    val value = presentation.value ?: return "E2 不可用"
    return String.format(Locale.ROOT, "%.1f pg/mL", value)
}

internal fun todayPendingOverflowLabel(totalCount: Int, visibleCount: Int): String {
    require(totalCount >= visibleCount)
    return "还有 ${totalCount - visibleCount} 项 · 打开手机查看"
}

internal fun isRetryableConfirmation(pending: WearAppPendingConfirmation?): Boolean =
    pending?.terminalResult?.resultType ==
        io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType.RETRYABLE_STORAGE_FAILURE

internal fun nextDoseActionLabel(
    pending: WearAppPendingConfirmation?,
    fallback: String
): String = if (isRetryableConfirmation(pending)) {
    "确认失败，请重试"
} else {
    fallback
}

internal fun todayPendingCount(
    snapshot: WearAppSnapshot,
    filteredOccurrences: List<WearAppUpcomingOccurrence>
): Int = snapshot.todaySummary?.let {
    (it.totalCount - it.completedCount).coerceAtLeast(0)
} ?: filteredOccurrences.size

private fun occurrenceLabel(occurrence: WearAppUpcomingOccurrence, zoneId: ZoneId): String {
    val status = when (occurrence.status) {
        WearAppOccurrenceStatus.UPCOMING -> "即将"
        WearAppOccurrenceStatus.DUE -> "待服"
    }
    return "$status ${occurrence.medicationName} ${formatDose(occurrence.dose)}${occurrence.doseUnit}\n" +
        tileTimeFormatter.format(occurrence.scheduledAt.atZone(zoneId))
}

private fun formatDose(dose: Double): String =
    if (dose % 1.0 == 0.0) {
        dose.toInt().toString()
    } else {
        "%.2f".format(Locale.ROOT, dose).trimEnd('0').trimEnd('.')
    }

private fun wearGalleryStateMessage(state: WearAppDisplayState): String = when (state) {
    WearAppDisplayState.WAITING_FOR_PHONE -> "等待手机端数据"
    WearAppDisplayState.SYNCING -> "正在同步"
    WearAppDisplayState.OFFLINE -> "未连接到手机"
    WearAppDisplayState.STALE -> "数据可能已过期"
    WearAppDisplayState.ERROR -> "同步失败，请打开手机端重试"
    WearAppDisplayState.EMPTY -> "暂无启用方案"
    WearAppDisplayState.READY -> ""
}
