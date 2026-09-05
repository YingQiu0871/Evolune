package io.github.yingqiu0871.evolune.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.ui.components.MedicationPlanBottomSheet
import io.github.yingqiu0871.evolune.ui.components.MedicationPlanCard
import io.github.yingqiu0871.evolune.ui.components.ContextualAuthorizationDialog
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanViewModel
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperation
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationError
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationState
import io.github.yingqiu0871.evolune.viewmodel.ReminderSideEffectResult
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * 用药方案屏幕（带状态管理）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationPlansScreen(
    viewModel: MedicationPlanViewModel,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    showTopBar: Boolean = true,
    notificationPermissionGrantedOverride: Boolean? = null
) {
    val context = LocalContext.current
    val plans by viewModel.plans.collectAsState()
    val editSession by viewModel.editSession.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val enabledPlanIdsInFlight by viewModel.enabledPlanIdsInFlight.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val globalOperationInProgress =
        (operationState as? MedicationPlanOperationState.Running)?.operation?.let { operation ->
            operation != MedicationPlanOperation.SET_ENABLED
        } == true
    val submissionFailure = operationState as? MedicationPlanOperationState.Failure
    val unknownErrorMessage = stringResource(R.string.common_unknown_error)
    val submissionErrorMessage = submissionFailure?.let { failure ->
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
                MedicationPlanOperation.RESCHEDULE -> unknownErrorMessage
            }
            MedicationPlanOperationError.UnexpectedFailure -> unknownErrorMessage
        }
    }
    var notificationPermissionGranted by remember(notificationPermissionGrantedOverride) {
        mutableStateOf(
            notificationPermissionGrantedOverride
                ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED)
        )
    }
    var promotedNotificationsEnabled by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
                NotificationManagerCompat.from(context).canPostPromotedNotifications()
        )
    }
    var pendingNotificationPlanId by remember { mutableStateOf<UUID?>(null) }
    var showNotificationPermissionGuidance by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }
    val promotedNotificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        promotedNotificationsEnabled =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
                NotificationManagerCompat.from(context).canPostPromotedNotifications()
    }

    fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionGranted
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openPromotedNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            promotedNotificationSettingsLauncher.launch(
                Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        }
    }

    val hasEnabledPlan = plans.any { it.isEnabled }

    LaunchedEffect(operationState, unknownErrorMessage) {
        when (val state = operationState) {
            is MedicationPlanOperationState.Success -> {
                if (state.result.operation in listOf(
                        MedicationPlanOperation.SAVE,
                        MedicationPlanOperation.DELETE
                    )
                ) {
                    viewModel.closeEditSession()
                }
                if (state.result.reminder == ReminderSideEffectResult.FAILED) {
                    snackbarHostState.showSnackbar(unknownErrorMessage)
                }
                viewModel.acknowledgeOperation()
            }
            is MedicationPlanOperationState.Failure -> {
                if (state.operation in listOf(
                        MedicationPlanOperation.SET_ENABLED,
                        MedicationPlanOperation.RESCHEDULE
                    )
                ) {
                    snackbarHostState.showSnackbar(unknownErrorMessage)
                    viewModel.acknowledgeOperation()
                }
            }
            MedicationPlanOperationState.Idle,
            is MedicationPlanOperationState.Running -> Unit
        }
    }

    MedicationPlansScreenContent(
        plans = plans,
        onPlanClick = { plan ->
            viewModel.startEditSession(plan)
        },
        onAddClick = {
            viewModel.startCreateSession()
        },
        onToggleEnabled = { id, isEnabled ->
            if (isEnabled && !notificationPermissionGranted) {
                pendingNotificationPlanId = id
            } else {
                if (isEnabled) {
                    requestNotificationPermissionIfNeeded()
                }
                viewModel.setPlanEnabled(id, isEnabled)
            }
        },
        showNotificationPermissionSetup = hasEnabledPlan && !notificationPermissionGranted,
        onNotificationPermissionSetup = {
            if (notificationPermissionGranted) {
                requestNotificationPermissionIfNeeded()
            } else {
                showNotificationPermissionGuidance = true
            }
        },
        showPromotedNotificationSetup =
            hasEnabledPlan &&
                notificationPermissionGranted &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                !promotedNotificationsEnabled,
        onPromotedNotificationSetup = ::openPromotedNotificationSettings,
        interactionsEnabled = !globalOperationInProgress,
        enabledPlanIdsInFlight = enabledPlanIdsInFlight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        showTopBar = showTopBar,
        modifier = modifier
    )

    ContextualAuthorizationDialog(
        visible = pendingNotificationPlanId != null,
        title = stringResource(R.string.contextual_notification_title),
        message = stringResource(R.string.contextual_notification_message),
        onContinue = {
            pendingNotificationPlanId?.let { id ->
                pendingNotificationPlanId = null
                requestNotificationPermissionIfNeeded()
                viewModel.setPlanEnabled(id, true)
            }
        },
        onNotNow = {
            pendingNotificationPlanId?.let { id ->
                pendingNotificationPlanId = null
                viewModel.setPlanEnabled(id, true)
            }
        }
    )
    ContextualAuthorizationDialog(
        visible = showNotificationPermissionGuidance,
        title = stringResource(R.string.contextual_notification_title),
        message = stringResource(R.string.contextual_notification_message),
        onContinue = {
            showNotificationPermissionGuidance = false
            requestNotificationPermissionIfNeeded()
        },
        onNotNow = { showNotificationPermissionGuidance = false }
    )
}

/**
 * 用药方案屏幕内容（无状态）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationPlansScreenContent(
    plans: List<MedicationPlan>,
    onPlanClick: (MedicationPlan) -> Unit,
    onAddClick: () -> Unit,
    onToggleEnabled: (UUID, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showNotificationPermissionSetup: Boolean = false,
    onNotificationPermissionSetup: () -> Unit = {},
    showPromotedNotificationSetup: Boolean = false,
    onPromotedNotificationSetup: () -> Unit = {},
    interactionsEnabled: Boolean = true,
    enabledPlanIdsInFlight: Set<UUID> = emptySet(),
    snackbarHost: @Composable () -> Unit = {},
    showTopBar: Boolean = true
) {
    Scaffold(
        contentWindowInsets = if (showTopBar) {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            )
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        snackbarHost = snackbarHost,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.headlineMediumEmphasized) },
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
            LargeFloatingActionButton(
                onClick = { if (interactionsEnabled) onAddClick() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.testTag("plan-add")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.plans_add_content_desc)
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showNotificationPermissionSetup) {
                item(key = "notification-permission") {
                    ReminderSetupCard(
                        title = stringResource(R.string.plans_notification_permission_title),
                        description = stringResource(R.string.plans_notification_permission_desc),
                        onClick = onNotificationPermissionSetup
                    )
                }
            }

            if (showPromotedNotificationSetup) {
                item(key = "promoted-notification-permission") {
                    ReminderSetupCard(
                        title = stringResource(R.string.plans_promoted_notification_title),
                        description = stringResource(R.string.plans_promoted_notification_desc),
                        onClick = onPromotedNotificationSetup
                    )
                }
            }

            if (plans.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillParentMaxHeight(0.7f)
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.plans_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(plans, key = { it.id }) { plan ->
                    MedicationPlanCard(
                        plan = plan,
                        onClick = { onPlanClick(plan) },
                        onToggleEnabled = { onToggleEnabled(plan.id, !plan.isEnabled) },
                        enabled = interactionsEnabled && plan.id !in enabledPlanIdsInFlight
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderSetupCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null
            )
        }
    }
}

/**
 * 预览
 */
@Preview(showBackground = true)
@Composable
private fun MedicationPlansScreenPreview() {
    EvoluneTheme {
        val samplePlans = listOf(
            previewPlan(
                id = UUID(0L, 11L),
                name = "EV注射",
                route = Route.INJECTION,
                ester = Ester.EV,
                doseMG = 10.0,
                scheduleType = ScheduleType.WEEKLY,
                times = listOf(LocalTime.of(9, 0)),
                daysOfWeek = setOf(DayOfWeek.MONDAY),
                isEnabled = true
            ),
            previewPlan(
                id = UUID(0L, 12L),
                name = "E2凝胶",
                route = Route.GEL,
                ester = Ester.E2,
                doseMG = 3.0,
                scheduleType = ScheduleType.DAILY,
                times = listOf(LocalTime.of(23, 0)),
                isEnabled = true
            ),
            previewPlan(
                id = UUID(0L, 13L),
                name = "口服EV",
                route = Route.ORAL,
                ester = Ester.EV,
                doseMG = 2.0,
                scheduleType = ScheduleType.DAILY,
                times = listOf(LocalTime.of(8, 0), LocalTime.of(23, 30)),
                isEnabled = false
            )
        )

        MedicationPlansScreenContent(
            plans = samplePlans,
            onPlanClick = {},
            onAddClick = {},
            onToggleEnabled = { _, _ -> }
        )
    }
}

private fun previewPlan(
    id: UUID,
    name: String,
    route: Route,
    ester: Ester,
    doseMG: Double,
    scheduleType: ScheduleType,
    times: List<LocalTime>,
    daysOfWeek: Set<DayOfWeek> = emptySet(),
    isEnabled: Boolean
): MedicationPlan = MedicationPlan(
    id = id,
    name = name,
    route = route,
    ester = ester,
    doseMG = doseMG,
    scheduleType = scheduleType,
    slots = times.mapIndexed { position, localTime ->
        ScheduledDoseSlot(
            id = UUID(2L, position.toLong()),
            planId = id,
            localTime = localTime,
            position = position
        )
    },
    daysOfWeek = daysOfWeek,
    intervalDays = 1,
    isEnabled = isEnabled,
    extras = emptyMap(),
    createdAt = Instant.EPOCH
)
