package io.github.yingqiu0871.evolune.data.lab

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LabResultDao {
    @Query("SELECT * FROM lab_results ORDER BY measuredAtEpochMillis DESC, id ASC")
    fun observeAll(): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_results WHERE id = :id")
    suspend fun getById(id: String): LabResultEntity?

    /** Returns the row id, or -1 when a row with the same id already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: LabResultEntity): Long

    @Query(
        """
        UPDATE lab_results
        SET measuredAtEpochMillis = :measuredAtEpochMillis,
            value = :value,
            unit = :unit,
            revision = :revision
        WHERE id = :id AND revision = :expectedRevision
        """
    )
    suspend fun updateIfRevisionMatches(
        id: String,
        measuredAtEpochMillis: Long,
        value: Double,
        unit: String,
        revision: Long,
        expectedRevision: Long
    ): Int

    @Query("DELETE FROM lab_results WHERE id = :id AND revision = :expectedRevision")
    suspend fun deleteIfRevisionMatches(id: String, expectedRevision: Long): Int
}
