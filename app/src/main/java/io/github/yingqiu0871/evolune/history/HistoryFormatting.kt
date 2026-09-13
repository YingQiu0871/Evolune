package io.github.yingqiu0871.evolune.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The single presentation formatting path for History (A-03 §13).
 *
 * Every timestamp that History renders — matched actual intake, unmatched actual intake and
 * the current-schedule time — goes through this object. Individual cards must not build
 * their own patterns, so the "full date + time for a delayed actual intake" rule cannot be
 * applied inconsistently.
 *
 * Rules:
 * - an actual intake timestamp is rendered in the event's own persisted zone when it has
 *   one, otherwise in the current display zone;
 * - when the actual intake's local date differs from the entry's display date (delayed
 *   reminder / Wear record), the **full date and time** are shown instead of a bare time;
 * - the current schedule time is always labelled as current schedule context by the caller;
 *   it is never presented as a historical planned time.
 */
object HistoryFormatting {

    private const val TIME_24H = "HH:mm"
    private const val TIME_12H = "hh:mm a"
    private const val FULL_DATE_TIME_24H = "yyyy-MM-dd HH:mm"
    private const val FULL_DATE_TIME_12H = "yyyy-MM-dd hh:mm a"

    /** Matches the existing record-item convention (`MedicationRecordItem.formatDose`). */
    fun dose(doseAmount: Double): String = if (doseAmount >= 1.0) {
        String.format(Locale.getDefault(), "%.1f %s", doseAmount, "mg")
    } else {
        String.format(Locale.getDefault(), "%.2f %s", doseAmount, "mg")
    }

    fun localDate(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    fun timeText(instant: Instant, zone: ZoneId, is24Hour: Boolean): String =
        formatter(if (is24Hour) TIME_24H else TIME_12H).format(instant.atZone(zone))

    fun fullDateTimeText(instant: Instant, zone: ZoneId, is24Hour: Boolean): String =
        formatter(if (is24Hour) FULL_DATE_TIME_24H else FULL_DATE_TIME_12H)
            .format(instant.atZone(zone))

    /**
     * Timestamp text for an authoritative actual intake.
     *
     * [needsFullDate] must come from the domain/presentation flags (`crossesLocalDateBoundary`
     * for matched entries); this function never re-derives the historical date attribution.
     */
    fun actualIntakeText(
        instant: Instant,
        zone: ZoneId,
        needsFullDate: Boolean,
        is24Hour: Boolean
    ): String = if (needsFullDate) {
        fullDateTimeText(instant, zone, is24Hour)
    } else {
        timeText(instant, zone, is24Hour)
    }

    /** Timestamp text for the *current* schedule context of an occurrence. */
    fun scheduleTimeText(
        instant: Instant,
        zone: ZoneId,
        needsFullDate: Boolean,
        is24Hour: Boolean
    ): String = if (needsFullDate) {
        fullDateTimeText(instant, zone, is24Hour)
    } else {
        timeText(instant, zone, is24Hour)
    }

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
}
