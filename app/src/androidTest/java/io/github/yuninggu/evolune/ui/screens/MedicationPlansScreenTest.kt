package io.github.yuninggu.evolune.ui.screens

import android.Manifest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
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
import io.github.yuninggu.evolune.ui.theme.EvoluneTheme
import io.github.yuninggu.evolune.viewmodel.MedicationPlanOperationState
import io.github.yuninggu.evolune.viewmodel.MedicationPlanViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class MedicationPlansScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun grantNotificationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${instrumentation.targetContext.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
        ).close()
    }

    @After
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    @Test
    fun createSessionSurvivesRecomposition() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        val recomposeState = mutableStateOf(true)
        composeRule.setContent {
            EvoluneTheme {
                MedicationPlansScreen(viewModel, is24Hour = recomposeState.value)
            }
        }

        composeRule.onNodeWithTag("plan-add").performClick()
        val session = requireNotNull(viewModel.editSession.value)
        composeRule.runOnIdle { recomposeState.value = false }
        composeRule.waitForIdle()
        assertSame(session, viewModel.editSession.value)
        assertEquals(0, repository.saveCalls)
        composeRule.onNodeWithTag("plan-name").assertIsDisplayed()
    }

    @Test
    fun invalidDraftSkipsRepositoryAndKeepsEditorOpen() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        viewModel.startEditSession(plan().copy(name = ""))
        val session = requireNotNull(viewModel.editSession.value)
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is MedicationPlanOperationState.Failure
        }

        assertEquals(0, repository.saveCalls)
        assertSame(session, viewModel.editSession.value)
        composeRule.onNodeWithTag("plan-name").assertIsDisplayed()
        composeRule.onNodeWithTag("plan-error").assertIsDisplayed()
    }

    @Test
    fun saveFailureKeepsEditorOpenAndShowsError() {
        val repository = FakeRepository().apply { saveResult = PlanSaveResult.Invalid }
        val viewModel = viewModel(repository)
        viewModel.startEditSession(plan())
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is MedicationPlanOperationState.Failure
        }

        assertEquals(1, repository.saveCalls)
        assertTrue(viewModel.editSession.value != null)
        composeRule.onNodeWithTag("plan-name").assertIsDisplayed()
        composeRule.onNodeWithTag("plan-error").assertIsDisplayed()
    }

    @Test
    fun saveSuccessClosesEditor() {
        val repository = FakeRepository().apply { saveResult = PlanSaveResult.Updated }
        val viewModel = viewModel(repository)
        viewModel.startEditSession(plan())
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }

        assertEquals(1, repository.saveCalls)
        composeRule.onNodeWithTag("plan-name").assertDoesNotExist()
    }

    @Test
    fun deleteFailureKeepsEditorOpen() {
        val repository = FakeRepository().apply { deleteResult = DeleteResult.NotFound }
        val viewModel = viewModel(repository)
        viewModel.startEditSession(plan())
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-delete").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is MedicationPlanOperationState.Failure
        }

        assertEquals(1, repository.deleteCalls)
        assertTrue(viewModel.editSession.value != null)
        composeRule.onNodeWithTag("plan-name").assertIsDisplayed()
        composeRule.onNodeWithTag("plan-error").assertIsDisplayed()
    }

    private fun setScreen(viewModel: MedicationPlanViewModel) {
        composeRule.setContent {
            EvoluneTheme {
                MedicationPlansScreen(viewModel)
            }
        }
    }

    private fun viewModel(repository: FakeRepository): MedicationPlanViewModel {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        return MedicationPlanViewModel(
            repository = repository,
            reminderScheduler = ReminderSpy(),
            sessionFactory = MedicationPlanEditSessionFactory(
                idSupplier = { PLAN_ID },
                clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC)
            ),
            operationScope = scope
        )
    }

    private fun plan(): MedicationPlan = MedicationPlan(
        id = PLAN_ID,
        name = "Synthetic UI plan",
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
        isEnabled = false,
        extras = emptyMap(),
        createdAt = CREATED_AT
    )

    private class FakeRepository : MedicationPlanRepository {
        val plans = MutableStateFlow<List<MedicationPlan>>(emptyList())
        var saveResult: PlanSaveResult = PlanSaveResult.Created
        var deleteResult: DeleteResult = DeleteResult.Deleted
        var saveCalls = 0
        var deleteCalls = 0

        override fun observeAll(): Flow<List<MedicationPlan>> = plans

        override fun observeEnabled(): Flow<List<MedicationPlan>> = plans

        override suspend fun getById(id: UUID): MedicationPlan? = plans.value.firstOrNull {
            it.id == id
        }

        override suspend fun save(plan: MedicationPlan): PlanSaveResult {
            saveCalls += 1
            return saveResult
        }

        override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult =
            PlanUpdateResult.Updated

        override suspend fun delete(id: UUID): DeleteResult {
            deleteCalls += 1
            return deleteResult
        }

        override suspend fun deleteAll(): DeleteResult = deleteResult
    }

    private class ReminderSpy : MedicationPlanReminderScheduler {
        override fun schedule(plan: MedicationPlan) = Unit

        override fun cancel(planId: UUID) = Unit

        override suspend fun reschedule(plans: List<MedicationPlan>) = Unit
    }

    private companion object {
        val PLAN_ID: UUID = UUID(0L, 1001L)
        val CREATED_AT: Instant = Instant.parse("2024-01-02T03:04:05Z")
    }
}
