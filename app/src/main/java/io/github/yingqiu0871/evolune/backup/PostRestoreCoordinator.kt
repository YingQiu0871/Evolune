package io.github.yingqiu0871.evolune.backup

import kotlinx.coroutines.CancellationException

enum class PostRestoreRefreshResult {
    COMPLETE,
    WARNING
}

/**
 * Runs only after B2 has reached its committed success point. These effects
 * are maintenance/reconciliation work and cannot turn committed persistence
 * back into a failed restore.
 */
class PostRestoreCoordinator(
    private val effects: List<suspend () -> Unit>
) {
    suspend fun afterRestore(): PostRestoreRefreshResult {
        var warning = false
        effects.forEach { effect ->
            try {
                effect()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                warning = true
            }
        }
        return if (warning) {
            PostRestoreRefreshResult.WARNING
        } else {
            PostRestoreRefreshResult.COMPLETE
        }
    }
}
