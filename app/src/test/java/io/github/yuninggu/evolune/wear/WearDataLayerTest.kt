package io.github.yuninggu.evolune.wear

import io.github.yuninggu.evolune.application.createWearDoseEvent
import io.github.yuninggu.evolune.application.parseWearDoseAction
import io.github.yuninggu.evolune.application.syntheticPlan
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.pk.SimulationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class WearDataLayerTest {
    @Test
    fun `wear curve samples twelve hours on each side`() {
        val result = SimulationResult(
            timeH = (0..24).map(Int::toDouble),
            concPGmL = (0..24).map { it * 10.0 },
            auc = 0.0
        )

        val samples = sampleWearCurve(result, 12.0)

        assertEquals(25, samples.size)
        assertEquals(0f, samples.first())
        assertEquals(120f, samples[12])
        assertEquals(240f, samples.last())
    }

    @Test
    fun `wear sync contains only first two enabled Domain plans`() {
        val encoded = encodeWearPlans(
            listOf(
                syntheticPlan(UUID(0L, 1L)).copy(name = "First"),
                syntheticPlan(UUID(0L, 2L), enabled = false).copy(name = "Disabled"),
                syntheticPlan(UUID(0L, 3L)).copy(name = "Second"),
                syntheticPlan(UUID(0L, 4L)).copy(name = "Third")
            )
        )
        val array = Json.parseToJsonElement(encoded).jsonArray

        assertEquals(2, array.size)
        assertEquals("First", array[0].jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("Second", array[1].jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `wear protocol constants and action URI remain compatible`() {
        assertEquals("/hrt/plans", WearDataLayer.PLANS_PATH)
        assertEquals("/hrt/request-plans", WearDataLayer.REQUEST_PLANS_PATH)
        assertEquals("/hrt/dose-actions", WearDataLayer.DOSE_ACTIONS_PATH_PREFIX)
        assertEquals("plans_json", WearDataLayer.KEY_PLANS_JSON)
        assertEquals("plan_id", WearDataLayer.KEY_PLAN_ID)
        assertEquals("action_id", WearDataLayer.KEY_ACTION_ID)
        assertEquals("recorded_at", WearDataLayer.KEY_RECORDED_AT)
        assertEquals("current_concentration", WearDataLayer.KEY_CURRENT_CONCENTRATION)
        assertEquals("curve_values", WearDataLayer.KEY_CURVE_VALUES)
        assertEquals("dashboard_updated_at", WearDataLayer.KEY_DASHBOARD_UPDATED_AT)

        val actionId = UUID.fromString("dc206fed-7a62-4a9f-8911-22840a1152ef")
        val payload = parseWearDoseAction(
            dataItemUri = "wear://synthetic-node/hrt/dose-actions/$actionId",
            planId = "00000000-0000-0000-0000-000000000601",
            actionId = actionId.toString(),
            recordedAtMillis = 1_800_000_000_123L
        )

        assertEquals(actionId, payload.actionId)
        assertEquals(1_800_000_000_123L, payload.recordedAtMillis)
    }

    @Test
    fun `invalid or mismatched action identity is not replaced`() {
        val actionId = UUID.fromString("dc206fed-7a62-4a9f-8911-22840a1152ef")
        val anotherId = UUID.fromString("b038beff-83f1-4a9a-a1e7-22879774a110")

        assertNull(
            parseWearDoseAction(
                "wear://synthetic-node/hrt/dose-actions/$actionId",
                "not-a-plan-id",
                "not-an-action-id",
                1_800_000_000_123L
            ).actionId
        )
        assertNull(
            parseWearDoseAction(
                "wear://synthetic-node/hrt/dose-actions/$actionId",
                UUID(0L, 601L).toString(),
                anotherId.toString(),
                1_800_000_000_123L
            ).actionId
        )
        assertNull(
            parseWearDoseAction(
                "wear://synthetic-node/hrt/dose-actions/$actionId",
                UUID(0L, 601L).toString(),
                actionId.toString(),
                null
            ).recordedAtMillis
        )
    }

    @Test
    fun `wear materialization preserves complete Domain metadata`() {
        val plan = syntheticPlan()
        val actionId = UUID.fromString("dc206fed-7a62-4a9f-8911-22840a1152ef")
        val recordedAt = Instant.ofEpochMilli(1_800_000_000_123L)
        val zoneId = ZoneId.of("Asia/Shanghai")

        val event = createWearDoseEvent(plan, actionId, recordedAt, zoneId)

        assertEquals(actionId, event.id)
        assertEquals(recordedAt, event.occurredAt)
        assertEquals(zoneId, event.zoneId)
        assertEquals(recordedAt.atZone(zoneId).toLocalDate(), event.localDate)
        assertEquals(plan.route, event.route)
        assertEquals(plan.doseMG, event.doseMG, 0.0)
        assertEquals(plan.ester, event.ester)
        assertEquals(plan.extras, event.extras)
        assertNull(event.slotId)
        assertEquals(DoseEventSource.WEAR, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertEquals(1L, event.revision)
    }
}
