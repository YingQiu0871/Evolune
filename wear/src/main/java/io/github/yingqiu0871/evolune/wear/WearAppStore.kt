@file:Suppress("ApplySharedPref", "UseKtx")

package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Base64
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppRecentDose
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequest
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequestRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import java.time.Instant
import java.util.UUID

internal sealed interface WearAppSnapshotApplyResult {
    data object Applied : WearAppSnapshotApplyResult
    data object Duplicate : WearAppSnapshotApplyResult
    data object Older : WearAppSnapshotApplyResult
    data object Rejected : WearAppSnapshotApplyResult
}

internal data class WearAppSnapshotReducerState(
    val current: WearAppSnapshot?
)

internal data class WearAppSnapshotReduction(
    val state: WearAppSnapshotReducerState,
    val result: WearAppSnapshotApplyResult
)

internal fun reduceWearAppSnapshot(
    state: WearAppSnapshotReducerState,
    incoming: WearAppSnapshot
): WearAppSnapshotReduction {
    val current = state.current
    if (current == null) {
        return WearAppSnapshotReduction(
            state = WearAppSnapshotReducerState(incoming),
            result = WearAppSnapshotApplyResult.Applied
        )
    }
    if (incoming.producerInstanceId != current.producerInstanceId) {
        return if (WearAppSnapshotRules.compareProducers(incoming, current) > 0) {
            WearAppSnapshotReduction(
                state = WearAppSnapshotReducerState(incoming),
                result = WearAppSnapshotApplyResult.Applied
            )
        } else {
            WearAppSnapshotReduction(state, WearAppSnapshotApplyResult.Older)
        }
    }
    return when {
        incoming.snapshotRevision < current.snapshotRevision ->
            WearAppSnapshotReduction(state, WearAppSnapshotApplyResult.Older)
        incoming.snapshotRevision == current.snapshotRevision ->
            WearAppSnapshotReduction(
                state,
                if (incoming == current) {
                    WearAppSnapshotApplyResult.Duplicate
                } else {
                    WearAppSnapshotApplyResult.Rejected
                }
            )
        else -> WearAppSnapshotReduction(
            state = WearAppSnapshotReducerState(incoming),
            result = WearAppSnapshotApplyResult.Applied
        )
    }
}

internal fun classifyWearAppSnapshot(
    current: WearAppSnapshot?,
    incoming: WearAppSnapshot
): WearAppSnapshotApplyResult = reduceWearAppSnapshot(
    WearAppSnapshotReducerState(current),
    incoming
).result

internal object WearAppStore {
    private const val PREFERENCES_NAME = "wear_app_snapshot"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_RECEIVED_AT = "received_at"
    private const val KEY_LAST_REQUESTED_AT = "last_requested_at"
    private const val KEY_REQUEST_ID = "request_id"
    private const val KEY_PENDING_SINCE = "pending_since"
    private const val KEY_LAST_FAILURE_AT = "last_failure_at"
    private const val KEY_CONNECTION_STATE = "connection_state"
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
        // A Phone calculation timestamp from the future cannot be displayed as
        // fresh. Rejecting that snapshot preserves any last-known-good value.
        if (
            (
                incoming.concentrationState.status ==
                    io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus.AVAILABLE ||
                    incoming.concentrationState.status ==
                    io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus.STALE
            ) &&
            deriveWearAppConcentrationPresentation(incoming, receivedAt).state ==
                WearAppConcentrationDisplayState.UNAVAILABLE
        ) {
            return WearAppSnapshotApplyResult.Rejected
        }
        val current = getSnapshot(context)
        val reduction = reduceWearAppSnapshot(WearAppSnapshotReducerState(current), incoming)
        if (reduction.result != WearAppSnapshotApplyResult.Applied) return reduction.result
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        check(preferences(context).edit()
            .putString(KEY_PAYLOAD, encoded)
            .putLong(KEY_RECEIVED_AT, receivedAt)
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.CONNECTED.name)
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_LAST_FAILURE_AT)
            .remove(KEY_REQUEST_ID)
            .commit())
        WearAppConfirmationStore.clearAfterAuthoritativeSnapshot(context, incoming)
        notifyWearAppStateChanged(context)
        return WearAppSnapshotApplyResult.Applied
    }

    fun canConfirm(
        context: Context,
        snapshot: WearAppSnapshot,
        occurrenceId: UUID
    ): Boolean {
        if (!WearAppSnapshotRules.isValid(snapshot)) return false
        if (getSnapshot(context) != snapshot) return false
        if (getPresentation(context, System.currentTimeMillis()).state !=
            WearAppDisplayState.READY
        ) return false
        if (WearAppConfirmationStore.getPendingOperation(context) != null) return false
        return snapshot.upcomingOccurrences.any {
            it.occurrenceId == occurrenceId && (
                it.status == io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus.UPCOMING ||
                    it.status == io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus.DUE
                )
        }
    }

    fun canUndoRecentDose(
        context: Context,
        snapshot: WearAppSnapshot,
        eventId: UUID
    ): Boolean {
        if (!WearAppSnapshotRules.isValid(snapshot)) return false
        if (getSnapshot(context) != snapshot) return false
        if (getPresentation(context, System.currentTimeMillis()).state !=
            WearAppDisplayState.READY
        ) return false
        if (WearAppConfirmationStore.getPendingOperation(context) != null) return false
        return canUndoRecentDose(
            state = WearAppDisplayState.READY,
            snapshot = snapshot,
            recentDose = snapshot.recentDose,
            eventId = eventId
        )
    }

    fun beginRequest(context: Context, nowMillis: Long): Boolean {
        val preferences = preferences(context)
        val lastRequestedAt = preferences.getLong(KEY_LAST_REQUESTED_AT, 0L)
        if (shouldThrottleWearAppRequest(nowMillis, lastRequestedAt)) return false
        val committed = preferences.edit()
            .putLong(KEY_LAST_REQUESTED_AT, nowMillis)
            .putLong(KEY_PENDING_SINCE, nowMillis)
            .putString(KEY_REQUEST_ID, UUID.randomUUID().toString())
            .remove(KEY_LAST_FAILURE_AT)
            .putString(KEY_CONNECTION_STATE, WearAppConnectionState.UNKNOWN.name)
            .commit()
        if (committed) notifyWearAppStateChanged(context)
        return committed
    }

    fun getRequest(context: Context, requestedAt: Long): WearAppRequest? {
        if (requestedAt <= 0L) return null
        val requestId = runCatching {
            UUID.fromString(preferences(context).getString(KEY_REQUEST_ID, null) ?: return null)
        }.getOrNull() ?: return null
        val snapshot = getSnapshot(context)
        return WearAppRequest(
            protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
            requestId = requestId,
            observedProducerInstanceId = snapshot?.producerInstanceId,
            observedProducerGeneration = snapshot?.producerGeneration,
            observedSnapshotRevision = snapshot?.snapshotRevision,
            requestedAt = Instant.ofEpochMilli(requestedAt)
        ).takeIf(WearAppRequestRules::isValid)
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
            .remove(KEY_REQUEST_ID)
            .commit())
        notifyWearAppStateChanged(context)
    }

    fun markFailure(context: Context, nowMillis: Long) {
        check(preferences(context).edit()
            .putLong(KEY_LAST_FAILURE_AT, nowMillis)
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_REQUEST_ID)
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

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
}

internal fun canUndoRecentDose(
    state: WearAppDisplayState,
    snapshot: WearAppSnapshot?,
    recentDose: WearAppRecentDose?,
    eventId: UUID,
    pending: WearAppPendingOperation? = null
): Boolean = state == WearAppDisplayState.READY &&
    snapshot?.let(WearAppSnapshotRules::isValid) == true &&
    pending == null &&
    recentDose == snapshot.recentDose &&
    recentDose?.eventId == eventId &&
    recentDose.eventRevision?.let { it > 0L } == true

internal fun shouldThrottleWearAppRequest(nowMillis: Long, lastRequestedAt: Long): Boolean {
    if (lastRequestedAt <= 0L || nowMillis < lastRequestedAt) return false
    return nowMillis - lastRequestedAt < WearAppStore.REQUEST_THROTTLE_MILLIS
}
