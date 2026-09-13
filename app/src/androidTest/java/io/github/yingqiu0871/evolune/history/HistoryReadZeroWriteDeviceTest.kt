package io.github.yingqiu0871.evolune.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.LatestDoseDeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanUpdateResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.data.repository.ProductionRepositoryProvider
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * A-04 A7 on the real database: opening History must not change `dose_events` or `plans`.
 *
 * The counting decorators wrap the **production** Room repositories, so the measured read runs
 * through the real DAO layer while every mutation method is counted and the authoritative
 * `(id → revision)` / plan snapshots are compared before and after.
 */
@RunWith(AndroidJUnit4::class)
class HistoryReadZeroWriteDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone: ZoneId = ZoneOffset.UTC
    private val seededId = UUID.fromString("00000000-0000-0000-0000-0000a0400001")

    @Test
    fun readingHistoryNeverWritesToTheRealDatabase() = runBlocking {
        val provider = ProductionRepositoryProvider.get(context)
        val today = LocalDate.now(zone)
        val seeded = DoseEvent(
            id = seededId,
            route = Route.ORAL,
            occurredAt = today.atTime(9, 15).toInstant(ZoneOffset.UTC),
            zoneId = zone,
            localDate = today,
            doseMG = 2.0,
            ester = Ester.E2,
            extras = emptyMap(),
            slotId = null,
            source = DoseEventSource.MANUAL,
            status = DoseEventStatus.RECORDED,
            revision = 1L
        )
        // Seeding happens outside the measured window and is cleaned up below.
        provider.doseEvents.delete(seededId)
        provider.doseEvents.insert(seeded)

        try {
            val countingEvents = CountingDoseEvents(provider.doseEvents)
            val countingPlans = CountingPlans(provider.medicationPlans)

            val eventsBefore = eventRevisions(countingEvents)
            val plansBefore = planSnapshot(countingPlans)

            val range = HistoryReadService(countingPlans, countingEvents)
                .readRange(today, today, zone, today.atTime(23, 0).toInstant(ZoneOffset.UTC))

            val eventsAfter = eventRevisions(countingEvents)
            val plansAfter = planSnapshot(countingPlans)

            assertTrue("the read path must actually read events", countingEvents.reads > 0)
            assertTrue("the read path must actually read plans", countingPlans.reads > 0)
            assertEquals("no dose_events mutation during a History read", 0, countingEvents.writes)
            assertEquals("no plan mutation during a History read", 0, countingPlans.writes)
            assertEquals("dose_events rows/revisions changed", eventsBefore, eventsAfter)
            assertEquals("plan rows changed", plansBefore, plansAfter)
            assertTrue(
                "the seeded intake must be part of the read result",
                range.days.any { day -> day.entries.isNotEmpty() }
            )
        } finally {
            provider.doseEvents.delete(seededId)
        }
    }

    private suspend fun eventRevisions(events: DoseEventRepository): Map<UUID, Long> =
        events.findOccurredBetween(Instant.EPOCH, Instant.now().plusSeconds(86_400L))
            .associate { it.id to it.revision }

    private suspend fun planSnapshot(plans: MedicationPlanRepository): Map<UUID, String> =
        plans.observeAll().first().associate { it.id to "${it.doseMG}|${it.isEnabled}|${it.slots.size}" }

    private class CountingDoseEvents(
        private val delegate: DoseEventRepository
    ) : DoseEventRepository by delegate {

        var reads = 0
        var writes = 0

        override fun observeAll(): Flow<List<DoseEvent>> {
            reads += 1
            return delegate.observeAll()
        }

        override suspend fun getById(id: UUID): DoseEvent? {
            reads += 1
            return delegate.getById(id)
        }

        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> {
            reads += 1
            return delegate.findOccurredBetween(startInclusive, endExclusive)
        }

        override suspend fun findRecordedLocalDateBetween(
            startInclusive: LocalDate,
            endInclusive: LocalDate
        ): List<DoseEvent> {
            reads += 1
            return delegate.findRecordedLocalDateBetween(startInclusive, endInclusive)
        }

        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> {
            reads += 1
            return delegate.getEventsForPk(asOf)
        }

        override suspend fun insert(event: DoseEvent): InsertResult {
            writes += 1
            return delegate.insert(event)
        }

        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult {
            writes += 1
            return delegate.update(event, expectedRevision)
        }

        override suspend fun delete(id: UUID): DeleteResult {
            writes += 1
            return delegate.delete(id)
        }

        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult {
            writes += 1
            return delegate.deleteIfRevisionMatches(id, expectedRevision)
        }

        override suspend fun deleteLatestRecordedIfRevisionMatches(
            eventId: UUID,
            eventRevision: Long
        ): LatestDoseDeleteResult {
            writes += 1
            return delegate.deleteLatestRecordedIfRevisionMatches(eventId, eventRevision)
        }

        override suspend fun deleteAll(): DeleteResult {
            writes += 1
            return delegate.deleteAll()
        }
    }

    private class CountingPlans(
        private val delegate: MedicationPlanRepository
    ) : MedicationPlanRepository by delegate {

        var reads = 0
        var writes = 0

        override fun observeAll(): Flow<List<MedicationPlan>> {
            reads += 1
            return delegate.observeAll()
        }

        override fun observeEnabled(): Flow<List<MedicationPlan>> {
            reads += 1
            return delegate.observeEnabled()
        }

        override suspend fun getById(id: UUID): MedicationPlan? {
            reads += 1
            return delegate.getById(id)
        }

        override suspend fun save(plan: MedicationPlan): PlanSaveResult {
            writes += 1
            return delegate.save(plan)
        }

        override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult {
            writes += 1
            return delegate.setEnabled(id, enabled)
        }

        override suspend fun delete(id: UUID): DeleteResult {
            writes += 1
            return delegate.delete(id)
        }

        override suspend fun deleteAll(): DeleteResult {
            writes += 1
            return delegate.deleteAll()
        }
    }
}
