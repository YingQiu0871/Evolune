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

internal const val REQUEST_PLANS_PATH = "/hrt/request-plans"

internal sealed interface WearSyncOutcome {
    data object QueryFailed : WearSyncOutcome
    data object NoConnectedNodes : WearSyncOutcome
    data object ConnectedWithoutDispatch : WearSyncOutcome
    data class DispatchRequest(val nodeIds: List<String>) : WearSyncOutcome
}

internal fun decideWearSyncOutcome(
    connectedNodeIds: List<String>?,
    force: Boolean,
    wasDisconnected: Boolean,
    shouldRequestPlans: Boolean
): WearSyncOutcome {
    if (connectedNodeIds == null) return WearSyncOutcome.QueryFailed
    if (connectedNodeIds.isEmpty()) return WearSyncOutcome.NoConnectedNodes
    val shouldDispatch = force || wasDisconnected || shouldRequestPlans
    return if (shouldDispatch) {
        WearSyncOutcome.DispatchRequest(connectedNodeIds)
    } else {
        WearSyncOutcome.ConnectedWithoutDispatch
    }
}

object WearSyncManager {
    fun requestPlansFromPhone(context: Context, force: Boolean = false) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                val requestAt = System.currentTimeMillis()
                val metadata = WearPlanStore.getSyncMetadata(context)
                val wasDisconnected =
                    metadata.connectionState == WearConnectionState.DISCONNECTED
                val shouldRequest =
                    nodes.isNotEmpty() &&
                        WearPlanStore.shouldRequestPlans(context, requestAt)
                val outcome = decideWearSyncOutcome(
                    connectedNodeIds = nodes.map { it.id },
                    force = force,
                    wasDisconnected = wasDisconnected,
                    shouldRequestPlans = shouldRequest
                )
                when (outcome) {
                    WearSyncOutcome.QueryFailed -> {
                        WearPlanStore.markSyncFailure(
                            context,
                            requestAt,
                            WearConnectionState.UNKNOWN
                        )
                        Log.w(TAG, "Unable to determine connected phone nodes")
                        requestTileUpdate(context)
                    }
                    WearSyncOutcome.NoConnectedNodes -> {
                        WearPlanStore.markNotConnected(context)
                        Log.w(TAG, "No connected phone node")
                        requestTileUpdate(context)
                    }
                    WearSyncOutcome.ConnectedWithoutDispatch -> {
                        if (WearPlanStore.markConnected(context)) {
                            requestTileUpdate(context)
                        }
                    }
                    is WearSyncOutcome.DispatchRequest -> {
                        if (force || wasDisconnected) {
                            WearPlanStore.markPlansRequested(context, requestAt)
                        }
                        val pendingAfterUpdatedAt =
                            WearPlanStore.getDashboard(context).updatedAt
                        val sendTasks = runCatching {
                            outcome.nodeIds.map { nodeId ->
                                Wearable.getMessageClient(context)
                                    .sendMessage(
                                        nodeId,
                                        REQUEST_PLANS_PATH,
                                        byteArrayOf()
                                    )
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Requested plans from $nodeId")
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

    private const val TAG = "HRTWearSync"
}

class WearSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        WearSyncManager.requestPlansFromPhone(appContext, force = true)
    }
}
