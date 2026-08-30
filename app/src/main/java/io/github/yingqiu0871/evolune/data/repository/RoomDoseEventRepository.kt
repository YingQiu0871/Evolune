package io.github.yingqiu0871.evolune.data.repository

import androidx.room.withTransaction
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.mapper.MappingResult
import io.github.yingqiu0871.evolune.data.mapper.toV3DomainDoseEvent
import io.github.yingqiu0871.evolune.data.mapper.toV3Entity
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.DateTimeException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class RoomDoseEventRepository(
    private val database: AppDatabase
) : DoseEventRepository {
    private val dao = database.doseEventDao()

    override fun observeAll(): Flow<List<DoseEvent>> =
        dao.observeAllForRepository().map { entities ->
            entities.map { it.toV3DomainDoseEvent().orThrowCorrupt() }
        }.catch { error ->
            throw error.asRepositoryStorageException("observe dose events")
        }

    override suspend fun getById(id: UUID): DoseEvent? =
        runStorageOperation("get dose event") {
            dao.getEventById(id)?.toV3DomainDoseEvent()?.orThrowCorrupt()
        }

    override suspend fun findOccurredBetween(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<DoseEvent> = runStorageOperation("find dose events") {
        dao.getEventsByOccurredAtRange(
            startInclusive = startInclusive.requireEpochMillis(),
            endExclusive = endExclusive.requireEpochMillis()
        ).map { it.toV3DomainDoseEvent().orThrowCorrupt() }
    }

    override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> =
        runStorageOperation("get dose events for PK") {
            val windowStart = try {
                asOf.minus(30, ChronoUnit.DAYS)
            } catch (_: DateTimeException) {
                throw IllegalArgumentException("asOf is outside the persistence range")
            }
            val recentEvents = dao.getEventsAfterOccurredAt(
                windowStart.requireEpochMillis()
            ).map { it.toV3DomainDoseEvent().orThrowCorrupt() }
            val doseEventCount = recentEvents.count { it.route != Route.PATCH_REMOVE }
            if (doseEventCount < PK_MINIMUM_DOSE_EVENTS) {
                dao.getRecentEventsByOccurredAt(PK_MINIMUM_DOSE_EVENTS)
                    .map { it.toV3DomainDoseEvent().orThrowCorrupt() }
            } else {
                recentEvents
            }
        }

    override suspend fun insert(event: DoseEvent): InsertResult {
        if (event.revision != INITIAL_REVISION) {
            return InsertResult.Invalid
        }
        val entity = when (val result = event.toV3Entity()) {
            is MappingResult.Success -> result.value
            is MappingResult.Failure -> return InsertResult.Invalid
        }
        return runStorageOperation("insert dose event") {
            database.withTransaction {
                if (dao.insertEventIfAbsent(entity) != INSERT_CONFLICT) {
                    InsertResult.Inserted
                } else {
                    val existing = dao.getEventById(event.id)
                        ?.toV3DomainDoseEvent()
                        ?.orThrowCorrupt()
                        ?: throw RepositoryPersistenceException(
                            "resolve dose event insert conflict"
                        )
                    if (existing == event) {
                        InsertResult.Idempotent
                    } else {
                        InsertResult.Conflict
                    }
                }
            }
        }
    }

    override suspend fun update(
        event: DoseEvent,
        expectedRevision: Long
    ): UpdateResult {
        if (expectedRevision < INITIAL_REVISION || expectedRevision == Long.MAX_VALUE) {
            return UpdateResult.Invalid
        }
        val nextEvent = event.copy(revision = expectedRevision + 1)
        val nextEntity = when (val result = nextEvent.toV3Entity()) {
            is MappingResult.Success -> result.value
            is MappingResult.Failure -> return UpdateResult.Invalid
        }
        return runStorageOperation("update dose event") {
            database.withTransaction {
                val existingEntity = dao.getEventById(event.id)
                    ?: return@withTransaction UpdateResult.NotFound
                val existing = existingEntity.toV3DomainDoseEvent().orThrowCorrupt()
                if (existing.revision != expectedRevision) {
                    return@withTransaction UpdateResult.RevisionConflict
                }
                if (existing.sameContentIgnoringRevision(event)) {
                    return@withTransaction UpdateResult.NoChange
                }
                val updated = dao.updateEventIfRevisionMatches(
                    id = nextEntity.id,
                    route = nextEntity.route,
                    timeH = nextEntity.timeH,
                    doseMG = nextEntity.doseMG,
                    ester = nextEntity.ester,
                    extras = nextEntity.extras,
                    occurredAtEpochMillis = nextEntity.occurredAtEpochMillis,
                    zoneId = nextEntity.zoneId,
                    localDate = nextEntity.localDate,
                    slotId = nextEntity.slotId,
                    source = nextEntity.source,
                    status = nextEntity.status,
                    revision = nextEntity.revision,
                    expectedRevision = expectedRevision
                )
                if (updated == 1) {
                    UpdateResult.Updated
                } else if (dao.getEventById(event.id) == null) {
                    UpdateResult.NotFound
                } else {
                    UpdateResult.RevisionConflict
                }
            }
        }
    }

    override suspend fun delete(id: UUID): DeleteResult =
        runStorageOperation("delete dose event") {
            if (dao.deleteEventIfPresent(id) == 1) {
                DeleteResult.Deleted
            } else {
                DeleteResult.NotFound
            }
        }

    override suspend fun deleteIfRevisionMatches(
        id: UUID,
        expectedRevision: Long
    ): ConditionalDeleteResult {
        if (expectedRevision < INITIAL_REVISION) return ConditionalDeleteResult.Invalid
        return runStorageOperation("conditionally delete dose event") {
            if (dao.deleteEventIfRevisionMatches(id, expectedRevision) == 1) {
                ConditionalDeleteResult.Deleted
            } else if (dao.getEventById(id) == null) {
                ConditionalDeleteResult.NotFound
            } else {
                ConditionalDeleteResult.RevisionConflict
            }
        }
    }

    override suspend fun deleteAll(): DeleteResult =
        runStorageOperation("delete all dose events") {
            if (dao.deleteAllEventsIfPresent() > 0) {
                DeleteResult.Deleted
            } else {
                DeleteResult.NotFound
            }
        }

    private fun Instant.requireEpochMillis(): Long = try {
        toEpochMilli()
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("Instant is outside the persistence range")
    }

    private fun DoseEvent.sameContentIgnoringRevision(other: DoseEvent): Boolean =
        copy(revision = INITIAL_REVISION) == other.copy(revision = INITIAL_REVISION)

    private companion object {
        const val INITIAL_REVISION = 1L
        const val INSERT_CONFLICT = -1L
        const val PK_MINIMUM_DOSE_EVENTS = 20
    }
}
