package io.github.yuninggu.evolune.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 用药方案 DAO
 */
@Dao
interface MedicationPlanDao {

    /**
     * 获取所有用药方案
     */
    @Query("SELECT * FROM medication_plans ORDER BY createdAt DESC")
    fun getAllPlans(): Flow<List<MedicationPlanEntity>>

    /**
     * 获取所有启用的用药方案
     */
    @Query("SELECT * FROM medication_plans WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getEnabledPlans(): Flow<List<MedicationPlanEntity>>

    /**
     * 根据ID获取用药方案
     */
    @Query("SELECT * FROM medication_plans WHERE id = :id")
    suspend fun getPlanById(id: UUID): MedicationPlanEntity?

    /**
     * 插入或更新用药方案
     */
    @Upsert
    suspend fun upsertPlan(plan: MedicationPlanEntity)

    /**
     * 删除用药方案
     */
    @Query("DELETE FROM medication_plans WHERE id = :id")
    suspend fun deletePlan(id: UUID)

    /**
     * 启用/禁用用药方案
     */
    @Query("UPDATE medication_plans SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updatePlanEnabled(id: UUID, isEnabled: Boolean)

    /**
     * 删除所有用药方案
     */
    @Query("DELETE FROM medication_plans")
    suspend fun deleteAllPlans()
}
