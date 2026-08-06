package io.github.yuninggu.evolune.reminder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class ReceiverWorkLauncher(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onUnexpectedFailure: (Throwable) -> Unit = {},
    private val onFinished: () -> Unit = {}
) {
    fun launch(
        work: suspend () -> Unit,
        finish: () -> Unit
    ): Job {
        val deliveryJob = SupervisorJob()
        CoroutineScope(deliveryJob + dispatcher).launch {
            try {
                work()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onUnexpectedFailure(error)
            } finally {
                try {
                    finish()
                } finally {
                    onFinished()
                    deliveryJob.completeDelivery()
                }
            }
        }
        return deliveryJob
    }
}

private fun CompletableJob.completeDelivery() {
    complete()
}
