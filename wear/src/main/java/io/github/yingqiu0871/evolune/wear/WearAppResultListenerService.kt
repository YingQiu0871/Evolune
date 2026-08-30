@file:Suppress("UseKtx")

package io.github.yingqiu0871.evolune.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.WEAR_APP_RESULT_PATH_PREFIX
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WearAppResultListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter {
                it.type == DataEvent.TYPE_CHANGED &&
                    it.dataItem.uri.path?.startsWith(WEAR_APP_RESULT_PATH_PREFIX) == true
            }
            .forEach { event ->
                val dataMap = runCatching {
                    DataMapItem.fromDataItem(event.dataItem).dataMap
                }.getOrNull() ?: return@forEach
                if (dataMap.getInt(WearAppProtocol.KEY_PROTOCOL_VERSION) !=
                    WearAppProtocol.PROTOCOL_VERSION
                ) return@forEach
                val payload = dataMap.getByteArray(WearAppProtocol.KEY_CONFIRM_RESULT_PAYLOAD)
                    ?: return@forEach
                val result = WearAppConfirmResultCodec.decode(payload) ?: return@forEach
                serviceScope.launch {
                    try {
                        applyResult(event.dataItem.uri.toString(), result)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Log.w(TAG, "Unable to apply Wear App confirmation result", error)
                    }
                }
            }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun applyResult(
        dataItemUri: String,
        result: io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResult
    ) {
        when (
            WearAppConfirmationStore.applyResult(
                context = applicationContext,
                path = android.net.Uri.parse(dataItemUri).path.orEmpty(),
                result = result
            )
        ) {
            WearAppResultApply.Applied,
            WearAppResultApply.Duplicate -> {
                Wearable.getDataClient(applicationContext)
                    .deleteDataItems(android.net.Uri.parse(dataItemUri))
                    .awaitSuccess()
                notifyWearAppStateChanged(applicationContext)
            }
            WearAppResultApply.Rejected -> Unit
        }
    }

    private companion object {
        const val TAG = "HRTWearAppResultListener"
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitSuccess(): Boolean =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isActive) continuation.resume(task.isSuccessful)
        }
    }
