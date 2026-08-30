package io.github.yingqiu0871.evolune.core.dataapi

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

interface DoseEventRepository {
    /** Observes all events ordered by occurredAt descending. */
    fun observeAll(): Flow<List<DoseEvent>>

    suspend fun getById(id: UUID): DoseEvent?

    /** Returns events in the half-open interval [startInclusive, endExclusive). */
    suspend fun findOccurredBetween(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<DoseEvent>

    /**
     * Preserves the current 30-day and 20-event selection rules.
     * The existing order of each selection branch must not be unified.
     */
    suspend fun getEventsForPk(asOf: Instant): List<DoseEvent>

    suspend fun insert(event: DoseEvent): InsertResult

    suspend fun update(
        event: DoseEvent,
        expectedRevision: Long
    ): UpdateResult

    /** Physically deletes the event. */
    suspend fun delete(id: UUID): DeleteResult

    /** Atomically deletes only when the stored revision equals expectedRevision. */
    suspend fun deleteIfRevisionMatches(
        id: UUID,
        expectedRevision: Long
    ): ConditionalDeleteResult

    /** Physically deletes all events as a maintenance operation. */
    suspend fun deleteAll(): DeleteResult
}
