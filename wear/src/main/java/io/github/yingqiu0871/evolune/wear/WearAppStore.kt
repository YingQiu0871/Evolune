@file:Suppress("ApplySharedPref", "UseKtx")

package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Base64
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotRules
import java.util.UUID

internal sealed interface WearAppSnapshotApplyResult {
    data object Applied : WearAppSnapshotApplyResult
    data object Duplicate : WearAppSnapshotApplyResult
    data object Older : WearAppSnapshotApplyResult
    data object Rejected : WearAppSnapshotApplyResult
}

internal fun classifyWearAppSnapshot(
    current: WearAppSnapshot?,
    incoming: WearAppSnapshot
): WearAppSnapshotApplyResult {
    if (current == null) return WearAppSnapshotApplyResult.Applied
    if (incoming.producerInstanceId != current.producerInstanceId) {
        return if (WearAppSnapshotRules.compareProducers(incoming, current) > 0) {
            WearAppSnapshotApplyResult.Applied
        } else {
            WearAppSnapshotApplyResult.Older
        }
    }
    return when {
        incoming.snapshotRevision < current.snapshotRevision ->
            WearAppSnapshotApplyResult.Older
        incoming.snapshotRevision == current.snapshotRevision ->
            if (incoming == current) {
                WearAppSnapshotApplyResult.Duplicate
            } else {
                WearAppSnapshotApplyResult.Rejected
            }
        else -> WearAppSnapshotApplyResult.Applied
    }
}

/**
 * A producer not seen as retired is a valid reset candidate even if its
 * generation timestamp is lower because the Phone clock moved backwards.
 * Once a producer is retired, its delayed snapshots can never become current.
 */
internal fun shouldAcceptWearAppProducerSwitch(
    current: WearAppSnapshot?,
    incoming: WearAppSnapshot,
    retiredProducerIds: Set<UUID>
): Boolean =
    current != null &&
        current.producerInstanceId != incoming.producerInstanceId &&
        incoming.producerInstanceId !in retiredProducerIds

internal object WearAppStore {
    private const val PREFERENCES_NAME = "wear_app_snapshot"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_RECEIVED_AT = "received_at"
    private const val KEY_LAST_REQUESTED_AT = "last_requested_at"
    private const val KEY_PENDING_SINCE = "pending_since"
    private const val KEY_LAST_FAILURE_AT = "last_failure_at"
    private const val KEY_CONNECTION_STATE = "connection_state"
    private const val KEY_RETIRED_PRODUCER_IDS = "retired_producer_ids"
    internal const val REQUEST_THROTTLE_MILLIS = 15_000L

    fun getSnapshot(context: Context): WearAppSnapshot? {
        val encoded = preferences(context).getString(KEY_PAYLOAD, null) ?: return null
        val payload = runCatching {
            Base64.decode(encoded, Base64.DEFAULT)
        }.getOrNull() ?: return null
        return WearAppSnapshotCodec.decode(payload)
    }

    fun getPresentation(context: Context, nowMillis: Long): WearAppPresentation =
        deriveWearAppPresentation(
            snapshot = getSnapshot(context),
            metadata = getMetadata(context),
            nowMillis = nowMillis
        )

    fun acceptSnapshot(
        context: Context,
        payload: ByteArray,
        receivedAt: Long
    ): WearAppSnapshotApplyResult {
        val incoming = WearAppSnapshotCodec.decode(payload)
            ?: return WearAppSnapshotApplyResult.Rejected
        val current = getSnapshot(context)
        val retiredProducerIds = getRetiredProducerIds(context)
        if (incoming.producerInstanceId in retiredProducerIds) {
            return WearAppSnapshotApplyResult.Older
        }
        val classification = classifyWearAppSnapshot(current, incoming)
        when (classification) {
            WearAppSnapshotApplyResult.Applied -> Unit
            WearAppSnapshotApplyResult.Older -> {
                if (!shouldAcceptWearAppProducerSwitch(current, incoming, retiredProducerIds)) {
                    return classification
                }
            }
            else -> return classification
        }
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        val updatedRetiredProducerIds = if (
            current != null && current.producerInstanceId != incoming.producerInstanceId
        ) {
            retiredProducerIds + current.producerInstanceId
        } else {
            retiredProducerIds
        }
        check(preferences(context).edit()
            .putString(KEY_PAYLOAD, encoded)
            .putLong(KEY_RECEIVED_AT, receivedAt)
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.CONNECTED.name)
            .putString(
                KEY_RETIRED_PRODUCER_IDS,
                updatedRetiredProducerIds.joinToString(",")
            )
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_LAST_FAILURE_AT)
            .commit())
        notifyWearAppStateChanged(context)
        return WearAppSnapshotApplyResult.Applied
    }

    fun beginRequest(context: Context, nowMillis: Long): Boolean {
        val preferences = preferences(context)
        val lastRequestedAt = preferences.getLong(KEY_LAST_REQUESTED_AT, 0L)
        if (shouldThrottleWearAppRequest(nowMillis, lastRequestedAt)) return false
        val committed = preferences.edit()
            .putLong(KEY_LAST_REQUESTED_AT, nowMillis)
            .putLong(KEY_PENDING_SINCE, nowMillis)
            .remove(KEY_LAST_FAILURE_AT)
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.UNKNOWN.name)
            .commit()
        if (committed) notifyWearAppStateChanged(context)
        return committed
    }

    fun markDispatched(context: Context) {
        check(preferences(context).edit()
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.CONNECTED.name)
            .commit())
        notifyWearAppStateChanged(context)
    }

    fun markDisconnected(context: Context) {
        check(preferences(context).edit()
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.DISCONNECTED.name)
            .remove(KEY_PENDING_SINCE)
            .commit())
        notifyWearAppStateChanged(context)
    }

    fun markFailure(context: Context, nowMillis: Long) {
        check(preferences(context).edit()
            .putLong(KEY_LAST_FAILURE_AT, nowMillis)
            .remove(KEY_PENDING_SINCE)
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.CONNECTED.name)
            .commit())
        notifyWearAppStateChanged(context)
    }

    fun getNextRefreshDeadline(context: Context, nowMillis: Long): Long? =
        nextWearAppRefreshDeadline(nowMillis, getMetadata(context), getSnapshot(context))

    private fun getMetadata(context: Context): WearAppCacheMetadata {
        val preferences = preferences(context)
        return WearAppCacheMetadata(
            receivedAt = preferences.getLong(KEY_RECEIVED_AT, 0L),
            lastRequestedAt = preferences.getLong(KEY_LAST_REQUESTED_AT, 0L),
            pendingSince = preferences.getLong(KEY_PENDING_SINCE, 0L),
            lastFailureAt = preferences.getLong(KEY_LAST_FAILURE_AT, 0L),
            connectionState = runCatching {
                WearAppConnectionState.valueOf(
                    preferences.getString(
                        KEY_CONNECTION_STATE,
                        WearAppConnectionState.UNKNOWN.name
                    ) ?: WearAppConnectionState.UNKNOWN.name
                )
            }.getOrDefault(WearAppConnectionState.UNKNOWN)
        )
    }

    private fun getRetiredProducerIds(context: Context): Set<UUID> =
        preferences(context)
            .getString(KEY_RETIRED_PRODUCER_IDS, null)
            .orEmpty()
            .split(',')
            .asSequence()
            .filter(String::isNotBlank)
            .mapNotNull { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            .toSet()

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
}

internal fun shouldThrottleWearAppRequest(nowMillis: Long, lastRequestedAt: Long): Boolean {
    if (lastRequestedAt <= 0L || nowMillis < lastRequestedAt) return false
    return nowMillis - lastRequestedAt < WearAppStore.REQUEST_THROTTLE_MILLIS
}
