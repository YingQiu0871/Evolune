package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import java.util.concurrent.atomic.AtomicInteger

internal object WearAppDataLayer {
    fun requestSnapshot(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        if (!WearAppStore.beginRequest(context, nowMillis)) return
        val appContext = context.applicationContext
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
                        .sendMessage(node.id, WearAppProtocol.REQUEST_PATH, byteArrayOf())
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
