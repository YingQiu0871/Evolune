package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import io.github.yingqiu0871.evolune.application.DoseEventEditSessionFactory
import io.github.yingqiu0871.evolune.application.DoseEventEditorInput
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.ConditionalDeleteResult
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
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.ui.components.MedicationRecordBottomSheet
import io.github.yingqiu0871.evolune.ui.components.MedicationRecordItem
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.DoseEventOperationError
import io.github.yingqiu0871.evolune.viewmodel.DoseEventOperationState
import io.github.yingqiu0871.evolune.viewmodel.DoseEventUiEvent
import io.github.yingqiu0871.evolune.viewmodel.HRTViewModel
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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

    private fun readImeState(): String {
        return InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys input_method")
            .use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    .use { it.readBytes().toString(Charsets.UTF_8) }
            }
    }

    private fun imeShownState(): String? = readImeState()
        .lineSequence()
        .map(String::trim)
        .filter { it.startsWith("mInputShown=") }
        .lastOrNull()

    private fun imeIsVisible(): Boolean = imeShownState() == "mInputShown=true"

    private fun imeIsHidden(): Boolean = imeShownState() == "mInputShown=false"

    private fun ensureImeHidden() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        if (imeIsVisible()) {
            automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        }
        composeRule.waitUntil(5_000L) { imeIsHidden() }
        composeRule.waitForIdle()
    }

    @After
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ensureImeHidden()
        automation.executeShellCommand("ime reset").close()
        composeRule.waitUntil(5_000L) { imeIsHidden() }
        composeRule.waitForIdle()
    }

    @Test
    fun createSuccessClosesEditorAfterContractInsert() {
        val repository = FakeDoseEventRepository()
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)

        composeRule.onNodeWithTag("record-dose").performClick().performTextInput("2")
        ensureImeHidden()
        val saveButton = composeRule.onNodeWithTag("record-save").performScrollTo()
        composeRule.waitUntil(5_000L) {
            runCatching {
                saveButton.assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("record-save").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("record-save").assertIsEnabled()
        composeRule.onNodeWithTag("record-save").performTouchInput { click() }
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }

        assertEquals(1, repository.insertCalls)
        assertTrue(composeRule.onAllNodesWithTag("record-dose").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun dismissingImeKeepsEditorOpenAndPreservesDraftThenSecondBackCloses() {
        val repository = FakeDoseEventRepository()
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)
        val session = requireNotNull(viewModel.editSession.value)

        composeRule.onNodeWithTag("record-dose").performClick().performTextInput("2")

        composeRule.waitUntil(5_000L) { imeIsVisible() }
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        composeRule.waitForIdle()

        composeRule.waitUntil(5_000L) { imeIsHidden() }
        composeRule.onNodeWithTag("record-editor-surface").assertExists()
        assertEquals(
            "2",
            composeRule.onNodeWithTag("record-dose")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
        assertSame(session, viewModel.editSession.value)

        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }
        assertTrue(composeRule.onAllNodesWithTag("record-editor-surface").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun operationInProgressConsumesBackInsteadOfClosingRecordEditor() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeDoseEventRepository().apply { insertGate = gate }
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)
        val session = requireNotNull(viewModel.editSession.value)

        composeRule.onNodeWithTag("record-dose").performClick().performTextInput("2")
        composeRule.onNodeWithTag("record-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is DoseEventOperationState.Running
        }

        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        composeRule.waitForIdle()

        assertSame(session, viewModel.editSession.value)
        composeRule.onNodeWithTag("record-editor-surface").assertExists()

        gate.complete(Unit)
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }
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
    fun editActionsHaveEqualStableSizes() {
        val original = event()
        val repository = FakeDoseEventRepository().apply {
            stored[EVENT_ID] = original
        }
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle { viewModel.startEditSession(original) }

        composeRule.onNodeWithTag("record-save").performScrollTo()
        composeRule.waitForIdle()
        val deleteBounds = composeRule.onNodeWithTag("record-delete").fetchSemanticsNode().boundsInRoot
        val cancelBounds = composeRule.onNodeWithTag("record-cancel").fetchSemanticsNode().boundsInRoot
        val saveBounds = composeRule.onNodeWithTag("record-save").fetchSemanticsNode().boundsInRoot

        assertBoundsSizeEqual(deleteBounds, cancelBounds)
        assertBoundsSizeEqual(deleteBounds, saveBounds)
    }

    @Test
    fun rapidDoubleTapInvokesOneInsert() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeDoseEventRepository().apply { insertGate = gate }
        val viewModel = viewModel(repository)
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)
        composeRule.onNodeWithTag("record-dose").performClick().performTextInput("2")

        ensureImeHidden()
        val saveButton = composeRule.onNodeWithTag("record-save").performScrollTo()
        composeRule.waitUntil(5_000L) {
            runCatching {
                saveButton.assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("record-save").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("record-save").assertIsEnabled()
        composeRule.onNodeWithTag("record-save").performTouchInput { click() }
        composeRule.waitUntil(5_000L) {
            viewModel.operationState.value is DoseEventOperationState.Running
        }
        assertEquals(1, repository.insertCalls)
        saveButton.assertIsNotEnabled()
        saveButton.performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(1, repository.insertCalls)

        gate.complete(Unit)
        composeRule.waitUntil(5_000L) { viewModel.editSession.value == null }
        assertEquals(1, repository.insertCalls)
    }

    @Test
    fun cancelledRecordCardGestureDoesNotInvokeItsClickCallback() {
        var clickCount = 0
        composeRule.setContent {
            EvoluneTheme {
                MedicationRecordItem(
                    medicationName = "test",
                    route = Route.INJECTION,
                    doseMG = 2.0,
                    timeH = 0.0,
                    modifier = Modifier.testTag("record-card"),
                    onClick = { clickCount++ }
                )
            }
        }

        composeRule.onNodeWithTag("record-card").performTouchInput {
            down(center)
            moveTo(Offset(-1f, center.y))
            up()
        }
        assertEquals(0, clickCount)
        composeRule.onNodeWithTag("record-card").performClick()
        assertEquals(1, clickCount)
    }

    @Test
    fun doseLabelsAndFieldsKeepRelativeGeometryAcrossFocusChanges() {
        val viewModel = viewModel(FakeDoseEventRepository())
        setScreen(viewModel)
        composeRule.runOnIdle(viewModel::startCreateSession)

        val rawLabel = composeRule.onNodeWithTag("record-dose-label")
        val equivalentLabel = composeRule.onNodeWithTag("record-e2-dose-label")
        val rawField = composeRule.onNodeWithTag("record-dose").performScrollTo()
        val equivalentField = composeRule.onNodeWithTag("record-e2-dose")
        composeRule.waitForIdle()
        ensureImeHidden()

        val initialRawLabelBounds = rawLabel.fetchSemanticsNode().boundsInRoot
        val initialEquivalentLabelBounds = equivalentLabel.fetchSemanticsNode().boundsInRoot
        val initialRawFieldBounds = rawField.fetchSemanticsNode().boundsInRoot
        val initialEquivalentFieldBounds = equivalentField.fetchSemanticsNode().boundsInRoot

        rawField.performClick()
        composeRule.waitForIdle()
        assertBoundsEqual(initialRawLabelBounds, rawLabel.fetchSemanticsNode().boundsInRoot)
        assertBoundsEqual(
            initialEquivalentLabelBounds,
            equivalentLabel.fetchSemanticsNode().boundsInRoot
        )
        assertBoundsEqual(initialRawFieldBounds, rawField.fetchSemanticsNode().boundsInRoot)
        assertBoundsEqual(
            initialEquivalentFieldBounds,
            equivalentField.fetchSemanticsNode().boundsInRoot
        )

        equivalentField.performClick()
        composeRule.waitForIdle()
        val afterRawLabelBounds = rawLabel.fetchSemanticsNode().boundsInRoot
        val afterEquivalentLabelBounds = equivalentLabel.fetchSemanticsNode().boundsInRoot
        val afterRawFieldBounds = rawField.fetchSemanticsNode().boundsInRoot
        val afterEquivalentFieldBounds = equivalentField.fetchSemanticsNode().boundsInRoot
        val translationX = afterRawLabelBounds.left - initialRawLabelBounds.left
        val translationY = afterRawLabelBounds.top - initialRawLabelBounds.top
        assertBoundsTranslated(initialRawLabelBounds, afterRawLabelBounds, translationX, translationY)
        assertBoundsTranslated(
            initialEquivalentLabelBounds,
            afterEquivalentLabelBounds,
            translationX,
            translationY
        )
        assertBoundsTranslated(initialRawFieldBounds, afterRawFieldBounds, translationX, translationY)
        assertBoundsTranslated(
            initialEquivalentFieldBounds,
            afterEquivalentFieldBounds,
            translationX,
            translationY
        )
    }

    private fun assertBoundsTranslated(
        expected: androidx.compose.ui.geometry.Rect,
        actual: androidx.compose.ui.geometry.Rect,
        translationX: Float,
        translationY: Float
    ) {
        assertEquals((expected.left + translationX).toDouble(), actual.left.toDouble(), 0.5)
        assertEquals((expected.top + translationY).toDouble(), actual.top.toDouble(), 0.5)
        assertEquals((expected.right + translationX).toDouble(), actual.right.toDouble(), 0.5)
        assertEquals((expected.bottom + translationY).toDouble(), actual.bottom.toDouble(), 0.5)
    }

    private fun assertBoundsEqual(
        expected: androidx.compose.ui.geometry.Rect,
        actual: androidx.compose.ui.geometry.Rect
    ) {
        assertEquals(expected.left.toDouble(), actual.left.toDouble(), 0.5)
        assertEquals(expected.top.toDouble(), actual.top.toDouble(), 0.5)
        assertEquals(expected.right.toDouble(), actual.right.toDouble(), 0.5)
        assertEquals(expected.bottom.toDouble(), actual.bottom.toDouble(), 0.5)
    }

    private fun assertBoundsSizeEqual(
        expected: androidx.compose.ui.geometry.Rect,
        actual: androidx.compose.ui.geometry.Rect
    ) {
        assertEquals(expected.width.toDouble(), actual.width.toDouble(), 1.1)
        assertEquals(expected.height.toDouble(), actual.height.toDouble(), 0.5)
    }

    private fun setScreen(viewModel: HRTViewModel) {
        composeRule.setContent {
            EvoluneTheme {
                LaunchedEffect(viewModel) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is DoseEventUiEvent.Saved,
                            is DoseEventUiEvent.Deleted -> viewModel.closeEditSession()
                        }
                        viewModel.acknowledgeOperation()
                    }
                }
                val editSession by viewModel.editSession.collectAsState()
                val operationState by viewModel.operationState.collectAsState()
                MedicationRecordBottomSheet(
                    showBottomSheet = editSession != null,
                    onDismiss = {
                        viewModel.closeEditSession()
                        viewModel.acknowledgeOperation()
                    },
                    onSave = viewModel::saveEvent,
                    onDelete = viewModel::deleteEvent,
                    session = editSession,
                    is24Hour = true,
                    isOperationRunning = operationState is DoseEventOperationState.Running,
                    operationError = (operationState as? DoseEventOperationState.Failure)
                        ?.error
                        ?.probeDisplayMessage()
                )
            }
        }
    }

    private fun viewModel(repository: FakeDoseEventRepository): HRTViewModel {        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
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
        @Volatile
        var insertCalls = 0
        @Volatile
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

        override suspend fun deleteIfRevisionMatches(
            id: UUID,
            expectedRevision: Long
        ): ConditionalDeleteResult {
            val event = stored[id] ?: return ConditionalDeleteResult.NotFound
            if (event.revision != expectedRevision) return ConditionalDeleteResult.RevisionConflict
            return if (delete(id) == DeleteResult.Deleted) {
                ConditionalDeleteResult.Deleted
            } else {
                ConditionalDeleteResult.NotFound
            }
        }

        override suspend fun deleteLatestRecordedIfRevisionMatches(
            eventId: UUID,
            eventRevision: Long
        ): LatestDoseDeleteResult = LatestDoseDeleteResult.EventNotFound

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

private fun DoseEventOperationError.probeDisplayMessage(): String = when (this) {
    is DoseEventOperationError.InvalidInput -> "请检查记录输入"
    DoseEventOperationError.RepositoryInvalid -> "记录无法保存"
    DoseEventOperationError.Conflict -> "相同记录 ID 已存在不同内容"
    DoseEventOperationError.RevisionConflict -> "该记录已被其他操作修改"
    DoseEventOperationError.NotFound -> "该记录已不存在"
    DoseEventOperationError.StorageFailure -> "记录存储暂时不可用"
}
