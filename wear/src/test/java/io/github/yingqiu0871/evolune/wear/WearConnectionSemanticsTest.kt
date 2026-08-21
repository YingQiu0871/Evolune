package io.github.yingqiu0871.evolune.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Connection-semantics coverage for the BIND_LISTENER removal.
 *
 * Chain used by production (see WearSyncManager.requestPlansFromPhone):
 *   decideWearSyncOutcome  ->  WearPlanStore transition  ->  deriveWearDashboardState
 * The pure decision function is asserted here; the presentation mappings
 * (DISCONNECTED -> NOT_CONNECTED, pending -> SYNC_PENDING) are asserted in
 * WearDashboardStateTest, which these tests must not weaken.
 */
class WearConnectionSemanticsTest {

    private val nodeA = "phone-node-1"
    private val nodeB = "phone-node-2"

    // A. No connected nodes -> authoritative presentation becomes NOT_CONNECTED.
    @Test
    fun `empty connected node set decides not connected`() {
        assertEquals(
            WearSyncOutcome.NoConnectedNodes,
            decideWearSyncOutcome(
                connectedNodeIds = emptyList(),
                force = false,
                wasDisconnected = false,
                shouldRequestPlans = false
            )
        )
        // Presentation link: DISCONNECTED -> NOT_CONNECTED is covered by
        // WearDashboardStateTest.`zero connected nodes is not connected`.
        assertEquals(
            WearDashboardState.NOT_CONNECTED,
            deriveWearDashboardState(
                dashboard = dashboard,
                metadata = metadata(connectionState = WearConnectionState.DISCONNECTED),
                nowMillis = now
            )
        )
    }

    // B. Connected node available -> requestPlansFromPhone dispatches
    //    /hrt/request-plans and marks sync pending/connected per semantics.
    @Test
    fun `connected node after prior disconnect decides dispatch`() {
        assertEquals(
            WearSyncOutcome.DispatchRequest(listOf(nodeA)),
            decideWearSyncOutcome(
                connectedNodeIds = listOf(nodeA),
                force = false,
                wasDisconnected = true,
                shouldRequestPlans = false
            )
        )
    }

    @Test
    fun `connected node with forced refresh decides dispatch`() {
        assertEquals(
            WearSyncOutcome.DispatchRequest(listOf(nodeA)),
            decideWearSyncOutcome(
                connectedNodeIds = listOf(nodeA),
                force = true,
                wasDisconnected = false,
                shouldRequestPlans = false
            )
        )
    }

    @Test
    fun `connected node after request throttle decides dispatch`() {
        assertEquals(
            WearSyncOutcome.DispatchRequest(listOf(nodeA, nodeB)),
            decideWearSyncOutcome(
                connectedNodeIds = listOf(nodeA, nodeB),
                force = false,
                wasDisconnected = false,
                shouldRequestPlans = true
            )
        )
    }

    @Test
    fun `connected node within throttle stays connected without dispatch`() {
        assertEquals(
            WearSyncOutcome.ConnectedWithoutDispatch,
            decideWearSyncOutcome(
                connectedNodeIds = listOf(nodeA),
                force = false,
                wasDisconnected = false,
                shouldRequestPlans = false
            )
        )
        // Presentation link: pending request -> SYNC_PENDING is covered by
        // WearDashboardStateTest.`connected dispatched request is pending`.
    }

    @Test
    fun `failed node query decides query failed`() {
        assertEquals(
            WearSyncOutcome.QueryFailed,
            decideWearSyncOutcome(
                connectedNodeIds = null,
                force = false,
                wasDisconnected = false,
                shouldRequestPlans = false
            )
        )
    }

    @Test
    fun `request plans protocol path is unchanged`() {
        assertEquals("/hrt/request-plans", REQUEST_PLANS_PATH)
    }

    @Test
    fun `dose actions protocol path prefix is unchanged`() {
        val field = Class.forName(
            "io.github.yingqiu0871.evolune.wear.DoseTileServiceKt"
        ).getDeclaredField("DOSE_ACTIONS_PATH_PREFIX").apply { isAccessible = true }
        assertEquals("/hrt/dose-actions", field.get(null))
    }

    // C. Valid /hrt/plans snapshot handling remains unchanged.
    @Test
    fun `valid plans snapshot still applies and becomes ready`() {
        val applied = applyWearSnapshot(
            previousDashboard = null,
            plansJson = """[{"id":"$planId","name":"Primary","doseMG":2.0}]""",
            currentConcentration = 120.0,
            curveValues = listOf(110f, 120f, 130f),
            dashboardUpdatedAt = now - 1_000L
        ) as WearSnapshotApplyResult.Applied

        assertEquals(1, applied.dashboard.plans.size)
        assertEquals(
            WearDashboardState.READY,
            deriveWearDashboardState(
                applied.dashboard,
                metadata(),
                now
            )
        )
    }

    // D. Malformed snapshot -> last-good data preserved, failure semantics unchanged.
    @Test
    fun `malformed snapshot still retains last good dashboard`() {
        listOf(
            "not-json",
            """[{"id":"not-a-uuid","name":"Bad id","doseMG":2.0}]"""
        ).forEach { payload ->
            val rejected = applyWearSnapshot(
                previousDashboard = dashboard,
                plansJson = payload,
                currentConcentration = null,
                curveValues = emptyList(),
                dashboardUpdatedAt = now
            ) as WearSnapshotApplyResult.Rejected

            assertSame(dashboard, rejected.retainedDashboard)
        }
        assertEquals(
            WearDashboardState.SYNC_FAILED,
            deriveWearDashboardState(
                dashboard,
                metadata(lastFailureAt = now),
                now
            )
        )
    }

    // E. No BIND_LISTENER is required: the manifest keeps the filtered
    //    DATA_CHANGED listener and no longer registers BIND_LISTENER, and the
    //    listener service no longer depends on peer callbacks.
    @Test
    fun `wear manifest registers filtered data changed without bind listener`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest file must be readable from module dir", manifest.isFile)
        val text = manifest.readText()

        assertFalse(
            "BIND_LISTENER must not be registered",
            text.contains("com.google.android.gms.wearable.BIND_LISTENER")
        )
        assertTrue(
            "filtered DATA_CHANGED listener must remain",
            text.contains("com.google.android.gms.wearable.DATA_CHANGED")
        )
        assertTrue(
            "DATA_CHANGED must remain scoped to the plans path",
            text.contains("/hrt/plans")
        )
    }

    @Test
    fun `listener service no longer depends on peer callbacks`() {
        val methods = WearPlanListenerService::class.java
            .declaredMethods
            .map { it.name }
        assertFalse(methods.contains("onPeerConnected"))
        assertFalse(methods.contains("onPeerDisconnected"))
        assertTrue(methods.contains("onDataChanged"))
    }

    private val now = 1_800_000_000_000L
    private val planId = "00000000-0000-0000-0000-000000000601"
    private val dashboard = WearDashboard(
        plans = listOf(WearPlan(planId, "Primary", 2.0)),
        currentConcentration = 120.0,
        curveValues = listOf(110f, 120f, 130f),
        updatedAt = now - 1_000L
    )

    private fun metadata(
        connectionState: WearConnectionState = WearConnectionState.CONNECTED,
        lastFailureAt: Long = 0L
    ) = WearSyncMetadata(
        hasValidSnapshot = true,
        snapshotReceivedAt = now,
        connectionState = connectionState,
        pendingSince = 0L,
        pendingAfterDashboardUpdatedAt = 0L,
        lastFailureAt = lastFailureAt
    )
}
