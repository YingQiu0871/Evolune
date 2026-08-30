package io.github.yingqiu0871.evolune.core.dataapi

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class RepositoryContractTest {
    @Test
    fun doseEventContractUsesOnlyDomainAndStandardTimeTypes() = runBlocking {
        val event = doseEvent()
        val repository: DoseEventRepository = SignatureOnlyDoseEventRepository(event)
        val start = Instant.parse("2024-01-01T00:00:00Z")
        val end = Instant.parse("2024-02-01T00:00:00Z")

        assertEquals(listOf(event), repository.observeAll().first())
        assertEquals(event, repository.getById(event.id))
        assertTrue(repository.findOccurredBetween(start, end).isEmpty())
        assertTrue(repository.getEventsForPk(end).isEmpty())
        assertSame(InsertResult.Inserted, repository.insert(event))
        assertSame(UpdateResult.NoChange, repository.update(event, expectedRevision = 1))
        assertSame(DeleteResult.NotFound, repository.delete(event.id))
        assertSame(DeleteResult.NotFound, repository.deleteAll())
    }

    @Test
    fun medicationPlanContractUsesOnlyDomainTypes() = runBlocking {
        val plan = medicationPlan()
        val repository: MedicationPlanRepository = SignatureOnlyMedicationPlanRepository(plan)

        assertEquals(listOf(plan), repository.observeAll().first())
        assertEquals(listOf(plan), repository.observeEnabled().first())
        assertEquals(plan, repository.getById(plan.id))
        assertSame(PlanSaveResult.NoChange, repository.save(plan))
        assertSame(
            PlanUpdateResult.NoChange,
            repository.setEnabled(plan.id, enabled = true)
        )
        assertSame(DeleteResult.NotFound, repository.delete(plan.id))
        assertSame(DeleteResult.NotFound, repository.deleteAll())
    }

    @Test
    fun allResolvedResultVariantsRemainDistinctAndExhaustive() {
        assertEquals(
            listOf("Inserted", "Idempotent", "Conflict", "Invalid"),
            listOf(
                InsertResult.Inserted,
                InsertResult.Idempotent,
                InsertResult.Conflict,
                InsertResult.Invalid
            ).map(::insertResultName)
        )
        assertEquals(
            listOf("Updated", "NoChange", "NotFound", "RevisionConflict", "Invalid"),
            listOf(
                UpdateResult.Updated,
                UpdateResult.NoChange,
                UpdateResult.NotFound,
                UpdateResult.RevisionConflict,
                UpdateResult.Invalid
            ).map(::updateResultName)
        )
        assertEquals(
            listOf("Deleted", "NotFound"),
            listOf(DeleteResult.Deleted, DeleteResult.NotFound).map(::deleteResultName)
        )
        assertEquals(
            listOf("Created", "Updated", "NoChange", "Invalid"),
            listOf(
                PlanSaveResult.Created,
                PlanSaveResult.Updated,
                PlanSaveResult.NoChange,
                PlanSaveResult.Invalid
            ).map(::planSaveResultName)
        )
        assertEquals(
            listOf("Updated", "NoChange", "NotFound", "Invalid"),
            listOf(
                PlanUpdateResult.Updated,
                PlanUpdateResult.NoChange,
                PlanUpdateResult.NotFound,
                PlanUpdateResult.Invalid
            ).map(::planUpdateResultName)
        )
    }

    private class SignatureOnlyDoseEventRepository(
        private val event: DoseEvent
    ) : DoseEventRepository {
        override fun observeAll(): Flow<List<DoseEvent>> = flowOf(listOf(event))

        override suspend fun getById(id: UUID): DoseEvent? =
            event.takeIf { it.id == id }

        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> = emptyList()

        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = emptyList()

        override suspend fun insert(event: DoseEvent): InsertResult = InsertResult.Inserted

        override suspend fun update(
            event: DoseEvent,
            expectedRevision: Long
        ): UpdateResult = UpdateResult.NoChange

        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.NotFound

        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult = ConditionalDeleteResult.NotFound

        override suspend fun deleteLatestRecordedIfRevisionMatches(
            eventId: UUID,
            eventRevision: Long
        ): LatestDoseDeleteResult = LatestDoseDeleteResult.EventNotFound

        override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
    }

    private class SignatureOnlyMedicationPlanRepository(
        private val plan: MedicationPlan
    ) : MedicationPlanRepository {
        override fun observeAll(): Flow<List<MedicationPlan>> = flowOf(listOf(plan))

        override fun observeEnabled(): Flow<List<MedicationPlan>> = flowOf(listOf(plan))

        override suspend fun getById(id: UUID): MedicationPlan? =
            plan.takeIf { it.id == id }

        override suspend fun save(plan: MedicationPlan): PlanSaveResult =
            PlanSaveResult.NoChange

        override suspend fun setEnabled(
            id: UUID,
            enabled: Boolean
        ): PlanUpdateResult = PlanUpdateResult.NoChange

        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.NotFound

        override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
    }

    private fun doseEvent(): DoseEvent = DoseEvent(
        id = UUID.fromString("40000000-0000-0000-0000-000000000001"),
        route = Route.ORAL,
        occurredAt = Instant.parse("2024-01-15T12:00:00Z"),
        doseMG = 2.0,
        ester = Ester.E2,
        extras = mapOf(ExtraKey.CONCENTRATION_MG_ML to 1.0),
        source = DoseEventSource.MANUAL
    )

    private fun medicationPlan(): MedicationPlan {
        val planId = UUID.fromString("50000000-0000-0000-0000-000000000001")
        return MedicationPlan(
            id = planId,
            name = "Synthetic contract plan",
            route = Route.ORAL,
            ester = Ester.E2,
            doseMG = 2.0,
            scheduleType = ScheduleType.DAILY,
            slots = listOf(
                ScheduledDoseSlot(
                    id = UUID.fromString("60000000-0000-0000-0000-000000000001"),
                    planId = planId,
                    localTime = LocalTime.of(8, 0),
                    position = 0
                )
            ),
            daysOfWeek = emptySet(),
            intervalDays = 1,
            isEnabled = true,
            extras = emptyMap(),
            createdAt = Instant.parse("2024-01-01T00:00:00Z")
        )
    }

    private fun insertResultName(result: InsertResult): String = when (result) {
        InsertResult.Inserted -> "Inserted"
        InsertResult.Idempotent -> "Idempotent"
        InsertResult.Conflict -> "Conflict"
        InsertResult.Invalid -> "Invalid"
    }

    private fun updateResultName(result: UpdateResult): String = when (result) {
        UpdateResult.Updated -> "Updated"
        UpdateResult.NoChange -> "NoChange"
        UpdateResult.NotFound -> "NotFound"
        UpdateResult.RevisionConflict -> "RevisionConflict"
        UpdateResult.Invalid -> "Invalid"
    }

    private fun deleteResultName(result: DeleteResult): String = when (result) {
        DeleteResult.Deleted -> "Deleted"
        DeleteResult.NotFound -> "NotFound"
    }

    private fun planSaveResultName(result: PlanSaveResult): String = when (result) {
        PlanSaveResult.Created -> "Created"
        PlanSaveResult.Updated -> "Updated"
        PlanSaveResult.NoChange -> "NoChange"
        PlanSaveResult.Invalid -> "Invalid"
    }

    private fun planUpdateResultName(result: PlanUpdateResult): String = when (result) {
        PlanUpdateResult.Updated -> "Updated"
        PlanUpdateResult.NoChange -> "NoChange"
        PlanUpdateResult.NotFound -> "NotFound"
        PlanUpdateResult.Invalid -> "Invalid"
    }
}
