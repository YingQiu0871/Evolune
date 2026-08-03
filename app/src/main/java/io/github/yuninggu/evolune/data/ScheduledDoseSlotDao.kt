package io.github.yuninggu.evolune.data

import androidx.room.Dao
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
}
