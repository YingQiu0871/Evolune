package io.github.yingqiu0871.evolune.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import io.github.yingqiu0871.evolune.data.mapper.MappingError
import io.github.yingqiu0871.evolune.data.mapper.MappingResult
import kotlinx.coroutines.CancellationException

sealed class RepositoryStorageException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class CorruptAggregateException(
    val mappingError: MappingError
) : RepositoryStorageException("Persisted aggregate violates the repository contract")

class RepositoryConstraintException(
    operation: String,
    cause: Throwable
) : RepositoryStorageException("Database constraint failed during $operation", cause)

class RepositoryPersistenceException(
    operation: String,
    cause: Throwable? = null
) : RepositoryStorageException("Database operation failed during $operation", cause)

internal fun <T> MappingResult<T>.orThrowCorrupt(): T = when (this) {
    is MappingResult.Success -> value
    is MappingResult.Failure -> throw CorruptAggregateException(error)
}

internal suspend fun <T> runStorageOperation(
    operation: String,
    block: suspend () -> T
): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: RepositoryStorageException) {
    throw error
} catch (error: SQLiteConstraintException) {
    throw RepositoryConstraintException(operation, error)
} catch (error: SQLiteException) {
    throw RepositoryPersistenceException(operation, error)
}

internal fun Throwable.asRepositoryStorageException(operation: String): Throwable = when (this) {
    is CancellationException -> this
    is RepositoryStorageException -> this
    is SQLiteConstraintException -> RepositoryConstraintException(operation, this)
    is SQLiteException -> RepositoryPersistenceException(operation, this)
    else -> this
}
