@file:Suppress("ApplySharedPref", "UseKtx")

package io.github.yingqiu0871.evolune.wear

import android.content.Context
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommandCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommandCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoMessageCode
import io.github.yingqiu0871.evolune.experience.wear.operationIdFromWearAppResultPath
import io.github.yingqiu0871.evolune.experience.wear.wearAppCommandPath
import java.util.Base64
import java.util.UUID

internal enum class WearAppPendingOperationType {
    CONFIRM,
    UNDO
}

internal sealed interface WearAppPendingOperation {
    val operationType: WearAppPendingOperationType
    val operationId: UUID
    val commandDataItemUri: String
    val sourceSnapshot: WearAppSnapshotIdentity
    val sendAttempt: Long
    val awaitingAuthoritativeSnapshot: Boolean
}

internal data class WearAppPendingConfirmation(
    val command: WearAppConfirmCommand,
    override val commandDataItemUri: String,
    val terminalResult: WearAppConfirmResult?,
    override val sendAttempt: Long
) : WearAppPendingOperation {
    override val operationType = WearAppPendingOperationType.CONFIRM
    override val operationId get() = command.operationId
    val occurrenceId get() = command.occurrenceId
    val commandCreatedAt get() = command.createdAt
    override val sourceSnapshot get() = command.sourceSnapshot
    override val awaitingAuthoritativeSnapshot: Boolean
        get() = terminalResult?.resultType == WearAppConfirmResultType.CONFIRMED ||
            terminalResult?.resultType == WearAppConfirmResultType.ALREADY_CONFIRMED
}

internal data class WearAppPendingUndo(
    val command: WearAppUndoCommand,
    override val commandDataItemUri: String,
    val terminalResult: WearAppUndoResult?,
    override val sendAttempt: Long
) : WearAppPendingOperation {
    override val operationType = WearAppPendingOperationType.UNDO
    override val operationId get() = command.operationId
    val eventId get() = command.eventId
    override val sourceSnapshot get() = command.sourceSnapshot
    override val awaitingAuthoritativeSnapshot: Boolean
        get() = terminalResult?.resultType == WearAppUndoResultType.UNDONE ||
            terminalResult?.resultType == WearAppUndoResultType.ALREADY_UNDONE
}

internal enum class WearAppResultApply {
    Applied,
    Duplicate,
    Rejected
}

internal data class WearAppUndoTransientUiState(
    val messageCode: WearAppUndoMessageCode? = null
) {
    fun afterResult(result: WearAppUndoResult): WearAppUndoTransientUiState = copy(
        messageCode = when (result.resultType) {
            WearAppUndoResultType.UNDONE,
            WearAppUndoResultType.ALREADY_UNDONE -> null
            else -> result.messageCode
        }
    )

    fun afterAuthoritativeSnapshot(): WearAppUndoTransientUiState = copy(messageCode = null)

    fun consume(): Pair<WearAppUndoMessageCode?, WearAppUndoTransientUiState> =
        messageCode to afterAuthoritativeSnapshot()
}

internal object WearAppConfirmationStore {
    private const val PREFERENCES_NAME = "wear_app_confirmations"
    private const val KEY_COMMAND = "command"
    private const val KEY_COMMAND_URI = "command_uri"
    private const val KEY_OPERATION_TYPE = "operation_type"
    private const val KEY_RESULT = "result"
    private const val KEY_SEND_ATTEMPT = "send_attempt"
    private const val KEY_LAST_RESULT = "last_result"
    private const val KEY_LAST_CONFIRM_RESULT = "last_confirm_result"
    private const val KEY_LAST_UNDO_RESULT = "last_undo_result"
    private const val KEY_UNDO_UI_MESSAGE = "undo_ui_message"

    @Synchronized
    fun getPending(context: Context): WearAppPendingConfirmation? =
        readPendingOperation(context) as? WearAppPendingConfirmation

    @Synchronized
    fun getPendingUndo(context: Context): WearAppPendingUndo? =
        readPendingOperation(context) as? WearAppPendingUndo

    @Synchronized
    fun getPendingOperation(context: Context): WearAppPendingOperation? =
        readPendingOperation(context)

    @Synchronized
    fun beginOrReuse(
        context: Context,
        command: WearAppConfirmCommand
    ): WearAppPendingConfirmation? {
        val existing = readPendingOperation(context)
        if (existing != null) {
            return (existing as? WearAppPendingConfirmation)
                ?.takeIf { it.command == command }
        }
        if (hasStoredCommand(context)) return null
        val payload = Base64.getEncoder().encodeToString(
            WearAppConfirmCommandCodec.encode(command)
        )
        val committed = preferences(context).edit()
            .putString(KEY_COMMAND, payload)
            .putString(KEY_COMMAND_URI, wearAppCommandPath(command.operationId))
            .putString(KEY_OPERATION_TYPE, WearAppPendingOperationType.CONFIRM.name)
            .putLong(KEY_SEND_ATTEMPT, 0L)
            .remove(KEY_RESULT)
            .commit()
        return if (committed) getPending(context) else null
    }

    @Synchronized
    fun beginOrReuseUndo(
        context: Context,
        command: WearAppUndoCommand
    ): WearAppPendingUndo? {
        val existing = readPendingOperation(context)
        if (existing != null) {
            return (existing as? WearAppPendingUndo)
                ?.takeIf { it.command == command }
        }
        if (hasStoredCommand(context)) return null
        val payload = Base64.getEncoder().encodeToString(
            WearAppUndoCommandCodec.encode(command)
        )
        val committed = preferences(context).edit()
            .putString(KEY_COMMAND, payload)
            .putString(KEY_COMMAND_URI, wearAppCommandPath(command.operationId))
            .putString(KEY_OPERATION_TYPE, WearAppPendingOperationType.UNDO.name)
            .putLong(KEY_SEND_ATTEMPT, 0L)
            .remove(KEY_RESULT)
            .remove(KEY_UNDO_UI_MESSAGE)
            .commit()
        return if (committed) getPendingUndo(context) else null
    }

    @Synchronized
    fun clearPendingIfOperation(context: Context, operationId: UUID): Boolean {
        val pending = readPendingOperation(context) ?: return false
        if (pending.operationId != operationId) return false
        return clearPending(context)
    }

    @Synchronized
    fun applyResult(
        context: Context,
        path: String,
        result: WearAppConfirmResult
    ): WearAppResultApply {
        if (!WearAppConfirmResultRules.isValid(result)) return WearAppResultApply.Rejected
        val pathOperationId = operationIdFromWearAppResultPath(path)
            ?: return WearAppResultApply.Rejected
        if (pathOperationId != result.operationId) return WearAppResultApply.Rejected

        val preferences = preferences(context)
        val previousResult = decodeConfirmResult(
            preferences.getString(KEY_LAST_CONFIRM_RESULT, null)
                ?: preferences.getString(KEY_LAST_RESULT, null)
        )
        if (previousResult != null && previousResult.operationId == result.operationId) {
            return if (previousResult == result) {
                WearAppResultApply.Duplicate
            } else {
                WearAppResultApply.Rejected
            }
        }

        val pending = getPending(context) ?: return WearAppResultApply.Rejected
        if (
            pending.operationId != result.operationId ||
            pending.occurrenceId != result.occurrenceId
        ) return WearAppResultApply.Rejected

        val encoded = Base64.getEncoder().encodeToString(
            WearAppConfirmResultCodec.encode(result)
        )
        val editor = preferences.edit()
            .putString(KEY_LAST_RESULT, encoded)
            .putString(KEY_LAST_CONFIRM_RESULT, encoded)
        when (result.resultType) {
            WearAppConfirmResultType.CONFIRMED,
            WearAppConfirmResultType.ALREADY_CONFIRMED,
            WearAppConfirmResultType.RETRYABLE_STORAGE_FAILURE -> {
                editor.putString(KEY_RESULT, encoded)
            }
            else -> clearPending(editor)
        }
        return if (editor.commit()) {
            WearAppResultApply.Applied
        } else {
            WearAppResultApply.Rejected
        }
    }

    @Synchronized
    fun applyUndoResult(
        context: Context,
        path: String,
        result: WearAppUndoResult
    ): WearAppResultApply {
        if (!WearAppUndoResultRules.isValid(result)) return WearAppResultApply.Rejected
        val pathOperationId = operationIdFromWearAppResultPath(path)
            ?: return WearAppResultApply.Rejected
        if (pathOperationId != result.operationId) return WearAppResultApply.Rejected

        val preferences = preferences(context)
        val previousResult = decodeUndoResult(preferences.getString(KEY_LAST_UNDO_RESULT, null))
        if (previousResult != null && previousResult.operationId == result.operationId) {
            return if (previousResult == result) {
                WearAppResultApply.Duplicate
            } else {
                WearAppResultApply.Rejected
            }
        }

        val pending = getPendingUndo(context) ?: return WearAppResultApply.Rejected
        if (pending.operationId != result.operationId) return WearAppResultApply.Rejected

        val encoded = Base64.getEncoder().encodeToString(
            WearAppUndoResultCodec.encode(result)
        )
        val editor = preferences.edit().putString(KEY_LAST_UNDO_RESULT, encoded)
        val transientUi = WearAppUndoTransientUiState().afterResult(result)
        if (transientUi.messageCode == null) {
            editor.remove(KEY_UNDO_UI_MESSAGE)
        } else {
            editor.putString(KEY_UNDO_UI_MESSAGE, transientUi.messageCode.name)
        }
        when (result.resultType) {
            WearAppUndoResultType.UNDONE,
            WearAppUndoResultType.ALREADY_UNDONE -> editor
                .putString(KEY_RESULT, encoded)
            WearAppUndoResultType.RETRYABLE_STORAGE_FAILURE -> editor
                .putString(KEY_RESULT, encoded)
            else -> clearPending(editor)
        }
        return if (editor.commit()) {
            WearAppResultApply.Applied
        } else {
            WearAppResultApply.Rejected
        }
    }

    @Synchronized
    fun clearAfterAuthoritativeSnapshot(context: Context, snapshot: WearAppSnapshot) {
        clearTransientUndoMessage(context)
        when (val pending = readPendingOperation(context)) {
            is WearAppPendingConfirmation -> clearConfirmationAfterSnapshot(context, snapshot, pending)
            is WearAppPendingUndo -> clearUndoAfterSnapshot(context, snapshot, pending)
            null -> Unit
        }
    }

    @Synchronized
    fun nextSendAttempt(context: Context, operationId: UUID): Long? {
        val pending = readPendingOperation(context) ?: return null
        if (pending.operationId != operationId) return null
        val next = if (pending.sendAttempt < Long.MAX_VALUE) {
            pending.sendAttempt + 1L
        } else {
            Long.MAX_VALUE
        }
        return if (
            preferences(context).edit().putLong(KEY_SEND_ATTEMPT, next).commit()
        ) next else null
    }

    fun resultMessageCode(context: Context): io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmMessageCode? =
        decodeConfirmResult(
            preferences(context).getString(KEY_LAST_CONFIRM_RESULT, null)
                ?: preferences(context).getString(KEY_LAST_RESULT, null)
        )?.messageCode

    @Synchronized
    fun consumeUndoResultMessageCode(context: Context): WearAppUndoMessageCode? {
        val preferences = preferences(context)
        val code = runCatching {
            preferences.getString(KEY_UNDO_UI_MESSAGE, null)
                ?.let(WearAppUndoMessageCode::valueOf)
        }.getOrNull() ?: return null
        val (message, _) = WearAppUndoTransientUiState(code).consume()
        return message.takeIf {
            preferences.edit().remove(KEY_UNDO_UI_MESSAGE).commit()
        }
    }

    @Synchronized
    fun clearTransientUndoMessage(context: Context): Boolean =
        preferences(context).edit().remove(KEY_UNDO_UI_MESSAGE).commit()

    private fun clearConfirmationAfterSnapshot(
        context: Context,
        snapshot: WearAppSnapshot,
        pending: WearAppPendingConfirmation
    ) {
        val result = pending.terminalResult ?: return
        if (
            result.resultType != WearAppConfirmResultType.CONFIRMED &&
            result.resultType != WearAppConfirmResultType.ALREADY_CONFIRMED
        ) return
        if (!isNewerSnapshot(snapshot, pending.sourceSnapshot)) return
        val isReflectedByRecent = snapshot.recentDose?.eventId == result.eventId
        val isAbsentFromUpcoming = snapshot.upcomingOccurrences.none {
            it.occurrenceId == pending.occurrenceId
        }
        if (!isReflectedByRecent && !isAbsentFromUpcoming) return
        clearPending(context)
    }

    private fun clearUndoAfterSnapshot(
        context: Context,
        snapshot: WearAppSnapshot,
        pending: WearAppPendingUndo
    ) = if (shouldClearUndoAfterAuthoritativeSnapshot(snapshot, pending)) {
        clearPending(context)
    } else {
        false
    }

    private fun isNewerSnapshot(
        snapshot: WearAppSnapshot,
        source: WearAppSnapshotIdentity
    ): Boolean = when {
        snapshot.producerGeneration > source.producerGeneration -> true
        snapshot.producerGeneration < source.producerGeneration -> false
        snapshot.producerInstanceId != source.producerInstanceId ->
            WearAppSnapshotRules.isValid(snapshot)
        else -> snapshot.snapshotRevision > source.snapshotRevision
    }

    private fun readPendingOperation(context: Context): WearAppPendingOperation? {
        val preferences = preferences(context)
        val encodedCommand = preferences.getString(KEY_COMMAND, null) ?: return null
        val storedType = preferences.getString(KEY_OPERATION_TYPE, null)
        val type = if (storedType != null) {
            runCatching { WearAppPendingOperationType.valueOf(storedType) }.getOrNull()
                ?: return null
        } else if (decodeConfirmCommand(encodedCommand) != null) {
            // W2 records created before the operation-type field are confirms.
            WearAppPendingOperationType.CONFIRM
        } else {
            return null
        }
        return when (type) {
            WearAppPendingOperationType.CONFIRM -> readConfirmation(preferences, encodedCommand)
            WearAppPendingOperationType.UNDO -> readUndo(preferences, encodedCommand)
        }
    }

    private fun readConfirmation(
        preferences: android.content.SharedPreferences,
        encodedCommand: String
    ): WearAppPendingConfirmation? {
        val command = decodeConfirmCommand(encodedCommand) ?: return null
        val uri = preferences.getString(KEY_COMMAND_URI, null)
            ?.takeIf { it == wearAppCommandPath(command.operationId) }
            ?: return null
        return WearAppPendingConfirmation(
            command = command,
            commandDataItemUri = uri,
            sendAttempt = preferences.getLong(KEY_SEND_ATTEMPT, 0L),
            terminalResult = decodeConfirmResult(preferences.getString(KEY_RESULT, null))
                ?.takeIf {
                    it.operationId == command.operationId &&
                        it.occurrenceId == command.occurrenceId
                }
        )
    }

    private fun readUndo(
        preferences: android.content.SharedPreferences,
        encodedCommand: String
    ): WearAppPendingUndo? {
        val command = decodeUndoCommand(encodedCommand) ?: return null
        val uri = preferences.getString(KEY_COMMAND_URI, null)
            ?.takeIf { it == wearAppCommandPath(command.operationId) }
            ?: return null
        return WearAppPendingUndo(
            command = command,
            commandDataItemUri = uri,
            sendAttempt = preferences.getLong(KEY_SEND_ATTEMPT, 0L),
            terminalResult = decodeUndoResult(preferences.getString(KEY_RESULT, null))
                ?.takeIf { it.operationId == command.operationId }
        )
    }

    private fun hasStoredCommand(context: Context): Boolean =
        preferences(context).getString(KEY_COMMAND, null) != null

    private fun clearPending(context: Context): Boolean =
        clearPending(preferences(context).edit()).commit()

    private fun clearPending(
        editor: android.content.SharedPreferences.Editor
    ): android.content.SharedPreferences.Editor = editor
        .remove(KEY_COMMAND)
        .remove(KEY_COMMAND_URI)
        .remove(KEY_OPERATION_TYPE)
        .remove(KEY_RESULT)
        .remove(KEY_SEND_ATTEMPT)

    private fun decodeConfirmCommand(value: String?): WearAppConfirmCommand? = runCatching {
        value?.let { encoded ->
            WearAppConfirmCommandCodec.decode(Base64.getDecoder().decode(encoded))
        }
    }.getOrNull()

    private fun decodeUndoCommand(value: String?): WearAppUndoCommand? = runCatching {
        value?.let { encoded ->
            WearAppUndoCommandCodec.decode(Base64.getDecoder().decode(encoded))
        }
    }.getOrNull()

    private fun decodeConfirmResult(value: String?): WearAppConfirmResult? = runCatching {
        value?.let { encoded ->
            WearAppConfirmResultCodec.decode(Base64.getDecoder().decode(encoded))
        }
    }.getOrNull()

    private fun decodeUndoResult(value: String?): WearAppUndoResult? = runCatching {
        value?.let { encoded ->
            WearAppUndoResultCodec.decode(Base64.getDecoder().decode(encoded))
        }
    }.getOrNull()

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
}

internal fun shouldClearUndoAfterAuthoritativeSnapshot(
    snapshot: WearAppSnapshot,
    pending: WearAppPendingUndo
): Boolean {
    val result = pending.terminalResult ?: return false
    if (
        result.resultType != WearAppUndoResultType.UNDONE &&
        result.resultType != WearAppUndoResultType.ALREADY_UNDONE
    ) return false
    val source = pending.sourceSnapshot
    val isNewer = when {
        snapshot.producerGeneration > source.producerGeneration -> true
        snapshot.producerGeneration < source.producerGeneration -> false
        snapshot.producerInstanceId != source.producerInstanceId ->
            WearAppSnapshotRules.isValid(snapshot)
        else -> snapshot.snapshotRevision > source.snapshotRevision
    }
    return isNewer && snapshot.recentDose?.eventId != pending.eventId
}
