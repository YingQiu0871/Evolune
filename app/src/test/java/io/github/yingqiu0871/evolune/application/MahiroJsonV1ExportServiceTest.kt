package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeResult
import io.github.yingqiu0871.evolune.pk.DoseEvent as PkDoseEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.utils.MahiroJsonFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class MahiroJsonV1ExportServiceTest {
    @Test
    fun `formal export preserves legacy v1 fields order and metadata projection`() {
        val exportedAt = Instant.parse("2026-01-02T03:04:05Z")
        val events = listOf(
            event(UUID(0L, 2L), 2_000L),
            event(UUID(0L, 1L), 1_000L).copy(
                source = DoseEventSource.WEAR,
                revision = 8L
            )
        )
        val service = MahiroJsonV1ExportService(
            clock = Clock.fixed(exportedAt, ZoneOffset.UTC)
        )

        val actual = Json.parseToJsonElement(service.export(55.0, events)).jsonObject
        val legacy = Json.parseToJsonElement(
            MahiroJsonFormat.generateExport(55.0, events.map(::legacyProjection))
        ).jsonObject

        assertEquals(exportedAt.toString(), actual.getValue("meta").jsonObject
            .getValue("exportedAt").jsonPrimitive.content)
        assertEquals(
            events.map { it.id.toString() },
            actual.getValue("events").jsonArray.map {
                it.jsonObject.getValue("id").jsonPrimitive.content
            }
        )
        assertEquals(withoutExportedAt(legacy), withoutExportedAt(actual))
    }

    @Test
    fun `formal export rejects an Instant outside the v1 time range`() {
        val service = MahiroJsonV1ExportService(
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.export(55.0, listOf(event(UUID(0L, 3L), 0L).copy(occurredAt = Instant.MAX)))
        }
    }

    private fun event(id: UUID, epochMillis: Long): DoseEvent = DoseEvent(
        id = id,
        route = Route.SUBLINGUAL,
        occurredAt = Instant.ofEpochMilli(epochMillis),
        doseMG = 2.0,
        ester = Ester.E2,
        extras = mapOf(
            ExtraKey.SUBLINGUAL_TIER to 2.0,
            ExtraKey.SUBLINGUAL_THETA to 0.25,
            ExtraKey.CONCENTRATION_MG_ML to 1.5,
            ExtraKey.AREA_CM2 to 12.0,
            ExtraKey.RELEASE_RATE_UG_PER_DAY to 24.0,
            ExtraKey.ANTI_ANDROGEN_TYPE to 1.0
        ),
        source = DoseEventSource.JSON_V1
    )

    private fun legacyProjection(event: DoseEvent): PkDoseEvent {
        val timeH = when (val result = LegacyTimeAdapter.instantToTimeH(event.occurredAt)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> throw IllegalArgumentException("synthetic oracle time")
        }
        return PkDoseEvent(
            id = event.id,
            route = event.route,
            timeH = timeH,
            doseMG = event.doseMG,
            ester = event.ester,
            extras = event.extras.mapKeys { (key, _) ->
                when (key) {
                    ExtraKey.CONCENTRATION_MG_ML -> PkDoseEvent.ExtraKey.CONCENTRATION_MG_ML
                    ExtraKey.AREA_CM2 -> PkDoseEvent.ExtraKey.AREA_CM2
                    ExtraKey.RELEASE_RATE_UG_PER_DAY ->
                        PkDoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY
                    ExtraKey.SUBLINGUAL_THETA -> PkDoseEvent.ExtraKey.SUBLINGUAL_THETA
                    ExtraKey.SUBLINGUAL_TIER -> PkDoseEvent.ExtraKey.SUBLINGUAL_TIER
                    ExtraKey.ANTI_ANDROGEN_TYPE -> PkDoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE
                }
            }
        )
    }

    private fun withoutExportedAt(root: JsonObject): JsonObject = JsonObject(
        root.toMutableMap().apply {
            put(
                "meta",
                JsonObject(getValue("meta").jsonObject.toMutableMap().apply {
                    remove("exportedAt")
                })
            )
        }
    )
}
