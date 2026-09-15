package io.github.yingqiu0871.evolune.history.pk

import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import io.github.yingqiu0871.evolune.history.HistoryReadService
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * V17-C-01 §13.5: the whole retrospective read path performs zero writes over five intents and
 * still performs real reads. The counting decorator covers every mutation method of the
 * authoritative dose-event repository.
 */
class RetrospectivePkZeroWriteTest {

    private val t0: Instant = Instant.parse("2025-06-01T08:00:00Z")

    @Test
    fun `five intents perform zero writes and real reads`() = runBlocking {
        val injection = event(UUID(0L, 1L), t0.minus(Duration.ofHours(1)))
        val delegate = FakeDoseEventRepository(listOf(injection))
        val counting = CountingDoseEventRepository(delegate)
        val service = RetrospectivePkService(HistoryReadService(FakeMedicationPlanRepository(), counting))

        // 1) initial estimate
        assertTrue(service.estimate(request()) is RetrospectivePkResult.Available)
        // 2) refresh
        assertTrue(service.estimate(request()) is RetrospectivePkResult.Available)
        // 3) different window
        assertTrue(
            service.estimate(request(RetrospectivePkWindow(t0, t0.plus(Duration.ofDays(10)))))
                is RetrospectivePkResult.Available
        )
        // 4) read failure (typed storage failure) then retry
        delegate.allEventsUpperBoundFailure = RepositoryPersistenceException("read", null)
        val failed = service.estimate(request())
        assertTrue(failed is RetrospectivePkResult.Unavailable)
        assertEquals(
            RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE,
            (failed as RetrospectivePkResult.Unavailable).reason
        )
        delegate.allEventsUpperBoundFailure = null
        assertTrue(service.estimate(request()) is RetrospectivePkResult.Available)
        // 5) no eligible facts
        val emptyCounting = CountingDoseEventRepository(FakeDoseEventRepository())
        val emptyService = RetrospectivePkService(
            HistoryReadService(FakeMedicationPlanRepository(), emptyCounting)
        )
        assertTrue(emptyService.estimate(request()) is RetrospectivePkResult.Unavailable)

        assertEquals("no mutation method may be called", 0, counting.mutationCalls)
        assertEquals("no mutation method may be called", 0, emptyCounting.mutationCalls)
        assertTrue("reads must actually happen", delegate.allEventsUpperBoundCalls > 0)
        assertTrue("reads must actually happen", emptyCounting.delegateReadCount() > 0)
    }

    private fun request(window: RetrospectivePkWindow = RetrospectivePkWindow(t0, t0.plus(Duration.ofDays(30)))) =
        RetrospectivePkRequest(
            visibleWindow = window,
            cursor = null,
            displayZone = java.time.ZoneOffset.UTC,
            bodyWeightKG = 60.0,
            capturedAt = t0
        )

    private fun event(id: UUID, at: Instant): DoseEvent = DoseEvent(
        id = id,
        route = Route.INJECTION,
        occurredAt = at,
        doseMG = 5.0,
        ester = Ester.EV,
        source = DoseEventSource.MANUAL
    )

    private class CountingDoseEventRepository(
        private val delegate: DoseEventRepository
    ) : DoseEventRepository by delegate {
        var insertCalls = 0
            private set
        var updateCalls = 0
            private set
        var deleteCalls = 0
            private set
        var conditionalDeleteCalls = 0
            private set
        var latestDeleteCalls = 0
            private set
        var deleteAllCalls = 0
            private set

        val mutationCalls: Int
            get() = insertCalls + updateCalls + deleteCalls +
                conditionalDeleteCalls + latestDeleteCalls + deleteAllCalls

        fun delegateReadCount(): Int = (delegate as? FakeDoseEventRepository)?.allEventsUpperBoundCalls ?: 0

        override suspend fun insert(event: DoseEvent): InsertResult {
            insertCalls += 1
            return delegate.insert(event)
        }

        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult {
            updateCalls += 1
            return delegate.update(event, expectedRevision)
        }

        override suspend fun delete(id: UUID): DeleteResult {
            deleteCalls += 1
            return delegate.delete(id)
        }

        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult {
            conditionalDeleteCalls += 1
            return delegate.deleteIfRevisionMatches(id, expectedRevision)
        }

        override suspend fun deleteLatestRecordedIfRevisionMatches(
            eventId: UUID,
            eventRevision: Long
        ): LatestDoseDeleteResult {
            latestDeleteCalls += 1
            return delegate.deleteLatestRecordedIfRevisionMatches(eventId, eventRevision)
        }

        override suspend fun deleteAll(): DeleteResult {
            deleteAllCalls += 1
            return delegate.deleteAll()
        }
    }
}
