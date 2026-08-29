package io.github.yingqiu0871.evolune.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.UUID

@Dao
interface ScheduledDoseSlotDao {
    @Query(
        """
        SELECT * FROM scheduled_dose_slots
        WHERE planId = :planId
        ORDER BY position ASC
        """
    )
    suspend fun getSlotsForPlan(planId: UUID): List<ScheduledDoseSlotEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSlotsChecked(slots: List<ScheduledDoseSlotEntity>): List<Long>

    @Query("DELETE FROM scheduled_dose_slots WHERE planId = :planId")
    suspend fun deleteSlotsForPlan(planId: UUID): Int

    @Query("SELECT COUNT(*) FROM scheduled_dose_slots WHERE planId = :planId")
    suspend fun countSlotsForPlan(planId: UUID): Int

    @Query("SELECT COUNT(*) FROM scheduled_dose_slots")
    suspend fun countAllSlots(): Int

    @Query("DELETE FROM scheduled_dose_slots")
    suspend fun deleteAllSlotsIfPresent(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSlotsForRestore(slots: List<ScheduledDoseSlotEntity>)

    @Query("SELECT * FROM scheduled_dose_slots ORDER BY planId ASC, position ASC, id ASC")
    suspend fun getAllSlotsForRestore(): List<ScheduledDoseSlotEntity>
}
