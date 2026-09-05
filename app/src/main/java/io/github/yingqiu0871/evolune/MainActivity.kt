package io.github.yingqiu0871.evolune

import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import io.github.yingqiu0871.evolune.backup.RestoreRecoveryResult
import io.github.yingqiu0871.evolune.backup.BackupRestoreCoordinator
import io.github.yingqiu0871.evolune.backup.BackupRestoreViewModel
import io.github.yingqiu0871.evolune.backup.BackupRestoreViewModelFactory
import io.github.yingqiu0871.evolune.backup.EvoluneBackupCodec
import io.github.yingqiu0871.evolune.backup.FileRestoreJournalStore
import io.github.yingqiu0871.evolune.backup.PostRestoreCoordinator
import io.github.yingqiu0871.evolune.backup.RestorePersistenceSnapshotSource
import io.github.yingqiu0871.evolune.backup.RestoreTransaction
import io.github.yingqiu0871.evolune.backup.cloud.google.GoogleAuthorizationGateway
import io.github.yingqiu0871.evolune.backup.cloud.google.GoogleDriveBackupProvider
import io.github.yingqiu0871.evolune.backup.cloud.google.HttpUrlConnectionDriveRemoteGateway
import io.github.yingqiu0871.evolune.data.recoverInterruptedRestoreAtStartup
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.healthconnect.AndroidHealthConnectWeightProvider
import io.github.yingqiu0871.evolune.navigation.AppNavigation
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import io.github.yingqiu0871.evolune.reminder.ReminderManager
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.ui.theme.usesDarkColors
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModelFactory
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanViewModel
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanViewModelFactory
import io.github.yingqiu0871.evolune.viewmodel.OnboardingViewModel
import io.github.yingqiu0871.evolune.viewmodel.OnboardingViewModelFactory
import io.github.yingqiu0871.evolune.viewmodel.SettingsViewModel
import io.github.yingqiu0871.evolune.viewmodel.SettingsViewModelFactory
import io.github.yingqiu0871.evolune.widget.WidgetUpdateReason
import io.github.yingqiu0871.evolune.widget.requestEvoluneWidgetUpdate
import io.github.yingqiu0871.evolune.wear.WearDataLayer
import io.github.yingqiu0871.evolune.wear.WearAppDataLayer
import io.github.yingqiu0871.evolune.wear.WearAppProducerIdentityStore
import io.github.yingqiu0871.evolune.wear.WearAppSnapshotBuilder
import io.github.yingqiu0871.evolune.wear.WearAppSnapshotRevisionStore
import kotlinx.coroutines.flow.first

internal fun initialRouteForIntent(action: String?): String? = when (action) {
    "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
    "android.intent.action.VIEW_PERMISSION_USAGE" -> "disclosures"
    else -> null
}

class MainActivity : ComponentActivity() {
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onStart() {
        super.onStart()
        if (::settingsViewModel.isInitialized) {
            settingsViewModel.onForeground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Capture the pre-v1.4 data boundary before Room/DataStore startup can
        // create new files on a genuinely fresh installation.
        val isExistingInstallation =
            applicationContext.getDatabasePath("evolune_database").exists() ||
                applicationContext.preferencesDataStoreFile("settings").exists()

        // 初始化设置数据存储
        val settingsDataStore = SettingsDataStore(applicationContext)
        val recovery = recoverInterruptedRestoreAtStartup(
            context = applicationContext,
            settingsStore = settingsDataStore
        )
        if (recovery is RestoreRecoveryResult.Failure) {
            Log.e("EvoluneRestore", "Startup restore recovery required", recovery.error.cause)
            finish()
            return
        }

        val productionRepositoryProvider =
            ProductionRepositoryProvider.get(applicationContext)
        val roomRestorePersistence = productionRepositoryProvider.createRestorePersistence(
            settingsStore = settingsDataStore,
            atomicSettingsStore = settingsDataStore
        )
        val googleAuthorizationGateway = GoogleAuthorizationGateway(applicationContext)
        val googleDriveProvider = GoogleDriveBackupProvider(
            authorization = googleAuthorizationGateway,
            remote = HttpUrlConnectionDriveRemoteGateway()
        )
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val reminderManager = ReminderManager(applicationContext)
        val postRestoreCoordinator = PostRestoreCoordinator(
            effects = listOf(
                {
                    val plans = productionRepositoryProvider.medicationPlans.observeAll().first()
                    reminderManager.rescheduleDomainReminders(plans)
                },
                {
                    requestEvoluneWidgetUpdate(
                        applicationContext,
                        WidgetUpdateReason.MANUAL_APP_REFRESH
                    )
                }
            )
        )
        val backupRestoreCoordinator = BackupRestoreCoordinator(
            snapshotSource = RestorePersistenceSnapshotSource(roomRestorePersistence),
            codec = EvoluneBackupCodec(),
            authorization = googleAuthorizationGateway,
            provider = googleDriveProvider,
            restoreTransaction = RestoreTransaction(
                persistence = roomRestorePersistence,
                journalStore = FileRestoreJournalStore(applicationContext)
            ),
            postRestoreCoordinator = postRestoreCoordinator,
            producerAppVersionName = packageInfo.versionName.orEmpty(),
            producerAppVersionCode = packageInfo.longVersionCode.toInt()
        )
        val healthConnectWeightProvider = AndroidHealthConnectWeightProvider(applicationContext)
        settingsViewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(settingsDataStore, healthConnectWeightProvider)
        )[SettingsViewModel::class.java]
        val onboardingViewModel: OnboardingViewModel = ViewModelProvider(
            this,
            OnboardingViewModelFactory(
                store = OnboardingStateStore(
                    context = applicationContext,
                    isExistingInstallation = isExistingInstallation
                )
            )
        )[OnboardingViewModel::class.java]
        
        setContent {
            val settingsViewModel = this@MainActivity.settingsViewModel
            val backupRestoreViewModel: BackupRestoreViewModel = viewModel(
                factory = BackupRestoreViewModelFactory(backupRestoreCoordinator)
            )
            
            // 获取用户设置
            val userSettings by settingsViewModel.userSettings.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()
            val isDarkTheme = userSettings.themeMode.usesDarkColors(systemInDarkTheme)
            LaunchedEffect(userSettings.timeFormat) {
                requestEvoluneWidgetUpdate(
                    applicationContext,
                    WidgetUpdateReason.APPEARANCE_CHANGED
                )
            }
            
            // 应用主题
            EvoluneTheme(
                themeMode = userSettings.themeMode,
                colorTheme = userSettings.colorTheme
            ) {
                val windowBackgroundColor = MaterialTheme.colorScheme.background
                SideEffect {
                    val transparent = Color.Transparent.toArgb()
                    this@MainActivity.enableEdgeToEdge(
                        statusBarStyle = if (isDarkTheme) {
                            SystemBarStyle.dark(transparent)
                        } else {
                            SystemBarStyle.light(transparent, transparent)
                        },
                        navigationBarStyle = if (isDarkTheme) {
                            SystemBarStyle.dark(transparent)
                        } else {
                            SystemBarStyle.light(transparent, transparent)
                        }
                    )
                    window.setBackgroundDrawable(windowBackgroundColor.toArgb().toDrawable())
                }

                // 创建 HRTViewModel，观察 SettingsDataStore 的权威体重
                val hrtViewModel: HRTViewModel = viewModel(
                    factory = HRTViewModelFactory(
                        repository = productionRepositoryProvider.doseEvents,
                        medicationPlanRepository = productionRepositoryProvider.medicationPlans,
                        settingsDataStore = settingsDataStore
                    )
                )
                val doseEvents by hrtViewModel.events.collectAsState()
                val domainMedicationPlans by hrtViewModel.allPlans.collectAsState()
                val pkState by hrtViewModel.pkState.collectAsState()
                LaunchedEffect(doseEvents) {
                    requestEvoluneWidgetUpdate(
                        applicationContext,
                        WidgetUpdateReason.DOSE_EVENT_CHANGED
                    )
                }
                
                // 创建 MedicationPlanViewModel
                val medicationPlanViewModel: MedicationPlanViewModel = viewModel(
                    factory = MedicationPlanViewModelFactory(
                        productionRepositoryProvider.medicationPlans,
                        reminderManager
                    )
                )
                LaunchedEffect(domainMedicationPlans) {
                    requestEvoluneWidgetUpdate(
                        applicationContext,
                        WidgetUpdateReason.PLAN_CHANGED
                    )
                }
                LaunchedEffect(
                    domainMedicationPlans,
                    pkState.simulationResult,
                    pkState.currentConcentration
                ) {
                    val plans = runCatching {
                        productionRepositoryProvider.medicationPlans.observeAll().first()
                    }.getOrNull() ?: return@LaunchedEffect
                    WearDataLayer.syncDashboard(
                        applicationContext,
                        plans.filter { it.isEnabled }.take(2),
                        pkState.currentConcentration,
                        io.github.yingqiu0871.evolune.wear.sampleWearCurve(
                            pkState.simulationResult,
                            pkState.currentTimeH
                        )
                    )
                }
                LaunchedEffect(
                    domainMedicationPlans,
                    doseEvents,
                    pkState.currentConcentration,
                    pkState.concentrationCalculatedAt,
                    pkState.isSimulating,
                    pkState.error
                ) {
                    if (pkState.isSimulating) return@LaunchedEffect
                    io.github.yingqiu0871.evolune.wear.withReservedWearAppSnapshotRevision(
                        reserveRevision = {
                            WearAppSnapshotRevisionStore.reserve(applicationContext)
                        }
                    ) { snapshotRevision ->
                        WearAppDataLayer.publishSnapshot(
                            context = applicationContext,
                            snapshot = WearAppSnapshotBuilder.build(
                                plans = domainMedicationPlans,
                                events = doseEvents,
                                generatedAt = java.time.Instant.now(),
                                zoneId = java.time.ZoneId.systemDefault(),
                                snapshotRevision = snapshotRevision,
                                currentConcentration = pkState.currentConcentration,
                                concentrationCalculatedAt = pkState.concentrationCalculatedAt,
                                concentrationError = pkState.error != null,
                                producerIdentity = WearAppProducerIdentityStore.current(
                                    applicationContext
                                )
                            )
                        )
                    }
                }
                
                // 应用启动时重新设置所有提醒
                LaunchedEffect(medicationPlanViewModel) {
                    medicationPlanViewModel.rescheduleAllReminders()
                }
                
                // 使用导航
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        hrtViewModel = hrtViewModel,
                        settingsViewModel = settingsViewModel,
                        medicationPlanViewModel = medicationPlanViewModel,
                        backupRestoreViewModel = backupRestoreViewModel,
                        authorizationResultFromIntent = googleAuthorizationGateway::outcomeFromIntent,
                        onboardingViewModel = onboardingViewModel,
                        initialRoute = initialRouteForIntent(intent?.action)
                    )
                }
            }
        }
    }

}
