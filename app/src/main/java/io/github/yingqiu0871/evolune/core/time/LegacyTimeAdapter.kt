package io.github.yingqiu0871.evolune.core.time

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface LegacyTimeResult<out T> {
    data class Success<T>(val value: T) : LegacyTimeResult<T>
    data class Failure(val error: LegacyTimeError) : LegacyTimeResult<Nothing>
}

sealed interface LegacyTimeError {
    data class NonFinite(val value: Double, val kind: NonFiniteKind) : LegacyTimeError
    data class OutOfRange(val timeH: Double, val scaledMillis: Double) : LegacyTimeError
    data class Overflow(val operation: String) : LegacyTimeError
}

enum class NonFiniteKind {
    NAN,
    POSITIVE_INFINITY,
    NEGATIVE_INFINITY
}

object LegacyTimeAdapter {
    const val MILLIS_PER_HOUR: Double = 3_600_000.0
    private val minimumMillisInclusive = Long.MIN_VALUE.toDouble()
    private val maximumMillisExclusive = -minimumMillisInclusive

    fun timeHToEpochMillis(timeH: Double): LegacyTimeResult<Long> {
        if (!timeH.isFinite()) {
            val kind = when {
                timeH.isNaN() -> NonFiniteKind.NAN
                timeH > 0.0 -> NonFiniteKind.POSITIVE_INFINITY
                else -> NonFiniteKind.NEGATIVE_INFINITY
            }
            return LegacyTimeResult.Failure(LegacyTimeError.NonFinite(timeH, kind))
        }

        val scaledMillis = timeH * MILLIS_PER_HOUR
        if (!scaledMillis.isFinite()) {
            return LegacyTimeResult.Failure(
                LegacyTimeError.Overflow("timeH multiplication")
            )
        }
        if (
            scaledMillis < minimumMillisInclusive ||
            scaledMillis >= maximumMillisExclusive
        ) {
            return LegacyTimeResult.Failure(
                LegacyTimeError.OutOfRange(timeH, scaledMillis)
            )
        }

        return LegacyTimeResult.Success(Math.round(scaledMillis))
    }

    fun epochMillisToTimeH(epochMillis: Long): LegacyTimeResult<Double> =
        LegacyTimeResult.Success(epochMillis / MILLIS_PER_HOUR)

    fun timeHToInstant(timeH: Double): LegacyTimeResult<Instant> =
        when (val result = timeHToEpochMillis(timeH)) {
            is LegacyTimeResult.Success -> LegacyTimeResult.Success(
                Instant.ofEpochMilli(result.value)
            )
            is LegacyTimeResult.Failure -> result
        }

    fun instantToTimeH(instant: Instant): LegacyTimeResult<Double> = try {
        epochMillisToTimeH(instant.toEpochMilli())
    } catch (_: ArithmeticException) {
        LegacyTimeResult.Failure(LegacyTimeError.Overflow("Instant.toEpochMilli"))
    }

    fun localDateTimeToInstant(
        localDateTime: LocalDateTime,
        zoneId: ZoneId
    ): LegacyTimeResult<Instant> = try {
        LegacyTimeResult.Success(localDateTime.atZone(zoneId).toInstant())
    } catch (_: DateTimeException) {
        LegacyTimeResult.Failure(LegacyTimeError.Overflow("LocalDateTime.atZone"))
    }
}
