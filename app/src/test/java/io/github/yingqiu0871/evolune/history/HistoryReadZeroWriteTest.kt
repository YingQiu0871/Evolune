package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanUpdateResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * A-04 A7: the History read path must never write.
 *
 * The acceptance item asks for a **counting repository substitute** asserting zero writes
 * (`V17_ACCEPTANCE.md` A7). Every mutation method of both authoritative repositories is counted,
 * and the read counters prove that the read path really executed (so "zero writes" cannot be
 * vacuous).
 */
class HistoryReadZeroWriteTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val day: LocalDate = LocalDate.of(2025, 1, 5)
    private val plan = syntheticPlan(slots = listOf(LocalTime.of(23, 0)))
    private val now: Instant = day.plusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun `reading a history range performs reads and zero writes`() {
        val events = FakeDoseEventRepository(listOf(recordedEvent()))
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val countingEvents = CountingDoseEvents(events)
        val countingPlans = CountingPlans(plans)

        val range = runBlocking {
            HistoryReadService(countingPlans, countingEvents).readRange(day, day, utc, now)
        }

        assertEquals(1, range.recordedCount)
        assertTrue("the read path must actually read", countingEvents.reads > 0)
        assertTrue("the read path must actually read plans", countingPlans.reads > 0)
        assertEquals(0, countingEvents.writes)
        assertEquals(0, countingPlans.writes)
    }

    @Test
    fun `the initial month load performs reads and zero writes`() {
        val countingEvents = CountingDoseEvents(FakeDoseEventRepository(listOf(recordedEvent())))
        val countingPlans = CountingPlans(FakeMedicationPlanRepository(listOf(plan)))
        val viewModel = viewModel(countingPlans, countingEvents)
        try {
            assertEquals(1, viewModel.uiState.value.loadedMonth?.monthValue)
            assertTrue(countingEvents.reads > 0)
            assertEquals(0, countingEvents.writes)
            assertEquals(0, countingPlans.writes)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `selecting a day performs neither an additional read nor any write`() {
        val countingEvents = CountingDoseEvents(FakeDoseEventRepository(listOf(recordedEvent())))
        val countingPlans = CountingPlans(FakeMedicationPlanRepository(listOf(plan)))
        val viewModel = viewModel(countingPlans, countingEvents)
        try {
            val readsAfterLoad = countingEvents.reads

            viewModel.selectDate(LocalDate.of(2025, 1, 4))

            assertEquals(readsAfterLoad, countingEvents.reads)
            assertEquals(0, countingEvents.writes)
            assertEquals(0, countingPlans.writes)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `refreshing and retrying read again but still never write`() {
        val countingEvents = CountingDoseEvents(FakeDoseEventRepository(listOf(recordedEvent())))
        val countingPlans = CountingPlans(FakeMedicationPlanRepository(listOf(plan)))
        val viewModel = viewModel(countingPlans, countingEvents)
        try {
            val readsAfterLoad = countingEvents.reads

            viewModel.onSurfaceShown() // first entry: owned by the initial load
            viewModel.onSurfaceShown() // tab return → one refresh
            viewModel.showPreviousMonth()
            viewModel.retry()

            assertTrue("refresh/retry must read", countingEvents.reads > readsAfterLoad)
            assertEquals(0, countingEvents.writes)
            assertEquals(0, countingPlans.writes)
        } finally {
            scope.cancel()
        }
    }

    private fun viewModel(
        plans: MedicationPlanRepository,
        events: DoseEventRepository
    ): HistoryViewModel {
        val service = HistoryReadService(plans, events)
        return HistoryViewModel(
            rangeSource = HistoryRangeSource { start, end, zone, instant ->
                service.readRange(start, end, zone, instant)
            },
            clock = Clock.fixed(now, utc),
            displayZone = { utc },
            operationScope = scope
        )
    }

    private fun recordedEvent(): DoseEvent = DoseEvent(
        id = UUID(7L, 901L),
        route = Route.ORAL,
        occurredAt = day.atTime(23, 5).toInstant(ZoneOffset.UTC),
        zoneId = utc,
        localDate = day,
        doseMG = 2.0,
        ester = Ester.E2,
        extras = emptyMap(),
        slotId = UUID(1L, 0L),
        source = DoseEventSource.REMINDER,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )

    // ---------- counting substitutes ----------

    private class CountingDoseEvents(
        private val delegate: DoseEventRepository
    ) : DoseEventRepository by delegate {

        var reads = 0
        var writes = 0

        override fun observeAll(): Flow<List<DoseEvent>> {
            reads += 1
            return delegate.observeAll()
        }

        override suspend fun getById(id: UUID): DoseEvent? {
            reads += 1
            return delegate.getById(id)
        }

        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> {
            reads += 1
            return delegate.findOccurredBetween(startInclusive, endExclusive)
        }

        override suspend fun findRecordedLocalDateBetween(
            startInclusive: LocalDate,
            endInclusive: LocalDate
        ): List<DoseEvent> {
            reads += 1
            return delegate.findRecordedLocalDateBetween(startInclusive, endInclusive)
        }

        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> {
            reads += 1
            return delegate.getEventsForPk(asOf)
        }

        override suspend fun insert(event: DoseEvent): InsertResult {
            writes += 1
            return delegate.insert(event)
        }

        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult {
            writes += 1
            return delegate.update(event, expectedRevision)
        }

        override suspend fun delete(id: UUID): DeleteResult {
            writes += 1
            return delegate.delete(id)
        }

        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult {
            writes += 1
            return delegate.deleteIfRevisionMatches(id, expectedRevision)
        }

        override suspend fun deleteLatestRecordedIfRevisionMatches(
            eventId: UUID,
            eventRevision: Long
        ): LatestDoseDeleteResult {
            writes += 1
            return delegate.deleteLatestRecordedIfRevisionMatches(eventId, eventRevision)
        }

        override suspend fun deleteAll(): DeleteResult {
            writes += 1
            return delegate.deleteAll()
        }
    }

    private class CountingPlans(
        private val delegate: MedicationPlanRepository
    ) : MedicationPlanRepository by delegate {

        var reads = 0
        var writes = 0

        override fun observeAll(): Flow<List<MedicationPlan>> {
            reads += 1
            return delegate.observeAll()
        }

        override fun observeEnabled(): Flow<List<MedicationPlan>> {
            reads += 1
            return delegate.observeEnabled()
        }

        override suspend fun getById(id: UUID): MedicationPlan? {
            reads += 1
            return delegate.getById(id)
        }

        override suspend fun save(plan: MedicationPlan): PlanSaveResult {
            writes += 1
            return delegate.save(plan)
        }

        override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult {
            writes += 1
            return delegate.setEnabled(id, enabled)
        }

        override suspend fun delete(id: UUID): DeleteResult {
            writes += 1
            return delegate.delete(id)
        }

        override suspend fun deleteAll(): DeleteResult {
            writes += 1
            return delegate.deleteAll()
        }
    }
}
