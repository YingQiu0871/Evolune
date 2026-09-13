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
     * The single decision point for how an authoritative **actual intake** timestamp is
     * rendered (A-03-UI-R1).
     *
     * The rendered zone is the event's own persisted zone, or the current display zone when the
     * event has none. The full-date decision compares the *actual rendered local date* with the
     * history entry's display date:
     *
     * - same rendered local date → short time (`HH:mm`);
     * - different rendered local date → full date and time (`yyyy-MM-dd HH:mm`).
     *
     * The decision deliberately uses neither `crossesLocalDateBoundary` nor match provenance nor
     * the persisted planned `localDate`: the real Reminder writer persists the *planned* day
     * together with the *actual* confirmation instant, so a dose planned for 23:00 and confirmed
     * at 00:30 the next day is an exact slot/date match whose intake still happened on the next
     * local date. Both the matched and the unmatched card must go through this function.
     */
    fun actualTimestampPresentation(
        occurredAt: Instant,
        persistedZoneId: ZoneId?,
        displayZone: ZoneId,
        entryDisplayDate: LocalDate
    ): HistoryActualTimeUiModel {
        val renderedZone = persistedZoneId ?: displayZone
        return HistoryActualTimeUiModel(
            instant = occurredAt,
            zone = renderedZone,
            needsFullDate = occurredAt.atZone(renderedZone).toLocalDate() != entryDisplayDate
        )
    }

    /**
     * Timestamp text for an authoritative actual intake.
     *
     * [needsFullDate] must come from [actualTimestampPresentation]; this function never
     * re-derives the historical date attribution.
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
