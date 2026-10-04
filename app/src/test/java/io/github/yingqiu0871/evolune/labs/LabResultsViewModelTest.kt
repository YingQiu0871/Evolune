package io.github.yingqiu0871.evolune.labs

import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LabResultRepository
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.core.model.LabUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class LabResultsViewModelTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `saving a new draft inserts a revision 1 result with a fresh id`() {
        val repository = FakeLabRepository()
        val viewModel = viewModel(repository)

        viewModel.save(viewModel.newDraft().copy(measuredAt = MEASURED, valueText = "180.5"))

        assertEquals(LabResultOperationState.Saved, viewModel.operation.value)
        assertEquals(
            listOf(LabResult(NEW_ID, MEASURED, 180.5, LabUnit.PG_PER_ML, revision = 1)),
            repository.inserted
        )
    }

    @Test
    fun `an invalid draft reports the issue and writes nothing`() {
        val repository = FakeLabRepository()
        val viewModel = viewModel(repository)

        viewModel.save(viewModel.newDraft().copy(valueText = "0"))

        assertEquals(
            LabResultOperationState.Invalid(LabResultDraftIssue.VALUE_NOT_POSITIVE),
            viewModel.operation.value
        )
        assertEquals(emptyList<LabResult>(), repository.inserted)
    }

    @Test
    fun `editing updates against the revision the editor opened with`() {
        val repository = FakeLabRepository()
        val viewModel = viewModel(repository)
        val draft = LabResultDraft(EXISTING_ID, 3, MEASURED, "200", LabUnit.PMOL_PER_L)

        viewModel.save(draft)

        assertEquals(LabResultOperationState.Saved, viewModel.operation.value)
        assertEquals(
            listOf(LabResult(EXISTING_ID, MEASURED, 200.0, LabUnit.PMOL_PER_L, revision = 3) to 3L),
            repository.updated
        )

        repository.updateResult = UpdateResult.RevisionConflict
        viewModel.acknowledgeOperation()
        viewModel.save(draft)
        assertEquals(LabResultOperationState.Stale, viewModel.operation.value)
    }

    @Test
    fun `delete is conditional on the shown revision and storage errors are reported`() {
        val repository = FakeLabRepository()
        val viewModel = viewModel(repository)
        val shown = LabResult(EXISTING_ID, MEASURED, 120.0, LabUnit.PG_PER_ML, revision = 2)

        viewModel.delete(shown)
        assertEquals(LabResultOperationState.Deleted, viewModel.operation.value)
        assertEquals(listOf(EXISTING_ID to 2L), repository.deleted)

        viewModel.acknowledgeOperation()
        repository.failWrites = true
        viewModel.delete(shown)
        assertEquals(LabResultOperationState.StorageFailure, viewModel.operation.value)
    }

    @Test
    fun `list state follows the repository and reports read failures`() = runBlocking {
        val repository = FakeLabRepository()
        val viewModel = viewModel(repository)
        val stored = LabResult(EXISTING_ID, MEASURED, 120.0, LabUnit.PG_PER_ML)
        val collector = scope.launch { viewModel.listState.collect {} }

        repository.results.value = listOf(stored)
        assertEquals(LabResultsListState.Content(listOf(stored)), viewModel.listState.value)
        collector.cancel()

        val failing = viewModel(FakeLabRepository(failReads = true))
        val failingCollector = scope.launch { failing.listState.collect {} }
        assertEquals(
            LabResultsListState.Error,
            failing.listState.first { it != LabResultsListState.Loading }
        )
        failingCollector.cancel()
    }

    private fun viewModel(repository: LabResultRepository) = LabResultsViewModel(
        repository = repository,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        idSupplier = { NEW_ID },
        operationScope = scope
    )

    private class FakeLabRepository(private val failReads: Boolean = false) : LabResultRepository {
        val results = MutableStateFlow<List<LabResult>>(emptyList())
        val inserted = mutableListOf<LabResult>()
        val updated = mutableListOf<Pair<LabResult, Long>>()
        val deleted = mutableListOf<Pair<UUID, Long>>()
        var updateResult: UpdateResult = UpdateResult.Updated
        var failWrites = false

        override fun observeAll(): Flow<List<LabResult>> =
            if (failReads) flow { throw IllegalStateException("synthetic read failure") } else results

        override suspend fun getById(id: UUID): LabResult? = null

        override suspend fun insert(result: LabResult): InsertResult {
            if (failWrites) throw IllegalStateException("synthetic write failure")
            inserted += result
            return InsertResult.Inserted
        }

        override suspend fun update(result: LabResult, expectedRevision: Long): UpdateResult {
            if (failWrites) throw IllegalStateException("synthetic write failure")
            updated += result to expectedRevision
            return updateResult
        }

        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult {
            if (failWrites) throw IllegalStateException("synthetic write failure")
            deleted += id to expectedRevision
            return ConditionalDeleteResult.Deleted
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-04T12:00:00Z")
        val MEASURED: Instant = Instant.parse("2026-10-03T08:00:00Z")
        val NEW_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000ee")
        val EXISTING_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
    }
}
