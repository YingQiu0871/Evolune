package io.github.yingqiu0871.evolune.ui.screens.retrospective

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.HistoryFormatting
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkLimitation
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkResult
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkUnavailableReason
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePhase
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePkRange
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePkUiState
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePkViewModel
import java.time.ZoneId

/**
 * V17-C-04 §7/§9/§10 — the production retrospective destination.
 *
 * Every fact comes from frozen [RetrospectivePkUiState]; this file reads no history, derives no
 * marker and recomputes no concentration. The surface lifecycle bridge is hosted here, exactly
 * like Insights hosts the approved B-02 bridge.
 */
@Composable
fun RetrospectiveRoute(
    viewModel: RetrospectivePkViewModel,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    showTopBar: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()

    RetrospectiveSurfaceLifecycle(viewModel)

    RetrospectivePkScreenContent(
        state = state,
        modifier = modifier,
        is24Hour = is24Hour,
        showTopBar = showTopBar,
        onRetry = viewModel::retry,
        onSelectRange = viewModel::selectRange
    )
}

/**
 * Stateless retrospective content: previews and UI tests drive one synthetic
 * [RetrospectivePkUiState], exactly like the History / Insights patterns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetrospectivePkScreenContent(
    state: RetrospectivePkUiState,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    showTopBar: Boolean = false,
    onRetry: () -> Unit = {},
    onSelectRange: (RetrospectivePkRange) -> Unit = {}
) {
    Scaffold(
        modifier = modifier.testTag("retrospective-screen"),
        contentWindowInsets = if (showTopBar) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.retrospective_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                .testTag("retrospective-content-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mandatory model-estimate disclosure: always visible on the surface itself.
            Text(
                text = stringResource(R.string.retrospective_disclosure),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("retrospective-disclosure")
            )
            Text(
                text = stringResource(
                    R.string.retrospective_window_caption,
                    state.selectedRange.days.toInt(),
                    HistoryFormatting.fullDateTimeText(state.windowEnd, state.displayZone, is24Hour)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("retrospective-window-caption")
            )
            RetrospectiveRangeSelector(
                selectedRange = state.selectedRange,
                onSelectRange = onSelectRange
            )

            when (state.phase) {
                RetrospectivePhase.LOADING -> LoadingBlock()
                RetrospectivePhase.CONTENT -> {
                    val available = state.result as? RetrospectivePkResult.Available
                    if (available == null) {
                        ErrorBlock(onRetry)
                    } else {
                        ContentBlock(
                            available = available,
                            markers = state.markers,
                            displayZone = state.displayZone,
                            is24Hour = is24Hour,
                            onRetry = onRetry
                        )
                    }
                }

                RetrospectivePhase.UNAVAILABLE -> {
                    val unavailable = state.result as? RetrospectivePkResult.Unavailable
                    UnavailableBlock(
                        reason = unavailable?.reason
                            ?: RetrospectivePkUnavailableReason.QUERY_OUTSIDE_CALCULATED_INTERVAL,
                        limitations = unavailable?.limitations.orEmpty(),
                        onRetry = onRetry
                    )
                }

                RetrospectivePhase.ERROR -> ErrorBlock(onRetry)
            }
        }
    }
}

// ---------- range selector (v1.7.1 UI hotfix) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetrospectiveRangeSelector(
    selectedRange: RetrospectivePkRange,
    onSelectRange: (RetrospectivePkRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("retrospective-range-selector"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RetrospectivePkRange.entries.forEach { range ->
            FilterChip(
                selected = range == selectedRange,
                onClick = { onSelectRange(range) },
                label = { Text(stringResource(rangeLabelRes(range))) },
                modifier = Modifier.testTag(rangeTestTag(range))
            )
        }
    }
}

private fun rangeLabelRes(range: RetrospectivePkRange): Int = when (range) {
    RetrospectivePkRange.LAST_7_DAYS -> R.string.retrospective_range_7_days
    RetrospectivePkRange.LAST_30_DAYS -> R.string.retrospective_range_30_days
    RetrospectivePkRange.LAST_90_DAYS -> R.string.retrospective_range_90_days
}

private fun rangeTestTag(range: RetrospectivePkRange): String = when (range) {
    RetrospectivePkRange.LAST_7_DAYS -> "retrospective-range-7"
    RetrospectivePkRange.LAST_30_DAYS -> "retrospective-range-30"
    RetrospectivePkRange.LAST_90_DAYS -> "retrospective-range-90"
}

// ---------- phase blocks ----------

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .testTag("retrospective-loading"),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ContentBlock(
    available: RetrospectivePkResult.Available,
    markers: List<io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveMarker>,
    displayZone: ZoneId,
    is24Hour: Boolean,
    onRetry: () -> Unit
) {
    RetrospectiveConcentrationChart(
        series = available.series,
        markers = markers,
        displayZone = displayZone,
        is24Hour = is24Hour,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag("retrospective-chart")
    )
    Text(
        text = stringResource(R.string.retrospective_chart_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("retrospective-chart-hint")
    )
    MarkerLegend(markers)
    Text(
        text = stringResource(R.string.retrospective_marker_legend_disclosure),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("retrospective-marker-disclosure")
    )
    LimitationBlock(available.limitations)
}

@Composable
private fun MarkerLegend(
    markers: List<io.github.yingqiu0871.evolune.history.retrospective.RetrospectiveMarker>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("retrospective-marker-legend"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendRow(
            color = retrospectiveScheduleMarkerColor(),
            label = stringResource(R.string.retrospective_marker_schedule_context),
            count = RetrospectivePresentation.scheduleMarkerCount(markers),
            tag = "retrospective-legend-schedule"
        )
        LegendRow(
            color = retrospectiveIntakeMarkerColor(),
            label = stringResource(R.string.retrospective_marker_recorded_intake),
            count = RetrospectivePresentation.recordedIntakeCount(markers),
            tag = "retrospective-legend-intake"
        )
    }
}

@Composable
private fun LegendRow(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    count: Int,
    tag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = stringResource(R.string.retrospective_legend_entry_format, label, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("$tag-label")
        )
    }
}

@Composable
private fun LimitationBlock(limitations: Set<RetrospectivePkLimitation>) {
    if (limitations.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("retrospective-limitations"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        limitations.forEach { limitation ->
            Text(
                text = stringResource(RetrospectivePresentation.limitationMessageRes(limitation)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnavailableBlock(
    reason: RetrospectivePkUnavailableReason,
    limitations: Set<RetrospectivePkLimitation>,
    onRetry: () -> Unit
) {
    Text(
        text = stringResource(RetrospectivePresentation.unavailableMessageRes(reason)),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("retrospective-unavailable")
    )
    LimitationBlock(limitations)
    RetryButton(onRetry)
}

@Composable
private fun ErrorBlock(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.retrospective_unavailable_generic),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("retrospective-error")
    )
    RetryButton(onRetry)
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag("retrospective-retry")
    ) {
        Text(stringResource(R.string.retrospective_retry))
    }
}
