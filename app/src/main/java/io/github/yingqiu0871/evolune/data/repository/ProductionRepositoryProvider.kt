package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.AtomicSettingsStore
import io.github.yingqiu0871.evolune.data.RoomRestorePersistence
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.lab.LabDatabase

class ProductionRepositoryProvider internal constructor(
    private val database: AppDatabase
) {
    val doseEvents: DoseEventRepository = RoomDoseEventRepository(database)
    val medicationPlans: MedicationPlanRepository = RoomMedicationPlanRepository(database)

    internal fun createRestorePersistence(
        labDatabase: LabDatabase,
        settingsStore: SettingsStore,
        atomicSettingsStore: AtomicSettingsStore
    ): RoomRestorePersistence = RoomRestorePersistence(
        database = database,
        labDatabase = labDatabase,
        settingsStore = settingsStore,
        atomicSettingsStore = atomicSettingsStore
    )

    companion object {
        @Volatile
        private var instance: ProductionRepositoryProvider? = null

        fun get(context: Context): ProductionRepositoryProvider =
            instance ?: synchronized(this) {
                instance ?: ProductionRepositoryProvider(
                    AppDatabase.getDatabase(context.applicationContext)
                ).also { instance = it }
            }
    }
}
