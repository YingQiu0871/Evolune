package io.github.yuninggu.evolune.viewmodel

import io.github.yuninggu.evolune.application.DraftField
import io.github.yuninggu.evolune.application.DraftIssue
import io.github.yuninggu.evolune.application.MedicationPlanDraft
import io.github.yuninggu.evolune.application.MedicationPlanEditSessionFactory
import io.github.yuninggu.evolune.core.dataapi.DeleteResult
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.core.dataapi.PlanSaveResult
import io.github.yuninggu.evolune.core.dataapi.PlanUpdateResult
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlot
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.reminder.MedicationPlanReminderScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class MedicationPlanViewModelTest {
    @Test
    fun `create session remains stable through recomposition validation and save failure`() {
        var nextId = 1L
        val repository = FakeMedicationPlanRepository().apply {
            saveError = IllegalStateException("synthetic storage failure")
        }
        val fixture = fixture(
            repository = repository,
            sessionFactory = MedicationPlanEditSessionFactory(
                idSupplier = { UUID(0L, nextId++) },
                clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC)
            )
        )
        try {
            fixture.viewModel.startCreateSession()
            val session = requireNotNull(fixture.viewModel.editSession.value)
            fixture.viewModel.startCreateSession()
            assertSame(session, fixture.viewModel.editSession.value)

            fixture.viewModel.saveDraft(draft(id = session.id, name = " "))
            assertSame(session, fixture.viewModel.editSession.value)
            assertEquals(0, repository.saveCalls)

            fixture.viewModel.saveDraft(draft(id = session.id))
            assertSame(session, fixture.viewModel.editSession.value)
            assertEquals(1, repository.saveCalls)

            fixture.viewModel.closeEditSession()
            fixture.viewModel.startCreateSession()
            assertEquals(UUID(0L, 2L), fixture.viewModel.editSession.value?.id)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `Created Updated and NoChange are successful before one reminder side effect`() {
        listOf(
            PlanSaveResult.Created,
            PlanSaveResult.Updated,
            PlanSaveResult.NoChange
        ).forEach { repositoryResult ->
            val log = mutableListOf<String>()
            val repository = FakeMedicationPlanRepository(log).apply {
                saveResult = repositoryResult
            }
            val reminder = FakeReminderScheduler(log)
            val fixture = fixture(repository, reminder)
            try {
                fixture.viewModel.saveDraft(draft())

                val state = fixture.viewModel.operationState.value
                assertTrue(state is MedicationPlanOperationState.Success)
                val saved = (state as MedicationPlanOperationState.Success).result
                    as MedicationPlanOperationSuccess.Saved
                assertEquals(repositoryResult, saved.repositoryResult)
                assertEquals(ReminderSideEffectResult.APPLIED, saved.reminder)
                assertEquals(listOf("repository.save", "reminder.schedule"), log)
                assertEquals(1, repository.saveCalls)
                assertEquals(1, reminder.scheduleCalls)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `disabled save cancels only after persistence success`() {
        val log = mutableListOf<String>()
        val repository = FakeMedicationPlanRepository(log)
        val reminder = FakeReminderScheduler(log)
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.saveDraft(draft(isEnabled = false))

            assertEquals(listOf("repository.save", "reminder.cancel"), log)
            assertEquals(0, reminder.scheduleCalls)
            assertEquals(1, reminder.cancelCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `Draft and Repository invalid failures never call reminder`() {
        val repository = FakeMedicationPlanRepository().apply {
            saveResult = PlanSaveResult.Invalid
        }
        val reminder = FakeReminderScheduler()
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.saveDraft(draft(name = ""))
            assertEquals(0, repository.saveCalls)
            assertEquals(
                MedicationPlanOperationState.Failure(
                    MedicationPlanOperation.SAVE,
                    MedicationPlanOperationError.InvalidDraft(
                        listOf(DraftIssue.MissingRequiredField(DraftField.NAME))
                    )
                ),
                fixture.viewModel.operationState.value
            )

            fixture.viewModel.saveDraft(draft())
            assertEquals(1, repository.saveCalls)
            assertEquals(
                MedicationPlanOperationState.Failure(
                    MedicationPlanOperation.SAVE,
                    MedicationPlanOperationError.RepositoryInvalid
                ),
                fixture.viewModel.operationState.value
            )
            assertEquals(0, reminder.totalCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `storage failure is structured and has no reminder fallback`() {
        val repository = FakeMedicationPlanRepository().apply {
            saveError = IllegalStateException("synthetic storage failure")
        }
        val reminder = FakeReminderScheduler()
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.saveDraft(draft())

            assertEquals(
                MedicationPlanOperationState.Failure(
                    MedicationPlanOperation.SAVE,
                    MedicationPlanOperationError.StorageFailure
                ),
                fixture.viewModel.operationState.value
            )
            assertEquals(0, reminder.totalCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `unexpected repository runtime failure is distinct from storage failure`() {
        val repository = FakeMedicationPlanRepository().apply {
            unexpectedSaveError = UnsupportedOperationException("synthetic unexpected failure")
        }
        val reminder = FakeReminderScheduler()
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.saveDraft(draft())

            assertEquals(
                MedicationPlanOperationState.Failure(
                    MedicationPlanOperation.SAVE,
                    MedicationPlanOperationError.UnexpectedFailure
                ),
                fixture.viewModel.operationState.value
            )
            assertEquals(0, reminder.totalCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `repository cancellation returns operation to idle instead of unknown failure`() {
        val repository = FakeMedicationPlanRepository().apply {
            saveError = CancellationException("synthetic cancellation")
        }
        val reminder = FakeReminderScheduler()
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.saveDraft(draft())

            assertEquals(MedicationPlanOperationState.Idle, fixture.viewModel.operationState.value)
            assertEquals(0, reminder.totalCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `delete success cancels once while NotFound and storage failure do not`() {
        listOf(
            DeleteResult.Deleted to true,
            DeleteResult.NotFound to false
        ).forEach { (result, shouldCancel) ->
            val repository = FakeMedicationPlanRepository().apply { deleteResult = result }
            val reminder = FakeReminderScheduler()
            val fixture = fixture(repository, reminder)
            try {
                fixture.viewModel.deletePlan(PLAN_ID)

                assertEquals(if (shouldCancel) 1 else 0, reminder.cancelCalls)
                if (shouldCancel) {
                    assertTrue(fixture.viewModel.operationState.value is MedicationPlanOperationState.Success)
                } else {
                    assertEquals(
                        MedicationPlanOperationState.Failure(
                            MedicationPlanOperation.DELETE,
                            MedicationPlanOperationError.NotFound
                        ),
                        fixture.viewModel.operationState.value
                    )
                }
            } finally {
                fixture.close()
            }
        }

        val repository = FakeMedicationPlanRepository().apply {
            deleteError = IllegalStateException("synthetic delete failure")
        }
        val reminder = FakeReminderScheduler()
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.deletePlan(PLAN_ID)
            assertEquals(0, reminder.cancelCalls)
            assertEquals(
                MedicationPlanOperationState.Failure(
                    MedicationPlanOperation.DELETE,
                    MedicationPlanOperationError.StorageFailure
                ),
                fixture.viewModel.operationState.value
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `setEnabled applies matching side effect only for successful Repository results`() {
        val repository = FakeMedicationPlanRepository().apply {
            planById = plan(isEnabled = true)
            updateResult = PlanUpdateResult.Updated
        }
        val reminder = FakeReminderScheduler()
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.setPlanEnabled(PLAN_ID, true)
            assertEquals(1, reminder.scheduleCalls)
            assertEquals(0, reminder.cancelCalls)

            repository.updateResult = PlanUpdateResult.NoChange
            fixture.viewModel.setPlanEnabled(PLAN_ID, false)
            assertEquals(1, reminder.scheduleCalls)
            assertEquals(1, reminder.cancelCalls)

            listOf(PlanUpdateResult.NotFound, PlanUpdateResult.Invalid).forEach { failure ->
                repository.updateResult = failure
                fixture.viewModel.setPlanEnabled(PLAN_ID, true)
            }
            assertEquals(1, reminder.scheduleCalls)
            assertEquals(1, reminder.cancelCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `reminder failure remains separate from successful persistence`() {
        val reminder = FakeReminderScheduler().apply { scheduleError = RuntimeException("synthetic") }
        val fixture = fixture(FakeMedicationPlanRepository(), reminder)
        try {
            fixture.viewModel.saveDraft(draft())

            val state = fixture.viewModel.operationState.value as MedicationPlanOperationState.Success
            val saved = state.result as MedicationPlanOperationSuccess.Saved
            assertEquals(PlanSaveResult.Created, saved.repositoryResult)
            assertEquals(ReminderSideEffectResult.FAILED, saved.reminder)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `duplicate save while first write is suspended invokes Repository once`() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeMedicationPlanRepository().apply { saveGate = gate }
        val reminder = FakeReminderScheduler()
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.saveDraft(draft())
            assertTrue(fixture.viewModel.operationState.value is MedicationPlanOperationState.Running)

            fixture.viewModel.saveDraft(draft(doseMG = 3.0))
            assertEquals(1, repository.saveCalls)
            assertEquals(0, reminder.totalCalls)

            gate.complete(Unit)
            assertEquals(1, repository.saveCalls)
            assertEquals(1, reminder.scheduleCalls)
            assertTrue(fixture.viewModel.operationState.value is MedicationPlanOperationState.Success)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `reschedule reads contract plans and reports reminder result`() {
        val repository = FakeMedicationPlanRepository().apply {
            allPlans.value = listOf(plan())
        }
        val reminder = FakeReminderScheduler()
        val fixture = fixture(repository, reminder)
        try {
            fixture.viewModel.rescheduleAllReminders()

            assertEquals(1, reminder.rescheduleCalls)
            assertEquals(repository.allPlans.value, reminder.lastRescheduledPlans)
            assertEquals(
                MedicationPlanOperationState.Success(
                    MedicationPlanOperationSuccess.Rescheduled(ReminderSideEffectResult.APPLIED)
                ),
                fixture.viewModel.operationState.value
            )
        } finally {
            fixture.close()
        }
    }

    private fun fixture(
        repository: FakeMedicationPlanRepository,
        reminder: FakeReminderScheduler = FakeReminderScheduler(),
        sessionFactory: MedicationPlanEditSessionFactory = MedicationPlanEditSessionFactory()
    ): ViewModelFixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return ViewModelFixture(
            MedicationPlanViewModel(repository, reminder, sessionFactory, scope),
            scope
        )
    }

    private fun draft(
        id: UUID = PLAN_ID,
        name: String = "Synthetic ViewModel plan",
        doseMG: Double = 2.0,
        isEnabled: Boolean = true
    ): MedicationPlanDraft = MedicationPlanDraft(
        id = id,
        name = name,
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = doseMG,
        scheduleType = ScheduleType.DAILY,
        times = listOf(LocalTime.of(8, 30)),
        daysOfWeek = emptySet(),
        intervalDays = 1,
        isEnabled = isEnabled,
        extras = emptyMap(),
        createdAt = CREATED_AT
    )

    private fun plan(isEnabled: Boolean = true): MedicationPlan = MedicationPlan(
        id = PLAN_ID,
        name = "Synthetic ViewModel plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = ScheduleType.DAILY,
        slots = listOf(
            ScheduledDoseSlot(
                id = UUID(1L, 1L),
                planId = PLAN_ID,
                localTime = LocalTime.of(8, 30),
                position = 0
            )
        ),
        daysOfWeek = emptySet(),
        intervalDays = 1,
        isEnabled = isEnabled,
        extras = emptyMap(),
        createdAt = CREATED_AT
    )

    private data class ViewModelFixture(
        val viewModel: MedicationPlanViewModel,
        val scope: CoroutineScope
    ) {
        fun close() = scope.cancel()
    }

    private class FakeMedicationPlanRepository(
        private val operationLog: MutableList<String>? = null
    ) : MedicationPlanRepository {
        val allPlans = MutableStateFlow<List<MedicationPlan>>(emptyList())
        val enabledPlans = MutableStateFlow<List<MedicationPlan>>(emptyList())
        var planById: MedicationPlan? = null
        var saveResult: PlanSaveResult = PlanSaveResult.Created
        var updateResult: PlanUpdateResult = PlanUpdateResult.Updated
        var deleteResult: DeleteResult = DeleteResult.Deleted
        var saveError: IllegalStateException? = null
        var unexpectedSaveError: RuntimeException? = null
        var updateError: IllegalStateException? = null
        var deleteError: IllegalStateException? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var saveCalls: Int = 0
        var updateCalls: Int = 0
        var deleteCalls: Int = 0

        override fun observeAll(): Flow<List<MedicationPlan>> = allPlans

        override fun observeEnabled(): Flow<List<MedicationPlan>> = enabledPlans

        override suspend fun getById(id: UUID): MedicationPlan? = planById

        override suspend fun save(plan: MedicationPlan): PlanSaveResult {
            saveCalls += 1
            operationLog?.add("repository.save")
            saveGate?.await()
            saveError?.let { throw it }
            unexpectedSaveError?.let { throw it }
            return saveResult
        }

        override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult {
            updateCalls += 1
            updateError?.let { throw it }
            return updateResult
        }

        override suspend fun delete(id: UUID): DeleteResult {
            deleteCalls += 1
            deleteError?.let { throw it }
            return deleteResult
        }

        override suspend fun deleteAll(): DeleteResult = deleteResult
    }

    private class FakeReminderScheduler(
        private val operationLog: MutableList<String>? = null
    ) : MedicationPlanReminderScheduler {
        var scheduleCalls: Int = 0
        var cancelCalls: Int = 0
        var rescheduleCalls: Int = 0
        var scheduleError: RuntimeException? = null
        var lastRescheduledPlans: List<MedicationPlan> = emptyList()

        val totalCalls: Int
            get() = scheduleCalls + cancelCalls + rescheduleCalls

        override fun schedule(plan: MedicationPlan) {
            scheduleCalls += 1
            operationLog?.add("reminder.schedule")
            scheduleError?.let { throw it }
        }

        override fun cancel(planId: UUID) {
            cancelCalls += 1
            operationLog?.add("reminder.cancel")
        }

        override suspend fun reschedule(plans: List<MedicationPlan>) {
            rescheduleCalls += 1
            lastRescheduledPlans = plans
        }
    }

    private companion object {
        val PLAN_ID: UUID = UUID(0L, 501L)
        val CREATED_AT: Instant = Instant.parse("2024-01-02T03:04:05Z")
    }
}
