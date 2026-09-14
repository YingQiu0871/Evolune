package io.github.yingqiu0871.evolune.ui.screens.insights

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.history.HistoryReadService
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.insights.InsightsPhase
import io.github.yingqiu0871.evolune.history.insights.InsightsRangeSelection
import io.github.yingqiu0871.evolune.history.insights.InsightsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * v1.7-B-04 release gate: the Insights read path never writes.
 *
 * The counting decorators wrap the **production** Room repositories, so the measured read runs
 * through the real DAO layer while every mutation method is counted. Covered intents: initial load,
 * range change, surface return, foreground refresh and retry.
 */
@RunWith(AndroidJUnit4::class)
class InsightsZeroWriteDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun browsingInsightsNeverWritesToTheRealDatabase() {
        val provider = ProductionRepositoryProvider.get(context)
        val countingEvents = CountingDoseEvents(provider.doseEvents)
        val countingPlans = CountingPlans(provider.medicationPlans)
        val readService = HistoryReadService(countingPlans, countingEvents)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = InsightsViewModel(
            rangeSource = HistoryRangeSource { startDate, endDate, zone, now ->
                readService.readRange(startDate, endDate, zone, now)
            },
            clock = Clock.systemUTC(),
            displayZone = { ZoneOffset.UTC },
            operationScope = scope
        )

        try {
            val initialReads = countingEvents.reads + countingPlans.reads
            assertTrue("the initial load must read", initialReads > 0)
            assertNull(viewModel.uiState.value.failure)

            viewModel.selectRange(InsightsRangeSelection.Last7Days)
            viewModel.onSurfaceShown()
            viewModel.onAppForegrounded()
            viewModel.retry()
            viewModel.selectRange(InsightsRangeSelection.CurrentMonth)

            assertTrue(
                "browsing must keep reading",
                countingEvents.reads + countingPlans.reads > initialReads
            )
            assertEquals("no dose_events mutation during Insights", 0, countingEvents.writes)
            assertEquals("no plan mutation during Insights", 0, countingPlans.writes)
            // the production read runs on Room's executor, so wait for the load to settle
            val settled = runBlocking {
                withTimeoutOrNull(10_000L) {
                    viewModel.uiState.first { it.phase != InsightsPhase.LOADING }
                }
            }
            assertEquals(InsightsRangeSelection.CurrentMonth, viewModel.uiState.value.selection)
            // a successful load settles in CONTENT or EMPTY depending on the device's real data
            assertTrue(
                "the load must settle successfully",
                requireNotNull(settled).phase == InsightsPhase.CONTENT ||
                    settled.phase == InsightsPhase.EMPTY
            )
            assertNull(viewModel.uiState.value.failure)
        } finally {
            scope.cancel()
        }
    }

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
            startInclusive: java.time.Instant,
            endExclusive: java.time.Instant
        ): List<DoseEvent> {
            reads += 1
            return delegate.findOccurredBetween(startInclusive, endExclusive)
        }

        override suspend fun findRecordedLocalDateBetween(
            startInclusive: java.time.LocalDate,
            endInclusive: java.time.LocalDate
        ): List<DoseEvent> {
            reads += 1
            return delegate.findRecordedLocalDateBetween(startInclusive, endInclusive)
        }

        override suspend fun getEventsForPk(asOf: java.time.Instant): List<DoseEvent> {
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

        override suspend fun deleteIfRevisionMatches(id: UUID, expectedRevision: Long): ConditionalDeleteResult {
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
