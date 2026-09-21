package io.github.yingqiu0871.evolune.ui.screens

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
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
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import io.github.yingqiu0871.evolune.viewmodel.PKState
import io.github.yingqiu0871.evolune.viewmodel.PkSimulationCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * A-04 §13/§22: device proof for Home's lifecycle-gated 1 Hz clock collection.
 *
 * The test hosts the real [HomeScreen] and real Activity lifecycle. The clock remains allowed to
 * run through the existing WhileSubscribed(5000) grace period after STOP, then stops being read.
 * Resuming restarts the collection and reads the latest clock value without a synthetic PK
 * refresh when no boundary was crossed.
 */
@RunWith(AndroidJUnit4::class)
class HomeClockLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var operationScope: CoroutineScope? = null

    @After
    fun cancelOperationScope() {
        operationScope?.cancel()
    }

    @Test
    fun homeClockStopsAfterLifecycleStopAndResumesWithoutSyntheticRefresh() {
        val clock = CountingClock(Instant.parse("2026-09-21T12:00:00Z"))
        val calculator = RecordingCalculator()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        operationScope = scope
        val viewModel = HRTViewModel(
            repository = EmptyDoseEventRepository(),
            medicationPlanRepository = EmptyMedicationPlanRepository(),
            clock = clock,
            simulationDispatcher = Dispatchers.Unconfined,
            simulationCalculator = calculator,
            operationScope = scope
        )

        composeRule.setContent {
            EvoluneTheme {
                HomeScreen(viewModel = viewModel, showTopBar = false)
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { calculator.calls.get() == 1 }

        val readsBeforeStartedTick = clock.millisReads.get()
        composeRule.waitUntil(5_000) { clock.millisReads.get() > readsBeforeStartedTick }
        val readsAtStop = clock.millisReads.get()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()

        // The existing WhileSubscribed(5000) upstream grace period is intentional. Wait beyond it
        // before asserting that the Home clock is no longer being read.
        SystemClock.sleep(6_500)
        val readsAfterGrace = clock.millisReads.get()
        SystemClock.sleep(1_500)
        assertEquals("Home clock reads must stop after WhileSubscribed(5000)", readsAfterGrace, clock.millisReads.get())
        assertTrue("the clock was active before STOP", readsAfterGrace >= readsAtStop)

        clock.advanceByHours(4.0)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitUntil(5_000) { clock.millisReads.get() > readsAfterGrace }

        assertEquals("resume before a boundary must not synthesize PK refresh", 1, calculator.calls.get())
    }

    private class CountingClock(initial: Instant) : Clock() {
        private var nowMillis = initial.toEpochMilli()
        val millisReads = AtomicInteger()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)

        override fun millis(): Long {
            millisReads.incrementAndGet()
            return nowMillis
        }

        fun advanceByHours(hours: Double) {
            nowMillis += (hours * 3_600_000.0).toLong()
        }
    }

    private class RecordingCalculator : PkSimulationCalculator {
        val calls = AtomicInteger()

        override suspend fun calculate(input: io.github.yingqiu0871.evolune.viewmodel.PkSimulationInput): PKState {
            calls.incrementAndGet()
            return PKState()
        }
    }

    private class EmptyDoseEventRepository : DoseEventRepository {
        override fun observeAll(): Flow<List<DoseEvent>> = flowOf(emptyList())
        override suspend fun getById(id: UUID): DoseEvent? = null
        override suspend fun findOccurredBetween(startInclusive: Instant, endExclusive: Instant): List<DoseEvent> = emptyList()
        override suspend fun findRecordedLocalDateBetween(startInclusive: LocalDate, endInclusive: LocalDate): List<DoseEvent> = emptyList()
        override suspend fun findAllOccurredUpTo(endInclusive: Instant): List<DoseEvent> = emptyList()
        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = emptyList()
        override suspend fun insert(event: DoseEvent): InsertResult = error("unused")
        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult = error("unused")
        override suspend fun delete(id: UUID): DeleteResult = error("unused")
        override suspend fun deleteIfRevisionMatches(id: UUID, expectedRevision: Long): ConditionalDeleteResult = error("unused")
        override suspend fun deleteLatestRecordedIfRevisionMatches(eventId: UUID, eventRevision: Long): LatestDoseDeleteResult = error("unused")
        override suspend fun deleteAll(): DeleteResult = error("unused")
    }

    private class EmptyMedicationPlanRepository : MedicationPlanRepository {
        override fun observeAll(): Flow<List<MedicationPlan>> = flowOf(emptyList())
        override fun observeEnabled(): Flow<List<MedicationPlan>> = flowOf(emptyList())
        override suspend fun getById(id: UUID): MedicationPlan? = null
        override suspend fun save(plan: MedicationPlan): PlanSaveResult = error("unused")
        override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult = error("unused")
        override suspend fun delete(id: UUID): DeleteResult = error("unused")
        override suspend fun deleteAll(): DeleteResult = error("unused")
    }
}
