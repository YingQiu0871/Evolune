package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerNegotiationResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequest
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequestCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

internal object WearAppDataLayer {
    fun publishSnapshot(context: Context, snapshot: WearAppSnapshot) {
        val payload = WearAppSnapshotCodec.encode(snapshot)
        runCatching {
            val request = PutDataMapRequest.create(WearAppProtocol.SNAPSHOT_PATH).apply {
                dataMap.putInt(
                    WearAppProtocol.KEY_PROTOCOL_VERSION,
                    WearAppProtocol.PROTOCOL_VERSION
                )
                dataMap.putByteArray(WearAppProtocol.KEY_PAYLOAD, payload)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext)
                .putDataItem(request)
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to sync Wear App snapshot", error)
                }
        }.onFailure { error ->
            Log.w(TAG, "Unable to prepare Wear App snapshot", error)
        }
    }

    suspend fun publishCurrentSnapshot(
        context: Context,
        request: WearAppRequest? = null
    ) {
        val producerIdentity = if (request == null) {
            WearAppProducerIdentityStore.current(context)
        } else {
            when (val result = WearAppProducerIdentityStore.negotiate(context, request)) {
                is WearAppProducerNegotiationResult.Accepted -> result.identity
                WearAppProducerNegotiationResult.GenerationExhausted -> {
                    Log.w(TAG, "Wear App producer generation exhausted")
                    return
                }
                WearAppProducerNegotiationResult.InvalidObservedProducer -> {
                    Log.w(TAG, "Wear App producer request was invalid")
                    return
                }
            }
        }
        val repositories = ProductionRepositoryProvider.get(context)
        val plans = repositories.medicationPlans.observeAll().first()
        val events = repositories.doseEvents.observeAll().first()
        val cachedCurrent = context.getSharedPreferences(
            "wear_dashboard_cache",
            Context.MODE_PRIVATE
        ).getString("cached_current", null)?.toDoubleOrNull()
        val now = Instant.now()
        val snapshot = WearAppSnapshotBuilder.build(
            plans = plans,
            events = events,
            generatedAt = now,
            zoneId = ZoneId.systemDefault(),
            snapshotRevision = WearAppSnapshotRevisionStore.next(context),
            currentConcentration = cachedCurrent,
            producerIdentity = producerIdentity
        )
        publishSnapshot(context, snapshot)
    }

    private const val TAG = "HRTWearAppDataLayer"
}

/** Receives only the additive v1 Wear App refresh request. */
class WearAppListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearAppProtocol.REQUEST_PATH) return
        val request = when {
            messageEvent.data.isEmpty() -> null
            else -> WearAppRequestCodec.decode(messageEvent.data) ?: return
        }
        serviceScope.launch {
            try {
                WearAppDataLayer.publishCurrentSnapshot(applicationContext, request)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Log.w(TAG, "Unable to publish requested Wear App snapshot")
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "HRTWearAppListener"
    }
}
