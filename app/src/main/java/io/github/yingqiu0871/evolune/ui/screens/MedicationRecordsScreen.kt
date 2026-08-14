package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.pk.AntiAndrogen
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SublingualTier
import io.github.yingqiu0871.evolune.ui.components.MedicationRecordBottomSheet
import io.github.yingqiu0871.evolune.ui.components.MedicationRecordItem
import io.github.yingqiu0871.evolune.ui.components.PatchMode
import io.github.yingqiu0871.evolune.ui.components.RecordDefaults
import io.github.yingqiu0871.evolune.ui.components.getAntiAndrogenDisplayName
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.ui.utils.getRouteDisplayName
import io.github.yingqiu0871.evolune.ui.utils.getRouteIcon
import io.github.yingqiu0871.evolune.viewmodel.DoseEventOperationError
import io.github.yingqiu0871.evolune.viewmodel.DoseEventOperationState
import io.github.yingqiu0871.evolune.viewmodel.DoseEventUiEvent
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * 用药记录列表屏幕（带状态管理）
 * 
 * @param viewModel HRT ViewModel
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationRecordsScreen(
    viewModel: HRTViewModel,
    is24Hour: Boolean = true,
    showTopBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    val events by viewModel.events.collectAsState()
    val allPlans by viewModel.allPlans.collectAsState()
    val editSession by viewModel.editSession.collectAsState()

    MedicationRecordsScreenContent(
        events = events,
        allPlans = allPlans,
        onEventClick = viewModel::startEditSession,
        onAddClick = viewModel::startCreateSession,
        onQuickAddFromPlan = viewModel::quickAddFromPlan,
        is24Hour = is24Hour,
        showTopBar = showTopBar,
        modifier = modifier
    )
}

/**
 * 用药记录列表屏幕内容（无状态）
 * 
 * @param events 用药事件列表
 * @param onEventClick 点击事件回调
 * @param onAddClick 添加按钮点击回调
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MedicationRecordsScreenContent(
    events: List<DoseEvent>,
    allPlans: List<MedicationPlan>,
    onEventClick: (DoseEvent) -> Unit,
    onAddClick: () -> Unit,
    onQuickAddFromPlan: (MedicationPlan) -> Unit,
    is24Hour: Boolean = true,
    showTopBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    var fabMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = if (showTopBar) {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            )
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.records_title), style = MaterialTheme.typography.headlineMediumEmphasized) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = it },
                        containerSize = { 96.dp },
                        containerCornerRadius = { progress ->
                            lerp(28.dp, 48.dp, progress)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = if (fabMenuExpanded) {
                                stringResource(R.string.records_fab_close)
                            } else {
                                stringResource(R.string.records_fab_open)
                            }
                        )
                    }
                }
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        onAddClick()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(stringResource(R.string.records_manual_add))
                    }
                )

                if (allPlans.isEmpty()) {
                    FloatingActionButtonMenuItem(
                        onClick = {},
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null
                            )
                        },
                        text = {
                            Text(stringResource(R.string.records_no_plan))
                        }
                    )
                } else {
                    allPlans.forEach { plan ->
                        FloatingActionButtonMenuItem(
                            onClick = {
                                fabMenuExpanded = false
                                onQuickAddFromPlan(plan)
                            },
                            icon = {
                                Icon(
                                    imageVector = getRouteIcon(plan.route),
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.records_quick_add_format,
                                        getPlanMedicationDisplayName(plan),
                                        plan.doseMG,
                                        getRouteDisplayName(plan.route)
                                    )
                                )
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (events.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.records_no_records),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.records_add_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 用药记录列表（按时间倒序排列，最新的在前面）
            val sortedEvents = remember(events) {
                events.sortedByDescending { it.occurredAt }
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = sortedEvents,
                    key = { it.id }
                ) { event ->
                    MedicationRecordItem(
                        event = event,
                        is24Hour = is24Hour,
                        onClick = { onEventClick(event) }
                    )
                }
            }
        }
    }
}

private fun DoseEventOperationError.displayMessage(): String = when (this) {
    is DoseEventOperationError.InvalidInput -> "请检查记录输入"
    DoseEventOperationError.RepositoryInvalid -> "记录无法保存"
    DoseEventOperationError.Conflict -> "相同记录 ID 已存在不同内容"
    DoseEventOperationError.RevisionConflict -> "该记录已被其他操作修改"
    DoseEventOperationError.NotFound -> "该记录已不存在"
    DoseEventOperationError.StorageFailure -> "记录存储暂时不可用"
}

@Composable
private fun getPlanMedicationDisplayName(plan: MedicationPlan): String {
    return when (plan.route) {
        Route.ANTIANDROGEN -> {
            val aaType = plan.extras[ExtraKey.ANTI_ANDROGEN_TYPE]?.toInt()?.let {
                AntiAndrogen.values().getOrElse(it) { AntiAndrogen.CPA }
            } ?: AntiAndrogen.CPA
            getAntiAndrogenDisplayName(aaType)
        }
        else -> getEsterDisplayName(plan.ester)
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

private fun previewDoseEvent(
    route: Route,
    timeH: Double,
    doseMG: Double,
    ester: Ester,
    extras: Map<ExtraKey, Double> = emptyMap()
): DoseEvent {
    val occurredAt = Instant.ofEpochMilli(Math.round(timeH * 3_600_000.0))
    return DoseEvent(
        id = UUID.nameUUIDFromBytes(
            "preview:$route:$timeH:$doseMG:$ester".toByteArray(Charsets.UTF_8)
        ),
        route = route,
        occurredAt = occurredAt,
        zoneId = ZoneOffset.UTC,
        localDate = occurredAt.atZone(ZoneOffset.UTC).toLocalDate(),
        doseMG = doseMG,
        ester = ester,
        extras = extras,
        slotId = null,
        source = DoseEventSource.MANUAL,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )
}

// ============================================================================
// Previews
// ============================================================================

@Preview(name = "空列表", showBackground = true)
@Composable
private fun PreviewMedicationRecordsScreenEmpty() {
    EvoluneTheme {
        MedicationRecordsScreenContent(
            events = emptyList(),
            allPlans = emptyList(),
            onEventClick = {},
            onAddClick = {},
            onQuickAddFromPlan = {}
        )
    }
}

@Preview(name = "有记录列表", showBackground = true, showSystemUi = true,
    backgroundColor = 0xFF00E5FF
)
@Composable
private fun PreviewMedicationRecordsScreenWithData() {
    EvoluneTheme {
        val currentTime = System.currentTimeMillis() / 3600000.0
        val events = remember {
            listOf(
                previewDoseEvent(
                    route = Route.INJECTION,
                    timeH = currentTime - 168.0,
                    doseMG = 5.0,
                    ester = Ester.EV
                ),
                previewDoseEvent(
                    route = Route.ORAL,
                    timeH = currentTime - 24.0,
                    doseMG = 2.0,
                    ester = Ester.E2
                ),
                previewDoseEvent(
                    route = Route.ORAL,
                    timeH = currentTime - 12.0,
                    doseMG = 2.0,
                    ester = Ester.E2
                ),
                previewDoseEvent(
                    route = Route.SUBLINGUAL,
                    timeH = currentTime - 6.0,
                    doseMG = 1.0,
                    ester = Ester.E2
                ),
                previewDoseEvent(
                    route = Route.GEL,
                    timeH = currentTime - 2.0,
                    doseMG = 0.75,
                    ester = Ester.E2,
                    extras = mapOf(ExtraKey.AREA_CM2 to 750.0)
                ),
                previewDoseEvent(
                    route = Route.PATCH_APPLY,
                    timeH = currentTime - 72.0,
                    doseMG = 0.0,
                    ester = Ester.E2,
                    extras = mapOf(ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0)
                )
            )
        }

        val allPlans = remember {
            listOf(
                MedicationPlan(
                    id = UUID(0L, 101L),
                    name = "晚间口服",
                    route = Route.ORAL,
                    ester = Ester.E2,
                    doseMG = 2.0,
                    scheduleType = ScheduleType.DAILY,
                    slots = emptyList(),
                    daysOfWeek = emptySet(),
                    intervalDays = 1,
                    isEnabled = true,
                    extras = emptyMap(),
                    createdAt = Instant.EPOCH
                )
            )
        }

        MedicationRecordsScreenContent(
            events = events,
            allPlans = allPlans,
            onEventClick = {},
            onAddClick = {},
            onQuickAddFromPlan = {}
        )
    }
}

@Preview(name = "深色模式", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewMedicationRecordsScreenDark() {
    EvoluneTheme(themeMode = ThemeMode.DARK) {
        val currentTime = System.currentTimeMillis() / 3600000.0
        val events = remember {
            listOf(
                previewDoseEvent(
                    route = Route.INJECTION,
                    timeH = currentTime - 168.0,
                    doseMG = 5.0,
                    ester = Ester.EV
                ),
                previewDoseEvent(
                    route = Route.ORAL,
                    timeH = currentTime - 12.0,
                    doseMG = 2.0,
                    ester = Ester.E2
                ),
                previewDoseEvent(
                    route = Route.SUBLINGUAL,
                    timeH = currentTime - 2.0,
                    doseMG = 1.0,
                    ester = Ester.E2
                )
            )
        }

        MedicationRecordsScreenContent(
            events = events,
            allPlans = emptyList(),
            onEventClick = {},
            onAddClick = {},
            onQuickAddFromPlan = {}
        )
    }
}

@Preview(name = "系统浅色", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "系统深色", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "系统浅色绿色壁纸", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO, wallpaper = Wallpapers.GREEN_DOMINATED_EXAMPLE)
@Composable
private fun PreviewMedicationRecordsScreenSystem() {
    EvoluneTheme() {
        val currentTime = System.currentTimeMillis() / 3600000.0
        val events = remember {
            listOf(
                previewDoseEvent(
                    route = Route.INJECTION,
                    timeH = currentTime - 168.0,
                    doseMG = 5.0,
                    ester = Ester.EV
                ),
                previewDoseEvent(
                    route = Route.ORAL,
                    timeH = currentTime - 12.0,
                    doseMG = 2.0,
                    ester = Ester.E2
                ),
                previewDoseEvent(
                    route = Route.SUBLINGUAL,
                    timeH = currentTime - 2.0,
                    doseMG = 1.0,
                    ester = Ester.E2
                )
            )
        }

        MedicationRecordsScreenContent(
            events = events,
            allPlans = emptyList(),
            onEventClick = {},
            onAddClick = {},
            onQuickAddFromPlan = {}
        )
    }
}

// Note: 完整功能预览需要真实的 ViewModel，在实际设备上运行查看效果
