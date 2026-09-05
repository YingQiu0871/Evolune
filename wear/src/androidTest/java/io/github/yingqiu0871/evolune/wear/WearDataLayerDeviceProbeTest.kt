package io.github.yingqiu0871.evolune.wear

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Live-only v1.5 evidence probe. It is skipped when the emulator pair is not connected.
 */
@RunWith(AndroidJUnit4::class)
class WearDataLayerDeviceProbeTest {
    @Test
    fun requestPlansFromPhoneReceivesAnAuthoritativeDashboard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nodes = Tasks.await(
            Wearable.getNodeClient(context).connectedNodes,
            NODE_QUERY_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
        assumeTrue(
            "A paired Phone/Wear emulator is required for the live Data Layer probe",
            nodes.isNotEmpty()
        )

        val before = WearPlanStore.getDashboard(context).updatedAt
        WearSyncManager.requestPlansFromPhone(context, force = true)

        val deadline = SystemClock.elapsedRealtime() + RESPONSE_TIMEOUT_MILLIS
        var after = WearPlanStore.getDashboard(context)
        while (SystemClock.elapsedRealtime() < deadline && after.updatedAt <= before) {
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
            after = WearPlanStore.getDashboard(context)
        }

        assertTrue(
            "Phone did not deliver a newer authoritative Wear dashboard; nodes=${nodes.describe()}",
            after.updatedAt > before
        )
        val metadata = WearPlanStore.getSyncMetadata(context)
        assertEquals(WearConnectionState.CONNECTED, metadata.connectionState)
        assertEquals(0L, metadata.pendingSince)
    }

    private fun List<Node>.describe(): String = joinToString { node ->
        "${node.displayName}/${node.id}"
    }

    private companion object {
        const val NODE_QUERY_TIMEOUT_SECONDS = 10L
        const val RESPONSE_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 250L
    }
}
