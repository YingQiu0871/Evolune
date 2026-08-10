package io.github.yuninggu.evolune.external.mahiro.v1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MahiroV1CodecTest {
    @Test
    fun `minimal document accepts missing weight and events`() {
        val result = success(MahiroV1Codec().decode("{}"))

        assertEquals(null, result.document.weight)
        assertTrue(result.document.events.isEmpty())
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `full document preserves protocol fields and ignores unrelated top-level fields`() {
        val result = success(
            MahiroV1Codec().decode(
                """
                {
                  "meta": { "version": 99, "exportedAt": "ignored" },
                  "weight": 55.5,
                  "events": [{
                    "id": "59e6a6da-ee9b-44d2-8089-0db8943488fc",
                    "route": "sublingual",
                    "ester": "E2",
                    "timeH": 492244.25,
                    "doseMG": 2.5,
                    "extras": {
                      "sublingualTier": 2,
                      "unknown": 9
                    },
                    "unknownEventField": true
                  }],
                  "labResults": [{ "ignored": true }],
                  "doseTemplates": "ignored",
                  "unknownTopLevel": true
                }
                """.trimIndent()
            )
        )

        assertEquals(55.5, result.document.weight)
        assertEquals(
            MahiroV1DoseEventDto(
                id = "59e6a6da-ee9b-44d2-8089-0db8943488fc",
                route = "sublingual",
                ester = "E2",
                timeH = 492244.25,
                doseMG = 2.5,
                extras = linkedMapOf("sublingualTier" to 2.0, "unknown" to 9.0)
            ),
            result.document.events.single()
        )
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `malformed JSON returns document syntax failure`() {
        val result = MahiroV1Codec().decode("not valid JSON")

        assertTrue(result is MahiroV1DecodeResult.Failure)
        assertTrue(
            (result as MahiroV1DecodeResult.Failure).error is MahiroV1DocumentError.Syntax
        )
    }

    @Test
    fun `non-object document returns representation failure`() {
        val result = MahiroV1Codec().decode("[]")

        assertEquals(
            MahiroV1DocumentError.InvalidRepresentation("document must be an object"),
            failure(result)
        )
    }

    @Test
    fun `non-array events returns representation failure`() {
        val result = MahiroV1Codec().decode("""{ "events": {} }""")

        assertEquals(
            MahiroV1DocumentError.InvalidRepresentation("events must be an array or absent"),
            failure(result)
        )
    }

    @Test
    fun `non-number primitive weight remains absent-compatible null`() {
        val result = success(MahiroV1Codec().decode("""{ "weight": "unknown" }"""))

        assertEquals(null, result.document.weight)
    }

    @Test
    fun `malformed entries are diagnosed by input index while valid entries continue`() {
        val result = success(
            MahiroV1Codec().decode(
                """
                {
                  "events": [
                    7,
                    { "ester": "E2", "timeH": 1, "doseMG": 2 },
                    { "route": "oral", "ester": "E2", "timeH": 1, "doseMG": 2, "extras": [] },
                    { "route": "oral", "ester": "E2", "timeH": 1, "doseMG": 2 }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(1, result.document.events.size)
        assertEquals(
            listOf(
                MahiroV1EntryDiagnostic(0, MahiroV1EntryError.ExpectedObject),
                MahiroV1EntryDiagnostic(1, MahiroV1EntryError.MissingField("route")),
                MahiroV1EntryDiagnostic(2, MahiroV1EntryError.InvalidFieldType("extras"))
            ),
            result.diagnostics
        )
    }

    @Test
    fun `missing id and extras remain representable without codec UUID semantics`() {
        val result = success(
            MahiroV1Codec().decode(
                """{
                  "events": [{
                    "route": "oral",
                    "ester": "EV",
                    "timeH": 100,
                    "doseMG": 2
                  }]
                }"""
            )
        )

        assertEquals(null, result.document.events.single().id)
        assertTrue(result.document.events.single().extras.isEmpty())
    }

    @Test
    fun `wrong id type is an entry representation diagnostic`() {
        val result = success(
            MahiroV1Codec().decode(
                """{
                  "events": [{
                    "id": 7,
                    "route": "oral",
                    "ester": "EV",
                    "timeH": 100,
                    "doseMG": 2
                  }]
                }"""
            )
        )

        assertTrue(result.document.events.isEmpty())
        assertEquals(
            listOf(
                MahiroV1EntryDiagnostic(0, MahiroV1EntryError.InvalidFieldType("id"))
            ),
            result.diagnostics
        )
    }

    @Test
    fun `unknown and non-numeric extras preserve current ignore boundary`() {
        val result = success(
            MahiroV1Codec().decode(
                """{
                  "events": [{
                    "route": "oral",
                    "ester": "E2",
                    "timeH": 1,
                    "doseMG": 2,
                    "extras": {
                      "sublingualTier": "invalid",
                      "unknownNumeric": 4,
                      "unknownObject": {}
                    }
                  }]
                }"""
            )
        )

        assertEquals(
            mapOf("unknownNumeric" to 4.0),
            result.document.events.single().extras
        )
    }

    @Test
    fun `fixed clock export matches complete golden JSON`() {
        val codec = MahiroV1Codec(
            Clock.fixed(Instant.parse("2026-08-10T08:30:00Z"), ZoneOffset.UTC)
        )
        val document = MahiroV1DocumentDto(
            weight = 55.0,
            events = listOf(
                MahiroV1DoseEventDto(
                    id = "59e6a6da-ee9b-44d2-8089-0db8943488fc",
                    route = "sublingual",
                    ester = "E2",
                    timeH = 492244.0,
                    doseMG = 2.0,
                    extras = linkedMapOf(
                        "sublingualTier" to 1.0,
                        "sublingualTheta" to 0.4
                    )
                )
            )
        )

        assertEquals(
            """
            {
                "meta": {
                    "version": 1,
                    "exportedAt": "2026-08-10T08:30:00Z"
                },
                "weight": 55.0,
                "events": [
                    {
                        "id": "59e6a6da-ee9b-44d2-8089-0db8943488fc",
                        "route": "sublingual",
                        "ester": "E2",
                        "timeH": 492244.0,
                        "doseMG": 2.0,
                        "extras": {
                            "sublingualTier": 1.0,
                            "sublingualTheta": 0.4
                        }
                    }
                ],
                "labResults": [],
                "doseTemplates": []
            }
            """.trimIndent(),
            codec.encode(document)
        )
    }

    @Test
    fun `encode decode preserves event and extras order`() {
        val codec = MahiroV1Codec(
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        )
        val document = MahiroV1DocumentDto(
            weight = 60.0,
            events = listOf(
                event(id = "00000000-0000-0000-0000-000000000002", timeH = 2.0),
                event(id = "00000000-0000-0000-0000-000000000001", timeH = 1.0)
            )
        )

        val decoded = success(codec.decode(codec.encode(document))).document

        assertEquals(document.events.map { it.id }, decoded.events.map { it.id })
        assertEquals(document.events.map { it.timeH }, decoded.events.map { it.timeH })
        assertEquals(
            listOf("sublingualTier", "antiAndrogenType"),
            decoded.events.first().extras.keys.toList()
        )
    }

    private fun event(id: String, timeH: Double) = MahiroV1DoseEventDto(
        id = id,
        route = "oral",
        ester = "E2",
        timeH = timeH,
        doseMG = 2.0,
        extras = linkedMapOf(
            "sublingualTier" to 1.0,
            "antiAndrogenType" to 2.0
        )
    )

    private fun success(result: MahiroV1DecodeResult): MahiroV1DecodeResult.Success {
        assertTrue(result is MahiroV1DecodeResult.Success)
        return result as MahiroV1DecodeResult.Success
    }

    private fun failure(result: MahiroV1DecodeResult): MahiroV1DocumentError {
        assertTrue(result is MahiroV1DecodeResult.Failure)
        return (result as MahiroV1DecodeResult.Failure).error
    }
}
