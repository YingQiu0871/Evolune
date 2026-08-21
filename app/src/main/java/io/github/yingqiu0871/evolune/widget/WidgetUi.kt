package io.github.yingqiu0871.evolune.widget

import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.MedicationTimelineItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

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

internal object WidgetSizePolicy {
    const val STANDARD_MIN_HEIGHT_DP = 160
    const val WIDE_MIN_WIDTH_DP = 220
    const val EXPANDED_MIN_WIDTH_DP = 300
    const val TALL_MIN_HEIGHT_DP = 260

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

internal enum class WidgetRowAction { RECORD, COMPLETED }

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
    val appearance: WidgetAppearanceConfig,
    val timeFormat: TimeFormat,
    val concentration: Double?,
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
            snapshot.concentration
        )
        is WidgetPresentationState.NoUpcomingOccurrence -> emptyModel(
            layout,
            WidgetContentState.NO_UPCOMING_OCCURRENCE,
            appearance,
            snapshot.timeFormat,
            snapshot.concentration,
            state.dailyProgress
        )
        is WidgetPresentationState.Timeline -> {
            val currentAndUpcoming = state.window.current.actionableItems() +
                state.window.upcoming.actionableItems()
            val sourceRows = if (layout.tier == WidgetSizeTier.NARROW_SHORT) {
                currentAndUpcoming.takeIf { it.isNotEmpty() } ?: state.todayItems
            } else {
                state.todayItems.takeIf { it.isNotEmpty() } ?: currentAndUpcoming
            }
            val rows = (
                if (layout.tier == WidgetSizeTier.NARROW_SHORT) {
                    sourceRows.take(layout.rowCapacity)
                } else {
                    sourceRows
                }
                ).map { it.toUi() }
            WidgetUiModel(
                layout = layout,
                contentState = WidgetContentState.TIMELINE,
                appearance = appearance.normalized(),
                timeFormat = snapshot.timeFormat,
                concentration = snapshot.concentration,
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
        dailyProgress: WidgetDailyProgress = WidgetDailyProgress.Empty
    ) = WidgetUiModel(
        layout = layout,
        contentState = contentState,
        appearance = appearance.normalized(),
        timeFormat = timeFormat,
        concentration = concentration,
        dailyProgress = dailyProgress,
        progressSegments = emptyList(),
        rowLayout = WidgetRowDensityPolicy.resolve(layout, 0),
        rows = emptyList()
    )

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
