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
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotRules
import io.github.yingqiu0871.evolune.experience.wear.operationIdFromWearAppResultPath
import io.github.yingqiu0871.evolune.experience.wear.wearAppCommandPath
import java.util.Base64

internal data class WearAppPendingConfirmation(
    val command: WearAppConfirmCommand,
    val commandDataItemUri: String,
    val terminalResult: WearAppConfirmResult?,
    val sendAttempt: Long
) {
    val operationId get() = command.operationId
    val occurrenceId get() = command.occurrenceId
    val commandCreatedAt get() = command.createdAt
    val sourceSnapshot get() = command.sourceSnapshot
    val awaitingAuthoritativeSnapshot: Boolean
        get() = terminalResult?.resultType == WearAppConfirmResultType.CONFIRMED ||
            terminalResult?.resultType == WearAppConfirmResultType.ALREADY_CONFIRMED
}

internal enum class WearAppResultApply {
    Applied,
    Duplicate,
    Rejected
}

internal object WearAppConfirmationStore {
    private const val PREFERENCES_NAME = "wear_app_confirmations"
    private const val KEY_COMMAND = "command"
    private const val KEY_COMMAND_URI = "command_uri"
    private const val KEY_RESULT = "result"
    private const val KEY_SEND_ATTEMPT = "send_attempt"
    private const val KEY_LAST_RESULT = "last_result"

    @Synchronized
    fun getPending(context: Context): WearAppPendingConfirmation? = readPending(context)

    @Synchronized
    fun beginOrReuse(
        context: Context,
        command: WearAppConfirmCommand
    ): WearAppPendingConfirmation? {
        val existing = readPending(context)
        if (existing != null) {
            return existing.takeIf { it.command == command }
        }
        val payload = Base64.getEncoder().encodeToString(
            WearAppConfirmCommandCodec.encode(command)
        )
        val uri = wearAppCommandPath(command.operationId)
        val committed = preferences(context).edit()
            .putString(KEY_COMMAND, payload)
            .putString(KEY_COMMAND_URI, uri)
            .putLong(KEY_SEND_ATTEMPT, 0L)
            .remove(KEY_RESULT)
            .commit()
        return if (committed) readPending(context) else null
    }

    @Synchronized
    fun clearPendingIfOperation(context: Context, operationId: java.util.UUID): Boolean {
        val pending = readPending(context) ?: return false
        if (pending.operationId != operationId) return false
        return preferences(context).edit()
            .remove(KEY_COMMAND)
            .remove(KEY_COMMAND_URI)
            .remove(KEY_RESULT)
            .remove(KEY_SEND_ATTEMPT)
            .commit()
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
        val previousResult = decodeResult(preferences.getString(KEY_LAST_RESULT, null))
        if (previousResult != null && previousResult.operationId == result.operationId) {
            return if (previousResult == result) {
                WearAppResultApply.Duplicate
            } else {
                WearAppResultApply.Rejected
            }
        }

        val pending = readPending(context) ?: return WearAppResultApply.Rejected
        if (
            pending.operationId != result.operationId ||
            pending.occurrenceId != result.occurrenceId
        ) return WearAppResultApply.Rejected

        val encoded = Base64.getEncoder().encodeToString(
            WearAppConfirmResultCodec.encode(result)
        )
        val editor = preferences.edit()
            .putString(KEY_LAST_RESULT, encoded)
        when (result.resultType) {
            WearAppConfirmResultType.CONFIRMED,
            WearAppConfirmResultType.ALREADY_CONFIRMED,
            WearAppConfirmResultType.RETRYABLE_STORAGE_FAILURE -> {
                editor.putString(KEY_RESULT, encoded)
            }
            else -> {
                editor.remove(KEY_COMMAND).remove(KEY_COMMAND_URI).remove(KEY_RESULT)
                    .remove(KEY_SEND_ATTEMPT)
            }
        }
        return if (editor.commit()) {
            WearAppResultApply.Applied
        } else {
            WearAppResultApply.Rejected
        }
    }

    @Synchronized
    fun clearAfterAuthoritativeSnapshot(context: Context, snapshot: WearAppSnapshot) {
        val pending = readPending(context) ?: return
        val result = pending.terminalResult ?: return
        if (
            result.resultType != WearAppConfirmResultType.CONFIRMED &&
            result.resultType != WearAppConfirmResultType.ALREADY_CONFIRMED
        ) return
        if (!isNewerSnapshot(snapshot, pending)) return
        val isReflectedByRecent = snapshot.recentDose?.eventId == result.eventId
        val isAbsentFromUpcoming = snapshot.upcomingOccurrences.none {
            it.occurrenceId == pending.occurrenceId
        }
        if (!isReflectedByRecent && !isAbsentFromUpcoming) return
        preferences(context).edit()
            .remove(KEY_COMMAND)
            .remove(KEY_COMMAND_URI)
            .remove(KEY_RESULT)
            .remove(KEY_SEND_ATTEMPT)
            .commit()
    }

    @Synchronized
    fun nextSendAttempt(context: Context, operationId: java.util.UUID): Long? {
        val pending = readPending(context) ?: return null
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
        decodeResult(preferences(context).getString(KEY_LAST_RESULT, null))?.messageCode

    private fun readPending(context: Context): WearAppPendingConfirmation? {
        val preferences = preferences(context)
        val command = decodeCommand(preferences.getString(KEY_COMMAND, null)) ?: return null
        val uri = preferences.getString(KEY_COMMAND_URI, null)
            ?.takeIf { it == wearAppCommandPath(command.operationId) }
            ?: return null
        return WearAppPendingConfirmation(
            command = command,
            commandDataItemUri = uri,
            sendAttempt = preferences.getLong(KEY_SEND_ATTEMPT, 0L),
            terminalResult = decodeResult(preferences.getString(KEY_RESULT, null))
                ?.takeIf {
                    it.operationId == command.operationId &&
                        it.occurrenceId == command.occurrenceId
                }
        )
    }

    private fun isNewerSnapshot(
        snapshot: WearAppSnapshot,
        pending: WearAppPendingConfirmation
    ): Boolean {
        val source = pending.sourceSnapshot
        return when {
            snapshot.producerGeneration > source.producerGeneration -> true
            snapshot.producerGeneration < source.producerGeneration -> false
            snapshot.producerInstanceId != source.producerInstanceId ->
                WearAppSnapshotRules.isValid(snapshot)
            else -> snapshot.snapshotRevision > source.snapshotRevision
        }
    }

    private fun decodeCommand(value: String?): WearAppConfirmCommand? = runCatching {
        value?.let { encoded ->
            WearAppConfirmCommandCodec.decode(Base64.getDecoder().decode(encoded))
        }
    }.getOrNull()

    private fun decodeResult(value: String?): WearAppConfirmResult? = runCatching {
        value?.let { encoded ->
            WearAppConfirmResultCodec.decode(Base64.getDecoder().decode(encoded))
        }
    }.getOrNull()

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
}
