package io.github.yingqiu0871.evolune.phasef

import io.github.yingqiu0871.evolune.application.DoseEventEditSessionFactory
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.WearAppConfirmationHandler
import io.github.yingqiu0871.evolune.application.WearAppUndoHandler
import io.github.yingqiu0871.evolune.application.wearAppConfirmationEventId
import io.github.yingqiu0871.evolune.application.widgetOccurrenceActionEventId
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.SlotIdResult
import io.github.yingqiu0871.evolune.experience.HistoricalDisplayDateProvenance
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.HistoricalScheduleTimeContext
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchProvenance
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceIdentity
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.insights.MedicationInsightsSummary
import io.github.yingqiu0871.evolune.experience.insights.ReadOnlyMedicationInsightsAggregator
import io.github.yingqiu0871.evolune.experience.wear.WearAppCommandType
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoCommandType
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoResultType
import io.github.yingqiu0871.evolune.export.PortableExportFormat
import io.github.yingqiu0871.evolune.export.PortableExportRange
import io.github.yingqiu0871.evolune.export.PortableExportResult
import io.github.yingqiu0871.evolune.export.PortableExportService
import io.github.yingqiu0871.evolune.history.HistoryReadService
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkRequest
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkResult
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkService
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkWindow
import io.github.yingqiu0871.evolune.history.timeline.TimelineProjectionBuilder
import io.github.yingqiu0871.evolune.history.timeline.TimelineReadModel
import io.github.yingqiu0871.evolune.history.timeline.TimelineRowKind
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.widget.ContractWidgetQuickActionWork
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionCommand
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionOutcome
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionSideEffects
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * V17 Phase F — final consistency gate: cross-surface convergence suite (V17_ACCEPTANCE §7.1).
 *
 * F1 Phone / F2 Widget / F3 Wear / F4 Undo / F5 Legacy rows drive the REAL production writers
 * and handlers, then read the SAME shared repository state through the REAL downstream
 * production readers/builders: HistoryReadService, TimelineProjectionBuilder,
 * ReadOnlyMedicationInsightsAggregator, RetrospectivePkService and the Phase-E export service.
 *
 * The matrix asserts one authoritative medication fact -> truthful convergence on every
 * applicable derived surface (no re-implemented matcher/generator/projection/codec logic).
 */
class PhaseFConsistencyTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val day: LocalDate = LocalDate.of(2026, 8, 30)
    private val slotTime: LocalTime = LocalTime.of(8, 30)
    private val slotInstant: Instant = day.atTime(slotTime).atZone(zone).toInstant()
    private val afterSlot: Instant = slotInstant.plusSeconds(600)
    private val planId: UUID = UUID(0L, 900L)
    private val phoneEventId: UUID = UUID(0L, 9001L)
    private val wearOperationId: UUID = UUID(0L, 9002L)
    private val producer: WearAppProducerIdentity = WearAppProducerIdentity(UUID(0L, 777L), 3L)
    private val plan: MedicationPlan = planWithSlots()

    // ------------------------------------------------------------------ F1 Phone

    @Test
    fun `F1 phone record converges across History Timeline Insights PK and export`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val events = PhaseFRepository()
        val quick = DoseEventEditSessionFactory(
            idSupplier = { phoneEventId },
            clock = Clock.fixed(slotInstant, zone),
            zoneIdSupplier = { zone }
        ).createQuickEvent(plan)
        assertEquals(DoseEventSource.MANUAL, quick.source)
        assertNull(quick.slotId)
        assertEquals(InsertResult.Inserted, events.insert(quick))
        val writesBefore = events.writeCalls

        val surfaces = readSurfaces(plans, events)

        val entry = surfaces.range.days.single().entries
            .filterIsInstance<MatchedHistoricalOccurrence>()
            .single()
        assertEquals(phoneEventId, entry.event.eventId)
        assertEquals(slotInstant, entry.event.occurredAt)
        assertEquals(planId, entry.occurrence.planId)
        assertEquals(2.0, entry.event.matchKey.doseAmount, 0.0)
        assertEquals("ORAL", entry.event.matchKey.routeKey)
        assertEquals("E2", entry.event.matchKey.medicationKey)
        assertEquals(MedicationIntakeSource.MANUAL, entry.event.source)
        assertNull(entry.event.slotId)
        assertEquals(day, entry.event.localDate)
        assertEquals(day, entry.displayDate)
        assertEquals(MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, entry.matchProvenance)

        val row = surfaces.timeline.days.single().rows.single()
        assertEquals(TimelineRowKind.MATCHED, row.rowKind)
        assertEquals(phoneEventId, row.recordedIntake?.eventId)
        assertEquals(slotInstant, row.recordedIntake?.occurredAt)

        assertEquals(1, surfaces.insights.recordedIntakeCount)
        assertEquals(1, surfaces.insights.matchedOccurrenceCount)
        assertEquals(1, surfaces.insights.sourceCounts[MedicationIntakeSource.MANUAL])

        val pk = surfaces.pk as RetrospectivePkResult.Available
        assertEquals(listOf(phoneEventId), pk.summary.engineInputEventIds)
        assertEquals(listOf(phoneEventId), pk.summary.concentrationProducingEventIds)

        val json = exportJson(events)
        assertTrue(json.contains(phoneEventId.toString()))
        assertTrue(json.contains("\"source\": \"MANUAL\""))
        assertTrue(json.contains("\"slot_id\": null"))

        assertEquals(writesBefore, events.writeCalls)
    }

    // ------------------------------------------------------------------ F2 Widget

    @Test
    fun `F2 widget record converges through its deterministic identity`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val events = PhaseFRepository()
        val occurrenceId = MedicationOccurrenceIdentity.derive(planId, slotId(), day).value
        val effects = WidgetEffectsRecorder()
        val work = ContractWidgetQuickActionWork(
            medicationPlans = plans,
            doseEvents = events,
            sideEffects = effects,
            clock = Clock.fixed(slotInstant.plusSeconds(30), zone),
            zoneId = { zone }
        )

        val outcome = work.handle(
            WidgetQuickActionCommand(
                planId.toString(),
                slotId().toString(),
                day.toString(),
                occurrenceId.toString()
            )
        )

        assertEquals(WidgetQuickActionOutcome.Accepted(false), outcome)
        assertEquals(listOf("refresh", "recorded:Phase-F plan"), effects.order)
        val eventId = widgetOccurrenceActionEventId(occurrenceId)
        assertEquals(1, events.rows.size)
        assertEquals(1, events.insertCalls)
        val stored = events.rows.getValue(eventId)
        assertEquals(DoseEventSource.WIDGET, stored.source)
        assertEquals(slotId(), stored.slotId)
        assertEquals(day, stored.localDate)
        val writesBefore = events.writeCalls

        val surfaces = readSurfaces(plans, events)

        val entry = surfaces.range.days.single().entries
            .filterIsInstance<MatchedHistoricalOccurrence>()
            .single()
        assertEquals(eventId, entry.event.eventId)
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertEquals(MedicationIntakeSource.WIDGET, entry.event.source)
        assertEquals(slotId(), entry.event.slotId)
        assertEquals(slotInstant.plusSeconds(30), entry.event.occurredAt)

        val row = surfaces.timeline.days.single().rows.single()
        assertEquals(TimelineRowKind.MATCHED, row.rowKind)
        assertEquals(eventId, row.recordedIntake?.eventId)
        assertEquals(1, surfaces.insights.sourceCounts[MedicationIntakeSource.WIDGET])

        val pk = surfaces.pk as RetrospectivePkResult.Available
        assertEquals(listOf(eventId), pk.summary.concentrationProducingEventIds)

        val json = exportJson(events)
        assertTrue(json.contains(eventId.toString()))
        assertTrue(json.contains("\"source\": \"WIDGET\""))
        assertTrue(json.contains(slotId().toString()))

        assertEquals(writesBefore, events.writeCalls)
    }

    // ------------------------------------------------------------------ F3 Wear

    @Test
    fun `F3 wear app confirmation converges through the real handler`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val events = PhaseFRepository()

        val result = confirmWearEvent(plans, events)

        assertEquals(WearAppConfirmResultType.CONFIRMED, result.resultType)
        assertEquals(wearEventId(), result.eventId)
        assertEquals(1, events.rows.size)
        assertEquals(1, events.insertCalls)
        val stored = events.rows.getValue(wearEventId())
        assertEquals(DoseEventSource.WEAR, stored.source)
        assertEquals(slotId(), stored.slotId)
        assertEquals(day, stored.localDate)
        assertEquals(wearOccurredAt(), stored.occurredAt)
        val writesBefore = events.writeCalls

        val surfaces = readSurfaces(plans, events)

        val entry = surfaces.range.days.single().entries
            .filterIsInstance<MatchedHistoricalOccurrence>()
            .single()
        assertEquals(wearEventId(), entry.event.eventId)
        assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
        assertEquals(MedicationIntakeSource.WEAR, entry.event.source)
        assertEquals(wearOccurredAt(), entry.event.occurredAt)
        assertEquals(slotId(), entry.event.slotId)

        val row = surfaces.timeline.days.single().rows.single()
        assertEquals(wearEventId(), row.recordedIntake?.eventId)
        assertEquals(1, surfaces.insights.sourceCounts[MedicationIntakeSource.WEAR])

        val pk = surfaces.pk as RetrospectivePkResult.Available
        assertEquals(listOf(wearEventId()), pk.summary.concentrationProducingEventIds)

        val json = exportJson(events)
        assertTrue(json.contains(wearEventId().toString()))
        assertTrue(json.contains("\"source\": \"WEAR\""))

        assertEquals(writesBefore, events.writeCalls)
    }

    // ------------------------------------------------------------------ F4 Undo

    @Test
    fun `F4 phone delete removes the fact from every applicable surface`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val events = PhaseFRepository()
        val otherId = UUID(0L, 9003L)
        assertEquals(InsertResult.Inserted, events.insert(quickEvent(phoneEventId, slotInstant)))
        assertEquals(
            InsertResult.Inserted,
            events.insert(quickEvent(otherId, slotInstant.plusSeconds(7_200L)))
        )

        val before = readSurfaces(plans, events)
        assertEquals(
            setOf(phoneEventId, otherId),
            before.range.days.flatMap { it.entries }.mapNotNull { entry ->
                when (entry) {
                    is MatchedHistoricalOccurrence -> entry.event.eventId
                    is UnmatchedHistoricalIntake -> entry.event.eventId
                    else -> null
                }
            }.toSet()
        )

        // Production Phone delete mutation (the same call HRTViewModel.deleteEvent performs).
        assertEquals(
            io.github.yingqiu0871.evolune.core.dataapi.DeleteResult.Deleted,
            events.delete(phoneEventId)
        )

        val after = readSurfaces(plans, events)
        val remainingIds = after.range.days.flatMap { it.entries }.mapNotNull { entry ->
            when (entry) {
                is MatchedHistoricalOccurrence -> entry.event.eventId
                is UnmatchedHistoricalIntake -> entry.event.eventId
                else -> null
            }
        }
        assertEquals(listOf(otherId), remainingIds)
        assertTrue(
            after.range.days.flatMap { it.entries }.none { entry ->
                when (entry) {
                    is MatchedHistoricalOccurrence -> entry.event.eventId == phoneEventId
                    is UnmatchedHistoricalIntake -> entry.event.eventId == phoneEventId
                    else -> false
                }
            }
        )
        assertTrue(
            after.timeline.days.flatMap { it.rows }.none { it.recordedIntake?.eventId == phoneEventId }
        )
        assertEquals(1, after.insights.recordedIntakeCount)
        val pk = after.pk as RetrospectivePkResult.Available
        assertEquals(listOf(otherId), pk.summary.concentrationProducingEventIds)
        assertNull(events.rows[phoneEventId])
    }

    @Test
    fun `F4 wear latest-dose undo removes the fact from every applicable surface`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val events = PhaseFRepository()
        val earlierId = UUID(0L, 9004L)
        val earlierDay = day.minusDays(2)
        assertEquals(
            InsertResult.Inserted,
            events.insert(quickEvent(earlierId, earlierDay.atTime(slotTime).atZone(zone).toInstant()))
        )
        val confirmed = confirmWearEvent(plans, events)
        assertEquals(WearAppConfirmResultType.CONFIRMED, confirmed.resultType)

        val beforeDay = readSurfaces(plans, events)
        val beforePk = beforeDay.pk as RetrospectivePkResult.Available
        assertEquals(
            listOf(earlierId, wearEventId()),
            beforePk.summary.concentrationProducingEventIds
        )

        val undoResult = WearAppUndoHandler(
            context = null,
            doseEvents = events,
            clock = Clock.fixed(afterSlot, zone),
            producerIdentity = { producer },
            latestSnapshotRevision = { SNAPSHOT_REVISION },
            operationJournal = PhaseFUndoJournal()
        ).handle(
            WearAppUndoCommand(
                protocolVersion = 1,
                commandType = WearAppUndoCommandType.UNDO_RECENT_DOSE,
                operationId = UUID(0L, 9005L),
                createdAt = afterSlot.minusSeconds(60L),
                sourceSnapshot = WearAppSnapshotIdentity(
                    producer.producerInstanceId,
                    producer.producerGeneration,
                    SNAPSHOT_REVISION
                ),
                eventId = wearEventId(),
                expectedEventRevision = 1L,
                expectedOccurredAt = wearOccurredAt(),
                expectedSource = DoseEventSource.WEAR.name
            )
        )

        assertEquals(WearAppUndoResultType.UNDONE, undoResult.resultType)
        assertNull(events.rows[wearEventId()])

        val afterDay = readSurfaces(plans, events)
        assertTrue(
            afterDay.range.days.flatMap { it.entries }.none { it is MatchedHistoricalOccurrence }
        )
        assertTrue(
            afterDay.timeline.days.flatMap { it.rows }
                .none { it.recordedIntake?.eventId == wearEventId() }
        )
        assertEquals(0, afterDay.insights.recordedIntakeCount)
        val afterPk = afterDay.pk as RetrospectivePkResult.Available
        assertEquals(listOf(earlierId), afterPk.summary.concentrationProducingEventIds)

        // No unrelated event was removed: the earlier day's surface still converges.
        val earlierSurfaces = readSurfaces(plans, events, readDay = earlierDay)
        val earlierEntry = earlierSurfaces.range.days.single().entries
            .filterIsInstance<MatchedHistoricalOccurrence>()
            .single()
        assertEquals(earlierId, earlierEntry.event.eventId)
        val earlierPk = earlierSurfaces.pk as RetrospectivePkResult.Available
        assertEquals(listOf(earlierId), earlierPk.summary.concentrationProducingEventIds)
    }

    // ------------------------------------------------------------------ F5 Legacy

    @Test
    fun `F5 legacy orphan keeps its null provenance across surfaces and export`() = runBlocking {
        val plans = FakeMedicationPlanRepository(emptyList())
        val events = PhaseFRepository()
        val legacyId = UUID(0L, 9006L)
        assertEquals(
            InsertResult.Inserted,
            events.insert(
                legacyEvent(legacyId, day.atTime(8, 0).atZone(zone).toInstant())
            )
        )
        val writesBefore = events.writeCalls

        val surfaces = readSurfaces(plans, events)

        val entry = surfaces.range.days.single().entries
            .filterIsInstance<UnmatchedHistoricalIntake>()
            .single()
        assertEquals(legacyId, entry.event.eventId)
        assertFalse(entry.isManualIntake)
        assertEquals(MedicationIntakeSource.LEGACY, entry.source)
        assertNull(entry.event.localDate)
        assertNull(entry.event.zoneId)
        assertNull(entry.event.slotId)
        assertEquals(day, entry.displayDate)
        assertEquals(
            HistoricalDisplayDateProvenance.CURRENT_DISPLAY_TIMEZONE_DERIVED,
            entry.displayDateProvenance
        )
        assertFalse(entry.isOriginalLocalDate)

        val row = surfaces.timeline.days.single().rows.single()
        assertEquals(TimelineRowKind.UNMATCHED_INTAKE, row.rowKind)
        assertEquals(legacyId, row.recordedIntake?.eventId)
        assertNull(row.scheduleContext)

        assertEquals(1, surfaces.insights.unmatchedActualIntakeCount)
        assertEquals(1, surfaces.insights.sourceCounts[MedicationIntakeSource.LEGACY])
        assertEquals(0, surfaces.insights.sourceCounts[MedicationIntakeSource.MANUAL] ?: 0)
        assertTrue(surfaces.insights.containsCurrentTimezoneDerivedDates)

        val pk = surfaces.pk as RetrospectivePkResult.Available
        assertEquals(listOf(legacyId), pk.summary.concentrationProducingEventIds)

        val json = exportJson(events)
        assertTrue(json.contains(legacyId.toString()))
        assertTrue(json.contains("\"source\": \"LEGACY\""))
        assertTrue(json.contains("\"zone_id\": null"))
        assertTrue(json.contains("\"local_date\": null"))
        assertTrue(json.contains("\"slot_id\": null"))

        assertEquals(writesBefore, events.writeCalls)
    }

    // ------------------------------------------------------------------ W-DH-2 gate regression

    @Test
    fun `W-DH-2 gate regression rejected widget action stays zero-write with refresh and feedback`() =
        runBlocking {
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val events = PhaseFRepository().apply {
                writeFailure = IllegalStateException("Decision-H rejection must never write")
            }
            val effects = WidgetEffectsRecorder()
            val work = ContractWidgetQuickActionWork(
                medicationPlans = plans,
                doseEvents = events,
                sideEffects = effects,
                clock = Clock.fixed(slotInstant.plusSeconds(30), zone),
                zoneId = { zone }
            )

            val stale = WidgetQuickActionCommand(
                planId.toString(),
                slotId().toString(),
                day.minusDays(1).toString(),
                UUID(9L, 1L).toString()
            )

            assertEquals(WidgetQuickActionOutcome.Invalid, work.handle(stale))
            assertEquals(listOf("refresh", "reject"), effects.order)
            assertEquals(0, events.writeCalls)
            assertTrue(events.rows.isEmpty())
        }

    // ------------------------------------------------------------------ Scenario strengthening

    @Test
    fun `cross-midnight legacy inferred match stays convergent and provenance-bearing`() =
        runBlocking {
            val midnightPlan = planWithSlots(times = listOf(LocalTime.MIDNIGHT))
            val plans = FakeMedicationPlanRepository(listOf(midnightPlan))
            val events = PhaseFRepository()
            val legacyId = UUID(0L, 9007L)
            assertEquals(
                InsertResult.Inserted,
                events.insert(
                    legacyEvent(
                        legacyId,
                        day.minusDays(1).atTime(23, 0).atZone(zone).toInstant()
                    )
                )
            )

            val surfaces = readSurfaces(plans, events)

            val entry = surfaces.range.days.single().entries
                .filterIsInstance<MatchedHistoricalOccurrence>()
                .single()
            assertEquals(legacyId, entry.event.eventId)
            assertEquals(MedicationMatchProvenance.NULL_SLOT_TIME_WINDOW, entry.matchProvenance)
            assertTrue(entry.crossesLocalDateBoundary)
            assertEquals(day, entry.displayDate)
            assertEquals(MedicationIntakeSource.LEGACY, entry.event.source)

            val row = surfaces.timeline.days.single().rows.single()
            assertEquals(legacyId, row.recordedIntake?.eventId)

            val pk = surfaces.pk as RetrospectivePkResult.Available
            assertEquals(listOf(legacyId), pk.summary.concentrationProducingEventIds)

            // The previous day must not treat the consumed event as an orphan of its own.
            val previous = HistoryReadService(plans, events).readRange(
                day.minusDays(1),
                day.minusDays(1),
                zone,
                afterSlot
            )
            assertEquals(0, previous.unmatchedActualCount)
            assertEquals(0, previous.recordedCount)
        }

    @Test
    fun `schedule dose edit keeps recorded history truth while refreshing schedule context`() =
        runBlocking {
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val events = PhaseFRepository()
            assertEquals(WearAppConfirmResultType.CONFIRMED, confirmWearEvent(plans, events).resultType)
            val storedBefore = events.rows.getValue(wearEventId())

            plans.plans[planId] = plan.copy(doseMG = 5.0)

            val surfaces = readSurfaces(plans, events)

            val entry = surfaces.range.days.single().entries
                .filterIsInstance<MatchedHistoricalOccurrence>()
                .single()
            assertEquals(wearEventId(), entry.event.eventId)
            assertEquals(MedicationMatchProvenance.EXACT_SLOT_AND_LOCAL_DATE, entry.matchProvenance)
            // Recorded truth is untouched; only the current schedule context reflects the edit.
            assertEquals(2.0, entry.event.matchKey.doseAmount, 0.0)
            assertEquals(5.0, entry.occurrence.presentation.matchKey.doseAmount, 0.0)
            assertEquals(
                HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT,
                entry.scheduleTimeContext
            )
            assertEquals(wearOccurredAt(), entry.event.occurredAt)
            assertEquals(storedBefore, events.rows.getValue(wearEventId()))
        }

    @Test
    fun `slot identity replacement leaves the recorded event truthful and unmatched`() =
        runBlocking {
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val events = PhaseFRepository()
            assertEquals(WearAppConfirmResultType.CONFIRMED, confirmWearEvent(plans, events).resultType)
            val originalSlotId = slotId()
            val storedBefore = events.rows.getValue(wearEventId())

            plans.plans[planId] = plan.copy(
                slots = listOf(plan.slots.single().copy(id = UUID(0L, 950L)))
            )

            val surfaces = readSurfaces(plans, events)

            val unmatched = surfaces.range.days.single().entries
                .filterIsInstance<UnmatchedHistoricalIntake>()
                .single()
            assertEquals(wearEventId(), unmatched.event.eventId)
            assertEquals(MedicationIntakeSource.WEAR, unmatched.source)
            assertEquals(originalSlotId, unmatched.event.slotId)
            assertEquals(day, unmatched.displayDate)
            assertTrue(
                surfaces.range.days.flatMap { it.entries }
                    .none { it is MatchedHistoricalOccurrence }
            )
            assertEquals(storedBefore, events.rows.getValue(wearEventId()))

            val row = surfaces.timeline.days.single().rows
                .single { it.rowKind == TimelineRowKind.UNMATCHED_INTAKE }
            assertEquals(wearEventId(), row.recordedIntake?.eventId)

            assertEquals(1, surfaces.insights.unmatchedActualIntakeCount)
            assertEquals(1, surfaces.insights.unrecordedOccurrenceCount)

            val pk = surfaces.pk as RetrospectivePkResult.Available
            assertEquals(listOf(wearEventId()), pk.summary.concentrationProducingEventIds)
        }

    @Test
    fun `schedule deletion keeps the recorded event and stops future occurrence generation`() =
        runBlocking {
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val events = PhaseFRepository()
            assertEquals(InsertResult.Inserted, events.insert(quickEvent(phoneEventId, slotInstant)))

            plans.plans.remove(planId)

            val today = readSurfaces(plans, events)
            val unmatched = today.range.days.single().entries
                .filterIsInstance<UnmatchedHistoricalIntake>()
                .single()
            assertEquals(phoneEventId, unmatched.event.eventId)
            assertEquals(day, unmatched.displayDate)
            val row = today.timeline.days.single().rows
                .single { it.rowKind == TimelineRowKind.UNMATCHED_INTAKE }
            assertEquals(phoneEventId, row.recordedIntake?.eventId)
            val pk = today.pk as RetrospectivePkResult.Available
            assertEquals(listOf(phoneEventId), pk.summary.concentrationProducingEventIds)

            // No future occurrence is generated for the deleted schedule.
            val futureDay = day.plusDays(7)
            val future = HistoryReadService(plans, events).readRange(
                futureDay,
                futureDay,
                zone,
                futureDay.atTime(9, 0).atZone(zone).toInstant()
            )
            assertTrue(future.days.isEmpty())
        }

    // ------------------------------------------------------------------ helpers

    private data class Surfaces(
        val range: HistoricalRange,
        val timeline: TimelineReadModel,
        val insights: MedicationInsightsSummary,
        val pk: RetrospectivePkResult
    )

    private suspend fun readSurfaces(
        plans: MedicationPlanRepository,
        events: DoseEventRepository,
        readDay: LocalDate = day,
        now: Instant = afterSlot
    ): Surfaces {
        val history = HistoryReadService(plans, events)
        val range = history.readRange(readDay, readDay, zone, now)
        return Surfaces(
            range = range,
            timeline = TimelineProjectionBuilder.build(range),
            insights = ReadOnlyMedicationInsightsAggregator.aggregate(range),
            pk = RetrospectivePkService(history).estimate(
                RetrospectivePkRequest(
                    visibleWindow = RetrospectivePkWindow.fromLocalDates(readDay, readDay, zone),
                    cursor = null,
                    displayZone = zone,
                    bodyWeightKG = BODY_WEIGHT_KG,
                    capturedAt = now
                )
            )
        )
    }

    private suspend fun confirmWearEvent(
        plans: MedicationPlanRepository,
        events: DoseEventRepository
    ): WearAppConfirmResult = WearAppConfirmationHandler(
        context = null,
        medicationPlans = plans,
        doseEvents = events,
        clock = Clock.fixed(wearOccurredAt(), zone),
        zoneId = { zone },
        producerIdentity = { producer },
        latestSnapshotRevision = { SNAPSHOT_REVISION },
        operationJournal = PhaseFConfirmationJournal()
    ).handle(wearConfirmCommand())

    private suspend fun exportJson(
        events: DoseEventRepository,
        capturedAt: Instant = afterSlot
    ): String {
        val result = PortableExportService(events, Clock.fixed(capturedAt, zone)).export(
            PortableExportRange.ALL,
            PortableExportFormat.JSON
        )
        val success = result as PortableExportResult.Success
        return String(success.bytes, Charsets.UTF_8)
    }

    private fun quickEvent(id: UUID, occurredAt: Instant): DoseEvent =
        DoseEventEditSessionFactory(
            idSupplier = { id },
            clock = Clock.fixed(occurredAt, zone),
            zoneIdSupplier = { zone }
        ).createQuickEvent(plan)

    private fun legacyEvent(id: UUID, occurredAt: Instant): DoseEvent = DoseEvent(
        id = id,
        route = Route.ORAL,
        occurredAt = occurredAt,
        zoneId = null,
        localDate = null,
        doseMG = 2.0,
        ester = Ester.E2,
        extras = emptyMap(),
        slotId = null,
        source = DoseEventSource.LEGACY,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )

    private fun wearOccurredAt(): Instant = slotInstant.plusSeconds(30)

    private fun slotId(): UUID = plan.slots.single().id

    private fun wearEventId(): UUID = wearAppConfirmationEventId(wearOperationId)

    private fun wearConfirmCommand() = WearAppConfirmCommand(
        protocolVersion = 1,
        commandType = WearAppCommandType.CONFIRM_OCCURRENCE,
        operationId = wearOperationId,
        createdAt = slotInstant.minusSeconds(60L),
        sourceSnapshot = WearAppSnapshotIdentity(
            producer.producerInstanceId,
            producer.producerGeneration,
            SNAPSHOT_REVISION
        ),
        occurrenceId = MedicationOccurrenceIdentity.derive(planId, slotId(), day).value,
        planId = planId,
        slotId = slotId(),
        localDate = day,
        scheduledAt = slotInstant
    )

    private fun planWithSlots(
        id: UUID = planId,
        doseMG: Double = 2.0,
        times: List<LocalTime> = listOf(slotTime)
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = "Phase-F plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = doseMG,
        scheduleType = ScheduleType.DAILY,
        slots = times.mapIndexed { position, time ->
            ScheduledDoseSlot(
                id = deterministicSlotId(id, position, time),
                planId = id,
                localTime = time,
                position = position
            )
        },
        daysOfWeek = emptySet(),
        intervalDays = 1,
        isEnabled = true,
        extras = emptyMap(),
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private companion object {
        const val BODY_WEIGHT_KG = 60.0
        const val SNAPSHOT_REVISION = 11L

        fun deterministicSlotId(planId: UUID, position: Int, time: LocalTime): UUID =
            (ScheduledDoseSlotId.generate(planId.toString(), position, time) as SlotIdResult.Success).id
    }
}

private class WidgetEffectsRecorder : WidgetQuickActionSideEffects {
    val order = mutableListOf<String>()

    override suspend fun refreshWidgets() {
        order += "refresh"
    }

    override suspend fun showRecorded(planName: String) {
        order += "recorded:$planName"
    }

    override suspend fun showRejected() {
        order += "reject"
    }
}
