package io.github.yuninggu.evolune.data.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class LegacyPlanTimeParserTest {
    private val planId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun emptySqlStringReturnsEmptyEntries() {
        val parsed = success("")

        assertTrue(parsed.entries.isEmpty())
        assertEquals("", parsed.rawTimeOfDay)
    }

    @Test
    fun emptyJsonArrayReturnsEmptyEntries() {
        val parsed = success("[]")

        assertTrue(parsed.entries.isEmpty())
        assertEquals("[]", parsed.rawTimeOfDay)
    }

    @Test
    fun singleMinuteTimeParsesWithoutChangingOriginalValue() {
        val entry = success("[\"08:30\"]").entries.single()

        assertEquals("08:30", entry.originalValue)
        assertEquals(LocalTime.of(8, 30), entry.parsedLocalTime)
        assertEquals("08:30", entry.canonicalLocalTime)
    }

    @Test
    fun explicitZeroSecondsCanonicalizeToMinutePrecision() {
        val entry = success("[\"08:30:00\"]").entries.single()

        assertEquals("08:30:00", entry.originalValue)
        assertEquals(LocalTime.of(8, 30), entry.parsedLocalTime)
        assertEquals("08:30", entry.canonicalLocalTime)
    }

    @Test
    fun explicitZeroNanosecondsCanonicalizeToMinutePrecision() {
        val entry = success("[\"08:30:00.000000000\"]").entries.single()

        assertEquals("08:30:00.000000000", entry.originalValue)
        assertEquals(LocalTime.of(8, 30), entry.parsedLocalTime)
        assertEquals("08:30", entry.canonicalLocalTime)
    }

    @Test
    fun multipleTimesPreserveOriginalOrder() {
        val parsed = success("[\"22:00\",\"06:00\",\"12:15\"]")

        assertEquals(
            listOf("22:00", "06:00", "12:15"),
            parsed.entries.map { it.originalValue }
        )
    }

    @Test
    fun duplicateTimesRemainDistinctEntries() {
        val parsed = success("[\"08:30\",\"08:30\"]")

        assertEquals(2, parsed.entries.size)
        assertEquals(parsed.entries[0].parsedLocalTime, parsed.entries[1].parsedLocalTime)
        assertNotEquals(parsed.entries[0].slotId, parsed.entries[1].slotId)
    }

    @Test
    fun positionsAreZeroBasedAndContinuous() {
        val parsed = success("[\"00:00\",\"08:30\",\"23:59\"]")

        assertEquals(listOf(0, 1, 2), parsed.entries.map { it.position })
    }

    @Test
    fun parsedResultRetainsPlanAndRawJsonContext() {
        val rawTimeOfDay = "[\"08:30\"]"
        val parsed = success(rawTimeOfDay)

        assertEquals(planId, parsed.planId)
        assertEquals(rawTimeOfDay, parsed.rawTimeOfDay)
    }

    @Test
    fun fixedUuidV5VectorUsesHardcodedExpectedId() {
        val entry = success("[\"08:30\"]").entries.single()

        assertEquals(
            UUID.fromString("17d1fd14-9d70-5344-beaa-0b158c9f62f4"),
            entry.slotId
        )
    }

    @Test
    fun malformedJsonReturnsStructuredFailure() {
        val rawTimeOfDay = "[\"08:30\""

        assertEquals(
            LegacyMigrationError.InvalidTimeOfDayJson(
                planId,
                rawTimeOfDay,
                TimeOfDayJsonFailure.MALFORMED
            ),
            failure(rawTimeOfDay)
        )
    }

    @Test
    fun jsonObjectRootIsNotAcceptedAsAnArray() {
        val rawTimeOfDay = "{\"time\":\"08:30\"}"

        assertEquals(
            LegacyMigrationError.InvalidTimeOfDayJson(
                planId,
                rawTimeOfDay,
                TimeOfDayJsonFailure.ROOT_NOT_ARRAY
            ),
            failure(rawTimeOfDay)
        )
    }

    @Test
    fun numericArrayElementReturnsItsPositionAndKind() {
        assertEquals(
            LegacyMigrationError.TimeOfDayElementNotString(
                planId,
                1,
                "123",
                JsonElementKind.NUMBER
            ),
            failure("[\"08:30\",123]")
        )
    }

    @Test
    fun nullArrayElementReturnsItsPositionAndKind() {
        assertEquals(
            LegacyMigrationError.TimeOfDayElementNotString(
                planId,
                0,
                "null",
                JsonElementKind.NULL
            ),
            failure("[null]")
        )
    }

    @Test
    fun emptyStringElementIsAnInvalidLocalTime() {
        assertEquals(
            LegacyMigrationError.InvalidLocalTime(planId, 0, ""),
            failure("[\"\"]")
        )
    }

    @Test
    fun malformedLocalTimeReturnsOriginalValueAndPosition() {
        assertEquals(
            LegacyMigrationError.InvalidLocalTime(planId, 0, "not-a-time"),
            failure("[\"not-a-time\"]")
        )
    }

    @Test
    fun surroundingWhitespaceIsNotTrimmedOrRepaired() {
        assertEquals(
            LegacyMigrationError.InvalidLocalTime(planId, 0, " 08:30 "),
            failure("[\" 08:30 \"]")
        )
    }

    @Test
    fun nonZeroSecondsFailWithoutTruncation() {
        assertEquals(
            LegacyMigrationError.NonMinuteLocalTime(
                planId,
                0,
                "20:30:15",
                LocalTime.of(20, 30, 15)
            ),
            failure("[\"20:30:15\"]")
        )
    }

    @Test
    fun nonZeroNanosecondsFailWithoutRounding() {
        assertEquals(
            LegacyMigrationError.NonMinuteLocalTime(
                planId,
                0,
                "08:30:00.500",
                LocalTime.of(8, 30, 0, 500_000_000)
            ),
            failure("[\"08:30:00.500\"]")
        )
    }

    @Test
    fun midnightIsAccepted() {
        val entry = success("[\"00:00\"]").entries.single()

        assertEquals(LocalTime.MIDNIGHT, entry.parsedLocalTime)
        assertEquals("00:00", entry.canonicalLocalTime)
    }

    @Test
    fun lastMinuteOfDayIsAccepted() {
        val entry = success("[\"23:59\"]").entries.single()

        assertEquals(LocalTime.of(23, 59), entry.parsedLocalTime)
        assertEquals("23:59", entry.canonicalLocalTime)
    }

    @Test
    fun defaultLocaleDoesNotAffectParsingOrIdsAndIsRestored() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val first = success("[\"08:30:00\"]")
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val second = success("[\"08:30:00\"]")

            assertEquals(first, second)
        } finally {
            Locale.setDefault(originalLocale)
        }
        assertEquals(originalLocale, Locale.getDefault())
    }

    @Test
    fun defaultTimeZoneDoesNotAffectParsingOrIdsAndIsRestored() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val first = success("[\"08:30:00\"]")
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val second = success("[\"08:30:00\"]")

            assertEquals(first, second)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
        assertEquals(originalTimeZone, TimeZone.getDefault())
    }

    private fun success(rawTimeOfDay: String): ParsedLegacyPlanTimes {
        val result = LegacyPlanTimeParser.parse(planId, rawTimeOfDay)
        assertTrue(result is LegacyMigrationResult.Success)
        return (result as LegacyMigrationResult.Success).value
    }

    private fun failure(rawTimeOfDay: String): LegacyMigrationError {
        val result = LegacyPlanTimeParser.parse(planId, rawTimeOfDay)
        assertTrue(result is LegacyMigrationResult.Failure)
        return (result as LegacyMigrationResult.Failure).error
    }
}
