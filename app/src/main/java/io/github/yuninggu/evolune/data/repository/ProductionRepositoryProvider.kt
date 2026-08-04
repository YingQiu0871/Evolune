package io.github.yuninggu.evolune.data.repository

import android.content.Context
import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.data.AppDatabase

class ProductionRepositoryProvider internal constructor(
    database: AppDatabase
) {
    val doseEvents: DoseEventRepository = RoomDoseEventRepository(database)
    val medicationPlans: MedicationPlanRepository = RoomMedicationPlanRepository(database)

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
