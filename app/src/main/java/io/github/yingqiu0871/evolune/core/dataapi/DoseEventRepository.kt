package io.github.yingqiu0871.evolune.core.dataapi

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
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
     * Returns the authoritative events whose **persisted** `localDate` lies inside the
     * inclusive range `[startInclusive, endInclusive]`, ordered by
     * `(localDate, occurredAt, id)`.
     *
     * The persisted `localDate` is written when the intake is recorded and is not
     * guaranteed to be derivable from `occurredAt`: a reminder confirmation stores the
     * *planned* day while `occurredAt` is the actual action instant, and a Wear
     * confirmation stores the day carried by the command. Querying by persisted date is
     * therefore the only way to guarantee that such a row is not missed, no matter how
     * far apart the two are.
     *
     * Rows with a null `localDate` are **never** returned; they must be read through
     * [findOccurredBetween].
     */
    suspend fun findRecordedLocalDateBetween(
        startInclusive: LocalDate,
        endInclusive: LocalDate
    ): List<DoseEvent>

    /**
     * Returns every authoritative row whose `occurredAt` is <= [endInclusive], ordered
     * by `occurredAt` then id. There is deliberately NO lower bound: this is the
     * all-history read channel used by HistoryReadService's retrospective entry
     * (V17-C-01 §4.1). It must not be replaced by [getEventsForPk] (30d/20 heuristic)
     * and must not be called from outside the History layer.
     */
    suspend fun findAllOccurredUpTo(endInclusive: Instant): List<DoseEvent>

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

    /** Atomically validates the authoritative Wear recent dose and deletes it if exact. */
    suspend fun deleteLatestRecordedIfRevisionMatches(
        eventId: UUID,
        eventRevision: Long
    ): LatestDoseDeleteResult

    /** Physically deletes all events as a maintenance operation. */
    suspend fun deleteAll(): DeleteResult
}
