package io.github.yingqiu0871.evolune.d06

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.pk.SimulationResult
import io.github.yingqiu0871.evolune.viewmodel.DefaultPkSimulationCalculator
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import io.github.yingqiu0871.evolune.viewmodel.PKState
import io.github.yingqiu0871.evolune.viewmodel.PkSimulationCalculator
import io.github.yingqiu0871.evolune.viewmodel.PkSimulationRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class D06M04CooperativeCancellationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixedNow = Instant.parse("2026-09-01T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val tag = "D06M04"
    private val eventCount = 512

    private data class Submission(
        val id: Int,
        val label: String,
        val at: Long,
        val thread: String
    ) {
        @Volatile var activeAtSubmission: Int = 0
    }

    private class EngineCall(
        val ordinal: Int,
        val request: Submission,
        val bodyLaunched: Long,
        val bodyThread: String
    ) {
        @Volatile var engineEntered: Long = 0
        @Volatile var engineReturned: Long = 0
        @Volatile var calculatorReturned: Long = 0
        @Volatile var publicationObserved: Long = 0
        @Volatile var cancellationObserved: Long = 0
        @Volatile var completed = false
        @Volatile var engineThread = ""
        @Volatile var publicationThread = ""
    }

    private class Probe(private val logTag: String) {
        val submissions = CopyOnWriteArrayList<Submission>()
        val calls = CopyOnWriteArrayList<EngineCall>()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val firstBodyGate = CountDownLatch(1)
        private val nextSubmission = AtomicInteger()

        fun submit(label: String): Submission {
            val submission = Submission(
                id = nextSubmission.incrementAndGet(),
                label = label,
                at = System.nanoTime(),
                thread = Thread.currentThread().name
            )
            submission.activeAtSubmission = active.get()
            submissions += submission
            Log.i(
                logTag,
                "EVENT,type=submit,id=${submission.id},label=$label,at=${submission.at}," +
                    "thread=${submission.thread},active=${submission.activeAtSubmission}"
            )
            return submission
        }

        fun latestSubmission(): Submission = submissions.last()

        fun beginBody(): EngineCall {
            val request = latestSubmission()
            val call = EngineCall(
                ordinal = calls.size + 1,
                request = request,
                bodyLaunched = System.nanoTime(),
                bodyThread = Thread.currentThread().name
            )
            calls += call
            Log.i(
                logTag,
                "EVENT,type=body,ordinal=${call.ordinal},requestId=${request.id}," +
                    "at=${call.bodyLaunched},thread=${call.bodyThread}"
            )
            if (call.ordinal == 1) {
                check(firstBodyGate.await(10, TimeUnit.SECONDS)) { "initial body gate timed out" }
            }
            return call
        }

        fun runEngine(
            call: EngineCall,
            events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
            bodyWeightKG: Double,
            startTimeH: Double,
            endTimeH: Double,
            numberOfSteps: Int,
            cancellationCheck: () -> Unit
        ): SimulationResult {
            call.engineEntered = System.nanoTime()
            call.engineThread = Thread.currentThread().name
            val running = active.incrementAndGet()
            peak.updateAndGet { old -> maxOf(old, running) }
            Log.i(
                logTag,
                "EVENT,type=engine-enter,ordinal=${call.ordinal},requestId=${call.request.id}," +
                    "at=${call.engineEntered},thread=${call.engineThread},active=$running"
            )
            return try {
                SimulationEngine(
                    events = events,
                    bodyWeightKG = bodyWeightKG,
                    startTimeH = startTimeH,
                    endTimeH = endTimeH,
                    numberOfSteps = numberOfSteps,
                    cancellationCheck = {
                        try {
                            cancellationCheck()
                        } catch (error: CancellationException) {
                            call.cancellationObserved = System.nanoTime()
                            throw error
                        }
                    }
                ).run().also { call.completed = true }
            } finally {
                call.engineReturned = System.nanoTime()
                val remaining = active.decrementAndGet()
                Log.i(
                    logTag,
                    "EVENT,type=engine-return,ordinal=${call.ordinal},requestId=${call.request.id}," +
                        "at=${call.engineReturned},thread=${Thread.currentThread().name},active=$remaining"
                )
            }
        }

        fun calculator(): PkSimulationCalculator = PkSimulationCalculator { input ->
            val ownerContext = currentCoroutineContext()
            val call = beginBody()
            val runner = PkSimulationRunner { events, weight, start, end, steps, check ->
                runEngine(call, events, weight, start, end, steps, check)
            }
            try {
                DefaultPkSimulationCalculator.calculate(input, runner) {
                    ownerContext.ensureActive()
                }
            } catch (error: CancellationException) {
                throw error
            }.also {
                call.calculatorReturned = System.nanoTime()
                Log.i(
                    logTag,
                    "EVENT,type=calculator-return,ordinal=${call.ordinal},requestId=${call.request.id}," +
                        "at=${call.calculatorReturned},thread=${Thread.currentThread().name}"
                )
            }
        }

        fun observePublication(state: PKState) {
            if (state.isSimulating || state.simulationResult == null) return
            val call = calls.lastOrNull { it.calculatorReturned != 0L && it.publicationObserved == 0L }
                ?: return
            call.publicationObserved = System.nanoTime()
            call.publicationThread = Thread.currentThread().name
            Log.i(
                logTag,
                "EVENT,type=publication,ordinal=${call.ordinal},requestId=${call.request.id}," +
                    "at=${call.publicationObserved},thread=${call.publicationThread}"
            )
        }

        fun releaseInitialBody() = firstBodyGate.countDown()

        fun awaitCallCount(count: Int, timeoutMs: Long = 20_000): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (calls.size < count && System.currentTimeMillis() < deadline) Thread.sleep(5)
            return calls.size >= count
        }

        fun awaitPublication(requestId: Int, timeoutMs: Long = 30_000): EngineCall? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                calls.lastOrNull { it.request.id == requestId && it.publicationObserved != 0L }
                    ?.let { return it }
                Thread.sleep(5)
            }
            return calls.lastOrNull { it.request.id == requestId && it.publicationObserved != 0L }
        }

        fun awaitIdle(timeoutMs: Long = 30_000): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (active.get() != 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
            return active.get() == 0
        }
    }

    private data class Run(
        val probe: Probe,
        val vm: HRTViewModel,
        val scope: CoroutineScope,
        val collector: Job,
        val initial: Submission
    )

    private fun homeEvents(): List<DoseEvent> {
        val base = fixedNow.minusSeconds(86_400L * 90)
        return (0 until eventCount).map { index ->
            DoseEvent(
                id = UUID(0L, (index + 1).toLong()),
                route = Route.ORAL,
                occurredAt = base.plusSeconds(index * 3_600L),
                zoneId = ZoneOffset.UTC,
                localDate = null,
                doseMG = 2.0,
                ester = Ester.E2,
                extras = emptyMap(),
                slotId = null,
                source = DoseEventSource.MANUAL,
                status = DoseEventStatus.RECORDED,
                revision = 1L
            )
        }
    }

    private fun homeRepository(events: List<DoseEvent>) = object : DoseEventRepository {
        private val flow = MutableStateFlow(events)
        override fun observeAll(): Flow<List<DoseEvent>> = flow
        override suspend fun getById(id: UUID): DoseEvent? = events.firstOrNull { it.id == id }
        override suspend fun findOccurredBetween(startInclusive: Instant, endExclusive: Instant) = emptyList<DoseEvent>()
        override suspend fun findRecordedLocalDateBetween(startInclusive: java.time.LocalDate, endInclusive: java.time.LocalDate) = emptyList<DoseEvent>()
        override suspend fun findAllOccurredUpTo(endInclusive: Instant) = emptyList<DoseEvent>()
        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = events
        override suspend fun insert(event: DoseEvent) = InsertResult.Invalid
        override suspend fun update(event: DoseEvent, expectedRevision: Long) = UpdateResult.Invalid
        override suspend fun delete(id: UUID) = DeleteResult.NotFound
        override suspend fun deleteIfRevisionMatches(id: UUID, expectedRevision: Long) = ConditionalDeleteResult.NotFound
        override suspend fun deleteLatestRecordedIfRevisionMatches(eventId: UUID, eventRevision: Long) = LatestDoseDeleteResult.EventNotFound
        override suspend fun deleteAll() = DeleteResult.NotFound
    }

    private val emptyPlanRepository = object : MedicationPlanRepository {
        private val empty = MutableStateFlow(emptyList<MedicationPlan>())
        override fun observeAll(): Flow<List<MedicationPlan>> = empty
        override fun observeEnabled(): Flow<List<MedicationPlan>> = empty
        override suspend fun getById(id: UUID): MedicationPlan? = null
        override suspend fun save(plan: MedicationPlan) = PlanSaveResult.Invalid
        override suspend fun setEnabled(id: UUID, enabled: Boolean) = PlanUpdateResult.NotFound
        override suspend fun delete(id: UUID) = DeleteResult.NotFound
        override suspend fun deleteAll() = DeleteResult.NotFound
    }

    private fun startRun(): Run {
        val probe = Probe(tag)
        val events = homeEvents()
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        lateinit var vm: HRTViewModel
        lateinit var initial: Submission
        instrumentation.runOnMainSync {
            initial = probe.submit("initial")
            vm = HRTViewModel(
                repository = homeRepository(events),
                medicationPlanRepository = emptyPlanRepository,
                clock = fixedClock,
                operationScope = scope,
                simulationCalculator = probe.calculator()
            )
        }
        val collectorReady = CountDownLatch(1)
        val collector = scope.launch {
            collectorReady.countDown()
            vm.pkState.collect { state -> probe.observePublication(state) }
        }
        check(collectorReady.await(5, TimeUnit.SECONDS)) { "collector did not attach" }
        probe.releaseInitialBody()
        check(probe.awaitCallCount(1)) { "initial engine did not launch" }
        return Run(probe, vm, scope, collector, initial)
    }

    private fun submit(run: Run, label: String): Submission {
        lateinit var submission: Submission
        instrumentation.runOnMainSync {
            submission = run.probe.submit(label)
            run.vm.runSimulation()
        }
        return submission
    }

    private fun close(run: Run) {
        run.collector.cancel()
        run.scope.cancel()
    }

    private fun emitResult(group: String, rep: Int, run: Run, final: Submission, valid: Boolean) {
        val calls = run.probe.calls.toList()
        val finalCall = calls.lastOrNull { it.request.id == final.id }
        val obsolete = calls.filter { it.request.id != final.id && it.engineEntered != 0L }
        val obsoleteDrain = obsolete.mapNotNull { call ->
            call.engineReturned.takeIf { it != 0L }?.minus(final.at)
        }.maxOrNull()?.coerceAtLeast(0L) ?: 0L
        val finalStart = finalCall?.engineEntered?.minus(final.at) ?: 0L
        val finalRuntime = if (finalCall != null && finalCall.engineReturned != 0L) {
            finalCall.engineReturned - finalCall.engineEntered
        } else 0L
        val finalPublication = if (finalCall != null && finalCall.publicationObserved != 0L) {
            finalCall.publicationObserved - final.at
        } else 0L
        val engineOnMain = calls.any { it.engineThread.contains("main", ignoreCase = true) }
        Log.i(
            tag,
            "RESULT,group=$group,rep=$rep,valid=$valid," +
                "submitted=${run.probe.submissions.size},started=${calls.size}," +
                "skipped=${run.probe.submissions.size - calls.size},finalRequestId=${final.id}," +
                "finalSubmitNs=${final.at},finalEngineEnterNs=${finalCall?.engineEntered ?: 0}," +
                "finalEngineReturnNs=${finalCall?.engineReturned ?: 0}," +
                "finalPublicationNs=${finalCall?.publicationObserved ?: 0}," +
                "finalStartNs=$finalStart,finalRuntimeNs=$finalRuntime," +
                "finalPublicationLatencyNs=$finalPublication,activeAtFinalSubmission=${final.activeAtSubmission}," +
                "peakConcurrency=${run.probe.peak.get()},obsoleteDrainNs=$obsoleteDrain," +
                "obsoleteCancelled=${obsolete.count { it.cancellationObserved != 0L }}," +
                "obsoleteCompleted=${obsolete.count { it.completed }},engineOnMain=$engineOnMain," +
                "finalEngineThread=${finalCall?.engineThread ?: ""}," +
                "publicationThread=${finalCall?.publicationThread ?: ""}"
        )
    }

    @Test
    fun cleanReference() {
        repeat(30) { rep ->
            val run = startRun()
            val final = run.initial
            val published = run.probe.awaitPublication(final.id)
            run.probe.awaitIdle()
            emitResult("A", rep, run, final, published != null && run.probe.calls.all { it.completed })
            close(run)
        }
    }

    @Test
    fun realisticProductionBurst() {
        repeat(30) { rep ->
            val run = startRun()
            var final = run.initial
            repeat(4) { index ->
                Thread.sleep(200L)
                final = submit(run, if (index == 3) "burst-final" else "burst-${index + 1}")
            }
            val published = run.probe.awaitPublication(final.id)
            run.probe.awaitIdle()
            val obsolete = run.probe.calls.filter { it.request.id != final.id }
            val valid = published != null &&
                obsolete.all { it.cancellationObserved != 0L && !it.completed } &&
                final.let { submission ->
                    run.probe.calls.lastOrNull { it.request.id == submission.id }?.completed == true
                }
            emitResult("C", rep, run, final, valid)
            close(run)
        }
    }
}
