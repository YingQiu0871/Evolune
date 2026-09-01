package io.github.yingqiu0871.evolune.application

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the persistent Wear App confirm and undo mutations mutually exclusive. */
internal object WearAppMutationCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
