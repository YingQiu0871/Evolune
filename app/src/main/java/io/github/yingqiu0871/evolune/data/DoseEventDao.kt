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

    /**
     * All-history channel (V17-C-01 §4.1): every authoritative row with
     * `occurredAtEpochMillis <= endInclusive`, without any lower bound.
     *
     * Used only by `HistoryReadService.readAllAvailable` for retrospective PK.
     * The inclusive bound is the frozen upper bound; callers must not add a second
     * selection rule here.
     */
    @Query(
        """
        SELECT * FROM dose_events
        WHERE occurredAtEpochMillis <= :endInclusive
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """
    )
    suspend fun getEventsUpToOccurredAt(endInclusive: Long): List<DoseEventEntity>

    /**
     * Returns the authoritative rows whose **persisted** `localDate` falls inside the
     * inclusive range `[startInclusive, endInclusive]`.
     *
     * `localDate` is persisted as an ISO-8601 `yyyy-MM-dd` string, so lexicographic
     * comparison is chronological. Rows with `localDate IS NULL` (legacy migrated rows)
     * are deliberately excluded: their display day can only be derived from the instant
     * in the display zone, which is the responsibility of the instant channel.
     */
    @Query(
        """
        SELECT * FROM dose_events
        WHERE localDate IS NOT NULL
            AND localDate >= :startInclusive
            AND localDate <= :endInclusive
        ORDER BY localDate ASC, occurredAtEpochMillis ASC, id ASC
        """
    )
    suspend fun getEventsByLocalDateRange(
        startInclusive: String,
        endInclusive: String
    ): List<DoseEventEntity>

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
     * 删除用药事件
     */
    @Query("DELETE FROM dose_events WHERE id = :id")
    suspend fun deleteEvent(id: UUID)
}
