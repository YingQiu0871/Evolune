package io.github.yingqiu0871.evolune.wear

import android.content.Context

data class WearPlan(
    val id: String,
    val name: String,
    val doseMG: Double
)

data class WearDashboard(
    val plans: List<WearPlan>,
    val currentConcentration: Double?,
    val curveValues: List<Float>,
    val updatedAt: Long
) {
    fun concentrationAt(nowMillis: Long): Double? {
        if (curveValues.isEmpty() || updatedAt <= 0L) {
            return currentConcentration
        }
        val centerIndex = curveValues.lastIndex / 2.0
        val elapsedHours = (nowMillis - updatedAt) / 3_600_000.0
        val position = (centerIndex + elapsedHours)
            .coerceIn(0.0, curveValues.lastIndex.toDouble())
        val lower = position.toInt()
        val upper = minOf(lower + 1, curveValues.lastIndex)
        val ratio = position - lower
        return curveValues[lower] +
            (curveValues[upper] - curveValues[lower]) * ratio
    }

    fun currentCurvePosition(nowMillis: Long): Float {
        if (curveValues.size <= 1 || updatedAt <= 0L) return 0.5f
        val centerIndex = curveValues.lastIndex / 2.0
        val elapsedHours = (nowMillis - updatedAt) / 3_600_000.0
        return ((centerIndex + elapsedHours) / curveValues.lastIndex)
            .coerceIn(0.0, 1.0)
            .toFloat()
    }
}

object WearPlanStore {
    private const val PREFERENCES_NAME = "wear_plans"
    private const val KEY_PLANS_JSON = "plans_json"
    private const val KEY_LAST_SENT_PLAN_ID = "last_sent_plan_id"
    private const val KEY_LAST_SENT_AT = "last_sent_at"
    private const val KEY_LAST_REQUESTED_AT = "last_requested_at"
    private const val KEY_CURRENT_CONCENTRATION = "current_concentration"
    private const val KEY_CURVE_VALUES = "curve_values"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_SNAPSHOT_RECEIVED_AT = "snapshot_received_at"
    private const val KEY_CONNECTION_STATE = "connection_state"
    private const val KEY_PENDING_SINCE = "pending_since"
    private const val KEY_PENDING_AFTER_UPDATED_AT = "pending_after_updated_at"
    private const val KEY_LAST_FAILURE_AT = "last_failure_at"

    fun saveDashboard(
        context: Context,
        plansJson: String,
        dashboard: WearDashboard,
        snapshotReceivedAt: Long
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLANS_JSON, plansJson)
            .apply {
                if (dashboard.currentConcentration != null) {
                    putString(
                        KEY_CURRENT_CONCENTRATION,
                        dashboard.currentConcentration.toString()
                    )
                } else {
                    remove(KEY_CURRENT_CONCENTRATION)
                }
                putString(
                    KEY_CURVE_VALUES,
                    dashboard.curveValues.joinToString(",")
                )
                putLong(KEY_UPDATED_AT, dashboard.updatedAt)
                putLong(KEY_SNAPSHOT_RECEIVED_AT, snapshotReceivedAt)
                putString(KEY_CONNECTION_STATE, WearConnectionState.CONNECTED.name)
                remove(KEY_PENDING_SINCE)
                remove(KEY_PENDING_AFTER_UPDATED_AT)
                remove(KEY_LAST_FAILURE_AT)
            }
            .apply()
    }

    fun getDashboard(context: Context): WearDashboard {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val current = preferences
            .getString(KEY_CURRENT_CONCENTRATION, null)
            ?.toDoubleOrNull()
        val curve = preferences
            .getString(KEY_CURVE_VALUES, null)
            ?.split(',')
            ?.mapNotNull { it.toFloatOrNull() }
            .orEmpty()
        return when (
            val result = applyWearSnapshot(
                previousDashboard = null,
                plansJson = preferences.getString(KEY_PLANS_JSON, null),
                currentConcentration = current,
                curveValues = curve,
                dashboardUpdatedAt = preferences.getLong(KEY_UPDATED_AT, 0L)
            )
        ) {
            is WearSnapshotApplyResult.Applied -> result.dashboard
            is WearSnapshotApplyResult.Rejected -> WearDashboard(
                plans = emptyList(),
                currentConcentration = null,
                curveValues = emptyList(),
                updatedAt = 0L
            )
        }
    }

    fun getPlans(context: Context): List<WearPlan> =
        getDashboard(context).plans

    fun getPresentationState(
        context: Context,
        dashboard: WearDashboard,
        nowMillis: Long
    ): WearDashboardState = deriveWearDashboardState(
        dashboard = dashboard,
        metadata = getSyncMetadata(context),
        nowMillis = nowMillis
    )

    internal fun getSyncMetadata(context: Context): WearSyncMetadata {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val snapshotReceivedAt = preferences.getLong(KEY_SNAPSHOT_RECEIVED_AT, 0L)
        return WearSyncMetadata(
            hasValidSnapshot = snapshotReceivedAt > 0L,
            snapshotReceivedAt = snapshotReceivedAt,
            connectionState = runCatching {
                WearConnectionState.valueOf(
                    preferences.getString(
                        KEY_CONNECTION_STATE,
                        WearConnectionState.UNKNOWN.name
                    ) ?: WearConnectionState.UNKNOWN.name
                )
            }.getOrDefault(WearConnectionState.UNKNOWN),
            pendingSince = preferences.getLong(KEY_PENDING_SINCE, 0L),
            pendingAfterDashboardUpdatedAt = preferences.getLong(
                KEY_PENDING_AFTER_UPDATED_AT,
                0L
            ),
            lastFailureAt = preferences.getLong(KEY_LAST_FAILURE_AT, 0L)
        )
    }

    fun markConnected(context: Context): Boolean {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val wasConnected = preferences.getString(
            KEY_CONNECTION_STATE,
            WearConnectionState.UNKNOWN.name
        ) == WearConnectionState.CONNECTED.name
        if (!wasConnected) {
            preferences.edit()
                .putString(KEY_CONNECTION_STATE, WearConnectionState.CONNECTED.name)
                .apply()
        }
        return !wasConnected
    }

    fun markNotConnected(context: Context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONNECTION_STATE, WearConnectionState.DISCONNECTED.name)
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_PENDING_AFTER_UPDATED_AT)
            .remove(KEY_LAST_FAILURE_AT)
            .apply()
    }

    fun markSyncPending(
        context: Context,
        pendingSince: Long,
        pendingAfterDashboardUpdatedAt: Long
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONNECTION_STATE, WearConnectionState.CONNECTED.name)
            .putLong(KEY_PENDING_SINCE, pendingSince)
            .putLong(
                KEY_PENDING_AFTER_UPDATED_AT,
                pendingAfterDashboardUpdatedAt
            )
            .remove(KEY_LAST_FAILURE_AT)
            .apply()
    }

    internal fun markSyncFailure(
        context: Context,
        failedAt: Long,
        connectionState: WearConnectionState
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONNECTION_STATE, connectionState.name)
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_PENDING_AFTER_UPDATED_AT)
            .putLong(KEY_LAST_FAILURE_AT, failedAt)
            .apply()
    }

    fun markSyncFailureIfPending(
        context: Context,
        expectedPendingSince: Long,
        failedAt: Long
    ): Boolean {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        if (preferences.getLong(KEY_PENDING_SINCE, 0L) != expectedPendingSince) {
            return false
        }
        preferences.edit()
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_PENDING_AFTER_UPDATED_AT)
            .putLong(KEY_LAST_FAILURE_AT, failedAt)
            .apply()
        return true
    }

    fun completePendingIfNewerSnapshot(
        context: Context,
        expectedPendingSince: Long
    ): Boolean {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        if (preferences.getLong(KEY_PENDING_SINCE, 0L) != expectedPendingSince) {
            return false
        }
        val pendingAfterUpdatedAt = preferences.getLong(
            KEY_PENDING_AFTER_UPDATED_AT,
            0L
        )
        if (preferences.getLong(KEY_UPDATED_AT, 0L) <= pendingAfterUpdatedAt) {
            return false
        }
        preferences.edit()
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_PENDING_AFTER_UPDATED_AT)
            .apply()
        return true
    }

    fun markTimedOutIfPending(
        context: Context,
        expectedPendingSince: Long,
        nowMillis: Long
    ): Boolean {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val pendingSince = preferences.getLong(KEY_PENDING_SINCE, 0L)
        if (
            pendingSince != expectedPendingSince ||
            nowMillis - pendingSince < SYNC_TIMEOUT_MILLIS
        ) {
            return false
        }
        preferences.edit()
            .remove(KEY_PENDING_SINCE)
            .remove(KEY_PENDING_AFTER_UPDATED_AT)
            .putLong(KEY_LAST_FAILURE_AT, nowMillis)
            .apply()
        return true
    }

    fun markSent(context: Context, planId: String, sentAt: Long) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SENT_PLAN_ID, planId)
            .putLong(KEY_LAST_SENT_AT, sentAt)
            .apply()
    }

    fun recentSentPlanId(
        context: Context,
        nowMillis: Long,
        maxAgeMillis: Long
    ): String? {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val sentAt = preferences.getLong(KEY_LAST_SENT_AT, 0L)
        if (nowMillis - sentAt !in 0..maxAgeMillis) return null
        return preferences.getString(KEY_LAST_SENT_PLAN_ID, null)
    }

    fun clearSentFeedback(context: Context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_SENT_PLAN_ID)
            .remove(KEY_LAST_SENT_AT)
            .apply()
    }

    fun shouldRequestPlans(context: Context, nowMillis: Long): Boolean {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val lastRequestedAt = preferences.getLong(KEY_LAST_REQUESTED_AT, 0L)
        if (nowMillis - lastRequestedAt < REQUEST_THROTTLE_MILLIS) return false
        markPlansRequested(context, nowMillis)
        return true
    }

    fun markPlansRequested(context: Context, nowMillis: Long) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_REQUESTED_AT, nowMillis)
            .apply()
    }
}
