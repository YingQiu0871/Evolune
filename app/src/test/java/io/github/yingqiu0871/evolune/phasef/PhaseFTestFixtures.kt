package io.github.yingqiu0871.evolune.phasef

import io.github.yingqiu0871.evolune.application.WearAppConfirmationOperationJournal
import io.github.yingqiu0871.evolune.application.WearAppStoredConfirmation
import io.github.yingqiu0871.evolune.application.WearAppStoredUndo
import io.github.yingqiu0871.evolune.application.WearAppUndoOperationJournal
import io.github.yingqiu0871.evolune.application.WearAppUndoOperationStatus
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResult
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.core.dataapi.RecentRecordedDoseSelector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * V17 Phase F consistency suite — the single shared repository double.
 *
 * One stored event collection feeds every writer and every downstream reader; the range,
 * all-history and PK reads faithfully model the public `DoseEventRepository` contract
 * (mirroring `RoomDoseEventRepository` / `DoseEventDao` semantics) so the convergence
 * assertions observe real storage behavior instead of per-surface fixtures.
 */
internal class PhaseFRepository(
    initialEvents: List<DoseEvent> = emptyList()
) : DoseEventRepository {

    val rows = LinkedHashMap<UUID, DoseEvent>()

    init {
        initialEvents.forEach { rows[it.id] = it }
    }

    var writeFailure: Throwable? = null

    var insertCalls = 0
    var updateCalls = 0
    var deleteCalls = 0
    var conditionalDeleteCalls = 0
    var latestDoseDeleteCalls = 0
    var deleteAllCalls = 0
    var rangeReadCalls = 0
    var allHistoryReadCalls = 0
    var getByIdCalls = 0
    var pkReadCalls = 0

    val writeCalls: Int
        get() = insertCalls + updateCalls + deleteCalls + conditionalDeleteCalls +
            latestDoseDeleteCalls + deleteAllCalls

    private fun guardWrite() {
        writeFailure?.let { throw it }
    }

    override fun observeAll(): Flow<List<DoseEvent>> = flowOf(
        rows.values.sortedWith(compareByDescending<DoseEvent> { it.occurredAt }.thenBy { it.id.toString() })
    )

    override suspend fun getById(id: UUID): DoseEvent? {
        getByIdCalls += 1
        return rows[id]
    }

    override suspend fun findOccurredBetween(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<DoseEvent> {
        rangeReadCalls += 1
        return rows.values
            .filter { !it.occurredAt.isBefore(startInclusive) && it.occurredAt.isBefore(endExclusive) }
            .sortedWith(compareBy({ it.occurredAt }, { it.id.toString() }))
    }

    override suspend fun findRecordedLocalDateBetween(
        startInclusive: java.time.LocalDate,
        endInclusive: java.time.LocalDate
    ): List<DoseEvent> = rows.values
        .filter { event ->
            val date = event.localDate ?: return@filter false
            !date.isBefore(startInclusive) && !date.isAfter(endInclusive)
        }
        .sortedWith(compareBy({ it.localDate }, { it.occurredAt }, { it.id.toString() }))

    override suspend fun findAllOccurredUpTo(endInclusive: Instant): List<DoseEvent> {
        allHistoryReadCalls += 1
        return rows.values
            .filter { !it.occurredAt.isAfter(endInclusive) }
            .sortedWith(compareBy({ it.occurredAt }, { it.id.toString() }))
    }

    override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> {
        pkReadCalls += 1
        val windowStart = asOf.minus(30, ChronoUnit.DAYS)
        val recentEvents = rows.values
            .filter { !it.occurredAt.isBefore(windowStart) }
            .sortedWith(compareBy({ it.occurredAt }, { it.id.toString() }))
        val doseEventCount = recentEvents.count { it.route != Route.PATCH_REMOVE }
        return if (doseEventCount < PK_MINIMUM_DOSE_EVENTS) {
            rows.values
                .sortedWith(compareByDescending<DoseEvent> { it.occurredAt }.thenBy { it.id.toString() })
                .take(PK_MINIMUM_DOSE_EVENTS)
        } else {
            recentEvents
        }
    }

    override suspend fun insert(event: DoseEvent): InsertResult {
        guardWrite()
        insertCalls += 1
        if (event.revision != INITIAL_REVISION) return InsertResult.Invalid
        val existing = rows[event.id]
        return when {
            existing == null -> {
                rows[event.id] = event
                InsertResult.Inserted
            }

            existing == event -> InsertResult.Idempotent
            else -> InsertResult.Conflict
        }
    }

    override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult {
        guardWrite()
        updateCalls += 1
        if (expectedRevision < INITIAL_REVISION) return UpdateResult.Invalid
        val existing = rows[event.id] ?: return UpdateResult.NotFound
        if (existing.revision != expectedRevision) return UpdateResult.RevisionConflict
        val next = event.copy(revision = expectedRevision + 1)
        if (existing.copy(revision = INITIAL_REVISION) == next.copy(revision = INITIAL_REVISION)) {
            return UpdateResult.NoChange
        }
        rows[event.id] = next
        return UpdateResult.Updated
    }

    override suspend fun delete(id: UUID): DeleteResult {
        guardWrite()
        deleteCalls += 1
        return if (rows.remove(id) != null) DeleteResult.Deleted else DeleteResult.NotFound
    }

    override suspend fun deleteIfRevisionMatches(
        id: UUID,
        expectedRevision: Long
    ): ConditionalDeleteResult {
        guardWrite()
        conditionalDeleteCalls += 1
        if (expectedRevision < INITIAL_REVISION) return ConditionalDeleteResult.Invalid
        val existing = rows[id] ?: return ConditionalDeleteResult.NotFound
        if (existing.revision != expectedRevision) {
            return ConditionalDeleteResult.RevisionConflict
        }
        rows.remove(id)
        return ConditionalDeleteResult.Deleted
    }

    override suspend fun deleteLatestRecordedIfRevisionMatches(
        eventId: UUID,
        eventRevision: Long
    ): LatestDoseDeleteResult {
        guardWrite()
        latestDoseDeleteCalls += 1
        if (eventRevision < INITIAL_REVISION) return LatestDoseDeleteResult.Invalid
        val events = rows.values.toList()
        val target = events.firstOrNull { it.id == eventId }
            ?: return LatestDoseDeleteResult.EventNotFound
        if (target.revision != eventRevision) return LatestDoseDeleteResult.EventChanged
        val recent = RecentRecordedDoseSelector.select(events)
        if (recent?.id != eventId || recent.revision != eventRevision) {
            return LatestDoseDeleteResult.NotLatest
        }
        rows.remove(eventId)
        return LatestDoseDeleteResult.Deleted
    }

    override suspend fun deleteAll(): DeleteResult {
        guardWrite()
        deleteAllCalls += 1
        val hadRows = rows.isNotEmpty()
        rows.clear()
        return if (hadRows) DeleteResult.Deleted else DeleteResult.NotFound
    }

    private companion object {
        const val INITIAL_REVISION = 1L
        const val PK_MINIMUM_DOSE_EVENTS = 20
    }
}

/** Shared in-memory journals for the real Wear handlers (test infrastructure only). */
internal class PhaseFConfirmationJournal : WearAppConfirmationOperationJournal {
    private val records = linkedMapOf<UUID, WearAppStoredConfirmation>()

    override fun read(operationId: UUID): WearAppStoredConfirmation? = records[operationId]

    override fun begin(operationId: UUID, fingerprint: String): Boolean {
        val existing = records[operationId]
        if (existing != null) return existing.fingerprint == fingerprint
        records[operationId] = WearAppStoredConfirmation(fingerprint, null)
        return true
    }

    override fun saveResult(
        operationId: UUID,
        fingerprint: String,
        result: WearAppConfirmResult
    ): Boolean {
        val existing = records[operationId]
        if (existing != null && existing.fingerprint != fingerprint) return false
        records[operationId] = WearAppStoredConfirmation(fingerprint, result)
        return true
    }
}

internal class PhaseFUndoJournal : WearAppUndoOperationJournal {
    private val records = linkedMapOf<UUID, WearAppStoredUndo>()

    override fun read(operationId: UUID): WearAppStoredUndo? = records[operationId]

    override fun begin(operationId: UUID, fingerprint: String): Boolean {
        val existing = records[operationId]
        if (existing != null) return existing.fingerprint == fingerprint
        records[operationId] = WearAppStoredUndo(
            fingerprint = fingerprint,
            status = WearAppUndoOperationStatus.PREPARED,
            result = null
        )
        return true
    }

    override fun markDeleteInProgress(operationId: UUID, fingerprint: String): Boolean {
        val existing = records[operationId] ?: return false
        if (existing.fingerprint != fingerprint) return false
        records[operationId] = existing.copy(status = WearAppUndoOperationStatus.DELETE_IN_PROGRESS)
        return true
    }

    override fun saveResult(
        operationId: UUID,
        fingerprint: String,
        result: WearAppUndoResult
    ): Boolean {
        val existing = records[operationId]
        if (existing != null && existing.fingerprint != fingerprint) return false
        records[operationId] = WearAppStoredUndo(
            fingerprint = fingerprint,
            status = WearAppUndoOperationStatus.DELETE_IN_PROGRESS,
            result = result
        )
        return true
    }
}
