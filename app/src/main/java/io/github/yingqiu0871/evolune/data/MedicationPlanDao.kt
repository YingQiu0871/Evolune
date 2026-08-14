package io.github.yingqiu0871.evolune.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 用药方案 DAO
 */
@Dao
interface MedicationPlanDao {

    @Transaction
    @Query("SELECT * FROM medication_plans ORDER BY createdAt DESC, id ASC")
    fun observeAllPlanAggregates(): Flow<List<MedicationPlanAggregateEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM medication_plans
        WHERE isEnabled = 1
        ORDER BY createdAt DESC, id ASC
        """
    )
    fun observeEnabledPlanAggregates(): Flow<List<MedicationPlanAggregateEntity>>

    @Transaction
    @Query("SELECT * FROM medication_plans WHERE id = :id")
    suspend fun getPlanAggregateById(id: UUID): MedicationPlanAggregateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlanChecked(plan: MedicationPlanEntity): Long

    @Query(
        """
        UPDATE medication_plans SET
            name = :name,
            route = :route,
            ester = :ester,
            doseMG = :doseMG,
            scheduleType = :scheduleType,
            timeOfDay = :timeOfDay,
            daysOfWeek = :daysOfWeek,
            intervalDays = :intervalDays,
            isEnabled = :isEnabled,
            extras = :extras,
            createdAt = :createdAt
        WHERE id = :id
        """
    )
    suspend fun updatePlanChecked(
        id: UUID,
        name: String,
        route: String,
        ester: String,
        doseMG: Double,
        scheduleType: String,
        timeOfDay: List<String>,
        daysOfWeek: Set<Int>,
        intervalDays: Int,
        isEnabled: Boolean,
        extras: Map<String, Double>,
        createdAt: Long
    ): Int

    @Query("UPDATE medication_plans SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updatePlanEnabledChecked(id: UUID, isEnabled: Boolean): Int

    @Query("DELETE FROM medication_plans WHERE id = :id")
    suspend fun deletePlanIfPresent(id: UUID): Int

    @Query("DELETE FROM medication_plans")
    suspend fun deleteAllPlansIfPresent(): Int

    @Query("SELECT COUNT(*) FROM medication_plans")
    suspend fun countPlans(): Int

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
