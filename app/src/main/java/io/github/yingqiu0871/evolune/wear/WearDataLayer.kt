package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.github.yingqiu0871.evolune.application.WearDoseActionHandler
import io.github.yingqiu0871.evolune.application.WearDoseActionOutcome
import io.github.yingqiu0871.evolune.application.WearDoseActionPayload
import io.github.yingqiu0871.evolune.application.parseWearDoseAction
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.pk.SimulationResult
import io.github.yingqiu0871.evolune.widget.WidgetUpdateReason
import io.github.yingqiu0871.evolune.widget.requestEvoluneWidgetUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

object WearDataLayer {
    const val PLANS_PATH = "/hrt/plans"
    const val REQUEST_PLANS_PATH = "/hrt/request-plans"
    const val DOSE_ACTIONS_PATH_PREFIX = "/hrt/dose-actions"
    const val KEY_PLANS_JSON = "plans_json"
    const val KEY_PLAN_ID = "plan_id"
    const val KEY_ACTION_ID = "action_id"
    const val KEY_RECORDED_AT = "recorded_at"
    const val KEY_CURRENT_CONCENTRATION = "current_concentration"
    const val KEY_CURVE_VALUES = "curve_values"
    const val KEY_DASHBOARD_UPDATED_AT = "dashboard_updated_at"
    private const val PREFERENCES_NAME = "wear_dashboard_cache"
    private const val KEY_CACHED_CURRENT = "cached_current"
    private const val KEY_CACHED_CURVE = "cached_curve"

    fun syncPlans(context: Context, plans: List<MedicationPlan>) {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val current = preferences.getString(KEY_CACHED_CURRENT, null)
            ?.toDoubleOrNull()
        val curve = preferences.getString(KEY_CACHED_CURVE, null)
            ?.split(',')
            ?.mapNotNull { it.toFloatOrNull() }
            .orEmpty()
        syncDashboard(context, plans, current, curve)
    }

    fun syncDashboard(
        context: Context,
        plans: List<MedicationPlan>,
        currentConcentration: Double?,
        curveValues: List<Float>
    ) {
        val plansJson = encodeWearPlans(plans)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
                if (currentConcentration != null) {
                    putString(KEY_CACHED_CURRENT, currentConcentration.toString())
                } else {
                    remove(KEY_CACHED_CURRENT)
                }
                putString(KEY_CACHED_CURVE, curveValues.joinToString(","))
            }

        runCatching {
            val updatedAt = System.currentTimeMillis()
            val request = PutDataMapRequest.create(PLANS_PATH).apply {
                dataMap.putString(KEY_PLANS_JSON, plansJson)
                dataMap.putDouble(
                    KEY_CURRENT_CONCENTRATION,
                    currentConcentration ?: Double.NaN
                )
                dataMap.putFloatArray(
                    KEY_CURVE_VALUES,
                    curveValues.toFloatArray()
                )
                dataMap.putLong(KEY_DASHBOARD_UPDATED_AT, updatedAt)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context)
                .putDataItem(request)
                .addOnSuccessListener {
                    Log.d(TAG, "Synced ${plans.size} plan(s) to Wear OS")
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to sync plans to Wear OS", error)
                }
        }
    }

    private const val TAG = "HRTWearDataLayer"
}

internal fun sampleWearCurve(
    simulationResult: SimulationResult?,
    currentTimeH: Double
): List<Float> {
    if (simulationResult == null) return emptyList()
    return (-12..12).mapNotNull { offsetHours ->
        simulationResult.concentration(currentTimeH + offsetHours)
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.toFloat()
    }
}

/**
 * Receives reliable, buffered one-tap dose actions from the paired Wear OS app.
 */
class WearDoseListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearDataLayer.REQUEST_PLANS_PATH) return

        serviceScope.launch {
            try {
                val plans = ProductionRepositoryProvider
                    .get(applicationContext)
                    .medicationPlans
                    .observeEnabled()
                    .first()
                    .take(2)
                WearDataLayer.syncPlans(applicationContext, plans)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Log.w(TAG, "Unable to load plans for Wear sync")
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val changedActions = dataEvents
            .filter {
                it.type == DataEvent.TYPE_CHANGED &&
                    it.dataItem.uri.path?.startsWith(
                        WearDataLayer.DOSE_ACTIONS_PATH_PREFIX
                    ) == true
            }
            .map { parseDataItem(it.dataItem) }

        if (changedActions.isEmpty()) return

        changedActions.forEach { action ->
            serviceScope.launch {
                try {
                    processAction(action)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    Log.w(TAG, "Wear action processing failed")
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun processAction(action: WearDoseActionPayload) {
        val context = applicationContext
        val repositories = ProductionRepositoryProvider.get(context)
        val outcome = WearDoseActionHandler(
            medicationPlans = repositories.medicationPlans,
            doseEvents = repositories.doseEvents,
            acceptedSideEffect = {
                requestEvoluneWidgetUpdate(
                    context,
                    WidgetUpdateReason.ACCEPTED_WEAR_DOSE_EVENT
                )
            },
            deleteDataItem = { uri ->
                Wearable.getDataClient(context)
                    .deleteDataItems(uri.toUri())
                    .awaitSuccess()
            }
        ).handle(action)

        when (outcome) {
            is WearDoseActionOutcome.Accepted -> if (!outcome.dataItemDeleted) {
                Log.w(TAG, "Wear action accepted; DataItem acknowledgement pending")
            }
            WearDoseActionOutcome.StorageFailure,
            WearDoseActionOutcome.UnexpectedFailure ->
                Log.w(TAG, "Wear action retained for retry")
            else -> Unit
        }
    }

    private fun parseDataItem(item: DataItem): WearDoseActionPayload {
        val uri = item.uri.toString()
        val data = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull()
        val recordedAt = runCatching {
            if (data?.containsKey(WearDataLayer.KEY_RECORDED_AT) == true) {
                data.getLong(WearDataLayer.KEY_RECORDED_AT)
            } else {
                null
            }
        }.getOrNull()
        return parseWearDoseAction(
            dataItemUri = uri,
            planId = runCatching { data?.getString(WearDataLayer.KEY_PLAN_ID) }.getOrNull(),
            actionId = runCatching { data?.getString(WearDataLayer.KEY_ACTION_ID) }.getOrNull(),
            recordedAtMillis = recordedAt
        )
    }

    private companion object {
        const val TAG = "HRTWearDoseListener"
    }
}

internal fun encodeWearPlans(plans: List<MedicationPlan>): String =
    buildJsonArray {
        plans.filter { it.isEnabled }.take(2).forEach { plan ->
            add(
                buildJsonObject {
                    put("id", plan.id.toString())
                    put("name", plan.name)
                    put("doseMG", plan.doseMG)
                }
            )
        }
    }.toString()

private suspend fun <T> Task<T>.awaitSuccess(): Boolean =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isActive) {
                continuation.resume(task.isSuccessful)
            }
        }
    }
