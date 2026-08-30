@file:Suppress("ApplySharedPref", "UseKtx")

package io.github.yingqiu0871.evolune.application

import android.content.Context
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.data.repository.RepositoryStorageException
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommandCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmMessageCode
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultCodec
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

internal class WearAppConfirmationHandler(
    private val context: Context?,
    private val medicationPlans: MedicationPlanRepository,
    private val doseEvents: DoseEventRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val producerIdentity: () -> WearAppProducerIdentity = {
        io.github.yingqiu0871.evolune.wear.WearAppProducerIdentityStore.current(
            requireNotNull(context)
        )
    },
    private val latestSnapshotRevision: () -> Long = {
        context?.let {
            io.github.yingqiu0871.evolune.wear.WearAppSnapshotRevisionStore.current(it)
        } ?: Long.MAX_VALUE
    },
    operationJournal: WearAppConfirmationOperationJournal? = null
) {
    private val operationStore = operationJournal
        ?: WearAppConfirmationOperationStore(requireNotNull(context))
    suspend fun handle(command: WearAppConfirmCommand): WearAppConfirmResult =
        try {
            operationMutex.withLock {
                OccurrenceConfirmationCoordinator.withLock {
                    handleLocked(command)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: RepositoryStorageException) {
            retryable(command)
        } catch (_: Throwable) {
            retryable(command)
        }

    private suspend fun handleLocked(command: WearAppConfirmCommand): WearAppConfirmResult {
        if (!WearAppConfirmCommandRulesCompat.isValid(command)) {
            return invalid(command)
        }
        val fingerprint = encodedCommand(command)
        val previous = operationStore.read(command.operationId)
        if (previous != null && previous.fingerprint != fingerprint) {
            return conflict(command)
        }
        previous?.result?.let { return it }
        if (previous == null && !operationStore.begin(command.operationId, fingerprint)) {
            return retryable(command)
        }

        val currentProducer = producerIdentity()
        if (
            command.sourceSnapshot.producerInstanceId != currentProducer.producerInstanceId ||
            command.sourceSnapshot.producerGeneration != currentProducer.producerGeneration ||
            command.sourceSnapshot.snapshotRevision > latestSnapshotRevision()
        ) {
            return terminal(
                command,
                result(
                    command = command,
                    type = WearAppConfirmResultType.REJECTED_STALE_IDENTITY,
                    messageCode = WearAppConfirmMessageCode.STALE_IDENTITY
                )
            )
        }

        val plan = medicationPlans.getById(command.planId)
            ?: return terminal(
                command,
                result(
                    command,
                    WearAppConfirmResultType.REJECTED_OCCURRENCE_NOT_FOUND,
                    WearAppConfirmMessageCode.OCCURRENCE_NOT_FOUND
                )
            )
        if (!plan.isEnabled) {
            return terminal(
                command,
                result(
                    command,
                    WearAppConfirmResultType.REJECTED_PLAN_DISABLED,
                    WearAppConfirmMessageCode.PLAN_DISABLED
                )
            )
        }

        val occurrence = regenerateExactOccurrence(plan, command)
            ?: return terminal(
                command,
                result(
                    command,
                    WearAppConfirmResultType.REJECTED_OCCURRENCE_NOT_FOUND,
                    WearAppConfirmMessageCode.OCCURRENCE_NOT_FOUND
                )
            )
        val currentPlans = medicationPlans.observeEnabled().first()
        val presentationOccurrences = regeneratePresentationOccurrences(currentPlans, command)
        val events = doseEvents.observeAll().first()
        val existing = findPresentedEventForOccurrence(
            occurrence,
            presentationOccurrences,
            events,
            clock.instant()
        )
        if (existing != null) {
            return terminal(
                command,
                result(
                    command = command,
                    type = WearAppConfirmResultType.ALREADY_CONFIRMED,
                    eventId = existing.id,
                    messageCode = WearAppConfirmMessageCode.ALREADY_CONFIRMED,
                    snapshotRefreshExpected = true
                )
            )
        }

        val eventId = wearAppConfirmationEventId(command.operationId)
        val occurredAt = clock.instant()
        return when (
            val record = WearActionRecorder(medicationPlans, doseEvents).record(
                planId = plan.id,
                actionId = eventId,
                recordedAt = occurredAt
            ) { currentPlan ->
                createConfirmationEvent(currentPlan, command, eventId, occurredAt, zoneId())
            }
        ) {
            is RecordDoseEventActionResult.Accepted -> {
                val verifiedEvents = doseEvents.observeAll().first()
                val verified = findPresentedEventForOccurrence(
                    occurrence,
                    presentationOccurrences,
                    verifiedEvents,
                    clock.instant()
                )
                if (verified?.id == eventId && record.acceptance == RecordAcceptance.Inserted) {
                    terminal(
                        command,
                        result(
                            command = command,
                            type = WearAppConfirmResultType.CONFIRMED,
                            eventId = verified.id,
                            messageCode = WearAppConfirmMessageCode.CONFIRMED,
                            snapshotRefreshExpected = true
                        )
                    )
                } else if (verified != null) {
                    terminal(
                        command,
                        result(
                            command = command,
                            type = WearAppConfirmResultType.ALREADY_CONFIRMED,
                            eventId = verified.id,
                            messageCode = WearAppConfirmMessageCode.ALREADY_CONFIRMED,
                            snapshotRefreshExpected = true
                        )
                    )
                } else {
                    retryable(command)
                }
            }
            RecordDoseEventActionResult.Conflict -> {
                val verifiedEvents = doseEvents.observeAll().first()
                val verified = findPresentedEventForOccurrence(
                    occurrence,
                    presentationOccurrences,
                    verifiedEvents,
                    clock.instant()
                )
                if (verified != null) {
                    terminal(
                        command,
                        result(
                            command = command,
                            type = WearAppConfirmResultType.ALREADY_CONFIRMED,
                            eventId = verified.id,
                            messageCode = WearAppConfirmMessageCode.ALREADY_CONFIRMED,
                            snapshotRefreshExpected = true
                        )
                    )
                } else {
                    terminal(
                        command,
                        result(
                            command,
                            WearAppConfirmResultType.REJECTED_CONFLICT,
                            WearAppConfirmMessageCode.CONFLICT
                        )
                    )
                }
            }
            RecordDoseEventActionResult.PlanNotFound -> terminal(
                command,
                result(
                    command,
                    WearAppConfirmResultType.REJECTED_OCCURRENCE_NOT_FOUND,
                    WearAppConfirmMessageCode.OCCURRENCE_NOT_FOUND
                )
            )
            RecordDoseEventActionResult.PlanDisabled -> terminal(
                command,
                result(
                    command,
                    WearAppConfirmResultType.REJECTED_PLAN_DISABLED,
                    WearAppConfirmMessageCode.PLAN_DISABLED
                )
            )
            RecordDoseEventActionResult.Invalid -> terminal(
                command,
                result(
                    command,
                    WearAppConfirmResultType.REJECTED_INVALID,
                    WearAppConfirmMessageCode.INVALID_COMMAND
                )
            )
            RecordDoseEventActionResult.StorageFailure,
            RecordDoseEventActionResult.UnexpectedFailure -> retryable(command)
        }
    }

    private fun regenerateExactOccurrence(
        plan: MedicationPlan,
        command: WearAppConfirmCommand
    ): MedicationOccurrence? {
        val slot = plan.slots.singleOrNull { it.id == command.slotId && it.planId == plan.id }
            ?: return null
        val zone = zoneId()
        val start = runCatching { command.localDate.atStartOfDay(zone).toInstant() }
            .getOrNull() ?: return null
        val end = runCatching { command.localDate.plusDays(1L).atStartOfDay(zone).toInstant() }
            .getOrNull() ?: return null
        val generated = MedicationOccurrenceGenerator.generate(
            schedules = listOf(plan.toMedicationSchedule()),
            window = OccurrenceGenerationWindow(start, end),
            zoneId = zone
        )
        return generated.singleOrNull { occurrence ->
            occurrence.id.value == command.occurrenceId &&
                occurrence.planId == command.planId &&
                occurrence.slotId == slot.id &&
                occurrence.scheduledLocalDateTime.toLocalDate() == command.localDate &&
                occurrence.scheduledAt == command.scheduledAt
        }
    }

    private fun regeneratePresentationOccurrences(
        plans: List<MedicationPlan>,
        command: WearAppConfirmCommand
    ): List<MedicationOccurrence> {
        val zone = zoneId()
        val window = runCatching {
            OccurrenceGenerationWindow(
                startInclusive = command.localDate.minusDays(1L).atStartOfDay(zone).toInstant(),
                endExclusive = command.localDate.plusDays(2L).atStartOfDay(zone).toInstant()
            )
        }.getOrNull() ?: return emptyList()
        return MedicationOccurrenceGenerator.generate(
            schedules = plans.map(MedicationPlan::toMedicationSchedule),
            window = window,
            zoneId = zone
        )
    }

    private fun createConfirmationEvent(
        plan: MedicationPlan,
        command: WearAppConfirmCommand,
        eventId: UUID,
        occurredAt: Instant,
        zoneId: ZoneId
    ): DoseEvent = DoseEvent(
        id = eventId,
        route = plan.route,
        occurredAt = occurredAt,
        zoneId = zoneId,
        localDate = command.localDate,
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        slotId = command.slotId,
        source = DoseEventSource.WEAR,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )

    private fun terminal(
        command: WearAppConfirmCommand,
        result: WearAppConfirmResult
    ): WearAppConfirmResult = if (operationStore.saveResult(
            command.operationId,
            encodedCommand(command),
            result
        )
    ) {
        result
    } else {
        retryable(command)
    }

    private fun result(
        command: WearAppConfirmCommand,
        type: WearAppConfirmResultType,
        messageCode: WearAppConfirmMessageCode,
        eventId: UUID? = null,
        snapshotRefreshExpected: Boolean = false
    ): WearAppConfirmResult = WearAppConfirmResult(
        protocolVersion = io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol.PROTOCOL_VERSION,
        operationId = command.operationId,
        resultType = type,
        eventId = eventId,
        occurrenceId = command.occurrenceId,
        processedAt = clock.instant().takeIf { it.toEpochMilli() > 0L }
            ?: Instant.ofEpochMilli(1L),
        messageCode = messageCode,
        snapshotRefreshExpected = snapshotRefreshExpected
    ).also { check(WearAppConfirmResultRules.isValid(it)) }

    private fun invalid(command: WearAppConfirmCommand): WearAppConfirmResult = result(
        command,
        WearAppConfirmResultType.REJECTED_INVALID,
        WearAppConfirmMessageCode.INVALID_COMMAND
    )

    private fun conflict(command: WearAppConfirmCommand): WearAppConfirmResult = result(
        command,
        WearAppConfirmResultType.REJECTED_CONFLICT,
        WearAppConfirmMessageCode.CONFLICT
    )

    private fun retryable(command: WearAppConfirmCommand): WearAppConfirmResult = result(
        command,
        WearAppConfirmResultType.RETRYABLE_STORAGE_FAILURE,
        WearAppConfirmMessageCode.STORAGE_FAILURE
    )

    private fun encodedCommand(command: WearAppConfirmCommand): String =
        Base64.getEncoder().encodeToString(WearAppConfirmCommandCodec.encode(command))

    private object WearAppConfirmCommandRulesCompat {
        fun isValid(command: WearAppConfirmCommand): Boolean =
            io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommandRules
                .isValid(command)
    }

    private companion object {
        val operationMutex = Mutex()
    }
}

internal fun wearAppConfirmationEventId(operationId: UUID): UUID = UUID.nameUUIDFromBytes(
    "wear-app-confirm-occurrence:v1:$operationId".toByteArray(StandardCharsets.UTF_8)
)

internal data class WearAppStoredConfirmation(
    val fingerprint: String,
    val result: WearAppConfirmResult?
)

internal interface WearAppConfirmationOperationJournal {
    fun read(operationId: UUID): WearAppStoredConfirmation?
    fun begin(operationId: UUID, fingerprint: String): Boolean
    fun saveResult(operationId: UUID, fingerprint: String, result: WearAppConfirmResult): Boolean
}

internal class WearAppConfirmationOperationStore(context: Context) :
    WearAppConfirmationOperationJournal {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun read(operationId: UUID): WearAppStoredConfirmation? {
        val fingerprint = preferences.getString(key(operationId, FINGERPRINT), null)
            ?: return null
        val result = preferences.getString(key(operationId, RESULT), null)?.let { encoded ->
            runCatching {
                WearAppConfirmResultCodec.decode(Base64.getDecoder().decode(encoded))
            }.getOrNull()
        }
        return WearAppStoredConfirmation(fingerprint, result)
    }

    @Synchronized
    override fun begin(operationId: UUID, fingerprint: String): Boolean {
        val existing = preferences.getString(key(operationId, FINGERPRINT), null)
        if (existing != null) return existing == fingerprint
        return preferences.edit()
            .putString(key(operationId, FINGERPRINT), fingerprint)
            .commit()
    }

    @Synchronized
    override fun saveResult(
        operationId: UUID,
        fingerprint: String,
        result: WearAppConfirmResult
    ): Boolean {
        if (!WearAppConfirmResultRules.isValid(result)) return false
        val existing = preferences.getString(key(operationId, FINGERPRINT), null)
        if (existing != null && existing != fingerprint) return false
        return preferences.edit()
            .putString(key(operationId, FINGERPRINT), fingerprint)
            .putString(
                key(operationId, RESULT),
                Base64.getEncoder().encodeToString(WearAppConfirmResultCodec.encode(result))
            )
            .commit()
    }

    private fun key(operationId: UUID, suffix: String): String =
        "$KEY_PREFIX$operationId.$suffix"

    private companion object {
        const val PREFERENCES_NAME = "wear_app_confirmation_operations"
        const val KEY_PREFIX = "operation."
        const val FINGERPRINT = "fingerprint"
        const val RESULT = "result"
    }
}
