package io.github.yuninggu.hrttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yuninggu.hrttracker.data.AppDatabase
import io.github.yuninggu.hrttracker.data.ThemeMode
import io.github.yuninggu.hrttracker.data.DoseEventRepository
import io.github.yuninggu.hrttracker.data.MedicationPlanRepository
import io.github.yuninggu.hrttracker.data.SettingsDataStore
import io.github.yuninggu.hrttracker.navigation.AppNavigation
import io.github.yuninggu.hrttracker.reminder.ReminderManager
import io.github.yuninggu.hrttracker.ui.theme.HRTTrackerTheme
import io.github.yuninggu.hrttracker.viewmodel.HRTViewModel
import io.github.yuninggu.hrttracker.viewmodel.HRTViewModelFactory
import io.github.yuninggu.hrttracker.viewmodel.MedicationPlanViewModel
import io.github.yuninggu.hrttracker.viewmodel.MedicationPlanViewModelFactory
import io.github.yuninggu.hrttracker.viewmodel.SettingsViewModel
import io.github.yuninggu.hrttracker.viewmodel.SettingsViewModelFactory
import io.github.yuninggu.hrttracker.widget.updateAllHRTTrackerWidgets
import io.github.yuninggu.hrttracker.wear.WearDataLayer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 初始化数据库和仓库
        val database = AppDatabase.getDatabase(applicationContext)
        val doseEventRepository = DoseEventRepository(database.doseEventDao())
        val medicationPlanRepository = MedicationPlanRepository(database.medicationPlanDao())
        
        // 初始化设置数据存储
        val settingsDataStore = SettingsDataStore(applicationContext)
        
        setContent {
            // 创建 SettingsViewModel
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(settingsDataStore)
            )
            
            // 获取用户设置
            val userSettings by settingsViewModel.userSettings.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()
            val isDarkTheme = when (userSettings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }

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
            }
            
            // 应用主题
            HRTTrackerTheme(
                themeMode = userSettings.themeMode,
                colorTheme = userSettings.colorTheme
            ) {
                // 创建 HRTViewModel，使用用户设置的体重
                val hrtViewModel: HRTViewModel = viewModel(
                    factory = HRTViewModelFactory(
                        repository = doseEventRepository,
                        medicationPlanRepository = medicationPlanRepository,
                        bodyWeightKG = userSettings.bodyWeight
                    )
                )
                val doseEvents by hrtViewModel.events.collectAsState()
                val pkState by hrtViewModel.pkState.collectAsState()
                LaunchedEffect(doseEvents) {
                    updateAllHRTTrackerWidgets(applicationContext)
                }
                
                // 创建 MedicationPlanViewModel
                val reminderManager = ReminderManager(applicationContext)
                val medicationPlanViewModel: MedicationPlanViewModel = viewModel(
                    factory = MedicationPlanViewModelFactory(medicationPlanRepository, reminderManager)
                )
                val medicationPlans by medicationPlanViewModel.plans.collectAsState()
                LaunchedEffect(
                    medicationPlans,
                    pkState.simulationResult,
                    pkState.currentConcentration
                ) {
                    WearDataLayer.syncDashboard(
                        applicationContext,
                        medicationPlans.filter { it.isEnabled }.take(2),
                        pkState.currentConcentration,
                        io.github.yuninggu.hrttracker.wear.sampleWearCurve(
                            pkState.simulationResult,
                            pkState.currentTimeH
                        )
                    )
                }
                
                // 应用启动时重新设置所有提醒
                LaunchedEffect(medicationPlanViewModel) {
                    medicationPlanViewModel.rescheduleAllReminders()
                }
                
                // 使用导航
                AppNavigation(
                    hrtViewModel = hrtViewModel,
                    settingsViewModel = settingsViewModel,
                    medicationPlanViewModel = medicationPlanViewModel
                )
            }
        }
    }

}
