package io.github.yingqiu0871.evolune.navigation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.pk.AntiAndrogen
import io.github.yingqiu0871.evolune.pk.SublingualTier
import io.github.yingqiu0871.evolune.ui.components.EditorTransitionHost
import io.github.yingqiu0871.evolune.ui.components.MedicationPlanBottomSheet
import io.github.yingqiu0871.evolune.ui.components.MedicationRecordBottomSheet
import io.github.yingqiu0871.evolune.ui.components.PatchMode
import io.github.yingqiu0871.evolune.ui.components.RecordDefaults
import io.github.yingqiu0871.evolune.ui.motion.evolunePageEnterTransition
import io.github.yingqiu0871.evolune.ui.motion.evolunePageExitTransition
import io.github.yingqiu0871.evolune.ui.screens.HomeScreen
import io.github.yingqiu0871.evolune.ui.screens.MedicationPlansScreen
import io.github.yingqiu0871.evolune.ui.screens.MedicationRecordsScreen
import io.github.yingqiu0871.evolune.ui.screens.SettingsScreen
import io.github.yingqiu0871.evolune.viewmodel.DoseEventOperationError
import io.github.yingqiu0871.evolune.viewmodel.DoseEventOperationState
import io.github.yingqiu0871.evolune.viewmodel.DoseEventUiEvent
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import io.github.yingqiu0871.evolune.viewmodel.ImportResult
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperation
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationError
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationState
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanViewModel
import io.github.yingqiu0871.evolune.viewmodel.SettingsViewModel
import io.github.yingqiu0871.evolune.viewmodel.UpdateCheckResult

private const val NAV_CLICK_THROTTLE_MS = 200L
private const val NAV_SWIPE_THRESHOLD_DP = 60
private val NAVIGATION_RAIL_WIDTH = 80.dp
private val NAVIGATION_RAIL_ITEM_SPACING = 4.dp

/**
 * 应用主导航
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AppNavigation(
    hrtViewModel: HRTViewModel,
    settingsViewModel: SettingsViewModel,
    medicationPlanViewModel: MedicationPlanViewModel
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val updateCheckResult by settingsViewModel.updateCheckResult.collectAsState()
    val userSettings by settingsViewModel.userSettings.collectAsState()
    val importResult by hrtViewModel.importResult.collectAsState()
    val scope = rememberCoroutineScope()

    // 根据用户设置和设备语言区域计算是否使用24小时制
    val is24Hour = when (userSettings.timeFormat) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.HOUR_12 -> false
        TimeFormat.HOUR_24 -> true
    }

    @Suppress("DEPRECATION")
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
    }

    // 用于导出时暂存 JSON 内容，直到文件选择器返回 URI
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    // 剪贴板导出结果消息（用于触发 Snackbar）
    var clipboardExportMessage by remember { mutableStateOf<String?>(null) }

    // 预先获取剪贴板操作所需的字符串资源（避免在非 @Composable 上下文中调用 context.getString）
    val strClipboardEmpty = stringResource(R.string.import_clipboard_empty)
    val strCopiedToClipboard = stringResource(R.string.export_copied_to_clipboard)
    val strExportFilename = stringResource(R.string.export_filename)

    // 导入文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val content = context.contentResolver.openInputStream(uri)
                    ?.use { it.bufferedReader().readText() }
                    ?: return@launch
                hrtViewModel.importFromMahiroJson(content) { weight ->
                    settingsViewModel.updateBodyWeight(weight)
                }
            }
        }
    }

    // 导出文件选择器
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                pendingExportJson = null
            }
        } else {
            pendingExportJson = null
        }
    }

    // 从剪贴板导入
    fun importFromClipboard() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            hrtViewModel.reportClipboardImportError(strClipboardEmpty)
            return
        }
        hrtViewModel.importFromMahiroJson(text) { weight ->
            settingsViewModel.updateBodyWeight(weight)
        }
    }

    // 导出到剪贴板
    fun exportToClipboard() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        val json = hrtViewModel.exportToMahiroJson(userSettings.bodyWeight)
        val clip = ClipData.newPlainText("Evolune Export", json)
        clipboardManager.setPrimaryClip(clip)
        clipboardExportMessage = strCopiedToClipboard
    }

    // 应用启动时自动检查更新
    LaunchedEffect(Unit) {
        settingsViewModel.triggerAutoCheckOnStartup(versionName)
    }

    // 有新版本时显示更新弹窗
    if (updateCheckResult is UpdateCheckResult.UpdateAvailable) {
        val result = updateCheckResult as UpdateCheckResult.UpdateAvailable
        AlertDialog(
            onDismissRequest = { settingsViewModel.dismissUpdateCheckResult() },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(stringResource(R.string.update_available_content, result.tagName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uriHandler.openUri(result.releaseUrl)
                        settingsViewModel.dismissUpdateCheckResult()
                    }
                ) {
                    Text(stringResource(R.string.update_go_to_release))
                }
            },
            dismissButton = {
                TextButton(onClick = { settingsViewModel.dismissUpdateCheckResult() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Debug 版本时提示下载 Release
    if (updateCheckResult is UpdateCheckResult.DebugBuild) {
        val result = updateCheckResult as UpdateCheckResult.DebugBuild
        AlertDialog(
            onDismissRequest = { settingsViewModel.dismissUpdateCheckResult() },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(stringResource(R.string.update_debug_content, result.tagName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uriHandler.openUri(result.releaseUrl)
                        settingsViewModel.dismissUpdateCheckResult()
                    }
                ) {
                    Text(stringResource(R.string.update_go_to_release))
                }
            },
            dismissButton = {
                TextButton(onClick = { settingsViewModel.dismissUpdateCheckResult() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 折叠屏/宽屏自适应导航：compact 走底部导航栏，medium/expanded 走侧边导航栏。
    // 通过 calculateWindowSizeClass(activity) 取得真实窗口尺寸类，而非硬编码设备宽度。
    val activity = LocalActivity.current
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val useNavigationRail =
        windowSizeClass != null && windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val recordEditSession by hrtViewModel.editSession.collectAsState()
    val planEditSession by medicationPlanViewModel.editSession.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = Screen.entries.firstOrNull { it.route == currentRoute } ?: Screen.HOME
    val currentRouteState = rememberUpdatedState(currentRoute)

    // The top-level Scaffold stays intact beneath full-screen editor layers so its
    // bottom inset and navigation geometry cannot change during editor transitions.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
            bottomBar = {
                if (!useNavigationRail) {
                    BottomNavigationBar(navController = navController)
                }
            },
            topBar = {
                AppTopBar(
                    currentScreen = currentScreen,
                    alignWithNavigationRail = useNavigationRail,
                    onRefresh = hrtViewModel::runSimulation
                )
            }
        ) { innerPadding ->
        var swipeDelta by remember { mutableFloatStateOf(0f) }
        val swipeThresholdPx = with(LocalDensity.current) { NAV_SWIPE_THRESHOLD_DP.dp.toPx() }

        Row(
            modifier = Modifier
                .padding(innerPadding)
                // 根 Scaffold 是 Horizontal+Bottom 安全区的唯一所有者：
                // 消费掉这部分 inset，目的地内部的 Scaffold 取到的 safeDrawing
                // 只剩 Top，不会再次叠加底部导航栏高度（Settings 底部异常色块的根因）。
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
        ) {
            if (useNavigationRail) {
                NavigationRailBar(navController = navController)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .testTag("app-content")
                    .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipeDelta = 0f },
                        onDragCancel = { swipeDelta = 0f },
                        onDragEnd = {
                            if (abs(swipeDelta) > swipeThresholdPx) {
                                // swipeDelta > 0 means finger moved right -> go to previous tab
                                val direction = if (swipeDelta > 0) -1 else 1
                                val currentIndex = screenIndex(currentRouteState.value)
                                val targetIndex = currentIndex + direction
                                if (currentIndex != -1 && targetIndex in Screen.entries.indices) {
                                    navController.navigate(Screen.entries[targetIndex].route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    ) { _, dragAmount ->
                        swipeDelta += dragAmount
                    }
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.HOME.route,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            enterTransition = {
                evolunePageEnterTransition()
            },
            exitTransition = {
                evolunePageExitTransition()
            },
            popEnterTransition = {
                evolunePageEnterTransition()
            },
            popExitTransition = {
                evolunePageExitTransition()
            },
        ) {
            composable(Screen.HOME.route) {
                HomeScreen(
                    viewModel = hrtViewModel,
                    is24Hour = is24Hour,
                    showTopBar = false
                )
            }
            composable(Screen.RECORDS.route) {
                MedicationRecordsScreen(
                    viewModel = hrtViewModel,
                    is24Hour = is24Hour,
                    showTopBar = false
                )
            }
            composable(Screen.MEDICATION_PLANS.route) {
                MedicationPlansScreen(
                    viewModel = medicationPlanViewModel,
                    is24Hour = is24Hour,
                    showTopBar = false
                )
            }
            composable(Screen.SETTINGS.route) {
                SettingsScreen(
                    settings = userSettings,
                    onBodyWeightChange = settingsViewModel::updateBodyWeight,
                    onThemeModeChange = settingsViewModel::updateThemeMode,
                    onColorThemeChange = settingsViewModel::updateColorTheme,
                    onTimeFormatChange = settingsViewModel::updateTimeFormat,
                    onAutoCheckUpdatesChange = settingsViewModel::updateAutoCheckUpdates,
                    onCheckForUpdates = { settingsViewModel.checkForUpdates(versionName) },
                    updateCheckResult = updateCheckResult,
                    onImportClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    onImportFromClipboard = { importFromClipboard() },
                    onExportClick = {
                        pendingExportJson = hrtViewModel.exportToMahiroJson(userSettings.bodyWeight)
                        exportLauncher.launch(strExportFilename)
                    },
                    onExportToClipboard = { exportToClipboard() },
                    importResult = importResult,
                    onDismissImportResult = hrtViewModel::dismissImportResult,
                    clipboardExportMessage = clipboardExportMessage,
                    onClipboardExportMessageShown = { clipboardExportMessage = null },
                    showTopBar = false
                )
            }
        }
        } // Box
        } // Row
    }

    // ---- Fullscreen editor overlays (root level; cover bottom bar and system bar areas) ----
    val recordOperationState by hrtViewModel.operationState.collectAsState()
    val planOperationState by medicationPlanViewModel.operationState.collectAsState()
    var recordDefaults by remember { mutableStateOf<RecordDefaults?>(null) }

    LaunchedEffect(hrtViewModel) {
        hrtViewModel.uiEvents.collect { event ->
            when (event) {
                is DoseEventUiEvent.Saved -> {
                    if (event.created) {
                        recordDefaults = event.event.toRecordDefaults()
                    }
                    hrtViewModel.closeEditSession()
                }
                is DoseEventUiEvent.Deleted -> hrtViewModel.closeEditSession()
            }
            hrtViewModel.acknowledgeOperation()
        }
    }

    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }
    fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionGranted
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    EditorTransitionHost(
        session = recordEditSession,
        modifier = Modifier.fillMaxSize()
    ) { visibleSession, isActive ->
        MedicationRecordBottomSheet(
            showBottomSheet = true,
            onDismiss = {
                if (isActive) {
                    hrtViewModel.closeEditSession()
                    hrtViewModel.acknowledgeOperation()
                }
            },
            onSave = { input ->
                if (isActive) hrtViewModel.saveEvent(input)
            },
            onDelete = { id ->
                if (isActive) hrtViewModel.deleteEvent(id)
            },
            session = visibleSession,
            defaults = recordDefaults,
            is24Hour = is24Hour,
            isOperationRunning =
                !isActive || recordOperationState is DoseEventOperationState.Running,
            operationError = (recordOperationState as? DoseEventOperationState.Failure)
                ?.error
                ?.displayMessage()
        )
    }
    EditorTransitionHost(
        session = planEditSession,
        modifier = Modifier.fillMaxSize()
    ) { visibleSession, isActive ->
        val planSubmissionFailure = planOperationState as? MedicationPlanOperationState.Failure
        val unknownErrorText = stringResource(R.string.common_unknown_error)
        val planSubmissionErrorMessage = planSubmissionFailure?.let { failure ->
            when (failure.error) {
                is MedicationPlanOperationError.InvalidDraft ->
                    stringResource(R.string.plan_error_invalid_input)
                MedicationPlanOperationError.RepositoryInvalid ->
                    stringResource(R.string.plan_error_invalid_plan)
                MedicationPlanOperationError.NotFound ->
                    stringResource(R.string.plan_error_not_found)
                MedicationPlanOperationError.StorageFailure -> when (failure.operation) {
                    MedicationPlanOperation.SAVE -> stringResource(R.string.plan_error_save_failed)
                    MedicationPlanOperation.DELETE -> stringResource(R.string.plan_error_delete_failed)
                    MedicationPlanOperation.SET_ENABLED,
                    MedicationPlanOperation.RESCHEDULE -> unknownErrorText
                }
                MedicationPlanOperationError.UnexpectedFailure -> unknownErrorText
            }
        }
        MedicationPlanBottomSheet(
            showBottomSheet = true,
            onDismiss = {
                if (isActive) {
                    medicationPlanViewModel.closeEditSession()
                    medicationPlanViewModel.acknowledgeOperation()
                }
            },
            onSave = { draft ->
                if (isActive) {
                    if (draft.isEnabled) {
                        requestNotificationPermissionIfNeeded()
                    }
                    medicationPlanViewModel.saveDraft(draft)
                }
            },
            onDelete = { id ->
                if (isActive) medicationPlanViewModel.deletePlan(id)
            },
            session = visibleSession,
            is24Hour = is24Hour,
            operationInProgress =
                !isActive || planOperationState is MedicationPlanOperationState.Running,
            submissionErrorMessage = planSubmissionErrorMessage.takeIf {
                planSubmissionFailure?.operation in listOf(
                    MedicationPlanOperation.SAVE,
                    MedicationPlanOperation.DELETE
                )
            }
        )
        }
        }
}

private fun io.github.yingqiu0871.evolune.core.model.DoseEvent.toRecordDefaults(): RecordDefaults =
    RecordDefaults(
        route = route,
        ester = ester,
        doseMG = doseMG,
        patchMode = if (extras.containsKey(ExtraKey.RELEASE_RATE_UG_PER_DAY)) {
            PatchMode.RATE
        } else {
            PatchMode.DOSE
        },
        patchRateUgPerDay = extras[ExtraKey.RELEASE_RATE_UG_PER_DAY] ?: 0.0,
        sublingualTier = extras[ExtraKey.SUBLINGUAL_TIER]?.toInt()?.let { tier ->
            SublingualTier.values().getOrElse(tier) { SublingualTier.STANDARD }
        } ?: SublingualTier.STANDARD,
        antiAndrogen = extras[ExtraKey.ANTI_ANDROGEN_TYPE]?.toInt()?.let {
            AntiAndrogen.values().getOrElse(it) { AntiAndrogen.CPA }
        } ?: AntiAndrogen.CPA
    )

private fun DoseEventOperationError.displayMessage(): String = when (this) {
    is DoseEventOperationError.InvalidInput -> "请检查记录输入"
    DoseEventOperationError.RepositoryInvalid -> "记录无法保存"
    DoseEventOperationError.Conflict -> "相同记录 ID 已存在不同内容"
    DoseEventOperationError.RevisionConflict -> "该记录已被其他操作修改"
    DoseEventOperationError.NotFound -> "该记录已不存在"
    DoseEventOperationError.StorageFailure -> "记录存储暂时不可用"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppTopBar(
    currentScreen: Screen,
    alignWithNavigationRail: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("app-top-bar")
    ) {
        if (alignWithNavigationRail) {
            Spacer(modifier = Modifier.width(NAVIGATION_RAIL_WIDTH))
        }
        CenterAlignedTopAppBar(
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    text = when (currentScreen) {
                        Screen.HOME -> stringResource(R.string.nav_home)
                        Screen.RECORDS -> stringResource(R.string.records_title)
                        Screen.MEDICATION_PLANS -> stringResource(R.string.plans_title)
                        Screen.SETTINGS -> stringResource(R.string.settings_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("app-top-title")
                )
            },
            actions = {
                Box(modifier = Modifier.size(48.dp)) {
                    if (currentScreen == Screen.HOME) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.home_refresh)
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/**
 * 导航项（底部栏与侧边栏共用）
 */
@Composable
private fun rememberNavItems(): List<BottomNavItem> = listOf(
    BottomNavItem(
        screen = Screen.HOME,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        label = stringResource(R.string.nav_home)
    ),
    BottomNavItem(
        screen = Screen.RECORDS,
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List,
        label = stringResource(R.string.nav_records)
    ),
    BottomNavItem(
        screen = Screen.MEDICATION_PLANS,
        selectedIcon = Icons.Filled.MedicalServices,
        unselectedIcon = Icons.Outlined.MedicalServices,
        label = stringResource(R.string.nav_plans)
    ),
    BottomNavItem(
        screen = Screen.SETTINGS,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        label = stringResource(R.string.nav_settings)
    )
)

/**
 * 导航到目标 tab（底部栏与侧边栏共用同一套 popUpTo/saveState/restoreState 语义，
 * 保证 compact ↔ medium/expanded 切换时不会重建 NavController、不丢目的地状态）。
 */
private fun navigateToTab(navController: NavHostController, screen: Screen) {
    navController.navigate(screen.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 底部导航栏（compact 宽度）
 */
@Composable
private fun BottomNavigationBar(
    navController: NavHostController
) {
    var lastNavigateAt by remember { mutableLongStateOf(0L) }
    val items = rememberNavItems()

    NavigationBar(modifier = Modifier.testTag("navigation-bar")) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == item.screen.route
            } == true

            NavigationBarItem(
                modifier = Modifier.testTag("nav-bar-${item.screen.route}"),
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                selected = selected,
                onClick = {
                    if (selected) return@NavigationBarItem

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNavigateAt < NAV_CLICK_THROTTLE_MS) {
                        return@NavigationBarItem
                    }
                    lastNavigateAt = now

                    navigateToTab(navController, item.screen)
                }
            )
        }
    }
}

/**
 * 侧边导航栏（medium / expanded 宽度，如折叠屏展开态、平板、横屏）
 */
@Composable
private fun NavigationRailBar(
    navController: NavHostController
) {
    var lastNavigateAt by remember { mutableLongStateOf(0L) }
    val items = rememberNavItems()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(
        modifier = Modifier
            .width(NAVIGATION_RAIL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("navigation-rail"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.testTag("navigation-rail-items"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NAVIGATION_RAIL_ITEM_SPACING)
        ) {
            items.forEach { item ->
                val selected = currentDestination?.hierarchy?.any {
                    it.route == item.screen.route
                } == true

                NavigationRailItem(
                    modifier = Modifier
                        .width(NAVIGATION_RAIL_WIDTH)
                        .testTag("nav-rail-${item.screen.route}"),
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    },
                    label = { Text(item.label) },
                    selected = selected,
                    onClick = {
                        if (selected) return@NavigationRailItem

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNavigateAt < NAV_CLICK_THROTTLE_MS) {
                            return@NavigationRailItem
                        }
                        lastNavigateAt = now

                        navigateToTab(navController, item.screen)
                    }
                )
            }
        }
    }
}

/**
 * 底部导航项
 */
private data class BottomNavItem(
    val screen: Screen,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

private fun screenIndex(route: String?): Int {
    return Screen.entries.indexOfFirst { it.route == route }
}
