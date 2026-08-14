package io.github.yingqiu0871.evolune.utils

import io.github.yingqiu0871.evolune.pk.DoseEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * MahiroJsonFormat 单元测试
 */
class MahiroJsonFormatTest {

    // ---- parseImport ----

    @Test
    fun `parseImport - parses weight and events from valid JSON`() {
        val json = """
        {
          "meta": { "version": 1, "exportedAt": "2026-03-08T09:28:37.841Z" },
          "weight": 55,
          "events": [
            {
              "id": "59e6a6da-ee9b-44d2-8089-0db8943488fc",
              "route": "sublingual",
              "ester": "E2",
              "timeH": 492244,
              "doseMG": 2,
              "extras": { "sublingualTier": 1 }
            },
            {
              "id": "5ef1b068-b15e-48fd-b6bb-6cc8d2e890d4",
              "route": "injection",
              "ester": "EV",
              "timeH": 492489.5,
              "doseMG": 5,
              "extras": {}
            }
          ],
          "labResults": [],
          "doseTemplates": []
        }
        """.trimIndent()

        val result = MahiroJsonFormat.parseImport(json)

        assertEquals(55.0, result.weight)
        assertEquals(2, result.events.size)

        val sublingual = result.events[0]
        assertEquals(UUID.fromString("59e6a6da-ee9b-44d2-8089-0db8943488fc"), sublingual.id)
        assertEquals(Route.SUBLINGUAL, sublingual.route)
        assertEquals(Ester.E2, sublingual.ester)
        assertEquals(492244.0, sublingual.timeH, 0.0)
        assertEquals(2.0, sublingual.doseMG, 0.0)
        assertEquals(1.0, sublingual.extras[DoseEvent.ExtraKey.SUBLINGUAL_TIER])

        val injection = result.events[1]
        assertEquals(Route.INJECTION, injection.route)
        assertEquals(Ester.EV, injection.ester)
        assertEquals(492489.5, injection.timeH, 0.0)
        assertEquals(5.0, injection.doseMG, 0.0)
        assertTrue(injection.extras.isEmpty())
    }

    @Test
    fun `parseImport - missing weight returns null weight`() {
        val json = """
        {
          "events": []
        }
        """.trimIndent()

        val result = MahiroJsonFormat.parseImport(json)
        assertNull(result.weight)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `parseImport - missing events returns empty list`() {
        val json = """{ "weight": 60 }""".trimIndent()

        val result = MahiroJsonFormat.parseImport(json)
        assertEquals(60.0, result.weight)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `parseImport - unknown route skips event`() {
        val json = """
        {
          "events": [
            { "route": "unknown_route", "ester": "E2", "timeH": 100, "doseMG": 2, "extras": {} }
          ]
        }
        """.trimIndent()

        val result = MahiroJsonFormat.parseImport(json)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `parseImport - unknown ester skips event`() {
        val json = """
        {
          "events": [
            { "route": "oral", "ester": "UNKNOWN", "timeH": 100, "doseMG": 2, "extras": {} }
          ]
        }
        """.trimIndent()

        val result = MahiroJsonFormat.parseImport(json)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `parseImport - missing id generates a random UUID`() {
        val json = """
        {
          "events": [
            { "route": "oral", "ester": "EV", "timeH": 100, "doseMG": 2, "extras": {} }
          ]
        }
        """.trimIndent()

        val result = MahiroJsonFormat.parseImport(json)
        assertEquals(1, result.events.size)
        assertNotNull(result.events[0].id)
    }

    @Test
    fun `parseImport - all route types are parsed correctly`() {
        val routeTests = listOf(
            "injection" to Route.INJECTION,
            "oral" to Route.ORAL,
            "sublingual" to Route.SUBLINGUAL,
            "gel" to Route.GEL,
            "patch_apply" to Route.PATCH_APPLY,
            "patch_remove" to Route.PATCH_REMOVE
        )
        routeTests.forEach { (jsonRoute, expected) ->
            val json = """
            {
              "events": [
                { "route": "$jsonRoute", "ester": "E2", "timeH": 100, "doseMG": 2, "extras": {} }
              ]
            }
            """.trimIndent()
            val result = MahiroJsonFormat.parseImport(json)
            assertEquals("Route '$jsonRoute' should map to $expected", expected, result.events[0].route)
        }
    }

    @Test
    fun `parseImport - all ester types are parsed correctly`() {
        val esterTests = listOf("E2", "EB", "EV", "EC", "EN")
        esterTests.forEach { esterStr ->
            val json = """
            {
              "events": [
                { "route": "oral", "ester": "$esterStr", "timeH": 100, "doseMG": 2, "extras": {} }
              ]
            }
            """.trimIndent()
            val result = MahiroJsonFormat.parseImport(json)
            assertEquals(Ester.valueOf(esterStr), result.events[0].ester)
        }
    }

    @Test
    fun `parseImport - all extra keys are parsed correctly`() {
        val json = """
        {
          "events": [
            {
              "route": "sublingual",
              "ester": "E2",
              "timeH": 100,
              "doseMG": 2,
              "extras": {
                "sublingualTier": 2,
                "sublingualTheta": 0.5,
                "concentrationMgMl": 10.0,
                "areaCm2": 25.0,
                "releaseRateUgPerDay": 50.0
              }
            }
          ]
        }
        """.trimIndent()

        val result = MahiroJsonFormat.parseImport(json)
        assertEquals(1, result.events.size)
        val extras = result.events[0].extras
        assertEquals(2.0, extras[DoseEvent.ExtraKey.SUBLINGUAL_TIER])
        assertEquals(0.5, extras[DoseEvent.ExtraKey.SUBLINGUAL_THETA])
        assertEquals(10.0, extras[DoseEvent.ExtraKey.CONCENTRATION_MG_ML])
        assertEquals(25.0, extras[DoseEvent.ExtraKey.AREA_CM2])
        assertEquals(50.0, extras[DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY])
    }

    @Test(expected = Exception::class)
    fun `parseImport - throws on invalid JSON`() {
        MahiroJsonFormat.parseImport("not valid json")
    }

    // ---- generateExport ----

    @Test
    fun `generateExport - produces valid JSON with correct structure`() {
        val events = listOf(
            DoseEvent(
                id = UUID.fromString("59e6a6da-ee9b-44d2-8089-0db8943488fc"),
                route = Route.SUBLINGUAL,
                ester = Ester.E2,
                timeH = 492244.0,
                doseMG = 2.0,
                extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_TIER to 1.0)
            ),
            DoseEvent(
                id = UUID.fromString("5ef1b068-b15e-48fd-b6bb-6cc8d2e890d4"),
                route = Route.INJECTION,
                ester = Ester.EV,
                timeH = 492489.5,
                doseMG = 5.0,
                extras = emptyMap()
            )
        )

        val json = MahiroJsonFormat.generateExport(55.0, events)

        // Re-parse to verify
        val reimported = MahiroJsonFormat.parseImport(json)
        assertEquals(55.0, reimported.weight)
        assertEquals(2, reimported.events.size)

        val event0 = reimported.events[0]
        assertEquals(UUID.fromString("59e6a6da-ee9b-44d2-8089-0db8943488fc"), event0.id)
        assertEquals(Route.SUBLINGUAL, event0.route)
        assertEquals(Ester.E2, event0.ester)
        assertEquals(492244.0, event0.timeH, 0.0)
        assertEquals(2.0, event0.doseMG, 0.0)
        assertEquals(1.0, event0.extras[DoseEvent.ExtraKey.SUBLINGUAL_TIER])

        val event1 = reimported.events[1]
        assertEquals(Route.INJECTION, event1.route)
        assertEquals(Ester.EV, event1.ester)
    }

    @Test
    fun `generateExport - empty events list produces valid JSON`() {
        val json = MahiroJsonFormat.generateExport(70.0, emptyList())
        val reimported = MahiroJsonFormat.parseImport(json)
        assertEquals(70.0, reimported.weight)
        assertTrue(reimported.events.isEmpty())
    }

    @Test
    fun `generateExport - contains meta version field`() {
        val json = MahiroJsonFormat.generateExport(55.0, emptyList())
        assertTrue("JSON should contain meta version", json.contains("\"version\""))
    }

    @Test
    fun `roundtrip - export then import preserves all data`() {
        val original = listOf(
            DoseEvent(
                route = Route.GEL,
                ester = Ester.E2,
                timeH = 100.0,
                doseMG = 3.0,
                extras = mapOf(DoseEvent.ExtraKey.AREA_CM2 to 15.0)
            ),
            DoseEvent(
                route = Route.PATCH_APPLY,
                ester = Ester.E2,
                timeH = 200.0,
                doseMG = 100.0,
                extras = mapOf(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY to 100.0)
            )
        )

        val json = MahiroJsonFormat.generateExport(60.0, original)
        val imported = MahiroJsonFormat.parseImport(json)

        assertEquals(60.0, imported.weight)
        assertEquals(original.size, imported.events.size)
        original.zip(imported.events).forEach { (orig, imp) ->
            assertEquals(orig.id, imp.id)
            assertEquals(orig.route, imp.route)
            assertEquals(orig.ester, imp.ester)
            assertEquals(orig.timeH, imp.timeH, 0.001)
            assertEquals(orig.doseMG, imp.doseMG, 0.001)
            assertEquals(orig.extras, imp.extras)
        }
    }
}
