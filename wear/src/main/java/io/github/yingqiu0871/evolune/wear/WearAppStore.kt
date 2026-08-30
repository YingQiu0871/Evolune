@file:Suppress("ApplySharedPref", "UseKtx")

package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Base64
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec

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

internal object WearAppStore {
    private const val PREFERENCES_NAME = "wear_app_snapshot"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_RECEIVED_AT = "received_at"
    private const val KEY_LAST_REQUESTED_AT = "last_requested_at"
    private const val KEY_PENDING_SINCE = "pending_since"
    private const val KEY_LAST_FAILURE_AT = "last_failure_at"
    private const val KEY_CONNECTION_STATE = "connection_state"
    private const val REQUEST_THROTTLE_MILLIS = 15_000L

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
        when (val result = classifyWearAppSnapshot(current, incoming)) {
            WearAppSnapshotApplyResult.Applied -> Unit
            else -> return result
        }
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        check(preferences(context).edit()
            .putString(KEY_PAYLOAD, encoded)
            .putLong(KEY_RECEIVED_AT, receivedAt)
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.CONNECTED.name)
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_LAST_FAILURE_AT)
            .commit())
        return WearAppSnapshotApplyResult.Applied
    }

    fun beginRequest(context: Context, nowMillis: Long): Boolean {
        val preferences = preferences(context)
        val lastRequestedAt = preferences.getLong(KEY_LAST_REQUESTED_AT, 0L)
        if (nowMillis - lastRequestedAt < REQUEST_THROTTLE_MILLIS) return false
        return preferences.edit()
            .putLong(KEY_LAST_REQUESTED_AT, nowMillis)
            .putLong(KEY_PENDING_SINCE, nowMillis)
            .remove(KEY_LAST_FAILURE_AT)
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.UNKNOWN.name)
            .commit()
    }

    fun markDispatched(context: Context) {
        preferences(context).edit()
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.CONNECTED.name)
            .commit()
    }

    fun markDisconnected(context: Context) {
        preferences(context).edit()
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.DISCONNECTED.name)
            .remove(KEY_PENDING_SINCE)
            .commit()
    }

    fun markFailure(context: Context, nowMillis: Long) {
        preferences(context).edit()
            .putLong(KEY_LAST_FAILURE_AT, nowMillis)
            .remove(KEY_PENDING_SINCE)
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.CONNECTED.name)
            .commit()
    }

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

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
}
