package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.data.repository.RepositoryStorageException
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal data class WearDoseActionPayload(
    val dataItemUri: String,
    val planId: UUID?,
    val actionId: UUID?,
    val recordedAtMillis: Long?
)

internal sealed interface WearDoseActionOutcome {
    data class Accepted(
        val acceptance: RecordAcceptance,
        val event: DoseEvent,
        val dataItemDeleted: Boolean
    ) : WearDoseActionOutcome

    data object PlanNotFound : WearDoseActionOutcome
    data object PlanDisabled : WearDoseActionOutcome
    data object Conflict : WearDoseActionOutcome
    data object Invalid : WearDoseActionOutcome
    data object StorageFailure : WearDoseActionOutcome
    data object UnexpectedFailure : WearDoseActionOutcome
}

internal class WearDoseActionHandler(
    medicationPlans: MedicationPlanRepository,
    doseEvents: DoseEventRepository,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val acceptedSideEffect: suspend () -> Unit = {},
    private val deleteDataItem: suspend (String) -> Boolean
) {
    private val recorder = WearActionRecorder(medicationPlans, doseEvents)

    suspend fun handle(action: WearDoseActionPayload): WearDoseActionOutcome = try {
        val planId = action.planId ?: return WearDoseActionOutcome.Invalid
        val actionId = action.actionId ?: return WearDoseActionOutcome.Invalid
        val recordedAtMillis = action.recordedAtMillis
            ?.takeIf { it > 0L }
            ?: return WearDoseActionOutcome.Invalid
        val recordedAt = Instant.ofEpochMilli(recordedAtMillis)

        when (
            val result = recorder.record(
                planId = planId,
                actionId = actionId,
                recordedAt = recordedAt
            ) { plan ->
                createWearDoseEvent(
                    plan = plan,
                    actionId = actionId,
                    recordedAt = recordedAt,
                    zoneId = zoneId()
                )
            }
        ) {
            is RecordDoseEventActionResult.Accepted -> {
                val sideEffectCompleted = try {
                    acceptedSideEffect()
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                }
                if (!sideEffectCompleted) {
                    WearDoseActionOutcome.Accepted(
                        acceptance = result.acceptance,
                        event = result.event,
                        dataItemDeleted = false
                    )
                } else {
                    val deleted = try {
                        deleteDataItem(action.dataItemUri)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        false
                    }
                    WearDoseActionOutcome.Accepted(
                        acceptance = result.acceptance,
                        event = result.event,
                        dataItemDeleted = deleted
                    )
                }
            }
            RecordDoseEventActionResult.PlanNotFound -> WearDoseActionOutcome.PlanNotFound
            RecordDoseEventActionResult.PlanDisabled -> WearDoseActionOutcome.PlanDisabled
            RecordDoseEventActionResult.Conflict -> WearDoseActionOutcome.Conflict
            RecordDoseEventActionResult.Invalid -> WearDoseActionOutcome.Invalid
            RecordDoseEventActionResult.StorageFailure -> WearDoseActionOutcome.StorageFailure
            RecordDoseEventActionResult.UnexpectedFailure ->
                WearDoseActionOutcome.UnexpectedFailure
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RepositoryStorageException) {
        WearDoseActionOutcome.StorageFailure
    } catch (_: Throwable) {
        WearDoseActionOutcome.UnexpectedFailure
    }
}

internal fun parseWearDoseAction(
    dataItemUri: String,
    planId: String?,
    actionId: String?,
    recordedAtMillis: Long?
): WearDoseActionPayload {
    val uriActionId = actionIdFromDataItemUri(dataItemUri)
    val payloadActionId = actionId?.let(::parseUuid)
    return WearDoseActionPayload(
        dataItemUri = dataItemUri,
        planId = planId?.let(::parseUuid),
        actionId = payloadActionId?.takeIf { it == uriActionId },
        recordedAtMillis = recordedAtMillis?.takeIf { it > 0L }
    )
}

internal fun createWearDoseEvent(
    plan: MedicationPlan,
    actionId: UUID,
    recordedAt: Instant,
    zoneId: ZoneId
): DoseEvent = DoseEvent(
    id = actionId,
    route = plan.route,
    occurredAt = recordedAt,
    zoneId = zoneId,
    localDate = recordedAt.atZone(zoneId).toLocalDate(),
    doseMG = plan.doseMG,
    ester = plan.ester,
    extras = plan.extras,
    slotId = null,
    source = DoseEventSource.WEAR,
    status = DoseEventStatus.RECORDED,
    revision = 1L
)

private fun actionIdFromDataItemUri(dataItemUri: String): UUID? = runCatching {
    val path = URI(dataItemUri).path ?: return@runCatching null
    val prefix = "/hrt/dose-actions/"
    if (!path.startsWith(prefix)) return@runCatching null
    val encodedId = path.removePrefix(prefix)
    if (encodedId.isEmpty() || encodedId.contains('/')) return@runCatching null
    parseUuid(encodedId)
}.getOrNull()

private fun parseUuid(value: String): UUID? = runCatching {
    UUID.fromString(value)
}.getOrNull()
