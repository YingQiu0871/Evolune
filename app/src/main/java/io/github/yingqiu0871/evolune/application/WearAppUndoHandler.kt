@file:Suppress("ApplySharedPref", "UseKtx")

package io.github.yingqiu0871.evolune.application

import android.content.Context
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.data.repository.RepositoryStorageException
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommandCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommandRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoMessageCode
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultType
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

internal class WearAppUndoHandler(
    private val context: Context?,
    private val doseEvents: DoseEventRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val producerIdentity: () -> WearAppProducerIdentity = {
        io.github.yingqiu0871.evolune.wear.WearAppProducerIdentityStore.current(
            requireNotNull(context)
        )
    },
    private val latestSnapshotRevision: () -> Long = {
        context?.let {
            io.github.yingqiu0871.evolune.wear.WearAppSnapshotRevisionStore.current(it)
        } ?: Long.MAX_VALUE
    },
    operationJournal: WearAppUndoOperationJournal? = null
) {
    private val operationStore = operationJournal
        ?: WearAppUndoOperationStore(requireNotNull(context))

    suspend fun handle(command: WearAppUndoCommand): WearAppUndoResult =
        try {
            WearAppMutationCoordinator.withLock {
                handleLocked(command)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: RepositoryStorageException) {
            retryable(command)
        } catch (_: Throwable) {
            retryable(command)
        }

    private suspend fun handleLocked(command: WearAppUndoCommand): WearAppUndoResult {
        if (!WearAppUndoCommandRules.isValid(command)) return invalid(command)

        val fingerprint = encodedCommand(command)
        val previous = operationStore.read(command.operationId)
        if (previous != null && previous.fingerprint != fingerprint) {
            return conflict(command)
        }
        previous?.result?.let { return it }
        if (previous == null && !operationStore.begin(command.operationId, fingerprint)) {
            return retryable(command)
        }

        val currentEvent = doseEvents.getById(command.eventId)
        if (currentEvent == null && previous?.status == WearAppUndoOperationStatus.DELETE_IN_PROGRESS) {
            return terminal(
                command,
                result(
                    command,
                    WearAppUndoResultType.ALREADY_UNDONE,
                    WearAppUndoMessageCode.ALREADY_UNDONE,
                    eventId = command.eventId,
                    snapshotRefreshExpected = true
                )
            )
        }

        val currentProducer = producerIdentity()
        if (
            command.sourceSnapshot.producerInstanceId != currentProducer.producerInstanceId ||
            command.sourceSnapshot.producerGeneration != currentProducer.producerGeneration ||
            command.sourceSnapshot.snapshotRevision > latestSnapshotRevision()
        ) {
            return terminal(
                command,
                result(
                    command,
                    WearAppUndoResultType.REJECTED_STALE_IDENTITY,
                    WearAppUndoMessageCode.STALE_IDENTITY
                )
            )
        }

        if (currentEvent == null) {
            return terminal(
                command,
                result(
                    command,
                    WearAppUndoResultType.REJECTED_EVENT_NOT_FOUND,
                    WearAppUndoMessageCode.EVENT_NOT_FOUND
                )
            )
        }
        val changed = currentEvent.revision != command.expectedEventRevision ||
            currentEvent.occurredAt != command.expectedOccurredAt ||
            currentEvent.source.name != command.expectedSource
        if (changed) {
            return terminal(
                command,
                result(
                    command,
                    WearAppUndoResultType.REJECTED_EVENT_CHANGED,
                    WearAppUndoMessageCode.EVENT_CHANGED
                )
            )
        }

        if (!operationStore.markDeleteInProgress(command.operationId, fingerprint)) {
            return retryable(command)
        }
        return when (doseEvents.deleteLatestRecordedIfRevisionMatches(
            eventId = command.eventId,
            eventRevision = command.expectedEventRevision
        )) {
            LatestDoseDeleteResult.Deleted -> terminal(
                command,
                result(
                    command,
                    WearAppUndoResultType.UNDONE,
                    WearAppUndoMessageCode.UNDONE,
                    eventId = command.eventId,
                    snapshotRefreshExpected = true
                )
            )
            LatestDoseDeleteResult.EventNotFound -> terminal(
                command,
                if (previous?.status == WearAppUndoOperationStatus.DELETE_IN_PROGRESS) {
                    result(
                        command,
                        WearAppUndoResultType.ALREADY_UNDONE,
                        WearAppUndoMessageCode.ALREADY_UNDONE,
                        eventId = command.eventId,
                        snapshotRefreshExpected = true
                    )
                } else {
                    result(
                        command,
                        WearAppUndoResultType.REJECTED_EVENT_NOT_FOUND,
                        WearAppUndoMessageCode.EVENT_NOT_FOUND
                    )
                }
            )
            LatestDoseDeleteResult.EventChanged -> terminal(
                command,
                result(
                    command,
                    WearAppUndoResultType.REJECTED_EVENT_CHANGED,
                    WearAppUndoMessageCode.EVENT_CHANGED
                )
            )
            LatestDoseDeleteResult.NotLatest -> terminal(
                command,
                result(
                    command,
                    WearAppUndoResultType.REJECTED_NOT_LATEST,
                    WearAppUndoMessageCode.NOT_LATEST
                )
            )
            LatestDoseDeleteResult.Invalid -> terminal(
                command,
                result(
                    command,
                    WearAppUndoResultType.REJECTED_INVALID,
                    WearAppUndoMessageCode.INVALID_COMMAND
                )
            )
        }
    }

    private fun terminal(
        command: WearAppUndoCommand,
        result: WearAppUndoResult
    ): WearAppUndoResult = if (operationStore.saveResult(
        command.operationId,
        encodedCommand(command),
        result
    )) {
        result
    } else {
        retryable(command)
    }

    private fun result(
        command: WearAppUndoCommand,
        type: WearAppUndoResultType,
        messageCode: WearAppUndoMessageCode,
        eventId: UUID? = null,
        snapshotRefreshExpected: Boolean = false
    ): WearAppUndoResult = WearAppUndoResult(
        protocolVersion = io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol.PROTOCOL_VERSION,
        operationId = command.operationId,
        resultType = type,
        eventId = eventId,
        processedAt = clock.instant().takeIf { it.toEpochMilli() > 0L }
            ?: Instant.ofEpochMilli(1L),
        messageCode = messageCode,
        snapshotRefreshExpected = snapshotRefreshExpected
    ).also { check(WearAppUndoResultRules.isValid(it)) }

    private fun invalid(command: WearAppUndoCommand): WearAppUndoResult = result(
        command,
        WearAppUndoResultType.REJECTED_INVALID,
        WearAppUndoMessageCode.INVALID_COMMAND
    )

    private fun conflict(command: WearAppUndoCommand): WearAppUndoResult = result(
        command,
        WearAppUndoResultType.REJECTED_CONFLICT,
        WearAppUndoMessageCode.CONFLICT
    )

    private fun retryable(command: WearAppUndoCommand): WearAppUndoResult = result(
        command,
        WearAppUndoResultType.RETRYABLE_STORAGE_FAILURE,
        WearAppUndoMessageCode.STORAGE_FAILURE
    )

    private fun encodedCommand(command: WearAppUndoCommand): String =
        Base64.getEncoder().encodeToString(WearAppUndoCommandCodec.encode(command))
}

internal enum class WearAppUndoOperationStatus {
    PREPARED,
    DELETE_IN_PROGRESS
}

internal data class WearAppStoredUndo(
    val fingerprint: String,
    val status: WearAppUndoOperationStatus,
    val result: WearAppUndoResult?
)

internal interface WearAppUndoOperationJournal {
    fun read(operationId: UUID): WearAppStoredUndo?
    fun begin(operationId: UUID, fingerprint: String): Boolean
    fun markDeleteInProgress(operationId: UUID, fingerprint: String): Boolean
    fun saveResult(operationId: UUID, fingerprint: String, result: WearAppUndoResult): Boolean
}

internal class WearAppUndoOperationStore(context: Context) : WearAppUndoOperationJournal {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun read(operationId: UUID): WearAppStoredUndo? {
        val fingerprint = preferences.getString(key(operationId, FINGERPRINT), null)
            ?: return null
        val status = WearAppUndoOperationStatus.valueOf(
            preferences.getString(key(operationId, STATUS), null)
                ?: WearAppUndoOperationStatus.PREPARED.name
        )
        val result = preferences.getString(key(operationId, RESULT), null)?.let { encoded ->
            runCatching {
                WearAppUndoResultCodec.decode(Base64.getDecoder().decode(encoded))
            }.getOrNull()
        }
        return WearAppStoredUndo(fingerprint, status, result)
    }

    @Synchronized
    override fun begin(operationId: UUID, fingerprint: String): Boolean {
        val existing = preferences.getString(key(operationId, FINGERPRINT), null)
        if (existing != null) return existing == fingerprint
        return preferences.edit()
            .putString(key(operationId, FINGERPRINT), fingerprint)
            .putString(key(operationId, STATUS), WearAppUndoOperationStatus.PREPARED.name)
            .remove(key(operationId, RESULT))
            .commit()
    }

    @Synchronized
    override fun markDeleteInProgress(operationId: UUID, fingerprint: String): Boolean {
        val existing = preferences.getString(key(operationId, FINGERPRINT), null)
        if (existing != fingerprint) return false
        return preferences.edit()
            .putString(key(operationId, STATUS), WearAppUndoOperationStatus.DELETE_IN_PROGRESS.name)
            .commit()
    }

    @Synchronized
    override fun saveResult(
        operationId: UUID,
        fingerprint: String,
        result: WearAppUndoResult
    ): Boolean {
        if (!WearAppUndoResultRules.isValid(result)) return false
        val existing = preferences.getString(key(operationId, FINGERPRINT), null)
        if (existing != null && existing != fingerprint) return false
        return preferences.edit()
            .putString(key(operationId, FINGERPRINT), fingerprint)
            .putString(key(operationId, STATUS), WearAppUndoOperationStatus.DELETE_IN_PROGRESS.name)
            .putString(
                key(operationId, RESULT),
                Base64.getEncoder().encodeToString(WearAppUndoResultCodec.encode(result))
            )
            .commit()
    }

    private fun key(operationId: UUID, suffix: String): String =
        "$KEY_PREFIX$operationId.$suffix"

    private companion object {
        const val PREFERENCES_NAME = "wear_app_undo_operations"
        const val KEY_PREFIX = "operation."
        const val FINGERPRINT = "fingerprint"
        const val STATUS = "status"
        const val RESULT = "result"
    }
}
