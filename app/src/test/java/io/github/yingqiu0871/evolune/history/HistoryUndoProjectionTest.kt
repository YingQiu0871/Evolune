package io.github.yingqiu0871.evolune.history

import io.github.yingqiu0871.evolune.application.DoseEventEditSessionFactory
import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.WearAppStoredUndo
import io.github.yingqiu0871.evolune.application.WearAppUndoHandler
import io.github.yingqiu0871.evolune.application.WearAppUndoOperationJournal
import io.github.yingqiu0871.evolune.application.WearAppUndoOperationStatus
import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.experience.FutureOccurrenceContext
import io.github.yingqiu0871.evolune.experience.HistoricalDay
import io.github.yingqiu0871.evolune.experience.HistoricalEntry
import io.github.yingqiu0871.evolune.experience.HistoricalProjectionBuilder
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommandType
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultType
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * A-04 A6: **undo → historical projection**.
 *
 * The acceptance item requires that after an undo the row exists in no historical projection, for
 * **both** authoritative mutation paths, and that long-term history keeps expressing only
 * `Recorded` / `No recorded intake`. These tests therefore drive the real production mutation
 * paths — `HRTViewModel.deleteEvent` (Phone delete) and `WearAppUndoHandler.handle`
 * (Wear latest-delete) — and then re-read History through the real `HistoryReadService`.
 */
class HistoryUndoProjectionTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val day: LocalDate = LocalDate.of(2025, 1, 5)
    private val plan = syntheticPlan(slots = listOf(LocalTime.of(23, 0)))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val producer = WearAppProducerIdentity(UUID(0L, 777L), 3L)

    // ---------- Case A: matched intake ----------

    @Test
    fun `phone delete turns a matched intake back into a no-recorded-intake entry`() {
        val event = reminderEvent(confirmedAt = day.atTime(23, 5).atZone(paris).toInstant())
        val events = FakeDoseEventRepository(listOf(event))
        val now = day.plusDays(1).atTime(12, 0).atZone(paris).toInstant()

        val before = readHistory(events, now).days.single().entries.single()
        assertTrue(before is MatchedHistoricalOccurrence)
        assertEquals(event.id, (before as MatchedHistoricalOccurrence).event.eventId)

        phoneDelete(events, event.id)
        assertEquals(1, events.deleteCalls)

        val after = readHistory(events, now)
        val entry = after.days.single().entries.single()
        assertTrue("the occurrence must survive as 'no recorded intake'", entry is UnrecordedHistoricalOccurrence)
        assertEquals(0, after.recordedCount)
        assertEquals(1, after.unrecordedCount)
        assertEquals("no ghost or tombstone entry may remain", 0, after.unmatchedActualCount)
        assertEquals(day, entry.displayDate)
    }

    // ---------- Case B: unmatched actual intake ----------

    @Test
    fun `phone delete removes an unmatched actual intake without inventing an occurrence`() {
        val orphan = manualOrphanEvent(occurredAt = day.atTime(9, 45).atZone(paris).toInstant())
        val events = FakeDoseEventRepository(listOf(orphan))
        val now = day.plusDays(1).atTime(12, 0).atZone(paris).toInstant()

        val before = readHistory(events, now)
        assertTrue(before.days.single().entries.any { it is UnmatchedHistoricalIntake })
        val occurrenceIdsBefore = occurrenceIds(before)

        phoneDelete(events, orphan.id)

        val after = readHistory(events, now)
        assertTrue(
            "the unmatched intake must disappear completely",
            after.days.single().entries.none { it is UnmatchedHistoricalIntake }
        )
        assertEquals(0, after.unmatchedActualCount)
        assertEquals(
            "no occurrence may be invented by the undo",
            occurrenceIdsBefore,
            occurrenceIds(after)
        )
        assertNull(events.events[orphan.id])
    }

    // ---------- Case C: future scheduled occurrence + early intake ----------

    @Test
    fun `undoing an early intake makes the future occurrence vanish from History instead of becoming unrecorded`() {
        val occurrence = occurrenceOn(day, paris, plan).single()
        val earlyIntake = reminderEvent(
            confirmedAt = day.atTime(11, 0).atZone(paris).toInstant(),
            slotId = occurrence.slotId
        )
        val events = FakeDoseEventRepository(listOf(earlyIntake))
        // Now is *before* the scheduled time: the occurrence has not arrived yet.
        val now = day.atTime(12, 0).atZone(paris).toInstant()

        val before = readHistory(events, now)
        assertTrue(before.days.single().entries.single() is MatchedHistoricalOccurrence)

        phoneDelete(events, earlyIntake.id)

        val after = readHistory(events, now)
        assertTrue("History must contain nothing for an occurrence that has not arrived", after.days.isEmpty())

        // Domain-level cross-check: the occurrence is upcoming context, never unrecorded.
        val projection = HistoricalProjectionBuilder.derive(
            occurrences = listOf(occurrence),
            events = emptyList(),
            now = now,
            displayZone = paris
        )
        assertTrue(projection.entries.isEmpty())
        assertEquals(1, projection.futureOccurrences.size)
        assertTrue(projection.futureOccurrences.single() is FutureOccurrenceContext)
    }

    // ---------- Case D: delayed / cross-date intake, zoned and on a DST day ----------

    @Test
    fun `wear latest-delete removes a cross-date intake and keeps the display-date attribution`() {
        // Production Reminder/Wear shape: planned day persisted, actual instant on the next day.
        val occurrence = occurrenceOn(day, paris, plan).single()
        val event = wearEvent(
            slotId = occurrence.slotId,
            localDate = day,
            occurredAt = day.plusDays(1).atTime(0, 30).atZone(paris).toInstant()
        )
        val events = FakeDoseEventRepository(listOf(event))
        val now = day.plusDays(1).atTime(12, 0).atZone(paris).toInstant()

        val matched = readHistory(events, now).days.single().entries.single()
        assertTrue(matched is MatchedHistoricalOccurrence)
        assertEquals(day, matched.displayDate)
        assertEquals(
            day.plusDays(1),
            HistoryFormatting.localDate(
                (matched as MatchedHistoricalOccurrence).event.occurredAt,
                requireNotNull(matched.event.zoneId)
            )
        )

        val result = wearUndo(events, event)
        assertEquals(WearAppUndoResultType.UNDONE, result.resultType)
        assertEquals(1, events.latestDoseDeleteCalls)
        assertEquals(0, events.conditionalDeleteCalls)
        assertNull(events.events[event.id])

        val after = readHistory(events, now)
        val entry = after.days.single().entries.single()
        assertTrue(entry is UnrecordedHistoricalOccurrence)
        assertEquals("the intended display date must not drift", day, entry.displayDate)
        assertEquals(0, after.recordedCount)
        assertEquals(0, after.unmatchedActualCount)
    }

    @Test
    fun `undo across a DST day keeps attribution correct and leaves no ghost`() {
        // Europe/Paris 2025-03-30 is the DST gap day (02:00 -> 03:00 local).
        val dstDay = LocalDate.of(2025, 3, 30)
        val occurrence = occurrenceOn(dstDay, paris, plan).single()
        val event = reminderEvent(
            confirmedAt = dstDay.plusDays(1).atTime(0, 30).atZone(paris).toInstant(),
            slotId = occurrence.slotId,
            localDate = dstDay
        )
        val events = FakeDoseEventRepository(listOf(event))
        val now = dstDay.plusDays(1).atTime(12, 0).atZone(paris).toInstant()

        val before = readHistory(events, now, dstDay)
        assertTrue(before.days.single().entries.single() is MatchedHistoricalOccurrence)

        phoneDelete(events, event.id)

        val after = readHistory(events, now, dstDay)
        val entry = after.days.single().entries.single()
        assertTrue(entry is UnrecordedHistoricalOccurrence)
        assertEquals(dstDay, entry.displayDate)
        assertEquals(1, after.unrecordedCount)
        assertEquals(0, after.unmatchedActualCount)
    }

    // ---------- production mutation paths ----------

    private fun phoneDelete(events: FakeDoseEventRepository, id: UUID) {
        val viewModel = HRTViewModel(
            repository = events,
            medicationPlanRepository = FakeMedicationPlanRepository(listOf(plan)),
            sessionFactory = DoseEventEditSessionFactory(
                idSupplier = { UUID(0L, 900L) },
                clock = Clock.fixed(day.atTime(12, 0).atZone(paris).toInstant(), paris),
                zoneIdSupplier = { paris }
            ),
            clock = Clock.fixed(day.atTime(12, 0).atZone(paris).toInstant(), paris),
            operationScope = scope
        )
        viewModel.deleteEvent(id)
    }

    private fun wearUndo(
        events: FakeDoseEventRepository,
        event: DoseEvent
    ): WearAppUndoResult = runBlocking {
        val handler = WearAppUndoHandler(
            context = null,
            doseEvents = events,
            clock = Clock.fixed(day.plusDays(2).atTime(12, 0).atZone(paris).toInstant(), paris),
            producerIdentity = { producer },
            latestSnapshotRevision = { SNAPSHOT_REVISION },
            operationJournal = InMemoryUndoJournal()
        )
        handler.handle(
            WearAppUndoCommand(
                protocolVersion = 1,
                commandType = WearAppUndoCommandType.UNDO_RECENT_DOSE,
                operationId = UUID(0L, 703L),
                createdAt = day.plusDays(2).atTime(12, 0).atZone(paris).toInstant().minusSeconds(60L),
                sourceSnapshot = WearAppSnapshotIdentity(
                    producer.producerInstanceId,
                    producer.producerGeneration,
                    SNAPSHOT_REVISION
                ),
                eventId = event.id,
                expectedEventRevision = event.revision,
                expectedOccurredAt = event.occurredAt,
                expectedSource = event.source.name
            )
        )
    }

    // ---------- read path / fixtures ----------

    private fun readHistory(
        events: FakeDoseEventRepository,
        now: Instant,
        date: LocalDate = day
    ): HistoricalRange = runBlocking {
        HistoryReadService(
            FakeMedicationPlanRepository(listOf(plan)),
            events
        ).readRange(date, date, paris, now)
    }

    private fun occurrenceIds(range: HistoricalRange): Set<UUID> =
        range.days.flatMap { it.entries }.mapNotNull { entry ->
            when (entry) {
                is MatchedHistoricalOccurrence -> entry.occurrence.id.value
                is UnrecordedHistoricalOccurrence -> entry.occurrence.id.value
                is UnmatchedHistoricalIntake -> null
            }
        }.toSet()

    private fun occurrenceOn(
        date: LocalDate,
        zone: ZoneId,
        plan: MedicationPlan
    ): List<MedicationOccurrence> = MedicationOccurrenceGenerator.generate(
        schedules = listOf(plan.toMedicationSchedule()),
        window = OccurrenceGenerationWindow(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant()
        ),
        zoneId = zone
    )

    /** Reminder-shaped authoritative event: planned day persisted, slot identity present. */
    private fun reminderEvent(
        confirmedAt: Instant,
        slotId: UUID = occurrenceOn(day, paris, plan).single().slotId,
        localDate: LocalDate = day
    ): DoseEvent = DoseEvent(
        id = UUID(7L, 801L),
        route = Route.ORAL,
        occurredAt = confirmedAt,
        zoneId = paris,
        localDate = localDate,
        doseMG = 2.0,
        ester = Ester.E2,
        extras = emptyMap(),
        slotId = slotId,
        source = DoseEventSource.REMINDER,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )

    /** Wear-shaped authoritative event (the shape `WearAppUndoHandler` validates). */
    private fun wearEvent(
        slotId: UUID,
        localDate: LocalDate,
        occurredAt: Instant
    ): DoseEvent = DoseEvent(
        id = UUID(7L, 802L),
        route = Route.ORAL,
        occurredAt = occurredAt,
        zoneId = paris,
        localDate = localDate,
        doseMG = 2.0,
        ester = Ester.E2,
        extras = emptyMap(),
        slotId = slotId,
        source = DoseEventSource.WEAR,
        status = DoseEventStatus.RECORDED,
        revision = 4L
    )

    /** Orphan intake: different medication identity, so no occurrence can claim it. */
    private fun manualOrphanEvent(occurredAt: Instant): DoseEvent = DoseEvent(
        id = UUID(7L, 803L),
        route = Route.INJECTION,
        occurredAt = occurredAt,
        zoneId = paris,
        localDate = day,
        doseMG = 5.0,
        ester = Ester.EV,
        extras = emptyMap(),
        slotId = null,
        source = DoseEventSource.MANUAL,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )

    private class InMemoryUndoJournal : WearAppUndoOperationJournal {
        private val records = linkedMapOf<UUID, WearAppStoredUndo>()

        override fun read(operationId: UUID): WearAppStoredUndo? = records[operationId]

        override fun begin(operationId: UUID, fingerprint: String): Boolean {
            val existing = records[operationId]
            if (existing != null) return existing.fingerprint == fingerprint
            records[operationId] = WearAppStoredUndo(
                fingerprint = fingerprint,
                status = WearAppUndoOperationStatus.PREPARED,
                result = null
            )
            return true
        }

        override fun markDeleteInProgress(operationId: UUID, fingerprint: String): Boolean {
            val existing = records[operationId] ?: return false
            if (existing.fingerprint != fingerprint) return false
            records[operationId] = existing.copy(status = WearAppUndoOperationStatus.DELETE_IN_PROGRESS)
            return true
        }

        override fun saveResult(
            operationId: UUID,
            fingerprint: String,
            result: WearAppUndoResult
        ): Boolean {
            val existing = records[operationId]
            if (existing != null && existing.fingerprint != fingerprint) return false
            records[operationId] = WearAppStoredUndo(
                fingerprint = fingerprint,
                status = WearAppUndoOperationStatus.DELETE_IN_PROGRESS,
                result = result
            )
            return true
        }
    }

    private companion object {
        const val SNAPSHOT_REVISION = 11L
    }
}
