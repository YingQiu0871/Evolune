package io.github.yingqiu0871.evolune.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDashboardStateTest {
    private val now = 1_800_000_000_000L
    private val planId = "00000000-0000-0000-0000-000000000601"
    private val dashboard = WearDashboard(
        plans = listOf(WearPlan(planId, "Primary", 2.0)),
        currentConcentration = 120.0,
        curveValues = listOf(110f, 120f, 130f),
        updatedAt = now - 1_000L
    )

    @Test
    fun `no snapshot waits for phone instead of claiming no plans`() {
        assertEquals(
            WearDashboardState.WAITING_FOR_PHONE,
            state(hasSnapshot = false, snapshotReceivedAt = 0L)
        )
    }

    @Test
    fun `zero connected nodes is not connected`() {
        assertEquals(
            WearDashboardState.NOT_CONNECTED,
            state(connectionState = WearConnectionState.DISCONNECTED)
        )
    }

    @Test
    fun `connected dispatched request is pending`() {
        assertEquals(
            WearDashboardState.SYNC_PENDING,
            state(pendingSince = now - 1_000L)
        )
    }

    @Test
    fun `pending request times out as sync failure`() {
        assertEquals(
            WearDashboardState.SYNC_FAILED,
            state(pendingSince = now - SYNC_TIMEOUT_MILLIS)
        )
        assertEquals(
            WearDashboardState.SYNC_FAILED,
            state(pendingSince = now - SYNC_TIMEOUT_MILLIS - 1L)
        )
    }

    @Test
    fun `explicit sync failure takes precedence over cached content`() {
        assertEquals(
            WearDashboardState.SYNC_FAILED,
            state(lastFailureAt = now - 1L)
        )
    }

    @Test
    fun `unreadable persisted dashboard cannot masquerade as no plans`() {
        assertEquals(
            WearDashboardState.SYNC_FAILED,
            deriveWearDashboardState(
                dashboard = WearDashboard(emptyList(), null, emptyList(), 0L),
                metadata = freshMetadata(),
                nowMillis = now
            )
        )
    }

    @Test
    fun `stale valid cache is distinguished from ready`() {
        assertEquals(
            WearDashboardState.STALE_CACHE,
            state(snapshotReceivedAt = now - STALE_AFTER_MILLIS - 1L)
        )
    }

    @Test
    fun `valid one plan snapshot becomes ready`() {
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
            deriveWearDashboardState(applied.dashboard, freshMetadata(), now)
        )
    }

    @Test
    fun `valid multiple plans preserve order and cap existing format at two`() {
        val applied = applyWearSnapshot(
            previousDashboard = null,
            plansJson = """
                [
                  {"id":"00000000-0000-0000-0000-000000000601","name":"First","doseMG":1.0},
                  {"id":"00000000-0000-0000-0000-000000000602","name":"Second","doseMG":2.0},
                  {"id":"00000000-0000-0000-0000-000000000603","name":"Third","doseMG":3.0}
                ]
            """.trimIndent(),
            currentConcentration = null,
            curveValues = emptyList(),
            dashboardUpdatedAt = now
        ) as WearSnapshotApplyResult.Applied

        assertEquals(listOf("First", "Second"), applied.dashboard.plans.map { it.name })
        assertEquals(
            WearDashboardState.READY,
            deriveWearDashboardState(applied.dashboard, freshMetadata(), now)
        )
    }

    @Test
    fun `valid explicit empty snapshot is authoritative no enabled plans`() {
        val applied = applyWearSnapshot(
            previousDashboard = dashboard,
            plansJson = "[]",
            currentConcentration = null,
            curveValues = emptyList(),
            dashboardUpdatedAt = now
        ) as WearSnapshotApplyResult.Applied

        assertTrue(applied.dashboard.plans.isEmpty())
        assertEquals(
            WearDashboardState.NO_ENABLED_PLANS,
            deriveWearDashboardState(applied.dashboard, freshMetadata(), now)
        )
    }

    @Test
    fun `missing or malformed payload retains good cache and becomes failure`() {
        listOf(
            null,
            "not-json",
            "{}",
            """[{"id":"$planId","name":"Missing dose"}]""",
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
        assertTrue(
            applyWearSnapshot(
                previousDashboard = dashboard,
                plansJson = "[]",
                currentConcentration = null,
                curveValues = emptyList(),
                dashboardUpdatedAt = 0L
            ) is WearSnapshotApplyResult.Rejected
        )
        assertEquals(
            WearDashboardState.SYNC_FAILED,
            state(lastFailureAt = now)
        )
    }

    @Test
    fun `fresh valid replacement clears stale and failure state`() {
        val applied = applyWearSnapshot(
            previousDashboard = dashboard,
            plansJson = """[{"id":"$planId","name":"Fresh","doseMG":2.0}]""",
            currentConcentration = 125.0,
            curveValues = listOf(125f),
            dashboardUpdatedAt = now
        ) as WearSnapshotApplyResult.Applied

        assertEquals(
            WearDashboardState.READY,
            deriveWearDashboardState(applied.dashboard, freshMetadata(), now)
        )
    }

    @Test
    fun `pending request completes only with newer dashboard snapshot`() {
        val pending = freshMetadata().copy(
            pendingSince = now - 1_000L,
            pendingAfterDashboardUpdatedAt = now - 2_000L
        )

        assertFalse(snapshotCompletesPending(pending, now - 2_000L))
        assertFalse(snapshotCompletesPending(pending, now - 3_000L))
        assertTrue(snapshotCompletesPending(pending, now - 1_999L))
        assertTrue(
            snapshotCompletesPending(
                pending.copy(
                    hasValidSnapshot = false,
                    snapshotReceivedAt = 0L,
                    pendingAfterDashboardUpdatedAt = 0L
                ),
                1L
            )
        )
    }

    @Test
    fun `dose action is enabled only for selected ready cached plan`() {
        WearDashboardState.entries
            .filterNot { it == WearDashboardState.READY }
            .forEach { state ->
                assertFalse(canSendDoseAction(state, planId, dashboard.plans))
            }
        assertTrue(canSendDoseAction(WearDashboardState.READY, planId, dashboard.plans))
        assertFalse(canSendDoseAction(WearDashboardState.READY, null, dashboard.plans))
        assertFalse(
            canSendDoseAction(
                WearDashboardState.READY,
                "00000000-0000-0000-0000-000000000999",
                dashboard.plans
            )
        )
    }

    private fun state(
        hasSnapshot: Boolean = true,
        snapshotReceivedAt: Long = now - 1_000L,
        connectionState: WearConnectionState = WearConnectionState.CONNECTED,
        pendingSince: Long = 0L,
        lastFailureAt: Long = 0L
    ): WearDashboardState = deriveWearDashboardState(
        dashboard = dashboard,
        metadata = WearSyncMetadata(
            hasValidSnapshot = hasSnapshot,
            snapshotReceivedAt = snapshotReceivedAt,
            connectionState = connectionState,
            pendingSince = pendingSince,
            pendingAfterDashboardUpdatedAt = 0L,
            lastFailureAt = lastFailureAt
        ),
        nowMillis = now
    )

    private fun freshMetadata(): WearSyncMetadata = WearSyncMetadata(
        hasValidSnapshot = true,
        snapshotReceivedAt = now,
        connectionState = WearConnectionState.CONNECTED,
        pendingSince = 0L,
        pendingAfterDashboardUpdatedAt = 0L,
        lastFailureAt = 0L
    )
}
