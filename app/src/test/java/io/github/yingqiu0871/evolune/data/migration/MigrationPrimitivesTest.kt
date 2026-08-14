package io.github.yingqiu0871.evolune.data.migration

import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeError
import io.github.yingqiu0871.evolune.core.time.NonFiniteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MigrationPrimitivesTest {
    private val eventId = UUID.fromString("10000000-0000-0000-0000-000000000001")

    @Test
    fun epochZeroMapsToZeroMillis() {
        assertEquals(0L, success(0.0))
    }

    @Test
    fun positiveTimeHMapsToExpectedMillis() {
        assertEquals(45_000_000L, success(12.5))
    }

    @Test
    fun negativeTimeHMapsToExpectedMillis() {
        assertEquals(-4_500_000L, success(-1.25))
    }

    @Test
    fun millisecondPrecisionVectorRoundTripsExactly() {
        val timeH = 1_700_000_000_123L / LegacyTimeAdapter.MILLIS_PER_HOUR

        assertEquals(1_700_000_000_123L, success(timeH))
    }

    @Test
    fun positiveValueImmediatelyAboveHalfMillisecondRoundsUp() {
        val timeH = Math.nextUp(0.5 / LegacyTimeAdapter.MILLIS_PER_HOUR)

        assertEquals(1L, success(timeH))
    }

    @Test
    fun negativeValueImmediatelyBelowHalfMillisecondRoundsDown() {
        val timeH = Math.nextDown(-0.5 / LegacyTimeAdapter.MILLIS_PER_HOUR)

        assertEquals(-1L, success(timeH))
    }

    @Test
    fun nanReturnsStructuredNonFiniteFailure() {
        assertNonFinite(Double.NaN, NonFiniteKind.NAN)
    }

    @Test
    fun positiveInfinityReturnsStructuredNonFiniteFailure() {
        assertNonFinite(Double.POSITIVE_INFINITY, NonFiniteKind.POSITIVE_INFINITY)
    }

    @Test
    fun negativeInfinityReturnsStructuredNonFiniteFailure() {
        assertNonFinite(Double.NEGATIVE_INFINITY, NonFiniteKind.NEGATIVE_INFINITY)
    }

    @Test
    fun positiveEpochMillisRangeOverflowReturnsFailure() {
        val upperHours = (-Long.MIN_VALUE.toDouble()) / LegacyTimeAdapter.MILLIS_PER_HOUR
        val error = failure(Math.nextUp(upperHours))

        assertTrue(error.cause is LegacyTimeError.OutOfRange)
    }

    @Test
    fun negativeEpochMillisRangeOverflowReturnsFailure() {
        val lowerHours = Long.MIN_VALUE.toDouble() / LegacyTimeAdapter.MILLIS_PER_HOUR
        val error = failure(Math.nextDown(lowerHours))

        assertTrue(error.cause is LegacyTimeError.OutOfRange)
    }

    @Test
    fun positiveMultiplicationOverflowReturnsFailure() {
        val error = failure(Double.MAX_VALUE)

        assertTrue(error.cause is LegacyTimeError.Overflow)
    }

    @Test
    fun negativeMultiplicationOverflowReturnsFailure() {
        val error = failure(-Double.MAX_VALUE)

        assertTrue(error.cause is LegacyTimeError.Overflow)
    }

    @Test
    fun failureRetainsEventIdAndRawTimeH() {
        val error = failure(Double.NaN)

        assertEquals(eventId, error.eventId)
        assertTrue(error.rawTimeH.isNaN())
    }

    @Test
    fun wrapperMatchesHardcodedLegacyAdapterVector() {
        assertEquals(444_444_440_400L, success(123_456.789))
    }

    private fun assertNonFinite(rawTimeH: Double, kind: NonFiniteKind) {
        val error = failure(rawTimeH)
        assertTrue(error.cause is LegacyTimeError.NonFinite)
        assertEquals(kind, (error.cause as LegacyTimeError.NonFinite).kind)
    }

    private fun success(rawTimeH: Double): Long {
        val result = legacyTimeHToOccurredAtEpochMillis(eventId, rawTimeH)
        assertTrue(result is LegacyMigrationResult.Success)
        return (result as LegacyMigrationResult.Success).value
    }

    private fun failure(rawTimeH: Double): LegacyMigrationError.InvalidEventTimeH {
        val result = legacyTimeHToOccurredAtEpochMillis(eventId, rawTimeH)
        assertTrue(result is LegacyMigrationResult.Failure)
        val error = (result as LegacyMigrationResult.Failure).error
        assertTrue(error is LegacyMigrationError.InvalidEventTimeH)
        return error as LegacyMigrationError.InvalidEventTimeH
    }
}
