package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * V17 Phase E E2.2/E2.5/E7.3 — CSV v1 shape (frozen contract §24–§28): 16 columns, constant
 * planned_time/timing_delta, Table D medication mapping, RFC4180 quoting, LF, one final LF.
 */
class PortableCsvCodecTest {

    private fun encode(vararg events: PortableEvent): String {
        val bytes = PortableCsvCodec.encode(events.toList())
        return String(requireNotNull(bytes), Charsets.UTF_8)
    }

    private fun portableEvent(
        localDate: LocalDate? = LocalDate.of(2026, 9, 15),
        zoneId: java.time.ZoneId? = java.time.ZoneId.of("Asia/Tokyo"),
        route: Route = Route.INJECTION,
        ester: Ester = Ester.EV,
        doseMG: Double = 5.0,
        extras: Map<ExtraKey, Double> = emptyMap(),
        slotId: java.util.UUID? = null
    ): PortableEvent = PortableEvent(
        id = eventId(7L),
        occurredAt = Instant.parse("2026-09-15T09:00:00.000Z"),
        localDate = localDate,
        zoneId = zoneId,
        route = route,
        ester = ester,
        doseMG = doseMG,
        extras = extras,
        slotId = slotId,
        source = io.github.yingqiu0871.evolune.core.model.DoseEventSource.MANUAL,
        status = io.github.yingqiu0871.evolune.core.model.DoseEventStatus.RECORDED
    )

    @Test
    fun `the header is the frozen 16 column order`() {
        assertEquals(
            "schema_version,event_id,date,medication,dose,route,planned_time," +
                "actual_time,timing_delta,event_type,status,ester,zone_id,slot_id,source,extras_json",
            PortableCsvCodec.HEADER
        )
    }

    @Test
    fun `an empty export is the header plus exactly one final LF`() {
        val text = encode()
        assertEquals(PortableCsvCodec.HEADER + "\n", text)
    }

    @Test
    fun `a plain row keeps the frozen column order and constant columns`() {
        val text = encode(portableEvent(slotId = eventId(77L)))
        val row = text.removeSuffix("\n").lines()[1]
        assertEquals(
            "1,00000000-0000-0000-0000-000000000007,2026-09-15,EV,5.0,INJECTION,," +
                "2026-09-15T09:00:00.000Z,,recorded_intake,RECORDED,EV,Asia/Tokyo," +
                "00000000-0000-0000-0000-00000000004d,MANUAL,{}",
            row
        )
    }

    @Test
    fun `null date and null zone project to empty columns`() {
        val text = encode(portableEvent(localDate = null, zoneId = null))
        val row = text.removeSuffix("\n").lines()[1]
        val fields = row.split(',')
        assertEquals("", fields[2])
        assertEquals("", fields[12])
    }

    @Test
    fun `extras_json is compact, lexically ordered and RFC4180 quoted`() {
        val text = encode(
            portableEvent(
                extras = linkedMapOf(
                    ExtraKey.SUBLINGUAL_TIER to 3.0,
                    ExtraKey.ANTI_ANDROGEN_TYPE to 2.0
                )
            )
        )
        assertTrue(
            text.contains("\"{\"\"ANTI_ANDROGEN_TYPE\"\":2.0,\"\"SUBLINGUAL_TIER\"\":3.0}\"")
        )
    }

    @Test
    fun `medication mapping follows Table D for every non antiandrogen route`() {
        val esterByWire = mapOf(
            Ester.E2 to "E2",
            Ester.EB to "EB",
            Ester.EV to "EV",
            Ester.EC to "EC",
            Ester.EN to "EN"
        )
        val routes = listOf(
            Route.INJECTION,
            Route.ORAL,
            Route.SUBLINGUAL,
            Route.GEL,
            Route.PATCH_APPLY,
            Route.PATCH_REMOVE
        )
        routes.forEach { route ->
            esterByWire.forEach { (ester, wire) ->
                assertEquals(wire, PortableCsvCodec.medication(route, ester, emptyMap()))
            }
        }
    }

    @Test
    fun `medication mapping follows Table D for antiandrogen ordinals`() {
        val expected = mapOf(
            0.0 to "CPA",
            1.0 to "MPA",
            2.0 to "BICALUTAMIDE",
            3.0 to "SPIRONOLACTONE"
        )
        expected.forEach { (value, drug) ->
            assertEquals(
                drug,
                PortableCsvCodec.medication(
                    Route.ANTIANDROGEN,
                    Ester.E2,
                    mapOf(ExtraKey.ANTI_ANDROGEN_TYPE to value)
                )
            )
        }
    }

    @Test
    fun `medication mapping never guesses partial or unavailable identity`() {
        assertEquals(
            "",
            PortableCsvCodec.medication(Route.ANTIANDROGEN, Ester.E2, emptyMap())
        )
        assertEquals(
            "",
            PortableCsvCodec.medication(
                Route.ANTIANDROGEN,
                Ester.E2,
                mapOf(ExtraKey.ANTI_ANDROGEN_TYPE to 1.5)
            )
        )
        assertEquals(
            "",
            PortableCsvCodec.medication(
                Route.ANTIANDROGEN,
                Ester.E2,
                mapOf(ExtraKey.ANTI_ANDROGEN_TYPE to 4.0)
            )
        )
    }

    @Test
    fun `rfc4180 quoting doubles embedded quotes and quotes separators`() {
        assertEquals("plain", PortableCsvCodec.quoteField("plain"))
        assertEquals("\"a,b\"", PortableCsvCodec.quoteField("a,b"))
        assertEquals("\"a\"\"b\"", PortableCsvCodec.quoteField("a\"b"))
        assertEquals("\"a\nb\"", PortableCsvCodec.quoteField("a\nb"))
        assertEquals("\"a\rb\"", PortableCsvCodec.quoteField("a\rb"))
    }

    @Test
    fun `rows keep the caller provided deterministic order`() {
        val first = portableEvent().copy(id = eventId(1L), occurredAt = Instant.parse("2026-09-01T00:00:00.000Z"))
        val second = portableEvent().copy(id = eventId(2L), occurredAt = Instant.parse("2026-09-02T00:00:00.000Z"))
        val text = encode(first, second)
        val ids = text.removeSuffix("\n").lines().drop(1).map { it.split(',')[1] }
        assertEquals(listOf(eventId(1L).toString(), eventId(2L).toString()), ids)
    }
}
