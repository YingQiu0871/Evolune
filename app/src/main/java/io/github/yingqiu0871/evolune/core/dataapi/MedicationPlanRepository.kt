package io.github.yingqiu0871.evolune.core.dataapi

import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface MedicationPlanRepository {
    fun observeAll(): Flow<List<MedicationPlan>>

    fun observeEnabled(): Flow<List<MedicationPlan>>

    suspend fun getById(id: UUID): MedicationPlan?

    /**
     * Atomically saves the plan and its complete slots aggregate.
     * The v2 schema has no slots storage, so no production implementation is provided yet.
     * Transaction implementation waits for the v3 schema.
     */
    suspend fun save(plan: MedicationPlan): PlanSaveResult

    suspend fun setEnabled(
        id: UUID,
        enabled: Boolean
    ): PlanUpdateResult

    suspend fun delete(id: UUID): DeleteResult

    /** Deletes all plans as a maintenance operation. */
    suspend fun deleteAll(): DeleteResult
}
