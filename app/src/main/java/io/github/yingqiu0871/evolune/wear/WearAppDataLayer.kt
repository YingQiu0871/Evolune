package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerNegotiationResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequest
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequestCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotCodec
import io.github.yingqiu0871.evolune.reminder.NotificationHelper
import io.github.yingqiu0871.evolune.reminder.ReminderManager
import io.github.yingqiu0871.evolune.reminder.ReminderSkipStore
import io.github.yingqiu0871.evolune.viewmodel.DefaultPkSimulationCalculator
import io.github.yingqiu0871.evolune.viewmodel.PkSimulationInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
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
        request: WearAppRequest? = null,
        clock: Clock = Clock.systemUTC()
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
        withReservedWearAppSnapshotRevision(
            reserveRevision = { WearAppSnapshotRevisionStore.reserve(context) }
        ) { snapshotRevision ->
            val repositories = ProductionRepositoryProvider.get(context)
            val plans = repositories.medicationPlans.observeAll().first()
            val events = repositories.doseEvents.observeAll().first()
            val now = clock.instant()
            val zoneId = ZoneId.systemDefault()
            val bodyWeight = SettingsDataStore(context.applicationContext)
                .userSettings
                .first()
                .bodyWeight
            val pkEvents = repositories.doseEvents.getEventsForPk(now)
            val pkCalculation = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    DefaultPkSimulationCalculator.calculate(
                        PkSimulationInput(
                            now = now,
                            currentTimeH = now.toEpochMilli() / 3_600_000.0,
                            historicalDoseEvents = pkEvents,
                            enabledPlans = plans.filter { it.isEnabled },
                            bodyWeightKG = bodyWeight,
                            zoneId = zoneId
                        )
                    )
                }
            }
            val computedPkState = pkCalculation.getOrNull()
            val snapshot = WearAppSnapshotBuilder.build(
                plans = plans,
                events = events,
                generatedAt = now,
                zoneId = zoneId,
                snapshotRevision = snapshotRevision,
                currentConcentration = computedPkState?.currentConcentration,
                concentrationCalculatedAt = computedPkState?.concentrationCalculatedAt,
                concentrationError = pkCalculation.isFailure,
                producerIdentity = producerIdentity
            )
            publishSnapshot(context, snapshot)
        }
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

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter {
                it.type == DataEvent.TYPE_CHANGED
            }
            .forEach { event ->
                serviceScope.launch {
                    try {
                        if (event.dataItem.uri.path == WearAppProtocol.SKIP_NOTIFICATION_PATH) {
                            processWearAppSkipNotification(applicationContext, event.dataItem)
                        } else if (
                            event.dataItem.uri.path?.startsWith(
                                io.github.yingqiu0871.evolune.experience.wear.WEAR_APP_COMMAND_PATH_PREFIX
                            ) == true
                        ) {
                            processWearAppConfirmationDataItem(applicationContext, event.dataItem)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        Log.w(TAG, "Unable to process Wear App confirmation")
                    }
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

private suspend fun processWearAppSkipNotification(context: Context, item: DataItem) {
    val dataMap = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull() ?: return
    if (dataMap.getInt(WearAppProtocol.KEY_PROTOCOL_VERSION) != WearAppProtocol.PROTOCOL_VERSION) return
    val planId = runCatching {
        java.util.UUID.fromString(dataMap.getString(WearAppProtocol.KEY_SKIP_PLAN_ID))
    }.getOrNull() ?: return
    val slotId = runCatching {
        java.util.UUID.fromString(dataMap.getString(WearAppProtocol.KEY_SKIP_SLOT_ID))
    }.getOrNull() ?: return
    val scheduledAtMillis = dataMap.getLong(WearAppProtocol.KEY_SKIP_SCHEDULED_AT, 0L)
    val notificationId = dataMap.getInt(WearAppProtocol.KEY_SKIP_NOTIFICATION_ID, 0)
    val occurrenceId = runCatching {
        java.util.UUID.fromString(dataMap.getString(WearAppProtocol.KEY_SKIP_OCCURRENCE_ID))
    }.getOrNull() ?: return
    if (scheduledAtMillis <= 0L || notificationId == 0) return
    val plan = ProductionRepositoryProvider.get(context).medicationPlans.getById(planId)
        ?: return
    val zoneId = ZoneId.systemDefault()
    val scheduledAt = Instant.ofEpochMilli(scheduledAtMillis)
    val validated = validateWearSkipNotification(
        plan = plan,
        command = WearSkipNotificationCommand(
            occurrenceId = occurrenceId,
            planId = planId,
            slotId = slotId,
            scheduledAt = scheduledAt,
            notificationId = notificationId
        ),
        now = Instant.now(),
        zoneId = zoneId
    ) ?: return
    if (!ReminderSkipStore(context).markSkipped(planId, slotId, scheduledAtMillis)) return
    ReminderManager(context).cancelOccurrence(planId, validated.requestOffset)
    NotificationHelper(context).cancelNotification(notificationId)
    com.google.android.gms.wearable.Wearable.getDataClient(context)
        .deleteDataItems(item.uri)
}
