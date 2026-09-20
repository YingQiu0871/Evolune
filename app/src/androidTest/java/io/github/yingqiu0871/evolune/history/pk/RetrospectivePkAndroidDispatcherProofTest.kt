package io.github.yingqiu0871.evolune.history.pk

import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.experience.HistoricalProjection
import io.github.yingqiu0871.evolune.history.AllAvailableHistory
import io.github.yingqiu0871.evolune.history.AllAvailableHistorySource
import io.github.yingqiu0871.evolune.history.matchedEntry
import io.github.yingqiu0871.evolune.history.testEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * V1.8.0 A-01 device-level dispatcher proof (evidence supplement).
 *
 * Proves on a real Android runtime that the retrospective PK computation executes off the
 * Android main thread while the caller context is the main thread:
 *
 * - the caller invokes the service from `Dispatchers.Main` and asserts identity with
 *   `Looper.getMainLooper().thread`;
 * - the production public constructor (`RetrospectivePkService(history)`, real defaults:
 *   `DefaultRetrospectivePkCurveRunner` + `Dispatchers.Default`) is exercised and the
 *   thread observed inside the computation context (history read) is off main;
 * - a test-only delegating runner probe (internal seam, explicit production default
 *   `Dispatchers.Default`) observes the actual curve-runner thread identity.
 *
 * Identity comparisons (`===` / assertSame) are the gate; thread-name prefixes are only
 * corroboration. No production code is involved in the probe.
 */
@RunWith(AndroidJUnit4::class)
class RetrospectivePkAndroidDispatcherProofTest {

    private val tag = "A01DispatcherProof"

    @Test
    fun productionDefaultDispatcherRunsPkComputationOffMain() {
        val mainThread = Looper.getMainLooper().thread
        val windowStart = Instant.parse("2025-06-01T08:00:00Z")
        val window = RetrospectivePkWindow(windowStart, windowStart.plus(Duration.ofDays(30)))
        val request = RetrospectivePkRequest(
            visibleWindow = window,
            cursor = null,
            displayZone = ZoneOffset.UTC,
            bodyWeightKG = 60.0,
            capturedAt = windowStart
        )
        val event = testEvent(
            id = 101L,
            occurredAt = windowStart,
            routeKey = "INJECTION",
            medicationKey = "EV",
            doseAmount = 5.0
        )
        val projection = HistoricalProjection(entries = listOf(matchedEntry(event = event)))

        var callerThread: Thread? = null
        var productionReaderThread: Thread? = null
        var probeReaderThread: Thread? = null
        var runnerThread: Thread? = null
        var productionResult: RetrospectivePkResult? = null
        var probedResult: RetrospectivePkResult? = null

        val productionHistory = AllAvailableHistorySource { upperBound, _, _ ->
            productionReaderThread = Thread.currentThread()
            AllAvailableHistory(
                upperBoundInclusive = upperBound,
                lookbackStart = event.occurredAt,
                projection = projection
            )
        }
        val probeHistory = AllAvailableHistorySource { upperBound, _, _ ->
            probeReaderThread = Thread.currentThread()
            AllAvailableHistory(
                upperBoundInclusive = upperBound,
                lookbackStart = event.occurredAt,
                projection = projection
            )
        }

        val productionService = RetrospectivePkService(productionHistory)
        val probeRunner = RetrospectivePkCurveRunner { events, weight, startH, endH, steps ->
            runnerThread = Thread.currentThread()
            DefaultRetrospectivePkCurveRunner.run(events, weight, startH, endH, steps)
        }
        val probedService = RetrospectivePkService(
            history = probeHistory,
            curveRunner = probeRunner,
            computationDispatcher = Dispatchers.Default
        )

        runBlocking {
            withContext(Dispatchers.Main) {
                callerThread = Thread.currentThread()
                productionResult = productionService.estimate(request)
                probedResult = probedService.estimate(request)
            }
        }

        val caller = requireNotNull(callerThread)
        val productionReader = requireNotNull(productionReaderThread)
        val probeReader = requireNotNull(probeReaderThread)
        val runner = requireNotNull(runnerThread)

        logThread("caller-thread", caller, mainThread)
        logThread("main-looper-thread", mainThread, mainThread)
        logThread("production-reader-thread", productionReader, mainThread)
        logThread("probe-reader-thread", probeReader, mainThread)
        logThread("runner-thread", runner, mainThread)

        assertSame("caller must be the Android main thread", mainThread, caller)
        assertNotSame("production computation (history read) must be off main", mainThread, productionReader)
        assertNotSame("probe computation (history read) must be off main", mainThread, probeReader)
        assertNotSame("curve runner must be off main", mainThread, runner)
        assertTrue(
            "production computation should run on the production default dispatcher",
            productionReader.name.startsWith("DefaultDispatcher-worker")
        )
        assertTrue(
            "curve runner should run on the production default dispatcher",
            runner.name.startsWith("DefaultDispatcher-worker")
        )

        val available = productionResult as RetrospectivePkResult.Available
        assertTrue("production result must publish a non-empty series", available.series.points.isNotEmpty())
        assertEquals(listOf(event.eventId), available.summary.engineInputEventIds)
        assertTrue(probedResult is RetrospectivePkResult.Available)

        Log.i(
            tag,
            "result=Available seriesPoints=${available.series.points.size} " +
                "engineEvents=${available.summary.engineInputEventIds.size} dispatcher=Dispatchers.Default"
        )
        Log.i(tag, "A01_ANDROID_DISPATCHER_PROOF_PASS")
    }

    private fun logThread(label: String, thread: Thread, mainThread: Thread) {
        Log.i(
            tag,
            "$label=${thread.name} identity=${System.identityHashCode(thread)} " +
                "sameAsMain=${thread === mainThread}"
        )
    }
}
