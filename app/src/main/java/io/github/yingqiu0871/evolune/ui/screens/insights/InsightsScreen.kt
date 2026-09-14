package io.github.yingqiu0871.evolune.ui.screens.insights

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.history.insights.InsightsPhase
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection
import io.github.yingqiu0871.evolune.history.insights.InsightsSurfaceLifecycle
import io.github.yingqiu0871.evolune.history.insights.InsightsUiState
import io.github.yingqiu0871.evolune.history.insights.InsightsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * v1.7-B-03: the production Insights destination.
 *
 * B-02 owns every fact and every query. This file only renders the frozen state: it never reads
 * history, never aggregates and never recomputes a count. The surface/foreground contract is the
 * approved B-02 bridge, hosted here for the first time in a real destination.
 */
@Composable
fun InsightsRoute(
    viewModel: InsightsViewModel,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()

    // v1.7-B-02 §19/§20: first composition adds no read (the initial load owns it), a return from
    // another destination refreshes once, a real stop → start refreshes once. The observer lives
    // only while this destination is composed.
    InsightsSurfaceLifecycle(viewModel)

    InsightsScreenContent(
        state = state,
        modifier = modifier,
        showTopBar = showTopBar,
        onSelectRange = viewModel::selectRange,
        onRetry = viewModel::retry
    )
}

/**
 * Stateless Insights content: screen composition, Preview and UI tests all drive one synthetic
 * [InsightsUiState], exactly like the History surface does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreenContent(
    state: InsightsUiState,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = false,
    onSelectRange: (InsightsRangeSelection) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val model = remember(state) { InsightsPresentation.present(state) }
    var showCustomPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.testTag("insights-screen"),
        contentWindowInsets = if (showTopBar) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.insights_title)) },
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
                .testTag("insights-content-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                RangeSelector(
                    selection = state.selection,
                    onSelectRange = onSelectRange,
                    onCustomClick = { showCustomPicker = true }
                )
            }
            model.rangeHeading?.let { heading ->
                item {
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("insights-range-heading")
                    )
                }
            }
            model.timezoneDisclosureRes?.let { disclosure ->
                item { DisclosureText(disclosure, "insights-timezone-disclosure") }
            }

            when {
                state.phase == InsightsPhase.LOADING -> item { InsightsLoading() }
                state.phase == InsightsPhase.INVALID_RANGE -> item {
                    InsightsInvalidRange(model.validationMessageRes)
                }
                state.phase == InsightsPhase.ERROR -> item { InsightsError(onRetry) }
                state.phase == InsightsPhase.EMPTY -> item { InsightsEmpty() }
                else -> {
                    item { OverviewCards(model) }
                    item { CoverageSection(model) }
                    item { SourceSection(model) }
                    item { ConfidenceSection(model) }
                    item { DoseSection(model) }
                }
            }
        }
    }

    if (showCustomPicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.startDate?.toUtcMillis(),
            initialSelectedEndDateMillis = state.endDate?.toUtcMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showCustomPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.toLocalDateUtc()
                        val end = pickerState.selectedEndDateMillis?.toLocalDateUtc()
                        showCustomPicker = false
                        if (start != null && end != null) {
                            onSelectRange(InsightsRangeSelection.Custom(start, end))
                        }
                    },
                    modifier = Modifier.testTag("insights-custom-range-confirm")
                ) {
                    Text(stringResource(R.string.insights_range_custom_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomPicker = false },
                    modifier = Modifier.testTag("insights-custom-range-cancel")
                ) {
                    Text(stringResource(R.string.insights_range_custom_cancel))
                }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.testTag("insights-custom-range-picker"),
                showModeToggle = false
            )
        }
    }
}

// ---------- range selector ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(
    selection: InsightsRangeSelection,
    onSelectRange: (InsightsRangeSelection) -> Unit,
    onCustomClick: () -> Unit
) {
    val options = listOf(
        InsightsRangeSelection.Last7Days,
        InsightsRangeSelection.Last30Days,
        InsightsRangeSelection.Last90Days,
        InsightsRangeSelection.CurrentMonth,
        InsightsRangeSelection.Custom(LocalDate.MIN, LocalDate.MIN)
    )
    Column(modifier = Modifier.testTag("insights-range-selector")) {
        Text(
            text = stringResource(R.string.insights_range_selector_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isCustom = option is InsightsRangeSelection.Custom
                val selected = if (isCustom) {
                    selection is InsightsRangeSelection.Custom
                } else {
                    selection == option
                }
                FilterChip(
                    selected = selected,
                    onClick = { if (isCustom) onCustomClick() else onSelectRange(option) },
                    label = { Text(stringResource(InsightsPresentation.selectionLabelRes(option))) },
                    modifier = Modifier.testTag(
                        if (isCustom) "insights-range-custom" else option.testTagSuffix()
                    )
                )
            }
        }
    }
}

private fun InsightsRangeSelection.testTagSuffix(): String = when (this) {
    InsightsRangeSelection.Last7Days -> "insights-range-last7"
    InsightsRangeSelection.Last30Days -> "insights-range-last30"
    InsightsRangeSelection.Last90Days -> "insights-range-last90"
    InsightsRangeSelection.CurrentMonth -> "insights-range-current-month"
    is InsightsRangeSelection.Custom -> "insights-range-custom"
}

// ---------- content sections ----------

@Composable
private fun OverviewCards(model: InsightsPresentation.InsightsUiModel) {
    model.overviewCards.forEach { card ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(card.testTag()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(card.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = card.value.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun InsightsPresentation.MetricCard.testTag(): String = when (labelRes) {
    R.string.insights_card_recorded_intakes -> "insights-card-recorded-intakes"
    else -> "insights-card-recorded-days"
}

@Composable
private fun CoverageSection(model: InsightsPresentation.InsightsUiModel) {
    val coverage = model.coverage ?: return
    SectionCard(
        title = stringResource(coverage.titleRes),
        testTag = "insights-coverage-section"
    ) {
        CountRow(coverage.linkedLabelRes, coverage.linkedCount, "insights-coverage-linked")
        CountRow(coverage.unlinkedLabelRes, coverage.unlinkedCount, "insights-coverage-unlinked")
        coverage.disclosureRes.forEachIndexed { index, res ->
            DisclosureText(
                textRes = res,
                testTag = if (index == 0) {
                    "insights-coverage-disclosure-schedule"
                } else {
                    "insights-coverage-disclosure-absence"
                }
            )
        }
    }
}

@Composable
private fun SourceSection(model: InsightsPresentation.InsightsUiModel) {
    SectionCard(
        title = stringResource(R.string.insights_sources_title),
        testTag = "insights-sources-section"
    ) {
        model.sources.forEach { row ->
            CountRow(row.labelRes, row.count, "insights-source-row-${row.source.name}")
        }
    }
}

@Composable
private fun ConfidenceSection(model: InsightsPresentation.InsightsUiModel) {
    SectionCard(
        title = stringResource(R.string.insights_confidence_title),
        testTag = "insights-confidence-section"
    ) {
        Text(
            text = stringResource(R.string.insights_confidence_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("insights-confidence-explanation")
        )
        model.confidence.forEach { row ->
            CountRow(row.labelRes, row.count, "insights-confidence-row-${row.confidence.name}")
        }
    }
}

@Composable
private fun DoseSection(model: InsightsPresentation.InsightsUiModel) {
    SectionCard(
        title = stringResource(R.string.insights_dose_title),
        testTag = "insights-dose-section"
    ) {
        model.doseRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("insights-dose-row-${row.medicationKey.name}"),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(row.labelRes))
                Text(
                    text = io.github.yingqiu0871.evolune.history.HistoryFormatting.dose(row.totalMg),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        model.doseEmptyMessageRes?.let { emptyRes ->
            Text(
                text = stringResource(emptyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("insights-dose-empty")
            )
        }
        if (model.unknownIdentityCount > 0) {
            Text(
                text = stringResource(R.string.insights_unknown_identity, model.unknownIdentityCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("insights-unknown-identity")
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, testTag: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

/**
 * A label/count row. The count is real text (never colour- or geometry-only) and the row carries
 * the same pair as its content description, so TalkBack receives the same fact as the eye.
 */
@Composable
private fun CountRow(labelRes: Int, count: Int, testTag: String) {
    val label = stringResource(labelRes)
    val description = stringResource(R.string.insights_bar_description, label, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = count.toString(), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DisclosureText(textRes: Int, testTag: String) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(testTag)
    )
}

// ---------- states ----------

@Composable
private fun InsightsLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .testTag("insights-loading"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.insights_loading),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InsightsEmpty() {
    Text(
        text = stringResource(R.string.insights_empty),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("insights-empty")
    )
}

@Composable
private fun InsightsError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("insights-error")
    ) {
        Text(text = stringResource(R.string.insights_error), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRetry, modifier = Modifier.testTag("insights-retry")) {
            Text(stringResource(R.string.insights_retry))
        }
    }
}

@Composable
private fun InsightsInvalidRange(messageRes: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("insights-invalid-range")
    ) {
        Text(
            text = stringResource(messageRes ?: R.string.insights_invalid_generic),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// ---------- date helpers (picker millis ↔ LocalDate) ----------

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
