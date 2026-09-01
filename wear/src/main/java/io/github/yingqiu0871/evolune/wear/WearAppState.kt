package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.content.Intent
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import java.util.Locale

internal const val WEAR_APP_SYNC_TIMEOUT_MILLIS = 30_000L
internal const val WEAR_APP_STALE_AFTER_MILLIS = 15 * 60_000L
/** Independent freshness window for the Phone-calculated concentration. */
internal const val WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS = 15 * 60_000L
internal const val WEAR_APP_STATE_CHANGED_ACTION =
    "io.github.yingqiu0871.evolune.wear.ACTION_STATE_CHANGED"

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

internal enum class WearAppConcentrationDisplayState {
    FRESH,
    STALE,
    UNAVAILABLE
}

internal data class WearAppConcentrationPresentation(
    val state: WearAppConcentrationDisplayState,
    val value: Double? = null,
    val unit: String? = null,
    val calculatedAtMillis: Long? = null
)

internal fun formatWearAppConcentration(value: Double, unit: String): String =
    String.format(
        Locale.ROOT,
        if (value != 0.0 && (value < 0.01 || value >= 1_000_000.0)) {
            "%.2e %s"
        } else {
            "%.2f %s"
        },
        value,
        unit
    )

internal fun deriveWearAppConcentrationPresentation(
    snapshot: WearAppSnapshot?,
    nowMillis: Long
): WearAppConcentrationPresentation {
    val concentration = snapshot?.concentrationState
        ?: return WearAppConcentrationPresentation(WearAppConcentrationDisplayState.UNAVAILABLE)
    if (concentration.status != io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus.AVAILABLE &&
        concentration.status != io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus.STALE
    ) {
        return WearAppConcentrationPresentation(WearAppConcentrationDisplayState.UNAVAILABLE)
    }
    val value = concentration.value
    val calculatedAtMillis = concentration.calculatedAt
        ?.let { runCatching { it.toEpochMilli() }.getOrNull() }
    if (
        value == null ||
        !value.isFinite() ||
        value < 0.0 ||
        concentration.unit != io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotRules.CONCENTRATION_UNIT_PG_ML ||
        calculatedAtMillis == null ||
        calculatedAtMillis <= 0L ||
        nowMillis < calculatedAtMillis
    ) {
        return WearAppConcentrationPresentation(WearAppConcentrationDisplayState.UNAVAILABLE)
    }
    val ageMillis = nowMillis - calculatedAtMillis
    val state = if (
        concentration.status == io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus.STALE ||
        ageMillis >= WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS
    ) {
        WearAppConcentrationDisplayState.STALE
    } else {
        WearAppConcentrationDisplayState.FRESH
    }
    return WearAppConcentrationPresentation(state, value, concentration.unit, calculatedAtMillis)
}

/**
 * Returns the next wall-clock boundary that can change the displayed state.
 * No periodic polling is needed: pending requests wake at timeout and a
 * received snapshot wakes at the freshness boundary.
 */
internal fun nextWearAppRefreshDeadline(
    nowMillis: Long,
    metadata: WearAppCacheMetadata,
    snapshot: WearAppSnapshot?
): Long? {
    val deadlines = buildList {
        futureDeadline(metadata.pendingSince, WEAR_APP_SYNC_TIMEOUT_MILLIS, nowMillis)
            ?.let(::add)
        if (snapshot != null) {
            futureDeadline(metadata.receivedAt, WEAR_APP_STALE_AFTER_MILLIS, nowMillis)
                ?.let(::add)
            val concentration = deriveWearAppConcentrationPresentation(snapshot, nowMillis)
            if (concentration.state == WearAppConcentrationDisplayState.FRESH) {
                futureDeadline(
                    requireNotNull(concentration.calculatedAtMillis),
                    WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS,
                    nowMillis
                )?.let(::add)
            }
        }
    }
    return deadlines.minOrNull()
}

internal fun shouldHandleWearAppRotaryScroll(
    isVisible: Boolean,
    isRotaryEncoder: Boolean,
    isScrollAction: Boolean,
    axisValue: Float
): Boolean =
    isVisible && isRotaryEncoder && isScrollAction && axisValue.isFinite() && axisValue != 0f

internal fun shouldRunWearAppRefreshCallback(isVisible: Boolean): Boolean = isVisible

internal fun notifyWearAppStateChanged(context: Context) {
    context.sendBroadcast(
        Intent(WEAR_APP_STATE_CHANGED_ACTION).setPackage(context.packageName)
    )
}

internal fun deriveWearAppPresentation(
    snapshot: WearAppSnapshot?,
    metadata: WearAppCacheMetadata,
    nowMillis: Long
): WearAppPresentation {
    if (metadata.connectionState == WearAppConnectionState.DISCONNECTED) {
        return WearAppPresentation(snapshot, WearAppDisplayState.OFFLINE)
    }
    if (metadata.pendingSince > 0L) {
        val age = elapsedSince(nowMillis, metadata.pendingSince)
        return if (age == null || age < WEAR_APP_SYNC_TIMEOUT_MILLIS) {
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
    val age = elapsedSince(nowMillis, metadata.receivedAt)
    if (age == null || age >= WEAR_APP_STALE_AFTER_MILLIS) {
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

private fun elapsedSince(nowMillis: Long, thenMillis: Long): Long? =
    if (nowMillis < thenMillis) null else nowMillis - thenMillis

private fun futureDeadline(startMillis: Long, durationMillis: Long, nowMillis: Long): Long? {
    if (startMillis <= 0L) return null
    val deadline = if (startMillis > Long.MAX_VALUE - durationMillis) {
        Long.MAX_VALUE
    } else {
        startMillis + durationMillis
    }
    return deadline.takeIf { it > nowMillis }
}
