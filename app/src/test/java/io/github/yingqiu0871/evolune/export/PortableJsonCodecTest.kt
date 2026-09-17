package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.model.ExtraKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * V17 Phase E E2.1/E3.3 — canonical JSON v1 encoding shape (frozen contract §6/§10/§12/§15/§29):
 * ordered fields, explicit nulls, canonical numbers, lexical extras, UTF-8, LF, one final LF.
 */
class PortableJsonCodecTest {

    private val capturedAt = Instant.parse("2026-09-16T08:05:00.000Z")

    private fun encode(vararg events: PortableEvent): String {
        val bytes = PortableJsonCodec.encode(
            PortableExportRequest(PortableExportRange.ALL, PortableExportFormat.JSON, capturedAt),
            events.toList()
        )
        return String(requireNotNull(bytes), Charsets.UTF_8)
    }

    private fun portableEvent(
        localDate: java.time.LocalDate? = null,
        zoneId: java.time.ZoneId? = null,
        extras: Map<ExtraKey, Double> = emptyMap(),
        doseMG: Double = 2.0,
        occurredAt: Instant = Instant.parse("2026-09-15T09:00:00.000Z")
    ): PortableEvent = PortableEvent(
        id = eventId(42L),
        occurredAt = occurredAt,
        localDate = localDate,
        zoneId = zoneId,
        route = io.github.yingqiu0871.evolune.pk.Route.INJECTION,
        ester = io.github.yingqiu0871.evolune.pk.Ester.EV,
        doseMG = doseMG,
        extras = extras,
        slotId = null,
        source = io.github.yingqiu0871.evolune.core.model.DoseEventSource.MANUAL,
        status = io.github.yingqiu0871.evolune.core.model.DoseEventStatus.RECORDED
    )

    @Test
    fun `empty export keeps the frozen top level field order`() {
        val text = encode()
        assertEquals(
            listOf(
                "{",
                "  \"schema\": \"evolune-portable\",",
                "  \"version\": 1,",
                "  \"captured_at\": \"2026-09-16T08:05:00.000Z\",",
                "  \"range\": \"ALL\",",
                "  \"events\": []",
                "}"
            ),
            text.removeSuffix("\n").lines()
        )
    }

    @Test
    fun `event objects keep the frozen 11 field order with explicit nulls`() {
        val text = encode(portableEvent())
        val body = text.removeSuffix("\n").lines()
            .map { it.trim() }
            .filter { it.startsWith("\"") }
            .takeLast(11)
        assertEquals(
            listOf(
                "\"id\": \"00000000-0000-0000-0000-00000000002a\",",
                "\"actual_time\": \"2026-09-15T09:00:00.000Z\",",
                "\"local_date\": null,",
                "\"zone_id\": null,",
                "\"route\": \"INJECTION\",",
                "\"ester\": \"EV\",",
                "\"dose_mg\": 2.0,",
                "\"extras\": {},",
                "\"slot_id\": null,",
                "\"source\": \"MANUAL\",",
                "\"status\": \"RECORDED\""
            ),
            body
        )
    }

    @Test
    fun `extras keys are emitted in lexical order`() {
        val text = encode(
            portableEvent(
                extras = linkedMapOf(
                    ExtraKey.SUBLINGUAL_TIER to 3.0,
                    ExtraKey.ANTI_ANDROGEN_TYPE to 2.0,
                    ExtraKey.AREA_CM2 to 10.0
                )
            )
        )
        assertTrue(
            text.contains("\"extras\": {\"ANTI_ANDROGEN_TYPE\":2.0,\"AREA_CM2\":10.0,\"SUBLINGUAL_TIER\":3.0},")
        )
    }

    @Test
    fun `numbers use canonical locale independent double text`() {
        val text = encode(portableEvent(doseMG = 0.15))
        assertTrue(text.contains("\"dose_mg\": 0.15,"))
        assertTrue(encode(portableEvent(doseMG = 2.0)).contains("\"dose_mg\": 2.0,"))
        assertTrue(encode(portableEvent(doseMG = 1.0E7)).contains("\"dose_mg\": 1.0E7,"))
        assertFalse(encode(portableEvent(doseMG = 1.0E7)).contains("1,0E7"))
    }

    @Test
    fun `instants are UTC RFC3339 with exactly three fractional digits and terminal Z`() {
        val text = encode(portableEvent(occurredAt = Instant.parse("2026-09-15T09:00:00.000Z")))
        assertTrue(text.contains("\"actual_time\": \"2026-09-15T09:00:00.000Z\","))
    }

    @Test
    fun `the document is UTF-8 without BOM and ends with exactly one LF`() {
        val bytes = requireNotNull(
            PortableJsonCodec.encode(
                PortableExportRequest(PortableExportRange.ALL, PortableExportFormat.JSON, capturedAt),
                listOf(portableEvent())
            )
        )
        assertEquals('{'.code.toByte(), bytes.first())
        assertEquals(0x0A.toByte(), bytes.last())
        assertFalse("exactly one final LF", bytes[bytes.size - 2] == 0x0A.toByte())
        assertFalse(String(bytes, Charsets.UTF_8).contains("\r"))
        assertFalse(String(bytes, Charsets.UTF_8).contains("\uFEFF"))
    }

    @Test
    fun `encoding the same request and events twice is byte identical`() {
        val events = nineClassFixture().map { it.toPortableEvent() }
        val request = PortableExportRequest(PortableExportRange.ALL, PortableExportFormat.JSON, capturedAt)
        val first = PortableJsonCodec.encode(request, events)
        val second = PortableJsonCodec.encode(request, events)
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun `string literals escape control characters and quotes`() {
        assertEquals("\"a\\\"b\"", PortableJsonText.stringLiteral("a\"b"))
        assertEquals("\"a\\\\b\"", PortableJsonText.stringLiteral("a\\b"))
        assertEquals("\"a\\nb\"", PortableJsonText.stringLiteral("a\nb"))
        assertEquals("\"a\\tb\"", PortableJsonText.stringLiteral("a\tb"))
        assertEquals("\"a\\u0001b\"", PortableJsonText.stringLiteral("a\u0001b"))
        assertEquals("\"\\u0000\"", PortableJsonText.stringLiteral("\u0000"))
    }

    @Test
    fun `canonical instant rendering rejects sub millisecond precision`() {
        assertNull(PortableJsonText.formatInstant(Instant.parse("2026-09-15T09:00:00.000000001Z")))
        assertEquals(
            "2026-09-15T09:00:00.000Z",
            PortableJsonText.formatInstant(Instant.parse("2026-09-15T09:00:00Z"))
        )
    }

    @Test
    fun `canonical instant parsing is strict about shape and calendar validity`() {
        assertEquals(
            Instant.parse("2026-09-15T09:00:00.000Z"),
            PortableJsonText.parseInstant("2026-09-15T09:00:00.000Z")
        )
        assertNull(PortableJsonText.parseInstant("2026-09-15T09:00:00Z"))
        assertNull(PortableJsonText.parseInstant("2026-09-15T09:00:00.00Z"))
        assertNull(PortableJsonText.parseInstant("2026-09-15T09:00:00.000+00:00"))
        assertNull(PortableJsonText.parseInstant("2026-13-15T09:00:00.000Z"))
        assertNull(PortableJsonText.parseInstant("2026-02-30T09:00:00.000Z"))
    }
}
