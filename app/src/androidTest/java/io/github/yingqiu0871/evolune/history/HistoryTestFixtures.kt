package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.HistoricalScheduleTimeContext
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationActionAvailability
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceId
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.MedicationPresentation
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Synthetic domain fixtures for the History UI JVM tests (A-03 §20 instrumentation; verbatim copy of the unit-test fixture file, since androidTest cannot see that source set).
 *
 * They build authoritative projection values directly; the UI layer only ever sees these
 * through [HistoricalRange]/[HistoricalDay], exactly like production output.
 */
internal val TEST_UTC: ZoneId = ZoneOffset.UTC
internal val TEST_DAY: LocalDate = LocalDate.of(2025, 1, 5)
internal val TEST_MONTH_START: LocalDate = LocalDate.of(2025, 1, 1)

internal fun testOccurrence(
    slotId: Long = 1L,
    position: Int = 0,
    date: LocalDate = TEST_DAY,
    time: LocalTime = LocalTime.of(8, 0),
    zone: ZoneId = TEST_UTC,
    planName: String = "Synthetic plan",
    routeKey: String = "ORAL",
    medicationKey: String = "E2",
    doseAmount: Double = 2.0
): MedicationOccurrence {
    val local = date.atTime(time)
    return MedicationOccurrence(
        id = MedicationOccurrenceId(UUID(3L, slotId)),
        planId = UUID(0L, 1L),
        slotId = UUID(1L, slotId),
        slotPosition = position,
        presentation = MedicationPresentation(
            planName = planName,
            matchKey = MedicationMatchKey(routeKey, medicationKey, doseAmount)
        ),
        scheduledAt = local.atZone(zone).toInstant(),
        scheduledLocalDateTime = local,
        zoneId = zone
    )
}

internal fun testEvent(
    id: Long = 1L,
    occurredAt: Instant = TEST_DAY.atTime(8, 5).toInstant(ZoneOffset.UTC),
    slotId: UUID? = null,
    localDate: LocalDate? = TEST_DAY,
    zoneId: ZoneId? = TEST_UTC,
    source: MedicationIntakeSource = MedicationIntakeSource.REMINDER,
    routeKey: String = "ORAL",
    medicationKey: String = "E2",
    doseAmount: Double = 2.0
): RecordedMedicationEvent = RecordedMedicationEvent(
    eventId = UUID(7L, id),
    occurredAt = occurredAt,
    slotId = slotId,
    matchKey = MedicationMatchKey(routeKey, medicationKey, doseAmount),
    source = source,
    localDate = localDate,
    zoneId = zoneId
)

internal fun matchedEntry(
    occurrence: MedicationOccurrence = testOccurrence(),
    event: RecordedMedicationEvent = testEvent(slotId = occurrence.slotId),
    provenance: MedicationMatchProvenance = MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE,
    crossesLocalDateBoundary: Boolean = false,
    displayDate: LocalDate = occurrence.scheduledLocalDateTime.toLocalDate()
): MatchedHistoricalOccurrence = MatchedHistoricalOccurrence(
    occurrence = occurrence,
    event = event,
    matchProvenance = provenance,
    status = MedicationOccurrenceStatus.RECORDED,
    actionAvailability = MedicationActionAvailability.ALREADY_RECORDED,
    scheduleTimeContext = HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
    crossesLocalDateBoundary = crossesLocalDateBoundary,
    displayDate = displayDate,
    displayDateProvenance = HistoricalDisplayDateProvenance.INTENDED_LOCAL_DATE
)

internal fun unrecordedEntry(
    occurrence: MedicationOccurrence = testOccurrence(slotId = 2L, time = LocalTime.of(16, 0)),
    displayDate: LocalDate = occurrence.scheduledLocalDateTime.toLocalDate()
): UnrecordedHistoricalOccurrence = UnrecordedHistoricalOccurrence(
    occurrence = occurrence,
    status = MedicationOccurrenceStatus.PAST_UNRECORDED,
    actionAvailability = MedicationActionAvailability.WINDOW_EXPIRED,
    displayDate = displayDate
)

internal fun unmatchedEntry(
    event: RecordedMedicationEvent = testEvent(id = 3L, localDate = null, zoneId = null),
    source: MedicationIntakeSource = event.source,
    displayDate: LocalDate = TEST_DAY,
    provenance: HistoricalDisplayDateProvenance =
        HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE
): UnmatchedHistoricalIntake = UnmatchedHistoricalIntake(
    event = event,
    source = source,
    displayDate = displayDate,
    displayDateProvenance = provenance
)

internal fun testDay(
    date: LocalDate = TEST_DAY,
    entries: List<HistoricalEntry> = emptyList()
): HistoricalDay = HistoricalDay(date = date, entries = entries)

internal fun testRange(
    startDate: LocalDate,
    endDate: LocalDate,
    days: List<HistoricalDay>
): HistoricalRange = HistoricalRange(startDate = startDate, endDate = endDate, days = days)
