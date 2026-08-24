package io.github.yingqiu0871.evolune.backup

import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationOperationResult
import io.github.yingqiu0871.evolune.backup.cloud.AuthorizationResolution
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationGateway
import io.github.yingqiu0871.evolune.backup.cloud.CloudAuthorizationOutcome
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupGeneration
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupId
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupProvider
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupResult
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupUploadMetadata
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupUploadResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreCoordinatorTest {
    @Test
    fun `creating the view model does not perform cloud work`() {
        val fixture = Fixture()

        BackupRestoreViewModel(fixture.coordinator)

        assertEquals(0, fixture.authorization.authorizeCalls)
        assertEquals(0, fixture.provider.listCalls)
        assertEquals(0, fixture.provider.uploadCalls)
        assertEquals(0, fixture.provider.downloadCalls)
    }

    @Test
    fun `view model ignores a second backup submission while busy`() = runBlocking {
        val fixture = Fixture()
        val uploadEntered = CompletableDeferred<Unit>()
        val releaseUpload = CompletableDeferred<Unit>()
        fixture.provider.beforeUpload = {
            uploadEntered.complete(Unit)
            releaseUpload.await()
        }
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val viewModel = BackupRestoreViewModel(fixture.coordinator, scope)
            viewModel.backUpNow()
            assertEquals(
                BackupRestoreUiState.AwaitingBackupPassphrase,
                viewModel.uiState.value
            )

            viewModel.submitBackupPassphrase("first".toCharArray(), "first".toCharArray())
            uploadEntered.await()
            assertEquals(BackupRestoreUiState.PreparingBackup, viewModel.uiState.value)

            viewModel.submitBackupPassphrase("second".toCharArray(), "second".toCharArray())
            assertEquals(1, fixture.provider.uploadCalls)

            releaseUpload.complete(Unit)
            assertTrue(viewModel.uiState.value is BackupRestoreUiState.BackupSuccess)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancel interactive operation clears authorization and pending restore state`() = runBlocking {
        val fixture = Fixture()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val viewModel = BackupRestoreViewModel(fixture.coordinator, scope)
            fixture.authorization.outcome = CloudAuthorizationOutcome.UserResolutionRequired(
                object : AuthorizationResolution {}
            )
            viewModel.backUpNow()
            assertEquals(
                BackupRestoreUiState.Authorizing(BackupRestoreOperation.BACKUP),
                viewModel.uiState.value
            )
            viewModel.cancelInteractiveOperation()
            assertEquals(BackupRestoreUiState.Idle, viewModel.uiState.value)

            fixture.authorization.outcome = CloudAuthorizationOutcome.Authorized("token")
            fixture.provider.uploadedBytes = fixture.encodedBackup()
            fixture.provider.generations = listOf(fixture.provider.generation)
            viewModel.restoreFromBackup()
            viewModel.selectGeneration(fixture.provider.generation)
            viewModel.submitRestorePassphrase("secret".toCharArray())
            assertTrue(viewModel.uiState.value is BackupRestoreUiState.Preview)

            viewModel.cancelInteractiveOperation()
            viewModel.confirmRestore()
            assertEquals(BackupRestoreUiState.Idle, viewModel.uiState.value)
            assertEquals(0, fixture.persistence.replaceRoomCalls)
            assertEquals(0, fixture.persistence.replaceSettingsCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `backup uses authoritative snapshot real B1 codec and verified B3 result`() = runBlocking {
        val fixture = Fixture()
        assertTrue(
            fixture.coordinator.authorizeFor(BackupRestoreOperation.BACKUP) is
                AuthorizationGateResult.Authorized
        )
        val capture = fixture.persistenceSnapshot().capture()
        assertTrue(capture.toString(), capture is SnapshotCaptureResult.Success)

        val result = fixture.coordinator.createBackup(
            "correct horse battery staple".toCharArray(),
            "correct horse battery staple".toCharArray()
        )

        assertTrue(result.toString(), result is BackupCreationResult.Success)
        assertEquals(1, fixture.provider.uploadCalls)
        assertNotNull(fixture.provider.uploadedBytes)
        assertFalse(
            String(fixture.provider.uploadedBytes!!, Charsets.UTF_8)
                .contains("medicationPlans")
        )
    }

    @Test
    fun `representative large history completes backup and restore preview`() = runBlocking {
        val fixture = Fixture()
        val template = samplePayload().doseEvents.single()
        val largeEvents = (10..2_009).map { index ->
            template.copy(
                id = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"
            )
        }
        val payload = samplePayload().copy(doseEvents = largeEvents)
        fixture.persistence.room = RestoreRoomState.fromPayload(payload)
        fixture.persistence.settings = payload.settings

        val backup = fixture.coordinator.createBackup(
            "large-history-secret".toCharArray(),
            "large-history-secret".toCharArray()
        )

        assertTrue(backup is BackupCreationResult.Success)
        assertTrue(fixture.provider.uploadedBytes!!.size > 100_000)

        val preview = fixture.coordinator.prepareRestore(
            fixture.provider.generation,
            "large-history-secret".toCharArray()
        ) as RestorePreparationResult.Success

        assertEquals(2_000, preview.preview.doseEventCount)
        assertEquals(0, fixture.persistence.replaceRoomCalls)
        assertEquals(0, fixture.persistence.replaceSettingsCalls)
    }

    @Test
    fun `passphrase mismatch does not snapshot or upload`() = runBlocking {
        val fixture = Fixture()

        val result = fixture.coordinator.createBackup(
            "one".toCharArray(),
            "two".toCharArray()
        )

        assertEquals(BackupCreationResult.InvalidPassphrase, result)
        assertEquals(0, fixture.persistence.roomReads)
        assertEquals(0, fixture.provider.uploadCalls)
    }

    @Test
    fun `invalid local data stops before upload`() = runBlocking {
        val fixture = Fixture()
        fixture.persistence.settings = fixture.persistence.settings.copy(bodyWeightKg = 0.0)

        val result = fixture.coordinator.createBackup(
            "secret".toCharArray(),
            "secret".toCharArray()
        )

        assertEquals(BackupCreationResult.InvalidLocalData, result)
        assertEquals(0, fixture.provider.uploadCalls)
    }

    @Test
    fun `restore decrypts and previews before B2 mutation`() = runBlocking {
        val fixture = Fixture()
        fixture.provider.uploadedBytes = fixture.encodedBackup()
        fixture.provider.generations = listOf(fixture.provider.generation)

        val prepared = fixture.coordinator.prepareRestore(
            fixture.provider.generation,
            "secret".toCharArray()
        )

        assertTrue(prepared is RestorePreparationResult.Success)
        val preparedSuccess = prepared as RestorePreparationResult.Success
        assertEquals(
            "test",
            preparedSuccess.preview.producerAppVersionName
        )
        assertEquals(0, fixture.persistence.replaceRoomCalls)
        assertEquals(0, fixture.persistence.replaceSettingsCalls)

        val success = fixture.coordinator.confirmRestore(
            preparedSuccess.prepared
        )

        assertEquals(RestoreCompletionResult.Success, success)
        assertEquals(1, fixture.persistence.replaceRoomCalls)
        assertEquals(1, fixture.persistence.replaceSettingsCalls)
        assertEquals(1, fixture.refreshCalls)
    }

    @Test
    fun `cancelling restore preview leaves B2 untouched`() = runBlocking {
        val fixture = Fixture()
        fixture.provider.uploadedBytes = fixture.encodedBackup()

        val preview = fixture.coordinator.prepareRestore(
            fixture.provider.generation,
            "secret".toCharArray()
        )

        assertTrue(preview is RestorePreparationResult.Success)
        assertEquals(0, fixture.persistence.replaceRoomCalls)
        assertEquals(0, fixture.persistence.replaceSettingsCalls)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test
    fun `wrong passphrase never reaches B2`() = runBlocking {
        val fixture = Fixture()
        fixture.provider.uploadedBytes = fixture.encodedBackup()

        val result = fixture.coordinator.prepareRestore(
            fixture.provider.generation,
            "wrong".toCharArray()
        )

        assertTrue(result is RestorePreparationResult.Failure)
        assertEquals(
            BackupRestoreErrorCode.WRONG_SECRET_OR_TAMPERED,
            (result as RestorePreparationResult.Failure).error.code
        )
        assertEquals(0, fixture.persistence.replaceRoomCalls)
        assertEquals(0, fixture.persistence.replaceSettingsCalls)
    }

    @Test
    fun `corrupt download never reaches preview or B2`() = runBlocking {
        val fixture = Fixture()
        fixture.provider.uploadedBytes = byteArrayOf(1, 2, 3, 4)

        val result = fixture.coordinator.prepareRestore(
            fixture.provider.generation,
            "secret".toCharArray()
        )

        assertTrue(result is RestorePreparationResult.Failure)
        assertEquals(0, fixture.persistence.replaceRoomCalls)
        assertEquals(0, fixture.persistence.replaceSettingsCalls)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test
    fun `snapshot preserves dangling event slot and all backup settings`() = runBlocking {
        val fixture = Fixture()
        val expected = samplePayload().copy(
            doseEvents = samplePayload().doseEvents.map {
                it.copy(slotId = "00000000-0000-0000-0000-000000000099")
            },
            settings = BackupSettingsV1(
                bodyWeightKg = 63.5,
                themeMode = "DARK",
                colorTheme = "BUILTIN",
                autoCheckUpdates = false,
                timeFormat = "HOUR_12"
            )
        )
        fixture.persistence.room = RestoreRoomState.fromPayload(expected)
        fixture.persistence.settings = expected.settings

        val result = fixture.persistenceSnapshot().capture()

        assertTrue(result is SnapshotCaptureResult.Success)
        val actual = (result as SnapshotCaptureResult.Success).payload
        assertEquals(expected.medicationPlans, actual.medicationPlans)
        assertEquals(expected.scheduledDoseSlots, actual.scheduledDoseSlots)
        assertEquals(expected.doseEvents, actual.doseEvents)
        assertEquals(expected.settings, actual.settings)
    }

    @Test
    fun `concurrent backups serialize cloud critical section`() = runBlocking {
        val fixture = Fixture()
        val firstUploadEntered = CompletableDeferred<Unit>()
        val releaseFirstUpload = CompletableDeferred<Unit>()
        fixture.provider.beforeUpload = {
            if (fixture.provider.uploadCalls == 1) {
                firstUploadEntered.complete(Unit)
                releaseFirstUpload.await()
            }
        }

        val first = async {
            fixture.coordinator.createBackup("first".toCharArray(), "first".toCharArray())
        }
        firstUploadEntered.await()
        val second = async {
            fixture.coordinator.createBackup("second".toCharArray(), "second".toCharArray())
        }
        yield()

        assertEquals(1, fixture.provider.uploadCalls)
        assertFalse(second.isCompleted)

        releaseFirstUpload.complete(Unit)
        assertTrue(first.await() is BackupCreationResult.Success)
        assertTrue(second.await() is BackupCreationResult.Success)
        assertEquals(2, fixture.provider.uploadCalls)
    }

    @Test
    fun `B2 failure does not invoke post restore effects`() = runBlocking {
        val fixture = Fixture().apply { persistence.failReplaceRoom = true }
        fixture.provider.uploadedBytes = fixture.encodedBackup()
        val prepared = fixture.coordinator.prepareRestore(
            fixture.provider.generation,
            "secret".toCharArray()
        ) as RestorePreparationResult.Success

        val result = fixture.coordinator.confirmRestore(prepared.prepared)

        assertTrue(result is RestoreCompletionResult.Failure)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test
    fun `post restore failure is success with refresh warning`() = runBlocking {
        val fixture = Fixture().apply { refreshFails = true }
        fixture.provider.uploadedBytes = fixture.encodedBackup()
        val prepared = fixture.coordinator.prepareRestore(
            fixture.provider.generation,
            "secret".toCharArray()
        ) as RestorePreparationResult.Success

        val result = fixture.coordinator.confirmRestore(prepared.prepared)

        assertEquals(RestoreCompletionResult.SuccessWithRefreshWarning, result)
        assertEquals(1, fixture.persistence.replaceRoomCalls)
    }

    @Test
    fun `disconnect never deletes cloud backups`() = runBlocking {
        val fixture = Fixture()

        assertTrue(fixture.coordinator.disconnect())

        assertEquals(1, fixture.provider.disconnectCalls)
        assertEquals(0, fixture.provider.deleteCalls)
    }

    private class Fixture {
        val persistence = FakeRestorePersistence()
        val authorization = FakeAuthorization()
        val provider = FakeCloudProvider()
        var refreshCalls = 0
        var refreshFails = false
        private val postRestore = PostRestoreCoordinator(
            effects = listOf {
                refreshCalls++
                if (refreshFails) error("refresh failed")
            }
        )
        val coordinator = BackupRestoreCoordinator(
            snapshotSource = RestorePersistenceSnapshotSource(persistence),
            codec = EvoluneBackupCodec(),
            authorization = authorization,
            provider = provider,
            restoreTransaction = RestoreTransaction(persistence, FakeJournalStore()),
            postRestoreCoordinator = postRestore,
            producerAppVersionName = "test",
            producerAppVersionCode = 1
        )

        fun persistenceSnapshot(): LocalBackupSnapshotSource =
            RestorePersistenceSnapshotSource(persistence)

        fun encodedBackup(): ByteArray = when (
            val result = EvoluneBackupCodec().encode(
                payload = samplePayload(),
                passphrase = "secret".toCharArray(),
                metadata = BackupProducerMetadataV1(
                    createdAt = provider.generation.createdAt,
                    producerAppVersionName = "test",
                    producerAppVersionCode = 1
                )
            )
        ) {
            is BackupEncodeResult.Success -> result.bytes
            is BackupEncodeResult.Failure -> error(result.error)
        }
    }

    private class FakeAuthorization : CloudAuthorizationGateway {
        var authorizeCalls = 0
        var outcome: CloudAuthorizationOutcome = CloudAuthorizationOutcome.Authorized("token")
        override suspend fun authorize(): CloudAuthorizationOutcome {
            authorizeCalls++
            return outcome
        }

        override suspend fun clearToken(accessToken: String): AuthorizationOperationResult =
            AuthorizationOperationResult.Success

        override suspend fun disconnect(): AuthorizationOperationResult =
            AuthorizationOperationResult.Success
    }

    private class FakeCloudProvider : CloudBackupProvider {
        var uploadCalls = 0
        var downloadCalls = 0
        var listCalls = 0
        var deleteCalls = 0
        var disconnectCalls = 0
        var uploadedBytes: ByteArray? = null
        var beforeUpload: (suspend () -> Unit)? = null
        var generations: List<CloudBackupGeneration> = emptyList()
        val generation = CloudBackupGeneration(
            id = CloudBackupId("generation-1"),
            name = "evolune-backup-test.evbackup",
            createdAt = "2026-08-23T22:10:00Z",
            sizeBytes = null,
            contentSha256 = "0".repeat(64)
        )

        override suspend fun uploadBackup(
            bytes: ByteArray,
            metadata: CloudBackupUploadMetadata
        ): CloudBackupResult<CloudBackupUploadResult> {
            uploadCalls++
            beforeUpload?.invoke()
            uploadedBytes = bytes.copyOf()
            generations = listOf(generation)
            return CloudBackupResult.Success(
                CloudBackupUploadResult(generation, verified = true, retentionCleanupPending = false)
            )
        }

        override suspend fun listBackups(): CloudBackupResult<List<CloudBackupGeneration>> {
            listCalls++
            return CloudBackupResult.Success(generations)
        }

        override suspend fun downloadBackup(id: CloudBackupId): CloudBackupResult<ByteArray> {
            downloadCalls++
            return CloudBackupResult.Success(uploadedBytes?.copyOf() ?: error("missing bytes"))
        }

        override suspend fun deleteBackup(id: CloudBackupId): CloudBackupResult<Unit> {
            deleteCalls++
            return CloudBackupResult.Success(Unit)
        }

        override suspend fun disconnect(): CloudBackupResult<Unit> {
            disconnectCalls++
            return CloudBackupResult.Success(Unit)
        }
    }

    private class FakeRestorePersistence : RestorePersistence {
        var room = RestoreRoomState.fromPayload(samplePayload())
        var settings = samplePayload().settings
        var roomReads = 0
        var replaceRoomCalls = 0
        var replaceSettingsCalls = 0
        var failReplaceRoom = false

        override suspend fun readRoomState(): RestoreRoomState {
            roomReads++
            return room
        }

        override suspend fun replaceRoom(state: RestoreRoomState) {
            replaceRoomCalls++
            if (failReplaceRoom) error("room write failed")
            room = state
        }

        override suspend fun readSettings(): BackupSettingsV1 = settings

        override suspend fun replaceSettings(settings: BackupSettingsV1): Boolean {
            replaceSettingsCalls++
            this.settings = settings
            return true
        }
    }

    private class FakeJournalStore : RestoreJournalStore {
        var journal: RestoreJournal? = null
        override suspend fun read(): RestoreJournalReadResult =
            journal?.let(RestoreJournalReadResult::Found) ?: RestoreJournalReadResult.Missing

        override suspend fun write(journal: RestoreJournal) {
            this.journal = journal
        }

        override suspend fun delete() {
            journal = null
        }
    }

    companion object {
        private fun samplePayload() = EvoluneBackupPayloadV1(
            medicationPlans = listOf(
                BackupMedicationPlanV1(
                    id = "00000000-0000-0000-0000-000000000001",
                    name = "Plan",
                    route = "ORAL",
                    ester = "E2",
                    doseMG = 2.0,
                    scheduleType = "DAILY",
                    daysOfWeek = emptyList(),
                    intervalDays = 1,
                    isEnabled = true,
                    extras = emptyMap(),
                    createdAt = "2026-01-01T00:00:00Z"
                )
            ),
            scheduledDoseSlots = listOf(
                BackupScheduledDoseSlotV1(
                    id = "00000000-0000-0000-0000-000000000002",
                    planId = "00000000-0000-0000-0000-000000000001",
                    localTime = "08:00",
                    position = 0
                )
            ),
            doseEvents = listOf(
                BackupDoseEventV1(
                    id = "00000000-0000-0000-0000-000000000003",
                    route = "ORAL",
                    occurredAt = "2026-01-01T08:00:00Z",
                    zoneId = "UTC",
                    localDate = "2026-01-01",
                    doseMG = 2.0,
                    ester = "E2",
                    extras = emptyMap(),
                    slotId = "00000000-0000-0000-0000-000000000002",
                    source = "MANUAL",
                    status = "RECORDED",
                    revision = 1
                )
            ),
            settings = BackupSettingsV1(
                bodyWeightKg = 55.0,
                themeMode = "SYSTEM",
                colorTheme = "DYNAMIC",
                autoCheckUpdates = true,
                timeFormat = "SYSTEM"
            )
        )
    }
}
