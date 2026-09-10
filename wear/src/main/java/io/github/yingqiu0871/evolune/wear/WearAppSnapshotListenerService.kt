package io.github.yingqiu0871.evolune.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol

class WearAppSnapshotListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .filter { it.dataItem.uri.path == WearAppProtocol.SNAPSHOT_PATH }
            .forEach { event ->
                val dataMap = runCatching {
                    DataMapItem.fromDataItem(event.dataItem).dataMap
                }.getOrNull() ?: return@forEach
                if (dataMap.getInt(WearAppProtocol.KEY_PROTOCOL_VERSION) !=
                    WearAppProtocol.PROTOCOL_VERSION
                ) {
                    return@forEach
                }
                val payload = dataMap.getByteArray(WearAppProtocol.KEY_PAYLOAD)
                    ?: return@forEach
                val result = WearAppStore.acceptSnapshot(
                    context = applicationContext,
                    payload = payload,
                    receivedAt = System.currentTimeMillis()
                )
                Log.i(TAG, "snapshot result=$result")
                if (shouldRefreshAfterSnapshot(result)) {
                    requestGalleryTileUpdates()
                    requestWearComplicationUpdates(applicationContext)
                }
            }
    }

    private fun requestGalleryTileUpdates() {
        Log.i(TAG, "requesting gallery Tile updates")
        val updater = TileService.getUpdater(applicationContext)
        updater.requestUpdate(NextDoseTileService::class.java)
        updater.requestUpdate(TodayPlanTileService::class.java)
        updater.requestUpdate(CurrentE2TileService::class.java)
        val executor = applicationContext.mainExecutor
        val activeTiles = TileService.getActiveTilesAsync(applicationContext, executor)
        activeTiles.addListener(
            {
                runCatching { activeTiles.get() }
                    .onSuccess { identifiers ->
                        identifiers.forEach { identifier ->
                            val serviceClass = galleryTileServiceClass(
                                identifier.componentName.className
                            ) ?: return@forEach
                            Log.i(
                                TAG,
                                "requesting active Tile component=${identifier.componentName.flattenToShortString()} " +
                                    "instanceId=${identifier.instanceId}"
                            )
                            updater.requestUpdate(serviceClass, identifier.instanceId)
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "could not enumerate active Tiles", error)
                    }
            },
            executor
        )
    }

    private fun galleryTileServiceClass(className: String): Class<out TileService>? = when (className) {
        NextDoseTileService::class.java.name -> NextDoseTileService::class.java
        TodayPlanTileService::class.java.name -> TodayPlanTileService::class.java
        CurrentE2TileService::class.java.name -> CurrentE2TileService::class.java
        else -> null
    }

    private companion object {
        const val TAG = "EvoluneWearSnapshot"
    }

}
