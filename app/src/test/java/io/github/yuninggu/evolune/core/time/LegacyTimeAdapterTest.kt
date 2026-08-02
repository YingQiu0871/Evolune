package io.github.yuninggu.evolune.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class LegacyTimeAdapterTest {
    @Test
    fun zeroIntegerFractionMillisecondAndNegativeValuesConvertWithMathRound() {
        assertEquals(0L, millis(0.0))
        assertEquals(43_200_000L, millis(12.0))
        assertEquals(5_400_000L, millis(1.5))
        assertEquals(1L, millis(1.0 / LegacyTimeAdapter.MILLIS_PER_HOUR))
        assertEquals(-5_400_000L, millis(-1.5))
        assertEquals(0L, millis(-0.4 / LegacyTimeAdapter.MILLIS_PER_HOUR))
    }

    @Test
    fun distantHistoryAndFutureRemainRepresentable() {
        assertTrue(millis(-2_000_000_000.25) < 0)
        assertTrue(millis(2_000_000_000.25) > 0)
    }

    @Test
    fun timeHMillisAndInstantRoundTripsStayWithinOneMillisecond() {
        val inputs = listOf(
            -2_000_000_000.25,
            -1.5,
            0.0,
            1.0 / LegacyTimeAdapter.MILLIS_PER_HOUR,
            123_456.789_012,
            2_000_000_000.25
        )
        val toleranceHours = 1.0 / LegacyTimeAdapter.MILLIS_PER_HOUR

        inputs.forEach { input ->
            val epochMillis = millis(input)
            val fromMillis = value(LegacyTimeAdapter.epochMillisToTimeH(epochMillis))
            val instant = value(LegacyTimeAdapter.timeHToInstant(input))
            val fromInstant = value(LegacyTimeAdapter.instantToTimeH(instant))

            assertTrue(abs(input - fromMillis) <= toleranceHours)
            assertTrue(abs(input - fromInstant) <= toleranceHours)
            assertEquals(epochMillis, instant.toEpochMilli())
        }
    }

    @Test
    fun nonFiniteInputsReturnSpecificErrors() {
        assertNonFinite(Double.NaN, NonFiniteKind.NAN)
        assertNonFinite(Double.POSITIVE_INFINITY, NonFiniteKind.POSITIVE_INFINITY)
        assertNonFinite(Double.NEGATIVE_INFINITY, NonFiniteKind.NEGATIVE_INFINITY)
    }

    @Test
    fun finiteMultiplicationOverflowReturnsOverflow() {
        val result = LegacyTimeAdapter.timeHToEpochMillis(Double.MAX_VALUE)
        assertTrue(result is LegacyTimeResult.Failure)
        assertTrue((result as LegacyTimeResult.Failure).error is LegacyTimeError.Overflow)
    }

    @Test
    fun positiveAndNegativeLongOverflowsAreRejectedBeforeMathRoundSaturation() {
        val upperHours = (-Long.MIN_VALUE.toDouble()) / LegacyTimeAdapter.MILLIS_PER_HOUR
        val lowerHours = Long.MIN_VALUE.toDouble() / LegacyTimeAdapter.MILLIS_PER_HOUR
        val positiveOverflow = LegacyTimeAdapter.timeHToEpochMillis(Math.nextUp(upperHours))
        val negativeOverflow = LegacyTimeAdapter.timeHToEpochMillis(Math.nextDown(lowerHours))

        assertOutOfRange(positiveOverflow)
        assertOutOfRange(negativeOverflow)
    }

    @Test
    fun closestRepresentableLegalLongBoundariesAreAccepted() {
        val upperHours = (-Long.MIN_VALUE.toDouble()) / LegacyTimeAdapter.MILLIS_PER_HOUR
        val lowerHours = Long.MIN_VALUE.toDouble() / LegacyTimeAdapter.MILLIS_PER_HOUR
        val upperMillis = millis(upperHours)
        val lowerMillis = millis(lowerHours)

        assertTrue(upperMillis in 1 until Long.MAX_VALUE)
        assertTrue(lowerMillis in Long.MIN_VALUE until 0)
        assertTrue(value(LegacyTimeAdapter.epochMillisToTimeH(Long.MAX_VALUE)).isFinite())
        assertTrue(value(LegacyTimeAdapter.epochMillisToTimeH(Long.MIN_VALUE)).isFinite())
    }

    @Test
    fun invalidValuesAreNotClampedOrReplacedWithEpochZero() {
        val positive = LegacyTimeAdapter.timeHToEpochMillis(
            Math.nextUp((-Long.MIN_VALUE.toDouble()) / LegacyTimeAdapter.MILLIS_PER_HOUR)
        )
        val negative = LegacyTimeAdapter.timeHToEpochMillis(
            Math.nextDown(Long.MIN_VALUE.toDouble() / LegacyTimeAdapter.MILLIS_PER_HOUR)
        )

        assertTrue(positive is LegacyTimeResult.Failure)
        assertTrue(negative is LegacyTimeResult.Failure)
    }

    @Test
    fun explicitZoneConvertsOrdinaryLocalDateTime() {
        val result = value(
            LegacyTimeAdapter.localDateTimeToInstant(
                LocalDateTime.of(2024, 1, 15, 12, 0),
                ZoneId.of("America/New_York")
            )
        )
        assertEquals(Instant.parse("2024-01-15T17:00:00Z"), result)
    }

    @Test
    fun dstGapUsesJavaAtZoneForwardAdjustment() {
        val result = value(
            LegacyTimeAdapter.localDateTimeToInstant(
                LocalDateTime.of(2024, 3, 10, 2, 30),
                ZoneId.of("America/New_York")
            )
        )
        assertEquals(Instant.parse("2024-03-10T07:30:00Z"), result)
    }

    @Test
    fun dstOverlapUsesJavaAtZoneEarlierOffset() {
        val result = value(
            LegacyTimeAdapter.localDateTimeToInstant(
                LocalDateTime.of(2024, 11, 3, 1, 30),
                ZoneId.of("America/New_York")
            )
        )
        assertEquals(Instant.parse("2024-11-03T05:30:00Z"), result)
    }

    @Test
    fun localeAndDefaultTimeZoneDoNotAffectAbsoluteConversions() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val first = value(LegacyTimeAdapter.timeHToInstant(123_456.789))
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val second = value(LegacyTimeAdapter.timeHToInstant(123_456.789))
            assertEquals(first, second)
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun millis(timeH: Double): Long =
        value(LegacyTimeAdapter.timeHToEpochMillis(timeH))

    private fun assertNonFinite(value: Double, expectedKind: NonFiniteKind) {
        val result = LegacyTimeAdapter.timeHToEpochMillis(value)
        assertTrue(result is LegacyTimeResult.Failure)
        val error = (result as LegacyTimeResult.Failure).error
        assertTrue(error is LegacyTimeError.NonFinite)
        assertEquals(expectedKind, (error as LegacyTimeError.NonFinite).kind)
    }

    private fun assertOutOfRange(result: LegacyTimeResult<Long>) {
        assertTrue(result is LegacyTimeResult.Failure)
        assertTrue((result as LegacyTimeResult.Failure).error is LegacyTimeError.OutOfRange)
    }

    private fun <T> value(result: LegacyTimeResult<T>): T {
        assertTrue(result is LegacyTimeResult.Success)
        return (result as LegacyTimeResult.Success).value
    }
}
