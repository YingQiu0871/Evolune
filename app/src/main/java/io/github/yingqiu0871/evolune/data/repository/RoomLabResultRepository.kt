package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import androidx.room.withTransaction
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LabResultRepository
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.data.lab.LabDatabase
import io.github.yingqiu0871.evolune.data.lab.LabMappingResult
import io.github.yingqiu0871.evolune.data.lab.toDomain
import io.github.yingqiu0871.evolune.data.lab.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomLabResultRepository(
    private val database: LabDatabase
) : LabResultRepository {
    private val dao = database.labResultDao()

    override fun observeAll(): Flow<List<LabResult>> =
        dao.observeAll().map { rows ->
            rows.map { it.toDomain().orThrowCorrupt() }
        }.catch { error ->
            throw error.asRepositoryStorageException("observe lab results")
        }

    override suspend fun getById(id: UUID): LabResult? =
        runStorageOperation("get lab result") {
            dao.getById(id.toString())?.toDomain()?.orThrowCorrupt()
        }

    override suspend fun insert(result: LabResult): InsertResult {
        if (result.revision != INITIAL_REVISION) return InsertResult.Invalid
        val entity = when (val mapped = result.toEntity()) {
            is LabMappingResult.Success -> mapped.value
            is LabMappingResult.Failure -> return InsertResult.Invalid
        }
        return runStorageOperation("insert lab result") {
            database.withTransaction {
                if (dao.insertIfAbsent(entity) != INSERT_CONFLICT) {
                    InsertResult.Inserted
                } else {
                    val existing = dao.getById(entity.id)?.toDomain()?.orThrowCorrupt()
                        ?: throw RepositoryPersistenceException("resolve lab result insert conflict")
                    if (existing == result) InsertResult.Idempotent else InsertResult.Conflict
                }
            }
        }
    }

    override suspend fun update(result: LabResult, expectedRevision: Long): UpdateResult {
        if (expectedRevision < INITIAL_REVISION || expectedRevision == Long.MAX_VALUE) {
            return UpdateResult.Invalid
        }
        val next = when (val mapped = result.copy(revision = expectedRevision + 1).toEntity()) {
            is LabMappingResult.Success -> mapped.value
            is LabMappingResult.Failure -> return UpdateResult.Invalid
        }
        return runStorageOperation("update lab result") {
            database.withTransaction {
                val existing = dao.getById(next.id)?.toDomain()?.orThrowCorrupt()
                    ?: return@withTransaction UpdateResult.NotFound
                if (existing.revision != expectedRevision) {
                    return@withTransaction UpdateResult.RevisionConflict
                }
                if (existing.copy(revision = INITIAL_REVISION) == result.copy(revision = INITIAL_REVISION)) {
                    return@withTransaction UpdateResult.NoChange
                }
                val updated = dao.updateIfRevisionMatches(
                    id = next.id,
                    measuredAtEpochMillis = next.measuredAtEpochMillis,
                    value = next.value,
                    unit = next.unit,
                    revision = next.revision,
                    expectedRevision = expectedRevision
                )
                if (updated == 1) UpdateResult.Updated else UpdateResult.RevisionConflict
            }
        }
    }

    override suspend fun deleteIfRevisionMatches(
        id: UUID,
        expectedRevision: Long
    ): ConditionalDeleteResult {
        if (expectedRevision < INITIAL_REVISION) return ConditionalDeleteResult.Invalid
        return runStorageOperation("conditionally delete lab result") {
            database.withTransaction {
                if (dao.deleteIfRevisionMatches(id.toString(), expectedRevision) == 1) {
                    ConditionalDeleteResult.Deleted
                } else if (dao.getById(id.toString()) == null) {
                    ConditionalDeleteResult.NotFound
                } else {
                    ConditionalDeleteResult.RevisionConflict
                }
            }
        }
    }

    private fun <T> LabMappingResult<T>.orThrowCorrupt(): T = when (this) {
        is LabMappingResult.Success -> value
        is LabMappingResult.Failure -> throw CorruptLabResultException(error)
    }

    companion object {
        private const val INITIAL_REVISION = 1L
        private const val INSERT_CONFLICT = -1L

        @Volatile
        private var instance: RoomLabResultRepository? = null

        fun get(context: Context): RoomLabResultRepository =
            instance ?: synchronized(this) {
                instance ?: RoomLabResultRepository(LabDatabase.getDatabase(context)).also { instance = it }
            }
    }
}
