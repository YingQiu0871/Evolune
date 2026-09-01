package io.github.yingqiu0871.evolune.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
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
                WearAppStore.acceptSnapshot(
                    context = applicationContext,
                    payload = payload,
                    receivedAt = System.currentTimeMillis()
                )
            }
    }

}
