package io.github.yuninggu.evolune.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import io.github.yuninggu.evolune.application.DoseEventEditSessionFactory
import io.github.yuninggu.evolune.application.DoseEventEditorInput
import io.github.yuninggu.evolune.core.dataapi.DeleteResult
import io.github.yuninggu.evolune.core.dataapi.DoseEventRepository
import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.dataapi.MedicationPlanRepository
import io.github.yuninggu.evolune.core.dataapi.PlanSaveResult
import io.github.yuninggu.evolune.core.dataapi.PlanUpdateResult
import io.github.yuninggu.evolune.core.dataapi.UpdateResult
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.ui.theme.EvoluneTheme
import io.github.yuninggu.evolune.viewmodel.DoseEventOperationState
import io.github.yuninggu.evolune.viewmodel.HRTViewModel
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class MedicationRecordsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    @Test
    fun createSuccessClosesEditorAfterContractInsert() {
        val repository = FakeDoseEventRepository()
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)

        composeRule.onNodeWithTag("record-dose").performTextInput("2")
        composeRule.onNodeWithTag("record-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }

        assertEquals(1, repository.insertCalls)
        composeRule.onNodeWithTag("record-dose").assertDoesNotExist()
    }

    @Test
    fun localValidationFailureKeepsEditorOpenAndShowsStructuredError() {
        val repository = FakeDoseEventRepository()
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)
        val session = requireNotNull(viewModel.editSession.value)

        composeRule.runOnIdle {
            viewModel.saveEvent(editorInput(doseMG = 0.0))
        }
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is DoseEventOperationState.Failure
        }

        assertEquals(0, repository.insertCalls)
        assertSame(session, viewModel.editSession.value)
        composeRule.onNodeWithTag("record-dose").assertIsDisplayed()
        composeRule.onNodeWithTag("record-error").assertIsDisplayed()
    }

    @Test
    fun conflictKeepsEditorOpen() {
        assertInsertFailureKeepsEditorOpen(
            FakeDoseEventRepository().apply { insertResult = InsertResult.Conflict }
        )
    }

    @Test
    fun storageFailureKeepsEditorOpen() {
        assertInsertFailureKeepsEditorOpen(
            FakeDoseEventRepository().apply {
                insertError = IllegalStateException("synthetic storage failure")
            }
        )
    }

    private fun assertInsertFailureKeepsEditorOpen(repository: FakeDoseEventRepository) {
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)
        composeRule.runOnIdle {
            viewModel.saveEvent(editorInput(doseMG = 2.0))
        }
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is DoseEventOperationState.Failure
        }

        assertTrue(viewModel.editSession.value != null)
        composeRule.onNodeWithTag("record-dose").assertIsDisplayed()
        composeRule.onNodeWithTag("record-error").assertIsDisplayed()
    }

    @Test
    fun editSuccessPreservesDomainMetadataBeforeClosing() {
        val original = event(
            source = DoseEventSource.WEAR,
            slotId = SLOT_ID,
            revision = 7L
        )
        val repository = FakeDoseEventRepository().apply {
            stored[EVENT_ID] = original
        }
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle { viewModel.startEditSession(original) }

        composeRule.onNodeWithTag("record-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }

        val updated = repository.updated.single().first
        assertEquals(7L, repository.updated.single().second)
        assertEquals(original.id, updated.id)
        assertEquals(original.occurredAt, updated.occurredAt)
        assertEquals(original.zoneId, updated.zoneId)
        assertEquals(original.localDate, updated.localDate)
        assertEquals(original.slotId, updated.slotId)
        assertEquals(original.source, updated.source)
        assertEquals(original.status, updated.status)
        assertEquals(original.revision, updated.revision)
        assertEquals(original.extras, updated.extras)
    }

    @Test
    fun deleteFailureKeepsEditorOpenAndShowsError() {
        val original = event()
        val repository = FakeDoseEventRepository().apply {
            deleteResult = DeleteResult.NotFound
            stored[EVENT_ID] = original
        }
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle { viewModel.startEditSession(original) }

        composeRule.onNodeWithTag("record-delete").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is DoseEventOperationState.Failure
        }

        assertEquals(1, repository.deleteCalls)
        assertTrue(viewModel.editSession.value != null)
        composeRule.onNodeWithTag("record-dose").assertIsDisplayed()
        composeRule.onNodeWithTag("record-error").assertIsDisplayed()
    }

    @Test
    fun rapidDoubleTapInvokesOneInsert() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeDoseEventRepository().apply { insertGate = gate }
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)
        composeRule.onNodeWithTag("record-dose").performTextInput("2")

        val saveButton = composeRule.onNodeWithTag("record-save").performScrollTo()
        composeRule.waitUntil(5_000L) {
            runCatching {
                saveButton.assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        saveButton.performClick()
        composeRule.waitUntil(5_000L) { repository.insertCalls == 1 }
        saveButton.assertIsNotEnabled()
        saveButton.performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(1, repository.insertCalls)

        gate.complete(Unit)
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }
        assertEquals(1, repository.insertCalls)
    }

    private fun setScreen(viewModel: HRTViewModel) {
        composeRule.setContent {
            EvoluneTheme {
                MedicationRecordsScreen(viewModel)
            }
        }
    }

    private fun viewModel(repository: FakeDoseEventRepository): HRTViewModel {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        return HRTViewModel(
            repository = repository,
            medicationPlanRepository = FakeMedicationPlanRepository(),
            sessionFactory = DoseEventEditSessionFactory(
                idSupplier = { EVENT_ID },
                clock = Clock.fixed(NOW, ZoneOffset.UTC),
                zoneIdSupplier = { TEST_ZONE }
            ),
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            operationScope = scope
        )
    }

    private fun editorInput(doseMG: Double): DoseEventEditorInput = DoseEventEditorInput(
        occurredAt = NOW,
        occurredAtEdited = false,
        route = Route.INJECTION,
        doseMG = doseMG,
        ester = Ester.EV,
        extras = emptyMap()
    )

    private fun event(
        source: DoseEventSource = DoseEventSource.MANUAL,
        slotId: UUID? = null,
        revision: Long = 1L
    ): DoseEvent = DoseEvent(
        id = EVENT_ID,
        route = Route.INJECTION,
        occurredAt = NOW,
        zoneId = TEST_ZONE,
        localDate = NOW.atZone(TEST_ZONE).toLocalDate(),
        doseMG = 2.0,
        ester = Ester.EV,
        extras = mapOf(ExtraKey.CONCENTRATION_MG_ML to 20.0),
        slotId = slotId,
        source = source,
        status = DoseEventStatus.RECORDED,
        revision = revision
    )

    private class FakeDoseEventRepository : DoseEventRepository {
        val events = MutableStateFlow<List<DoseEvent>>(emptyList())
        val stored = mutableMapOf<UUID, DoseEvent>()
        val updated = mutableListOf<Pair<DoseEvent, Long>>()
        var insertResult: InsertResult = InsertResult.Inserted
        var updateResult: UpdateResult = UpdateResult.Updated
        var deleteResult: DeleteResult = DeleteResult.Deleted
        var insertError: RuntimeException? = null
        var insertGate: CompletableDeferred<Unit>? = null
        var insertCalls = 0
        var deleteCalls = 0

        override fun observeAll(): Flow<List<DoseEvent>> = events

        override suspend fun getById(id: UUID): DoseEvent? = stored[id]

        override suspend fun findOccurredBetween(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<DoseEvent> = events.value.filter {
            it.occurredAt >= startInclusive && it.occurredAt < endExclusive
        }

        override suspend fun getEventsForPk(asOf: Instant): List<DoseEvent> = events.value

        override suspend fun insert(event: DoseEvent): InsertResult {
            insertCalls += 1
            insertError?.let { throw it }
            insertGate?.await()
            if (insertResult == InsertResult.Inserted || insertResult == InsertResult.Idempotent) {
                stored[event.id] = event
                events.value = stored.values.toList()
            }
            return insertResult
        }

        override suspend fun update(event: DoseEvent, expectedRevision: Long): UpdateResult {
            updated += event to expectedRevision
            if (updateResult == UpdateResult.Updated || updateResult == UpdateResult.NoChange) {
                stored[event.id] = event.copy(revision = expectedRevision + 1)
                events.value = stored.values.toList()
            }
            return updateResult
        }

        override suspend fun delete(id: UUID): DeleteResult {
            deleteCalls += 1
            if (deleteResult == DeleteResult.Deleted) {
                stored.remove(id)
                events.value = stored.values.toList()
            }
            return deleteResult
        }

        override suspend fun deleteAll(): DeleteResult = deleteResult
    }

    private class FakeMedicationPlanRepository : MedicationPlanRepository {
        private val plans = MutableStateFlow<List<MedicationPlan>>(emptyList())

        override fun observeAll(): Flow<List<MedicationPlan>> = plans
        override fun observeEnabled(): Flow<List<MedicationPlan>> = plans
        override suspend fun getById(id: UUID): MedicationPlan? = null
        override suspend fun save(plan: MedicationPlan): PlanSaveResult = PlanSaveResult.Invalid
        override suspend fun setEnabled(id: UUID, enabled: Boolean): PlanUpdateResult =
            PlanUpdateResult.Invalid
        override suspend fun delete(id: UUID): DeleteResult = DeleteResult.NotFound
        override suspend fun deleteAll(): DeleteResult = DeleteResult.NotFound
    }

    private companion object {
        val EVENT_ID: UUID = UUID(0L, 701L)
        val SLOT_ID: UUID = UUID(0L, 702L)
        val NOW: Instant = Instant.parse("2026-01-02T03:04:05.678Z")
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
