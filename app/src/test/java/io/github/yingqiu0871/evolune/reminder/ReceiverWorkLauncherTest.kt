package io.github.yingqiu0871.evolune.reminder

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

class ReceiverWorkLauncherTest {
    @Test
    fun `every typed outcome finishes exactly once after work`() {
        listOf(
            "success",
            "idempotent",
            "conflict",
            "not-found",
            "storage-failure"
        ).forEach { outcome ->
            val dispatcher = QueueDispatcher()
            val calls = mutableListOf<String>()
            val job = ReceiverWorkLauncher(dispatcher).launch(
                work = { calls += outcome },
                finish = { calls += "finish" }
            )

            assertTrue(calls.isEmpty())
            assertFalse(job.isCompleted)
            dispatcher.runUntilIdle()

            assertEquals(listOf(outcome, "finish"), calls)
            assertTrue(job.isCompleted)
        }
    }

    @Test
    fun `unexpected and side effect failures finish once`() {
        listOf("repository", "side-effect").forEach { category ->
            val dispatcher = QueueDispatcher()
            var failures = 0
            var finishes = 0
            val job = ReceiverWorkLauncher(
                dispatcher = dispatcher,
                onUnexpectedFailure = { failures += 1 }
            ).launch(
                work = { throw IllegalStateException(category) },
                finish = { finishes += 1 }
            )

            dispatcher.runUntilIdle()

            assertEquals(1, failures)
            assertEquals(1, finishes)
            assertTrue(job.isCompleted)
        }
    }

    @Test
    fun `in process cancellation finishes once and completes delivery job`() {
        val dispatcher = QueueDispatcher()
        var started = false
        var finishes = 0
        val job = ReceiverWorkLauncher(dispatcher).launch(
            work = {
                suspendCancellableCoroutine<Unit> {
                    started = true
                }
            },
            finish = { finishes += 1 }
        )

        dispatcher.runNext()
        assertTrue(started)
        job.cancel()
        dispatcher.runUntilIdle()

        assertEquals(1, finishes)
        assertTrue(job.isCompleted)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `cancelling one delivery does not affect another`() {
        val dispatcher = QueueDispatcher()
        var firstStarted = false
        var firstFinishes = 0
        var secondFinishes = 0
        val launcher = ReceiverWorkLauncher(dispatcher)
        val first = launcher.launch(
            work = {
                suspendCancellableCoroutine<Unit> {
                    firstStarted = true
                }
            },
            finish = { firstFinishes += 1 }
        )
        val second = launcher.launch(
            work = {},
            finish = { secondFinishes += 1 }
        )

        dispatcher.runNext()
        assertTrue(firstStarted)
        first.cancel()
        dispatcher.runUntilIdle()

        assertEquals(1, firstFinishes)
        assertEquals(1, secondFinishes)
        assertTrue(first.isCancelled)
        assertTrue(second.isCompleted)
    }
}

private class QueueDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    fun runNext() {
        queue.removeFirst().run()
    }

    fun runUntilIdle() {
        while (queue.isNotEmpty()) {
            runNext()
        }
    }
}
