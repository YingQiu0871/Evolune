package io.github.yingqiu0871.evolune.external.mahiro.v1

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.time.LegacyTimeError
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.utils.MahiroJsonFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MahiroV1DomainAdapterTest {
    @Test
    fun `valid DTO maps every Domain field and locked JSON metadata`() {
        val event = imported(
            dto(
                id = EVENT_ID.toString(),
                route = "sublingual",
                ester = "EV",
                timeH = 1_700_000_000_125L / 3_600_000.0,
                extras = ALL_WIRE_EXTRAS + ("unknown" to 99.0)
            )
        )

        assertEquals(EVENT_ID, event.id)
        assertEquals(Route.SUBLINGUAL, event.route)
        assertEquals(Ester.EV, event.ester)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_125L), event.occurredAt)
        assertEquals(2.0, event.doseMG, 0.0)
        assertEquals(ALL_DOMAIN_EXTRAS, event.extras)
        assertNull(event.zoneId)
        assertNull(event.localDate)
        assertNull(event.slotId)
        assertEquals(DoseEventSource.JSON_V1, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertEquals(1L, event.revision)
    }

    @Test
    fun `all seven route spellings map explicitly and case remains strict`() {
        assertEquals(Route.INJECTION, imported(dto(route = "injection")).route)
        assertEquals(Route.ORAL, imported(dto(route = "oral")).route)
        assertEquals(Route.SUBLINGUAL, imported(dto(route = "sublingual")).route)
        assertEquals(Route.GEL, imported(dto(route = "gel")).route)
        assertEquals(Route.PATCH_APPLY, imported(dto(route = "patch_apply")).route)
        assertEquals(Route.PATCH_REMOVE, imported(dto(route = "patch_remove")).route)
        assertEquals(Route.ANTIANDROGEN, imported(dto(route = "antiandrogen")).route)
        assertEquals(
            MahiroV1DomainMappingError.UnknownRoute("Oral"),
            importFailure(dto(route = "Oral"))
        )
    }

    @Test
    fun `all five ester spellings map explicitly and case remains strict`() {
        assertEquals(Ester.E2, imported(dto(ester = "E2")).ester)
        assertEquals(Ester.EB, imported(dto(ester = "EB")).ester)
        assertEquals(Ester.EV, imported(dto(ester = "EV")).ester)
        assertEquals(Ester.EC, imported(dto(ester = "EC")).ester)
        assertEquals(Ester.EN, imported(dto(ester = "EN")).ester)
        assertEquals(
            MahiroV1DomainMappingError.UnknownEster("ev"),
            importFailure(dto(ester = "ev"))
        )
    }

    @Test
    fun `missing blank and malformed ids use independent supplied UUIDs`() {
        val generated = ArrayDeque(
            listOf(
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                UUID.fromString("00000000-0000-0000-0000-000000000013")
            )
        )
        val adapter = MahiroV1DoseEventAdapter { generated.removeFirst() }

        val missing = imported(dto(id = null), adapter)
        val blank = imported(dto(id = ""), adapter)
        val malformed = imported(dto(id = "not-a-uuid"), adapter)

        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000011"), missing.id)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000012"), blank.id)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000013"), malformed.id)
        assertNotEquals(missing.id, blank.id)
        assertNotEquals(blank.id, malformed.id)
    }

    @Test
    fun `valid UUID bypasses supplier and is preserved`() {
        val adapter = MahiroV1DoseEventAdapter {
            throw AssertionError("supplier must not be called for valid ID")
        }

        assertEquals(EVENT_ID, imported(dto(id = EVENT_ID.toString()), adapter).id)
    }

    @Test
    fun `invalid legacy time returns typed adapter failure`() {
        val nonFinite = importFailure(dto(timeH = Double.NaN))
        val overflow = importFailure(dto(timeH = Double.MAX_VALUE))

        assertTrue(nonFinite is MahiroV1DomainMappingError.InvalidTimeH)
        assertTrue(
            (nonFinite as MahiroV1DomainMappingError.InvalidTimeH).error is
                LegacyTimeError.NonFinite
        )
        assertTrue(overflow is MahiroV1DomainMappingError.InvalidTimeH)
        assertTrue(
            (overflow as MahiroV1DomainMappingError.InvalidTimeH).error is
                LegacyTimeError.Overflow
        )
    }

    @Test
    fun `negative and sub-millisecond legacy times retain Math round semantics`() {
        val negative = imported(dto(timeH = -1.5))
        val roundedToOneMillis = imported(dto(timeH = 1.0 / 3_600_000.0))

        assertEquals(Instant.ofEpochMilli(-5_400_000L), negative.occurredAt)
        assertEquals(Instant.ofEpochMilli(1L), roundedToOneMillis.occurredAt)
    }

    @Test
    fun `Domain export uses exact wire spellings and deterministic extras order`() {
        val event = domainEvent(
            route = Route.ANTIANDROGEN,
            ester = Ester.EN,
            extras = ALL_DOMAIN_EXTRAS.entries.reversed().associate { it.toPair() }
        )

        val dto = exported(event)

        assertEquals(EVENT_ID.toString(), dto.id)
        assertEquals("antiandrogen", dto.route)
        assertEquals("EN", dto.ester)
        assertEquals(event.occurredAt.toEpochMilli() / 3_600_000.0, dto.timeH, 0.0)
        assertEquals(event.doseMG, dto.doseMG, 0.0)
        assertEquals(ALL_WIRE_EXTRAS, dto.extras)
        assertEquals(ALL_WIRE_EXTRAS.keys.toList(), dto.extras.keys.toList())
    }

    @Test
    fun `all Domain routes export exact protocol spellings`() {
        assertEquals("injection", exported(domainEvent(route = Route.INJECTION)).route)
        assertEquals("oral", exported(domainEvent(route = Route.ORAL)).route)
        assertEquals("sublingual", exported(domainEvent(route = Route.SUBLINGUAL)).route)
        assertEquals("gel", exported(domainEvent(route = Route.GEL)).route)
        assertEquals("patch_apply", exported(domainEvent(route = Route.PATCH_APPLY)).route)
        assertEquals("patch_remove", exported(domainEvent(route = Route.PATCH_REMOVE)).route)
        assertEquals("antiandrogen", exported(domainEvent(route = Route.ANTIANDROGEN)).route)
    }

    @Test
    fun `all Domain esters export exact protocol spellings`() {
        assertEquals("E2", exported(domainEvent(ester = Ester.E2)).ester)
        assertEquals("EB", exported(domainEvent(ester = Ester.EB)).ester)
        assertEquals("EV", exported(domainEvent(ester = Ester.EV)).ester)
        assertEquals("EC", exported(domainEvent(ester = Ester.EC)).ester)
        assertEquals("EN", exported(domainEvent(ester = Ester.EN)).ester)
    }

    @Test
    fun `unrepresentable Domain Instant returns explicit export failure`() {
        val result = MahiroV1DoseEventAdapter().fromDomain(
            domainEvent().copy(occurredAt = Instant.MAX)
        )

        assertTrue(result is MahiroV1ExportMappingResult.Failure)
        val error = (result as MahiroV1ExportMappingResult.Failure).error
        assertTrue(error is MahiroV1DomainMappingError.UnrepresentableInstant)
        assertEquals(Instant.MAX, (error as MahiroV1DomainMappingError.UnrepresentableInstant).value)
        assertTrue(error.error is LegacyTimeError.Overflow)
    }

    @Test
    fun `representable Domain round trip preserves protocol fields and applies import defaults`() {
        val original = domainEvent(
            route = Route.PATCH_APPLY,
            ester = Ester.EC,
            extras = mapOf(ExtraKey.RELEASE_RATE_UG_PER_DAY to 100.0)
        ).copy(
            zoneId = java.time.ZoneId.of("Asia/Shanghai"),
            localDate = java.time.LocalDate.of(2026, 8, 10),
            slotId = UUID(0L, 44L),
            source = DoseEventSource.WEAR,
            revision = 9L
        )

        val imported = imported(exported(original))

        assertEquals(original.id, imported.id)
        assertEquals(original.route, imported.route)
        assertEquals(original.ester, imported.ester)
        assertEquals(original.occurredAt, imported.occurredAt)
        assertEquals(original.doseMG, imported.doseMG, 0.0)
        assertEquals(original.extras, imported.extras)
        assertNull(imported.zoneId)
        assertNull(imported.localDate)
        assertNull(imported.slotId)
        assertEquals(DoseEventSource.JSON_V1, imported.source)
        assertEquals(DoseEventStatus.RECORDED, imported.status)
        assertEquals(1L, imported.revision)
    }

    @Test
    fun `new boundary matches legacy facade for synthetic v1 event semantics`() {
        val json = """
            {
              "weight": 55,
              "events": [{
                "id": "$EVENT_ID",
                "route": "sublingual",
                "ester": "E2",
                "timeH": 492244.25,
                "doseMG": 2.0,
                "extras": {
                  "sublingualTier": 1.0,
                  "sublingualTheta": 0.4,
                  "concentrationMgMl": 10.0,
                  "areaCm2": 25.0,
                  "releaseRateUgPerDay": 50.0,
                  "antiAndrogenType": 2.0
                }
              }]
            }
        """.trimIndent()

        val decoded = success(MahiroV1Codec().decode(json))
        val domain = imported(decoded.document.events.single())
        val legacy = MahiroJsonFormat.parseImport(json).events.single()

        assertEquals(55.0, decoded.document.weight)
        assertEquals(legacy.id, domain.id)
        assertEquals(legacy.route, domain.route)
        assertEquals(legacy.ester, domain.ester)
        assertEquals(legacy.timeH, domain.occurredAt.toEpochMilli() / 3_600_000.0, 0.0)
        assertEquals(legacy.doseMG, domain.doseMG, 0.0)
        assertEquals(legacy.extras.values.toList(), domain.extras.values.toList())
    }

    private fun dto(
        id: String? = EVENT_ID.toString(),
        route: String = "oral",
        ester: String = "E2",
        timeH: Double = 100.0,
        extras: Map<String, Double> = emptyMap()
    ) = MahiroV1DoseEventDto(
        id = id,
        route = route,
        ester = ester,
        timeH = timeH,
        doseMG = 2.0,
        extras = extras
    )

    private fun domainEvent(
        route: Route = Route.ORAL,
        ester: Ester = Ester.E2,
        extras: Map<ExtraKey, Double> = emptyMap()
    ) = DoseEvent(
        id = EVENT_ID,
        route = route,
        occurredAt = Instant.ofEpochMilli(1_700_000_000_125L),
        zoneId = null,
        localDate = null,
        doseMG = 2.0,
        ester = ester,
        extras = extras,
        slotId = null,
        source = DoseEventSource.MANUAL,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )

    private fun imported(
        dto: MahiroV1DoseEventDto,
        adapter: MahiroV1DoseEventAdapter = MahiroV1DoseEventAdapter()
    ): DoseEvent {
        val result = adapter.toDomain(dto)
        assertTrue(result is MahiroV1ImportMappingResult.Success)
        return (result as MahiroV1ImportMappingResult.Success).event
    }

    private fun importFailure(dto: MahiroV1DoseEventDto): MahiroV1DomainMappingError {
        val result = MahiroV1DoseEventAdapter().toDomain(dto)
        assertTrue(result is MahiroV1ImportMappingResult.Failure)
        return (result as MahiroV1ImportMappingResult.Failure).error
    }

    private fun exported(event: DoseEvent): MahiroV1DoseEventDto {
        val result = MahiroV1DoseEventAdapter().fromDomain(event)
        assertTrue(result is MahiroV1ExportMappingResult.Success)
        return (result as MahiroV1ExportMappingResult.Success).event
    }

    private fun success(result: MahiroV1DecodeResult): MahiroV1DecodeResult.Success {
        assertTrue(result is MahiroV1DecodeResult.Success)
        return result as MahiroV1DecodeResult.Success
    }

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("59e6a6da-ee9b-44d2-8089-0db8943488fc")
        val ALL_WIRE_EXTRAS = linkedMapOf(
            "sublingualTier" to 1.0,
            "sublingualTheta" to 0.4,
            "concentrationMgMl" to 10.0,
            "areaCm2" to 25.0,
            "releaseRateUgPerDay" to 50.0,
            "antiAndrogenType" to 2.0
        )
        val ALL_DOMAIN_EXTRAS = linkedMapOf(
            ExtraKey.SUBLINGUAL_TIER to 1.0,
            ExtraKey.SUBLINGUAL_THETA to 0.4,
            ExtraKey.CONCENTRATION_MG_ML to 10.0,
            ExtraKey.AREA_CM2 to 25.0,
            ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0,
            ExtraKey.ANTI_ANDROGEN_TYPE to 2.0
        )
    }
}
