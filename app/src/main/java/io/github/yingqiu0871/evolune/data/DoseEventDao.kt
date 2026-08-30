package io.github.yingqiu0871.evolune.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 用药事件 DAO
 */
@Dao
interface DoseEventDao {
    @Query("SELECT * FROM dose_events ORDER BY occurredAtEpochMillis DESC, id ASC")
    fun observeAllForRepository(): Flow<List<DoseEventEntity>>

    @Query("SELECT * FROM dose_events WHERE id = :id")
    suspend fun getEventById(id: UUID): DoseEventEntity?

    @Query(
        """
        SELECT * FROM dose_events
        WHERE occurredAtEpochMillis >= :startInclusive
            AND occurredAtEpochMillis < :endExclusive
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """
    )
    suspend fun getEventsByOccurredAtRange(
        startInclusive: Long,
        endExclusive: Long
    ): List<DoseEventEntity>

    @Query(
        """
        SELECT * FROM dose_events
        WHERE occurredAtEpochMillis >= :startInclusive
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """
    )
    suspend fun getEventsAfterOccurredAt(startInclusive: Long): List<DoseEventEntity>

    @Query(
        """
        SELECT * FROM dose_events
        ORDER BY occurredAtEpochMillis DESC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getRecentEventsByOccurredAt(limit: Int): List<DoseEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventIfAbsent(event: DoseEventEntity): Long

    @Query(
        """
        UPDATE dose_events SET
            route = :route,
            timeH = :timeH,
            doseMG = :doseMG,
            ester = :ester,
            extras = :extras,
            occurredAtEpochMillis = :occurredAtEpochMillis,
            zoneId = :zoneId,
            localDate = :localDate,
            slotId = :slotId,
            source = :source,
            status = :status,
            revision = :revision
        WHERE id = :id AND revision = :expectedRevision
        """
    )
    suspend fun updateEventIfRevisionMatches(
        id: UUID,
        route: String,
        timeH: Double,
        doseMG: Double,
        ester: String,
        extras: Map<String, Double>,
        occurredAtEpochMillis: Long,
        zoneId: String?,
        localDate: String?,
        slotId: UUID?,
        source: String,
        status: String,
        revision: Long,
        expectedRevision: Long
    ): Int

    @Query("DELETE FROM dose_events WHERE id = :id")
    suspend fun deleteEventIfPresent(id: UUID): Int

    @Query("DELETE FROM dose_events WHERE id = :id AND revision = :expectedRevision")
    suspend fun deleteEventIfRevisionMatches(id: UUID, expectedRevision: Long): Int

    @Query("SELECT * FROM dose_events ORDER BY occurredAtEpochMillis DESC, id ASC")
    suspend fun getAllEventsForLatestDose(): List<DoseEventEntity>

    @Query("DELETE FROM dose_events")
    suspend fun deleteAllEventsIfPresent(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEventsForRestore(events: List<DoseEventEntity>)

    @Query("SELECT * FROM dose_events ORDER BY occurredAtEpochMillis DESC, id ASC")
    suspend fun getAllEventsForRestore(): List<DoseEventEntity>

    /**
     * 获取所有用药事件（按时间排序）
     */
    @Query("SELECT * FROM dose_events ORDER BY timeH DESC")
    fun getAllEvents(): Flow<List<DoseEventEntity>>

    /**
     * 获取指定时间范围内的用药事件
     */
    @Query("SELECT * FROM dose_events WHERE timeH >= :startTimeH AND timeH <= :endTimeH ORDER BY timeH ASC")
    suspend fun getEventsByTimeRange(startTimeH: Double, endTimeH: Double): List<DoseEventEntity>

    /**
     * 获取最近N条用药记录
     */
    @Query("SELECT * FROM dose_events ORDER BY timeH DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<DoseEventEntity>

    /**
     * 获取指定时间之后的所有记录
     */
    @Query("SELECT * FROM dose_events WHERE timeH >= :startTimeH ORDER BY timeH ASC")
    suspend fun getEventsAfter(startTimeH: Double): List<DoseEventEntity>

    /**
     * 插入或更新用药事件
     */
    @Upsert
    suspend fun upsertEvent(event: DoseEventEntity)

    /**
     * 删除用药事件
     */
    @Query("DELETE FROM dose_events WHERE id = :id")
    suspend fun deleteEvent(id: UUID)

    /**
     * 删除所有用药事件
     */
    @Query("DELETE FROM dose_events")
    suspend fun deleteAllEvents()
}
