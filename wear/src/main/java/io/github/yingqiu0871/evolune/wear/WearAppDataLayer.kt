package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommandRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppUpcomingOccurrence
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequestCodec
import io.github.yingqiu0871.evolune.experience.wear.wearAppCommandPath
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

internal object WearAppDataLayer {
    fun confirmOccurrence(
        context: Context,
        snapshot: WearAppSnapshot,
        occurrence: WearAppUpcomingOccurrence
    ): Boolean {
        val applicationContext = context.applicationContext
        if (!WearAppStore.canConfirm(applicationContext, snapshot, occurrence.occurrenceId)) {
            return false
        }
        val existing = WearAppConfirmationStore.getPending(applicationContext)
        val command = existing?.command ?: WearAppConfirmCommand(
            protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
            commandType = io.github.yingqiu0871.evolune.experience.wear.WearAppCommandType.CONFIRM_OCCURRENCE,
            operationId = UUID.randomUUID(),
            createdAt = Instant.now(),
            sourceSnapshot = io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity(
                producerInstanceId = snapshot.producerInstanceId,
                producerGeneration = snapshot.producerGeneration,
                snapshotRevision = snapshot.snapshotRevision
            ),
            occurrenceId = occurrence.occurrenceId,
            planId = occurrence.planId,
            slotId = occurrence.slotId,
            localDate = occurrence.localDate,
            scheduledAt = occurrence.scheduledAt
        )
        if (
            existing != null &&
            (existing.occurrenceId != occurrence.occurrenceId ||
                existing.command.sourceSnapshot != command.sourceSnapshot)
        ) return false
        if (!WearAppConfirmCommandRules.isValid(command)) return false
        val pending = WearAppConfirmationStore.beginOrReuse(applicationContext, command)
            ?: return false
        sendCommand(applicationContext, pending, clearPendingOnFailure = existing == null)
        notifyWearAppStateChanged(applicationContext)
        return true
    }

    fun retryPending(context: Context): Boolean {
        val applicationContext = context.applicationContext
        val pending = WearAppConfirmationStore.getPending(applicationContext) ?: return false
        if (pending.awaitingAuthoritativeSnapshot) return false
        sendCommand(applicationContext, pending, clearPendingOnFailure = false)
        return true
    }

    private fun sendCommand(
        context: Context,
        pending: WearAppPendingConfirmation,
        clearPendingOnFailure: Boolean
    ) {
        val sendAttempt = WearAppConfirmationStore.nextSendAttempt(
            context,
            pending.operationId
        ) ?: return
        val payload = runCatching {
            io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommandCodec
                .encode(pending.command)
        }.getOrElse {
            if (clearPendingOnFailure) {
                WearAppConfirmationStore.clearPendingIfOperation(context, pending.operationId)
            }
            return
        }
        runCatching {
            val request = PutDataMapRequest.create(wearAppCommandPath(pending.operationId)).apply {
                dataMap.putInt(
                    WearAppProtocol.KEY_PROTOCOL_VERSION,
                    WearAppProtocol.PROTOCOL_VERSION
                )
                dataMap.putByteArray(WearAppProtocol.KEY_CONFIRM_COMMAND_PAYLOAD, payload)
                dataMap.putLong(WearAppProtocol.KEY_CONFIRM_ATTEMPT, sendAttempt)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request)
                .addOnFailureListener { error ->
                    if (clearPendingOnFailure) {
                        WearAppConfirmationStore.clearPendingIfOperation(context, pending.operationId)
                        notifyWearAppStateChanged(context)
                    }
                    Log.w(TAG, "Unable to send Wear App confirmation", error)
                }
        }.onFailure { error ->
            if (clearPendingOnFailure) {
                WearAppConfirmationStore.clearPendingIfOperation(context, pending.operationId)
                notifyWearAppStateChanged(context)
            }
            Log.w(TAG, "Unable to prepare Wear App confirmation", error)
        }
    }

    fun requestSnapshot(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        if (!WearAppStore.beginRequest(context, nowMillis)) return
        val appContext = context.applicationContext
        val request = WearAppStore.getRequest(appContext, nowMillis)
        if (request == null) {
            WearAppStore.markFailure(appContext, nowMillis)
            return
        }
        val payload = WearAppRequestCodec.encode(request)
        Wearable.getNodeClient(appContext).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    WearAppStore.markDisconnected(appContext)
                    return@addOnSuccessListener
                }
                val remaining = AtomicInteger(nodes.size)
                val successes = AtomicInteger(0)
                nodes.forEach { node ->
                    Wearable.getMessageClient(appContext)
                        .sendMessage(node.id, WearAppProtocol.REQUEST_PATH, payload)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) successes.incrementAndGet()
                            if (remaining.decrementAndGet() == 0) {
                                if (successes.get() > 0) {
                                    WearAppStore.markDispatched(appContext)
                                } else {
                                    WearAppStore.markFailure(
                                        appContext,
                                        System.currentTimeMillis()
                                    )
                                }
                            }
                        }
                }
            }
            .addOnFailureListener { error ->
                WearAppStore.markFailure(appContext, System.currentTimeMillis())
                Log.w(TAG, "Unable to find connected Wear nodes", error)
            }
    }

    private const val TAG = "HRTWearAppDataLayer"
}
