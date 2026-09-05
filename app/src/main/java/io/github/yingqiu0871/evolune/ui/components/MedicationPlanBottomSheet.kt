package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.application.MedicationPlanDraft
import io.github.yingqiu0871.evolune.application.MedicationPlanEditSession
import io.github.yingqiu0871.evolune.application.MedicationPlanEditorInput
import io.github.yingqiu0871.evolune.application.MedicationPlanInputResult
import io.github.yingqiu0871.evolune.application.selectedAntiAndrogen
import io.github.yingqiu0871.evolune.application.selectedSublingualTier
import io.github.yingqiu0871.evolune.application.toMedicationPlanDraft
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.pk.*
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.ui.utils.getRouteDisplayName
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 添加或编辑用药方案的底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationPlanBottomSheet(
    showBottomSheet: Boolean,
    onDismiss: () -> Unit,
    onSave: (MedicationPlanDraft) -> Unit,
    onDelete: ((UUID) -> Unit)? = null,
    session: MedicationPlanEditSession? = null,
    is24Hour: Boolean = true,
    operationInProgress: Boolean = false,
    submissionErrorMessage: String? = null
) {
    val planToEdit = session?.existingPlan

    // 表单状态
    var name by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.name ?: "")
    }

    var selectedRoute by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.route ?: Route.INJECTION)
    }

    var selectedEster by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.ester ?: Ester.EV)
    }

    var selectedAntiAndrogen by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.selectedAntiAndrogen() ?: AntiAndrogen.CPA)
    }

    var scheduleType by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.scheduleType ?: ScheduleType.DAILY)
    }

    var doseMGText by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.doseMG?.toString() ?: "")
    }

    var doseTimes by remember(planToEdit, showBottomSheet) {
        mutableStateOf(
            planToEdit?.slots
                ?.map { EditableDoseTime(it.id, it.localTime, it.position) }
                ?.sortedChronologically()
                ?: listOf(EditableDoseTime(null, LocalTime.of(9, 0), 0))
        )
    }

    var daysOfWeek by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.daysOfWeek ?: emptySet())
    }

    var intervalDays by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.intervalDays?.toString() ?: "1")
    }

    // 舌下吸收等级
    var sublingualTier by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.selectedSublingualTier() ?: SublingualTier.STANDARD)
    }

    var hasInputError by remember(session, showBottomSheet) { mutableStateOf(false) }

    var showTimePicker by remember { mutableStateOf(false) }
    var timeIndexToEdit by remember { mutableIntStateOf(0) }

    // 根据给药途径过滤可用的酯类
    val availableEsters = remember(selectedRoute) {
        getAvailableEstersForRoute(selectedRoute)
    }

    // 如果当前选择的酯类不在可用列表中，自动切换到第一个
    LaunchedEffect(selectedRoute, availableEsters) {
        if (selectedEster !in availableEsters) {
            selectedEster = availableEsters.firstOrNull() ?: Ester.E2
        }
    }

    if (showBottomSheet) {
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        BackHandler(
            enabled = !imeVisible,
            onBack = { if (!operationInProgress) onDismiss() }
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("plan-editor-surface"),
            color = MaterialTheme.colorScheme.background
        ) {
            // IME 跟随完全交给框架（见 MedicationRecordBottomSheet 同款修复）：
            // windowInsetsPadding(safeDrawing) 缩小可视区，焦点字段由框架
            // BringIntoView 一次性带入视口；删除手写 snapshotFlow + animateScrollTo，
            // 消除二次缓动“回弹”。
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 24.dp)
                    .verticalScroll(scrollState)
                    .testTag("plan-editor-scroll")
            ) {
                // 标题
                Text(
                    text = if (planToEdit != null) stringResource(R.string.plan_sheet_edit_title) else stringResource(R.string.plan_sheet_add_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // 方案名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.plan_sheet_name_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan-name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 给药途径选择
                RouteSelectionSection(
                    selectedRoute = selectedRoute,
                    onRouteSelected = { selectedRoute = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 药物类型选择（雌激素）/ 抗雄药物类型选择（抗雄途径）
                when (selectedRoute) {
                    Route.ANTIANDROGEN -> AntiAndrogenSelectionSection(
                        selectedAntiAndrogen = selectedAntiAndrogen,
                        onAntiAndrogenSelected = { selectedAntiAndrogen = it }
                    )
                    else -> EsterSelectionSection(
                        selectedEster = selectedEster,
                        availableEsters = availableEsters,
                        onEsterSelected = { selectedEster = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 剂量输入
                OutlinedTextField(
                    value = doseMGText,
                    onValueChange = { doseMGText = it },
                    label = { Text(stringResource(R.string.plan_sheet_dose_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan-dose"),
                    singleLine = true,
                    trailingIcon = {
                        Text(
                            stringResource(R.string.unit_mg),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 舌下吸收等级（仅舌下途径显示）
                AnimatedVisibility(visible = selectedRoute == Route.SUBLINGUAL) {
                    Column {
                        SublingualTierSelector(
                            selectedTier = sublingualTier,
                            onTierSelected = { sublingualTier = it }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(0.dp))

                // 给药周期类型选择
                ScheduleTypeSection(
                    selectedType = scheduleType,
                    onTypeSelected = { scheduleType = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 根据周期类型显示不同的配置
                when (scheduleType) {
                    ScheduleType.DAILY -> {
                        // 每天：只需要选择时间
                    }
                    ScheduleType.WEEKLY -> {
                        // 每周：选择星期几
                        DaysOfWeekSection(
                            selectedDays = daysOfWeek,
                            onDayToggled = { day ->
                                daysOfWeek = if (daysOfWeek.contains(day)) {
                                    daysOfWeek - day
                                } else {
                                    daysOfWeek + day
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    ScheduleType.CUSTOM -> {
                        // 自定义：输入间隔天数
                        OutlinedTextField(
                            value = intervalDays,
                            onValueChange = { intervalDays = it },
                            label = { Text(stringResource(R.string.plan_sheet_interval_days_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // 时间选择
                TimeOfDaySection(
                    times = doseTimes.map { it.localTime },
                    onAddTime = {
                        doseTimes = (doseTimes + EditableDoseTime(
                            slotId = null,
                            localTime = LocalTime.of(9, 0),
                            stableOrder = doseTimes.size
                        )).sortedChronologically()
                    },
                    onRemoveTime = { index ->
                        if (doseTimes.size > 1) {
                            doseTimes = doseTimes.filterIndexed { i, _ -> i != index }
                        }
                    },
                    onEditTime = { index ->
                        timeIndexToEdit = index
                        showTimePicker = true
                    },
                    is24Hour = is24Hour
                )

                Spacer(modifier = Modifier.height(24.dp))

                val errorMessage = if (hasInputError) {
                    stringResource(R.string.plan_error_invalid_input)
                } else {
                    submissionErrorMessage
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("plan-error")
                    )
                }

                MedicationEditorActionRow(
                    onDelete = if (planToEdit != null && onDelete != null) {
                        { onDelete(planToEdit.id) }
                    } else {
                        null
                    },
                    onCancel = onDismiss,
                    onSave = onSaveAction@{
                            val currentSession = session ?: return@onSaveAction
                            val input = MedicationPlanEditorInput(
                                name = name,
                                route = selectedRoute,
                                ester = selectedEster,
                                selectedAntiAndrogen = selectedAntiAndrogen,
                                doseMGText = doseMGText,
                                scheduleType = scheduleType,
                                times = doseTimes.map { it.localTime },
                                daysOfWeek = daysOfWeek,
                                intervalDaysText = intervalDays,
                                isEnabled = planToEdit?.isEnabled ?: true,
                                sublingualTier = sublingualTier,
                                slotIds = doseTimes.map { it.slotId }
                            )
                            when (val result = input.toMedicationPlanDraft(currentSession)) {
                                is MedicationPlanInputResult.Success -> {
                                    hasInputError = false
                                    onSave(result.draft)
                                }
                                is MedicationPlanInputResult.InvalidInput -> {
                                    hasInputError = true
                                }
                            }
                    },
                    actionsEnabled = !operationInProgress,
                    saveEnabled = !operationInProgress,
                    deleteTag = "plan-delete",
                    cancelTag = "plan-cancel",
                    saveTag = "plan-save"
                )
            }
        }
    }

    // 时间选择器
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = doseTimes.getOrNull(timeIndexToEdit)?.localTime?.hour ?: 9,
            initialMinute = doseTimes.getOrNull(timeIndexToEdit)?.localTime?.minute ?: 0,
            is24Hour = is24Hour
        )

        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                doseTimes = doseTimes.mapIndexed { index, entry ->
                    if (index == timeIndexToEdit) entry.copy(localTime = newTime) else entry
                }.sortedChronologically()
                showTimePicker = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

private data class EditableDoseTime(
    val slotId: UUID?,
    val localTime: LocalTime,
    val stableOrder: Int
)

private fun List<EditableDoseTime>.sortedChronologically(): List<EditableDoseTime> =
    sortedWith(compareBy<EditableDoseTime> { it.localTime }.thenBy { it.stableOrder })

/**
 * 给药途径选择组件
 */
@Composable
private fun RouteSelectionSection(
    selectedRoute: Route,
    onRouteSelected: (Route) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_route_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val routes = Route.values().filter { it != Route.PATCH_REMOVE && it != Route.PATCH_APPLY }
        MedicationOptionGrid(
            options = routes,
            selectedOption = selectedRoute,
            onOptionSelected = onRouteSelected,
            optionLabel = { getRouteDisplayName(it) },
            optionTag = { "plan-route-${it.name.lowercase()}" },
            compactColumns = 2,
            expandedColumns = 3,
            itemHeight = 64.dp
        )
    }
}

/**
 * 药物类型选择组件
 */
@Composable
private fun EsterSelectionSection(
    selectedEster: Ester,
    availableEsters: List<Ester>,
    onEsterSelected: (Ester) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_ester_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        MedicationOptionGrid(
            options = availableEsters,
            selectedOption = selectedEster,
            onOptionSelected = onEsterSelected,
            optionLabel = { it.name },
            optionTag = { "plan-ester-${it.name.lowercase()}" },
            compactColumns = 3,
            expandedColumns = 5
        )
    }
}

/**
 * 给药周期类型选择组件
 */
@Composable
private fun ScheduleTypeSection(
    selectedType: ScheduleType,
    onTypeSelected: (ScheduleType) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_schedule_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val types = ScheduleType.values()
        MedicationOptionGrid(
            options = types.toList(),
            selectedOption = selectedType,
            onOptionSelected = onTypeSelected,
            optionLabel = { type ->
                when (type) {
                    ScheduleType.DAILY -> stringResource(R.string.plan_sheet_schedule_daily)
                    ScheduleType.WEEKLY -> stringResource(R.string.plan_sheet_schedule_weekly)
                    ScheduleType.CUSTOM -> stringResource(R.string.plan_sheet_schedule_custom)
                }
            },
            optionTag = { "plan-schedule-${it.name.lowercase()}" },
            compactColumns = 3
        )
    }
}

/**
 * 星期选择组件
 */
@Composable
private fun DaysOfWeekSection(
    selectedDays: Set<DayOfWeek>,
    onDayToggled: (DayOfWeek) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_weekday_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val days = listOf(
                DayOfWeek.MONDAY to stringResource(R.string.weekday_mon_short),
                DayOfWeek.TUESDAY to stringResource(R.string.weekday_tue_short),
                DayOfWeek.WEDNESDAY to stringResource(R.string.weekday_wed_short),
                DayOfWeek.THURSDAY to stringResource(R.string.weekday_thu_short),
                DayOfWeek.FRIDAY to stringResource(R.string.weekday_fri_short),
                DayOfWeek.SATURDAY to stringResource(R.string.weekday_sat_short),
                DayOfWeek.SUNDAY to stringResource(R.string.weekday_sun_short)
            )

            days.forEach { (day, label) ->
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = { onDayToggled(day) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 时间选择组件
 */
@Composable
private fun TimeOfDaySection(
    times: List<LocalTime>,
    onAddTime: () -> Unit,
    onRemoveTime: (Int) -> Unit,
    onEditTime: (Int) -> Unit,
    is24Hour: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.plan_sheet_time_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onAddTime) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.plan_sheet_time_add)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        times.forEachIndexed { index, time ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedCard(
                    onClick = { onEditTime(index) },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = time.format(
                                DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "hh:mm a")
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                if (times.size > 1) {
                    IconButton(onClick = { onRemoveTime(index) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.plan_sheet_time_remove)
                        )
                    }
                }
            }

            if (index < times.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 时间选择器对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        text = { content() }
    )
}

/**
 * 舌下吸收等级选择器
 */
@Composable
private fun SublingualTierSelector(
    selectedTier: SublingualTier,
    onTierSelected: (SublingualTier) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_sublingual_tier_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val tiers = SublingualTier.values().toList()
        MedicationOptionGrid(
            options = tiers,
            selectedOption = selectedTier,
            onOptionSelected = onTierSelected,
            optionLabel = {
                "${getSublingualTierName(it)}\n${getSublingualTierDescription(it)}"
            },
            optionTag = { "plan-sublingual-tier-${it.name.lowercase()}" },
            compactColumns = 2,
            expandedColumns = 4,
            itemHeight = 72.dp
        )
    }
}

/**
 * 获取舌下吸收等级名称
 */
@Composable
private fun getSublingualTierName(tier: SublingualTier): String {
    return when (tier) {
        SublingualTier.QUICK -> stringResource(R.string.sublingual_tier_quick)
        SublingualTier.CASUAL -> stringResource(R.string.sublingual_tier_casual)
        SublingualTier.STANDARD -> stringResource(R.string.sublingual_tier_standard)
        SublingualTier.STRICT -> stringResource(R.string.sublingual_tier_strict)
    }
}

/**
 * 获取舌下吸收等级描述
 */
@Composable
private fun getSublingualTierDescription(tier: SublingualTier): String {
    return when (tier) {
        SublingualTier.QUICK -> stringResource(R.string.sublingual_tier_quick_desc)
        SublingualTier.CASUAL -> stringResource(R.string.sublingual_tier_casual_desc)
        SublingualTier.STANDARD -> stringResource(R.string.sublingual_tier_standard_desc)
        SublingualTier.STRICT -> stringResource(R.string.sublingual_tier_strict_desc)
    }
}

@Composable
private fun getEsterDisplayName(ester: Ester): String {
    return when (ester) {
        Ester.E2 -> stringResource(R.string.ester_e2)
        Ester.EB -> stringResource(R.string.ester_eb)
        Ester.EV -> stringResource(R.string.ester_ev)
        Ester.EC -> stringResource(R.string.ester_ec)
        Ester.EN -> stringResource(R.string.ester_en)
    }
}

/**
 * 根据给药途径获取可用的酯类列表
 */
private fun getAvailableEstersForRoute(route: Route): List<Ester> {
    return when (route) {
        Route.INJECTION -> listOf(Ester.E2, Ester.EB, Ester.EV, Ester.EC, Ester.EN)
        Route.ORAL -> listOf(Ester.E2, Ester.EV)
        Route.SUBLINGUAL -> listOf(Ester.E2, Ester.EV)
        Route.GEL -> listOf(Ester.E2)
        Route.PATCH_APPLY -> listOf(Ester.E2)
        Route.PATCH_REMOVE -> listOf(Ester.E2)
        Route.ANTIANDROGEN -> listOf(Ester.E2) // 抗雄药物使用E2作为占位符
    }
}

/**
 * 抗雄药物类型选择组件
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AntiAndrogenSelectionSection(
    selectedAntiAndrogen: AntiAndrogen,
    onAntiAndrogenSelected: (AntiAndrogen) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_antiandrogen_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val antiAndrogens = AntiAndrogen.values()
        MedicationOptionGrid(
            options = antiAndrogens.toList(),
            selectedOption = selectedAntiAndrogen,
            onOptionSelected = onAntiAndrogenSelected,
            optionLabel = { getAntiAndrogenDisplayName(it) },
            optionTag = { "plan-antiandrogen-${it.name.lowercase()}" },
            compactColumns = 2,
            expandedColumns = 4
        )
    }
}

/**
 * 预览
 */
@Preview(showBackground = true)
@Composable
private fun MedicationPlanBottomSheetPreview() {
    EvoluneTheme {
        // 预览占位
        Box(modifier = Modifier.fillMaxSize()) {
            Text("用药方案底部弹窗预览")
        }
    }
}
