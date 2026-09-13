package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.reminder.createReminderDoseEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A-03-UI-R1 P1 regression: the *actual intake* timestamp must show a full date whenever the
 * intake really happened on a different local date than the history entry — decided from the
 * authoritative event alone, never from `crossesLocalDateBoundary`.
 *
 * The critical case is the real Reminder writer ([createReminderDoseEvent]): it persists the
 * **planned** day (`localDate = occurrence.scheduledLocalDateTime.toLocalDate()`) together with
 * the **actual confirmation** instant (`occurredAt = recordedAtMillis`). A dose planned for
 * 23:00 and confirmed at 00:30 the next day therefore has `localDate = D`,
 * `provenance = EXACT_SLOT_AND_LOCAL_DATE` and `crossesLocalDateBoundary = false`, while the
 * rendered actual timestamp belongs to `D + 1`.
 */
class HistoryCrossDatePresentationTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    /** Planned day D: the occurrence the reminder belongs to. */
    private val day: LocalDate = LocalDate.of(2025, 1, 5)
    private val plan = syntheticPlan(slots = listOf(LocalTime.of(23, 0)))
    private val occurrence: MedicationOccurrence = occurrenceOn(day, paris, plan)

    private val now: Instant = day.plusDays(2).atTime(12, 0).atZone(paris).toInstant()

    // ---------- production-shaped delayed reminder ----------

    @Test
    fun `delayed reminder confirmation on the next local day shows the full date`() {
        val event = reminderEvent(confirmedAt = day.plusDays(1).atTime(0, 30).atZone(paris).toInstant())

        // Production shape: planned day persisted, actual instant on the next local day.
        assertEquals(day, event.localDate)
        assertEquals(paris, event.zoneId)
        assertEquals(DoseEventSource.REMINDER, event.source)
        assertNotNull(event.slotId)

        val entry = matchedEntryFor(listOf(event))
        assertEquals(day, entry.displayDate)
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertFalse(
            "production shape: the domain boundary flag is false even though the intake is on the next day",
            entry.crossesLocalDateBoundary
        )

        val model = HistoryPresentation.entry(entry, paris)
        val actual = requireNotNull(model.actualTime)
        assertEquals(paris, actual.zone)
        assertEquals(day.plusDays(1), HistoryFormatting.localDate(actual.instant, actual.zone))
        assertTrue("a cross-date actual intake must show its full date", actual.needsFullDate)
        assertEquals("2025-01-06 00:30", HistoryFormatting.actualIntakeText(actual.instant, actual.zone, actual.needsFullDate, is24Hour = true))
        assertNull("an exact match carries no inferred note", model.noteRes)
        assertEquals(HistoryEntryKind.MATCHED, model.kind)
    }

    @Test
    fun `delayed reminder confirmation at midnight boundary keeps the event zone`() {
        val event = reminderEvent(confirmedAt = day.plusDays(1).atTime(0, 5).atZone(paris).toInstant())

        val model = HistoryPresentation.entry(matchedEntryFor(listOf(event)), paris)

        assertEquals(paris, model.actualTime!!.zone)
        assertTrue(model.actualTime!!.needsFullDate)
        assertEquals("2025-01-06 00:05", HistoryFormatting.actualIntakeText(model.actualTime!!.instant, model.actualTime!!.zone, model.actualTime!!.needsFullDate, is24Hour = true))
    }

    // ---------- unmatched cross-date intake ----------

    @Test
    fun `unmatched cross-date intake also shows the full date`() {
        val event = reminderEvent(confirmedAt = day.plusDays(1).atTime(0, 30).atZone(paris).toInstant())

        // No plan in the repository: the authoritative event cannot be claimed by any
        // occurrence, so it stays an unmatched actual intake whose display date is the
        // persisted planned day D.
        val entry = unmatchedEntryFor(listOf(event))
        assertEquals(day, entry.displayDate)
        assertEquals(
            HistoricalDisplayDateProvenance.PERSISTED_RECORDING_DATE,
            entry.displayDateProvenance
        )

        val model = HistoryPresentation.entry(entry, paris)
        val actual = requireNotNull(model.actualTime)
        assertEquals(paris, actual.zone)
        assertTrue("an unmatched cross-date intake must show its full date", actual.needsFullDate)
        assertEquals("2025-01-06 00:30", HistoryFormatting.actualIntakeText(actual.instant, actual.zone, actual.needsFullDate, is24Hour = true))
    }

    @Test
    fun `unmatched intake without a persisted zone falls back to the display zone`() {
        // Persisted planned day D, no zone: the rendered date must come from the display zone.
        val event = DoseEvent(
            id = UUID(7L, 41L),
            route = Route.ORAL,
            occurredAt = day.plusDays(1).atTime(0, 30).atZone(paris).toInstant(),
            zoneId = null,
            localDate = day,
            doseMG = 2.0,
            ester = Ester.E2,
            slotId = null,
            source = DoseEventSource.LEGACY
        )

        val entry = unmatchedEntryFor(listOf(event))

        val model = HistoryPresentation.entry(entry, paris)
        assertEquals(paris, model.actualTime!!.zone)
        assertTrue(model.actualTime!!.needsFullDate)
        assertEquals(
            "2025-01-06 00:30",
            HistoryFormatting.actualIntakeText(
                model.actualTime!!.instant,
                model.actualTime!!.zone,
                model.actualTime!!.needsFullDate,
                is24Hour = true
            )
        )
    }

    @Test
    fun `an intake whose display date is derived from its instant stays a short time`() {
        // Legacy orphan (no persisted date, no zone): the display date IS the rendered date by
        // construction, so the short time is correct and must not be expanded to a full date.
        val event = DoseEvent(
            id = UUID(7L, 42L),
            route = Route.ORAL,
            occurredAt = day.atTime(0, 30).atZone(paris).toInstant(),
            zoneId = null,
            localDate = null,
            doseMG = 2.0,
            ester = Ester.E2,
            slotId = null,
            source = DoseEventSource.LEGACY
        )

        val entry = unmatchedEntryFor(listOf(event))
        assertEquals(day, entry.displayDate)

        val model = HistoryPresentation.entry(entry, paris)
        assertFalse(model.actualTime!!.needsFullDate)
        assertEquals(
            "00:30",
            HistoryFormatting.actualIntakeText(
                model.actualTime!!.instant,
                model.actualTime!!.zone,
                model.actualTime!!.needsFullDate,
                is24Hour = true
            )
        )
    }

    // ---------- same-day regressions (must stay short) ----------

    @Test
    fun `same-day matched intake keeps the short time`() {
        val event = reminderEvent(confirmedAt = day.atTime(23, 30).atZone(paris).toInstant())

        val model = HistoryPresentation.entry(matchedEntryFor(listOf(event)), paris)

        assertFalse(model.actualTime!!.needsFullDate)
        assertEquals("23:30", HistoryFormatting.actualIntakeText(model.actualTime!!.instant, model.actualTime!!.zone, model.actualTime!!.needsFullDate, is24Hour = true))
    }

    @Test
    fun `same-day unmatched intake keeps the short time`() {
        val event = reminderEvent(confirmedAt = day.atTime(23, 30).atZone(paris).toInstant())

        val model = HistoryPresentation.entry(unmatchedEntryFor(listOf(event)), paris)

        assertFalse(model.actualTime!!.needsFullDate)
        assertEquals("23:30", HistoryFormatting.actualIntakeText(model.actualTime!!.instant, model.actualTime!!.zone, model.actualTime!!.needsFullDate, is24Hour = true))
    }

    // ---------- rendered zone precedence ----------

    @Test
    fun `the persisted event zone decides the rendered local date`() {
        // Plan and confirmation live in Tokyo while History is displayed in Paris: the
        // persisted zone must win, and it changes the full-date decision.
        val tokyoPlan = syntheticPlan(id = UUID(0L, 900L), slots = listOf(LocalTime.of(23, 0)))
        val tokyoOccurrence = occurrenceOn(day, tokyo, tokyoPlan)
        val event = createReminderDoseEvent(
            plan = tokyoPlan,
            targetOccurrence = tokyoOccurrence,
            recordedAtMillis = day.plusDays(1).atTime(0, 30).atZone(tokyo).toInstant().toEpochMilli(),
            zoneId = tokyo
        )

        val entry = matchedEntryFor(listOf(event), plan = tokyoPlan, displayZone = paris)

        val model = HistoryPresentation.entry(entry, paris)
        val actual = requireNotNull(model.actualTime)
        assertEquals(tokyo, actual.zone)
        assertEquals(day.plusDays(1), HistoryFormatting.localDate(actual.instant, actual.zone))
        assertTrue("the Tokyo-local date differs from the Paris display date", actual.needsFullDate)
        assertEquals("2025-01-06 00:30", HistoryFormatting.actualIntakeText(actual.instant, actual.zone, actual.needsFullDate, is24Hour = true))
        // The same instant rendered in the display zone would have looked same-day:
        assertEquals(day, HistoryFormatting.localDate(actual.instant, paris))
    }

    // ---------- fixtures / read path ----------

    private fun reminderEvent(confirmedAt: Instant): DoseEvent = createReminderDoseEvent(
        plan = plan,
        targetOccurrence = occurrence,
        recordedAtMillis = confirmedAt.toEpochMilli(),
        zoneId = paris
    )

    private fun matchedEntryFor(
        events: List<DoseEvent>,
        plan: io.github.yingqiu0871.evolune.core.model.MedicationPlan = this.plan,
        displayZone: ZoneId = paris
    ): MatchedHistoricalOccurrence = runBlocking {
        val range = HistoryReadService(
            FakeMedicationPlanRepository(listOf(plan)),
            FakeDoseEventRepository(events)
        ).readRange(day, day, displayZone, now)
        range.days.single().entries.single() as MatchedHistoricalOccurrence
    }

    private fun unmatchedEntryFor(
        events: List<DoseEvent>,
        displayZone: ZoneId = paris
    ): UnmatchedHistoricalIntake = runBlocking {
        val range = HistoryReadService(
            FakeMedicationPlanRepository(),
            FakeDoseEventRepository(events)
        ).readRange(day, day, displayZone, now)
        range.days.single().entries.single() as UnmatchedHistoricalIntake
    }

    private fun occurrenceOn(
        date: LocalDate,
        zone: ZoneId,
        plan: io.github.yingqiu0871.evolune.core.model.MedicationPlan
    ): MedicationOccurrence = MedicationOccurrenceGenerator.generate(
        schedules = listOf(plan.toMedicationSchedule()),
        window = OccurrenceGenerationWindow(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant()
        ),
        zoneId = zone
    ).single()
}
