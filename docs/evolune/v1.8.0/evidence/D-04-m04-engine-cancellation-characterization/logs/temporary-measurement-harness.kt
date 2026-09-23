package io.github.yingqiu0871.evolune.d04

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
import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.ThemeColorSource
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.ThemePresetSelection
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.experience.HistoricalProjection
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.history.AllAvailableHistory
import io.github.yingqiu0871.evolune.history.AllAvailableHistorySource
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.retrospective.FakeSettingsStore
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePkRange
import io.github.yingqiu0871.evolune.history.retrospective.RetrospectivePkViewModel
import io.github.yingqiu0871.evolune.history.testEvent
import io.github.yingqiu0871.evolune.history.testOccurrence
import io.github.yingqiu0871.evolune.history.pk.DefaultRetrospectivePkCurveRunner
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkRequest
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkResult
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkService
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSource
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkWindow
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.pk.SimulationResult
import io.github.yingqiu0871.evolune.viewmodel.DefaultPkSimulationCalculator
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import io.github.yingqiu0871.evolune.viewmodel.PKState
import io.github.yingqiu0871.evolune.viewmodel.PkSimulationCalculator
import io.github.yingqiu0871.evolune.viewmodel.PkSimulationInput
import io.github.yingqiu0871.evolune.viewmodel.PkSimulationRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Temporary D-04 measurement harness. It uses only the existing calculator/curve-runner seams;
 * this file is captured into evidence and removed after the run. It is never a product test.
 */
class D04M04MeasurementTest {
    @Test
    fun `measure home and retrospective supersession`() {
        val csv = StringBuilder(CSV_HEADER).append('\n')
        val repeated = StringBuilder()
        val cells = listOf(
            Cell("HOME", "normal", 16),
            Cell("HOME", "stress", 512),
            Cell("RETROSPECTIVE", "normal", 16),
            Cell("RETROSPECTIVE", "stress", 512)
        )

        cells.forEach { cell ->
            repeat(REPETITIONS) { repetition ->
                val row = when (cell.path) {
                    "HOME" -> measureHome(cell, repetition + 1, repeated = false)
                    else -> measureRetrospective(cell, repetition + 1, repeated = false)
                }
                csv.append(row).append('\n')
            }
        }

        listOf(
            Cell("HOME", "stress", 512),
            Cell("RETROSPECTIVE", "stress", 512)
        ).forEach { cell ->
            repeat(REPETITIONS) { repetition ->
                val trace = when (cell.path) {
                    "HOME" -> measureHome(cell, repetition + 1, repeated = true)
                    else -> measureRetrospective(cell, repetition + 1, repeated = true)
                }
                repeated.append(trace).append('\n')
            }
        }

        val csvPath = requireNotNull(System.getenv("D04_CSV"))
        val tracePath = requireNotNull(System.getenv("D04_TRACE"))
        File(csvPath).writeText(csv.toString())
        File(tracePath).writeText(repeated.toString())
    }

    private fun measureHome(cell: Cell, repetition: Int, repeated: Boolean): String {
        val events = homeEvents(cell.eventCount)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executor = Executors.newCachedThreadPool()
        val controller = HomeController(cell, repetition, repeated, executor)
        val repository = HomeDoseEventRepository(events)
        val viewModel = HRTViewModel(
            repository = repository,
            medicationPlanRepository = EmptyMedicationPlanRepository(),
            clock = Clock.fixed(HOME_NOW, ZoneOffset.UTC),
            simulationDispatcher = Dispatchers.Default,
            simulationCalculator = controller.calculator(),
            operationScope = scope,
            bodyWeightFlow = flowOf(55.0)
        )
        val observationJob = scope.launch {
            viewModel.pkState.collect(controller::observeHomePublication)
        }
        controller.attachSupersession { viewModel.runSimulation() }
        return try {
            controller.awaitEngine(1)
            val finalGeneration = controller.finalGeneration()
            controller.awaitEngine(finalGeneration)
            controller.awaitHomePublication(viewModel, finalGeneration)
            controller.assertValidTimeline(finalGeneration)
            controller.assertNumericallyEquivalent()
            if (repeated) controller.trace() else controller.csvRow()
        } finally {
            scope.cancel()
            observationJob.cancel()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun measureRetrospective(cell: Cell, repetition: Int, repeated: Boolean): String {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executor = Executors.newCachedThreadPool()
        val controller = RetrospectiveController(cell, repetition, repeated, executor)
        val history = fixedHistory(cell.eventCount)
        val source = RetrospectivePkSource { request ->
            controller.estimate(request, history)
        }
        val settings = FakeSettingsStore(55.0)
        val viewModel = RetrospectivePkViewModel(
            retrospectivePkSource = source,
            allAvailableHistorySource = emptyAllHistorySource(history),
            historyRangeSource = emptyRangeSource(),
            settingsStore = settings,
            clock = Clock.fixed(RETROSPECTIVE_NOW, ZoneOffset.UTC),
            displayZone = { ZoneOffset.UTC },
            operationScope = scope
        )
        controller.attachSupersession { generation ->
            viewModel.selectRange(
                when (generation) {
                    1 -> RetrospectivePkRange.LAST_30_DAYS
                    2 -> RetrospectivePkRange.LAST_90_DAYS
                    else -> RetrospectivePkRange.LAST_7_DAYS
                }
            )
        }
        return try {
            controller.awaitEngine(1)
            val finalGeneration = controller.finalGeneration()
            controller.awaitEngine(finalGeneration)
            controller.awaitRetrospectivePublication(viewModel, finalGeneration)
            controller.assertValidTimeline(finalGeneration)
            controller.assertNumericallyEquivalent(history)
            if (repeated) controller.trace() else controller.csvRow()
        } finally {
            scope.cancel()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private data class Cell(val path: String, val fixture: String, val eventCount: Int)

    private abstract class BaseController(
        private val cell: Cell,
        private val repetition: Int,
        private val repeated: Boolean,
        private val executor: ExecutorService
    ) {
        protected val records = ConcurrentHashMap<Int, Record>()
        private val nextGeneration = AtomicInteger()
        private var supersede: ((Int) -> Unit)? = null

        fun attach(action: (Int) -> Unit) {
            supersede = action
        }

        protected fun beginGeneration(): Record {
            val generation = nextGeneration.incrementAndGet()
            val shouldSupersede = if (repeated) {
                generation <= REPEATED_SUPERSESSIONS
            } else {
                generation == 1
            }
            return Record(generation, shouldSupersede).also {
                it.t0 = System.nanoTime()
                records[generation] = it
            }
        }

        protected fun recordEngineEntry(record: Record) {
            if (record.t1 != 0L) return
            record.t1 = System.nanoTime()
            record.engineEntered.countDown()
            val shouldSupersede = if (repeated) record.generation <= REPEATED_SUPERSESSIONS else record.generation == 1
            if (shouldSupersede) {
                executor.submit {
                    Thread.sleep(SUPERSESSION_DELAY_MILLIS)
                    record.t2 = System.nanoTime()
                    while (supersede == null) Thread.yield()
                    try {
                        requireNotNull(supersede)(record.generation)
                    } finally {
                        record.releaseEngine.countDown()
                    }
                }
            }
        }

        fun finalGeneration(): Int = if (repeated) REPEATED_SUPPRESSIONS_FINAL else 2

        fun awaitEngine(generation: Int) {
            val record = awaitRecord(generation)
            assertTrue("generation $generation did not enter engine", record.engineEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        protected fun awaitRecord(generation: Int): Record {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
            while (System.nanoTime() < deadline) {
                records[generation]?.let { return it }
                Thread.yield()
            }
            error("generation $generation was not created")
        }

        fun assertValidTimeline(finalGeneration: Int) {
            for (generation in 1 until finalGeneration) {
                val incumbent = awaitRecord(generation)
                val successor = awaitRecord(generation + 1)
                awaitUntil("generation $generation engine return") { incumbent.t3 > 0L }
                if (incumbent.cancelled) {
                    awaitUntil("generation $generation suppression") { incumbent.t4 > 0L }
                }
                assertTrue("T1<T2 for g$generation", incumbent.t1 < incumbent.t2)
                assertTrue(
                    "T2<T3 for g$generation: T1=${incumbent.t1},T2=${incumbent.t2},T3=${incumbent.t3}," +
                        "cancelled=${incumbent.cancelled},attempt=${incumbent.publicationAttempt}",
                    incumbent.t2 < incumbent.t3
                )
                assertTrue("successor T0 after T2 for g$generation", incumbent.t2 <= successor.t0)
                if (incumbent.cancelled) {
                    assertTrue("T3<=T4 for g$generation", incumbent.t3 <= incumbent.t4)
                } else {
                    assertTrue(
                        "g$generation neither suppressed nor directly classified",
                        incumbent.publicationAttempt != "NONE"
                    )
                }
            }
            val final = awaitRecord(finalGeneration)
            assertTrue("final generation did not complete", final.t3 > final.t1)
            assertTrue("final generation must not be cancelled", !final.cancelled)
        }

        protected fun recordsInOrder(): List<Record> = (1..finalGeneration()).map { awaitRecord(it) }

        fun trace(): String = buildString {
            append("path=${cell.path},fixture=${cell.fixture},repetition=$repetition\n")
            recordsInOrder().forEach { record ->
                append("generation=${record.generation}")
                append(",T0_ns=${record.t0},T1_ns=${record.t1},T2_ns=${record.t2}")
                append(",T3_ns=${record.t3},T4_ns=${record.t4},T5_ns=${record.t5}")
                append(",engine_exit_result=${record.engineResult != null}")
                append(",cancelled=${record.cancelled},publication_attempt=${record.publicationAttempt}")
                append(",publication_accepted=${record.publicationAccepted}\n")
            }
        }

        protected fun setFinalPublication(generation: Int, timestamp: Long) {
            awaitRecord(generation).t5 = timestamp
        }

        protected fun csvBase(incumbent: Record, successor: Record, numerical: String): String =
            listOf(
                cell.path,
                cell.fixture,
                repetition,
                incumbent.generation,
                successor.generation,
                incumbent.t0,
                incumbent.t1,
                incumbent.t2,
                incumbent.t3,
                if (incumbent.t4 > 0L) incumbent.t4 else NOT_OBSERVABLE,
                successor.t5,
                successor.t5 - incumbent.t1,
                incumbent.t3 - incumbent.t2,
                if (incumbent.t4 > 0L) incumbent.t4 - incumbent.t2 else NOT_OBSERVABLE,
                successor.t5 - incumbent.t2,
                if (incumbent.t4 > 0L) incumbent.t4 - incumbent.t3 else NOT_OBSERVABLE,
                if (incumbent.t4 > 0L) successor.t5 - incumbent.t4 else NOT_OBSERVABLE,
                if (incumbent.publicationAttempt == "NONE") 0 else 1,
                if (incumbent.publicationAccepted) 1 else 0,
                0,
                0,
                1,
                0,
                0,
                numerical
            ).joinToString(",")
    }

    private class HomeController(
        cell: Cell,
        repetition: Int,
        repeated: Boolean,
        executor: ExecutorService
    ) : BaseController(cell, repetition, repeated, executor) {
        private var attachedViewModel: HRTViewModel? = null

        fun calculator(): PkSimulationCalculator = PkSimulationCalculator { input ->
            val record = beginGeneration()
            record.input = input
            record.job = currentCoroutineContext()[Job]
            val runner = PkSimulationRunner { events, weight, start, end, steps ->
                recordEngineEntry(record)
                awaitReleaseIfSuperseded(record)
                val result = SimulationEngine(events, weight, start, end, steps).run()
                record.t3 = System.nanoTime()
                record.engineResult = result
                record.observePostEngineCancellation()
                result
            }
            try {
                DefaultPkSimulationCalculator.calculate(input, runner).also { state ->
                    record.state = state
                    if (record.t2 != 0L) {
                        record.publicationAttempt = "REACHED_HRT_ASSIGNMENT"
                    }
                }
            } catch (cancelled: CancellationException) {
                record.t4 = System.nanoTime()
                record.cancelled = true
                record.publicationAttempt = "NONE_BEFORE_HRT_ASSIGNMENT"
                throw cancelled
            }
        }

        fun attachSupersession(action: () -> Unit) {
            super.attach { action() }
        }

        fun observeHomePublication(state: PKState) {
            records.values.forEach { record ->
                val result = record.state?.simulationResult
                if (record.t2 != 0L && result != null && state.simulationResult === result) {
                    record.publicationAccepted = true
                    record.publicationAttempt = "ACCEPTED_STALE_AT_HRT_ASSIGNMENT"
                }
            }
        }

        fun awaitHomePublication(viewModel: HRTViewModel, generation: Int) {
            attachedViewModel = viewModel
            awaitUntil("Home calculation result") { awaitRecord(generation).state != null }
            val result = requireNotNull(awaitRecord(generation).state).simulationResult
            awaitUntil("Home publication") {
                val state = viewModel.pkState.value
                state.simulationResult === result && !state.isSimulating
            }.also { timestamp ->
                setFinalPublication(generation, timestamp)
                awaitRecord(generation).publicationAccepted = true
                awaitRecord(generation).publicationAttempt = "ACCEPTED"
            }
        }

        fun assertNumericallyEquivalent() {
            val final = awaitRecord(finalGeneration())
            val input = requireNotNull(final.input)
            val reference = kotlinx.coroutines.runBlocking {
                DefaultPkSimulationCalculator.calculate(input)
            }
            assertTrue("Home successor differs from clean reference", reference == final.state)
        }

        fun csvRow(): String {
            val incumbent = awaitRecord(1)
            val successor = awaitRecord(2)
            return csvBase(incumbent, successor, "PASS")
        }
    }

    private class RetrospectiveController(
        private val cell: Cell,
        repetition: Int,
        repeated: Boolean,
        executor: ExecutorService
    ) : BaseController(cell, repetition, repeated, executor) {
        private var sourceAction: ((Int) -> Unit)? = null
        private var history: AllAvailableHistory? = null

        suspend fun estimate(
            request: RetrospectivePkRequest,
            fixedHistory: AllAvailableHistory
        ): RetrospectivePkResult {
            val record = beginGeneration()
            record.request = request
            record.job = currentCoroutineContext()[Job]
            history = fixedHistory
            val runner = io.github.yingqiu0871.evolune.history.pk.RetrospectivePkCurveRunner {
                    events, weight, start, end, steps ->
                recordEngineEntry(record)
                awaitReleaseIfSuperseded(record)
                val result = SimulationEngine(events, weight, start, end, steps).run()
                record.t3 = System.nanoTime()
                record.engineResult = result
                record.observePostEngineCancellation()
                result
            }
            return try {
                RetrospectivePkService(
                    history = AllAvailableHistorySource { _, _, _ -> fixedHistory },
                    curveRunner = runner,
                    computationDispatcher = Dispatchers.Default
                ).estimate(request).also { result ->
                    record.retroResult = result as? RetrospectivePkResult.Available
                    if (record.t2 != 0L) {
                        record.publicationAttempt = "REJECTED_BY_VIEWMODEL_GENERATION_TOKEN"
                    }
                }
            } catch (cancelled: CancellationException) {
                record.t4 = System.nanoTime()
                record.cancelled = true
                record.publicationAttempt = "NONE_BEFORE_RETROSPECTIVE_COORDINATOR"
                throw cancelled
            } finally {
                if (!currentCoroutineContext().isActive && record.t3 > 0L && record.t4 == 0L) {
                    record.t4 = System.nanoTime()
                    record.cancelled = true
                    record.publicationAttempt = "REJECTED_BY_RETROSPECTIVE_CANCELLATION"
                }
            }
        }

        fun attachSupersession(action: (Int) -> Unit) {
            sourceAction = action
            super.attach(action)
        }

        fun awaitRetrospectivePublication(viewModel: RetrospectivePkViewModel, generation: Int) {
            awaitUntil("retrospective calculation result") { awaitRecord(generation).retroResult != null }
            val result = requireNotNull(awaitRecord(generation).retroResult).curve
            awaitUntil("retrospective publication") {
                val available = viewModel.uiState.value.result as? RetrospectivePkResult.Available
                available?.curve === result
            }.also { timestamp ->
                setFinalPublication(generation, timestamp)
                awaitRecord(generation).publicationAccepted = true
                awaitRecord(generation).publicationAttempt = "ACCEPTED"
            }
        }

        fun assertNumericallyEquivalent(fixedHistory: AllAvailableHistory) {
            val final = awaitRecord(finalGeneration())
            val request = requireNotNull(final.request)
            val reference = kotlinx.coroutines.runBlocking {
                RetrospectivePkService(
                    history = AllAvailableHistorySource { _, _, _ -> fixedHistory },
                    curveRunner = DefaultRetrospectivePkCurveRunner,
                    computationDispatcher = Dispatchers.Default
                ).estimate(request)
            }
            assertTrue("retrospective successor differs from clean reference", reference == final.retroResult)
        }

        fun csvRow(): String {
            val incumbent = awaitRecord(1)
            val successor = awaitRecord(2)
            return csvBase(incumbent, successor, "PASS")
        }
    }

    private class Record(val generation: Int, val shouldSupersede: Boolean) {
        @Volatile var t0: Long = 0
        @Volatile var t1: Long = 0
        @Volatile var t2: Long = 0
        @Volatile var t3: Long = 0
        @Volatile var t4: Long = 0
        @Volatile var t5: Long = 0
        @Volatile var cancelled: Boolean = false
        @Volatile var state: PKState? = null
        @Volatile var input: PkSimulationInput? = null
        @Volatile var request: RetrospectivePkRequest? = null
        @Volatile var retroResult: RetrospectivePkResult.Available? = null
        @Volatile var engineResult: SimulationResult? = null
        @Volatile var publicationAttempt: String = "NONE"
        @Volatile var publicationAccepted: Boolean = false
        @Volatile var job: Job? = null
        val engineEntered = CountDownLatch(1)
        val releaseEngine = CountDownLatch(1)

        fun observePostEngineCancellation() {
            if (job?.isCancelled == true && t4 == 0L) {
                t4 = System.nanoTime()
                cancelled = true
                publicationAttempt = "REJECTED_AT_POST_ENGINE_BOUNDARY"
            }
        }
    }

    private class HomeDoseEventRepository(private val events: List<DoseEvent>) : DoseEventRepository {
        private val observed = MutableStateFlow(events)
        override fun observeAll(): Flow<List<DoseEvent>> = observed
        override suspend fun getById(id: UUID): DoseEvent? = events.firstOrNull { it.id == id }
        override suspend fun findOccurredBetween(startInclusive: Instant, endExclusive: Instant) =
            events.filter { it.occurredAt >= startInclusive && it.occurredAt < endExclusive }
        override suspend fun findRecordedLocalDateBetween(startInclusive: LocalDate, endInclusive: LocalDate) =
            events.filter { it.localDate != null && it.localDate >= startInclusive && it.localDate <= endInclusive }
        override suspend fun findAllOccurredUpTo(endInclusive: Instant) = events.filter { !it.occurredAt.isAfter(endInclusive) }
        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = events
        override suspend fun insert(event: DoseEvent): InsertResult = InsertResult.Invalid
        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult = UpdateResult.Invalid
        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.NotFound
        override suspend fun deleteIfRevisionMatches(id: UUID, expectedRevision: Long): ConditionalDeleteResult =
            ConditionalDeleteResult.NotFound
        override suspend fun deleteLatestRecordedIfRevisionMatches(eventId: UUID, eventRevision: Long): LatestDoseDeleteResult =
            LatestDoseDeleteResult.EventNotFound
        override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
    }

    private class EmptyMedicationPlanRepository : MedicationPlanRepository {
        private val empty = flowOf<List<MedicationPlan>>(emptyList())
        override fun observeAll(): Flow<List<MedicationPlan>> = empty
        override fun observeEnabled(): Flow<List<MedicationPlan>> = empty
        override suspend fun getById(id: UUID): MedicationPlan? = null
        override suspend fun save(plan: MedicationPlan): PlanSaveResult = PlanSaveResult.Invalid
        override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult = PlanUpdateResult.Invalid
        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.NotFound
        override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
    }

    private fun fixedHistory(eventCount: Int): AllAvailableHistory {
        val entries = (0 until eventCount).map { index ->
            val eventTime = RETROSPECTIVE_NOW.minusSeconds((index + 1L) * 60L)
            val occurrence = testOccurrence(
                slotId = index + 1L,
                date = eventTime.atZone(ZoneOffset.UTC).toLocalDate(),
                time = eventTime.atZone(ZoneOffset.UTC).toLocalTime().withNano(0)
            )
            matchedEntry(
                occurrence = occurrence,
                event = testEvent(
                    id = index + 1L,
                    occurredAt = eventTime,
                    slotId = occurrence.slotId,
                    localDate = eventTime.atZone(ZoneOffset.UTC).toLocalDate(),
                    zoneId = ZoneOffset.UTC
                )
            )
        }
        return AllAvailableHistory(
            upperBoundInclusive = RETROSPECTIVE_NOW,
            lookbackStart = entries.minOf { it.event.occurredAt },
            projection = HistoricalProjection(entries)
        )
    }

    private fun emptyAllHistorySource(history: AllAvailableHistory): AllAvailableHistorySource =
        AllAvailableHistorySource { _, _, _ -> history }

    private fun emptyRangeSource(): HistoryRangeSource = HistoryRangeSource { start, end, _, _ ->
        HistoricalRange(startDate = start, endDate = end, days = emptyList())
    }

    private fun homeEvents(eventCount: Int): List<DoseEvent> = (0 until eventCount).map { index ->
        val occurredAt = HOME_NOW.minusSeconds((index + 1L) * 3_600L)
        DoseEvent(
            id = UUID(0L, index + 1L),
            route = Route.ORAL,
            occurredAt = occurredAt,
            zoneId = ZoneOffset.UTC,
            localDate = occurredAt.atZone(ZoneOffset.UTC).toLocalDate(),
            doseMG = 2.0,
            ester = Ester.E2,
            source = DoseEventSource.MANUAL,
            status = DoseEventStatus.RECORDED
        )
    }

    private companion object {
        const val REPETITIONS = 3
        const val REPEATED_SUPERSESSIONS = 3
        const val REPEATED_SUPPRESSIONS_FINAL = 4
        const val SUPERSESSION_DELAY_MILLIS = 2L
        const val TIMEOUT_SECONDS = 60L
        const val NOT_OBSERVABLE = "NOT_OBSERVABLE"
        val HOME_NOW: Instant = Instant.parse("2026-01-02T03:04:05.678Z")
        val RETROSPECTIVE_NOW: Instant = Instant.parse("2026-09-16T12:00:00Z")
        const val CSV_HEADER =
            "path,fixture,repetition,incumbent_generation,successor_generation,T0_ns,T1_ns,T2_ns,T3_ns,T4_ns,T5_ns," +
                "in_flight_run_duration_ns,supersession_to_engine_return_ns,supersession_to_old_suppression_ns," +
                "supersession_to_new_publication_ns,post_engine_suppression_overhead_ns," +
                "successor_completion_after_suppression_ns,obsolete_runs_entered_before_T2," +
                "obsolete_runs_finished_after_T2,obsolete_runs_started_after_T2,current_generation_complete_runs," +
                "stale_publication_attempts,stale_publications_accepted,partial_publications,numerical_equivalence"

        fun awaitUntil(label: String, predicate: () -> Boolean): Long {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
            while (System.nanoTime() < deadline) {
                if (predicate()) return System.nanoTime()
                Thread.yield()
            }
            error("$label did not become observable")
        }

        fun awaitReleaseIfSuperseded(record: Record) {
            if (record.shouldSupersede) {
                assertTrue(
                    "supersession did not release generation ${record.generation}",
                    record.releaseEngine.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                )
            }
        }
    }
}
