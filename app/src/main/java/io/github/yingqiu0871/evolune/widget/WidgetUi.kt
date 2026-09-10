package io.github.yingqiu0871.evolune.widget

import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.MedicationTimelineItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt

internal data class WidgetSize(val widthDp: Int, val heightDp: Int) {
    init {
        require(widthDp > 0)
        require(heightDp > 0)
    }
}

internal enum class WidgetSizeTier {
    NARROW_SHORT,
    NARROW_STANDARD,
    WIDE_STANDARD,
    EXPANDED
}

internal enum class WidgetWidthDensity { NARROW, WIDE }

internal data class WidgetLayoutSpec(
    val tier: WidgetSizeTier,
    val widthDensity: WidgetWidthDensity,
    val rowCapacity: Int
)

internal data class WidgetHeroTypography(
    val labelTextSp: Int,
    val valueTextSp: Int,
    val metaTextSp: Int
)

internal data class WidgetChartTypography(
    val titleTextSp: Int,
    val concentrationTextSp: Int,
    val axisTextSp: Int
)

internal object WidgetHeroTypographyPolicy {
    fun resolve(layout: WidgetLayoutSpec): WidgetHeroTypography = when (layout.tier) {
        WidgetSizeTier.NARROW_SHORT -> WidgetHeroTypography(16, 21, 10)
        WidgetSizeTier.NARROW_STANDARD -> WidgetHeroTypography(18, 25, 11)
        WidgetSizeTier.WIDE_STANDARD -> WidgetHeroTypography(20, 30, 12)
        WidgetSizeTier.EXPANDED -> WidgetHeroTypography(23, 35, 13)
    }
}

internal object WidgetChartTypographyPolicy {
    fun resolve(layout: WidgetLayoutSpec): WidgetChartTypography = when (layout.tier) {
        WidgetSizeTier.NARROW_SHORT -> WidgetChartTypography(11, 11, 8)
        WidgetSizeTier.NARROW_STANDARD -> WidgetChartTypography(12, 12, 8)
        WidgetSizeTier.WIDE_STANDARD -> WidgetChartTypography(14, 14, 9)
        WidgetSizeTier.EXPANDED -> WidgetChartTypography(16, 16, 10)
    }
}

/** Targets a three-quarter peak while leaving room for the rounded cap. */
internal object WidgetChartGeometryPolicy {
    private const val PEAK_FILL_RATIO = 0.75f
    private const val PEAK_TOP_HEADROOM_DP = 4

    fun maxSegmentHeightDp(size: WidgetSize, layout: WidgetLayoutSpec): Int {
        val verticalChromeDp = when (layout.tier) {
            WidgetSizeTier.NARROW_SHORT -> 44
            WidgetSizeTier.NARROW_STANDARD -> 54
            WidgetSizeTier.WIDE_STANDARD -> 60
            WidgetSizeTier.EXPANDED -> 68
        }
        // Launcher hosts may report a compact minimum height while rendering a larger cell.
        // Use a smaller chrome estimate below the standard bound so the preview and runtime
        // chart still occupy the visible plot instead of being compressed to a short baseline.
        val effectiveChromeDp = if (size.heightDp < WidgetSizePolicy.STANDARD_MIN_HEIGHT_DP) {
            minOf(verticalChromeDp, 30)
        } else {
            verticalChromeDp
        }
        val estimatedPlotHeightDp = (size.heightDp - effectiveChromeDp).coerceAtLeast(12)
        return (
            estimatedPlotHeightDp * PEAK_FILL_RATIO - PEAK_TOP_HEADROOM_DP
            ).roundToInt().coerceIn(3, 220)
    }
}

internal object WidgetSizePolicy {
    const val STANDARD_MIN_HEIGHT_DP = 160
    const val WIDE_MIN_WIDTH_DP = 220
    const val EXPANDED_MIN_WIDTH_DP = 300
    const val TALL_MIN_HEIGHT_DP = 260

    fun currentSize(
        minWidthDp: Int,
        minHeightDp: Int,
        maxWidthDp: Int,
        maxHeightDp: Int,
        isLandscape: Boolean
    ): WidgetSize = if (isLandscape) {
        WidgetSize(maxWidthDp.coerceAtLeast(1), minHeightDp.coerceAtLeast(1))
    } else {
        WidgetSize(minWidthDp.coerceAtLeast(1), maxHeightDp.coerceAtLeast(1))
    }

    fun resolve(size: WidgetSize): WidgetLayoutSpec {
        val density = if (size.widthDp >= WIDE_MIN_WIDTH_DP) {
            WidgetWidthDensity.WIDE
        } else {
            WidgetWidthDensity.NARROW
        }
        val rowCapacity = when {
            size.heightDp < STANDARD_MIN_HEIGHT_DP -> 1
            size.heightDp < TALL_MIN_HEIGHT_DP -> 3
            else -> 5
        }
        val tier = when {
            size.heightDp < STANDARD_MIN_HEIGHT_DP -> WidgetSizeTier.NARROW_SHORT
            size.widthDp >= EXPANDED_MIN_WIDTH_DP || size.heightDp >= TALL_MIN_HEIGHT_DP ->
                WidgetSizeTier.EXPANDED
            size.widthDp >= WIDE_MIN_WIDTH_DP -> WidgetSizeTier.WIDE_STANDARD
            else -> WidgetSizeTier.NARROW_STANDARD
        }
        return WidgetLayoutSpec(tier, density, rowCapacity)
    }
}

internal sealed interface WidgetRenderState {
    data class Loaded(val snapshot: WidgetSnapshot) : WidgetRenderState
    data object Loading : WidgetRenderState
    data class ReadFailure(val timeFormat: TimeFormat = TimeFormat.SYSTEM) : WidgetRenderState
}

internal enum class WidgetContentState {
    TIMELINE,
    NO_ENABLED_PLANS,
    NO_UPCOMING_OCCURRENCE,
    LOADING,
    READ_FAILURE
}

internal enum class WidgetRowAction { RECORD, COMPLETED, OPEN_APP }

internal enum class WidgetActionButtonTreatment { OUTLINED, TONAL }

internal enum class WidgetActionButtonShape { CIRCLE }

internal data class WidgetActionButtonStyle(
    val shape: WidgetActionButtonShape,
    val treatment: WidgetActionButtonTreatment,
    val containerColor: Int,
    val iconColor: Int
)

internal fun WidgetRowAction.buttonStyle(palette: WidgetPalette): WidgetActionButtonStyle =
    when (this) {
        WidgetRowAction.RECORD -> WidgetActionButtonStyle(
            shape = WidgetActionButtonShape.CIRCLE,
            treatment = WidgetActionButtonTreatment.OUTLINED,
            containerColor = palette.primaryForeground,
            iconColor = palette.primaryForeground
        )
        WidgetRowAction.COMPLETED -> WidgetActionButtonStyle(
            shape = WidgetActionButtonShape.CIRCLE,
            treatment = WidgetActionButtonTreatment.TONAL,
            containerColor = palette.primaryContainer,
            iconColor = palette.onPrimaryContainer
        )
        WidgetRowAction.OPEN_APP -> WidgetActionButtonStyle(
            shape = WidgetActionButtonShape.CIRCLE,
            treatment = WidgetActionButtonTreatment.OUTLINED,
            containerColor = palette.onSurfaceVariant,
            iconColor = palette.onSurfaceVariant
        )
    }

internal enum class WidgetRowStatusPresentation { SCHEDULED_TIME, COMPLETED }

internal enum class WidgetProgressSegment { FILLED, EMPTY }

internal enum class WidgetRowDensity { COMPACT, MEDIUM, EXPANDED }

internal enum class WidgetRowDistribution { STACKED, FULL_HEIGHT, BALANCED, CENTERED }

internal data class WidgetRowSpacingPlan(
    val leadingSpacers: Int,
    val betweenSpacers: Int,
    val trailingSpacers: Int
)

internal fun WidgetRowDistribution.spacingPlan(rowCount: Int): WidgetRowSpacingPlan {
    require(rowCount >= 0)
    if (rowCount == 0) return WidgetRowSpacingPlan(0, 0, 0)
    return when (this) {
        WidgetRowDistribution.STACKED -> WidgetRowSpacingPlan(0, 0, 0)
        WidgetRowDistribution.FULL_HEIGHT,
        WidgetRowDistribution.BALANCED -> WidgetRowSpacingPlan(0, rowCount - 1, 0)
        WidgetRowDistribution.CENTERED -> WidgetRowSpacingPlan(1, 0, 1)
    }
}

internal data class WidgetRowLayoutSpec(
    val density: WidgetRowDensity,
    val distribution: WidgetRowDistribution,
    val rowHeightDp: Int,
    val titleTextSp: Int,
    val metadataTextSp: Int,
    val statusTextSp: Int,
    val actionSizeDp: Int,
    val actionContainerSizeDp: Int,
    val actionTouchTargetDp: Int,
    val railHeightDp: Int,
    val verticalPaddingDp: Int
)

internal object WidgetRowDensityPolicy {
    private val compact = WidgetRowLayoutSpec(
        WidgetRowDensity.COMPACT,
        WidgetRowDistribution.STACKED,
        rowHeightDp = 44,
        titleTextSp = 13,
        metadataTextSp = 9,
        statusTextSp = 10,
        actionSizeDp = 18,
        actionContainerSizeDp = 32,
        actionTouchTargetDp = 40,
        railHeightDp = 32,
        verticalPaddingDp = 3
    )
    private val medium = WidgetRowLayoutSpec(
        WidgetRowDensity.MEDIUM,
        WidgetRowDistribution.BALANCED,
        rowHeightDp = 56,
        titleTextSp = 14,
        metadataTextSp = 10,
        statusTextSp = 11,
        actionSizeDp = 20,
        actionContainerSizeDp = 32,
        actionTouchTargetDp = 42,
        railHeightDp = 40,
        verticalPaddingDp = 6
    )
    private val expanded = WidgetRowLayoutSpec(
        WidgetRowDensity.EXPANDED,
        WidgetRowDistribution.CENTERED,
        rowHeightDp = 72,
        titleTextSp = 16,
        metadataTextSp = 11,
        statusTextSp = 12,
        actionSizeDp = 22,
        actionContainerSizeDp = 34,
        actionTouchTargetDp = 44,
        railHeightDp = 48,
        verticalPaddingDp = 9
    )

    fun resolve(layout: WidgetLayoutSpec, visibleRowCount: Int): WidgetRowLayoutSpec {
        require(visibleRowCount >= 0)
        if (layout.tier == WidgetSizeTier.NARROW_SHORT) return compact
        return when (visibleRowCount) {
            1 -> expanded
            2 -> medium
            0 -> compact
            else -> compact.copy(distribution = WidgetRowDistribution.CENTERED)
        }
    }
}

internal data class WidgetOccurrenceUi(
    val occurrenceId: UUID,
    val planId: UUID,
    val slotId: UUID,
    val scheduledLocalDate: LocalDate,
    val planName: String,
    val routeKey: String,
    val scheduledLocalDateTime: LocalDateTime,
    val doseMg: Double,
    val status: MedicationOccurrenceStatus,
    val action: WidgetRowAction,
    val statusPresentation: WidgetRowStatusPresentation
)

internal data class WidgetUiModel(
    val layout: WidgetLayoutSpec,
    val contentState: WidgetContentState,
    val style: WidgetStyle,
    val appearance: WidgetAppearanceConfig,
    val timeFormat: TimeFormat,
    val concentration: Double?,
    val concentrationComputedAt: Instant?,
    val pkChart: List<WidgetPkPoint>,
    val dailyProgress: WidgetDailyProgress,
    val progressSegments: List<WidgetProgressSegment>,
    val rowLayout: WidgetRowLayoutSpec,
    val rows: List<WidgetOccurrenceUi>
)

internal object WidgetUiMapper {
    fun map(
        renderState: WidgetRenderState,
        layout: WidgetLayoutSpec,
        appearance: WidgetAppearanceConfig
    ): WidgetUiModel = when (renderState) {
        WidgetRenderState.Loading -> emptyModel(
            layout,
            WidgetContentState.LOADING,
            appearance,
            TimeFormat.SYSTEM
        )
        is WidgetRenderState.ReadFailure -> emptyModel(
            layout,
            WidgetContentState.READ_FAILURE,
            appearance,
            renderState.timeFormat
        )
        is WidgetRenderState.Loaded -> mapLoaded(renderState.snapshot, layout, appearance)
    }

    private fun mapLoaded(
        snapshot: WidgetSnapshot,
        layout: WidgetLayoutSpec,
        appearance: WidgetAppearanceConfig
    ): WidgetUiModel = when (val state = snapshot.presentation) {
        WidgetPresentationState.NoEnabledPlans -> emptyModel(
            layout,
            WidgetContentState.NO_ENABLED_PLANS,
            appearance,
            snapshot.timeFormat,
            snapshot.concentration,
            snapshot.concentrationComputedAt,
            snapshot.pkChart
        )
        is WidgetPresentationState.NoUpcomingOccurrence -> emptyModel(
            layout,
            WidgetContentState.NO_UPCOMING_OCCURRENCE,
            appearance,
            snapshot.timeFormat,
            snapshot.concentration,
            snapshot.concentrationComputedAt,
            snapshot.pkChart,
            state.dailyProgress
        )
        is WidgetPresentationState.Timeline -> {
            val style = appearance.styleId
            val rows = styleRows(state, style).map { it.toUi() }
            WidgetUiModel(
                layout = layout,
                contentState = WidgetContentState.TIMELINE,
                style = style,
                appearance = appearance.normalized(),
                timeFormat = snapshot.timeFormat,
                concentration = snapshot.concentration,
                concentrationComputedAt = snapshot.concentrationComputedAt,
                pkChart = snapshot.pkChart,
                dailyProgress = state.dailyProgress,
                progressSegments = state.todayItems
                    .map { item ->
                        if (item.status == MedicationOccurrenceStatus.RECORDED) {
                            WidgetProgressSegment.FILLED
                        } else {
                            WidgetProgressSegment.EMPTY
                        }
                    }
                    .take(MAX_PROGRESS_SEGMENTS),
                rowLayout = WidgetRowDensityPolicy.resolve(
                    layout,
                    minOf(rows.size, layout.rowCapacity)
                ),
                rows = rows
            )
        }
    }

    private fun emptyModel(
        layout: WidgetLayoutSpec,
        contentState: WidgetContentState,
        appearance: WidgetAppearanceConfig,
        timeFormat: TimeFormat,
        concentration: Double? = null,
        concentrationComputedAt: Instant? = null,
        pkChart: List<WidgetPkPoint> = emptyList(),
        dailyProgress: WidgetDailyProgress = WidgetDailyProgress.Empty
    ) = WidgetUiModel(
        layout = layout,
        contentState = contentState,
        style = appearance.styleId,
        appearance = appearance.normalized(),
        timeFormat = timeFormat,
        concentration = concentration,
        concentrationComputedAt = concentrationComputedAt,
        pkChart = pkChart,
        dailyProgress = dailyProgress,
        progressSegments = emptyList(),
        rowLayout = WidgetRowDensityPolicy.resolve(layout, 0),
        rows = emptyList()
    )

    private fun styleRows(
        state: WidgetPresentationState.Timeline,
        style: WidgetStyle
    ): List<MedicationTimelineItem> {
        val currentAndUpcoming = state.window.current.actionableItems() +
            state.window.upcoming.actionableItems()
        val todayOrWindow = state.todayItems.takeIf { it.isNotEmpty() } ?: currentAndUpcoming
        return when (style) {
            WidgetStyle.NEXT_DOSE -> (currentAndUpcoming + state.todayItems)
                .distinctBy { it.occurrence.id }
                .sortedWith(
                    compareBy<MedicationTimelineItem> {
                        when (it.status) {
                            MedicationOccurrenceStatus.DUE -> 0
                            MedicationOccurrenceStatus.UPCOMING -> 1
                            MedicationOccurrenceStatus.PAST_UNRECORDED -> 2
                            MedicationOccurrenceStatus.RECORDED -> 3
                        }
                    }.thenBy { it.occurrence.scheduledLocalDateTime }
                )
                .take(1)
            WidgetStyle.CURRENT_E2 -> emptyList()
            WidgetStyle.LEGACY_DEFAULT,
            WidgetStyle.TODAY_PLAN,
            WidgetStyle.PK_CHART -> todayOrWindow
        }
    }

    private fun List<MedicationTimelineItem>.actionableItems() = filter { item ->
        item.status == MedicationOccurrenceStatus.DUE ||
            item.status == MedicationOccurrenceStatus.UPCOMING
    }

    private fun MedicationTimelineItem.toUi() = WidgetOccurrenceUi(
        occurrenceId = occurrence.id.value,
        planId = occurrence.planId,
        slotId = occurrence.slotId,
        scheduledLocalDate = occurrence.scheduledLocalDateTime.toLocalDate(),
        planName = occurrence.presentation.planName,
        routeKey = occurrence.presentation.matchKey.routeKey,
        scheduledLocalDateTime = occurrence.scheduledLocalDateTime,
        doseMg = occurrence.presentation.matchKey.doseAmount,
        status = status,
        action = if (status == MedicationOccurrenceStatus.RECORDED) {
            WidgetRowAction.COMPLETED
        } else {
            WidgetRowAction.RECORD
        },
        statusPresentation = if (status == MedicationOccurrenceStatus.RECORDED) {
            WidgetRowStatusPresentation.COMPLETED
        } else {
            WidgetRowStatusPresentation.SCHEDULED_TIME
        }
    )

    private const val MAX_PROGRESS_SEGMENTS = 5
}
