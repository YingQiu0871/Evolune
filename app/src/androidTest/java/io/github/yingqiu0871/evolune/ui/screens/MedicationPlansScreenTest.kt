package io.github.yingqiu0871.evolune.ui.screens

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.res.stringResource
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.application.MedicationPlanEditSessionFactory
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanUpdateResult
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.reminder.MedicationPlanReminderScheduler
import io.github.yingqiu0871.evolune.ui.components.MedicationPlanBottomSheet
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperation
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationError
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationState
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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

    @Volatile
    private var composeImeVisible = false

    private fun readImeState(): String {
        val state = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys input_method")
            .use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }
        return state
    }

    private fun imeIsVisible(): Boolean = readImeState().contains("mInputShown=true")

    private fun imeIsHidden(): Boolean {
        val state = readImeState()
        val imeWindowHidden = state.lineSequence()
            .map(String::trim)
            .any { it == "mImeWindowVis=0" }
        return imeWindowHidden && state.contains("mInputShown=false")
    }

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
        viewModel.startCreateSession()
        setScreen(viewModel, is24Hour = recomposeState.value)

        val session = requireNotNull(viewModel.editSession.value)
        composeRule.runOnIdle { recomposeState.value = false }
        composeRule.waitForIdle()
        assertSame(session, viewModel.editSession.value)
        assertEquals(0, repository.saveCalls)
        composeRule.onNodeWithTag("plan-name").assertIsDisplayed()
    }

    @Test
    fun dismissingImeKeepsEditorOpenAndPreservesDraft() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        viewModel.startCreateSession()
        val session = requireNotNull(viewModel.editSession.value)
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-name")
            .performClick()
            .performTextInput("BF1 IME test")

        composeRule.waitUntil(5_000L) { imeIsVisible() && composeImeVisible }
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        composeRule.waitForIdle()

        composeRule.waitUntil(5_000L) { imeIsHidden() && !composeImeVisible }
        composeRule.onNodeWithTag("plan-editor-surface").assertExists()
        assertEquals(
            "BF1 IME test",
            composeRule.onNodeWithTag("plan-name")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        assertSame(session, viewModel.editSession.value)

        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }
        assertTrue(composeRule.onAllNodesWithTag("plan-editor-surface").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun operationInProgressConsumesBackInsteadOfClosingPlanEditor() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply { saveGate = gate }
        val viewModel = viewModel(repository)
        viewModel.startEditSession(plan())
        val session = requireNotNull(viewModel.editSession.value)
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is MedicationPlanOperationState.Running
        }

        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        composeRule.waitForIdle()

        assertSame(session, viewModel.editSession.value)
        composeRule.onNodeWithTag("plan-editor-surface").assertExists()

        gate.complete(Unit)
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }
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
        composeRule.onNodeWithTag("plan-editor-surface").assertExists()
        composeRule.onNodeWithTag("plan-name").assertExists()
        composeRule.onNodeWithTag("plan-error").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.plan_error_invalid_input)).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(string(R.string.common_unknown_error))
                .fetchSemanticsNodes().isEmpty()
        )
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
        composeRule.onNodeWithTag("plan-editor-surface").assertExists()
        composeRule.onNodeWithTag("plan-name").assertExists()
        composeRule.onNodeWithTag("plan-error").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.plan_error_invalid_plan)).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(string(R.string.common_unknown_error))
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun saveSuccessClosesEditorWithoutUnknownError() {
        val repository = FakeRepository().apply { saveResult = PlanSaveResult.Updated }
        val viewModel = viewModel(repository)
        viewModel.startEditSession(plan())
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }

        assertEquals(1, repository.saveCalls)
        assertTrue(composeRule.onAllNodesWithTag("plan-name").fetchSemanticsNodes().isEmpty())
        assertTrue(
            composeRule.onAllNodesWithText(string(R.string.common_unknown_error))
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun storageFailureShowsSaveFailureInsteadOfUnknownError() {
        val repository = FakeRepository().apply {
            saveError = IllegalStateException("synthetic storage failure")
        }
        val viewModel = viewModel(repository)
        viewModel.startEditSession(plan())
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is MedicationPlanOperationState.Failure
        }

        composeRule.onNodeWithText(string(R.string.plan_error_save_failed)).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(string(R.string.common_unknown_error))
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun unexpectedFailureIsTheOnlySavePathUsingUnknownError() {
        val repository = FakeRepository().apply {
            saveError = UnsupportedOperationException("synthetic unexpected failure")
        }
        val viewModel = viewModel(repository)
        viewModel.startEditSession(plan())
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is MedicationPlanOperationState.Failure
        }

        composeRule.onNodeWithText(string(R.string.common_unknown_error)).assertIsDisplayed()
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
        composeRule.onNodeWithTag("plan-editor-surface").assertExists()
        composeRule.onNodeWithTag("plan-name").assertExists()
        composeRule.onNodeWithTag("plan-error").assertIsDisplayed()
    }

    @Test
    fun busyPlanDisablesOnlyItsOwnSwitch() {
        val first = plan()
        val secondId = UUID(0L, 302L)
        val secondBase = plan()
        val second = secondBase.copy(
            id = secondId,
            name = "Other plan",
            slots = secondBase.slots.map { it.copy(planId = secondId) }
        )
        composeRule.setContent {
            EvoluneTheme {
                MedicationPlansScreenContent(
                    plans = listOf(first, second),
                    onPlanClick = {},
                    onAddClick = {},
                    onToggleEnabled = { _, _ -> },
                    enabledPlanIdsInFlight = setOf(first.id),
                    showTopBar = false
                )
            }
        }

        composeRule.onNodeWithTag("plan-enabled-${first.id}").assertIsNotEnabled()
        composeRule.onNodeWithTag("plan-enabled-${second.id}").assertIsEnabled()
    }

    @Test
    fun portraitOptionGridKeepsRouteSizesStableAcrossSelection() {
        val viewModel = viewModel(FakeRepository())
        viewModel.startCreateSession()
        setScreen(viewModel)

        val routeTags = listOf(
            "plan-route-injection",
            "plan-route-oral",
            "plan-route-sublingual",
            "plan-route-gel",
            "plan-route-antiandrogen"
        )
        val antiAndrogenOption = composeRule.onNodeWithTag("plan-route-antiandrogen").performScrollTo()
        composeRule.waitForIdle()
        val initialBounds = routeTags.associateWith { tag ->
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        }

        val referenceBounds = initialBounds.getValue("plan-route-injection")
        initialBounds.values.forEach { bounds ->
            // Equal weights may distribute one remaining physical pixel across a row.
            assertEquals(referenceBounds.width.toDouble(), bounds.width.toDouble(), 1.1)
            assertEquals(referenceBounds.height.toDouble(), bounds.height.toDouble(), 0.5)
        }
        assertTrue(referenceBounds.width > referenceBounds.height * 1.5f)

        val antiAndrogenBounds = initialBounds.getValue("plan-route-antiandrogen")
        antiAndrogenOption.performClick().assertIsSelected()
        composeRule.waitForIdle()
        assertBoundsSizeEqual(
            antiAndrogenBounds,
            antiAndrogenOption.fetchSemanticsNode().boundsInRoot
        )
    }

    @Test
    fun editActionsHaveEqualStableSizes() {
        val viewModel = viewModel(FakeRepository())
        viewModel.startEditSession(plan())
        setScreen(viewModel)

        composeRule.onNodeWithTag("plan-save").performScrollTo()
        composeRule.waitForIdle()
        val deleteBounds = composeRule.onNodeWithTag("plan-delete").fetchSemanticsNode().boundsInRoot
        val cancelBounds = composeRule.onNodeWithTag("plan-cancel").fetchSemanticsNode().boundsInRoot
        val saveBounds = composeRule.onNodeWithTag("plan-save").fetchSemanticsNode().boundsInRoot

        assertBoundsSizeEqual(deleteBounds, cancelBounds)
        assertBoundsSizeEqual(deleteBounds, saveBounds)
    }

    private fun assertBoundsSizeEqual(
        expected: androidx.compose.ui.geometry.Rect,
        actual: androidx.compose.ui.geometry.Rect
    ) {
        assertEquals(expected.width.toDouble(), actual.width.toDouble(), 1.1)
        assertEquals(expected.height.toDouble(), actual.height.toDouble(), 0.5)
    }

    private fun setScreen(
        viewModel: MedicationPlanViewModel,
        is24Hour: Boolean = true
    ) {
        composeRule.setContent {
            EvoluneTheme {
                LaunchedEffect(viewModel) {
                    viewModel.operationState.collect { state ->
                        if (state is MedicationPlanOperationState.Success) {
                            if (state.result.operation in listOf(
                                    MedicationPlanOperation.SAVE,
                                    MedicationPlanOperation.DELETE
                                )
                            ) {
                                viewModel.closeEditSession()
                            }
                            viewModel.acknowledgeOperation()
                        }
                    }
                }
                val editSession by viewModel.editSession.collectAsState()
                val operationState by viewModel.operationState.collectAsState()
                val currentImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                SideEffect { composeImeVisible = currentImeVisible }
                val submissionFailure = operationState as? MedicationPlanOperationState.Failure
                val unknownErrorMessage = stringResource(R.string.common_unknown_error)
                val submissionErrorMessage = submissionFailure?.let { failure ->
                    when (failure.error) {
                        is MedicationPlanOperationError.InvalidDraft ->
                            stringResource(R.string.plan_error_invalid_input)
                        MedicationPlanOperationError.RepositoryInvalid ->
                            stringResource(R.string.plan_error_invalid_plan)
                        MedicationPlanOperationError.NotFound ->
                            stringResource(R.string.plan_error_not_found)
                        MedicationPlanOperationError.StorageFailure -> when (failure.operation) {
                            MedicationPlanOperation.SAVE ->
                                stringResource(R.string.plan_error_save_failed)
                            MedicationPlanOperation.DELETE ->
                                stringResource(R.string.plan_error_delete_failed)
                            MedicationPlanOperation.SET_ENABLED,
                            MedicationPlanOperation.RESCHEDULE -> unknownErrorMessage
                        }
                        MedicationPlanOperationError.UnexpectedFailure -> unknownErrorMessage
                    }
                }
                MedicationPlanBottomSheet(
                    showBottomSheet = editSession != null,
                    onDismiss = {
                        viewModel.closeEditSession()
                        viewModel.acknowledgeOperation()
                    },
                    onSave = viewModel::saveDraft,
                    onDelete = viewModel::deletePlan,
                    session = editSession,
                    is24Hour = is24Hour,
                    operationInProgress = operationState is MedicationPlanOperationState.Running,
                    submissionErrorMessage = submissionErrorMessage.takeIf {
                        submissionFailure?.operation in listOf(
                            MedicationPlanOperation.SAVE,
                            MedicationPlanOperation.DELETE
                        )
                    }
                )
            }
        }
    }

    private fun string(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

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
        var saveError: RuntimeException? = null
        var saveGate: CompletableDeferred<Unit>? = null
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
            saveError?.let { throw it }
            saveGate?.await()
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
