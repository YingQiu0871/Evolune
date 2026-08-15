package io.github.yingqiu0871.evolune.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable

private const val REQUEST_PLANS_PATH = "/hrt/request-plans"

object WearSyncManager {
    fun requestPlansFromPhone(context: Context, force: Boolean = false) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    WearPlanStore.markNotConnected(context)
                    Log.w(TAG, "No connected phone node")
                    requestTileUpdate(context)
                    return@addOnSuccessListener
                }
                val requestAt = System.currentTimeMillis()
                val wasDisconnected = WearPlanStore.getSyncMetadata(context)
                    .connectionState == WearConnectionState.DISCONNECTED
                val shouldDispatch =
                    force ||
                        wasDisconnected ||
                        WearPlanStore.shouldRequestPlans(context, requestAt)
                if (!shouldDispatch) {
                    if (WearPlanStore.markConnected(context)) {
                        requestTileUpdate(context)
                    }
                    return@addOnSuccessListener
                }
                if (force || wasDisconnected) {
                    WearPlanStore.markPlansRequested(context, requestAt)
                }
                val pendingAfterUpdatedAt =
                    WearPlanStore.getDashboard(context).updatedAt
                val sendTasks = runCatching {
                    nodes.map { node ->
                        Wearable.getMessageClient(context)
                        .sendMessage(node.id, REQUEST_PLANS_PATH, byteArrayOf())
                        .addOnSuccessListener {
                            Log.d(TAG, "Requested plans from ${node.displayName}")
                        }
                    }
                }.getOrElse { error ->
                    WearPlanStore.markSyncFailure(
                        context,
                        System.currentTimeMillis(),
                        WearConnectionState.CONNECTED
                    )
                    Log.w(TAG, "Unable to dispatch plan request", error)
                    requestTileUpdate(context)
                    return@addOnSuccessListener
                }

                val pendingSince = System.currentTimeMillis()
                WearPlanStore.markSyncPending(
                    context = context,
                    pendingSince = pendingSince,
                    pendingAfterDashboardUpdatedAt = pendingAfterUpdatedAt
                )
                val responseAlreadyArrived =
                    WearPlanStore.completePendingIfNewerSnapshot(
                        context,
                        pendingSince
                    )
                requestTileUpdate(context)
                if (!responseAlreadyArrived) {
                    scheduleTimeout(context, pendingSince)
                }
                Tasks.whenAllComplete(sendTasks).addOnCompleteListener {
                    if (sendTasks.none { it.isSuccessful }) {
                        WearPlanStore.markSyncFailureIfPending(
                            context,
                            pendingSince,
                            System.currentTimeMillis()
                        )
                        Log.w(TAG, "Unable to request plans from connected phone")
                        requestTileUpdate(context)
                    }
                }
            }
            .addOnFailureListener { error ->
                WearPlanStore.markSyncFailure(
                    context,
                    System.currentTimeMillis(),
                    WearConnectionState.UNKNOWN
                )
                Log.w(TAG, "Unable to find connected phone", error)
                requestTileUpdate(context)
            }
    }

    private fun scheduleTimeout(context: Context, pendingSince: Long) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (
                    WearPlanStore.markTimedOutIfPending(
                        appContext,
                        pendingSince,
                        System.currentTimeMillis()
                    )
                ) {
                    requestTileUpdate(appContext)
                }
            },
            SYNC_TIMEOUT_MILLIS
        )
    }

    private fun requestTileUpdate(context: Context) {
        TileService.getUpdater(context.applicationContext)
            .requestUpdate(DoseTileService::class.java)
    }

    fun markPeerDisconnected(context: Context) {
        WearPlanStore.markNotConnected(context)
        requestTileUpdate(context)
    }

    private const val TAG = "HRTWearSync"
}

class WearSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        WearSyncManager.requestPlansFromPhone(appContext, force = true)
    }
}
