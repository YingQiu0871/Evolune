package io.github.yingqiu0871.evolune.ui.screens.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangePhase
import io.github.yingqiu0871.evolune.history.timeline.TimelineRangeState
import io.github.yingqiu0871.evolune.history.timeline.TimelineViewModel
import java.time.LocalDate

/** Day-cell slot geometry: one fixed width per cell keeps the strip symmetric (D-04 §11). */
private val DAY_CELL_WIDTH = 48.dp
private val DAY_HIGHLIGHT_SIZE = 36.dp
private val MONTH_CONTROL_SLOT = 48.dp

/**
 * V17-D-04 §26 — the production Timeline destination.
 *
 * Every fact comes from the published D-03 [TimelineRangeState]; this file reads no history,
 * rebuilds no model and derives no today of its own. The surface lifecycle bridge is hosted here,
 * exactly like the Insights / Retrospective precedents.
 */
@Composable
fun TimelineRoute(
    viewModel: TimelineViewModel,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    showTopBar: Boolean = false
) {
    val state by viewModel.state.collectAsState()

    TimelineSurfaceLifecycle(viewModel)

    TimelineScreenContent(
        state = state,
        modifier = modifier,
        is24Hour = is24Hour,
        showTopBar = showTopBar,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onSelectDate = viewModel::selectDate,
        onRetry = viewModel::retry,
        onReturnToCurrentMonth = viewModel::returnToCurrentMonth
    )
}

/**
 * Stateless Timeline content: previews and UI tests drive one synthetic [TimelineRangeState],
 * exactly like the History / Insights / Retrospective patterns.
 *
 * Layout split (D-04 §8/§10/§14/§33, frozen): calendar / month / day selection is centered while
 * the information body below is left aligned. Selection only moves presentation focus; the whole
 * loaded month stays rendered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreenContent(
    state: TimelineRangeState,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    showTopBar: Boolean = false,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onSelectDate: (LocalDate) -> Unit = {},
    onRetry: () -> Unit = {},
    onReturnToCurrentMonth: () -> Unit = {}
) {
    val model = remember(state, is24Hour) { TimelinePresentation.present(state, is24Hour) }
    val bodyListState = rememberLazyListState()

    // Selection focus (§27): the body scrolls to the selected section only when the selection
    // actually changes (never on recomposition or refresh), and only when that section exists.
    var lastFocusedSelection by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(state.selectedDate, model.sections) {
        val previous = lastFocusedSelection
        if (previous == null) {
            lastFocusedSelection = state.selectedDate
            return@LaunchedEffect
        }
        if (previous == state.selectedDate) return@LaunchedEffect
        lastFocusedSelection = state.selectedDate
        val index = model.sections.indexOfFirst { it.date == state.selectedDate }
        if (index >= 0) {
            bodyListState.animateScrollToItem(index + 1)
        }
    }

    Scaffold(
        modifier = modifier.testTag("timeline-screen"),
        contentWindowInsets = if (showTopBar) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.timeline_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = bodyListState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .testTag("timeline-content-list"),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "timeline-calendar") {
                TimelineCalendarRegion(
                    model = model,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onSelectDate = onSelectDate
                )
            }
            when (model.phase) {
                TimelineRangePhase.LOADING -> item(key = "loading") { TimelineLoading() }

                TimelineRangePhase.EMPTY_RANGE -> item(key = "empty-range") { NeutralNotice(
                    textRes = R.string.timeline_empty_range,
                    tag = "timeline-empty-range"
                ) }

                TimelineRangePhase.EMPTY_DAY -> {
                    item(key = "empty-day") { NeutralNotice(
                        textRes = R.string.timeline_empty_day,
                        tag = "timeline-empty-day"
                    ) }
                    timelineSections(model)
                }

                TimelineRangePhase.INVALID_REQUEST -> item(key = "invalid") { DefensiveBlock(
                    textRes = R.string.timeline_invalid_request,
                    tag = "timeline-invalid-request",
                    onReturnToCurrentMonth = onReturnToCurrentMonth
                ) }

                TimelineRangePhase.NOT_LOADABLE -> item(key = "not-loadable") { DefensiveBlock(
                    textRes = R.string.timeline_not_loadable,
                    tag = "timeline-not-loadable",
                    onReturnToCurrentMonth = onReturnToCurrentMonth
                ) }

                TimelineRangePhase.ERROR -> item(key = "error") { TimelineError(onRetry) }

                TimelineRangePhase.CONTENT -> timelineSections(model)
            }
        }
    }
}

/** The complete non-empty month, newest section first; rows keep canonical D-01 order. */
private fun LazyListScope.timelineSections(model: TimelinePresentation.Model) =
    model.sections.forEach { section ->
        item(key = "section-${section.date}") {
            TimelineSection(section = section)
        }
    }

// ---------- calendar region (centered) ----------

@Composable
private fun TimelineCalendarRegion(
    model: TimelinePresentation.Model,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("timeline-calendar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MonthNavigationRow(
            model = model,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth
        )
        if (model.effectiveStartDate != null && model.effectiveEndDate != null) {
            Spacer(modifier = Modifier.height(4.dp))
            DayStrip(model = model, onSelectDate = onSelectDate)
        }
    }
}

/**
 * Month controls with symmetric fixed-width navigation slots: the title stays geometrically
 * centered even while the next-month control is disabled (D-04 §8, F20).
 */
@Composable
private fun MonthNavigationRow(
    model: TimelinePresentation.Model,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timeline-month-navigation"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(MONTH_CONTROL_SLOT)
                .testTag("timeline-previous-month-slot"),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.testTag("timeline-previous-month")
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.timeline_previous_month)
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(
                    R.string.timeline_month_title,
                    model.requestedMonth.year,
                    model.requestedMonth.monthValue
                ),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("timeline-month-title")
            )
        }
        Box(
            modifier = Modifier
                .size(MONTH_CONTROL_SLOT)
                .testTag("timeline-next-month-slot"),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onNextMonth,
                enabled = model.canGoToNextMonth,
                modifier = Modifier.testTag("timeline-next-month")
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.timeline_next_month)
                )
            }
        }
    }
}

// ---------- compact day strip (centered) ----------

/**
 * Compact horizontal strip over `effectiveStartDate .. effectiveEndDate` (ascending, at most 31
 * cells). Symmetric content padding lets the selected cell rest at the horizontal center of the
 * viewport, including the first and last selectable days (D-04 §11/§11.1, UI46/UI47).
 */
@Composable
private fun DayStrip(
    model: TimelinePresentation.Model,
    onSelectDate: (LocalDate) -> Unit
) {
    val stripState = rememberLazyListState()
    val selectedIndex = model.dayCells.indexOfFirst { it.isSelected }
    LaunchedEffect(selectedIndex, model.effectiveStartDate, model.effectiveEndDate) {
        if (selectedIndex >= 0) {
            stripState.animateScrollToItem(selectedIndex, 0)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timeline-day-strip-container")
    ) {
        val sidePadding = ((maxWidth - DAY_CELL_WIDTH) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = stripState,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("timeline-day-strip"),
            contentPadding = PaddingValues(horizontal = sidePadding)
        ) {
            itemsIndexed(
                items = model.dayCells,
                key = { _, cell -> cell.date }
            ) { _, cell ->
                TimelineDayCell(cell = cell, onSelectDate = onSelectDate)
            }
        }
    }
}

/**
 * One day cell: weekday label, date number and selected highlight share one horizontal center
 * axis; the selected number is centered on both axes inside its highlight by structural
 * `Alignment.Center` (no offsets, no asymmetric padding, no baseline tricks — D-04 §10, F19).
 */
@Composable
private fun TimelineDayCell(
    cell: TimelinePresentation.DayCell,
    onSelectDate: (LocalDate) -> Unit
) {
    val containerColor = when {
        cell.isSelected -> MaterialTheme.colorScheme.primaryContainer
        cell.isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        !cell.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        cell.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val description = dayCellDescription(cell)

    Column(
        modifier = Modifier
            .width(DAY_CELL_WIDTH)
            .clickable(enabled = cell.enabled) { onSelectDate(cell.date) }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = cell.isSelected
                if (!cell.enabled) disabled()
            }
            .padding(vertical = 4.dp)
            .testTag("timeline-day-cell-${cell.date}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(TimelinePresentation.weekdayRes(cell.date.dayOfWeek)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("timeline-day-weekday-${cell.date}")
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(DAY_HIGHLIGHT_SIZE)
                .clip(CircleShape)
                .background(containerColor)
                .testTag("timeline-day-highlight-${cell.date}"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (cell.isSelected || cell.isToday) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
                modifier = Modifier.testTag("timeline-day-number-${cell.date}")
            )
        }
    }
}

@Composable
private fun dayCellDescription(cell: TimelinePresentation.DayCell): String {
    val parts = mutableListOf(cell.date.toString())
    if (cell.isToday) parts += stringResource(R.string.history_cell_today)
    if (cell.isSelected) parts += stringResource(R.string.history_cell_selected)
    if (!cell.enabled) parts += stringResource(R.string.history_cell_not_arrived)
    return parts.joinToString("，")
}

// ---------- month sections (left aligned) ----------

@Composable
private fun TimelineSection(section: TimelinePresentation.Section) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("timeline-section-${section.date}"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (val label = section.label) {
            is TimelinePresentation.SectionLabel.Relative -> Text(
                text = stringResource(label.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("timeline-section-header-${section.date}")
            )

            is TimelinePresentation.SectionLabel.Absolute -> Text(
                text = stringResource(
                    R.string.timeline_section_date_weekday,
                    label.dateText,
                    stringResource(label.weekdayRes)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("timeline-section-header-${section.date}")
            )
        }
        section.rows.forEachIndexed { index, row ->
            TimelineRowCard(
                row = row,
                tag = "timeline-row-${section.date}-$index"
            )
        }
    }
}

// ---------- truthful row cards ----------

@Composable
private fun TimelineRowCard(row: TimelinePresentation.RowUi, tag: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (row) {
                is TimelinePresentation.RowUi.Matched -> {
                    ScheduleSide(
                        timeText = row.scheduleTimeText,
                        identity = row.scheduleIdentity,
                        doseText = row.scheduleDoseText
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    RecordedSide(
                        timeText = row.recordedTimeText,
                        identity = row.recordedIdentity,
                        doseText = row.recordedDoseText
                    )
                }

                is TimelinePresentation.RowUi.Unrecorded -> {
                    ScheduleSide(
                        timeText = row.scheduleTimeText,
                        identity = row.scheduleIdentity,
                        doseText = row.scheduleDoseText
                    )
                    Text(
                        text = stringResource(R.string.timeline_no_recorded_intake),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .testTag("timeline-no-recorded-intake")
                    )
                }

                is TimelinePresentation.RowUi.Unmatched -> {
                    RecordedSide(
                        timeText = row.recordedTimeText,
                        identity = row.recordedIdentity,
                        doseText = row.recordedDoseText
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleSide(
    timeText: String,
    identity: TimelinePresentation.IdentityUi,
    doseText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timeline-schedule-side"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.history_label_current_schedule_context),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("timeline-schedule-time")
        )
        Text(
            text = stringResource(
                R.string.timeline_row_identity_dose,
                stringResource(identity.labelRes),
                doseText
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("timeline-schedule-identity-dose")
        )
    }
}

@Composable
private fun RecordedSide(
    timeText: String,
    identity: TimelinePresentation.IdentityUi,
    doseText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timeline-recorded-side"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.history_label_actual_time),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("timeline-recorded-time")
        )
        Text(
            text = stringResource(
                R.string.timeline_row_identity_dose,
                stringResource(identity.labelRes),
                doseText
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("timeline-recorded-identity-dose")
        )
    }
}

// ---------- state regions ----------

@Composable
private fun TimelineLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("timeline-loading"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.timeline_loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun NeutralNotice(textRes: Int, tag: String) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag(tag)
    )
}

@Composable
private fun DefensiveBlock(
    textRes: Int,
    tag: String,
    onReturnToCurrentMonth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(
            onClick = onReturnToCurrentMonth,
            modifier = Modifier.testTag("timeline-return-current-month")
        ) {
            Text(stringResource(R.string.timeline_return_current_month))
        }
    }
}

@Composable
private fun TimelineError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("timeline-error"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.timeline_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag("timeline-retry")
        ) {
            Text(stringResource(R.string.timeline_retry))
        }
    }
}
