package io.github.yingqiu0871.evolune.wear

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.UUID

internal const val REQUEST_THROTTLE_MILLIS = 15_000L
internal const val SYNC_TIMEOUT_MILLIS = 30_000L
internal const val STALE_AFTER_MILLIS = 15 * 60_000L

enum class WearDashboardState(val actionsEnabled: Boolean) {
    WAITING_FOR_PHONE(false),
    NOT_CONNECTED(false),
    SYNC_PENDING(false),
    SYNC_FAILED(false),
    STALE_CACHE(false),
    NO_ENABLED_PLANS(false),
    READY(true)
}

internal enum class WearConnectionState {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED
}

internal data class WearSyncMetadata(
    val hasValidSnapshot: Boolean,
    val snapshotReceivedAt: Long,
    val connectionState: WearConnectionState,
    val pendingSince: Long,
    val pendingAfterDashboardUpdatedAt: Long,
    val lastFailureAt: Long
)

internal sealed interface WearSnapshotApplyResult {
    data class Applied(val dashboard: WearDashboard) : WearSnapshotApplyResult
    data class Rejected(
        val retainedDashboard: WearDashboard?
    ) : WearSnapshotApplyResult
}

internal fun applyWearSnapshot(
    previousDashboard: WearDashboard?,
    plansJson: String?,
    currentConcentration: Double?,
    curveValues: List<Float>,
    dashboardUpdatedAt: Long
): WearSnapshotApplyResult {
    if (plansJson == null || dashboardUpdatedAt <= 0L) {
        return WearSnapshotApplyResult.Rejected(previousDashboard)
    }

    val plans = parseWearPlans(plansJson)
        ?: return WearSnapshotApplyResult.Rejected(previousDashboard)
    return WearSnapshotApplyResult.Applied(
        WearDashboard(
            plans = plans,
            currentConcentration = currentConcentration,
            curveValues = curveValues,
            updatedAt = dashboardUpdatedAt
        )
    )
}

internal fun deriveWearDashboardState(
    dashboard: WearDashboard,
    metadata: WearSyncMetadata,
    nowMillis: Long
): WearDashboardState {
    if (metadata.connectionState == WearConnectionState.DISCONNECTED) {
        return WearDashboardState.NOT_CONNECTED
    }
    if (metadata.pendingSince > 0L) {
        val pendingAge = nowMillis - metadata.pendingSince
        return if (pendingAge in 0 until SYNC_TIMEOUT_MILLIS) {
            WearDashboardState.SYNC_PENDING
        } else {
            WearDashboardState.SYNC_FAILED
        }
    }
    if (metadata.lastFailureAt > 0L) {
        return WearDashboardState.SYNC_FAILED
    }
    if (metadata.hasValidSnapshot && dashboard.updatedAt <= 0L) {
        return WearDashboardState.SYNC_FAILED
    }
    if (!metadata.hasValidSnapshot || metadata.snapshotReceivedAt <= 0L) {
        return WearDashboardState.WAITING_FOR_PHONE
    }

    val snapshotAge = nowMillis - metadata.snapshotReceivedAt
    if (snapshotAge !in 0..STALE_AFTER_MILLIS) {
        return WearDashboardState.STALE_CACHE
    }
    return if (dashboard.plans.isEmpty()) {
        WearDashboardState.NO_ENABLED_PLANS
    } else {
        WearDashboardState.READY
    }
}

internal fun WearDashboardState.displayMessage(): String? = when (this) {
    WearDashboardState.WAITING_FOR_PHONE -> "正在等待手机端数据"
    WearDashboardState.NOT_CONNECTED -> "未连接到手机"
    WearDashboardState.SYNC_PENDING -> "正在同步用药方案"
    WearDashboardState.SYNC_FAILED -> "同步失败，请重试"
    WearDashboardState.STALE_CACHE -> "数据可能已过期"
    WearDashboardState.NO_ENABLED_PLANS -> "请先在手机端启用用药方案"
    WearDashboardState.READY -> null
}

internal fun canSendDoseAction(
    state: WearDashboardState,
    selectedPlanId: String?,
    plans: List<WearPlan>
): Boolean =
    state.actionsEnabled &&
        !selectedPlanId.isNullOrBlank() &&
        plans.any { it.id == selectedPlanId }

internal fun snapshotCompletesPending(
    metadata: WearSyncMetadata,
    dashboardUpdatedAt: Long
): Boolean =
    metadata.pendingSince <= 0L ||
        dashboardUpdatedAt > metadata.pendingAfterDashboardUpdatedAt

private fun parseWearPlans(plansJson: String): List<WearPlan>? {
    val array = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(plansJson) as? JsonArray
    }.getOrNull() ?: return null

    val plans = buildList {
        array.forEach { element ->
            val item = element as? JsonObject ?: return null
            val idValue = item["id"] as? JsonPrimitive ?: return null
            val nameValue = item["name"] as? JsonPrimitive ?: return null
            val doseValue = item["doseMG"] as? JsonPrimitive ?: return null
            if (!idValue.isString || !nameValue.isString || doseValue.isString) {
                return null
            }
            val id = idValue.content
            val dose = doseValue.doubleOrNull ?: return null
            if (runCatching { UUID.fromString(id) }.isFailure || !dose.isFinite()) {
                return null
            }
            add(WearPlan(id = id, name = nameValue.content, doseMG = dose))
        }
    }
    return plans.take(2)
}
