package io.github.yingqiu0871.evolune

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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.navigation.AppNavigation
import io.github.yingqiu0871.evolune.reminder.ReminderManager
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.ui.theme.usesDarkColors
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModelFactory
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanViewModel
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanViewModelFactory
import io.github.yingqiu0871.evolune.viewmodel.SettingsViewModel
import io.github.yingqiu0871.evolune.viewmodel.SettingsViewModelFactory
import io.github.yingqiu0871.evolune.widget.updateAllEvoluneWidgets
import io.github.yingqiu0871.evolune.wear.WearDataLayer
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val productionRepositoryProvider =
            ProductionRepositoryProvider.get(applicationContext)
        
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
            val isDarkTheme = userSettings.themeMode.usesDarkColors(systemInDarkTheme)
            
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
                    window.setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(
                            windowBackgroundColor.toArgb()
                        )
                    )
                }

                // 创建 HRTViewModel，使用用户设置的体重
                val hrtViewModel: HRTViewModel = viewModel(
                    factory = HRTViewModelFactory(
                        repository = productionRepositoryProvider.doseEvents,
                        medicationPlanRepository = productionRepositoryProvider.medicationPlans,
                        bodyWeightKG = userSettings.bodyWeight
                    )
                )
                val doseEvents by hrtViewModel.events.collectAsState()
                val domainMedicationPlans by hrtViewModel.allPlans.collectAsState()
                val pkState by hrtViewModel.pkState.collectAsState()
                LaunchedEffect(doseEvents) {
                    updateAllEvoluneWidgets(applicationContext)
                }
                
                // 创建 MedicationPlanViewModel
                val reminderManager = ReminderManager(applicationContext)
                val medicationPlanViewModel: MedicationPlanViewModel = viewModel(
                    factory = MedicationPlanViewModelFactory(
                        productionRepositoryProvider.medicationPlans,
                        reminderManager
                    )
                )
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
                        medicationPlanViewModel = medicationPlanViewModel
                    )
                }
            }
        }
    }

}
