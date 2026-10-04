package io.github.yingqiu0871.evolune.ui.screens.labs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import io.github.yingqiu0871.evolune.labs.LabResultDraft
import io.github.yingqiu0871.evolune.labs.LabResultDraftIssue
import io.github.yingqiu0871.evolune.labs.LabResultOperationState
import io.github.yingqiu0871.evolune.labs.LabResultsListState
import io.github.yingqiu0871.evolune.labs.LabResultsPresentation
import io.github.yingqiu0871.evolune.labs.LabResultsViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Which editor is open: a new result or an existing one. */
private sealed interface LabEditorTarget {
    data object New : LabEditorTarget
    data class Existing(val result: LabResult) : LabEditorTarget
}

/** PK 2.0 slice 3 — lab results list with add, edit and delete. */
@Composable
fun LabResultsRoute(
    viewModel: LabResultsViewModel,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true
) {
    val listState by viewModel.listState.collectAsState()
    val operation by viewModel.operation.collectAsState()
    var editor by remember { mutableStateOf<LabEditorTarget?>(null) }

    LaunchedEffect(operation) {
        if (operation == LabResultOperationState.Saved || operation == LabResultOperationState.Deleted) {
            editor = null
            viewModel.acknowledgeOperation()
        }
    }

    LabResultsScreenContent(
        listState = listState,
        modifier = modifier,
        is24Hour = is24Hour,
        onAdd = { editor = LabEditorTarget.New },
        onOpen = { editor = LabEditorTarget.Existing(it) }
    )

    editor?.let { target ->
        val initial = when (target) {
            LabEditorTarget.New -> viewModel.newDraft()
            is LabEditorTarget.Existing -> LabResultDraft(
                id = target.result.id,
                revision = target.result.revision,
                measuredAt = target.result.measuredAt,
                valueText = LabResultsPresentation.valueText(target.result.value),
                unit = target.result.unit
            )
        }
        LabResultEditorDialog(
            initial = initial,
            existing = (target as? LabEditorTarget.Existing)?.result,
            operation = operation,
            is24Hour = is24Hour,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = {
                editor = null
                viewModel.acknowledgeOperation()
            }
        )
    }
}

/** Stateless list content for previews and UI tests. */
@Composable
fun LabResultsScreenContent(
    listState: LabResultsListState,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
    onAdd: () -> Unit = {},
    onOpen: (LabResult) -> Unit = {}
) {
    Scaffold(
        modifier = modifier.testTag("lab-results-screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.lab_results_add)) },
                modifier = Modifier.testTag("lab-results-add")
            )
        }
    ) { innerPadding ->
        when (listState) {
            LabResultsListState.Loading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            LabResultsListState.Error -> CenteredNotice(
                text = stringResource(R.string.lab_results_load_error),
                tag = "lab-results-error",
                padding = innerPadding
            )

            is LabResultsListState.Content -> {
                val rows = remember(listState.results, is24Hour, zone) {
                    LabResultsPresentation.rows(listState.results, zone, is24Hour)
                }
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .testTag("lab-results-list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "intro") {
                        Text(
                            text = stringResource(R.string.lab_results_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.lab_results_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(vertical = 24.dp)
                                    .testTag("lab-results-empty")
                            )
                        }
                    }
                    items(rows, key = { it.result.id.toString() }) { row ->
                        LabResultRow(row = row, onOpen = { onOpen(row.result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LabResultRow(row: LabResultsPresentation.Row, onOpen: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.lab_results_edit_action)
            ) { onOpen() }
            .testTag("lab-result-row-${row.result.id}")
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
                    text = stringResource(
                        R.string.lab_results_value_with_unit,
                        row.valueText,
                        row.unitLabel
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = row.measuredAtText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    if (row.isTestosterone) R.string.lab_results_kind_testosterone
                    else R.string.lab_results_kind_estradiol
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CenteredNotice(text: String, tag: String, padding: PaddingValues) {
    Box(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(24.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { Text(text = text, style = MaterialTheme.typography.bodyMedium) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LabResultEditorDialog(
    initial: LabResultDraft,
    existing: LabResult?,
    operation: LabResultOperationState,
    is24Hour: Boolean,
    onSave: (LabResultDraft) -> Unit,
    onDelete: (LabResult) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    var dateTime by rememberSaveable {
        // Full precision is kept so an unchanged time (e.g. an imported result) is saved as is.
        mutableStateOf(LocalDateTime.ofInstant(initial.measuredAt, zone))
    }
    var valueText by rememberSaveable { mutableStateOf(initial.valueText) }
    var unit by rememberSaveable { mutableStateOf(initial.unit) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val running = operation == LabResultOperationState.Running

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        modifier = Modifier.testTag("lab-result-editor"),
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.lab_results_editor_add_title
                    else R.string.lab_results_editor_edit_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.testTag("lab-result-date")
                    ) { Text(dateTime.toLocalDate().toString()) }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.testTag("lab-result-time")
                    ) {
                        Text(
                            LabResultsPresentation.measuredAtText(
                                dateTime.atZone(zone).toInstant(), zone, is24Hour
                            ).substringAfter(' ')
                        )
                    }
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text(stringResource(R.string.lab_results_value_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = operation is LabResultOperationState.Invalid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lab-result-value")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabUnit.entries.forEach { option ->
                        FilterChip(
                            selected = unit == option,
                            onClick = { unit = option },
                            label = { Text(LabResultsPresentation.unitLabel(option)) },
                            modifier = Modifier.testTag("lab-result-unit-${option.code}")
                        )
                    }
                }
                if (!unit.isEstradiol) {
                    Text(
                        text = stringResource(R.string.lab_results_testosterone_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                operationMessage(operation)?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("lab-result-error")
                    )
                }
                if (existing != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !running,
                        modifier = Modifier.testTag("lab-result-delete")
                    ) {
                        Text(
                            stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            measuredAt = dateTime.atZone(zone).toInstant(),
                            valueText = valueText,
                            unit = unit
                        )
                    )
                },
                enabled = !running,
                modifier = Modifier.testTag("lab-result-save")
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        dateTime = LocalDateTime.of(date, dateTime.toLocalTime())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
            is24Hour = is24Hour
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateTime = LocalDateTime.of(
                        dateTime.toLocalDate(),
                        LocalTime.of(pickerState.hour, pickerState.minute)
                    )
                    showTimePicker = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            text = { TimePicker(state = pickerState) }
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.testTag("lab-result-delete-confirm"),
            title = { Text(stringResource(R.string.lab_results_delete_title)) },
            text = { Text(stringResource(R.string.lab_results_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(existing)
                    },
                    modifier = Modifier.testTag("lab-result-delete-confirm-button")
                ) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun operationMessage(operation: LabResultOperationState): String? = when (operation) {
    is LabResultOperationState.Invalid -> stringResource(
        when (operation.issue) {
            LabResultDraftIssue.VALUE_MISSING -> R.string.lab_results_error_value_missing
            LabResultDraftIssue.VALUE_NOT_A_NUMBER -> R.string.lab_results_error_value_not_number
            LabResultDraftIssue.VALUE_NOT_POSITIVE -> R.string.lab_results_error_value_not_positive
            LabResultDraftIssue.VALUE_TOO_LARGE -> R.string.lab_results_error_value_too_large
            LabResultDraftIssue.MEASURED_IN_FUTURE -> R.string.lab_results_error_future
        }
    )
    LabResultOperationState.Stale -> stringResource(R.string.lab_results_error_stale)
    LabResultOperationState.StorageFailure -> stringResource(R.string.lab_results_error_storage)
    else -> null
}

