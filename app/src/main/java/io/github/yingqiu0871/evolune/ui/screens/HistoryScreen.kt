package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalScheduleTimeContext
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationActionAvailability
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceId
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.MedicationPresentation
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.history.HistoryCalendarCellUiModel
import io.github.yingqiu0871.evolune.history.HistoryDayPhase
import io.github.yingqiu0871.evolune.history.HistoryEntryKind
import io.github.yingqiu0871.evolune.history.HistoryEntryUiModel
import io.github.yingqiu0871.evolune.history.HistoryFormatting
import io.github.yingqiu0871.evolune.history.HistoryMonthUiModel
import io.github.yingqiu0871.evolune.history.HistoryPresentation
import io.github.yingqiu0871.evolune.history.HistoryUiState
import io.github.yingqiu0871.evolune.history.HistoryViewModel
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

/**
 * 用药历史屏幕（A-03）。
 *
 * 只消费 `HistoryReadService` 输出的 `HistoricalRange`：不访问 DAO/仓库、不运行 matcher、
 * 不比较 `scheduledAt` 与 `now`、不做 future 过滤、不重新归因 display date/provenance。
 *
 * @param viewModel 历史状态持有者
 * @param is24Hour 时间显示制式（来自设置）
 * @param showTopBar 是否自带 TopAppBar（作为 tab 页面时为 false，由根 Scaffold 提供标题）
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    showTopBar: Boolean = false,
    onOpenInsights: () -> Unit = {},
    onOpenRetrospectivePk: () -> Unit = {},
    onOpenTimeline: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // A-04 refresh contract (local only, no navigation/lifecycle architecture change):
    //  - the destination leaves composition when the user switches tabs, so this keyed effect
    //    fires exactly once per entry (a cold start is owned by the ViewModel's initial load);
    //  - a real background → foreground transition while History is active refreshes once. The
    //    "was stopped" bit makes the cold-start ON_START indistinguishable from a normal start.
    LaunchedEffect(Unit) { viewModel.onSurfaceShown() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var wentToBackground by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wentToBackground = true
                Lifecycle.Event.ON_START -> if (wentToBackground) {
                    wentToBackground = false
                    viewModel.onAppForegrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HistoryScreenContent(
        state = state,
        is24Hour = is24Hour,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onSelectDate = viewModel::selectDate,
        onRetry = viewModel::retry,
        modifier = modifier,
        showTopBar = showTopBar,
        onOpenInsights = onOpenInsights,
        onOpenRetrospectivePk = onOpenRetrospectivePk,
        onOpenTimeline = onOpenTimeline
    )
}

/**
 * 无状态历史屏幕内容：供屏幕组合、Preview 与 UI 测试直接驱动一个合成 [HistoryUiState]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreenContent(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onSelectDate: (LocalDate) -> Unit = {},
    onRetry: () -> Unit = {},
    showTopBar: Boolean = false,
    onOpenInsights: () -> Unit = {},
    onOpenRetrospectivePk: () -> Unit = {},
    onOpenTimeline: () -> Unit = {}
) {
    val model = remember(state) { HistoryPresentation.present(state) }

    Scaffold(
        modifier = modifier.testTag("history-screen"),
        contentWindowInsets = if (showTopBar) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .testTag("history-content-list"),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { InsightsEntryCard(onOpenInsights) }
            item { RetrospectivePkEntryCard(onOpenRetrospectivePk) }
            item { TimelineEntryCard(onOpenTimeline) }
            item { MonthNavigationHeader(model, onPreviousMonth, onNextMonth) }
            item { WeekdayHeader() }
            item { HistoryMonthCalendar(model, onSelectDate) }
            item { SelectedDaySummary(model) }
            when (model.phase) {
                HistoryDayPhase.LOADING -> item { HistoryLoading() }
                HistoryDayPhase.ERROR -> item { HistoryError(onRetry) }
                HistoryDayPhase.EMPTY -> item { HistoryEmpty() }
                HistoryDayPhase.CONTENT -> {
                    val entries = model.day?.entries.orEmpty()
                    items(entries, key = { it.key }) { entry ->
                        HistoryEntryCard(entry = entry, is24Hour = is24Hour)
                    }
                }
            }
        }
    }
}

// ---------- Retrospective PK entry (v1.7-C-04) ----------

/**
 * The visible entry point into the retrospective PK surface: the estimate consumes the same
 * authoritative history, so it is reached from History instead of a sixth bottom tab
 * (V17-C-04 §7/§D-8). The card is presentational only; it never reads or derives anything.
 */
@Composable
private fun RetrospectivePkEntryCard(onOpenRetrospectivePk: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
            .clickable(
                onClickLabel = stringResource(R.string.retrospective_entry_action)
            ) { onOpenRetrospectivePk() }
            .testTag("history-retrospective-entry"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.retrospective_entry_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.retrospective_entry_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

// ---------- Timeline entry (v1.7-D-04) ----------

/**
 * The visible entry point into the Timeline surface: the Timeline renders the same authoritative
 * history as a month-scoped calendar + row list, so it is reached from History instead of a sixth
 * bottom tab (V17-D-04 §2). The card carries no date/month argument (F16) and never reads or
 * derives anything.
 */
@Composable
private fun TimelineEntryCard(onOpenTimeline: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClickLabel = stringResource(R.string.timeline_entry_action)) {
                onOpenTimeline()
            }
            .testTag("history-timeline-entry"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.timeline_entry_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.timeline_entry_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

// ---------- Insights entry (v1.7-B-03) ----------

/**
 * The visible entry point into the Insights surface: History and Insights describe the same
 * authoritative history, so Insights is reached from History instead of a sixth bottom tab.
 */
@Composable
private fun InsightsEntryCard(onOpenInsights: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClickLabel = stringResource(R.string.insights_entry_action)) { onOpenInsights() }
            .testTag("history-insights-entry"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.insights_entry_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.insights_entry_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

// ---------- header ----------

@Composable
private fun MonthNavigationHeader(
    model: HistoryMonthUiModel,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPreviousMonth,
            modifier = Modifier.testTag("history-previous-month")
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.history_previous_month)
            )
        }
        Text(
            text = stringResource(
                R.string.history_month_title,
                model.month.year,
                model.month.monthValue
            ),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .testTag("history-month-title")
        )
        IconButton(
            onClick = onNextMonth,
            enabled = model.canGoToNextMonth,
            modifier = Modifier.testTag("history-next-month")
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.history_next_month)
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val weekdays = listOf(
        R.string.history_weekday_mon,
        R.string.history_weekday_tue,
        R.string.history_weekday_wed,
        R.string.history_weekday_thu,
        R.string.history_weekday_fri,
        R.string.history_weekday_sat,
        R.string.history_weekday_sun
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("history-weekday-header")
    ) {
        weekdays.forEach { label ->
            Text(
                text = stringResource(label),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------- calendar ----------

@Composable
private fun HistoryMonthCalendar(
    model: HistoryMonthUiModel,
    onSelectDate: (LocalDate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("history-calendar")
    ) {
        model.cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                week.forEach { cell ->
                    HistoryCalendarCell(
                        cell = cell,
                        onSelectDate = onSelectDate,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HistoryCalendarCell(
    cell: HistoryCalendarCellUiModel,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val date = cell.date
    if (date == null) {
        Box(modifier = modifier.aspectRatio(1f))
        return
    }

    val description = calendarCellDescription(cell)
    val containerColor = when {
        cell.isSelected -> MaterialTheme.colorScheme.primaryContainer
        cell.isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        !cell.isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        cell.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .padding(2.dp)
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (cell.isEnabled) {
                    Modifier.clickable { onSelectDate(date) }
                } else {
                    Modifier.semantics { disabled() }
                }
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("history-cell-$date"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (cell.isSelected || cell.isToday) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.clearAndSetSemantics { }
        )
        DayIndicators(cell)
    }
}

@Composable
private fun DayIndicators(cell: HistoryCalendarCellUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (cell.hasRecorded) {
            IndicatorDot(MaterialTheme.colorScheme.primary, "history-indicator-recorded")
        }
        if (cell.hasUnrecorded) {
            IndicatorDot(MaterialTheme.colorScheme.outline, "history-indicator-unrecorded")
        }
        if (cell.hasUnmatchedActual) {
            IndicatorDot(MaterialTheme.colorScheme.tertiary, "history-indicator-unmatched")
        }
    }
}

@Composable
private fun IndicatorDot(color: Color, tag: String) {
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
            .testTag(tag)
    )
}

@Composable
private fun calendarCellDescription(cell: HistoryCalendarCellUiModel): String {
    val date = cell.date ?: return ""
    val parts = mutableListOf(date.toString())
    if (cell.isToday) parts += stringResource(R.string.history_cell_today)
    if (cell.isSelected) parts += stringResource(R.string.history_cell_selected)
    if (!cell.isEnabled) parts += stringResource(R.string.history_cell_not_arrived)
    // Real counts, never a fabricated "1": the cell model carries the domain counts and the
    // indicator dots derive from the same numbers (A-03-UI-R1 accessibility fix).
    val counts = mutableListOf<String>()
    if (cell.recordedCount > 0) {
        counts += stringResource(R.string.history_cell_recorded, cell.recordedCount)
    }
    if (cell.unrecordedCount > 0) {
        counts += stringResource(R.string.history_cell_no_recorded_intake, cell.unrecordedCount)
    }
    if (cell.unmatchedActualCount > 0) {
        counts += stringResource(R.string.history_cell_other_intake, cell.unmatchedActualCount)
    }
    parts += if (counts.isEmpty()) {
        stringResource(R.string.history_cell_no_history)
    } else {
        counts.joinToString("，")
    }
    return parts.joinToString("，")
}

// ---------- selected day ----------

@Composable
private fun SelectedDaySummary(model: HistoryMonthUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("history-day-summary")
    ) {
        Text(
            text = stringResource(
                R.string.history_day_title,
                model.selectedDate.monthValue,
                model.selectedDate.dayOfMonth
            ),
            style = MaterialTheme.typography.titleMedium
        )
        model.day?.let { day ->
            Text(
                text = stringResource(R.string.history_summary_recorded, day.recordedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.history_summary_no_recorded_intake, day.unrecordedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.history_summary_other_intake, day.unmatchedActualCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- states ----------

@Composable
private fun HistoryLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("history-loading"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.history_loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun HistoryError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("history-error"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.history_error_title), style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = onRetry,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("history-retry")
        ) {
            Text(stringResource(R.string.history_retry))
        }
    }
}

@Composable
private fun HistoryEmpty() {
    Text(
        text = stringResource(R.string.history_empty_day),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("history-empty-day")
    )
}

// ---------- entry card ----------

@Composable
private fun HistoryEntryCard(entry: HistoryEntryUiModel, is24Hour: Boolean) {
    val statusColor = when (entry.kind) {
        HistoryEntryKind.MATCHED -> MaterialTheme.colorScheme.primary
        HistoryEntryKind.UNRECORDED -> MaterialTheme.colorScheme.onSurfaceVariant
        HistoryEntryKind.UNMATCHED -> MaterialTheme.colorScheme.tertiary
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("history-entry-${entry.key}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Screen-reader order: status -> medication/dose -> actual time -> auxiliary context.
            Text(
                text = stringResource(entry.statusLabelRes),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
                modifier = Modifier.testTag("history-entry-status")
            )
            Text(
                text = headline(entry),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            metadata(entry)?.let { meta ->
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.actualTime?.let { actual ->
                Text(
                    text = stringResource(R.string.history_label_actual_time) + " " +
                        HistoryFormatting.actualIntakeText(
                            instant = actual.instant,
                            zone = actual.zone,
                            needsFullDate = actual.needsFullDate,
                            is24Hour = is24Hour
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("history-entry-actual-time")
                )
            }
            entry.scheduleTime?.let { schedule ->
                Text(
                    text = stringResource(R.string.history_label_current_schedule_context) + " " +
                        HistoryFormatting.scheduleTimeText(
                            instant = schedule.instant,
                            zone = schedule.zone,
                            needsFullDate = schedule.needsFullDate,
                            is24Hour = is24Hour
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("history-entry-schedule-time")
                )
            }
            entry.sourceLabelRes?.let { source ->
                Text(
                    text = stringResource(source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.noteRes?.let { note ->
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("history-entry-note")
                )
            }
            entry.secondaryNoteRes?.let { note ->
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun headline(entry: HistoryEntryUiModel): String {
    val medication = entry.medicationLabelRes?.let { stringResource(it) } ?: entry.medicationFallback
    val dose = entry.doseAmount?.let { HistoryFormatting.dose(it) }
    val parts = listOfNotNull(medication, dose)
    return when {
        parts.isNotEmpty() -> parts.joinToString(" · ")
        entry.planName != null -> entry.planName
        else -> stringResource(entry.statusLabelRes)
    }
}

@Composable
private fun metadata(entry: HistoryEntryUiModel): String? {
    val route = entry.routeLabelRes?.let { stringResource(it) } ?: entry.routeFallback
    val parts = listOfNotNull(entry.planName, route)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

// ---------- previews (synthetic state only, never the read service) ----------

private val PREVIEW_ZONE: ZoneId = ZoneOffset.UTC
private val PREVIEW_DAY: LocalDate = LocalDate.of(2025, 1, 5)
private val PREVIEW_MONTH: YearMonth = YearMonth.of(2025, 1)

@Preview(name = "有记录的混合日", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewHistoryMixedDay() {
    EvoluneTheme {
        HistoryScreenContent(state = previewState(PreviewDay.MIXED))
    }
}

@Preview(name = "空日", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewHistoryEmptyDay() {
    EvoluneTheme {
        HistoryScreenContent(state = previewState(PreviewDay.EMPTY))
    }
}

@Preview(name = "加载失败", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewHistoryError() {
    EvoluneTheme {
        HistoryScreenContent(state = previewState(PreviewDay.ERROR))
    }
}

private enum class PreviewDay { MIXED, EMPTY, ERROR }

private fun previewState(previewDay: PreviewDay): HistoryUiState {
    val days = if (previewDay == PreviewDay.EMPTY) {
        mapOf(PREVIEW_DAY to HistoricalDay(date = PREVIEW_DAY, entries = emptyList()))
    } else {
        previewDays()
    }
    return HistoryUiState(
        visibleMonth = PREVIEW_MONTH,
        selectedDate = PREVIEW_DAY,
        today = PREVIEW_DAY,
        displayZone = PREVIEW_ZONE,
        loadedMonth = PREVIEW_MONTH,
        loadedDays = days,
        loading = false,
        failed = previewDay == PreviewDay.ERROR
    )
}

private fun previewDays(): Map<LocalDate, HistoricalDay> {
    val matchKey = MedicationMatchKey(routeKey = "ORAL", medicationKey = "E2", doseAmount = 2.0)
    val presentation = MedicationPresentation(planName = "示例方案", matchKey = matchKey)

    fun occurrence(slotId: UUID, hour: Int, minute: Int): MedicationOccurrence {
        val local = PREVIEW_DAY.atTime(hour, minute)
        return MedicationOccurrence(
            id = MedicationOccurrenceId(UUID(3L, slotId.leastSignificantBits)),
            planId = UUID(0L, 1L),
            slotId = slotId,
            slotPosition = 0,
            presentation = presentation,
            scheduledAt = local.toInstant(ZoneOffset.UTC),
            scheduledLocalDateTime = local,
            zoneId = PREVIEW_ZONE
        )
    }

    val exactOccurrence = occurrence(UUID(1L, 1L), 8, 0)
    val inferredOccurrence = occurrence(UUID(1L, 2L), 23, 0)
    val unrecordedOccurrence = occurrence(UUID(1L, 3L), 16, 0)

    val exactEvent = RecordedMedicationEvent(
        eventId = UUID(7L, 1L),
        occurredAt = PREVIEW_DAY.atTime(8, 5).toInstant(ZoneOffset.UTC),
        slotId = exactOccurrence.slotId,
        matchKey = matchKey,
        source = MedicationIntakeSource.REMINDER,
        localDate = PREVIEW_DAY,
        zoneId = PREVIEW_ZONE
    )
    // Delayed legacy record: the actual intake happens on the next local date, so the card
    // must show a full date instead of a bare time.
    val inferredEvent = RecordedMedicationEvent(
        eventId = UUID(7L, 2L),
        occurredAt = PREVIEW_DAY.plusDays(1).atTime(1, 30).toInstant(ZoneOffset.UTC),
        slotId = inferredOccurrence.slotId,
        matchKey = matchKey,
        source = MedicationIntakeSource.LEGACY,
        localDate = null,
        zoneId = null
    )
    val manualOrphan = RecordedMedicationEvent(
        eventId = UUID(7L, 3L),
        occurredAt = PREVIEW_DAY.atTime(21, 0).toInstant(ZoneOffset.UTC),
        slotId = null,
        matchKey = MedicationMatchKey(routeKey = "INJECTION", medicationKey = "EV", doseAmount = 5.0),
        source = MedicationIntakeSource.MANUAL,
        localDate = PREVIEW_DAY,
        zoneId = PREVIEW_ZONE
    )
    val legacyOrphan = RecordedMedicationEvent(
        eventId = UUID(7L, 4L),
        occurredAt = PREVIEW_DAY.atTime(9, 45).toInstant(ZoneOffset.UTC),
        slotId = null,
        matchKey = MedicationMatchKey(routeKey = "SUBLINGUAL", medicationKey = "E2", doseAmount = 1.0),
        source = MedicationIntakeSource.LEGACY,
        localDate = null,
        zoneId = null
    )

    val entries: List<HistoricalEntry> = listOf(
        MatchedHistoricalOccurrence(
            occurrence = exactOccurrence,
            event = exactEvent,
            matchProvenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE,
            status = MedicationOccurrenceStatus.RECORDED,
            actionAvailability = MedicationActionAvailability.ALREADY_RECORDED,
            scheduleTimeContext = HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
            crossesLocalDateBoundary = false,
            displayDate = PREVIEW_DAY,
            displayDateProvenance = HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE
        ),
        MatchedHistoricalOccurrence(
            occurrence = inferredOccurrence,
            event = inferredEvent,
            matchProvenance = MedicationMatchProvenance.SLOT_WINDOW_WITHOUT_LOCAL_DATE,
            status = MedicationOccurrenceStatus.RECORDED,
            actionAvailability = MedicationActionAvailability.ALREADY_RECORDED,
            scheduleTimeContext = HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
            crossesLocalDateBoundary = true,
            displayDate = PREVIEW_DAY,
            displayDateProvenance = HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE
        ),
        UnrecordedHistoricalOccurrence(
            occurrence = unrecordedOccurrence,
            status = MedicationOccurrenceStatus.PAST_UNRECORDED,
            actionAvailability = MedicationActionAvailability.WINDOW_EXPIRED,
            displayDate = PREVIEW_DAY
        ),
        UnmatchedHistoricalIntake(
            event = manualOrphan,
            source = MedicationIntakeSource.MANUAL,
            displayDate = PREVIEW_DAY,
            displayDateProvenance = HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE
        ),
        UnmatchedHistoricalIntake(
            event = legacyOrphan,
            source = MedicationIntakeSource.LEGACY,
            displayDate = PREVIEW_DAY,
            displayDateProvenance = HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED
        )
    )
    return mapOf(PREVIEW_DAY to HistoricalDay(date = PREVIEW_DAY, entries = entries))
}
