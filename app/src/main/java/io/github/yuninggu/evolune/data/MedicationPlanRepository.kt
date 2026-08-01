package io.github.yuninggu.evolune.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 用药方案数据仓库
 */
class MedicationPlanRepository(private val dao: MedicationPlanDao) {

    /**
     * 获取所有用药方案
     */
    fun getAllPlans(): Flow<List<MedicationPlan>> {
        return dao.getAllPlans().map { entities ->
            entities.map { it.toMedicationPlan() }
        }
    }

    /**
     * 获取所有启用的用药方案
     */
    fun getEnabledPlans(): Flow<List<MedicationPlan>> {
        return dao.getEnabledPlans().map { entities ->
            entities.map { it.toMedicationPlan() }
        }
    }

    /**
     * 根据ID获取用药方案
     */
    suspend fun getPlanById(id: UUID): MedicationPlan? {
        return dao.getPlanById(id)?.toMedicationPlan()
    }

    /**
     * 插入或更新用药方案
     */
    suspend fun upsertPlan(plan: MedicationPlan) {
        val entity = MedicationPlanEntity.fromMedicationPlan(plan)
        dao.upsertPlan(entity)
    }

    /**
     * 删除用药方案
     */
    suspend fun deletePlan(id: UUID) {
        dao.deletePlan(id)
    }

    /**
     * 启用/禁用用药方案
     */
    suspend fun updatePlanEnabled(id: UUID, isEnabled: Boolean) {
        dao.updatePlanEnabled(id, isEnabled)
    }

    /**
     * 删除所有用药方案
     */
    suspend fun deleteAllPlans() {
        dao.deleteAllPlans()
    }
}
