package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot

internal const val WEAR_APP_SYNC_TIMEOUT_MILLIS = 30_000L
internal const val WEAR_APP_STALE_AFTER_MILLIS = 15 * 60_000L

internal enum class WearAppConnectionState {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED
}

internal data class WearAppCacheMetadata(
    val receivedAt: Long,
    val lastRequestedAt: Long,
    val pendingSince: Long,
    val lastFailureAt: Long,
    val connectionState: WearAppConnectionState
)

internal enum class WearAppDisplayState {
    WAITING_FOR_PHONE,
    SYNCING,
    READY,
    EMPTY,
    OFFLINE,
    STALE,
    ERROR
}

internal data class WearAppPresentation(
    val snapshot: WearAppSnapshot?,
    val state: WearAppDisplayState
)

internal fun deriveWearAppPresentation(
    snapshot: WearAppSnapshot?,
    metadata: WearAppCacheMetadata,
    nowMillis: Long
): WearAppPresentation {
    if (metadata.connectionState == WearAppConnectionState.DISCONNECTED) {
        return WearAppPresentation(snapshot, WearAppDisplayState.OFFLINE)
    }
    if (metadata.pendingSince > 0L) {
        val age = nowMillis - metadata.pendingSince
        return if (age in 0 until WEAR_APP_SYNC_TIMEOUT_MILLIS) {
            WearAppPresentation(snapshot, WearAppDisplayState.SYNCING)
        } else {
            WearAppPresentation(snapshot, WearAppDisplayState.ERROR)
        }
    }
    if (metadata.lastFailureAt > 0L) {
        return WearAppPresentation(snapshot, WearAppDisplayState.ERROR)
    }
    if (snapshot == null || metadata.receivedAt <= 0L) {
        return WearAppPresentation(snapshot, WearAppDisplayState.WAITING_FOR_PHONE)
    }
    if (nowMillis - metadata.receivedAt !in 0..WEAR_APP_STALE_AFTER_MILLIS) {
        return WearAppPresentation(snapshot, WearAppDisplayState.STALE)
    }
    return WearAppPresentation(
        snapshot = snapshot,
        state = if (snapshot.overallStatus == WearAppOverallStatus.EMPTY) {
            WearAppDisplayState.EMPTY
        } else {
            WearAppDisplayState.READY
        }
    )
}
