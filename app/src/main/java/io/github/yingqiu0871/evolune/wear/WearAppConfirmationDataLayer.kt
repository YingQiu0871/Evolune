package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.github.yingqiu0871.evolune.application.WearAppConfirmationHandler
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommandCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.operationIdFromWearAppCommandPath
import io.github.yingqiu0871.evolune.experience.wear.wearAppResultPath
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.widget.WidgetUpdateReason
import io.github.yingqiu0871.evolune.widget.requestEvoluneWidgetUpdate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

internal suspend fun processWearAppConfirmationDataItem(
    context: Context,
    item: DataItem,
    medicationPlans: MedicationPlanRepository? = null,
    doseEvents: DoseEventRepository? = null
) {
    val path = item.uri.path ?: return
    val operationId = operationIdFromWearAppCommandPath(path) ?: return
    val dataMap = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull() ?: return
    if (dataMap.getInt(WearAppProtocol.KEY_PROTOCOL_VERSION) !=
        WearAppProtocol.PROTOCOL_VERSION
    ) return
    val payload = dataMap.getByteArray(WearAppProtocol.KEY_CONFIRM_COMMAND_PAYLOAD) ?: return
    val command = WearAppConfirmCommandCodec.decode(payload) ?: return
    if (command.operationId != operationId) return

    val repositories = ProductionRepositoryProvider.get(context.applicationContext)
    val planRepository = medicationPlans ?: repositories.medicationPlans
    val eventRepository = doseEvents ?: repositories.doseEvents
    val result = WearAppConfirmationHandler(
        context = context.applicationContext,
        medicationPlans = planRepository,
        doseEvents = eventRepository
    ).handle(command)
    val resultPayload = WearAppConfirmResultCodec.encode(result)
    if (!publishWearAppConfirmationResult(context, result.operationId, resultPayload)) return

    // A retryable result deliberately leaves the command DataItem available.
    if (result.resultType == WearAppConfirmResultType.RETRYABLE_STORAGE_FAILURE) return
    val deleted = runCatching {
        Wearable.getDataClient(context.applicationContext)
            .deleteDataItems(item.uri)
            .awaitSuccess()
    }.getOrDefault(false)
    if (!deleted) return

    runCatching {
        WearAppDataLayer.publishCurrentSnapshot(context.applicationContext)
    }.onFailure { error ->
        Log.w(TAG, "Unable to refresh Wear App snapshot after confirmation", error)
    }
    runCatching {
        val currentPlans = planRepository.observeEnabled().first().take(2)
        WearDataLayer.syncPlans(context.applicationContext, currentPlans)
    }.onFailure { error ->
        Log.w(TAG, "Unable to refresh legacy Wear tile snapshot after confirmation", error)
    }
    runCatching {
        requestEvoluneWidgetUpdate(
            context.applicationContext,
            WidgetUpdateReason.ACCEPTED_WEAR_DOSE_EVENT
        )
    }.onFailure { error ->
        Log.w(TAG, "Unable to refresh widget after Wear App confirmation", error)
    }
}

private suspend fun publishWearAppConfirmationResult(
    context: Context,
    operationId: java.util.UUID,
    payload: ByteArray
): Boolean = runCatching {
    val request = PutDataMapRequest.create(wearAppResultPath(operationId)).apply {
        dataMap.putInt(
            WearAppProtocol.KEY_PROTOCOL_VERSION,
            WearAppProtocol.PROTOCOL_VERSION
        )
        dataMap.putByteArray(WearAppProtocol.KEY_CONFIRM_RESULT_PAYLOAD, payload)
    }.asPutDataRequest().setUrgent()
    Wearable.getDataClient(context.applicationContext)
        .putDataItem(request)
        .awaitSuccess()
}.getOrDefault(false)

private suspend fun <T> Task<T>.awaitSuccess(): Boolean =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isActive) continuation.resume(task.isSuccessful)
        }
    }

private const val TAG = "HRTWearAppConfirmation"
