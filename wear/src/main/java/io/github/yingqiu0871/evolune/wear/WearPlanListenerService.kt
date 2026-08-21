package io.github.yingqiu0871.evolune.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService

class WearPlanListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter {
                it.type == DataEvent.TYPE_CHANGED &&
                    it.dataItem.uri.path == PLANS_PATH
            }
            .forEach { event ->
                applySnapshot(event.dataItem)
            }

        TileService.getUpdater(this)
            .requestUpdate(DoseTileService::class.java)
    }

    private fun applySnapshot(dataItem: com.google.android.gms.wearable.DataItem) {
        val receivedAt = System.currentTimeMillis()
        val dataMap = runCatching {
            DataMapItem.fromDataItem(dataItem).dataMap
        }.getOrNull()
        if (dataMap == null) {
            rejectSnapshot(receivedAt)
            return
        }

        val plansJson = runCatching {
            if (dataMap.containsKey(KEY_PLANS_JSON)) {
                dataMap.getString(KEY_PLANS_JSON)
            } else {
                null
            }
        }.getOrNull()
        val current = runCatching {
            dataMap.getDouble(KEY_CURRENT_CONCENTRATION, Double.NaN)
                .takeIf { it.isFinite() }
        }.getOrNull()
        val curveValues = runCatching {
            dataMap.getFloatArray(KEY_CURVE_VALUES)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        val updatedAt = runCatching {
            dataMap.getLong(KEY_UPDATED_AT, 0L)
        }.getOrDefault(0L)

        when (
            val result = applyWearSnapshot(
                previousDashboard = WearPlanStore.getDashboard(this),
                plansJson = plansJson,
                currentConcentration = current,
                curveValues = curveValues,
                dashboardUpdatedAt = updatedAt
            )
        ) {
            is WearSnapshotApplyResult.Applied -> {
                val metadata = WearPlanStore.getSyncMetadata(this)
                if (!snapshotCompletesPending(metadata, result.dashboard.updatedAt)) {
                    Log.d(TAG, "Ignored dashboard older than pending request")
                    return
                }
                WearPlanStore.saveDashboard(
                    context = this,
                    plansJson = requireNotNull(plansJson),
                    dashboard = result.dashboard,
                    snapshotReceivedAt = receivedAt
                )
                Log.d(TAG, "Received ${result.dashboard.plans.size} plan(s) from phone")
            }
            is WearSnapshotApplyResult.Rejected -> rejectSnapshot(receivedAt)
        }
    }

    private fun rejectSnapshot(failedAt: Long) {
        WearPlanStore.markSyncFailure(
            context = this,
            failedAt = failedAt,
            connectionState = WearConnectionState.CONNECTED
        )
        Log.w(TAG, "Rejected invalid dashboard snapshot from phone")
    }

    private companion object {
        const val TAG = "HRTWearPlanListener"
        const val PLANS_PATH = "/hrt/plans"
        const val KEY_PLANS_JSON = "plans_json"
        const val KEY_CURRENT_CONCENTRATION = "current_concentration"
        const val KEY_CURVE_VALUES = "curve_values"
        const val KEY_UPDATED_AT = "dashboard_updated_at"
    }
}
