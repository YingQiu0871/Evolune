package io.github.yingqiu0871.evolune.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * V17 Phase E E2.3 — strict canonical import failure matrix (frozen contract §5/§11–§18):
 * every structural or semantic violation is rejected before any write.
 */
class PortableJsonStrictDecoderTest {

    private fun eventJson(
        id: String = "\"00000000-0000-0000-0000-000000000001\"",
        actualTime: String = "\"2026-09-15T09:00:00.000Z\"",
        localDate: String = "\"2026-09-15\"",
        zoneId: String = "\"Asia/Tokyo\"",
        route: String = "\"INJECTION\"",
        ester: String = "\"EV\"",
        dose: String = "5.0",
        extras: String = "{}",
        slotId: String = "null",
        source: String = "\"MANUAL\"",
        status: String = "\"RECORDED\""
    ): String = """{
      "id": $id,
      "actual_time": $actualTime,
      "local_date": $localDate,
      "zone_id": $zoneId,
      "route": $route,
      "ester": $ester,
      "dose_mg": $dose,
      "extras": $extras,
      "slot_id": $slotId,
      "source": $source,
      "status": $status
    }"""

    private fun document(
        events: String,
        schema: String = "\"evolune-portable\"",
        version: String = "1",
        capturedAt: String = "\"2026-09-16T08:05:00.000Z\"",
        range: String = "\"ALL\""
    ): String = """{
  "schema": $schema,
  "version": $version,
  "captured_at": $capturedAt,
  "range": $range,
  "events": [$events]
}"""

    private fun decode(text: String): PortableJsonDecodeResult =
        PortableJsonCodec.decode(text.toByteArray(Charsets.UTF_8), maxEventCount = 100)

    private fun assertFailure(error: PortableJsonError, text: String) {
        val result = decode(text)
        assertTrue("expected failure for $text", result is PortableJsonDecodeResult.Failure)
        assertEquals(error, (result as PortableJsonDecodeResult.Failure).error)
    }

    @Test
    fun `a valid single event document decodes with all 11 fields`() {
        val result = decode(document(eventJson()))
        assertTrue(result is PortableJsonDecodeResult.Success)
        val document = (result as PortableJsonDecodeResult.Success).document
        assertEquals(Instant.parse("2026-09-16T08:05:00.000Z"), document.capturedAt)
        assertEquals(PortableExportRange.ALL, document.range)
        assertEquals(1, document.events.size)
        val event = document.events.single()
        assertEquals("00000000-0000-0000-0000-000000000001", event.id.toString())
        assertEquals(Instant.parse("2026-09-15T09:00:00.000Z"), event.occurredAt)
        assertEquals(LocalDate.of(2026, 9, 15), event.localDate)
        assertEquals(ZoneId.of("Asia/Tokyo"), event.zoneId)
        assertEquals(5.0, event.doseMG, 0.0)
    }

    @Test
    fun `nullable fields may be explicit null and extras remain empty`() {
        val result = decode(
            document(
                eventJson(
                    localDate = "null",
                    zoneId = "null",
                    slotId = "null"
                )
            )
        )
        assertTrue(result is PortableJsonDecodeResult.Success)
        val event = (result as PortableJsonDecodeResult.Success).document.events.single()
        assertEquals(null, event.localDate)
        assertEquals(null, event.zoneId)
        assertEquals(null, event.slotId)
    }

    @Test
    fun `an empty events array is a valid empty export`() {
        val result = decode(document(""))
        assertTrue(result is PortableJsonDecodeResult.Success)
        assertEquals(0, (result as PortableJsonDecodeResult.Success).document.events.size)
    }

    @Test
    fun `missing or wrong schema identifiers fail closed`() {
        assertFailure(PortableJsonError.INVALID_SCHEMA, document(eventJson()).replace("\"schema\": \"evolune-portable\",\n", ""))
        assertFailure(PortableJsonError.INVALID_SCHEMA, document(eventJson(), schema = "\"evolune-backup\""))
        assertFailure(PortableJsonError.INVALID_SCHEMA, document(eventJson(), schema = "1"))
    }

    @Test
    fun `version is required, integral and exactly one`() {
        assertFailure(
            PortableJsonError.MISSING_FIELD,
            document(eventJson()).replace("\"version\": 1,\n", "")
        )
        assertFailure(PortableJsonError.INVALID_FIELD_TYPE, document(eventJson(), version = "\"1\""))
        assertFailure(PortableJsonError.INVALID_FIELD_TYPE, document(eventJson(), version = "1.5"))
        assertFailure(PortableJsonError.UNSUPPORTED_VERSION, document(eventJson(), version = "2"))
        assertFailure(PortableJsonError.UNSUPPORTED_VERSION, document(eventJson(), version = "0"))
    }

    @Test
    fun `unknown top level and event fields fail closed`() {
        assertFailure(
            PortableJsonError.UNKNOWN_FIELD,
            document(eventJson()).replace("\"range\": \"ALL\",", "\"range\": \"ALL\",\n  \"weight\": 55.0,")
        )
        assertFailure(
            PortableJsonError.UNKNOWN_FIELD,
            document(
                eventJson().replace(
                    "      \"source\": \"MANUAL\",",
                    "      \"source\": \"MANUAL\",\n      \"planName\": \"x\","
                )
            )
        )
    }

    @Test
    fun `duplicate keys at any level fail closed`() {
        assertFailure(
            PortableJsonError.DUPLICATE_KEY,
            document(eventJson()).replace(
                "\"range\": \"ALL\",",
                "\"range\": \"ALL\",\n  \"range\": \"ALL\","
            )
        )
        assertFailure(
            PortableJsonError.DUPLICATE_KEY,
            document(eventJson().replace("\"dose_mg\": 5.0,", "\"dose_mg\": 5.0,\n      \"dose_mg\": 5.0,"))
        )
        assertFailure(
            PortableJsonError.DUPLICATE_KEY,
            document(eventJson(extras = "{\"AREA_CM2\": 10.0, \"AREA_CM2\": 11.0}"))
        )
        assertFailure(
            PortableJsonError.DUPLICATE_KEY,
            document(eventJson(extras = "{\"a\": 1.0, \"\\u0061\": 2.0}"))
        )
    }

    @Test
    fun `malformed json fails closed`() {
        assertFailure(PortableJsonError.MALFORMED, "{ not json")
        assertFailure(PortableJsonError.MALFORMED, document(eventJson()).dropLast(2))
    }

    @Test
    fun `missing required event fields fail closed`() {
        assertFailure(
            PortableJsonError.MISSING_FIELD,
            document(eventJson().replace("      \"dose_mg\": 5.0,\n", ""))
        )
        assertFailure(
            PortableJsonError.MISSING_FIELD,
            document(eventJson().replace("      \"local_date\": \"2026-09-15\",\n", ""))
        )
    }

    @Test
    fun `invalid event values fail closed`() {
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(id = "\"not-a-uuid\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(id = "\"00000000-0000-0000-0000-00000000000\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(actualTime = "\"2026-09-15T09:00:00Z\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(actualTime = "\"2026-09-15T09:00:00.0000Z\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(localDate = "\"2026-02-30\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(zoneId = "\"Not/AZone\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(route = "\"MISSED\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(ester = "\"EV2\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(source = "\"IMPORT\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(status = "\"SKIPPED\"")))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(slotId = "\"nope\"")))
    }

    @Test
    fun `wrong json types fail closed`() {
        assertFailure(PortableJsonError.INVALID_FIELD_TYPE, document(eventJson(id = "1")))
        assertFailure(PortableJsonError.INVALID_FIELD_TYPE, document(eventJson(dose = "\"5.0\"")))
        assertFailure(PortableJsonError.INVALID_FIELD_TYPE, document(eventJson(route = "null")))
        assertFailure(PortableJsonError.INVALID_FIELD_TYPE, document(eventJson(localDate = "20260915")))
        assertFailure(PortableJsonError.INVALID_FIELD_TYPE, document(eventJson(extras = "[]")))
    }

    @Test
    fun `non finite numbers and unknown extras keys fail closed`() {
        assertFailure(PortableJsonError.INVALID_FIELD_TYPE, document(eventJson(dose = "1e999")))
        assertFailure(
            PortableJsonError.UNKNOWN_FIELD,
            document(eventJson(extras = "{\"FUTURE_KEY\": 1.0}"))
        )
        assertFailure(
            PortableJsonError.INVALID_FIELD_TYPE,
            document(eventJson(extras = "{\"AREA_CM2\": \"10.0\"}"))
        )
    }

    @Test
    fun `special floating point literals never decode`() {
        val nanResult = decode(document(eventJson(dose = "NaN")))
        assertTrue(nanResult is PortableJsonDecodeResult.Failure)
        val infinityResult = decode(document(eventJson(dose = "Infinity")))
        assertTrue(infinityResult is PortableJsonDecodeResult.Failure)
    }

    @Test
    fun `captured_at and range are validated`() {
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(), capturedAt = "\"2026-09-16T08:05:00Z\""))
        assertFailure(PortableJsonError.MISSING_FIELD, document(eventJson()).replace("  \"captured_at\": \"2026-09-16T08:05:00.000Z\",\n", ""))
        assertFailure(PortableJsonError.INVALID_FIELD, document(eventJson(), range = "\"LAST_7_DAYS\""))
        assertFailure(PortableJsonError.MISSING_FIELD, document(eventJson()).replace("  \"range\": \"ALL\",\n", ""))
    }

    @Test
    fun `event count bound is enforced during decoding`() {
        val events = (1..3).joinToString(",") { index ->
            eventJson(id = "\"00000000-0000-0000-0000-00000000000$index\"")
        }
        val result = PortableJsonCodec.decode(
            document(events).toByteArray(Charsets.UTF_8),
            maxEventCount = 2
        )
        assertTrue(result is PortableJsonDecodeResult.Failure)
        assertEquals(
            PortableJsonError.TOO_MANY_EVENTS,
            (result as PortableJsonDecodeResult.Failure).error
        )
    }
}
