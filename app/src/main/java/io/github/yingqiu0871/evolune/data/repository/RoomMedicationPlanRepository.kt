package io.github.yingqiu0871.evolune.data.repository

import androidx.room.withTransaction
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanUpdateResult
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.MedicationPlanAggregateEntity
import io.github.yingqiu0871.evolune.data.MedicationPlanEntity
import io.github.yingqiu0871.evolune.data.mapper.MappingResult
import io.github.yingqiu0871.evolune.data.mapper.toDomainMedicationPlan
import io.github.yingqiu0871.evolune.data.mapper.toPersistenceAggregate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomMedicationPlanRepository(
    private val database: AppDatabase
) : MedicationPlanRepository {
    private val planDao = database.medicationPlanDao()
    private val slotDao = database.scheduledDoseSlotDao()

    override fun observeAll(): Flow<List<MedicationPlan>> =
        planDao.observeAllPlanAggregates().map { aggregates ->
            aggregates.map { it.toDomainOrThrow() }
        }.catch { error ->
            throw error.asRepositoryStorageException("observe medication plans")
        }

    override fun observeEnabled(): Flow<List<MedicationPlan>> =
        planDao.observeEnabledPlanAggregates().map { aggregates ->
            aggregates.map { it.toDomainOrThrow() }
        }.catch { error ->
            throw error.asRepositoryStorageException("observe enabled medication plans")
        }

    override suspend fun getById(id: UUID): MedicationPlan? =
        runStorageOperation("get medication plan") {
            planDao.getPlanAggregateById(id)?.toDomainOrThrow()
        }

    override suspend fun save(plan: MedicationPlan): PlanSaveResult {
        val canonicalPlan = plan.withChronologicalSlots()
        val persistence = when (val result = canonicalPlan.toPersistenceAggregate()) {
            is MappingResult.Success -> result.value
            is MappingResult.Failure -> return PlanSaveResult.Invalid
        }
        return runStorageOperation("save medication plan") {
            database.withTransaction {
                val existingAggregate = planDao.getPlanAggregateById(canonicalPlan.id)
                val existingPlan = existingAggregate?.toDomainOrThrow()
                if (existingPlan == canonicalPlan) {
                    return@withTransaction PlanSaveResult.NoChange
                }

                if (existingAggregate == null) {
                    if (planDao.insertPlanChecked(persistence.plan) == INSERT_CONFLICT) {
                        throw RepositoryPersistenceException("insert medication plan")
                    }
                } else {
                    requireSinglePlanUpdate(persistence.plan)
                }

                val expectedDeletedSlots = existingAggregate?.slots?.size ?: 0
                val deletedSlots = slotDao.deleteSlotsForPlan(canonicalPlan.id)
                if (deletedSlots != expectedDeletedSlots) {
                    throw RepositoryPersistenceException("replace medication plan slots")
                }
                val insertedSlots = if (persistence.slots.isEmpty()) {
                    emptyList()
                } else {
                    slotDao.insertSlotsChecked(persistence.slots)
                }
                if (
                    insertedSlots.size != persistence.slots.size ||
                    insertedSlots.any { it == INSERT_CONFLICT }
                ) {
                    throw RepositoryPersistenceException("insert medication plan slots")
                }

                val verified = planDao.getPlanAggregateById(canonicalPlan.id)
                    ?.toDomainOrThrow()
                    ?: throw RepositoryPersistenceException("verify medication plan save")
                if (verified != canonicalPlan) {
                    throw RepositoryPersistenceException("verify medication plan aggregate")
                }

                if (existingAggregate == null) {
                    PlanSaveResult.Created
                } else {
                    PlanSaveResult.Updated
                }
            }
        }
    }

    override suspend fun setEnabled(
        id: UUID,
        enabled: Boolean
    ): PlanUpdateResult = runStorageOperation("set medication plan enabled") {
        database.withTransaction {
            val existing = planDao.getPlanAggregateById(id)
                ?: return@withTransaction PlanUpdateResult.NotFound
            val existingPlan = existing.toDomainOrThrow()
            if (existingPlan.isEnabled == enabled) {
                return@withTransaction PlanUpdateResult.NoChange
            }
            if (planDao.updatePlanEnabledChecked(id, enabled) != 1) {
                return@withTransaction PlanUpdateResult.NotFound
            }
            val verified = planDao.getPlanAggregateById(id)
                ?.toDomainOrThrow()
                ?: throw RepositoryPersistenceException("verify medication plan enabled state")
            if (verified != existingPlan.copy(isEnabled = enabled)) {
                throw RepositoryPersistenceException("verify medication plan enabled update")
            }
            PlanUpdateResult.Updated
        }
    }

    override suspend fun delete(id: UUID): DeleteResult =
        runStorageOperation("delete medication plan") {
            database.withTransaction {
                planDao.getPlanAggregateById(id)?.toDomainOrThrow()
                    ?: return@withTransaction DeleteResult.NotFound
                if (planDao.deletePlanIfPresent(id) != 1) {
                    return@withTransaction DeleteResult.NotFound
                }
                if (slotDao.countSlotsForPlan(id) != 0) {
                    throw RepositoryPersistenceException("verify medication plan cascade delete")
                }
                DeleteResult.Deleted
            }
        }

    override suspend fun deleteAll(): DeleteResult =
        runStorageOperation("delete all medication plans") {
            database.withTransaction {
                val planCount = planDao.countPlans()
                if (planCount == 0) {
                    return@withTransaction DeleteResult.NotFound
                }
                if (planDao.deleteAllPlansIfPresent() != planCount) {
                    throw RepositoryPersistenceException("delete all medication plans")
                }
                if (slotDao.countAllSlots() != 0) {
                    throw RepositoryPersistenceException("verify all plan slots deleted")
                }
                DeleteResult.Deleted
            }
        }

    private suspend fun requireSinglePlanUpdate(plan: MedicationPlanEntity) {
        val updated = planDao.updatePlanChecked(
            id = plan.id,
            name = plan.name,
            route = plan.route,
            ester = plan.ester,
            doseMG = plan.doseMG,
            scheduleType = plan.scheduleType,
            timeOfDay = plan.timeOfDay,
            daysOfWeek = plan.daysOfWeek,
            intervalDays = plan.intervalDays,
            isEnabled = plan.isEnabled,
            extras = plan.extras,
            createdAt = plan.createdAt
        )
        if (updated != 1) {
            throw RepositoryPersistenceException("update medication plan")
        }
    }

    private fun MedicationPlanAggregateEntity.toDomainOrThrow(): MedicationPlan =
        toDomainMedicationPlan().orThrowCorrupt()

    private fun MedicationPlan.withChronologicalSlots(): MedicationPlan = copy(
        slots = slots
            .sortedWith(
                compareBy<ScheduledDoseSlot> { it.localTime }.thenBy { it.position }
            )
            .mapIndexed { position, slot -> slot.copy(position = position) }
    )

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
