package io.github.yingqiu0871.evolune.core.dataapi

import io.github.yingqiu0871.evolune.core.model.LabResult
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Local store of user-entered lab results. Same identity, idempotency and revision semantics
 * as [DoseEventRepository]: insert requires revision 1, update bumps the revision only when the
 * stored revision matches.
 */
interface LabResultRepository {
    /** Observes all lab results ordered by measuredAt descending, then id. */
    fun observeAll(): Flow<List<LabResult>>

    suspend fun getById(id: UUID): LabResult?

    suspend fun insert(result: LabResult): InsertResult

    suspend fun update(result: LabResult, expectedRevision: Long): UpdateResult

    /** Atomically deletes only when the stored revision equals expectedRevision. */
    suspend fun deleteIfRevisionMatches(id: UUID, expectedRevision: Long): ConditionalDeleteResult
}
